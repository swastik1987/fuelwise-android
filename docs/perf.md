# Performance and field tests

Results the PRD asks to be recorded (PRD §8, F-07 AC, F-21.3 AC). Add a row per run; keep old rows.

## GPS trip distance (PRD F-07 AC: within ±3 % of the odometer on a 20 km drive)

Start the trip in Fuelwise, drive, stop, then compare the trip distance with the odometer difference.

| Date | Device (model, Android) | Route | Odometer km | GPS km | Error % | Screen off? | Notes |
|---|---|---|---|---|---|---|---|
| | | | | | | | |

## No location request outside a trip (CLAUDE.md rule 11)

After pressing Stop (and after Pause), run `adb shell dumpsys location` and check the "Location Request History" /
active requests list has nothing live for `in.fuelwise.app` (`.debug` for debug builds).

| Date | Device | After Pause | After Stop | Notes |
|---|---|---|---|---|
| | | | | |

## Battery during recording (PRD §8)

| Date | Device | Hours recorded | Battery used % | Screen | Notes |
|---|---|---|---|---|---|
| | | | | | |

Test on a Pixel, a Samsung and one of Xiaomi / Realme / Vivo (CLAUDE.md definition of done for location features).
