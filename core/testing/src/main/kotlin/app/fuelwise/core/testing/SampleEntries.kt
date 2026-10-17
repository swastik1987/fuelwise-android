package app.fuelwise.core.testing

import app.fuelwise.core.model.Expense
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.PaymentMethod
import app.fuelwise.core.model.Refuel
import app.fuelwise.core.model.SystemCategory
import java.time.LocalDate
import java.time.ZoneId

/** Safari diesel history in Bengaluru (₹88–₹90/L), Aug–Oct 2026: three measured intervals, then a service. */
object SampleEntries {
    private val zone = ZoneId.of("Asia/Kolkata")

    fun at(year: Int, month: Int, day: Int, hour: Int = 9): Long =
        LocalDate.of(year, month, day).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun fill(id: String, ms: Long, odo: Double, litres: Double, pricePaise: Long, station: String? = null) =
        Refuel(
            id = id,
            vehicleId = "safari",
            tankId = "safari-diesel",
            dateTimeUtcMs = ms,
            zoneId = zone.id,
            odometerKm = odo,
            quantity = litres,
            unitPrice = Money(pricePaise),
            total = Money((litres * pricePaise).toLong()),
            isFull = true,
            stationName = station,
            paymentMethod = PaymentMethod.Upi,
        )

    val safariRefuels = listOf(
        fill("r1", at(2026, 8, 20), 42_400.0, 44.0, 8_890),
        fill("r2", at(2026, 9, 10), 43_050.0, 42.5, 8_905),
        fill("r3", at(2026, 9, 30), 43_690.0, 41.0, 8_920, "Indian Oil · Koramangala"),
        fill("r4", at(2026, 10, 14), 44_310.0, 40.2, 8_930, "HP · Indiranagar"),
    )

    val safariService = Expense(
        id = "e1",
        vehicleId = "safari",
        categoryId = SystemCategory.Service.id,
        dateTimeUtcMs = at(2026, 10, 5, 11),
        zoneId = zone.id,
        amount = Money(485_000),
        odometerKm = 44_000.0,
        paymentMethod = PaymentMethod.Card,
        notes = "20,000 km service",
    )
}
