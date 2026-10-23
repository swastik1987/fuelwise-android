package app.fuelwise.feature.refuel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.fuelwise.core.data.repository.EntryRepository
import app.fuelwise.core.data.repository.FuelPriceRepository
import app.fuelwise.core.data.repository.TripRepository
import app.fuelwise.core.data.repository.UserDataRepository
import app.fuelwise.core.data.repository.VehicleRepository
import app.fuelwise.core.data.vault.ScanPhotoStash
import app.fuelwise.core.designsystem.component.KeypadKey
import app.fuelwise.core.domain.engine.ComputePredictions
import app.fuelwise.core.domain.entry.CheckFillUp
import app.fuelwise.core.domain.entry.ComputeFillReward
import app.fuelwise.core.domain.entry.PriceTriangle
import app.fuelwise.core.domain.entry.PriceTriangle.Field
import app.fuelwise.core.domain.entry.gpsDistances
import app.fuelwise.core.domain.entry.odometerReadings
import app.fuelwise.core.domain.prices.FairPrice
import app.fuelwise.core.domain.prices.PriceDefault
import app.fuelwise.core.domain.prices.choosePriceDefault
import app.fuelwise.core.domain.prices.priceFuelFor
import app.fuelwise.core.domain.trip.FillUpPrefill
import app.fuelwise.core.model.ChargerType
import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.model.FuelGrade
import app.fuelwise.core.model.FuelPrice
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.PaymentMethod
import app.fuelwise.core.model.PriceSource
import app.fuelwise.core.model.Refuel
import app.fuelwise.core.model.ScanPrefill
import app.fuelwise.core.ui.format.KeypadInput
import app.fuelwise.core.ui.format.MoneyInput
import app.fuelwise.core.ui.format.NumberInput
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Add / edit fill-up and EV charge (PRD F-02, design.md screens 6 and 8). Orchestration only: the triangle, checks
 * and reward come from core:domain.
 */
