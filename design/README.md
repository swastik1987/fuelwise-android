# Design reference (Claude Design export)

Imported 17 Oct 2026 from Claude Design project `755843d6-19eb-4aab-b90f-7bc080f0c987`. Reference only: never ship these files.
Open any `.dc.html` in a browser (needs `support.js` next to it and internet for Google Fonts). Each board has light/dark and 412/360 toggles.

| File | Screens (design.md §7) |
|---|---|
| `Fuelwise 1 - Onboarding & Home.dc.html` | 01 Welcome · 02/02b Add vehicle · 03 History · 04 Privacy · 05 Home · 05b new vehicle · 05c privacy blur · 05d tracking pill, battery banner, pause sheet |
| `Fuelwise 2 - Logging.dc.html` | 06 Add fill-up · 06b bi-fuel/validation/More · 06c station chip states · 07/07b reward · 08 EV charge · 09/09b scan · 10 expense · 11 toll |
| `Fuelwise 3 - Logbook & Insights.dc.html` | 12 Logbook · 12b map · 12c empty · 13 entry detail · 13b formula sheet · 14 Insights · 15 chart detail · 16/16b/16c recap |
| `Fuelwise 4 - Trips.dc.html` | 17 Trips · 17b auto trips on · 18 Live trip · 19 Trip detail + split · 20 Planner (PRO) |
| `Fuelwise 5 - Garage.dc.html` | 21 Garage · 22 Vehicle detail · 23 Reminders · 23b Add reminder · 24 Documents · 24b quick view · 24c add document |
| `Fuelwise 6 - Utilities.dc.html` | 25 Prices · 26 FASTag · 27/27b/27c Backup · 28 Paywall · 29 Settings |
| `Fuelwise 7 - Widgets & Notifications.dc.html` | 30/30b widgets · 31 notifications |
| `Fuelwise 8 - App Icon.dc.html` | Gauge mark, adaptive layers, themed, Play 512 |
| `Fuelwise 9 - Automatic Trips & Stations.dc.html` | 32–32e disclosure + setup steps · 33/33b/33c battery saver · 34 station picker · 35 pre-filled sheet |
| `FwTabBar.dc.html`, `FwStatusBar.dc.html` | Shared bottom nav and status bar |

## Notes for implementation

Colour tokens, type scale and radii match `docs/design.md` §4 exactly. Where the mocks add to or differ from the spec:

- **Two primary button styles.** Accent fill for the screen's main action; **ink fill** (`ink` bg, `bg` text) for a second strong action when accent is already used (e.g. Continue, Save reminder, Find a pump, Start as trip). Add `InkButton` to `core:designsystem`.
- **Two selected-chip styles.** Filter/selection chips use `accent-soft` + accent text. In-sheet choice chips (grade, payment, charger, purpose, route options) use `surface-2` + a 1.5 dp `ink` inner outline.
- **Keypad keys are 54 dp** in the fill-up sheet (50 dp in sheet 35), not the 64 dp in design.md. Use 54 dp; still ≥ 48 dp.
- **Odometer cells:** 6 cells, 48 dp tall in sheets, 60 dp in onboarding; Geist Mono 26/30 sp; prediction shown in `ink-3`; "Use 42,318" chip in accent-soft.
- **Segmented control:** `surface-2` track, 3 dp padding, selected segment `surface` with 11 dp radius, 44 dp tall.
- **Bottom nav:** 72 dp row; icons `home`, `receipt_long`, `insights`, `garage_home`; FAB 64 dp, radius 20, raised 26 dp with a 6 dp `bg` ring.
- **Cards:** 24 dp radius, 1 dp `line` border in light, no border in dark (`--cb: transparent`).
- **Sheet scrim:** solid `--dim` (#8C8B87 light, #050506 dark) behind sheets.
- **Icons:** Material Symbols Rounded, weight 300 (filled for active nav). The mocks load it as a web font.
- **Logo mark:** open gauge arc (ink) + needle (accent). SVG paths are in file 8; use them for the adaptive icon, themed monochrome icon and notification small icon.
- **Sample data in mocks** includes a real-looking email and UPI ID; fixtures must use fictional values.
