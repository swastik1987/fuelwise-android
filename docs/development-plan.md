# Development plan — Fuelwise (R1)

> Derived from `docs/PRD.md` v1.1, `docs/design.md` and `CLAUDE.md`. Last updated 19 Oct 2026.
> One PR per F-ID (or sub-ID). Each milestone is a set of vertical slices: entity → DAO → repository → use-case → ViewModel → UI → tests.

---

## 0. Before code: setup checklist (owner: Swastik)

### Must do before M0
| # | Where | Action |
|---|---|---|
| S1 | GitHub | Create a **private** repo (e.g. `fuelwise-android`), empty: no README, .gitignore or licence (the scaffold adds them). |
| S2 | This PC | Install GitHub CLI (`winget install GitHub.cli`), then `gh auth login` (HTTPS, browser). Lets Claude push branches and open PRs. |
| S3 | GitHub | After the first push: Settings → Branches → protect `main` (require PR + the `ci` check). Actions stay enabled. |
| S4 | Claude Design | Run `/design-login` once from an interactive `claude` terminal, so Claude can read the design project. Alternative: export the project's HTML files into `design/` in this folder. |
| S5 | Decision | ✅ **App ID: `in.fuelwise.app`** (decided 17 Oct 2026). Permanent once published; if D1 picks another name, the ID stays. `in` is a Kotlin keyword, so the code namespace is `app.fuelwise` (the applicationId and namespace are independent in Gradle). |

### Already in place on this PC
- Android Studio with bundled JBR (JDK 25), SDK platforms 36 and 37, build-tools 35/36, a `Medium_Phone_API_37` emulator.
- Optional: add an **API 26** emulator (SDK Manager → System Images) to test minSdk behaviour.

### Needed later (not blocking M0–M4)
| # | Where | When | Action |
|---|---|---|---|
| L1 ✅ done 19 Oct 2026 | Google Cloud | Before M7 | Create a GCP project; enable **Google Drive API**; configure OAuth consent screen (External, scope `drive.appdata` + optional `drive.file`); create an **Android OAuth client** (package + debug SHA-1) and a **Web client ID** (Credential Manager needs it). |
| L2 ✅ done 19 Oct 2026 | Google Cloud | Before M8 | Enable **Maps SDK for Android**, create a key restricted to package + SHA-1 and to that API. Put it in `local.properties` as `MAPS_API_KEY` (never committed). |
| L3 | GitHub | Before release CI | Repo secrets: upload keystore (base64), its passwords, `MAPS_API_KEY` for CI builds. |
| L4 | Play Console | Before M11 | Developer account; create the app; in-app products (monthly, yearly with trial, lifetime) for Billing tests; license testers. New personal accounts need a closed test (currently 12 testers × 14 days — verify). |
| L5 | Data | Before M10 | D9 samples: Fuelio / Drivvo / Simply Auto CSV exports; FASTag SMS texts from 5+ issuers; 50 pump-display photos. |
| L6 | Data | Before M9 | D14 OSM coverage audit: 30 known pumps each in Bengaluru, Delhi, Mumbai → `docs/osm-coverage.md`. |

---

## 1. Open decisions that block specific milestones

| Decision | Blocks | Default if you don't decide |
|---|---|---|
| D1 name / brand | Store listing, icon, app ID (S5) | Keep "Fuelwise" as placeholder string only; app ID neutral |
| D2 city price source + D3 feed host | F-09, OSM refresh feed | R1 ships manual city price only (PRD §12.2); no network endpoint |
| D4 pricing | F-19 product setup | ₹49 / ₹299 / ₹999 as proposed |
| D7 crash reporting | M0 dependency list | **None** in R1 (Crashlytics pulls in Firebase; needs your approval per CLAUDE.md rule 7) |
| D8 maps provider | M8 | Google Maps SDK (already in the CLAUDE.md stack) |
| D9 samples, D14 audit | F-13.2, F-10.4, F-15, F-21.2 | Those features wait |

