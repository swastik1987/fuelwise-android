# Fuelwise (working name)

Android app for tracking vehicle running costs in India: fuel, CNG, EV charging, expenses, trips, reminders and documents.
Data stays on the phone and is backed up only to the user's own Google Drive.

- What to build: [docs/PRD.md](docs/PRD.md)
- How it looks: [docs/design.md](docs/design.md) and the Claude Design export in [design/](design/README.md)
- Plan and milestones: [docs/development-plan.md](docs/development-plan.md)
- Engineering rules: [CLAUDE.md](CLAUDE.md)

## Build

Open the folder in Android Studio (2026.2 or newer) and run the `app` configuration, or:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest test          # all unit tests (Android + pure Kotlin modules)
./gradlew ktlintCheck detekt lintDebug    # static checks, same as CI
./gradlew :core:designsystem:recordRoborazziDebug   # refresh component screenshots
```

Secrets (Maps key, later) go in `local.properties`, which is git-ignored.
