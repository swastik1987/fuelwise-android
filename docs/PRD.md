# PRD — Fuelwise (working name)

> Android vehicle running-cost tracker for India. Native Kotlin + Jetpack Compose.
> Feature set benchmarked against Fuelio (Sygic) free + Pro, extended for Indian vehicles and habits.
> **Owner:** Swastik · **Status:** Draft v1.1 · **Last updated:** 16 Oct 2026
> **v1.1 changes:** Added F-21 Location intelligence (station auto-detect, automatic trips, fill-up detection). Decisions D11–D13 recorded in §13.
> Companion files: `CLAUDE.md` (engineering rules), `docs/design.md` (UI brief for Claude Design).

"Fuelwise" is a placeholder. Run a trademark and Play Store name search before using any name. Never use "Fuelio", its icon, its screens or its copy anywhere in the product. The single exception is a factual compatibility label inside the import feature ("Import from Fuelio CSV").

---

## 0. How to read this document

- Every feature has an ID (`F-xx.yy`), a **tier** (`FREE` / `PRO`) and a **release** (`R1` launch, `R2`, `R3`, `Later`).
- "Fuelio parity" marks features Fuelio already has. "New" marks our additions.
- **Acceptance criteria (AC)** are written to be testable. Claude Code should implement against the AC and write tests for them.
- §13 lists the decisions still open. Do not silently resolve them in code.

---

## 1. Product summary

### 1.1 One-liner
Know exactly what your vehicle costs per kilometre — petrol, diesel, CNG or EV — and never miss a PUC, insurance or service date. Everything stays on your phone, backed up to your own Google Drive.

### 1.2 Why this exists (positioning)

| Competitor | What they own | Where they're weak, and our opening |
|---|---|---|
| **Fuelio** (5M+ downloads, 4.6★) | Mature fuel log, crowdsourced prices (Europe-strong), trip GPS log, Pro route planner | Global and generic. Weak Indian price data. Bi-fuel CNG handling is generic. Multi-device sync complaints. Users find it over-complex. |
| **Park+ / CarInfo** | India "vehicle admin" super-apps: challans, FASTag recharge, insurance sales, RC lookup | Monetised through insurance and recharge commissions. Not a running-cost tool. Ad- and upsell-heavy. |
| **Drivvo / Simply Auto** | Generic expense loggers | Generic, ad-supported, no Indian depth. |

**Wedge:** cost intelligence for Indian vehicles. Be the most accurate and the fastest at answering "what does my vehicle cost me?", for CNG, EV and two-wheelers too. Do **not** compete with Park+/CarInfo on challans, insurance selling or FASTag recharge. We remind; we don't sell.

### 1.3 Principles
1. **10-second logging.** If adding a fill-up takes longer than 10 seconds, it's a bug.
2. **Private by default.** User data never touches our servers. The only network calls are the user's own Google Drive, a public reference-data feed (fuel prices and station-list updates) and map tiles/routing APIs. Location history never leaves the device unless the user enables encrypted backups.
3. **Honest numbers.** Every metric shows how it was computed. Estimates are labelled "est.". Bi-fuel and EV numbers never pretend to be more precise than the inputs allow.
4. **Calm UI.** Minimal, numbers first. No ads, ever. No streak guilt.
5. **India-native.** ₹ with lakh grouping, kg for CNG, two-wheelers first-class, Hindi at launch, PUC/insurance/RC rules built in.

---

## 2. Users and jobs-to-be-done

| Persona | Vehicle | Primary jobs |
|---|---|---|
| **Commuter car owner** | Petrol/diesel hatch or SUV | "Am I getting the mileage I should?" "What did the car cost this month?" |
| **CNG owner** | Factory CNG + petrol (e.g., Ertiga, WagonR, Nexon iCNG) | "Is CNG actually saving me money?" "Track kg vs litres properly." |
| **EV owner** | Nexon EV, MG ZS EV, Ather, Ola | "Home vs public charging cost, km/kWh, real ₹/km vs petrol." |
| **Two-wheeler rider** | Scooter/motorcycle (~majority of Indian vehicles) | "Mileage, service every 3,000 km, PUC." |
| **Family fleet** | 2–3 vehicles | "One place for all vehicles, documents and renewal dates." |
| **Reimbursement driver** | Salaried employee or self-employed using own car for work | "Business km and cost report for claims." |

---

## 3. Engagement model (honest)

A fuel log is **not** a daily-use app. Indians fill fuel 3–8 times a month. Design for the real loops instead of faking daily engagement:

| Loop | Frequency | Hook |
|---|---|---|
| **Log** | Every fill-up/charge | 10-second entry, pump-meter scan, instant "this tank: 18.4 km/L ▲ 3%" reward |
| **Check** | Weekly | Optional weekly digest notification; home widget shows ₹/km and next due date |
| **Reflect** | Monthly | Monthly Recap card (shareable); yearly "Drive Wrapped" |
| **Protect** | Event-driven | PUC, insurance, service, document expiry reminders |
| **Plan** | Occasional | Trip cost estimate, trip split with friends (UPI link) |

**North-star metric:** % of monthly actives who log ≥2 fill-ups in the month. That is what makes the numbers accurate, and accurate numbers are the retention reason.
**Guardrail:** median fill-up entry time ≤10 s (measured from tapping + to tapping Save).

---

## 4. Fuelio feature inventory → our plan

This is the parity map from the research (Play Store listing, fuel.io, Pro release notes, changelogs, reviews).

| # | Fuelio feature | Fuelio tier | Our ID | Our tier | Release | Our take |
|---|---|---|---|---|---|---|
| 1 | Fill-up log (qty, price, total, odometer, full/partial, GPS location, station, notes, time) | Free | F-02 | FREE | R1 | Parity + price triangle + odometer prediction |
| 2 | Full-tank consumption algorithm, partial fills | Free | F-03 | FREE | R1 | Parity + missed fill-up handling, golden tests |
| 3 | Unlimited vehicles; car/moto/van/truck | Free | F-01 | FREE | R1 | Parity + scooter, auto-rickshaw |
| 4 | Fuel types: petrol, diesel, ethanol, LPG, CNG, EV; bi-fuel (2 tanks); flex | Free | F-01/F-03 | FREE | R1 | Parity + Indian fuel grades (E20, premium) |
| 5 | Units: km/mi; L, US/UK gal, kWh, kg, m³; many consumption units | Free | F-18 | FREE | R1 | Parity, India defaults (km, L, kg, kWh, km/L) |
| 6 | Change units during a fill-up; decimal odometer; discounts (%) | Free | F-02 | FREE | R1 | Parity |
| 7 | Cost module with custom categories (service, insurance, wash, parking, tolls…) | Free on Android | F-04 | FREE | R1 | Parity + Indian presets |
| 8 | Stats (totals, averages, fill-ups, costs, distance) | Free | F-06 | FREE | R1 | Parity |
| 9 | Charts: consumption, monthly costs, fuel price, fill-up cost, odometer, cost/km, categories, fuel vs other | Free | F-06 | FREE | R1 | Parity, redesigned |
| 10 | Timeline (fuel + costs in one list) | Free | F-05 | FREE | R1 | Parity: "Logbook" |
| 11 | Fill-ups on a map | Free | F-05 | FREE | R1 | Parity |
| 12 | Reminders (by date, by odometer) | Free on Android | F-11 | FREE | R1 | Parity + India presets (PUC by emission norm, insurance) |
| 13 | CSV import/export; import from other apps | Free | F-13 | FREE | R1 | Parity + one-tap "Import from Fuelio CSV" |
| 14 | Dropbox + Google Drive backup, auto-backup on change | Free | F-14 | FREE | R1 | Google Drive only (appDataFolder), scheduled; Dropbox dropped |
| 15 | Multi-device sync | Free (complained about) | F-14.6 | PRO | R2 | Do it properly: row-level merge |
| 16 | Reports (text/PDF), seller's report without prices | Free | F-13 | FREE basic / PRO PDF | R1 | Parity + business mileage report |
| 17 | Crowdsourced fuel prices, station DB, ratings, facilities, add station | Free | — | — | Later | **Dropped from launch.** Needs a backend and a network effect we can't bootstrap. Replaced by city-level official prices (F-09). |
| 18 | Nearest gas stations | Free | F-09.4 | FREE | R1 | Hand-off to Google Maps search (zero API cost) |
| 19 | Trip log: manual or GPS; trip cost; map preview; GPX export; restore interrupted trip | Free | F-07 | FREE | R1 | Parity |
| 20 | Trip auto-start via vehicle Bluetooth | Free | F-21.3 | FREE | R2 | **Better than parity:** automatic trips from driving detection (works for two-wheelers too), with car Bluetooth as an extra signal. This is the app's one declared background-location feature. |
| 20a | — (new) Auto-detect the fuel station when logging | — | F-21.1 | FREE | R1 | New. Offline station list (OpenStreetMap) plus the user's own history. No Google Places. |
| 20b | — (new) Detect a likely fill-up from a stop at a station and prompt to log | — | F-21.4 | FREE | R2 | New. Attacks the #1 risk: users forgetting to log. |
| 21 | Post-trip odometer prompt; floating "smart bubble" | Free | F-07.6 | FREE | R1 | Prompt yes; floating overlay **no** (SYSTEM_ALERT_WINDOW is a review risk; use a notification action instead) |
| 22 | Trip weekly view | Pro | F-07.7 | FREE | R1 | Cheap to build, keep free |
| 23 | Widgets: quick actions, trip controls (free); dashboard, weekly trip, consumption (Pro) | Mixed | F-16 | Mixed | R1 | Parity |
| 24 | Light/dark themes | Free | F-18 | FREE | R1 | Parity + Material You optional |
| 25 | Currency conversion tool | Free | — | — | Later | Low value for an India-first app |
| 26 | Weather in fuel logs | Pro | — | — | Later | Low value; skip |
| 27 | Fuel receipt scanning (offline OCR) | Pro | F-15 | FREE | R1 | **Free** + pump-meter and odometer scan. This is our acquisition magic. |
| 28 | Route planner: stations on route, filters, average prices on route | Pro | F-08 | PRO | R3 | Parity, India-tuned (state price differences) |
| 29 | Route expense report: distance, ETA, fuel needed, cost, number of stops | Pro | F-08 | PRO | R3 | Parity + toll estimate (manual or best-effort) |
| 30 | Route settings: fastest/shortest/economical, avoid highways/tolls | Pro | F-08 | PRO | R3 | Parity |
| 31 | Refuel stop suggestions based on range | Pro | F-08 | PRO | R3 | Parity |
| 32 | Custom vehicle parameters (consumption, price, range, tank) | Pro | F-01.4 | FREE | R1 | Free; it's just a settings screen |
| 33 | Station name / fuel-type filter on map | Pro | F-08 | PRO | R3 | Within the planner only |
| 34 | Android Auto (POI: fuel prices) | Free | F-20 | — | Later | Needs station-level data first |
| 35 | No ads, no account, data local | Free | — | — | R1 | Same promise. It's table stakes for trust. |