✅ Approved 17 Oct 2026: bundle Geist + Geist Mono (OFL), Noto Sans Devanagari (OFL) and Material Symbols Rounded weight 300 as vector drawables (Apache 2.0) in `core:designsystem`, with licences in `assets/licenses/`. D7 decided: no crash reporting in R1.

Design reference: the Claude Design export is in `design/` (see `design/README.md` for screen mapping and spec differences).

---

## 2. Milestones (R1)

Sizes are rough, for one builder working with Claude Code.

### M0 — Foundations (≈1 week) ✅ done (PR #1; DataStore + FeatureFlags moved to M2)
- Gradle: version catalog, `build-logic/` convention plugins (android-library, compose, hilt, room, jvm-library, test), all modules from CLAUDE.md as empty skeletons.
- Quality: ktlint, detekt, Android Lint warnings-as-errors; GitHub Actions `ci` workflow (`ktlintCheck detekt lintDebug testDebugUnitTest`).
- `core:model` / `core:domain` basics: `Money` (Long paise), units + conversions, UUIDv7 generator, injected `Clock` and dispatchers.
- `core:ui` formatters: Indian grouping `₹1,23,456.78`, dates `16 Oct 2026`.
- `core:designsystem`: tokens from design.md §4 (colour light/dark, type scale, spacing, radius, motion), Geist fonts, base components (buttons, chips, cards, hero metric, metric tile, delta chip, bottom sheet, snackbar, entry row). Roborazzi screenshots in light + dark.
- `app`: Hilt application, empty nav graph with the 5-slot bottom nav + FAB notch. `values/` + `values-hi/` strings.
- Proto DataStore schema for settings + `FeatureFlags`; `EntitlementRepository` stub (always free).

### M1 — Consumption & cost engine, F-03 (≈1 week) ✅ done (TCO, F-03.5 optional part, deferred)
Pure Kotlin, highest-stakes code, built before any UI depends on it.
- F-03.1 full-tank algorithm, F-03.2 units, F-03.3 bi-fuel (est.), F-03.4 EV SoC + full-charge fallback, F-03.5 cost metrics, F-03.6 predictions.
- Golden vectors from PRD §F-03.7 as the first tests; property tests for ordering/back-dating; perf test (2,000 entries recompute < 200 ms).

### M2 — Garage & onboarding, F-01 + screens 1–4, 21, 22 (≈1.5 weeks) ✅ done (vehicle photo, purchase details and custom-parameter editing deferred; CSV import rows wait for M10, Drive restore for M7)
- Room DB v1: `vehicle`, `tank`, `category` (+ presets), sync columns on every syncable table, exported schemas, migration-test harness.
- Vehicle create/edit/archive/delete-with-name-confirm + 10 s undo; vehicle switcher.
- Onboarding: Welcome, Add first vehicle (incl. bi-fuel/EV reveals), Bring history (import rows stubbed until M10), Privacy choices.

### M3 — Logging loop, F-02, F-04, F-10.2 (≈2.5 weeks) ✅ done (recurring expenses, FASTag accounts/recharges/balance (F-10.1, F-10.3) and service line items moved to M6; photos and trip-meter odometer mode deferred)
- Custom numeric keypad, price triangle, odometer roller input with prediction chip, full-tank toggle.
- Add fill-up sheet (screen 6) with validation guards (odo order, short-fill, anomaly chip), smart defaults, "More" section; post-save reward (screen 7).
- Add EV charge (screen 8), Add expense with categories, recurring, line items (screen 10), Add toll / FASTag + recharges + est. balance (screens 11, 26).
- Station chip placeholder ("Add station") until M9.
- Measure the 10-second guardrail in a UI test.

### M4 — Home & Logbook, F-05 + screens 5, 12, 13 (≈1.5 weeks) ✅ done (category/date/payment filters, bi-fuel "CNG saved" tile and the 5,000-entry benchmark deferred; reminder pills wait for M6, prices strip for M9, map mode for M8)
- Home dashboard incl. brand-new-vehicle state, privacy blur, range gauge, reminder pills (wired in M6), recent entries.
- Logbook with Paging 3, month headers + totals, filters/search, swipe edit/delete + undo; entry detail. Map mode deferred to M8.
- Perf: 5,000-entry scroll benchmark.

