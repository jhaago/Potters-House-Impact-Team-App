# Potter's House Impact Team App

An installed, offline-first mobile application for international Impact Teams. The first engineering milestone is a field proof that 5–10 phones can exchange useful, last-known location state while mobile data is disabled, screens are off, and the group is moving.

## Platform strategy

Android and iPhone are both product platforms. Android is the initial field-test platform because its background execution and nearby-device APIs make rapid radio experiments practical; iOS is not a later port.

- `shared/` is Kotlin Multiplatform code for the versioned protocol, ledger, relay rules, freshness, separation analysis, and use cases.
- `androidApp/` is the Android shell and will contain Android location, foreground-service, and Nearby Connections adapters.
- `iosApp/` is the native SwiftUI shell and will contain Core Location and iOS-supported peer transport adapters.
- Maps are presentation adapters. Tracking state remains useful without Google Maps, offline tiles, or any map at all.

iOS has stricter background peer-discovery limits than Android. The architecture therefore promises transport-independent store-and-forward delivery, not identical radio behavior on both operating systems. The iPhone proof must be tested early on real devices and must not be deferred until the Android product is complete.

## Current scope

This branch establishes the cross-platform shells and begins the offline tracking proof. It is intentionally not a polished user interface.

## Build

Requirements:

- JDK 17
- Android SDK 37 for Android
- Xcode 16 or newer on macOS for iOS

```bash
./gradlew :shared:jvmTest
./gradlew :androidApp:assembleDebug
```

Open `iosApp/ImpactTeamApp.xcodeproj` in Xcode to run the iPhone shell. The Xcode build phase builds and embeds the shared Kotlin framework.

## Design documents

- [Architecture design](docs/superpowers/specs/2026-09-27-impact-team-app-architecture-design.md)
- [Implementation plan](docs/superpowers/plans/2026-09-27-cross-platform-tracking-foundation-implementation.md)
- [Offline field-test procedure](docs/testing/offline-field-test.md)
- [Next-session handoff](docs/NEXT_SESSION.md)

No secrets or map API keys belong in source control. Tracking will only operate during an explicitly active trip session.