**Fuelio pricing for reference:** Pro launched June 2023 at about €9/year with a 3-day trial. App Store lists $2.99/month and $14.99/year. Third-party reviews cite roughly $2.19/month on Android. There's no lifetime option, which users notice. → See §11 for our pricing proposal.

**User pain points pulled from Fuelio reviews (design against these):**
- Sync across devices or Android Auto loses entries.
- "Over-complex"; the station finder doesn't default to the vehicle's fuel type and doesn't sort by price.
- No way to see all trips over a period on one map.
- Station price data sparse or wrong in many regions.

---

## 5. Feature specifications

### F-01 Vehicles (Garage) — FREE · R1

**F-01.1 Vehicle profile.** ✅ built (photo, accent colour, make/model/year and purchase details not yet) Fields:
- Name (required, e.g., "Safari")
- Type: car / motorcycle / scooter / auto-rickshaw / van / truck / other
- Make, model, variant, year (optional, free text in R1)
- Registration number (optional; format-validated for Indian plates, BH-series supported)
- Emission norm: BS-III / BS-IV / BS-VI (drives PUC defaults)
- Purchase date and purchase odometer
- Photo
- Colour accent (auto from photo or picked)
- Archived flag (sold vehicles stay in history)

**F-01.2 Energy setup.** ✅ built (hybrid tag and default grade not yet)
- **Single fuel:** petrol, diesel, CNG, LPG, electric, ethanol/flex.
- **Bi-fuel:** two tanks, e.g., CNG (kg) + petrol (L). One is marked primary.
- **Hybrid (HEV):** treated as single-fuel petrol with a "hybrid" tag.
- **Per tank:** capacity (L, kg or usable kWh), default fuel grade (e.g., Petrol E20, Petrol premium/XP95, Diesel, CNG).

**F-01.3 Claimed efficiency.** ✅ built Optional ARAI/claimed figure (km/L, km/kg, km/kWh). Used for "your real vs claimed" comparisons.

**F-01.4 Custom parameters.** (stored; editing UI not yet) The user can override computed values used for estimates: consumption, fuel price, range and tank capacity. Mark them "manual" in the UI.

**F-01.5 Multiple vehicles.** ✅ built ("All vehicles" view arrives with stats, M5) Unlimited. A global vehicle switcher exists. "All vehicles" aggregate view is available for stats.

**AC**
- Creating a vehicle requires only name, type, fuel setup and current odometer. Everything else is optional.
- Archiving hides the vehicle from the switcher and keeps its data in exports and "All vehicles" history.
- Deleting a vehicle requires typing its name to confirm. It cascades and is undoable for 10 s via snackbar.

---

### F-02 Refuel and charge log — FREE · R1

**F-02.1 Fill-up entry fields:** ✅ built (photos and GPS location not yet)
- Vehicle, tank (bi-fuel only), date-time (default now)
- Odometer
- Quantity, price/unit and total (the **price triangle**)
- Filled to full? (default: last used value per vehicle)
- Missed previous fill-up? (hidden under "More")
- Fuel grade, station name/brand, payment method (UPI/card/cash/FASTag-wallet/fleet card), discount (₹ or %), location (auto, editable), notes, attachments (receipt/meter photos)

**F-02.2 Price triangle.** ✅ built (discount in ₹; % option not yet) The user enters any two of {quantity, price/unit, total}; the third is computed live.
- Last-edited two fields are "locked"; the third shows as computed (lighter text, "=" prefix).
- Rounding: quantity to 2 dp, price to 2 dp, total to 2 dp. Display ₹ with lakh grouping.
- Discount reduces **amount paid**, not price/unit. Both "pump price" and "paid" are stored.

**F-02.3 Smart defaults.** ✅ built (city price waits for F-09; station auto-detect for F-21.1)
- Price/unit = last price paid for that tank's grade. If a city price is available (F-09) and newer, use it and tag the source.
- Odometer field pre-filled with the **predicted** odometer (last odo + avg km/day × days since). Shown as a placeholder hint, not a value. The user must confirm or type it.
- Station = auto-detected from the current location when the sheet opens (F-21.1), if location permission is granted.

**F-02.4 Odometer input modes.** (not yet: odometer mode only) "Odometer" (default) or "Trip meter since last fill" (converted to odometer). Decimal allowed.

**F-02.5 EV charge session fields:** ✅ built (home tariff setting arrives with Settings, M11; the last tariff per charger type is used meanwhile)
- Date-time, odometer
- Energy (kWh billed/metered), cost (₹) or tariff (₹/kWh), price triangle on kWh
- Start SoC %, end SoC %
- Charger: home AC / public AC / public DC fast; network/operator (free text)
- Duration (optional), location, notes, attachments
- Home charging tariff stored in settings (₹/kWh) so the cost auto-fills.

**F-02.6 Validation and guards (new, India-relevant):** ✅ built
- Odometer must be greater than the previous entry's odometer **by date order**. If not, show an inline error with the conflicting entry. Allow override for back-dated entries; re-sort on save.
- **Short-fill guard:** if quantity > tank capacity × 1.03, warn "That's more than your tank holds (45 L). Check the pump reading or your tank size."
- **Anomaly flag:** on a full fill, if the computed consumption deviates more than 25% from the rolling average of the last 5 valid intervals, show a soft info chip ("Unusual — tap to review"). It never blocks saving.

**F-02.7 Post-save reward.** ✅ built (city-price delta waits for F-09) After saving a full fill that closes an interval, show a toast/sheet: "This tank: **16.8 km/L**, ▲ 4% vs your average. ₹5.92/km." Hold for 3 s, swipe to dismiss.

**AC**
- From the Home screen, a returning user can save a fill-up with 3 inputs (odometer, total, quantity or price) and 3 taps beyond typing.
- The triangle recomputes on every keystroke without jank (frame-time < 16 ms on a mid-range device).
- All entries are editable and deletable. Delete offers 10 s undo.
- Editing a historical entry triggers recomputation of all dependent intervals (F-03) within 200 ms for 2,000 entries.

---

### F-03 Consumption and cost engine — FREE · R1

A pure Kotlin module (`core:domain`), with no Android dependencies, 100% unit-tested. **This is the product's credibility, so treat it as such.**

