# Import: file formats (PRD F-13.2)

Everything comes in through one screen, **Garage → Import data** (also reachable from onboarding's "Bring your history"). The app recognises the file type itself.

| File | How it's recognised | What happens |
|---|---|---|
| Fuelwise template (.xlsx) | Hidden `_fuelwise` sheet with `fuelwise-template` in A1 | Goes straight to the preview |
| Fuelio CSV | `## Log` section plus `## Vehicle` or `## Costs` | Goes straight to the preview; the vehicle is preselected by name |
| Any other CSV or .xlsx | Anything else with a header row | Column mapper, then preview. The mapping is remembered per header row |

Code: `core/domain/.../importing` (reader, planner, template, xlsx), `core/data/.../ImportRepository.kt`, `feature/dataimport`.

## The template

"Download template" writes `fuelwise-template.xlsx`. Its sheets, in order:

1. **Fuel**: Vehicle · Date · Time · Odometer (km) · Fuel · Quantity (L, kg or kWh) · Price per unit (₹) · Total (₹) · Full tank · Missed previous fill-up · Station · Payment · Notes
2. **Expenses**: Vehicle · Date · Category · Amount (₹) · Odometer (km) · Payment · Toll plaza · Notes
3. **How to fill**: instructions, plus one line per column with an example
4. **Lists** (hidden): the dropdown sources, i.e. the user's vehicles, their fuels, visible categories, payment methods, yes/no
5. **_fuelwise** (hidden): marker and template version

Sheet names, headers and list values follow the app language. Import accepts English and Hindi labels whichever language is active, as well as common English aliases (e.g. "challan", "GPay", "tyre").

The first two sheets are read by position. Columns are matched by header text. If the headers were edited beyond recognition, the fixed column order above is used.

## Row rules

- **Vehicle**: matched by name or registration number, ignoring case, spaces and punctuation. If the cell is blank, or the file has no vehicle column, the row goes to the vehicle chosen on the preview.
- **Date**: Excel dates; `YYYY-MM-DD`; `DD/MM/YYYY`, `DD-MM-YY` or `DD.MM.YYYY` (or month first, picked in the mapper or detected from a day above 12); `16 Oct 2026`; `Oct 16, 2026`. A time can follow the date, or go in the Time column. With no time, the entry is placed at noon.
- **Numbers**: `₹`, `Rs`, Indian grouping (`1,23,456.78`) and a decimal comma (`1.234,56`) are all accepted. Negative values are rejected.
- **Fill-ups**: need the odometer and two of quantity, price per unit and total; the third is worked out (same rounding as the entry sheet). Full tank defaults to yes and missed-previous to no. The tank comes from the tank number (Fuelio), else the Fuel column, else the primary tank.
- **Expenses**: need an amount above zero. A blank category means Other. An unrecognised category is also filed under Other, with its name added to the notes.
- **Duplicates** (of existing entries or of earlier rows in the same file) are skipped. A fill-up is a duplicate if the vehicle, day, odometer (to the km) and quantity (to 0.01) all match. An expense is a duplicate if the vehicle, day, category and amount all match.
- **Limits**: 10,000 rows and 20 MB per file.

## Fuelio

Fuelio's sections are read by header name, so reordered or extra columns don't matter.

- **Log** columns: `Data`, `Odo`, `Fuel`, `Full`, `Price` (the total), `VolumePrice`, `Missed`, `TankNumber`, `City` (the station), `Notes`.
- **Costs** columns: `CostTitle` and `Notes` are joined into the notes, and `CostTypeID` is resolved to a name through `## CostCategories`.
- Reminder templates (`isTemplate=1`) are skipped. Income rows are listed as problems.
- Units are converted to km and litres: `DistUnit 1` means miles; `FuelUnit 1` means US gallons and `2` UK gallons.
- This parser was built from the documented layout without a real export (PRD D9). Check it against a real file when one is available.

## Undo

Each import is an `import_batch` row (Room v4). Each entry it created carries the batch's `importBatchId`. Undo tombstones the batch and every live entry in it, including entries edited since the import. Entries typed in by hand are never touched.
