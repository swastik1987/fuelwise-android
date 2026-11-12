# design.md — Fuelwise (working name)

UI brief for **Claude Design**. Android phone app, Material 3 foundations with a custom visual language.
Functional source of truth: `PRD.md` (feature IDs `F-xx` referenced below).

---

## 1. Brief

**What it is:** A calm, fast, beautiful logbook for what your vehicle really costs — petrol, diesel, CNG, EV, car or two-wheeler — built for India.

**Who it's for:** Indian vehicle owners aged 22–50 who care about money but hate spreadsheets. They're comfortable with UPI apps like CRED and Google Pay, and with tech-forward EV/scooter apps like Ather. They expect polish.

**How it should feel:**
- Like a **precision instrument**: quiet, confident, numbers first.
- The big number is the hero; everything else steps back.
- Think premium car instrument cluster meets a well-made finance app.
- **Not** a dashboard crammed with charts. **Not** gamified. **Not** an ad-supported super-app.

**Three words:** Precise · Calm · Rewarding.

**The one moment to nail:** Logging a fill-up in under 10 seconds, then seeing "This tank: **16.8 km/L** ▲4%" land with a satisfying micro-animation. Everything in the product serves that loop.

---

## 2. Design principles

1. **One hero number per screen.** Each screen answers one question with one big figure. Supporting data is quieter.
2. **Thumb-first.** Primary actions sit in the bottom 40% of the screen. Entry sheets rise from the bottom; inputs come with a custom numeric keypad.
3. **Reward honest effort.** After every log, give back insight immediately. Never shame missed entries.
4. **Show the math.** Every metric has a quiet "i" that reveals how it was computed. Estimates carry an "est." tag.
5. **Calm by default.** Hairlines over shadows, whitespace over boxes, one accent colour, motion only where it means something.
6. **India-native details.** ₹ with lakh grouping (₹1,23,456), kg for CNG, two-wheelers as first-class, Hindi without layout breakage.

---

## 3. Brand