**F-03.1 Full-tank algorithm (liquid/gas fuels):** ✅ built
1. Sort a tank's fill-ups by odometer, with date-time as tiebreaker.
2. An **interval** opens at a full fill-up `j` and closes at the next full fill-up `i`.
3. `distance = odo_i − odo_j`
4. `fuel = Σ quantity of fills in (j, i]` — partial fills between them are included.
5. If any fill in `(j, i]` has `missedPrevious = true`, the interval is **invalid**: no consumption is shown and the baseline resets at `i`.
6. The first full fill-up is a baseline only. Partial fills never show their own consumption.
7. Consumption is shown per interval on the closing fill-up.
8. The **average** is weighted: `Σ fuel(valid intervals) / Σ distance(valid intervals)`. Never use a mean of interval means.

**F-03.2 Units.** ✅ built Compute internally as L/100km, kg/100km or kWh/100km. Display in the user's chosen unit (km/L default in India).

**F-03.3 Bi-fuel.** ✅ built
- Run F-03.1 independently per tank.
- Per-fuel efficiency is **approximate**, because the distance in an interval is shared with the other fuel. Label it "est." with an info tooltip explaining why.
- The **hero metric for bi-fuel is combined cost/km**, which is exact.

**F-03.4 EV efficiency:** ✅ built
- **Primary method (SoC):** needs start/end SoC on sessions and the usable battery capacity.
  - `energy_used = (prev.endSoC − curr.startSoC) × capacity` over `distance = curr.odo − prev.odo`
  - `charging_efficiency = ((curr.endSoC − curr.startSoC) × capacity) / kWh_billed`
- **Fallback (full-charge method):** treat a session with endSoC ≥ 99% (or a "charged to full" toggle) as "full" and apply F-03.1 to kWh.
- Show "battery-to-wheel" efficiency (SoC method) and "wall-to-wheel" efficiency (billed kWh), labelled clearly.

**F-03.5 Cost metrics:** ✅ built (TCO not yet)
- **Cost/km (period)** = (fuel paid + included expenses) / distance driven in the period.
  - Distance = max odo − min odo of entries in the period. When there is too little data, extrapolate using the closest entries outside the period, and label it.
  - The user can configure which expense categories are included. Default: all except "Purchase" and "Loan EMI".
- **Fuel-only cost/km**, **cost/day**, **monthly burn**.
- **Total cost of ownership:** optional. Includes purchase price amortised over N years plus EMI and insurance.

**F-03.6 Predictions:** ✅ built
- `avgKmPerDay` from the last 90 days. Falls back to all-time; needs a minimum of 2 entries spanning ≥7 days.
- Predicted odometer today; predicted date for odometer-based reminders; estimated range left (if last fill was full: `capacity × efficiency − distance since`).

**F-03.7 Golden test vectors** ✅ passing (must pass exactly, tolerance 0.001):

| # | Odo (km) | Qty (L) | Full | Missed prev | Expected interval result |
|---|---|---|---|---|---|
| 1 | 10000 | 30 | ✓ | | baseline |
| 2 | 10300 | 10 | ✗ | | — (partial) |
| 3 | 10500 | 25 | ✓ | | 500 km / 35 L → **14.286 km/L**, 7.000 L/100km |
| 4 | 10900 | 28 | ✓ | ✓ | invalid → baseline reset |
| 5 | 11350 | 30 | ✓ | | 450 km / 30 L → **15.000 km/L**, 6.667 L/100km |

Weighted average over the valid intervals = 950 km / 65 L = **14.615 km/L** (6.842 L/100km).

EV vector: usable capacity 30 kWh. Session A ends at 80% at odo 5000. Session B starts at 30% at odo 5150, ends at 90%, billed 20 kWh.
→ energy used 15 kWh over 150 km = **10.0 km/kWh** (10.0 kWh/100km); charging efficiency 18/20 = **90%**.

---

### F-04 Expenses — FREE · R1

**F-04.1 Expense entry fields:** ✅ built (attachments and trip link not yet) vehicle, category, date, amount, odometer (optional), payment method, notes, attachments, link to trip (optional), toll plaza (toll category only).

**F-04.2 Categories.** ✅ presets built (custom categories, rename and hide not yet) System presets (renameable, hideable, not deletable if used):
- Service, Repair, Tyres, Battery, Insurance, PUC, Parking, Toll (FASTag), Toll (cash), Wash/Detailing, Accessories
- Fine/Challan, Road tax, Registration/RTO, Loan EMI, Purchase, Extended warranty, Other

Users can add custom categories (name, icon, colour).

**F-04.3 Recurring expenses.** ✅ built (M6) Repeats every N days/weeks/months/years (e.g., EMI, parking pass). Auto-creates entries on the due date, marked "auto" until the user confirms (one tap).

**F-04.4 Service line items.** An optional itemised breakdown for service/repair (part/labour, qty, amount), so "what did the last service include?" is answerable.

**AC:** Adding an expense from the "+" menu takes ≤3 taps plus typing the amount. A recurring entry never double-creates after a backup restore (idempotent by `recurrenceId + dueDate`).

---

### F-05 Logbook (timeline) — FREE · R1 ✅ built (M4: vehicle + type filters and search; category, date-range and payment filters, the map toggle and the 5,000-entry scroll benchmark are still open)

- Unified reverse-chronological list of fill-ups, charges, expenses, trips and reminder completions, grouped by month with a month total.
- Filters: vehicle, type, category, date range, payment method. Search on notes, station and plaza.
- Each row shows the key number (₹ total), a secondary line (qty · price/unit · odo) and, on closing fills, the interval efficiency chip.
- Map view toggle: all geotagged entries for the filtered range.
- Swipe: edit / delete (with undo).

**AC:** Smooth scrolling (no dropped frames) with 5,000 entries; paging via Room + Paging 3.

---

### F-06 Stats, charts and insights — FREE core · PRO advanced · R1 ✅ built (M5: periods, overview, six charts with data tables and formula sheets, free cards, Pro cards locked; custom date range and the city-price overlay are still open, the overlay waits for the price feed in M9)

**F-06.1 Period selector:** this month, last month, 3M, 6M, YTD, 12M, all time, custom.

**F-06.2 Overview metrics:**
- Total spend, fuel spend, other spend, distance
- Average efficiency, best/worst interval
- Cost/km, cost/day
- Fill-up count, average fill size, average price paid
- Per vehicle and "All vehicles"

**F-06.3 Charts (FREE):**
- Efficiency over time (per interval, with rolling average line)
- Monthly spend (stacked by category)
- Price paid per unit over time (overlaid with city price, if available)
- Cost/km trend
- Odometer growth
- Spend by category (horizontal bars — no pie charts)
- Fuel vs other

**F-06.4 Insights cards** (rules-based, on-device, no LLM in R1):
- **FREE:** "Mileage drop" — the last 2 intervals average below the rolling baseline by more than 10%. Suggest checking tyre pressure, air filter and service.
- **FREE:** "Real vs claimed" — your efficiency as % of the claimed figure.
- **PRO:** "Fuel grade compare" — efficiency and cost/km by grade (E20 vs premium vs regular), once there are ≥3 intervals each.
- **PRO:** "CNG saving" (bi-fuel) — ₹ saved vs running only on petrol, computed from your own numbers.
- **PRO:** "EV vs petrol" — what the same km would have cost at your city petrol price and a user-set petrol km/L.
- **PRO:** "Home vs public charging" — cost per kWh and share of each.
- **PRO:** "Forecast" — projected spend this month and year.
- **PRO:** Vehicle comparison side-by-side.

**AC:** Every metric has an "i" explaining its formula and data used. Charts render 3 years of data in < 300 ms.

---

### F-07 Trips — FREE · R1 (automatic trips FREE · R2, see F-21.3)

**F-07.1 Manual trip:**
- Start/end odometer (or distance), date, purpose (Personal / Business / Commute / custom), note.
- Cost auto-estimated from average efficiency × current price. Linked tolls/parking are added.

**F-07.2 GPS trip:**
- Start from the app, widget or quick-settings tile.
- Runs a foreground service (`foregroundServiceType="location"`) with a persistent notification showing distance and elapsed time, plus Pause/Stop actions.
- Records a polyline (Douglas-Peucker simplified, ~5 m tolerance), distance, duration, average/max speed and idle time.

**F-07.3 Crash resilience.** Points are persisted every 15 s. On process death or reboot, the app offers to resume or finish the interrupted trip (Fuelio parity).

**F-07.4 Trip detail:**
- Map, stats, estimated cost, cost per person.
- **Split with friends:** N people → share text that includes a UPI "pay me" link (`upi://pay?pa=<user VPA>&pn=<name>&am=<share>&cu=INR&tn=<trip>`). The VPA is entered once in settings. The app never initiates payments.

