# CLAUDE.md — Fuelwise (working name)

Android app for tracking vehicle running costs in India: fuel, CNG, EV charging, expenses, trips, reminders and documents.
All user data lives on the device and is backed up to the user's own Google Drive (`appDataFolder`). There is no backend for user data.

**Source of truth:**
- `docs/PRD.md` — what to build. Feature IDs look like `F-03.1`. Releases are R1, R2, R3.
- `docs/design.md` — how it looks: tokens, components, screens.

Read the relevant PRD section **before** implementing a feature. When a PRD and this file disagree, stop and ask.

---

## Stack (do not change without asking)

| Area | Choice |
|---|---|
| Language | Kotlin (latest stable); Coroutines + Flow |
| UI | Jetpack Compose + Material 3, with a custom theme from `core:designsystem` |
| Navigation | Navigation Compose with type-safe routes (Kotlin serialization) |
| DI | Hilt |
| DB | Room (KSP), Paging 3 for the logbook |
| Prefs | Proto DataStore |
| Background | WorkManager (backup, reminders, recurring expenses, price feed) |
| Charts | Vico (Compose) |
| Maps | Maps Compose (Google Maps SDK) for display only. **No Google Places API** (PRD D12). |
| Location | Fused Location Provider + Activity Recognition Transition API (play-services-location); Companion Device Manager for car Bluetooth (R1, automatic trips) |
| Station data | Bundled OpenStreetMap extract (`amenity=fuel`, `amenity=charging_station`, India), refreshed via the public feed; ODbL attribution required |
| OCR | ML Kit Text Recognition, bundled model, offline |
| Camera | CameraX |
| Widgets | Jetpack Glance |
| Auth/Drive | Credential Manager (Sign in with Google) + `AuthorizationClient` for the `drive.appdata` scope. Drive REST v3 via OkHttp/Retrofit. |
| Billing | Play Billing Library (latest) |
| Crypto | Tink (AEAD) with an Android Keystore key; Argon2id for backup passphrases |
| Tests | JUnit5, Turbine, Truth/AssertK, Robolectric where needed, Compose UI tests, Roborazzi screenshots |
| Quality | ktlint, detekt, Android Lint (warnings as errors in CI) |

minSdk 26. targetSdk is the current Play requirement. Gradle version catalogs (`gradle/libs.versions.toml`) and convention plugins (`build-logic/`).

---

## Module layout

```
app/                      # Application, nav graph, DI wiring
core/model                # Pure Kotlin data classes, enums (no Android)
core/domain               # Pure Kotlin: consumption & cost engine, predictions, parsers (FASTag SMS, OCR post-processing)
core/database             # Room entities, DAOs, migrations
core/data                 # Repositories (single source of truth = Room)
core/datastore            # Proto DataStore
core/designsystem         # Theme, tokens, components (from docs/design.md)
core/ui                   # Shared composables built on the design system
core/notifications        # Channels, builders
core/location             # Single-fix requests, trip recorder service, activity transitions, stop/fill-up detection, station matcher (PRD F-21)
core/testing              # Fakes, fixtures, golden data
sync/drive                # Backup, restore, encryption, (R2) change-set sync
feature/<name>            # home, refuel, expense, logbook, stats, trips, planner, prices, fastag, reminders, documents, garage, settings, paywall, onboarding, recap, scan
widget/                   # Glance widgets
```

Feature modules depend only on `core:*`. They never depend on other feature modules.

---

## Non-negotiable rules

1. **Money is `Long` paise.**
   - Never use `Double` for money.
   - Format with Indian grouping (`₹1,23,456.78`) via `core:ui` formatters only.
2. **Canonical units in storage.** km, L, kg, kWh. Convert only at the UI edge, using `core:domain/units`.
3. **IDs are UUIDv7 strings.**
   - Every syncable entity has `createdAt`, `updatedAt` (UTC ms), `deviceId` and `deletedAt` (tombstone).
   - Never hard-delete synced rows.
4. **The consumption engine (`core:domain`) is pure Kotlin.**
   - Any change must keep the golden vectors in PRD §F-03.7 passing.
   - Add a vector for every bug fixed.
5. **No user data leaves the device** except to the user's Google Drive.
   - Never add analytics/ads SDKs.
   - Crash reporting stays opt-in (PRD D7).
   - The price/station feed request must not include location or identifiers. Download whole files, filter on device.
   - Never send coordinates to any lookup API (no Places, no reverse geocoding services). Station matching is offline.
   - Raw trip points are encrypted at rest, follow the retention setting (PRD F-21.6), and are backed up only when encrypted backups are on.
