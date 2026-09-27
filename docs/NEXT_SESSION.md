# Next Session Handoff

## Branch

Continue on `feature/offline-tracking-proof`. Do not merge into `main` until the proof has been reviewed and verified. Do not modify the Connection Card repository.

## Completed foundation

- Android Compose and iPhone SwiftUI application shells.
- Shared Kotlin Multiplatform protocol, validation, state ledger, freshness policy, and bounded relay planning.
- Tests for wire compatibility, invalid peer input, sequence ordering, clock skew, device-identity rotation, relay limits, and A→B→C→leader propagation.
- Android CI build and macOS iPhone build workflow.
- `iosArm64`, `iosSimulatorArm64`, and `iosX64` shared targets.

The current APK is a shell and simulation-backed foundation. It does not yet perform real background tracking or phone-to-phone discovery.

## Start here

Implement Task 5 in `docs/superpowers/plans/2026-09-27-cross-platform-tracking-foundation-implementation.md`: multiplatform persistent tracking storage.

Before implementation:

1. Inspect the latest GitHub Actions run for this branch.
2. Confirm both `shared-and-android` and `ios` jobs pass.
3. Read the Task 5 brief and existing SDD ledger.
4. Use the official Room 3 Kotlin Multiplatform setup and keep platform filesystem builders outside common tracking logic.

After persistence, proceed to Android foreground/background location, Nearby Connections, diagnostics UI, and the field test in `docs/testing/offline-field-test.md`.

## iPhone constraint

iPhone is a first-class product platform, not a later port. Keep shared protocol, persistence, freshness, and relay policy platform-neutral. Core Location and the supported iOS peer transport require real-device, locked-screen testing before Android mesh behaviour can be assumed on iPhone.