**F-07.5 Automatic trips (FREE, R2).** Superseded by F-21.3. Trips start and stop on their own when the user drives.

**F-07.6 Post-trip odometer prompt.** A notification action "Update odometer" opens a single-field sheet. **No floating overlay window.**

**F-07.7 Weekly view and combined map:**
- Week-by-week list with totals.
- "Show all trips on map" for any date range (a top Fuelio review request).

**F-07.8 GPX export** per trip or range.

**F-07.9 Business mileage report** (PDF, PRO; CSV free):
- Business trips in a period × reimbursement rate (₹/km, user-set).

**AC:**
- GPS trip distance is within ±3% of the vehicle odometer on a 20 km test drive (document test results).
- Recording continues with the screen off.
- No background location is requested in R1.

---

### F-08 Trip planner — PRO · R3

- **Inputs:** origin (current location default), destination, optional waypoints, vehicle, current fuel level (%), departure time.
- **Route options:** fastest / shortest / avoid tolls / avoid highways. Uses Google Routes API (paid per call; see §13).
- **Output report:**
  - Distance, ETA, fuel/energy needed
  - Estimated fuel cost, using state prices along the route where the feed has them, else the home-city price
  - Estimated tolls (manual entry or best-effort; no reliable public API)
  - Number of refuel/charge stops
- **Stop suggestions:**
  - Based on range = fuel level × capacity × efficiency, with a configurable buffer (default 15%).
  - Show fuel stations near each suggested stop from the offline station list (F-21.2), filtered by the vehicle's fuel type (CNG/EV handled). Sort by distance from the route. **No Google Places** (D12).
- **India add-on:** "Fill before the border" — when the route crosses a state boundary and the destination state's price is ≥₹1.5/L higher, suggest filling before crossing, with the ₹ saving estimate.
- Save a plan, then convert it to a trip when driving.

**AC:** One plan uses ≤ 1 Routes call; station lookups are offline. Results are cached for 24 h per origin/destination/options key.

---

### F-09 Fuel prices (India) — FREE · R1

**F-09.1 City prices.**
- Petrol, diesel and CNG (and premium petrol where available) for the user's home city, with day change and a 30-day sparkline.
- Pin up to 3 extra cities (travel/compare).

**F-09.2 Data feed:**
- The app downloads one compressed national JSON (all cities, all fuels, today plus 30-day history) at most once a day, after 06:30 IST (OMC revision time is 06:00).
- **No user location or identifier is sent** — the whole file is fetched and filtered locally.
- The feed is produced by a tiny serverless job (see §13 for source/licensing). The app works fully without it and falls back to the user's last paid price.

**F-09.3 "Did I pay fair?"** On fill-up save, if a city price exists for that date, show the delta ("₹0.42/L above city RSP").

**F-09.4 Nearby stations.** Button "Find a petrol pump" → Google Maps intent search filtered by fuel type ("CNG station near me"). Zero API cost.

**F-09.5 Price alerts (opt-in).** Notify when the home-city price changes by ≥₹1/L or kg.

**Note:** Metro prices have been largely flat for long stretches. The real value is **between states and cities**, and in CNG/EV tariffs. Don't over-invest in "daily" UI drama.

---

### F-10 FASTag and tolls — FREE · R1

- **F-10.1 FASTag account per vehicle (optional):** ✅ built (M6) issuer bank and tag ID (last 4 only).
- **F-10.2 Toll entry:** ✅ built (trip link not yet) expense category "Toll (FASTag)" or "Toll (cash)", with plaza name, amount, date-time and trip link.
- **F-10.3 Recharges:** ✅ built (M6; low balance notifies on the day a toll takes it under the threshold, then weekly) logged separately. They are transfers, not vehicle expenses, so they're excluded from cost/km. **Estimated balance** = Σ recharges − Σ FASTag tolls since the user's "balance anchor" (the user enters the current balance once). Labelled "est." with low-balance reminder (threshold user-set, default ₹300).
- **F-10.4 Share-to-app parser:**
  - The app registers as a share target for `text/plain`.
  - When the user shares a bank/FASTag SMS or text into the app, a parser extracts amount, plaza, date-time and vehicle reg, and pre-fills a toll or recharge entry for confirmation.
  - Ship parsers for the top issuers' formats (collect real samples first; see §13). Unknown formats fall back to amount-only extraction.
- **F-10.5 Statement import (R2):** CSV (and best-effort PDF) statement import from issuer portals, deduplicated by date + plaza + amount.

**Explicitly not allowed:**
- No `READ_SMS`/`RECEIVE_SMS`: Play restricts the SMS permission group to default SMS/Assistant handlers.
- No `NotificationListenerService` to read SMS notifications either: Play forbids using alternative methods to derive SMS data.

---

### F-11 Reminders — FREE · R1 ✅ built (M6: presets from assets/rules/india.json, date/odometer/both, daily WorkManager check around 09:30, Done/Snooze/Open actions, in-app banner without the notification permission; "Done" from a notification does not create an expense yet)

**F-11.1 Trigger types:** by date, by odometer, or both (whichever first). Can recur (every N km and/or N months).

**F-11.2 Presets** (one-tap, editable):

| Preset | Default rule |
|---|---|
| Insurance renewal | From policy expiry date; notify at 30/15/7/1 days |
| PUC renewal | Validity from emission norm + vehicle age; user-editable |
| General service | Every 10,000 km or 12 months (car); 3,000 km or 6 months (2W); user-editable |
| Engine oil | — |
| Tyre rotation | — |
| Battery check | — |
| Coolant | — |
| Wiper blades | — |
| RC renewal | 15 years for private vehicles |
| Fitness certificate | Commercial vehicles |
| Driving licence renewal | — |
| Extended warranty expiry | — |
| FASTag low balance | — |

> PUC validity rules are in flux. MoRTH published a draft in July 2026 proposing 3-year validity for BS-VI private vehicles under 6 years old. **Never hard-code legal validity.** Ship defaults as data (remote-config JSON shipped in-app, updatable via the price feed file) and always let the user edit the date.