// The sheet reads a vehicle's fills, trips, city prices and scan results, so it lists more collaborators than the
// detekt limit; a holder object would only hide them (as in HomeViewModel).
@Suppress("LongParameterList")
@HiltViewModel
class RefuelViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val vehicles: VehicleRepository,
    private val entries: EntryRepository,
    private val trips: TripRepository,
    private val cityPrices: FuelPriceRepository,
    private val userData: UserDataRepository,
    private val checkFillUp: CheckFillUp,
    private val computeReward: ComputeFillReward,
    private val predictions: ComputePredictions,
    private val clock: Clock,
    private val zoneId: ZoneId,
    private val scanPhotos: ScanPhotoStash,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<RefuelRoute>()
    private var editing: Refuel? = null
    private var tankFills: List<Refuel> = emptyList()

    /** Home charging tariff from Settings (PRD F-02.5, F-18); Home AC charges start from it when set. */
    private var homeTariff: Money? = null
    private var attemptedSave = false

    // From a fill-up prompt (PRD F-21.4): the stop's time and station. The odometer stays a hint (F-21.5).
    private val prompt = FillUpPrefill.decode(route.prompt)

    private val _state = MutableStateFlow(
        RefuelFormState(
            dateTimeUtcMs = prompt?.atUtcMs ?: clock.millis(),
            isEdit = route.refuelId != null,
            station = prompt?.station?.take(MAX_TEXT).orEmpty(),
            moreExpanded = prompt?.station != null,
        ),
    )
    val state: StateFlow<RefuelFormState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            load()
            // Scan results arrive in the route (opened from the scan screen) or later on this entry's saved state
            // (scanned from the sheet's banner). Each is applied once.
            savedStateHandle.getStateFlow<String?>(SCAN_RESULT_KEY, null).filterNotNull().collect { text ->
                savedStateHandle[SCAN_RESULT_KEY] = null
                applyScan(ScanPrefill.decode(text))
            }
        }
    }

    /** Fills what the user confirmed on the scan screen (PRD F-15); other fields keep what was typed. */
    private fun applyScan(scan: ScanPrefill) {
        // A newer scan's photo replaces an earlier one.
        _state.value.scanPhotoToken?.takeIf { it != scan.photoToken && scan.photoToken != null }?.let(scanPhotos::take)
        _state.update { s ->
            var next = s.withScannedTriangle(scan)
            scan.odometerKm?.let { next = next.copy(odometer = it.toString()) }
            scan.dateTimeUtcMs?.let { next = next.copy(dateTimeUtcMs = it) }
            scan.stationName?.let { next = next.copy(station = it.take(MAX_TEXT), moreExpanded = true) }
            next.copy(scanPhotoToken = scan.photoToken ?: s.scanPhotoToken, focus = null).revalidated()
        }
    }

    private fun RefuelFormState.withScannedTriangle(scan: ScanPrefill): RefuelFormState {
        val qty = scan.quantity?.let { "%.2f".format(java.util.Locale.ROOT, it) }
        val price = scan.unitPrice?.let(MoneyInput::plain)
        val total = scan.total?.let(MoneyInput::plain)
        // All three passed the triangle check on the scan screen: keep them as read, with no recompute rounding.
        if (qty != null && price != null && total != null) {
            return copy(
                quantity = qty,
                unitPrice = price,
                total = total,
                recentEdits = listOf(Field.Total, Field.UnitPrice),
                priceSource = PriceSource.Scanned,
            )
        }
        var next = this
        qty?.let { next = next.triangleEdit(Field.Quantity, it) }
        price?.let { next = next.triangleEdit(Field.UnitPrice, it).copy(priceSource = PriceSource.Scanned) }
        total?.let { next = next.triangleEdit(Field.Total, it) }
        return next
    }

    /** The scan photo stays off this entry. */
    fun onRemoveScanPhoto() {
        _state.value.scanPhotoToken?.let(scanPhotos::take)
        _state.update { it.copy(scanPhotoToken = null) }
    }

    override fun onCleared() {
        // A sheet closed without saving drops its photo from memory.
        _state.value.scanPhotoToken?.let(scanPhotos::take)
    }

    private suspend fun load() {
        val all = vehicles.observeVehicles().first()
        val existing = route.refuelId?.let { entries.observeRefuel(it).first() }
        editing = existing
        val vehicleId = existing?.vehicleId ?: route.vehicleId ?: userData.userData.first().selectedVehicleId
        val vehicle = all.firstOrNull { it.id == vehicleId } ?: all.firstOrNull()
            ?: vehicles.observeVehicle(vehicleId.orEmpty()).first()
        val units = userData.userData.first().units.forVehicle(vehicle?.id)
        _state.update { it.copy(vehicles = all, vehicle = vehicle, units = units, loading = false) }
        if (existing != null) {
            val tankIndex = vehicle?.tanks?.indexOfFirst { it.id == existing.tankId }?.coerceAtLeast(0) ?: 0
            _state.update { it.copy(tankIndex = tankIndex).withEntry(existing) }
            refreshTank()
        } else {
            refreshTank(applyDefaults = true)
        }
    }

    /** Reloads the tank's fills (for checks, prediction and defaults). Defaults apply only to new entries. */
    private suspend fun refreshTank(applyDefaults: Boolean = false) {
        val s = _state.value
        val vehicle = s.vehicle ?: return
        val tank = s.tank ?: return
        val refuels = entries.observeRefuels(vehicle.id).first()
        val expenses = entries.observeExpenses(vehicle.id).first()
        tankFills = refuels.filter { it.tankId == tank.id && it.id != editing?.id }
        homeTariff = userData.userData.first().homeTariff
        // A finished trip's odometer counts too (PRD F-07.6), and so does distance recorded since the last reading
        // (PRD F-21.5), so the hint follows recent driving. A hint only: the typed value always wins.
        val tripList = trips.observeTrips().first()
        val predicted = predictions.predictedOdometer(
            odometerReadings(vehicle, refuels, expenses, tripList),
            gpsDistances(vehicle, tripList),
        )
        val last = tankFills.maxByOrNull { it.dateTimeUtcMs }
        val latestCity = if (applyDefaults) latestCityPrice(tank.energyType, last?.grade) else null
        _state.update { current ->
            var next = current.copy(predictedOdometer = predicted)
            if (applyDefaults) {
                next = next.copy(
                    isFull = last?.isFull ?: true,
                    grade = last?.grade,
                    payment = last?.paymentMethod,
                    charger = if (current.isCharge) last?.chargerType ?: ChargerType.HomeAc else null,
                    socStart = last?.socStartPct?.toFloat() ?: RefuelFormState.DEFAULT_SOC_START,
                    socEnd = last?.socEndPct?.toFloat() ?: RefuelFormState.DEFAULT_SOC_END,
                )
                if (current.unitPrice.isEmpty()) next = next.withPriceDefault(last, latestCity)
            }
            next.revalidated()
        }
    }

    /**
     * Price defaults to the last price paid for this tank, or the home-city price when that is at least as new
     * (PRD F-02.3, F-09), and counts as one locked edit.
     */
    private fun RefuelFormState.withPriceDefault(last: Refuel?, latestCity: FuelPrice?): RefuelFormState {
        // Home AC charges start from the Settings home tariff when one is set (PRD F-02.5, F-18).
        homeTariff.takeIf { isCharge && charger == ChargerType.HomeAc }?.let { tariff ->
            return copy(
                unitPrice = MoneyInput.plain(tariff),
                recentEdits = listOf(Field.UnitPrice),
                priceSource = PriceSource.Typed,
                homeTariffPrice = true,
            )
        }
        val default = if (isCharge) {
            tankFills.filter { it.chargerType == charger }.maxByOrNull { it.dateTimeUtcMs }?.unitPrice
                ?.let(PriceDefault::LastPaid)
        } else {
            choosePriceDefault(last?.unitPrice, last?.let { localDate(it.dateTimeUtcMs) }, latestCity, today())
        } ?: return this
        return copy(
            unitPrice = MoneyInput.plain(default.price),
            recentEdits = listOf(Field.UnitPrice),
            priceSource = if (default is PriceDefault.City) PriceSource.CityPrice else PriceSource.LastPaid,
        )
    }

    /** The newest home-city price for the tank's fuel, if a home city and a price exist (PRD F-09.1). */
    private suspend fun latestCityPrice(energy: EnergyType, grade: FuelGrade?): FuelPrice? {
        val city = userData.userData.first().homeCityId ?: return null
        val fuel = priceFuelFor(energy, grade) ?: return null
        return cityPrices.latestOnOrBefore(city, fuel, today())
    }

    /** "Did I pay fair?" (PRD F-09.3): the saved price against the home-city price in effect that day. */
    private suspend fun fairPriceFor(refuel: Refuel): FairPrice? {
        val tank = _state.value.vehicle?.tanks?.firstOrNull { it.id == refuel.tankId } ?: return null
        val city = userData.userData.first().homeCityId ?: return null
        val fuel = priceFuelFor(tank.energyType, refuel.grade) ?: return null
        val day = localDate(refuel.dateTimeUtcMs)
        val history = cityPrices.history(city, fuel, day.minusDays(FairPrice.MAX_AGE_DAYS), day)
        return FairPrice.of(refuel.unitPrice, day, history)
    }

    private fun localDate(utcMs: Long): LocalDate = Instant.ofEpochMilli(utcMs).atZone(zoneId).toLocalDate()

    private fun today(): LocalDate = LocalDate.now(clock.withZone(zoneId))

    fun onKey(key: KeypadKey) {
        val field = _state.value.focus ?: return
        _state.update { s ->
            when (field) {
                NumericField.Odometer -> s.copy(odometer = KeypadInput.apply(s.odometer, key, maxDecimals = 1))

                NumericField.Quantity -> s.triangleEdit(Field.Quantity, KeypadInput.apply(s.quantity, key, 2))

                NumericField.UnitPrice -> s.triangleEdit(Field.UnitPrice, KeypadInput.apply(s.unitPrice, key, 2))

                NumericField.Total -> s.triangleEdit(Field.Total, KeypadInput.apply(s.total, key, 2))

                NumericField.Discount -> s.copy(discount = KeypadInput.apply(s.discount, key, 2))

                NumericField.Duration -> s.copy(
                    durationMin = KeypadInput.apply(s.durationMin, key, 0, maxIntegerDigits = 4),
                )
            }.revalidated()
        }
    }

    fun onFocus(field: NumericField?) = _state.update { it.copy(focus = field) }

    /** "Next field" walks odometer → the two triangle inputs the user hasn't had computed → done. */
    fun onNextField() = _state.update { s ->
        val order = listOf(NumericField.Odometer, NumericField.Quantity, NumericField.UnitPrice, NumericField.Total)
            .filterNot { it.toTriangle() == s.computedField }
        val i = order.indexOf(s.focus)
        s.copy(focus = order.getOrNull(i + 1))
    }

    fun onUsePrediction() = _state.update { s ->
        s.predictedOdometer?.let {
            s.copy(odometer = it.toLong().toString(), focus = NumericField.Quantity).revalidated()
        }
            ?: s
    }

    fun onFullChange(full: Boolean) = _state.update { it.copy(isFull = full) }

    fun onToggleMore() = _state.update { it.copy(moreExpanded = !it.moreExpanded, focus = null) }

    fun onGrade(grade: FuelGrade) = _state.update { it.copy(grade = if (it.grade == grade) null else grade) }

    /** ₹ or %: switching clears what was typed, since the same number means something else. */
    fun onDiscountUnit(percent: Boolean) = _state.update {
        if (it.discountPercent == percent) it else it.copy(discountPercent = percent, discount = "")
    }

    fun onPayment(method: PaymentMethod) = _state.update {
        it.copy(
            payment = if (it.payment ==
                method
            ) {
                null
            } else {
                method
            },
        )
    }

    fun onStation(text: String) = _state.update { it.copy(station = text.take(MAX_TEXT)) }

    fun onNotes(text: String) = _state.update { it.copy(notes = text.take(MAX_NOTES)) }

    fun onOperator(text: String) = _state.update { it.copy(operator = text.take(MAX_TEXT)) }

    fun onMissedPrevious(missed: Boolean) = _state.update { it.copy(missedPrevious = missed) }

    fun onSoc(start: Float, end: Float) = _state.update { it.copy(socStart = start, socEnd = end) }

    fun onCharger(type: ChargerType) {
        _state.update { it.copy(charger = type) }
        // Tariff follows the charger type: the Settings home tariff for Home AC when set, else the last price paid
        // with this charger type.
        val tariff = homeTariff.takeIf { type == ChargerType.HomeAc }
        val lastPrice = tariff
            ?: tankFills.filter { it.chargerType == type }.maxByOrNull { it.dateTimeUtcMs }?.unitPrice
        if (lastPrice != null && !_state.value.isEdit) {
            _state.update { s ->
                s.copy(unitPrice = MoneyInput.plain(lastPrice), priceSource = PriceSource.LastPaid)
                    .let {
                        if (Field.UnitPrice in
                            it.recentEdits
                        ) {
                            it.recomputed()
                        } else {
                            it.triangleEdit(Field.UnitPrice, it.unitPrice)
                        }
                    }
                    .copy(homeTariffPrice = tariff != null)
            }
        } else {
            _state.update { it.copy(homeTariffPrice = false) }
        }
    }

    fun onDateTime(utcMs: Long) {
        _state.update { it.copy(dateTimeUtcMs = utcMs).revalidated() }
    }

    fun onTank(index: Int) {
        if (index == _state.value.tankIndex || _state.value.isEdit) return
        _state.update {
            it.copy(tankIndex = index, unitPrice = "", quantity = "", total = "", recentEdits = emptyList())
        }
        viewModelScope.launch { refreshTank(applyDefaults = true) }
    }

    fun onVehicle(id: String?) {
        if (_state.value.isEdit) return
        val vehicle = _state.value.vehicles.firstOrNull { it.id == id } ?: return
        _state.update {
            RefuelFormState(
                loading = false,
                vehicles = it.vehicles,
                vehicle = vehicle,
                dateTimeUtcMs = it.dateTimeUtcMs,
            )
        }
        viewModelScope.launch {
            userData.setSelectedVehicle(vehicle.id)
            val units = userData.userData.first().units.forVehicle(vehicle.id)
            _state.update { it.copy(units = units) }
            refreshTank(applyDefaults = true)
        }
    }

    fun save() {
        val s = _state.value
        if (s.saving || s.reward != null) return
        attemptedSave = true
        val checked = s.revalidated()
        _state.value = checked
        if (checked.missing.isNotEmpty() || checked.problems.isNotEmpty()) return
        val refuel = checked.toRefuel() ?: return
        _state.update { it.copy(saving = true, focus = null) }
        viewModelScope.launch {
            val id = entries.saveRefuel(refuel)
            // Encrypted in the vault only now that the entry exists (PRD F-15, CLAUDE.md rule 5).
            checked.scanPhotoToken?.let { scanPhotos.attachToRefuel(it, id) }
            _state.update { it.copy(scanPhotoToken = null) }
            val fills = entries.observeRefuels(refuel.vehicleId).first().filter { it.tankId == refuel.tankId }
            val fair = fairPriceFor(refuel)
            _state.update { it.copy(saving = false, reward = computeReward(fills, id), fairPrice = fair) }
        }
    }

    // --- state helpers ---

    private fun RefuelFormState.triangleEdit(field: Field, text: String): RefuelFormState {
        val edited = when (field) {
            Field.Quantity -> copy(quantity = text)
            Field.UnitPrice -> copy(unitPrice = text, priceSource = PriceSource.Typed, homeTariffPrice = false)
            Field.Total -> copy(total = text)
        }
        return edited.copy(recentEdits = PriceTriangle.recordEdit(recentEdits, field)).recomputed()
    }

    /** Re-derives the computed field from the two locked ones. */
    private fun RefuelFormState.recomputed(): RefuelFormState {
        val values = PriceTriangle.Values(
            quantity = NumberInput.parseDecimal(quantity),
            unitPrice = MoneyInput.parse(unitPrice),
            total = MoneyInput.parse(total),
        )
        val solved = PriceTriangle.solve(values, computedField)
        return when (computedField) {
            Field.Quantity -> copy(
                quantity = solved.quantity?.let {
                    "%.2f".format(java.util.Locale.ROOT, it)
                }.orEmpty(),
            )

            Field.UnitPrice -> copy(unitPrice = solved.unitPrice?.let(MoneyInput::plain).orEmpty())

            Field.Total -> copy(total = solved.total?.let(MoneyInput::plain).orEmpty())
        }
    }

    private fun RefuelFormState.revalidated(): RefuelFormState {
        val odo = NumberInput.parseDecimal(odometer)
        val qty = NumberInput.parseDecimal(quantity)
        val result = checkFillUp(dateTimeUtcMs, odo, qty, tank?.capacity, tankFills)
        val missing = if (!attemptedSave) {
            emptySet()
        } else {
            buildSet {
                if (odo == null) add(RequiredField.Odometer)
                if (qty == null || qty <= 0.0) add(RequiredField.Quantity)
                if (MoneyInput.parse(total) == null) add(RequiredField.Total)
            }
        }
        return copy(problems = result.problems, warnings = result.warnings, missing = missing)
    }

    private fun RefuelFormState.withEntry(r: Refuel) = copy(
        dateTimeUtcMs = r.dateTimeUtcMs,
        odometer = NumberInput.plain(r.odometerKm),
        quantity = "%.2f".format(java.util.Locale.ROOT, r.quantity),
        unitPrice = MoneyInput.plain(r.unitPrice),
        total = MoneyInput.plain(r.total),
        recentEdits = listOf(Field.Total, Field.Quantity),
        priceSource = r.priceSource,
        isFull = r.isFull,
        grade = r.grade,
        payment = r.paymentMethod,
        discount = if (r.discount == Money.ZERO) "" else MoneyInput.plain(r.discount),
        station = r.stationName.orEmpty(),
        notes = r.notes.orEmpty(),
        missedPrevious = r.missedPrevious,
        socStart = r.socStartPct?.toFloat() ?: RefuelFormState.DEFAULT_SOC_START,
        socEnd = r.socEndPct?.toFloat() ?: RefuelFormState.DEFAULT_SOC_END,
        charger = r.chargerType,
        operator = r.operator.orEmpty(),
        durationMin = r.chargeDurationMin?.toString().orEmpty(),
        moreExpanded = r.missedPrevious || r.notes != null,
        focus = null,
    )

    private fun RefuelFormState.toRefuel(): Refuel? {
        val vehicle = vehicle ?: return null
        val tank = tank ?: return null
        val qty = NumberInput.parseDecimal(quantity) ?: return null
        val totalMoney = MoneyInput.parse(total) ?: return null
        val price = MoneyInput.parse(unitPrice) ?: PriceTriangle.unitPriceOf(qty, totalMoney) ?: return null
        val discountMoney = discountMoney(totalMoney)?.takeIf { it <= totalMoney } ?: Money.ZERO
        return Refuel(
            id = editing?.id.orEmpty(),
            vehicleId = vehicle.id,
            tankId = tank.id,
            dateTimeUtcMs = dateTimeUtcMs,
            zoneId = editing?.zoneId ?: zoneId.id,
            odometerKm = NumberInput.parseDecimal(odometer) ?: return null,
            quantity = qty,
            unitPrice = price,
            total = totalMoney,
            discount = discountMoney,
            isFull = isFull,
            missedPrevious = missedPrevious,
            grade = grade,
            stationName = station.ifBlank { null },
            paymentMethod = payment,
            notes = notes.ifBlank { null },
            priceSource = priceSource,
            // An edit keeps where the entry was logged (Logbook map, PRD F-05).
            lat = editing?.lat,
            lng = editing?.lng,
        ).let { if (isCharge) it.withChargeFields(this) else it }
    }

    /** EV sessions carry SoC, charger and operator; a charge to 99% or more counts as full (PRD F-03.4). */
    private fun Refuel.withChargeFields(s: RefuelFormState) = copy(
        isFull = s.socEnd >= FULL_SOC,
        grade = null,
        socStartPct = s.socStart.toDouble(),
        socEndPct = s.socEnd.toDouble(),
        chargerType = s.charger,
        chargeDurationMin = s.durationMin.toIntOrNull(),
        operator = s.operator.ifBlank { null },
    )

    private fun NumericField.toTriangle(): Field? = when (this) {
        NumericField.Quantity -> Field.Quantity
        NumericField.UnitPrice -> Field.UnitPrice
        NumericField.Total -> Field.Total
        else -> null
    }

    private companion object {
        const val MAX_TEXT = 60
        const val MAX_NOTES = 500
        const val FULL_SOC = 99f
    }
}
