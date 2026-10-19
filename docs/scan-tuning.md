# Scan tuning (PRD F-15)

How the on-device scan parsers work, what has been verified and what is still open.

## Pipeline

1. **Camera** (`feature/scan/ScanCamera.kt`): CameraX preview + `ImageAnalysis` (keep latest, ~3 frames/s).
   ML Kit Text Recognition with the **bundled** Latin model runs on each frame in the app process. Frames are not stored;
   the current frame is encoded as JPEG only if the user keeps the photo.
2. **Parsing** (`core/domain/scan`, pure Kotlin):
   - `OcrRows` groups recognised lines into visual rows by box overlap (labels and digits are often separate lines).
   - `OcrNumbers` finds numbers and undoes 7-segment/thermal-print confusions: O/o/D/Q→0, I/l/|→1, Z→2, S→5,
     G/b→6, T→7, B→8, g/q→9. It handles Indian grouping ("1,23,456"), decimal commas, a decimal point read as a gap
     ("2000 00"), and masks dates and clock times.
   - `ScanLabels` tags labels: AMOUNT/SALE/₹ (total), VOLUME/LITRE/KG (quantity), RATE/PRICE/₹/L (rate), and ignore
     words (DENSITY, TOTALIZER, NOZZLE, GSTIN…).
   - `TriangleReader` gives every number several readings (on pump LEDs a missing point usually means a dropped
     segment, so whole, 2-dp, 1-dp and 3-dp readings are tried). It then scores every amount/quantity/rate triple on
     label agreement, plausibility ranges and the **price triangle** (|q × p − t| ≤ ₹1). A triple that passes
     auto-fills. If none passes, only numbers a label vouches for are offered, unticked.
   - `ReceiptParser` adds the date (day-first, named months, ISO), time and the dealer name. `OdometerParser` picks
     the longest run of 4+ digits, favouring "km"/"ODO" rows and penalising TRIP/RANGE/DTE rows.
3. **Confirm** (`ScanViewModel`): the same passing reading in 2 frames in a row opens the confirm sheet with every
   field ticked. "Read now" opens it from the latest reading; values that didn't pass the triangle need a tick each.
   Nothing is saved on this screen. The values go to Add fuel, where the user saves as usual.

All weights and ranges are in `ScanTuning`.

## Verified

- Unit tests in `core/domain/src/test/.../scan` use hand-written fixtures (`ScanFixtures`): labelled and unlabelled
  displays, dropped points, O/S/B/l confusions, labels under the digits, density and totalizer noise, CNG in kg,
  boxed column layouts, three receipt layouts, and five odometer layouts.
- The fixture auto-fill rate is checked by `auto-fill rate across the fixture set` (target ≥85%).

## Open: the PRD AC needs real photos

The PRD AC says: "On a set of 50 real pump-display photos, ≥85% auto-fill all three fields correctly". That set
doesn't exist yet (PRD D9). To close it:

1. Collect 50+ photos of real dispensers (different OMCs and dispenser makers, day and night, glare). Include
   petrol, diesel and CNG.
2. Run them through ML Kit (e.g. a debug-only instrumented test reading from `androidTest/assets`) and dump each
   result as `OcrLine`s with boxes.
3. Add each dump to `ScanFixtures.pumpDisplays` with the true values. The accuracy test then measures the real rate.
4. Tune `ScanTuning` (and label vocabulary) until the rate is ≥85% without breaking the other fixtures.
5. Verify with network disabled (airplane mode) that scanning works end to end.

## Open: ML Kit's transitive telemetry

`com.google.mlkit:text-recognition` 16.0.1 depends on `mlkit:common`/`vision-common`. These pull in
`com.google.android.datatransport` (transport-api, transport-runtime, transport-backend-cct) and Firebase encoders,
which ML Kit uses for SDK usage logging. Recognition itself is offline, but this is a network path that CLAUDE.md
rules 5 and 7 require the owner to approve or remove. Options: accept it and list it in PRD §9, or exclude it and
verify that ML Kit still works. Not decided in this PR.
