# Potter's House Impact Team App

An installed, offline-first mobile application for international Impact Teams. The first engineering milestone is a field proof that 5–10 phones can exchange useful, last-known location state while mobile data is disabled, screens are off, and the group is moving.

## Platform strategy

Android and iPhone are both product platforms. Android is the initial field-test platform because its background execution and nearby-device APIs make rapid radio experiments practical; iOS is not a later port.

- `shared/` is Kotlin Multiplatform code for the versioned protocol, ledger, relay rules, freshness, separation analysis, and use cases.
- `androidApp/` is the Android shell and will contain Android location, foreground-service, and Nearby Connections adapters.
- `iosApp/` is the native SwiftUI shell and will contain Core Location and iOS-supported peer transport adapters.
- Maps are presentation adapters. Tracking state remains useful without Google Maps, offline tiles, or any map at all.

iOS has stricter background peer-discovery limits than Android. The architecture therefore promises transport-independent store-and-forward delivery, not identical radio behavior on both operating systems. The iPhone proof must be tested early on real devices and must not be deferred until the Android product is complete.

Offline records use Room 3 with the bundled SQLite driver on Android and iPhone. The supported Apple targets are physical iPhones (`iosArm64`) and Apple-silicon simulators (`iosSimulatorArm64`). Room 3.0.3 does not publish a legacy Intel-simulator binary; that simulator limitation does not affect physical iPhones.

## Current proof scope

The foundation branch now contains a usable Android field-test harness: explicit proof-trip setup, foreground/background location capture, persistent last-known state, Google Nearby peer exchange, bounded store-and-forward relay, truthful freshness labels, and redacted diagnostics. It is intentionally an engineering proof rather than the finished member or leader interface.

The iPhone shell and shared Kotlin framework compile, but native iPhone Core Location and Nearby adapters are not implemented yet. Android results must not be assumed to apply to locked iPhones.

## Build

Requirements:

- JDK 17
- Android SDK 37 for Android
- Xcode 16 or newer on macOS for iOS

```bash
./gradlew clean :shared:allTests :shared:kspAndroid :androidApp:testDebugUnitTest :androidApp:compileDebugAndroidTestKotlin :androidApp:lintDebug :androidApp:assembleDebug
```

GitHub Actions publishes `impact-team-android-debug-apk` from every successful feature-branch verification run. The APK is for controlled proof testing only; it uses local test identities and automatically accepts Nearby connections after surfacing authentication digits.

Open `iosApp/ImpactTeamApp.xcodeproj` in Xcode to run the iPhone shell. The Xcode build phase builds and embeds the shared Kotlin framework.

## Design documents

- [Architecture design](docs/superpowers/specs/2026-09-27-impact-team-app-architecture-design.md)
- [Implementation plan](docs/superpowers/plans/2026-09-27-cross-platform-tracking-foundation-implementation.md)
- [Android offline field-test procedure](docs/testing/android-offline-tracking-field-test.md)
- [Field-test results template](docs/testing/android-offline-tracking-results-template.md)
- [Device capability matrix](docs/testing/device-capability-matrix.md)
- [Next-session handoff](docs/NEXT_SESSION.md)

No secrets or map API keys belong in source control. Tracking will only operate during an explicitly active trip session.
