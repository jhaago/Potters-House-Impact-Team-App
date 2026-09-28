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
- Android proof setup, explicit start/stop, truthful member freshness/source rows, authentication/failure diagnostics, and redacted diagnostic copy.
- Current Android Nearby permissions, including nearby Wi-Fi on Android 13+.
- A ten-device shared simulation covering shuffled delivery, duplicates, future wall clocks, stale replay, and forced multi-hop propagation.
- `iosArm64` and `iosSimulatorArm64` shared targets. Room 3.0.3 does not publish a legacy Intel-simulator artifact.

The current APK is ready for controlled Android field testing. Code and CI do not prove real screen-off radio reliability; only the documented physical tests can supply that evidence.

## Start here

Run the physical Android proof in `docs/testing/android-offline-tracking-field-test.md`. Start with two phones, then four-phone forced relay, then 5–10 phones only after the smaller gates pass.

Before implementation:

1. Download the APK from the latest successful `Verify` run.
2. Copy `docs/testing/android-offline-tracking-results-template.md` for the test day.
3. Record every device in `docs/testing/device-capability-matrix.md` from observed results only.
4. File defects with phone model, OS, screen state, elapsed time, last exchange age, and redacted diagnostics.
5. Do not merge into `main` until the foundation branch receives its final review and physical-test findings are understood.

After the Android proof, implement the iPhone Nearby/Core Location adapters and repeat the direct, locked-screen, and mixed-platform gates on physical iPhones.

## iPhone constraint

iPhone is a first-class product platform, not a later port. Keep shared protocol, persistence, freshness, and relay policy platform-neutral. Core Location and the supported iOS peer transport require real-device, locked-screen testing before Android mesh behaviour can be assumed on iPhone.
