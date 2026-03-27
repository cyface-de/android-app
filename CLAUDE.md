# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Android app for capturing sensor (accelerometer, gyroscope) and GPS data to measure road surface quality. Three app variants -- Cyface, R4R, Digural -- share a modular SDK via Git submodules.

## Build & Test Commands

```bash
# Build a specific variant
./gradlew :ui:cyface:assembleDebug
./gradlew :ui:r4r:assembleDebug
./gradlew :ui:digural:assembleDebug

# Unit tests
./gradlew :ui:cyface:testDebugUnitTest
./gradlew :ui:r4r:testDebugUnitTest
./gradlew :ui:digural:testDebugUnitTest

# Backend module tests
./gradlew :persistence:testDebugUnitTest
./gradlew :datacapturing:testDebugUnitTest
./gradlew :synchronization:testDebugUnitTest

# Single test class
./gradlew :ui:cyface:testDebugUnitTest --tests "de.cyface.app.SomeTestClass"

# Instrumented tests (needs device/emulator)
./gradlew :ui:cyface:connectedDebugAndroidTest
# Single instrumented test
./gradlew :ui:cyface:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=de.cyface.app.SomeTestClass

# Lint
./gradlew :ui:cyface:lintDebug
```

## Architecture

### Module Dependency Graph

```
ui/cyface, ui/r4r, ui/digural
    └── utils (shared UI: auth, navigation, settings, trips, statistics)
         ├── backend/datacapturing (foreground service for sensor capture)
         │    └── backend/persistence (Room DB: measurements, events, sensor data)
         ├── backend/synchronization (upload to server, WiFi detection)
         ├── energy_settings (background location permission prompts)
         └── camera_service (visual footage capture, used by Digural only)
```

### Module Details

- **`:utils`** -- Shared across all three apps. Contains OAuth 2.0 auth (AppAuth), navigation, Proto DataStore settings, trip management, incentives, and map utilities.
- **`:ui:cyface`** -- Main app with Google Maps, capturing UI, and settings.
- **`:ui:r4r`** -- R4R variant with speed display and trip grouping/markers.
- **`:ui:digural`** -- Digural variant adds WebDAV upload (sardine library), Retrofit HTTP client, and camera service integration. Has its own Proto DataStore settings and a separate `collectorApi` configuration.
- **Backend modules** (`persistence`, `datacapturing`, `synchronization`, `testutils`) live in the `backend/` Git submodule.
- **`energy_settings/`** and **`camera_service/`** are also Git submodules.

### Key Patterns

- **Data flow**: Sensors -> `CyfaceDataCapturingService` (foreground service) -> Room DB -> Synchronization -> Server
- **UI**: MVVM with ViewModel/LiveData, Navigation Component with Safe Args, ViewBinding
- **Settings**: Proto DataStore (protobuf-based), not SharedPreferences
- **Inter-component communication**: BroadcastReceiver / LocalBroadcastManager

## Submodules

The `backend/`, `camera_service/`, and `energy_settings/` directories are Git submodules. They can be used as local source (for development) or as published packages from GitHub Packages.

```bash
# Initialize submodules for local development
git submodule update --init --recursive

# Update submodule to latest
cd backend/ && git fetch -p && git checkout <branch> && cd ..
```

Submodule version numbers are documented in root `build.gradle` (`cyfaceAndroidBackendVersion`, `cyfaceEnergySettingsVersion`, `cyfaceCameraServiceVersion`). When upgrading, update the submodule commit and these version constants together.

## Build Configuration

- **Build system**: Gradle with Groovy DSL (not Kotlin DSL)
- **Java**: 21 (source and target)
- **Android SDK**: min 26, target/compile 35
- **All versions** are centralized as `ext` properties in root `build.gradle`
- **Credentials**: Copy `gradle.properties.template` to `gradle.properties` and fill in `githubUser`/`githubToken` (required for GitHub Packages access)
- **Debug builds** connect to staging/demo APIs; **release builds** connect to production APIs
- Each app variant has its own Google Maps API key and OAuth redirect URI configured via `gradle.properties`

## Commit Convention

`[TICKET-ID] Short imperative summary` -- e.g., `[LEIP-410] Fix Digural API URL not updating without app restart`

## Package Structure

- `de.cyface.app` -- Cyface app (`ui/cyface/src/main/kotlin/`)
- `de.cyface.app.r4r` -- R4R app (`ui/r4r/src/main/kotlin/`)
- `de.cyface.app.digural` -- Digural app (`ui/digural/src/main/kotlin/`)
- `de.cyface.app.utils` -- Shared utils (`utils/src/main/kotlin/`)

Each app's source has sub-packages: `auth`, `capturing` (with `settings`, `map`), `notification`, `utils`.
