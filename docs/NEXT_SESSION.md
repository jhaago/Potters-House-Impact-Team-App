# Next Session Handoff

## Branch

Continue on `feature/offline-tracking-proof`. Do not merge into `main` until the proof has been reviewed and verified. Do not modify the Connection Card repository.

## Completed foundation

- Android Compose and iPhone SwiftUI application shells.
- Shared Kotlin Multiplatform protocol, validation, state ledger, freshness policy, bounded relay planning, and Room-backed persistence.
- Tests for wire compatibility, invalid peer input, sequence ordering, clock skew, device-identity rotation, relay limits, and A→B→C→leader propagation.
- Android CI build and macOS iPhone build workflow.
- `iosArm64` and `iosSimulatorArm64` shared targets. Room 3.0.3 does not publish a legacy Intel-simulator artifact.

The current APK is a shell and simulation-backed foundation. It does not yet perform real background tracking or phone-to-phone discovery.

## Start here

Implement Task 6 in `docs/superpowers/plans/2026-09-27-cross-platform-tracking-foundation-implementation.md`: the Android active-trip foreground location service.

Before implementation:

1. Inspect the latest GitHub Actions run for this branch.
2. Confirm both `shared-and-android` and `ios` jobs pass.
3. Read the Task 6 brief and existing SDD ledger.
4. Preserve the persisted sequence allocator and validate every location envelope before repository acceptance.

After Android foreground/background location, proceed to Nearby Connections, diagnostics UI, and the field test in `docs/testing/offline-field-test.md`.

## iPhone constraint

iPhone is a first-class product platform, not a later port. Keep shared protocol, persistence, freshness, and relay policy platform-neutral. Core Location and the supported iOS peer transport require real-device, locked-screen testing before Android mesh behaviour can be assumed on iPhone.