### M5 — Insights, F-06 + F-17 (≈2 weeks) ✅ done (custom period and city-price overlay deferred; recap notification moves to M6; the 3-year perf check covers computation, chart drawing is not benchmarked)
- Period selector, overview tiles, Vico charts (efficiency, monthly spend, price paid, cost/km, odometer, category bars), chart detail with data table + formula sheets.
- Free insight cards (mileage drop, real vs claimed); Pro cards rendered locked.
- Monthly recap card (9:16 share image) + milestones.

### M6 — Reminders & Documents, F-11, F-12 (≈2 weeks) ✅ done (also recurring expenses F-04.3 and FASTag F-10.1/10.3 from M3; the SMS share parser stays in M10; service line items deferred)
- `assets/rules/india.json` (versioned, never hard-coded), presets, date/odo/both triggers, WorkManager scheduling at preferred time, odo evaluation vs predicted odometer, notification actions (Done/Snooze/Open), reboot/restore idempotency.
- `core:notifications` channels; `POST_NOTIFICATIONS` asked when the first reminder is created.
- Documents vault with Tink AEAD + Keystore, photo picker, quick view with DigiLocker/mParivahan note, auto-reminder on expiry.

### M7 — Backup & restore, F-14 (R1 scope) (≈2 weeks) — needs L1 ✅ done (Drive sign-in verified on the emulator 19 Oct 2026 with the L1 client ID; drive.file visible export deferred)
- Credential Manager sign-in + `AuthorizationClient` for `drive.appdata`; snapshot export/import (schema-versioned, gz), content-addressed attachments, retention (10 + monthly), WorkManager schedules + constraints.
- Encrypted backups (Argon2id + AES-256-GCM), restore to temp DB → validate → swap, status UI with error fixes, local SAF backup file.

### M8 — Trips, F-07 (≈2 weeks) — needs L2 — ✅ built, field test open (slice 1 F-07.1 manual trips ✅: Room v5 `trip`, weekly list, editor, detail with UPI split. Slice 2 F-07.2/07.3 GPS recording ✅: Room v6, `core:location` recorder + foreground service, encrypted raw batches, live screen, interrupted-trip resume/finish. Slice 3 F-07.4 maps ✅: maps-compose in `core:ui` (`FwRouteMap`, `FwPinMap`, dark style), live and detail route maps, Logbook map mode. Slice 4 F-07.6 + F-07.8 ✅: post-trip odometer notification and sheet, trip odometers feed predictions, GPX export per trip and by range. Open: the 20 km field test (±3 %, `docs/perf.md`), and device runs on Pixel, Samsung and one of Xiaomi/Realme/Vivo. Widget and QS-tile trip starts come with M11. Approved 19 Oct 2026: play-services-location, maps-compose, location + foreground-service permissions, Maps SDK endpoint. Privacy default: raw points only while recording, then an encrypted simplified route; routes in Drive backups only when encrypted)
- Manual trips; GPS trip foreground service (`location` type), batched sampling per CLAUDE.md rule 11, 15 s persistence, interrupted-trip recovery, live trip screen, trip detail with UPI split, GPX export, weekly view.
- Logbook map mode (Maps Compose).
- Field test: 20 km drive within ±3 % → `docs/perf.md`.

### M9 — Offline station list, F-21.2 (R1, bundled) — needs L6 — ⏳ not started; F-21.1 station chip ➡ R2 (PRD D15, D16, 23 Oct 2026)
- R1: build script to turn an India OSM extract into a compact bundled file (< 3 MB target); `station_osm` read-only + `station_user`; offline nearest-station matcher (distance → fuel fit, personal stations boosted) used by F-21.4; ODbL attribution in Settings → About. Refreshed only with app updates. Ask before adding any tool or dependency for the build script.
- R2: single-fix request (balanced, 10 s, 2 min max age), station chip states, picker sheet with ODbL attribution, learning from corrections (F-21.1), monthly refresh through the feed (needs D3).

