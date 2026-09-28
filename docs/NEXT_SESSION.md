# Next Session Handoff

## Branch

Continue on `feature/offline-tracking-proof`. Do not merge into `main` until the proof has been reviewed and verified. Do not modify the Connection Card repository.

## Completed foundation

- Android Compose and iPhone SwiftUI application shells.
- Shared Kotlin Multiplatform protocol, validation, state ledger, freshness policy, bounded relay planning, and Room-backed persistence.
- Android active-trip foreground location service, persistent per-device sequencing, location-envelope validation, explicit stop control, and degraded health when permissions or location services are lost.
- Shared V1 peer framing, digest-first transport router, malformed-input isolation, and Android Google Nearby Connections adapter using cluster topology.
- Tests for wire compatibility, invalid peer input, sequence ordering, clock skew, device-identity rotation, relay limits, and A→B→C→leader propagation.
- Android CI build and macOS iPhone build workflow.
- CI-published Android debug APK artifact named `impact-team-android-debug-apk`.
- `iosArm64` and `iosSimulatorArm64` shared targets. Room 3.0.3 does not publish a legacy Intel-simulator artifact.

The current APK contains background location and real Android phone-to-phone discovery/exchange, but the proof UI does not start or diagnose it yet. It is not yet a practical field-test build.

## Start here

Begin Task 8 in `docs/superpowers/plans/2026-09-27-cross-platform-tracking-foundation-implementation.md`: the member-state, setup, and diagnostics proof UI.

Before implementation:

1. Inspect the latest GitHub Actions run for this branch.
2. Confirm both `shared-and-android` and `ios` jobs pass.
3. Read the Task 8 brief and existing SDD ledger.
4. Preserve explicit trip activation and make Stop Tracking continuously available while active.
5. Surface Nearby authentication digits, connection failures, peer counts, record freshness, and arrival path without displaying coordinates in ordinary diagnostics.

After the diagnostics UI, proceed to the Android multi-phone field test in `docs/testing/offline-field-test.md`, then implement the iPhone Nearby/Core Location adapters and repeat the mixed-platform gate.

## iPhone constraint

iPhone is a first-class product platform, not a later port. Keep shared protocol, persistence, freshness, and relay policy platform-neutral. Core Location and the supported iOS peer transport require real-device, locked-screen testing before Android mesh behaviour can be assumed on iPhone.