**F-11.3 Evaluation:**
- Date reminders are scheduled with WorkManager (inexact, delivered around the user's preferred time, default 09:30).
- Odo reminders are evaluated on every new odometer reading and once daily against the **predicted** odometer. Notify when within 300 km (car) or 150 km (2W), or on the predicted date.

**F-11.4 Actions from the notification:**
- "Done" → logs a completion and optionally creates the expense
- "Snooze 3 days"
- "Open"

**F-11.5 Documents auto-reminders.** Saving a document with an expiry (F-12) auto-creates its reminder.

**AC:** Reminders survive reboot, app update and backup restore without duplicates. They work with notifications permission denied (shown in-app with a banner).

---

### F-12 Documents vault — FREE · R1 ✅ built (M6: Tink AES-GCM with a Keystore key, photo picker and PDFs, quick view with DigiLocker/mParivahan; camera scan waits for M10, Drive backup of documents for M7)

- **Types:** RC, insurance policy, PUC certificate, driving licence, FASTag, extended warranty/protection plan, service invoice, loan papers, other.
- **Fields:**
  - Number, issuer, issue date, expiry date, notes, front/back photos or PDF.
  - Insurance extras: insurer, policy type (comprehensive / third-party / own-damage), IDV, NCB %, add-ons (free text).
- **Storage:** app-private storage, encrypted at rest (Jetpack Security / Tink AEAD with an Android Keystore key).
- **Quick view:** large card for showing at a checkpoint.
  - The UI must state that legally valid digital documents are those in **DigiLocker / mParivahan**. Ours is a personal reference copy.
  - Deep-link buttons to open those apps if installed.
- **Backup:** included in Drive backup only if "Encrypt backups" (F-14.5) is on. Otherwise documents stay device-only and the user is told so.
- **No selling.** Insurance renewal is a reminder only. Any future "renew now" lead-gen requires an IRDAI-registered intermediary partner (see §13).

---

### F-13 Reports, import and export — FREE (PDF reports PRO) · R1

**F-13.1 CSV export:** all data or a filtered range, one file per entity, zipped. Schema documented in `docs/csv-schema.md`.

**F-13.2 CSV import:** ✅ built (Fuelio preset + generic mapper + Excel template; Drivvo and Simply Auto presets not yet, they wait for D9 samples)
- Generic CSV with a column mapper (remembers mappings).
- One-tap presets: **Fuelio CSV**, Drivvo CSV, Simply Auto CSV. Collect real sample exports before building (see §13).
  - *Decided 18 Oct 2026:* Fuelio is built from its documented export layout without a real sample. Columns are found by header name, and the generic mapper is the fallback. Drivvo and Simply Auto go through the generic mapper until samples exist.
- Dry-run preview: counts, conflicts and unit detection. Import is undoable (batch ID).
- **Single entry point:** one "Import data" screen (Garage, and onboarding "Bring your history") recognises the file: the Fuelwise template, a Fuelio CSV, or any other CSV/.xlsx (column mapper).
- **Bulk-entry Excel template (.xlsx):** written and read by the app itself, with no spreadsheet library. Fuel and Expenses sheets have dropdowns (the user's vehicles, fuels, all expense categories, payment methods, yes/no), a "How to fill" sheet and a hidden marker sheet. Headers and lists follow the app language; either language imports. Layout in `docs/import-template.md`.
- Duplicates are skipped: same vehicle and day, plus the same odometer and quantity for a fill-up, or the same category and amount for an expense. Unknown expense categories go under Other, with the original name kept in the notes. Up to 10,000 rows per file.

**F-13.3 PDF reports (PRO):**
- **Monthly/yearly vehicle report**
- **Seller's report:** service history, km and documents — prices hidden, Fuelio parity
- **Business mileage report** (F-07.9)

FREE users can generate a text/CSV version of each.

**F-13.4 GPX export** (F-07.8).

---

### F-14 Backup and sync (Google Drive) — FREE backup R1 · PRO multi-device sync R2 ✅ built R1 scope (M7: Credential Manager + AuthorizationClient, Drive REST v3 app folder over OkHttp, gzip JSON snapshots with every table and tombstones, Argon2id + AES-256-GCM, content-addressed attachments with HMAC names, retention 10 + monthly, WorkManager schedules and constraints, local backup file. Restore is one Room transaction rather than a temp-file swap: same all-or-nothing result without restarting the app. "Export a visible copy to Drive" with drive.file is not built; multi-device sync is R2)

- **F-14.1 Storage location:**
  - The user's own Google Drive, **appDataFolder** (scope `drive.appdata`, classified non-sensitive by Google → basic OAuth verification only, no security assessment).
  - It's a hidden app folder; the user doesn't see the files in Drive. The settings screen says so, and offers "Export a copy to Drive (visible)" using `drive.file` via the system picker if the user wants a visible file.
- **F-14.2 Sign-in:** Credential Manager "Sign in with Google" for identity, then `AuthorizationClient` to request the `drive.appdata` scope. No app account, no backend.
- **F-14.3 Frequency (user setting):**
  - After every change (debounced 2 min)
  - Daily (default)
  - Weekly
  - Manual only
  - Constraints: Wi-Fi only (default off), while charging (default off).
  - Implemented with WorkManager unique periodic + one-time work.
- **F-14.4 Snapshot format:**
  - `snapshot-<utc>-<deviceId>.json.gz`, schema-versioned: all entities plus settings, no derived data.
  - Attachments uploaded separately, content-addressed by SHA-256, so they upload only once.
  - Keep the last 10 snapshots plus monthly snapshots for 12 months.
- **F-14.5 Encrypt backups (optional):**
  - Passphrase-derived key (Argon2id) encrypts snapshots and attachments with AES-256-GCM.
  - Required to back up the documents vault.
  - **If the passphrase is lost, the backup is unrecoverable** — explicit warning, and the user confirms by re-typing.
- **F-14.6 Multi-device sync (PRO, R2):**
  - Every row has a UUIDv7 `id`, `updatedAt` (UTC ms), `deviceId` and `deletedAt` (tombstone).
  - Devices upload change-sets (`changes-<deviceId>-<seq>.json.gz`) and merge others' change-sets with **row-level last-writer-wins** (tie-break on deviceId).
  - Compaction: a full snapshot every 200 change-sets.
  - Conflicts are logged, not shown, unless a row is edited on two devices within 60 s.
- **F-14.7 Restore:**
  - On first run, after sign-in, detect existing backups and offer restore (latest or choose a date).
  - Restore is atomic: write to a temp DB, validate, swap.
- **F-14.8 Status UI:** last successful backup time, size, next scheduled run, error with a fix action (re-auth, storage full, offline).
- **F-14.9 Local backup:** "Save backup file" to any folder via SAF, for users who refuse Google sign-in.

**AC:**
- Kill the app mid-upload and the next run completes.
- Restore on a second device produces identical stats (golden check: totals, efficiency average, cost/km).
- 5 years of data for 3 vehicles (≈1,500 entries, 300 photos) backs up in < 60 s on 4G after the first run.

---

### F-15 Scanning (on-device OCR) — FREE · R1

- **Pump meter scan:**
  - Camera frame on the dispenser display.
  - ML Kit Text Recognition (on-device, offline) extracts the three numbers (amount ₹, volume L/kg, rate ₹/unit).
  - Validate with the price triangle (|q × p − t| ≤ ₹1) before auto-filling. Otherwise ask the user to confirm the fields individually.
- **Receipt scan:** extract date, total, quantity, rate and station name (Fuelio Pro parity, free here).
- **Odometer scan:** read the cluster's odometer digits; pick the longest digit run ≥4 digits near the "km" glyph; always confirm.
- The photo is attached to the entry (optional, setting).

**AC:** On a set of 50 real pump-display photos (collect before tuning), ≥85% auto-fill all three fields correctly. All processing is on-device, verified with network disabled.

---

### F-16 Widgets, shortcuts and notifications — R1

| Surface | Tier |
|---|---|
| Quick Add widget (2×1): Fuel / Charge / Expense / Trip | FREE |
| Status widget (2×2): ₹/km this month, last efficiency, next due item | FREE |
| Trip controls widget | FREE |
| Dashboard widget (4×2): spend, efficiency trend sparkline, range left | PRO |
| Consumption widget | PRO |

Other surfaces:
- **Quick Settings tile:** "Log fuel", "Start trip".
- **App shortcuts** (long-press icon): Add fuel, Scan pump meter, Start trip, Documents.
- **Notification channels:** Reminders, Trip recording, Automatic trips (R2), Fill-up prompts (R2), Backup status, Price alerts, Weekly digest, Monthly recap. Each is individually toggleable.
- **Quick Settings tile (R2):** "Pause automatic trips".

Built with Jetpack Glance.

---

### F-17 Recap, Wrapped and milestones — FREE · R1 (Wrapped in R2, timed for December) ✅ built (M5: recap card with share and save, milestones; the 1st-of-month recap notification arrives with notifications in M6)

- **Monthly Recap** (1st of month notification, opt-in): spend, km, ₹/km, best tank, change vs last month. Rendered as a shareable 9:16 image card. No personal data on the card by default; the vehicle name is optional.
- **Drive Wrapped** (yearly, December): 5–6 story cards with km driven, ₹ spent, best efficiency month, most visited station, longest trip, CO₂ estimate.
- **Milestones** (earned, not streaks): first 1,000 km logged, best-ever tank, 1 year of logging, 10,000 km. Shown once as a subtle sheet.

---

### F-18 Settings, units and localisation — R1

- **Units:**
  - Defaults: km, L, kg (CNG), kWh, km/L, km/kg, km/kWh.
  - Alternatives: L/100km, mi, gallons, etc.
  - Settable per vehicle.
- **Currency:** INR default. Currency stored per entry for future-proofing.
- **Number format:** Indian grouping (₹1,23,456.78). Dates `16 Oct 2026`. 12/24-hour follows the system.
- **Languages at launch:** English (India), Hindi. R2: Tamil, Telugu, Kannada, Marathi, Bengali.
- **Theme:** light / dark / system; optional Material You dynamic colour (off by default to preserve brand).
- **Privacy:**
  - App lock (BiometricPrompt).
  - Hide amounts on Home (privacy blur).
  - "Delete all data", with a sub-option to delete Drive backups too.
- **Home city** for prices; **home charging tariff**; **UPI VPA** for trip split.

---

### F-19 Pro and billing — R1

- Google Play Billing.
- **Products** (prices are a proposal, see §13):
  - Monthly ₹49
  - Yearly ₹299 with a 7-day free trial
  - Lifetime ₹999 (one-time)
- **Paywall rules:**
  - Shown only when the user taps a PRO feature, or from Settings → Pro.
  - Never at launch, never as an interstitial.
  - States what's free forever.
- Restore purchases; Pro status cached offline (grace 7 days); entitlements verified with Play Billing on device. No server in R1; accepted risk.
- **No ads, ever.** It's a positioning commitment, so it's written into the store listing.

---

### F-20 Android Auto — Later
Point-of-interest category app (fuel stations with prices). Blocked on station-level data. Revisit after the crowdsourcing decision (§13).

---

### F-21 Location intelligence — FREE · R1 (F-21.1–21.2) / R2 (F-21.3–21.7)

Decisions behind this section: D11 (free), D12 (OpenStreetMap + history only, no Google Places), D13 (automatic trips are the app's single declared background-location feature).

**F-21.1 Station auto-detect when logging (R1)**
- **Trigger:** the Add fill-up or Add charge sheet opens.
- **Location:** request a single current location (balanced priority, 10 s timeout, max age 2 min).
  - Needs while-in-use location only. No background permission.
  - If permission is denied or the fix times out, the station field stays empty and editable; logging is never blocked.
- **Matching:** within 150 m (widen to 300 m if nothing is found), rank candidates by:
  1. Stations the user has used before (strong boost)
  2. Distance
  3. Fuel-type fit: CNG vehicle → stations tagged with CNG; EV → charging points
- **UI:**
  - Pre-select the top candidate as a chip ("HP · Indiranagar").
  - Show up to 2 alternatives plus "Other / type name".
  - GPS in dense Indian streets can be 20–50 m off, and pumps often face each other across a road, so the alternatives are always shown.
- **Learning:** a user's correction is stored as a personal station record (name, brand, location). It wins over the station list for that spot next time.
- **Brand-aware defaults:** the station's brand pre-selects the premium-grade chip label (IOCL → XP95, BPCL → Speed, HPCL → Power). The default grade itself doesn't change.
- **Back-dated entries:** when the entry time is more than 30 minutes in the past, don't use the current location. If an automatic trip (F-21.3) has a stop near that time, use the stop's location; otherwise leave the field empty.

**AC (F-21.1)**
- The station chip appears within 2 s of the sheet opening on a mid-range device with a GPS fix available.
- The correct station is pre-selected in ≥80% of cases on a 30-fill test in Bengaluru, and is within the 3 shown options in ≥95%.
- Works with mobile data off (offline list).

**F-21.2 Offline station list (R1)**
- **Source:**
  - OpenStreetMap features tagged `amenity=fuel` and `amenity=charging_station` within India.
  - Fields kept: name, brand, operator, fuels (e.g., `fuel:cng`, `fuel:diesel`), location.
- **Packaging:**
  - Compact on-device file, bundled in the APK and refreshed monthly through the same static public feed as fuel prices (F-09.2).
  - The whole India file is downloaded, so no location is sent.
  - Target < 3 MB compressed. Measure before committing.
- **Licence:**
  - OpenStreetMap data is under ODbL. Show "© OpenStreetMap contributors" in the station picker's info sheet and in Settings → About.
  - Keep the attribution and licence text in `assets/licenses/`.
- **Coverage risk:** Indian coverage in OSM is uneven, so personal history (F-21.1) carries the weight early on.
  - Before build, audit Bengaluru: compare OSM stations against 30 known pumps and record the hit rate in `docs/osm-coverage.md`. Repeat for Delhi and Mumbai before launch.
  - The user can submit nothing back to OSM from the app in R1. Contributing fixes upstream is a Later item.

**F-21.3 Automatic trips (R2, FREE)**
- **Opt-in:** off by default. Enabled from a dedicated setup screen (design.md screen 32) that shows Play's required prominent disclosure **before** any permission prompt.
- **Start signals** (any one):
  1. Android activity detection reports the user started travelling in a vehicle.
  2. The phone connects to the vehicle's paired Bluetooth (cars), set per vehicle. Uses Companion Device Manager, which is allowed to start foreground services from the background. Note: starting the service is not the same as reading location — reading location while the app is in the background still needs `ACCESS_BACKGROUND_LOCATION`.
- **Starting the trip:** a start signal launches the trip recorder as a foreground service (`foregroundServiceType="location"`) with an ongoing notification.
  - Android allows this background start when triggered by an activity-recognition transition event.
- **During the trip:**
  - Location every ~5 s or ~25 m, high accuracy.
  - Delivered to the app in batches (max delay 60–120 s) so the CPU rarely wakes.
  - Points written to the database on every batch, which also gives crash resilience (F-07.3).
- **Stop signals:**
  - Leaving the vehicle (activity transition), or no movement > 50 m for 5 minutes, or Bluetooth disconnect.
  - Trips shorter than 500 m or 2 minutes are discarded.
- **Vehicle assignment:**
  - Bluetooth match → that vehicle.
  - Otherwise, the user's default vehicle for the time-of-day pattern (learned from history).
  - Otherwise, the default vehicle.
  - The user can re-assign from the trip card. Two-wheeler and car trips are told apart only by Bluetooth or user correction. Don't guess from speed.
- **Outside trips:** zero location requests.
- **Controls:**
  - A "Pause automatic trips" quick-settings tile and Home toggle: for 1 hour, until tomorrow, or until turned back on.
  - Trips recorded while the user was a passenger can be deleted or marked "Not my drive"; that feedback isn't used for anything else.
- **Phone-maker battery savers:** see F-21.7.

**AC (F-21.3)**
- On 10 test drives, ≥9 trips start within 2 minutes of departure and end within 3 minutes of arrival, on Pixel, Samsung, and one of Xiaomi/Realme/Vivo.
- Distance within ±3% of the odometer on a 20 km drive.
- No location request is active when no trip is recording (verified in `dumpsys location`).
- Battery: record drain per hour of driving and per day of no driving on 3 devices. Daily drain with no driving must be indistinguishable from the app being uninstalled. Log results in `docs/perf.md`.

**F-21.4 Fill-up detection prompt (R2, FREE)**
- **Detection:** during an automatic trip (or at its end), a stop of 2–15 minutes within 40 m of a station from F-21.1/F-21.2 is a **likely fill-up**.
- **Prompt:** one notification after the trip ends: "Filled up at HP, Indiranagar? Tap to log." It opens the Add fill-up sheet pre-filled with:
  - Station, time and vehicle
  - The estimated odometer (F-21.5) as a hint, never as a value
- **Never auto-saves.** The notification is dismissable, with a "Not a fill-up" action.
- **Learning:**
  - "Not a fill-up" on the same station 3 times → stop prompting for that station.
  - Prompts are skipped when a fill-up was already logged for that vehicle within ±2 hours.
- **Rate limit:** at most 1 prompt per vehicle per 6 hours.

**AC (F-21.4)**
- On a 4-week internal dogfood, ≥70% of actual fill-ups get a prompt, and ≤1 false prompt per user per week.

**F-21.5 GPS odometer estimate (R2)**
- Between two typed odometer readings, sum automatic-trip distances to estimate today's odometer.
- Calibrate against the next typed reading: store a per-vehicle correction factor (GPS km vs odometer km).
- Used for: the odometer hint in F-02.3, odometer-based reminders (F-11.3), range left.
- **A typed odometer always wins.** The estimate is never written as an entry.

**F-21.6 Location privacy and retention (R2)**
- **Storage:** raw trip points stay on the device and are encrypted at rest (same key scheme as F-12).
- **Retention:** default "keep raw routes for 90 days, then keep only trip summaries" (distance, duration, start/end area, cost). Options: 30 days / 90 days / 1 year / forever.
- **Backup:**
  - Trip summaries are backed up with everything else.
  - Raw routes are backed up only if "Encrypt backups" (F-14.5) is on.
- **Places that are never recorded (optional):** the user can mark home or other spots; points within 200 m are trimmed from route ends so the start/end of every trip doesn't reveal exact addresses in shared GPX/maps.
- **Delete:** "Delete all location history" in Settings, separate from "Delete all data".

**F-21.7 Phone-maker battery-saver guidance (R2)**
- Many Android phones sold in India stop background apps aggressively, which kills automatic trips.
- **Guidance:** during automatic-trip setup, detect the manufacturer and show brand-specific steps (with a deep link to the battery-optimisation settings screen where one exists).
  - Use the system settings intent; do **not** declare `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` without first checking Play's acceptable-use policy for it.
- **Health check:** if activity detection hasn't delivered any event for 3 days while the user has logged fill-ups (so they're clearly driving), show a Home banner: "Automatic trips may be blocked by battery saver — fix it".

---

## 6. Free vs Pro summary (proposal)

**Free forever:**
- Unlimited vehicles; fuel/EV/CNG logs; expenses
- Consumption and cost engine; all core stats and charts
- Reminders; documents vault; Drive backup on schedule
- CSV import/export; GPS and manual trips; trip split
- City fuel prices; FASTag log and parser; OCR scanning
- Station auto-detect (R1); automatic trips, fill-up detection prompts and GPS odometer estimate (R2)
- Monthly recap; quick widgets; themes; app lock

**Pro:**
- Multi-device sync (R2)
- Trip planner with stop suggestions and border-price tips (R3)
- Advanced insights (fuel-grade, CNG saving, EV vs petrol, forecasts, vehicle compare)
- PDF reports (seller, business mileage, yearly)
- Dashboard and consumption widgets; combined trips map
- Custom accent colours and app icons

Rationale: never paywall the user's own data or safety (reminders, backup), or anything that makes logging happen (scan, station detect, fill-up prompts) — logging consistency is the North-star. Charge for intelligence, convenience, and anything with a per-call API cost.

---

## 7. Data model (Room)

All IDs are UUIDv7 strings. Money is `Long` in **paise**. Distances are `Double` km. Quantities are `Double` in the canonical unit (L, kg, kWh). Timestamps are `Long` epoch ms UTC plus a `zoneId` string. Every syncable table has `createdAt`, `updatedAt`, `deviceId` and `deletedAt?`.

| Entity | Key fields |
|---|---|
| `vehicle` | id, name, type, make, model, variant, year, regNo, emissionNorm, purchaseDate, purchaseOdo, purchasePricePaise, claimedEfficiency, photoAttachmentId, accentColor, archived, sortOrder |
| `tank` | id, vehicleId, fuelType, defaultGrade, capacity, unit, isPrimary, usableBatteryKwh (EV) |
| `refuel` | id, vehicleId, tankId, dateTime, zoneId, odometer, quantity, unitPricePaise, totalPaise, discountPaise, paidPaise, isFull, missedPrevious, grade, stationName, stationBrand, lat, lng, paymentMethod, priceSource, notes, socStart, socEnd, chargerType, chargeDurationMin, operator |
| `expense` | id, vehicleId, categoryId, date, odometer?, amountPaise, paymentMethod, notes, tripId?, tollPlaza?, recurrenceId?, isAutoCreated |
| `expense_item` | id, expenseId, label, kind (part/labour), qty, amountPaise |
| `category` | id, name, icon, color, isSystem, isHidden, includeInCostPerKm |
| `recurrence` | id, vehicleId, template (json), every, unit, nextDue, endDate? |
| `trip` | id, vehicleId, startTime, endTime, startOdo, endOdo, distanceKm, purpose, notes, polylineAttachmentId?, estCostPaise, status (recording/paused/done/interrupted) |
| `trip_point` | tripId, t, lat, lng, accuracy, speed (transient; compacted into polyline on finish; raw kept per F-21.6 retention) |
| `trip` (extra fields) | source (manual / gps / auto), startSignal (activity / bluetooth / user), notMyDrive |
| `trip_stop` | id, tripId, startTime, endTime, lat, lng, stationRef?, likelyFillUp, promptState (none / shown / logged / dismissed / notFillUp) |
| `station_osm` | osmId, name, brand, operator, fuels (bitset), lat, lng, datasetVersion (reference data, **not synced**; read-only bundled DB or file) |
| `station_user` | id, name, brand, lat, lng, fuels, osmIdOverride?, useCount, lastUsedAt, suppressFillUpPrompt (syncable) |
| `vehicle` (extra fields) | bluetoothDeviceAddress? (via Companion Device Manager association id), autoTripEnabled |
| `reminder` | id, vehicleId, type, title, dueDate?, dueOdo?, repeatEveryKm?, repeatEveryMonths?, notifyOffsets, lastDoneAt?, snoozedUntil?, documentId? |
| `document` | id, vehicleId?, type, number, issuer, issueDate, expiryDate, meta (json), encrypted attachment ids |
| `attachment` | id, ownerType, ownerId, mime, sha256, sizeBytes, localPath, uploaded |
| `fastag_account` | id, vehicleId, issuer, tagLast4, balanceAnchorPaise, anchorDate, lowBalanceThresholdPaise |
| `fastag_recharge` | id, fastagAccountId, date, amountPaise, notes |
| `fuel_price_cache` | cityId, date, fuelType, pricePaise (reference data, **not synced**) |
| `sync_state` | deviceId, lastSnapshotAt, lastChangeSeq, remoteCursor (json) |

Settings live in DataStore (Proto), are included in the backup snapshot and excluded from sync merges except an allow-list.

---

## 8. Non-functional requirements

| Area | Requirement |
|---|---|
| Performance | Cold start < 1.5 s on a mid-range device (Snapdragon 6-series / Dimensity 7000-class, 6 GB RAM). Add-fill sheet opens < 150 ms. Baseline Profiles shipped. |
| Size | Download < 20 MB (ML Kit text model bundled for offline use is ~4 MB; prefer the bundled model for reliability). |
| Offline | 100% of logging, stats, reminders and documents work offline. Network is needed only for backup, prices, maps and planner. |
| Privacy | No analytics SDK that sends user content. Crash reporting is **opt-in** (see §13). Data safety form: "No data collected" if crash reporting is off by default; otherwise declare crash logs. |
| Security | Documents encrypted at rest; app lock; backups optionally encrypted; no secrets in the APK beyond restricted Maps SDK / Routes API keys (Android app restriction + API restriction). |
| Battery | Trips (manual or automatic): high accuracy at ~5 s / ~25 m with batched delivery (60–120 s). Zero location requests outside trips. Measure drain on 3 devices (incl. one Xiaomi/Realme/Vivo) per release and keep a log in `docs/perf.md`. |
| Accessibility | TalkBack labels on every interactive element; 48 dp targets; WCAG AA contrast; works at 200% font scale; charts have a data-table alternative. |
| Reliability | Crash-free sessions ≥ 99.5%. Zero data-loss bugs tolerated; every migration has a test. |
| Compatibility | minSdk 26 (Android 8.0). targetSdk = the current Google Play requirement at release. |

---

## 9. Play Store compliance checklist

| Item | Decision |
|---|---|
| `ACCESS_FINE/COARSE_LOCATION` | While-in-use only in R1 (station auto-detect, fill-up tagging, manual GPS trips via foreground service) |
| `ACCESS_BACKGROUND_LOCATION` | R2 only, for **one declared feature: "Automatic trip log and fill-up detection"** (F-21.3/21.4, D13). Requires the Permissions Declaration Form, a ≤30 s demo video showing the disclosure, the permission prompt and the feature working in the background, and the disclosure in the store description. Off by default. Submit for review early with a closed-test build — expect a possible rejection round. Any new background-location use later needs a new declaration. |
| `ACTIVITY_RECOGNITION` (runtime, Android 10+) | R2, automatic trips only. Asked on the automatic-trip setup screen after the disclosure. |
| Foreground service type `location` + `FOREGROUND_SERVICE_LOCATION` | Declare in manifest and Play Console (Android 14+ FGS type declaration) |
| Companion Device Manager | R2, Bluetooth trip start. Declare `REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND` only if needed after testing; check policy first. |
| Battery optimisation | Open the system settings screen via intent. Don't declare `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` without checking Play's acceptable-use rules. |
| OpenStreetMap data | ODbL attribution in the station picker info sheet and Settings → About |
| `POST_NOTIFICATIONS` | Asked contextually when the first reminder is created |
| Exact alarms | **Not used.** WorkManager / inexact alarms only. |
| `CAMERA` | Asked on first scan |
| Photo access | Android Photo Picker. No broad storage permissions. |
| `BLUETOOTH_CONNECT` | R2, auto-trip only |
| SMS / call log / notification listener | **Never** |
| `SYSTEM_ALERT_WINDOW` | **Never** |
| Google OAuth | `drive.appdata` (non-sensitive) plus optional `drive.file`. Brand verification only. |
| Network endpoints (M7) | `www.googleapis.com` Drive v3 only (`/drive/v3/files`, `/upload/drive/v3/files`), with the user's own token, app folder only. No other user-data endpoint. |
| New libraries (M7) | Credential Manager, Google ID, Play services auth (sign-in and the Drive scope, in the stack), OkHttp (in the stack), BouncyCastle `bcprov` for Argon2id passphrase derivation (the stack names Argon2id; Tink has no Argon2), Secrets Gradle Plugin (CLAUDE.md). |
| Account deletion policy | No accounts, so it's not applicable. Still provide in-app "Delete all data, including Drive backups". |
| Target audience | 18+ (driving); not designed for children |
| Store listing | No competitor names in title or short description. "No ads. Your data stays with you." |

---

## 10. Release plan

| Release | Scope | Exit criteria |
|---|---|---|
| **R1 — Launch** | F-01–F-07 (no automatic trips), F-21.1–21.2 (station auto-detect, offline station list), F-09, F-10.1–10.4, F-11, F-12, F-13, F-14 (backup, restore, encrypt; no multi-device), F-15, F-16, F-17 (monthly recap, milestones), F-18 (EN + HI), F-19 | Golden tests green; closed test with 20+ real users for 14 days (Play requires a closed test for new personal developer accounts — verify current rule); median fill-up time ≤10 s in usability tests |
| **R2** | F-21.3–21.7 automatic trips, fill-up detection, GPS odometer estimate, location privacy, battery-saver guidance; F-14.6 multi-device sync; F-10.5 statement import; combined trip map; regional languages; Drive Wrapped (December) | Sync soak test: 3 devices × 2 weeks, zero lost rows. **Play background-location approval received.** F-21.3/21.4 ACs met. |
| **R3** | F-08 trip planner (Routes API; stations from the offline list), border-price tips | Routes API cost per Pro user/month within budget (§13) |
| **Later** | Station-level crowdsourced prices (needs backend + moderation), Android Auto POI app, OBD-II Bluetooth (fuel level, real odometer), on-device LLM Q&A over own data, iOS | Decided after R2 metrics |

---

## 11. Monetisation (proposal)
- Freemium with Pro subscription and lifetime; **no ads**.
- **India pricing proposal:** ₹49/month, ₹299/year (7-day trial), ₹999 lifetime. Fuelio has no lifetime option — the lifetime SKU is a deliberate differentiator for subscription-fatigued users.
- **Expected conversion is low** (utility apps typically convert low single-digit % of actives). Treat Pro as covering costs and signalling quality, not as a business on its own. The real upside, if any, is a later India-specific B2B2C play (fleet / reimbursement reports, OEM/insurer partnerships) — out of scope here.

---

## 12. Risks and the weakest assumptions

1. **"Users will log consistently."** This is the biggest risk; every number depends on it. Mitigations: OCR scan, price-triangle defaults, odometer prediction, the post-save reward, widgets, and reminders that pull users back. Measure the North-star (§3) from week 1 via an opt-in, on-device-aggregated metric only.
2. **City price data licensing.** A free, licensed, city-wide official feed may not exist. Third-party APIs have patchy coverage (one checked API showed only Delhi diesel populated). If no clean source exists, R1 ships with "your price" only plus a manual-entry city price, and F-09 moves to R2.
3. **OCR accuracy on Indian pump displays** (7-segment LEDs, glare). Mitigation: collect 50+ photos before building, and always offer a confirm step.
4. **Bi-fuel efficiency honesty.** Users want a precise CNG km/kg; the physics of shared distance won't give it. Keep cost/km as the hero metric.
5. **Park+/CarInfo add a cost tracker.** Plausible. Our defence is privacy (no account, no data harvest), speed, and accuracy, not feature count.
6. **Play rejects the background-location declaration.** Play's policy text singles out passive route tracking and start/stop-driving detection for close review. Mitigation: one clearly scoped declared feature (D13), a clean disclosure and video, early submission. Fallback if rejected: no background location at all. When the car's Bluetooth connects (or driving is detected), post a "Start trip?" notification; tapping it starts the recorder with while-in-use access (user interaction with a notification is an allowed start). Fill-up prompts then work only on trips started this way. Manual trips (R1) never depend on approval.
7. **Phone-maker battery savers kill automatic trips** on popular Indian phones. Mitigation: F-21.7 guidance and health check; the AC requires testing on at least one such phone.
8. **OpenStreetMap station coverage in India is thin in places.** Mitigation: personal station history learns fast (most people use 2–4 pumps); coverage audit before build (F-21.2).
9. **Scope.** R1 as written is large for one builder. If time-boxed, cut in this order: F-17 → F-16 Pro widgets → F-10.4 parser → F-09.

---

## 13. Open decisions (owner: Swastik)

| # | Decision | Options / recommendation |
|---|---|---|
| D1 | Product name and brand | Placeholder "Fuelwise". Needs name + trademark + Play search. |
| D2 | City fuel price source | (a) Licensed commercial API → our daily job → static JSON on a CDN. (b) PPAC public datasets (metros only). (c) Manual-only in R1. **Rec: evaluate (a) for coverage and terms; fall back to (c).** Do not scrape OMC sites without checking their terms. |
| D3 | Serverless host for the price feed | Cloudflare Worker + R2, or a Supabase Edge Function + Storage. Only public data passes through it. |
| D4 | Pricing | ₹49 / ₹299 / ₹999 proposed. Validate with 10 target users. |
| D5 | Multi-device sync: Free or Pro? | Rec: Pro. Single-device scheduled backup stays free. |
| D6 | OCR scanning: Free or Pro? | Rec: Free (it's the acquisition hook); Fuelio charges for it. |
| D7 | ✅ **Decided (17 Oct 2026):** crash reporting | **None in R1.** No crash SDK; the privacy-screen toggle is hidden until a provider is chosen. Revisit before the closed test. |
| D8 | Maps provider for trip/fill-up maps | Google Maps SDK (familiar, free map loads on Android SDK) vs MapLibre + OSM tiles. Rec: Google Maps SDK for map display; Routes API only for the R3 planner. Places is not used (D12). |
| D9 | Sample data collection | Fuelio/Drivvo/Simply Auto CSV exports; FASTag SMS formats from SBI, ICICI, HDFC, Axis, Airtel Payments Bank, Paytm (if still issuing), IDFC; 50 pump-display photos. Required before building F-13.2, F-10.4, F-15. |
| D10 | Insurance lead-gen in future | Out of scope. Any "renew" CTA requires an IRDAI-registered intermediary arrangement; keep reminder-only. |
| D11 | ✅ **Decided (16 Oct 2026):** location features tier | Free. Station auto-detect, automatic trips, fill-up prompts and GPS odometer estimate are all free. |
| D12 | ✅ **Decided (16 Oct 2026):** station data source | OpenStreetMap (offline, bundled) plus the user's own history. No Google Places anywhere in the app. |
| D13 | ✅ **Decided (16 Oct 2026):** background location scope | Automatic trips (with fill-up detection) are the app's single declared background-location feature. No other background-location use without a new declaration. |
| D14 | OSM coverage audit (action, owner: Swastik) | Check 30 known pumps each in Bengaluru, Delhi, Mumbai against OSM before building F-21.2. If hit rate < 60%, consider seeding personal history from the first 2 fill-ups only and lowering the F-21.1 AC. |

---

## 14. Research notes (sources)

- Fuelio Play Store listing (feature list, "Pro features are free" legacy list, What's New: currency tool, smart bubble, widgets free/PRO, light theme, weather PRO, odometer prompt, receipt scanning), 5M+ downloads, 4.6★, updated Sep 2026 — https://play.google.com/store/apps/details?id=com.kajda.fuelio
- Fuelio site and FAQ (full-tank algorithm needs 2 full fills; bi-fuel; categories; permissions; prices) — https://fuel.io , https://fuel.io/faq.html , https://fuel.io/faq_fuel_consumption.html
- Fuelio Pro features (stations on route, route expense report, route settings, stop suggestions, custom vehicle values, station filter) — https://fuel.io/news_fuelio_pro_features.html
- Sygic press release, Pro launch June 2023, ~€9/yr, 3-day trial — https://www.sygic.com/press/fuelio-introduced-its-premium-cost-saving-features-for-planning-fill-ups-on-the-route-smarter
- App Store listing (units incl. kg/m³/kWh, Pro $2.99/mo, $14.99/yr) — https://apps.apple.com/app/id1487753318
- Trip Log changelog (GPS/manual, GPX, Bluetooth auto start, interrupted-trip restore, seller's report) — APKMirror changelogs
- Play SMS/Call Log permission policy (default-handler only; no alternative methods) — https://support.google.com/googleplay/android-developer/answer/16909972
- Drive API scopes (`drive.appdata` and `drive.file` non-sensitive) — https://developers.google.com/workspace/drive/api/guides/api-specific-auth
- MoRTH draft PUC validity changes (Jul 2026) — Autocar India, https://www.autocarindia.com/industry/govt-proposes-extending-pucc-validity-for-bs6-cars-from-1-to-3-years-440313
- Android for Cars supported categories (POI includes fuel) — https://developer.android.com/training/cars
- Park+ / CarInfo positioning — Play/App Store listings
- Starting foreground services from the background (activity recognition and geofence events exempt; background location access needs `ACCESS_BACKGROUND_LOCATION`) — https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- Activity Recognition Transition API — https://developer.android.com/guide/topics/location/transitions
- Location batching and background limits — https://developer.android.com/develop/sensors-and-location/location/battery/optimize
- Play background location policy (prominent disclosure wording, one declared feature, video) — https://support.google.com/googleplay/android-developer/answer/9799150
- OSM fuel stations: https://wiki.openstreetmap.org/Tag:amenity=fuel ; planned HP station import — https://wiki.openstreetmap.org/wiki/HP_fuel_station_import