- **Name:** "Fuelwise" is a placeholder. Keep the wordmark area swappable.
- **Logo direction:** abstract mark. A circle segment (gauge arc) with a single needle tick, or a droplet that is also a map pin, cut by one diagonal line.
  - Monochrome-first, accent-orange variant.
  - **Must not resemble** any existing fuel-app icon (especially Fuelio's).
- **Voice:**
  - Plain Indian English, short, numbers first, warm but never cute.
  - ✅ "₹4.82/km this month. 6% cheaper than September."
  - ❌ "Woohoo! You're crushing it! 🚀"
- **Hindi:** natural conversational Hindi, not literal translation. Keep units and ₹ amounts as numerals.

---

## 4. Design tokens

### 4.1 Colour

**Light theme**

| Token | Hex | Use |
|---|---|---|
| `bg` | `#F5F4F0` | App background (warm paper) |
| `surface` | `#FFFFFF` | Cards, sheets |
| `surface-2` | `#EEEDE8` | Inputs, chips, pressed states |
| `ink` | `#121212` | Primary text, hero numbers |
| `ink-2` | `#5C5C58` | Secondary text |
| `ink-3` | `#8E8E88` | Tertiary text, placeholders, axis labels |
| `line` | `#E4E2DC` | Hairline borders and dividers (1 dp) |
| `accent` | `#FF5B1F` | Primary actions, FAB, active nav, key highlights ("Ignition orange") |
| `on-accent` | `#FFFFFF` | Text on accent |
| `accent-soft` | `#FFE7DC` | Accent tints, selected chips |
| `positive` | `#1F9D55` | Improvements (better efficiency, lower cost) |
| `warning` | `#E8A317` | Due soon |
| `negative` | `#D93025` | Overdue, errors, worse metrics |

**Dark theme**

| Token | Hex |
|---|---|
| `bg` | `#0D0D0E` |
| `surface` | `#161618` |
| `surface-2` | `#1F1F22` |
| `ink` | `#F2F1ED` |
| `ink-2` | `#A6A5A0` |
| `ink-3` | `#6E6D69` |
| `line` | `#2A2A2E` |
| `accent` | `#FF6A33` |
| `accent-soft` | `#3A1E14` |
| `positive` | `#3CCB7F` |
| `warning` | `#F2B84B` |
| `negative` | `#FF6B5E` |

**Energy colours** (used only for data — chart series, fuel-type dots and tags; never for buttons):

| Fuel | Hex |
|---|---|
| Petrol | `#F2B705` |
| Diesel | `#3A6FF7` |
| CNG | `#18A866` |
| LPG | `#8E5CF6` |
| Electric | `#00B3C7` |
| Premium / other | `#E85D9E` |

**Rules:**
- Accent orange appears **at most twice per screen**: the primary action plus one highlight.
- Up/down deltas use the positive/negative colours plus an arrow glyph, so the meaning never relies on colour alone.
- Note: lower cost/consumption = positive (green); higher km/L = positive.
- All text meets WCAG AA. Check `ink-3` on `surface-2`; use `ink-2` if it fails.

### 4.2 Typography

- **UI:** Geist (SIL OFL). Fallback: Noto Sans Devanagari (Hindi), then the system font.
- **Numerals:** Geist with tabular figures (`tnum`) everywhere numbers align. Geist Mono **only** for the odometer roller and the pump-scan readout.

| Style | Size / line | Weight | Tracking | Use |
|---|---|---|---|---|
| `display-xl` | 64 / 68 | 600 | −3% | Home hero metric |
| `display` | 48 / 52 | 600 | −2% | Screen hero numbers, post-save reward |
| `h1` | 30 / 36 | 600 | −1% | Screen titles (rare; most screens use top-bar `h3`) |
| `h2` | 22 / 28 | 600 | −0.5% | Section titles |
| `h3` | 18 / 24 | 600 | 0 | Top-bar titles, card titles |
| `body` | 16 / 24 | 400 | 0 | Body |
| `body-s` | 14 / 20 | 400 | 0 | Secondary rows |
| `label` | 14 / 18 | 500 | 0 | Buttons, chips, inputs |
| `caption` | 12 / 16 | 400 | +1% | Meta, chart labels |
| `overline` | 11 / 14 | 600 | +6%, uppercase | Section overlines ("THIS MONTH") |

The unit suffix sits next to a hero number at **40% size**, `ink-2`, baseline-aligned: **16.8** km/L.

### 4.3 Spacing, radius, elevation

- **Spacing scale (dp):** 4, 8, 12, 16, 20, 24, 32, 40, 56. Screen side gutter 20. Card padding 20. Vertical rhythm between sections 32.
- **Radius:** cards 24 · sheets 32 (top only) · inputs 16 · buttons 16 · chips and pills fully rounded · FAB 20 (squircle-ish rounded square, not a circle).
- **Elevation:** essentially flat.
  - Cards use `surface` on `bg` plus a 1 dp `line` border in light theme; no border in dark theme.
  - The only shadow is on the FAB and bottom sheets: soft, 24 dp blur, 8% opacity.

### 4.4 Iconography
- Material Symbols **Rounded**, weight 300, grade 0, 24 dp. Filled variant only for the active nav item.
- Fuel types use custom glyphs drawn in the same 300-weight rounded style: pump nozzle (petrol/diesel), cylinder (CNG/LPG), plug (EV).

### 4.5 Motion
- **Durations:** 120 ms (micro), 220 ms (standard), 360 ms (emphasis). Easing: Material 3 emphasized decelerate for entering, accelerate for exiting.
- **Signature motions:**
  1. **Odometer roller:** digits roll into place when the predicted odometer appears and when the user confirms.
  2. **Number tick-up:** hero metrics count up from the previous value (≤600 ms).
  3. **Tank fill:** the "Filled to full" toggle animates a small tank glyph filling.
  4. **Reward land:** the post-save efficiency number scales 0.92 → 1.0 with a single light haptic.
- Respect "Remove animations": all motions become instant fades.

### 4.6 Haptics
- Light tick on keypad presses (system "keyboard tap").
- A single "confirm" haptic on save.
- No haptics on scroll.

---

## 5. Components

Design each component in light and dark, with its states.

| Component | Spec |
|---|---|
| **Hero metric** | Overline label → `display-xl` number + unit suffix → delta chip (▲/▼ %, positive/negative) → "i" button. Tap "i" opens a formula sheet. |
| **Metric tile** | Square-ish card (min 156 dp wide), overline, `h2` number, caption line. Used in 2-up grids. |
| **Delta chip** | Pill, `caption` weight 500, arrow glyph + %, tinted background at 12% of positive/negative. |
| **Vehicle switcher** | Pill at top-left: vehicle photo (24 dp circle) + name + chevron. Opens a sheet listing vehicles, with "All vehicles" at the top. |
| **Entry row** | 64 dp tall. Left: 40 dp rounded-square icon tile tinted with the energy/category colour at 14%. Middle: title (`body`, e.g. "Petrol · 32.4 L") and meta (`body-s` `ink-3`, e.g. "Shell, HSR Layout · 42,318 km"). Right: amount (`body` 600, tnum) and efficiency chip if present. |
| **Section header** | `overline` + optional right-aligned text link ("See all"). |
| **Numeric keypad** | Custom in-sheet keypad (3×4: digits, ".", backspace). Keys 64 dp tall, `h2` digits, `surface-2` keys on `surface`. Above it: a "Next field" pill and the field-focus indicator. The system keyboard is never shown for numeric entry. |
| **Price triangle input** | Three stacked fields (Quantity / Price per L / Total ₹). The two user-entered fields show `ink`; the computed field shows `ink-3` with a "=" prefix and a small "auto" tag. Tapping a computed field makes it editable and auto-computes another field. |
| **Odometer input** | Large Geist Mono digits in individual cells (like a mechanical odometer) with a faint predicted value as placeholder. A "Use 42,318" chip accepts the prediction. Toggle "Odometer / Trip since last fill". |
| **Full-tank toggle** | Segmented control: "Full tank" / "Partial", with the tank glyph animation. |
| **Bottom sheet** | 32 dp top radius, grab handle, title `h3`, primary button pinned to the bottom. Entry sheets open at 92% height. |
| **Primary button** | Full-width, 56 dp, accent fill, `label` 600. Pressed: darken 8%. Disabled: `surface-2` + `ink-3`. |
| **Secondary button** | `surface-2` fill, `ink` text. |
| **Text button** | `ink` with underline on press only. |
| **FAB** | 64 dp rounded square, accent, "+" glyph, sitting in a notch in the centre of the bottom nav. Long-press opens a radial/arc quick menu: Fuel · Charge · Expense · Toll · Trip · Scan. |
| **Bottom navigation** | 5 slots: Home · Logbook · [FAB] · Insights · Trips (Trips replaced Garage, 12 Nov 2026; Garage opens from the Home top bar). Labels always visible, `caption`. Active = filled icon + `ink` label; inactive = outline icon + `ink-3`. |
| **Chips (filters)** | 36 dp, fully rounded, `surface-2`; selected = `accent-soft` bg + accent text. |
| **Reminder pill** | Status dot (warning/negative/positive) + title + "in 12 days" / "overdue 3 days" / "in ~420 km". |
| **Document card** | Credit-card proportion (1.586:1), document type overline, number in Geist Mono, expiry with status colour, small vehicle tag. Light paper texture at 3% (optional). |
| **Charts** | Hairline gridlines (`line`), no chart borders, `caption` axis labels in `ink-3`, series in energy colours or `ink` (single series). Rounded bar tops (4 dp). Line charts 2 dp with a soft 8% area fill. Tap/drag scrubber with a tooltip card. No pie or donut charts — use horizontal bars. |
| **Insight card** | `surface` card with a 4 dp accent-coloured left rule, `body` text with the key number bolded, optional "Why?" link, dismiss "×". |
| **Empty state** | Simple line illustration (single stroke, `ink-3`), one-line headline, one sentence, one primary action. No stock art. |
| **Snackbar / undo** | Dark pill (`ink` bg, `bg` text) with an "Undo" text action in accent. |
| **Station chip** | Pill in the Add fill-up sheet: brand mark dot (neutral grey circle with brand initial — **no real brand logos**), "HP · Indiranagar", small "auto" tag in `ink-3`. Tap opens the station picker sheet. States: detecting (shimmer, "Finding station…"), detected, user-picked, none ("Add station"). |
| **Station picker sheet** | Top candidate pre-selected (radio), up to 2 alternatives with distance ("40 m", "85 m"), "Other / type name" field, and a footer caption "Station data © OpenStreetMap contributors". |
| **Tracking status pill** | Small pill on Home top bar when automatic trips are on: green dot "Auto trips on", grey "Paused until 6 PM", amber "Blocked by battery saver" (tap → fix screen). |
| **Pro badge** | Tiny pill "PRO", `ink` outline, `caption` 600. Never orange, so it doesn't compete with actions. |
| **Paywall card** | See screen 28. |

---

## 6. Information architecture

```
Bottom nav
├── Home                  (dashboard for the selected vehicle)
├── Logbook               (timeline: fuel, charges, expenses, trips; list ⇄ map)
├── [+]                   (Add fill-up by default; long-press → quick menu)
├── Insights              (stats, charts, insight cards, monthly recap)
└── Trips                 (trips list, recording, automatic trips)

Global: vehicle switcher (top-left on Home, Logbook, Insights) · Garage (Home top bar, right) · Settings (Garage → gear)
Garage                    (vehicles, reminders, documents, FASTag, prices, backup, import/export, settings)
```

Trips has its own tab (owner decision, 12 Nov 2026: user feedback). Fuel prices, Reminders, Documents and FASTag are reachable from Home cards **and** Garage, so no one hunts for them. Garage is an icon in the Home top bar, shown also before the first vehicle so Settings, restore and import stay reachable.

---

## 7. Screens

For each screen: purpose → layout (top to bottom) → states → notes. Frame sizes: **412×915** (primary, Pixel-class) and **360×800** (small Android, check for overflow). Light and dark for screens marked ★.

### Onboarding

**1. Welcome ★**
- **Purpose:** promise and trust.
- **Layout:**
  - Full-bleed `bg`; animated gauge-arc logo mark.
  - Headline (`display`, 2 lines): "What your vehicle really costs."
  - Sub: "Log fuel in 10 seconds. Petrol, diesel, CNG or EV."
  - Three trust lines with icons: "No ads, ever" · "No account needed" · "Backed up to *your* Google Drive".
  - Primary "Get started". Text button "Restore from Google Drive".

**2. Add your first vehicle ★**
- A single scrolling sheet, not a wizard.
- **Layout:**
  - Vehicle type picker as large icon tiles in a horizontal row: Car, Scooter, Motorcycle, Auto, Van/Truck.
  - Fuel picker (chips): Petrol · Diesel · CNG + Petrol · Electric · LPG + Petrol · Other.
  - Name field (placeholder "e.g. Safari, Activa").
  - Current odometer (odometer input).
  - Collapsed "More details (optional)": tank capacity, battery (EV), reg number, emission norm, claimed mileage.
  - Primary "Add vehicle".
- **Notes:** CNG + Petrol reveals two tank capacities (kg + L). Electric reveals usable battery kWh and home tariff ₹/kWh.

**3. Bring your history (optional)**
- Three options as large rows: "Import from Fuelio (CSV)", "Import from another app / spreadsheet", "Start fresh".
- Then city selection for prices (search field with popular cities as chips: Bengaluru, Mumbai, Delhi, Chennai, Hyderabad, Pune, Kolkata, Ahmedabad).

**4. Privacy choices**
- Toggles: "Share anonymous crash reports" (default off), "Weekly digest" (default off), "Monthly recap" (default on).
- One-line explanation each. Primary "Done".
- Permissions are **not** asked here; ask in context later.

### Home

**5. Home ★** (the most important screen)
- **Top bar:** vehicle switcher (left); tracking status pill if automatic trips are on; privacy-blur eye toggle and bell (right; bell shows due items).
- **Hero:**
  - Overline "THIS MONTH · COST PER KM"
  - `display-xl` "₹4.82" + "/km"
  - Delta chip "▼ 6% vs Sep"
  - "i"
- **Metric tiles (2-up):**
  - "Mileage — 16.8 km/L (avg 16.1)" with a 30-day sparkline
  - "Spent — ₹6,240 · 1,294 km"
  - For EV: km/kWh and ₹/km. For bi-fuel: "CNG saved ₹1,180 this month (est.)".
- **"Last fill-up" card:** date, qty, ₹, efficiency chip, station; tap to open.
- **Range & next fill** (if the last fill was full): "~310 km left (est.)" with a slim horizontal gauge.
- **Upcoming (max 3 reminder pills):** "PUC renewal · in 12 days", "Service · in ~420 km", "Insurance · in 41 days". "See all".
- **Fuel prices today** (horizontal strip): "Bengaluru · Petrol ₹102.9 · Diesel ₹88.9 · CNG ₹89.0" with tiny day-change arrows. Tap → Prices.
- **Insight card** (one at a time, swipeable): e.g., "Your mileage dropped 11% over the last 2 tanks. Check tyre pressure — it's the most common cause."
- **States:**
  - **Brand-new vehicle:** hero replaced by "Log 2 full-tank fill-ups to unlock your real mileage", with a 2-step progress indicator and a big "Log fuel" button.
  - **Privacy blur:** all ₹ figures blurred.
- **Notes:** the scroll should feel short. Nothing below the insight card except "Recent" (last 3 entries).

### Logging (the core loop)

**6. Add fill-up sheet ★** (opens from FAB tap)
- **Header:** "Add fuel" + vehicle chip + date-time chip ("Now · 16 Oct").
- **Station chip** directly under the header, auto-detected ("HP · Indiranagar · auto"). Design the detecting, detected and none states. When opened from a fill-up prompt (screen 35), the chip, time and vehicle are already filled.
- **Scan banner** (slim, top): "📷 Scan pump meter" → screen 9.
- **Fields in order:**
  1. Odometer input with predicted placeholder and "Use 42,318" chip.
  2. Price triangle (Quantity L, Price per L prefilled "₹102.92 · city price", Total ₹).
  3. Full tank / Partial toggle.
- **"More" disclosure:** fuel grade chips (Regular · E20 · Premium — the premium label follows the detected brand: XP95 / Speed / Power), payment (UPI / Card / Cash / Fleet card), discount, notes, photos, "I missed logging the previous fill-up" checkbox with explanation.
- **Bottom:** custom keypad when a numeric field is focused; primary "Save" pinned above the keypad.
- **Validation states:** odometer lower than previous (inline negative text + "View entry"), short-fill warning (warning banner), unusual efficiency (info chip after save — not blocking).
- **Bi-fuel:** tank switch at top ("CNG · kg" / "Petrol · L") changes units.

**7. Post-save reward ★** (sheet or large toast over Home)
- `display` "16.8 km/L", delta chip "▲ 4% vs your average", line "₹5.92/km this tank · 412 km".
- If there's a city price: "You paid ₹0.20/L above city price" (neutral tone).
- First-ever fill: "Baseline set. One more full tank and we'll show your real mileage."
- Auto-dismiss after 3 s; swipe down to dismiss.

**8. Add EV charge sheet**
- Odometer, energy kWh / tariff / cost triangle, SoC start → end as a dual-handle slider over a battery glyph (0–100%), charger type chips (Home AC · Public AC · DC fast), operator, duration.
- Home charging pre-fills the tariff from settings.

**9. Scan (camera) ★**
- Full-screen camera with a rounded-rectangle guide labelled "Fit the pump display here".
- Mode switcher at the bottom: Pump meter · Receipt · Odometer.
- Detected numbers appear as live floating labels anchored to their positions (Geist Mono): "₹ 2,000.00", "19.43 L", "₹ 102.92/L".
- When all three validate, a checkmark appears and the result sheet slides up with "Use these values".
- **Failure state:** "Couldn't read clearly. Try less glare, or type it in." with a "Type instead" button.

**10. Add expense sheet**
- Category grid (4×4 icon tiles, most-used first), amount via keypad, date chip, odometer (optional), payment, notes, photo, "Repeats" toggle (frequency picker).
- Service/Repair reveals "Add line items".

**11. Add toll / FASTag sheet**
- Plaza name (autocomplete from history), amount, FASTag/Cash segmented, date-time, link to trip.
- Banner when opened via Android share: "From shared message" with the parsed fields highlighted for confirmation.

### Logbook

**12. Logbook ★**
- **Top:** vehicle switcher, search icon, list/map toggle.
- **Filter chips row:** All · Fuel · Charge · Expenses · Tolls · Trips · Date.
- **Grouped by month:** sticky month header with the month total on the right ("October · ₹6,240").
- **Rows:** entry rows; closing fill-ups show an efficiency chip.
- Swipe left = delete (undo snackbar), swipe right = edit.
- **Map mode:** full-screen map with energy-coloured pins, a bottom card carousel of entries in view, and a date-range chip.
- **Empty:** illustration of an open logbook, "Nothing logged yet", primary "Log your first fill-up".

**13. Entry detail**
- Hero amount, key facts list (qty, price/unit, odo, distance since last, efficiency for this interval with "i" formula), station with map thumbnail, photos, notes, edit/delete.

### Insights

**14. Insights overview ★**
- **Period selector** (segmented, scrollable): Month · 3M · 6M · YTD · 12M · All · Custom.
- **Hero:** "₹/km" for the period with delta.
- **2×2 metric tiles:** Total spent, Distance, Avg mileage, Cost/day.
- **Insight cards stack** (free + PRO-badged locked ones show a blurred number with "Unlock with Pro").
- **Chart list** (each a card with title + mini preview; tap → detail):
  - Mileage over time
  - Monthly spend
  - Price paid vs city price
  - Cost/km trend
  - Spend by category
  - Odometer
- "Monthly recap" entry card.

**15. Chart detail**
- Full-width chart (scrubbable), summary numbers above, data-table toggle below (accessibility + export), "i" formula sheet.
- Example to design: **Mileage over time** — bars per interval (energy colour), rolling-average line in `ink`, claimed-mileage dashed line labelled "Claimed 19.1".

**16. Monthly recap card** (shareable, 9:16) ★
- Bold typographic poster: month name huge, "1,294 km", "₹6,240", "₹4.82/km", best tank "18.2 km/L", one-line comparison. Small logo mark bottom.
- Buttons: Share (WhatsApp/Instagram story), Save image.
- Variants for petrol, CNG and EV (accent stripe in the energy colour).

### Trips

**17. Trips list**
- Week groups with totals (km, ₹ est.). "Show all on map" button (date range).
- Big "Start trip" button at top, plus "Add manual trip".
- If automatic trips are off: a quiet card "Record trips automatically" → screen 32.
- Automatic trips show a small "auto" tag and a stop marker when a likely fill-up was detected. Swipe actions: "Not my drive" (delete), "Change vehicle".

**18. Live trip ★**
- Map (70% height) with live polyline in accent.
- Bottom panel: Geist Mono distance "23.4 km", elapsed time, avg speed, est. cost.
- Pause (secondary), Stop (primary).
- Must be legible at arm's length: high contrast, large type.

**19. Trip detail**
- Map with route, stats grid, purpose chip (Personal/Business/Commute), linked tolls/parking, est. cost.
- **"Split cost" section:** people stepper (− 3 +) → "₹412 each" → "Share with UPI link" button. Preview of the WhatsApp message.
- Export GPX.

**20. Trip planner (PRO)** ★
- Origin/destination fields, vehicle chip, fuel level slider (tank glyph %), route option chips (Fastest · Shortest · Avoid tolls · Avoid highways).
- **Result:** map with route and stop markers.
- **Report card:**
  - Distance, ETA, fuel needed, est. fuel cost, tolls (est./manual)
  - "2 suggested stops" list, each with nearby stations (filtered by fuel type)
  - India tip card: "Fill up in Hosur before entering Tamil Nadu — save ~₹180."

### Garage and utilities

**21. Garage ★**
- Vehicle cards: photo, name, reg no in Geist Mono, odometer, fuel tags. "Add vehicle" card at the end.
- **List rows:** Reminders (count due) · Documents (count expiring) · FASTag (est. balance) · Fuel prices · Trips · Backup & sync (status dot) · Settings.

**22. Vehicle detail / edit**
- Header photo with name overlay.
- **Sections:** Energy setup (tanks), Claimed mileage, Custom parameters (marked "manual"), Emission norm, Purchase details, "Car Bluetooth for automatic trips" (pick paired device), Archive / Delete.

**23. Reminders**
- Tabs: Upcoming · Done.
- Pills grouped by vehicle, coloured by urgency.
- Add reminder sheet: preset grid (Insurance, PUC, Service, Oil, Tyres, Battery, RC, Licence, Warranty, Custom) → by date / by km / both → repeat → notify offsets.
- PUC preset shows helper text: "Validity depends on your emission norm and vehicle age. Rules may change — check your certificate."

**24. Documents vault ★**
- Stacked credit-card-style document cards, expiry status visible.
- Lock icon (app-lock state). Add document flow: type → scan/photo/PDF → fields → expiry auto-creates reminder (toggle shown).
- **Quick view:** full-screen card with large number and expiry, plus a footer note: "For legal checks, show DigiLocker or mParivahan" with buttons to open them.

**25. Fuel prices**
- City selector at top.
- Large rows per fuel: price, day change, 30-day sparkline.
- "Compare cities" (up to 3 pinned). "Find a pump near me" button.
- Source and update time in caption ("Updated 06:30 IST").

**26. FASTag**
- Card per vehicle: issuer, ••••1234, **est. balance** (with "est." tag + "i"), low-balance threshold.
- Recent tolls and recharges list. Actions: Add toll · Add recharge · "Paste/share an SMS" helper.

**27. Backup & sync ★**
- **Status hero:** green dot, "Backed up 2 hours ago", account email, size.
- Frequency segmented: After changes · Daily · Weekly · Manual. Toggles: Wi-Fi only, While charging.
- Encrypt backups (with a passphrase setup sheet and a loud warning).
- "Back up now", "Restore", "Save backup file to device".
- **Sync across devices** (PRO, R2) row.
- **Error state:** banner "Couldn't reach Google Drive — reconnect" with action.

**28. Paywall ★**
- **Never full-screen takeover.** Sheet at 92% height.
- **Layout:**
  - Title: "Fuelwise Pro".
  - Sub: "Everything you log stays free forever. Pro adds smarter insights and planning."
  - Feature list with icons (6 max): Trip planner · Advanced insights · Sync across devices · PDF reports · Pro widgets · Custom themes.
  - Plan selector: Yearly ₹299 "7 days free" (preselected, "Best value"), Monthly ₹49, Lifetime ₹999.
  - Primary "Start free trial", fine print, Restore purchases.
- No countdown timers, no fake discounts, no pre-ticked upsells.

**29. Settings**
- Grouped list:
  - Units
  - Currency
  - Home city
  - Home charging tariff
  - UPI ID (for trip split)
  - Language (English / हिन्दी)
  - Theme
  - App lock
  - Hide amounts
  - Notifications (per channel)
  - Import / Export
  - Location & trips: automatic trips on/off, keep routes for (30 days / 90 days / 1 year / forever), private places (trimmed from route ends), delete all location history
  - Privacy (crash reports)
  - Delete all data
  - About / Licences

### System surfaces

**30. Widgets** (design at actual home-screen sizes, light + dark, with Material You–neutral backgrounds):
- **Quick add 2×1:** four icon buttons: fuel, charge, expense, trip.
- **Status 2×2:** "₹4.82/km", "16.8 km/L", "PUC · 12 days".
- **Dashboard 4×2 (PRO):** month spend, mileage sparkline, range left, next due.
- **Trip controls 2×1:** Start / Stop, live distance.

**31. Notifications** (design the expanded templates):
- Reminder: "PUC for Safari expires in 7 days" · actions **Done** · **Snooze 3 days**.
- Trip recording (ongoing): "Recording trip · 23.4 km · 38 min" · **Pause** · **Stop**.
- Post-trip: "Trip saved · 42 km. Update odometer?" · **Update**.
- Monthly recap: "October: ₹4.82/km, 6% cheaper than September." · **See recap**.
- Price change: "Petrol in Bengaluru up ₹1.20/L today."
- Fill-up prompt: "Filled up at HP, Indiranagar? Tap to log." · **Log it** · **Not a fill-up**.
- Automatic trip recording (ongoing, minimal): "Recording drive · Safari · 12.4 km" · **Pause** · **Not my drive**.

**32. Automatic trips setup ★** (opened from Trips, Home card or Settings; never on first run)
- **This is Play's required prominent disclosure, so the copy matters.** It must appear before any permission prompt and must name "location" and "background".
- **Layout:**
  - Illustration: a single-stroke route line ending at a pump glyph.
  - Headline: "Log trips without lifting a finger."
  - Body (copy as written, translate faithfully): "This app collects **location** data in the **background**, even when the app is closed or not in use, to record your drives automatically and remind you to log fill-ups when you stop at a fuel station. Your routes stay on this phone. They're never sent to us or anyone else."
  - Three short lines: "Only while you're driving" · "Routes stay on your phone" · "Pause any time".
  - Primary "Turn on", text button "Not now". Closing is always possible.
- **Then the steps, one per screen, with a progress dots row:** physical activity permission → location "Allow all the time" (show a small illustration of which option to tap in the system dialog) → optional car Bluetooth pick → battery saver check (screen 33).
- **Denied states:** if "all the time" is refused, explain in one line that trips will need a tap to start, and offer "Start trips with one tap instead" (notification-based fallback).

**33. Battery saver fix**
- Detected phone brand at top ("Xiaomi / Redmi").
- 2–4 illustrated steps specific to that brand, with an "Open settings" button that deep-links where possible.
- Footer: "Done — test it" runs a 10-second check and shows ✓ or "Still blocked".

**34. Station picker sheet**
- As in the component spec. Show the map thumbnail with the user's dot and up to 3 station pins.
- OSM attribution in the footer.

**35. Fill-up prompt → pre-filled sheet**
- Design the notification (screen 31) and the Add fill-up sheet it opens. Station, time and vehicle are filled and tagged "from your drive". The odometer field shows the GPS estimate as a placeholder only.

---

## 8. Key flows to prototype

1. **First run → first fill-up:** Welcome → Add vehicle → Start fresh → Home empty state → FAB → Add fill-up → Save → Reward "Baseline set".
2. **10-second fill-up (returning user):** Home → FAB → Scan pump meter → values auto-filled → odometer chip "Use 42,318" → Save → Reward 16.8 km/L.
3. **CNG owner:** Add fill-up → tank switch to CNG (kg) → Save → Home tile "CNG saved ₹1,180 this month (est.)".
4. **Document → reminder:** Garage → Documents → Add PUC → photo → expiry → "Remind me" on → Reminders shows the pill.
5. **FASTag via share:** Messages app → share toll SMS → app opens the Add toll sheet pre-filled → Confirm.
6. **Trip split:** Trip detail → Split with 3 → Share → WhatsApp message preview with the UPI link.
7. **Backup setup:** Garage → Backup → Sign in with Google → Daily → "Backed up just now".
8. **Upgrade:** Insights → locked "Fuel grade compare" → Paywall sheet → Start trial → card unlocks with tick-up animation.
9. **Automatic trips setup:** Trips → "Record trips automatically" → disclosure (32) → activity permission → "Allow all the time" → car Bluetooth → battery saver fix (33) → Home shows "Auto trips on".
10. **Detected fill-up:** drive ends → notification "Filled up at HP, Indiranagar?" → pre-filled sheet (35) → type total + quantity → Save → reward.
11. **Station correction:** Add fill-up → station chip shows wrong pump → picker (34) → choose the one across the road → next visit it's pre-selected.

---

## 9. Delight budget (where to spend motion and craft)

Only these moments get extra animation; everything else is instant and quiet:
- Post-save reward number land + haptic.
- Odometer roller.
- Hero metric tick-up on Home when the period or vehicle changes.
- Scan: live number labels snapping into the result sheet.
- Milestone sheet (rare): "10,000 km logged" with a subtle gauge sweep.

---

## 10. Accessibility and localisation

- **Touch and contrast:** min touch target 48 dp; AA contrast; never colour alone for meaning (always arrow + sign).
- **Font scale:** design the Add fill-up sheet at 200% font scale. Fields stack; the keypad shrinks to 56 dp keys; Save stays visible.
- **Hindi:** design Home, Add fill-up and Reminders in Hindi. Devanagari runs ~20–30% wider and taller. Allow 2-line labels on tiles and chips; never truncate amounts.
- **Number formatting:** ₹1,23,456.78 (Indian grouping) in all mocks; dates as "16 Oct 2026".
- **Screen reader:** charts expose the data table; hero metrics read as "Cost per kilometre this month, 4 rupees 82 paise, down 6 percent from September".

---

## 11. Sample data for mocks (use these, keep consistent)

| Vehicle | Type | Fuel | Reg no | Odometer |
|---|---|---|---|---|
| Safari | SUV | Diesel, 50 L | KA 01 AB 1234 | 42,318 km |
| Ertiga | Car | CNG 60 L-water (≈12 kg) + Petrol 45 L | MH 02 CD 5678 | 31,045 km |
| Nexon EV | Car | Electric, 40.5 kWh usable | DL 3C EF 9012 | 18,402 km |
| Activa | Scooter | Petrol, 5.3 L | KA 05 GH 3456 | 9,870 km |

Reg numbers are fictional.

**Prices (Bengaluru, illustrative — not live data):** Petrol ₹102.92/L, Diesel ₹88.95/L, CNG ₹89.00/kg, Home tariff ₹7.50/kWh.

**Typical figures:**
- Safari: 13.4 km/L, ₹6.64/km
- Ertiga: 26.1 km/kg (est.), ₹3.41/km combined
- Nexon EV: 7.1 km/kWh, ₹1.06/km home
- Activa: 47 km/L, ₹2.19/km

---

## 12. Don'ts

- No pie or donut charts. No 3D. No gradients on data. No glassmorphism.
- No streaks, badges walls, confetti storms or leaderboards.
- No ads, banners, "sponsored" stations or upsell interstitials.
- No more than one accent element competing with the primary action.
- No stock photos or emoji in UI chrome (emoji are fine in user content only).
- Nothing that resembles Fuelio's, Park+'s or CarInfo's UI, icon or colour scheme.
- No real fuel-company logos (IOCL, BPCL, HPCL, Shell, Nayara, Jio-bp) anywhere. Use the neutral initial dot.
- No surveillance vibe: no live "we're watching" map on Home, no heatmaps of where the user has been.

---

## 13. Deliverables requested from Claude Design

1. Token sheet (colour light/dark, type scale, spacing, radius) and the component sheet (section 5) with states.
2. Screens 1–35 at 412×915. ★ screens in light and dark. Screens 5, 6, 14 and 32 also at 360×800 and in Hindi.
3. Flows 1–11 as connected prototypes.
4. App icon (adaptive: foreground mark + `bg`/`ink` background; themed monochrome variant) and Play Store feature graphic (1024×500).
5. 6 Play Store screenshots (portrait, 1080×1920) with short headline overlays:
   - "Log fuel in 10 seconds"
   - "Your real mileage, not the brochure's"
   - "CNG, EV, petrol — one app"
   - "Never miss PUC or insurance"
   - "No ads. Your data, your Drive."
   - "Know your ₹/km"