6. **Forbidden permissions/APIs** (Play policy):
   - `READ_SMS` / `RECEIVE_SMS` / any SMS permission
   - `NotificationListenerService` for reading SMS
   - `SYSTEM_ALERT_WINDOW`
   - `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM`
   - `MANAGE_EXTERNAL_STORAGE` / `READ_EXTERNAL_STORAGE`
   - `ACCESS_BACKGROUND_LOCATION` and `ACTIVITY_RECOGNITION` are allowed from R1 (PRD D15), behind a feature flag, and used **only** by automatic trips + fill-up detection (F-21.3/21.4, both R1) (D13). No other code path may read location in the background.
   - `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: don't declare. Open the system settings screen via intent instead.
7. **Any new permission, network endpoint or dependency → ask first** and update PRD §9.
8. **Never hard-code legal rules** (PUC validity, renewal periods). They live in `assets/rules/india.json` (versioned) and are always user-editable.
9. **Room migrations:** every schema change needs an explicit `Migration` and a migration test. No `fallbackToDestructiveMigration` — ever.
10. **Strings in resources** (`values/`, `values-hi/`). No hard-coded UI text. Plurals via `<plurals>`.
11. **Location requests have a lifetime.**
    - Outside a recording trip there must be zero active location requests. Every request sets a duration/timeout.
    - Trip sampling: high accuracy, ~5 s / ~25 m, batched delivery (max delay 60–120 s). Don't "optimise" this into a fixed 1-minute interval; it breaks route accuracy (PRD F-21.3).
    - Single fixes (station detect): balanced priority, 10 s timeout, max age 2 min.
    - Detected stations and fill-ups are suggestions. Never save an entry the user hasn't confirmed.

---

## Architecture conventions

- **UDF:** `ViewModel` exposes a single `StateFlow<UiState>` plus one-off events via `Channel`. Composables are stateless; screen-level composables collect state with `collectAsStateWithLifecycle`.
- **Placement of logic:**
  - No business logic in composables or ViewModels beyond orchestration.
  - Calculations live in `core:domain` use-cases.
  - Repositories expose `Flow`s from Room; writes are `suspend`.
- **Dispatchers:** inject them; never hard-code `Dispatchers.IO`.
- **Time:** use an injected `Clock`. Store UTC ms plus `zoneId`.
- **Feature flags:** `core:datastore` `FeatureFlags` for R2/R3 features and Pro gating. Pro checks go through `EntitlementRepository` only.
- **Design system:** use `core:designsystem` tokens and components. Never use raw colours, dp values or `MaterialTheme` defaults that bypass tokens. If a component is missing, add it there first.

---

## Commands

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest                 # all unit tests
./gradlew :core:domain:test                 # consumption engine + parsers (run on every engine change)
./gradlew connectedDebugAndroidTest         # instrumented (DB migrations, UI)
./gradlew verifyRoborazziDebug              # screenshot tests
./gradlew ktlintCheck detekt lintDebug
./gradlew :app:generateBaselineProfile      # before release
```

Secrets go in `local.properties` (git-ignored) via the Secrets Gradle Plugin. Restrict the `MAPS_API_KEY` to the package + SHA-1 in Google Cloud. Never commit keys.

---

## Definition of done (per feature)

- [ ] Implements the PRD acceptance criteria for its `F-` ID; the PR title includes the ID.
- [ ] Unit tests for the domain logic; UI test for the primary flow; screenshot tests in light and dark.
- [ ] Empty, loading and error states as specified in `docs/design.md`.
- [ ] TalkBack labels; 48 dp targets; works at 200% font scale.
- [ ] Strings in EN and HI. Run a pseudo-locale check for overflow.
- [ ] Works offline. No new permission or endpoint without approval.
- [ ] Location features: tested on Pixel, Samsung and one of Xiaomi/Realme/Vivo; `dumpsys location` shows no active request outside trips.
- [ ] `ktlintCheck detekt lintDebug testDebugUnitTest` green.
- [ ] PRD feature status updated (add `✅ built` next to the ID).

---

## Working style for Claude Code

- Before a feature: restate the `F-` ID, list the files you'll touch and any open PRD decision (§13) it depends on. If one is unresolved, ask; don't guess.
- Build in vertical slices: entity → DAO → repository → use-case → ViewModel → UI → tests.
- Keep PRs small (one F-ID or sub-ID).
- Don't add libraries for things the stack already covers.
- When unsure about a Play policy, Google API scope or Android API behaviour, check the official docs and cite them in the PR description.
- Fixtures: realistic Indian data (₹ prices around ₹95–₹110/L petrol, CNG ₹75–₹95/kg, Bengaluru/Delhi/Mumbai). Never use real people's registration numbers.
