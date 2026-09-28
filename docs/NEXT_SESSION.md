# Next Session Handoff

## Branch

Continue on `feature/offline-tracking-proof`. Do not merge into `main` until the proof has been reviewed and verified. Do not modify the Connection Card repository.

## Completed foundation

- Android Compose and iPhone SwiftUI application shells.
- Shared Kotlin Multiplatform protocol, validation, state ledger, freshness policy, bounded relay planning, and Room-backed persistence.
- Android active-trip foreground location service, persistent per-device sequencing, location-envelope validation, explicit stop control, and degraded health when permissions or location services are lost.
- Tests for wire compatibility, invalid peer input, sequence ordering, clock skew, device-identity rotation, relay limits, and A→B→C→leader propagation.
- Android CI build and macOS iPhone build workflow.
- CI-published Android debug APK artifact named `impact-team-android-debug-apk`.
- `iosArm64` and `iosSimulatorArm64` shared targets. Room 3.0.3 does not publish a legacy Intel-simulator artifact.

The current APK contains the background-tracking service foundation, but the proof UI does not start it yet and phone-to-phone discovery is not implemented. It is not yet a field-test build.

## Start here

Begin Task 7 in `docs/superpowers/plans/2026-09-27-cross-platform-tracking-foundation-implementation.md`: the peer transport and nearby exchange proof.

Before implementation:

1. Inspect the latest GitHub Actions run for this branch.
2. Confirm both `shared-and-android` and `ios` jobs pass.
3. Read the Task 7 brief and existing SDD ledger.
4. Preserve the shared transport boundary and store-and-forward protocol.
5. Reconcile the planned Android Nearby adapter with the iPhone-first transport findings before choosing the native radio implementation; BLE/GATT is the baseline mixed-platform candidate, while stronger transports remain optional and test-dependent.

After peer exchange, proceed to diagnostics UI and the field test in `docs/testing/offline-field-test.md`.

## iPhone constraint

iPhone is a first-class product platform, not a later port. Keep shared protocol, persistence, freshness, and relay policy platform-neutral. Core Location and the supported iOS peer transport require real-device, locked-screen testing before Android mesh behaviour can be assumed on iPhone.