### M10 — Scan, prices, import/export, FASTag parser (≈3 weeks) — needs L5, D2/D3 — 🚧 in progress (19 Oct 2026): F-15 scan, F-09 prices (manual + feed client, feed off with no URL until D2/D3), F-13.1 export + free reports, in parallel sessions
- F-15 ML Kit (bundled) pump/receipt/odometer scan with triangle validation.
- F-13 CSV export (zip); text reports (PDF = Pro). Drivvo/Simply Auto presets once D9 samples exist.
- ✅ F-13.2 import brought forward (18 Oct 2026): one Import screen, Fuelio preset, generic mapper, .xlsx bulk-entry template, dry-run + undo (Room v4).
- F-10.4 share-target SMS parser per issuer, amount-only fallback. ➡ moved to R2 (23 Oct 2026, PRD D15).
- F-09 city prices (feed or manual), "did I pay fair?", Maps intent for nearby pumps, price alerts.

### M11 — Settings, widgets, Pro, F-18, F-16, F-19 (≈2 weeks) — needs L4 — 🚧 in progress (19 Oct 2026): F-18 settings and F-16 free widgets, QS tiles, shortcuts in parallel sessions; F-19 Pro, Pro widgets and PDF reports ➡ moved to R2 (23 Oct 2026, PRD D15); Insights Pro cards show "Coming soon" ✅ 23 Oct 2026
- Settings screen (units, theme, Material You opt-in, app lock via BiometricPrompt, hide amounts, delete all data incl. Drive).
- Glance widgets (Quick add, Status, Trip controls; Pro Dashboard/Consumption), QS tiles, app shortcuts.
- Play Billing: products, paywall sheet, restore, 7-day offline grace; `EntitlementRepository` goes live.

### M13 — Automatic trips, F-21.3–21.7 (moved into R1, 23 Oct 2026, PRD D15) — 🚧 in progress: F-21.3 slice 1 (detection engine, recorder, notifications) built 23 Oct 2026
- F-21.4 fill-up prompts: stations from the M9 bundled list (PRD D16), so M9 lands first. F-21.5 GPS odometer estimate: per-vehicle correction factor, feeds the odometer hint, odometer reminders and range.
- Needs approval first (CLAUDE.md rule 7): `ACCESS_BACKGROUND_LOCATION`, `ACTIVITY_RECOGNITION`, `BLUETOOTH_CONNECT`, Companion Device Manager, any new Activity Recognition dependency.
- Setup screen with the prominent disclosure (design.md screens 32–33), activity-transition start/stop, car Bluetooth per vehicle, reuse of the M8 recorder, "Pause automatic trips" QS tile and Home pill, "Not my drive".
- F-21.6: retention setting, private places trimmed from route ends, "Delete all location history". F-21.7: brand-specific battery-saver steps and the 3-day health check.
- Play: Permissions Declaration Form and ≤30 s video, submitted early with a closed-test build. Device runs on Pixel, Samsung and one of Xiaomi/Realme/Vivo; battery drain logged in `docs/perf.md`.

### M12 — Release hardening (≈1.5 weeks) — ⏸ deferred (19 Oct 2026)
- Baseline profile, cold-start and sheet-open budgets, 200 % font scale, TalkBack pass, Hindi review + pseudo-locale, data safety form, privacy policy, store listing, closed test.

**Total R1 estimate: roughly 5 months.** If time-boxed, cut in PRD §12.9 order: F-17 → F-09.

---

## 3. R2 / R3 (after R1 ships)
- R2 (re-scoped 23 Oct 2026, PRD D15): F-21.1 station chip and picker plus the monthly station-list refresh (rest of M9), F-10.4 FASTag share parser, F-19 Pro and billing (Pro widgets, PDF reports, Pro insight cards), F-14.6 multi-device sync, F-10.5 statement import, combined trip map, regional languages, Drive Wrapped.
- R3: F-08 trip planner (Routes API; needs a cost budget decision).

---

## 4. How we'll work
- Branch per F-ID (`feat/F-03.1-full-tank`), PR title includes the ID, CI green before merge, PRD gets `✅ built` next to the ID.
- Claude restates the F-ID, files touched and any open decision before each slice, and asks before any new permission, endpoint or dependency.
