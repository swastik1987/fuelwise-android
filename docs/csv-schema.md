# Fuelwise CSV export — schema (PRD F-13.1)

**Export & reports** (Garage → Export & reports) writes one `.zip` file through the system file picker. It needs no storage permission. The zip holds one CSV file per entity. Code: `core:domain` `export/CsvExport.kt`.

## Format

- RFC 4180, comma-separated, CRLF line ends, UTF-8 **with a byte-order mark** (so Excel shows ₹ and Hindi text correctly).
- A field is quoted only when it holds a comma, a quote (doubled as `""`) or a line break.
- The first row is the header. Header names are part of the format: new columns go at the end, and no column is ever renamed or removed.
- **Money** columns end in `_inr`. They hold rupees with exactly two decimals (`3615.74`, `-0.05`), with no grouping and no ₹ sign. They are computed from stored paise with integer arithmetic.
- **Units** are canonical: km, L, kg, kWh (CLAUDE.md rule 2). The `unit` column says which one applies. Decimals use a dot. Trailing zeros are dropped. Odometer and distances are rounded to 2 decimals, quantities to 3.
- **Time:** a `*_utc_ms` column (epoch milliseconds, UTC) plus a readable local `date` (`yyyy-MM-dd`) and `time` (`HH:mm`, 24-hour), both in the row's own `zone_id`. Rows without a zone use the device zone.
- **Enums** use their stable code name (`PETROL`, `Upi`, `Business`, `Insurance`…), the same in every language. Category names follow the app language.
- **Booleans:** `true` / `false`. Empty field = no value.
- **Text the user typed** that starts with `=`, `+`, `-`, `@`, tab or CR gets a leading `'` so spreadsheets don't run it as a formula.
- **Left out:** deleted (tombstoned) rows, sync metadata, attachments and document files (they never leave the vault), raw GPS points, routes and app settings.

## Filters

- **Vehicles** (all, or a set) apply to every vehicle-owned file. Driver documents (no vehicle, e.g. a licence) are included only when all vehicles are.
- **Period** (all time, this month, last month, last 3 months, this year, last year) applies to dated history: `refuels`, `expenses`, `trips` (by start), `fastag_recharges`. Vehicles, tanks, categories, reminders, documents, FASTag accounts and recurrences are not dated, so they follow the vehicle filter only.
- With a period chosen and no dated rows in it, the app shows "Nothing to export" and doesn't open the file picker. Without a period, the same happens when there is nothing beyond vehicles, tanks and categories.

## Files and columns

### vehicles.csv
`id, name, type, make, model, variant, year, registration_number, emission_norm, purchase_date, purchase_odometer_km, purchase_price_inr, claimed_km_per_unit, is_hybrid, custom_km_per_unit, custom_price_per_unit_inr, custom_range_km, archived, start_odometer_km, start_odometer_utc_ms, start_odometer_date`

Archived vehicles are included (`archived = true`).

### tanks.csv
`id, vehicle_id, vehicle, tank, fuel, unit, capacity, is_primary`

`tank` is the tank's number on its vehicle: 1 is the primary tank, then the others. The importer uses the same numbering.

### categories.csv
`id, system_key, name, custom_name, hidden, include_in_cost_per_km, sort_order`

System presets have a fixed `id` and a `system_key`. `name` is what the user sees.

### refuels.csv — fill-ups and EV charges
`id, vehicle_id, vehicle, date, time, zone_id, date_time_utc_ms, tank, tank_id, fuel, odometer_km, quantity, unit, unit_price_inr, total_inr, discount_inr, paid_inr, full_tank, missed_previous, grade, station, payment, price_source, notes, soc_start_pct, soc_end_pct, charger_type, charge_duration_min, operator`

`paid_inr = total_inr − discount_inr`.

### expenses.csv
`id, vehicle_id, vehicle, date, time, zone_id, date_time_utc_ms, category_id, category, amount_inr, odometer_km, payment, toll_plaza, notes, trip_id, recurrence_id, auto_created`

### trips.csv
`id, vehicle_id, vehicle, start_date, start_time, end_date, end_time, zone_id, start_utc_ms, end_utc_ms, distance_km, start_odometer_km, end_odometer_km, purpose, custom_purpose, from_place, to_place, notes, est_fuel_cost_inr, source, status, recorded_seconds, idle_seconds, max_speed_kmh`

### reminders.csv
`id, vehicle_id, vehicle, kind, title, due_date, due_utc_ms, due_odometer_km, repeat_months, repeat_km, notify_days_before, snoozed_until_utc_ms, document_id, notes`

`notify_days_before` is a `;`-separated list of days, e.g. `30;7;1`.

### documents.csv — details only, never the files
`id, vehicle_id, vehicle, type, number, issuer, issue_date, issue_utc_ms, expiry_date, expiry_utc_ms, insurer, policy_type, idv_inr, ncb_percent, add_ons, remind_on_expiry, notes`

### fastag_accounts.csv
`id, vehicle_id, vehicle, issuer, tag_last4, anchor_balance_inr, anchor_date, anchor_utc_ms, low_balance_inr`

### fastag_recharges.csv
`id, account_id, vehicle_id, vehicle, date, time, zone_id, date_time_utc_ms, amount_inr, notes`

### recurrences.csv — recurring expenses
`id, vehicle_id, vehicle, category_id, category, amount_inr, every, unit, start_date, start_utc_ms, end_date, end_utc_ms, zone_id, payment, notes`

## Importing an export again (F-13.2)

`refuels.csv` and `expenses.csv` open in **Import data** like any other CSV. The column mapper fills itself from the headers: `vehicle`, `date`, `time`, `odometer_km`, `tank`, `fuel`, `quantity`, `unit_price_inr`, `total_inr`, `full_tank`, `missed_previous`, `station`, `payment`, `notes` for fill-ups, and `category`, `amount_inr`, `toll_plaza` for expenses. Rows already in the app are skipped as duplicates. Two limits:

- An expense comes back at noon on its date, because the expense importer reads no time.
- Discount, grade, EV fields and trip links are not imported.

`CsvExportTest` checks this round trip.

## Reports (F-13.3, free text/CSV)

Reports are shared as text or saved as a single CSV. Their headers follow the app language, because they are meant for people, not for re-import. Money is still plain rupees with two decimals.

- **Vehicle report** (month or year): `Item, Count, Quantity, Unit, Amount (₹)`. Rows: distance, then one per fuel, one per expense category, the total, and cost per km.
- **Seller's report:** `Section, Date, Odometer (km), Item`. Rows: odometer, then service history (Service, Repair, Tyres and Battery expenses), then documents with their expiry. No prices and no notes.
- **Business mileage** (F-07.9): `Date, Vehicle, From, To, Notes, Distance (km), Rate (₹/km), Amount (₹)`, plus a total row. Business trips that are finished and start in the period, × the user's rate (saved in preferences). Each amount is rounded to the paisa, and the total is the sum of the rounded amounts.

PDF versions are Pro and not built yet. They will render the same report models (`core:domain` `report/Reports.kt`).
