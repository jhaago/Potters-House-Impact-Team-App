# Cross-Platform Tracking Foundation and Android Proof Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a compilable Android/iOS foundation and the smallest Android vertical slice that produces, persists, exchanges, relays, and visibly reports current or last-known team locations without mobile data.

**Architecture:** A Kotlin Multiplatform shared module owns wire models, validation, ledger merge rules, persistence contracts, anti-entropy sync planning, and transport contracts. Native Android and iOS shells own lifecycle-sensitive services and UI; this plan implements the Android foreground-location and Nearby Connections adapters while keeping the iOS target compiling and protocol-compatible.

**Tech Stack:** Kotlin 2.4.20, Gradle 9.6.0, Android Gradle Plugin 9.4.0, Android API 36, JDK 17, Jetpack Compose BOM 2026.09.00, Room 3.0.3 KMP, kotlinx.serialization JSON, kotlinx.coroutines, Google Play services Nearby 19.5.0, SwiftUI/Xcode for the iOS shell.

**Spec:** `docs/superpowers/specs/2026-09-27-impact-team-app-architecture-design.md`

## Global Constraints

- Android `minSdk = 26`, `compileSdk = 36`, and `targetSdk = 36`.
- iOS deployment target is 16.0; create `iosArm64` and `iosSimulatorArm64` shared targets.
- Use JDK 17 and pin every dependency in `gradle/libs.versions.toml`; no dynamic or beta versions.
- `commonMain` must not import Android, Google Play services, Swift, UIKit, CoreLocation, or MapKit types.
- Android UI is Jetpack Compose; the iOS shell is SwiftUI. Do not share UI in this milestone.
- Production package/bundle namespace is `org.pottershouse.impactteam`.
- Tracking data is independent of every map provider; this plan does not add Google Maps or MapLibre.
- Tracking runs only for an explicitly active trip and must have a clear Android foreground-service notification.
- No API keys, signing files, trip secrets, personal data, or precise real-world test coordinates are committed.
- Wire JSON uses explicit serial names, `protocolVersion = 1`, ignores unknown fields, and never relies on Kotlin class names.
- Location ordering uses `originDeviceId + originSequence`; wall-clock timestamps never override a higher sequence.
- Substantial product code is developed on `feature/offline-tracking-proof`; do not merge it into `main` in this plan.
- The existing Connection Card repository remains untouched.

## Review Focus

1. **Clock skew:** a location whose wall clock is far in the future must not replace a higher origin sequence; Task 3 adds `futureClockDoesNotBeatHigherSequence`.
2. **Reinstallation/device identity rotation:** a new device epoch starting at sequence one must coexist with the old device identity; Task 3 adds `newDeviceIdentityStartsIndependentSequence`.
3. **Relay storms:** a record received repeatedly or returned by the peer that supplied it must not keep circulating; Task 4 adds `duplicateAndImmediateEchoAreSuppressed`.
4. **Permission/radio loss during an active trip:** tracking state must become degraded and the service must remain controllable rather than crash; Task 6 adds `permissionRevocationPublishesDegradedHealth`.
5. **Corrupt or incompatible persisted records:** startup must quarantine invalid rows and preserve valid current state; Task 5 adds `corruptEnvelopeIsQuarantinedWithoutLosingValidRows`.

---

## File structure

The first implementation keeps one shared Gradle module to reduce scaffolding overhead while preserving package boundaries:

- `shared/src/commonMain/kotlin/org/pottershouse/impactteam/domain/`: identifiers and trip/member/location types.
- `shared/src/commonMain/kotlin/org/pottershouse/impactteam/protocol/`: versioned envelopes and codecs.
- `shared/src/commonMain/kotlin/org/pottershouse/impactteam/state/`: ledger, freshness, and snapshots.
- `shared/src/commonMain/kotlin/org/pottershouse/impactteam/sync/`: digest/delta planning and relay policy.
- `shared/src/commonMain/kotlin/org/pottershouse/impactteam/storage/`: Room entities, DAO, and repository.
- `shared/src/commonMain/kotlin/org/pottershouse/impactteam/transport/`: platform-neutral transport events and commands.
- `androidApp/src/main/kotlin/org/pottershouse/impactteam/android/`: Compose shell, Android location/service/Nearby adapters.
- `iosApp/`: minimal SwiftUI application consuming the shared framework.
- `docs/testing/`: repeatable physical test procedures and result templates.

### Task 1: Cross-platform build and application shells

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `gradlew`, `gradlew.bat`, and wrapper JAR using the Gradle wrapper task
- Create: `.gitignore`
- Create: `README.md`
- Create: `shared/build.gradle.kts`
- Create: `shared/src/commonMain/kotlin/org/pottershouse/impactteam/AppIdentity.kt`
- Create: `shared/src/commonTest/kotlin/org/pottershouse/impactteam/AppIdentityTest.kt`
- Create: `androidApp/build.gradle.kts`
- Create: `androidApp/src/main/AndroidManifest.xml`
- Create: `androidApp/src/main/kotlin/org/pottershouse/impactteam/android/MainActivity.kt`
- Create: `androidApp/src/main/kotlin/org/pottershouse/impactteam/android/ImpactTeamApp.kt`
- Create: `iosApp/iosApp/ImpactTeamApp.swift`
- Create: `iosApp/iosApp/ContentView.swift`
- Create: `iosApp/iosApp.xcodeproj/project.pbxproj`
- Create: `.github/workflows/verify.yml`

**Interfaces:**
- Produces: `data class AppIdentity(val applicationName: String, val protocolVersion: Int)`.
- Produces: shared framework `ImpactTeamShared` importable from Swift.
- Produces: Gradle tasks `:shared:allTests` and `:androidApp:assembleDebug`.

- [ ] **Step 1: Create a failing common test**

Add `AppIdentityTest.defaultIdentityIsStable`, asserting application name `"Potter's House Impact Team"` and protocol version `1`.

- [ ] **Step 2: Generate the pinned build scaffold**

Configure Kotlin 2.4.20, AGP 9.4.0, Gradle 9.6.0, Compose BOM 2026.09.00, Room 3.0.3, Nearby 19.5.0, JVM 17, Android 26/36/36, and iOS 16.0. Use Google's `com.android.kotlin.multiplatform.library` plugin for the shared Android target and a separate `com.android.application` Android entry module.

- [ ] **Step 3: Implement the minimal shared identity and native shells**

The Android screen and SwiftUI screen each render the application name plus `"Tracking proof not started"`. No navigation framework is added yet.

- [ ] **Step 4: Add CI build checks**

On Linux run `./gradlew :shared:allTests :androidApp:assembleDebug`. On macOS additionally link the iOS simulator shared framework and build the iOS scheme without signing.

- [ ] **Step 5: Verify the scaffold**

Run: `./gradlew --version && ./gradlew :shared:allTests :androidApp:assembleDebug`  
Expected: Gradle 9.6.0, JDK 17, all tests PASS, Android debug APK assembled.

On macOS run the documented `xcodebuild` command. Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 6: Commit**

```bash
git add .
git commit -m "build: scaffold cross-platform impact team app"
```

### Task 2: Versioned location wire model and validation

**Files:**
- Create: `shared/src/commonMain/kotlin/org/pottershouse/impactteam/domain/Identifiers.kt`
- Create: `shared/src/commonMain/kotlin/org/pottershouse/impactteam/domain/TeamRole.kt`
- Create: `shared/src/commonMain/kotlin/org/pottershouse/impactteam/domain/GeoPoint.kt`
- Create: `shared/src/commonMain/kotlin/org/pottershouse/impactteam/protocol/LocationPayload.kt`
- Create: `shared/src/commonMain/kotlin/org/pottershouse/impactteam/protocol/TrackingEnvelope.kt`
- Create: `shared/src/commonMain/kotlin/org/pottershouse/impactteam/protocol/EnvelopeCodec.kt`
- Create: `shared/src/commonMain/kotlin/org/pottershouse/impactteam/protocol/EnvelopeValidator.kt`
- Create: `shared/src/commonTest/kotlin/org/pottershouse/impactteam/protocol/EnvelopeCodecTest.kt`
- Create: `shared/src/commonTest/kotlin/org/pottershouse/impactteam/protocol/EnvelopeValidatorTest.kt`
- Create: `shared/src/commonTest/resources/protocol/location-envelope-v1.json`

**Interfaces:**
- Produces value types: `TripId`, `TeamId`, `MemberId`, `DeviceId`, `RecordId`.
- Produces: `TrackingEnvelope(protocolVersion, recordId, tripId, teamId, memberId, originDeviceId, originSequence, createdAtEpochMillis, priority, expiresAtEpochMillis, payload)`.
- Produces: `EnvelopeCodec.encode(envelope): String` and `decode(json): DecodeResult`.
- Produces: `EnvelopeValidator.validate(envelope, nowEpochMillis): ValidationResult`.

- [ ] **Step 1: Write failing codec tests**

Add tests asserting an exact golden JSON fixture, round-trip equality, unknown-field tolerance, and rejection of unsupported protocol version `2`.

- [ ] **Step 2: Write failing validation tests**

Cover latitude outside `[-90, 90]`, longitude outside `[-180, 180]`, non-positive sequence, blank identifiers, accuracy below zero, battery outside `[0, 100]`, expired envelopes, and a valid V1 record.

- [ ] **Step 3: Implement immutable models and deterministic JSON codec**

Use explicit `@SerialName` values and `Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }`. Use `kotlin.time.Instant` conversions only at domain boundaries; persist epoch milliseconds on the wire.

- [ ] **Step 4: Implement validation**

Return typed failures rather than throwing for untrusted peer input. Cap encoded location envelopes at 16 KiB.

- [ ] **Step 5: Verify**

Run: `./gradlew :shared:allTests --tests "*Envelope*"`  
Expected: all codec and validation tests PASS on JVM/Android unit target and iOS simulator test target where available.

- [ ] **Step 6: Commit**

```bash
git add shared
git commit -m "feat: define versioned location protocol"
```

### Task 3: Deterministic ledger and freshness reducer

**Files:**
- Create: `shared/src/commonMain/kotlin/org/pottershouse/impactteam/state/ArrivalPath.kt`
- Create: `shared/src/commonMain/kotlin/org/pottershouse/impactteam/state/ObservedEnvelope.kt`
- Create: `shared/src/commonMain/kotlin/org/pottershouse/impactteam/state/AcceptResult.kt`
- Create: `shared/src/commonMain/kotlin/org/pottershouse/impactteam/state/TrackingLedger.kt`
- Create: `shared/src/commonMain/kotlin/org/pottershouse/impactteam/state/FreshnessPolicy.kt`
- Create: `shared/src/commonMain/kotlin/org/pottershouse/impactteam/state/MemberTrackingState.kt`
- Create: `shared/src/commonTest/kotlin/org/pottershouse/impactteam/state/TrackingLedgerTest.kt`
- Create: `shared/src/commonTest/kotlin/org/pottershouse/impactteam/state/FreshnessPolicyTest.kt`

**Interfaces:**
- Consumes: validated `TrackingEnvelope` from Task 2.
- Produces: `TrackingLedger.accept(envelope, receivedAtEpochMillis, arrivalPath, suppliedByPeerId): AcceptResult`.
- Produces: `TrackingLedger.snapshot(nowEpochMillis): List<MemberTrackingState>`.
- Produces: `TrackingLedger.syncCandidates(nowEpochMillis): List<ObservedEnvelope>` containing a bounded, deterministic relay window.
- Produces freshness enum `CURRENT`, `BECOMING_STALE`, `STALE`.

- [ ] **Step 1: Write failing ordering and deduplication tests**

Add `higherSequenceReplacesLowerSequence`, `lowerSequenceCannotReplaceNewerState`, `sameRecordIsDuplicate`, `futureClockDoesNotBeatHigherSequence`, and `newDeviceIdentityStartsIndependentSequence`.

- [ ] **Step 2: Write failing freshness tests**

Initial proof thresholds are current through 30 seconds, becoming stale after 30 seconds through 120 seconds, and stale after 120 seconds. Boundary values are pinned in tests.

- [ ] **Step 3: Implement the in-memory ledger**

Index accepted locations by `originDeviceId`; maintain record-ID deduplication and member-to-current-device projection. Preserve local receipt time and arrival path independently of origin time. Retain a bounded deterministic sync-candidate window: the newest accepted location per origin plus up to four immediately preceding unexpired locations per origin.

- [ ] **Step 4: Implement freshness reduction**

Freshness uses local receipt age. Negative age caused by local clock correction is clamped to zero. Origin timestamps are displayed metadata only.

- [ ] **Step 5: Verify**

Run: `./gradlew :shared:allTests --tests "*TrackingLedgerTest" --tests "*FreshnessPolicyTest"`  
Expected: all tests PASS.

- [ ] **Step 6: Commit**

```bash
git add shared
git commit -m "feat: add deterministic tracking ledger"
```

### Task 4: Anti-entropy digest, delta, priority, and relay controls

**Files:**
- Create: `shared/src/commonMain/kotlin/org/pottershouse/impactteam/sync/SyncDigest.kt`
- Create: `shared/src/commonMain/kotlin/org/pottershouse/impactteam/sync/SyncBatch.kt`
- Create: `shared/src/commonMain/kotlin/org/pottershouse/impactteam/sync/RelayPolicy.kt`
- Create: `shared/src/commonMain/kotlin/org/pottershouse/impactteam/sync/SyncPlanner.kt`
- Create: `shared/src/commonTest/kotlin/org/pottershouse/impactteam/sync/SyncPlannerTest.kt`
- Create: `shared/src/commonTest/kotlin/org/pottershouse/impactteam/sync/RelayPolicyTest.kt`
- Create: `shared/src/commonTest/kotlin/org/pottershouse/impactteam/sync/MeshScenarioTest.kt`

**Interfaces:**
- Consumes: `TrackingLedger.syncCandidates(nowEpochMillis)` and peer/source metadata from Task 3.
- Produces: `SyncDigest(highestSequenceByOrigin: Map<DeviceId, Long>, importantRecordIds: Set<RecordId>)`.
- Produces: `SyncPlanner.plan(peerId, peerDigest, nowEpochMillis, maxRecords = 50, maxBytes = 64 * 1024): SyncBatch`.
- Produces: `RelayPolicy.canOffer(observedEnvelope, peerId, nowEpochMillis): Boolean`.

- [ ] **Step 1: Write failing digest/delta tests**

Assert that only records newer than the peer high-water mark are selected, priority sorts before recency, limits are respected without splitting a record, and expired records are excluded.

- [ ] **Step 2: Write failing relay safety tests**

Add `duplicateAndImmediateEchoAreSuppressed`, maximum relay count `8`, expired-record rejection, and no offer to a peer already recorded in the bounded recent-route list.

- [ ] **Step 3: Write a failing four-device store-and-forward simulation**

Create A, B, C, and leader ledgers. Exchange only A↔B, B↔C, and C↔leader. Assert the leader obtains A's newest record, each accepted record ID appears once, and total offers remain bounded.

- [ ] **Step 4: Implement planner and relay policy**

Use digests and immutable envelopes; route observations stay local and are not included in the origin signature. Location history retains the newest record plus a small bounded relay window.

- [ ] **Step 5: Verify**

Run: `./gradlew :shared:allTests --tests "*SyncPlannerTest" --tests "*RelayPolicyTest" --tests "*MeshScenarioTest"`  
Expected: all tests PASS, including bounded A→B→C→leader propagation.

- [ ] **Step 6: Commit**

```bash
git add shared
git commit -m "feat: add bounded store-and-forward sync"
```

### Task 5: Multiplatform persistent ledger

**Files:**
- Create: `shared/src/commonMain/kotlin/org/pottershouse/impactteam/storage/TrackingRecordEntity.kt`
- Create: `shared/src/commonMain/kotlin/org/pottershouse/impactteam/storage/TrackingRecordDao.kt`
- Create: `shared/src/commonMain/kotlin/org/pottershouse/impactteam/storage/ImpactTeamDatabase.kt`
- Create: `shared/src/commonMain/kotlin/org/pottershouse/impactteam/storage/DatabaseFactory.kt`
- Create: `shared/src/androidMain/kotlin/org/pottershouse/impactteam/storage/DatabaseFactory.android.kt`
- Create: `shared/src/iosMain/kotlin/org/pottershouse/impactteam/storage/DatabaseFactory.ios.kt`
- Create: `shared/src/commonMain/kotlin/org/pottershouse/impactteam/storage/PersistentTrackingRepository.kt`
- Create: `shared/src/commonTest/kotlin/org/pottershouse/impactteam/storage/PersistentTrackingRepositoryTest.kt`
- Create: `shared/schemas/` generated Room schemas

**Interfaces:**
- Consumes: codec, validator, ledger, and sync history.
- Produces: `PersistentTrackingRepository.accept(observed): AcceptResult`.
- Produces: `loadLedger(tripId): TrackingLedger`, `nextOriginSequence(deviceId): Long`, and `compact(nowEpochMillis): CompactionResult`.

- [ ] **Step 1: Write failing repository tests**

Cover restart restoration, atomic sequence allocation, duplicate insert idempotence, expiry compaction, and `corruptEnvelopeIsQuarantinedWithoutLosingValidRows`.

- [ ] **Step 2: Define Room 3.0.3 KMP schema and platform factories**

Store raw canonical envelope JSON, searchable IDs/sequences, local receipt metadata, arrival path, supplier peer, relay count, and quarantine reason. Add unique indexes for record ID and `originDeviceId + originSequence`.

- [ ] **Step 3: Implement transactional acceptance and restoration**

Validate before projection. A bad row is moved to quarantine and omitted from current state; it does not abort restoration of other rows.

- [ ] **Step 4: Add Room schema verification**

Export schemas to version control and run Room migration/schema checks in CI.

- [ ] **Step 5: Verify**

Run: `./gradlew :shared:allTests :shared:kspAndroid :androidApp:assembleDebug`  
Expected: repository tests PASS, generated database code succeeds, schema is exported.

- [ ] **Step 6: Commit**

```bash
git add shared
git commit -m "feat: persist offline tracking ledger"
```

### Task 6: Android active-trip foreground location service

**Files:**
- Create: `shared/src/commonMain/kotlin/org/pottershouse/impactteam/domain/ActiveTripSession.kt`
- Create: `shared/src/commonMain/kotlin/org/pottershouse/impactteam/domain/TrackingHealth.kt`
- Create: `shared/src/commonMain/kotlin/org/pottershouse/impactteam/transport/LocationSource.kt`
- Create: `androidApp/src/main/kotlin/org/pottershouse/impactteam/android/tracking/AndroidLocationSource.kt`
- Create: `androidApp/src/main/kotlin/org/pottershouse/impactteam/android/tracking/TrackingForegroundService.kt`
- Create: `androidApp/src/main/kotlin/org/pottershouse/impactteam/android/tracking/TrackingNotificationFactory.kt`
- Create: `androidApp/src/main/kotlin/org/pottershouse/impactteam/android/tracking/ActiveTripController.kt`
- Create: `androidApp/src/test/kotlin/org/pottershouse/impactteam/android/tracking/ActiveTripControllerTest.kt`
- Create: `androidApp/src/test/kotlin/org/pottershouse/impactteam/android/tracking/LocationEnvelopeFactoryTest.kt`
- Modify: `androidApp/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: persistent sequence allocation and envelope codec.
- Produces: `ActiveTripController.start(session)`, `stop(reason)`, and `health: StateFlow<TrackingHealth>`.
- Produces Android location samples at requested cadence and accepted local envelopes.

- [ ] **Step 1: Write failing controller tests**

Assert start requires an explicit active session, second start is idempotent, stop cancels production, low-accuracy samples are retained with their accuracy rather than falsely improved, and `permissionRevocationPublishesDegradedHealth`.

- [ ] **Step 2: Implement notification and manifest declarations**

Declare fine/coarse/background location, foreground service, foreground service location, connected-device/Bluetooth permissions needed by later transport, boot receiver placeholder disabled by default, and `foregroundServiceType="location|connectedDevice"`. Notification text must say the active trip is sharing location.

- [ ] **Step 3: Implement Android location adapter**

Use Fused Location Provider. Initial request cadence: 15 seconds moving; the stationary and SOS policies remain domain configuration but are not activated until later milestones.

- [ ] **Step 4: Implement service/controller integration**

Each accepted platform location obtains the next persisted origin sequence, creates a V1 envelope, validates it, and accepts it into the repository. Permission/radio changes update health instead of crashing.

- [ ] **Step 5: Verify**

Run: `./gradlew :androidApp:testDebugUnitTest :androidApp:assembleDebug`  
Expected: tests PASS; manifest merger shows required service types and permissions; APK assembles.

- [ ] **Step 6: Commit**

```bash
git add shared androidApp
git commit -m "feat: add active-trip Android tracking service"
```

### Task 7: Android Nearby Connections transport

**Files:**
- Create: `shared/src/commonMain/kotlin/org/pottershouse/impactteam/transport/PeerId.kt`
- Create: `shared/src/commonMain/kotlin/org/pottershouse/impactteam/transport/PeerTransport.kt`
- Create: `shared/src/commonMain/kotlin/org/pottershouse/impactteam/transport/TransportEvent.kt`
- Create: `shared/src/commonMain/kotlin/org/pottershouse/impactteam/transport/TransportRouter.kt`
- Create: `shared/src/commonTest/kotlin/org/pottershouse/impactteam/transport/TransportRouterTest.kt`
- Create: `androidApp/src/main/kotlin/org/pottershouse/impactteam/android/nearby/NearbyClient.kt`
- Create: `androidApp/src/main/kotlin/org/pottershouse/impactteam/android/nearby/GoogleNearbyClient.kt`
- Create: `androidApp/src/main/kotlin/org/pottershouse/impactteam/android/nearby/NearbyPeerTransport.kt`
- Create: `androidApp/src/main/kotlin/org/pottershouse/impactteam/android/nearby/NearbyPayloadCodec.kt`
- Create: `androidApp/src/test/kotlin/org/pottershouse/impactteam/android/nearby/NearbyPeerTransportTest.kt`

**Interfaces:**
- Consumes: active trip, `SyncDigest`, `SyncBatch`, and persistent repository.
- Produces: `PeerTransport.start(session): Flow<TransportEvent>`, `send(peerId, message)`, and `stop()`.
- Produces transport events for discovered, connected, disconnected, digest received, batch received, and failure.

- [ ] **Step 1: Write failing router tests with fake transports**

Assert router start/stop is idempotent, incoming valid batches reach the repository, invalid payloads are rejected without terminating the event stream, and disconnected peers can reconnect.

- [ ] **Step 2: Write failing Nearby adapter tests against `NearbyClient` fake**

Assert P2P_CLUSTER strategy, service ID `org.pottershouse.impactteam.nearby.v1`, simultaneous advertise/discover, authentication-token exposure, byte payload limits, digest-first exchange, and clean stop.

- [ ] **Step 3: Implement payload framing**

Define message kinds `HELLO`, `DIGEST`, `BATCH`, and `ERROR` with V1 framing. Cap each byte payload at 64 KiB and each batch at 50 envelopes.

- [ ] **Step 4: Implement Google Nearby adapter**

Wrap Play services behind `NearbyClient`. Do not expose Google types to shared code. Connection authentication must be surfaced for the proof UI; automatic trusted enrollment is deferred.

- [ ] **Step 5: Connect transport to the active service**

When active, advertise and discover; on connection exchange digests and planned deltas. On stop, terminate all endpoints, advertising, and discovery.

- [ ] **Step 6: Verify**

Run: `./gradlew :shared:allTests :androidApp:testDebugUnitTest :androidApp:assembleDebug`  
Expected: all tests PASS and APK assembles.

- [ ] **Step 7: Commit**

```bash
git add shared androidApp
git commit -m "feat: exchange tracking state over Nearby"
```

### Task 8: Basic member-state and diagnostics UI

**Files:**
- Create: `androidApp/src/main/kotlin/org/pottershouse/impactteam/android/ui/TrackingProofViewModel.kt`
- Create: `androidApp/src/main/kotlin/org/pottershouse/impactteam/android/ui/TrackingProofScreen.kt`
- Create: `androidApp/src/main/kotlin/org/pottershouse/impactteam/android/ui/MemberStateRow.kt`
- Create: `androidApp/src/main/kotlin/org/pottershouse/impactteam/android/ui/DiagnosticsScreen.kt`
- Create: `androidApp/src/main/kotlin/org/pottershouse/impactteam/android/ui/ProofSetupScreen.kt`
- Create: `androidApp/src/test/kotlin/org/pottershouse/impactteam/android/ui/TrackingProofViewModelTest.kt`
- Create: `androidApp/src/androidTest/kotlin/org/pottershouse/impactteam/android/ui/TrackingProofScreenTest.kt`
- Modify: `androidApp/src/main/kotlin/org/pottershouse/impactteam/android/ImpactTeamApp.kt`

**Interfaces:**
- Consumes: session controller, repository snapshots, freshness, transport events, and health.
- Produces: proof setup for a non-production local test identity and trip.
- Produces: visible per-member name/ID, update age, freshness, battery, accuracy, and arrival path.
- Produces: redacted diagnostic counters.

- [ ] **Step 1: Write failing view-model tests**

Assert idle, permission-required, active, degraded, and stopped states; sorted member rows; explicit `Direct peer`, `Relayed (N hops)`, `Local`, and `Last known` labels; no stale state labelled live.

- [ ] **Step 2: Write failing Compose semantics tests**

Assert Start Tracking is deliberate, Stop Tracking is always available while active, ongoing tracking status is prominent, stale rows expose `Last known`, and diagnostics do not display coordinates by default.

- [ ] **Step 3: Implement proof setup and state list**

Use typed local test IDs. This is a field-test harness, not production trip onboarding. Keep the screen functional and plain.

- [ ] **Step 4: Implement diagnostics**

Show nearby count, last exchange age, accepted/duplicate/rejected counts, transport failures, and battery sample. Add copy/export of redacted text only.

- [ ] **Step 5: Verify**

Run: `./gradlew :androidApp:testDebugUnitTest :androidApp:connectedDebugAndroidTest :androidApp:assembleDebug`  
Expected: unit and UI tests PASS when an emulator/device is available; otherwise document the unrun connected test and keep CI unit/build gates passing.

- [ ] **Step 6: Commit**

```bash
git add androidApp
git commit -m "feat: add offline tracking proof interface"
```

### Task 9: End-to-end simulation, field procedure, and handoff

**Files:**
- Create: `shared/src/commonTest/kotlin/org/pottershouse/impactteam/scenario/OfflineTrackingScenarioTest.kt`
- Create: `docs/testing/android-offline-tracking-field-test.md`
- Create: `docs/testing/android-offline-tracking-results-template.md`
- Create: `docs/testing/device-capability-matrix.md`
- Modify: `README.md`

**Interfaces:**
- Consumes all prior shared interfaces and Android build output.
- Produces a repeatable two-phone smoke test, four-phone forced-relay test, and 5–10-phone locked-screen field protocol.

- [ ] **Step 1: Add the complete shared simulation**

Simulate 10 devices, disconnections, shuffled delivery, duplicates, a future wall clock, stale replay, and A→B→C→leader relay. Assert deterministic final locations, bounded offers, and no stale replacement.

- [ ] **Step 2: Document the two-phone test**

Include installation, permissions, mobile-data-off conditions, Wi-Fi/Bluetooth state, screen lock, walking separation, expected UI evidence, log export, and stop/cleanup.

- [ ] **Step 3: Document the forced-relay and group tests**

Specify physical isolation so A cannot directly reach leader, timed encounters, expected maximum propagation time, device/OS/battery recording, process kill, reboot, permission revocation, and rejoin cases.

- [ ] **Step 4: Add a results template and capability matrix**

Record pass/fail separately for location production, discovery, connection, digest, payload, relay, freshness, restart, screen-off duration, and battery delta. Leave results blank until physical testing; do not manufacture evidence.

- [ ] **Step 5: Run full verification**

Run: `./gradlew clean :shared:allTests :androidApp:testDebugUnitTest :androidApp:lintDebug :androidApp:assembleDebug`  
Expected: BUILD SUCCESSFUL with no failing tests or lint errors.

On macOS run the documented shared iOS framework and Xcode build. Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 6: Inspect the branch**

Run: `git status --short && git log --oneline --decorate -10`  
Expected: clean worktree; logical commits for scaffold, protocol, ledger, sync, persistence, location, Nearby, UI, and field documentation.

- [ ] **Step 7: Commit**

```bash
git add README.md shared docs/testing
git commit -m "test: add offline tracking proof scenarios"
```

## Completion boundary

This plan is complete when:

- Android and iOS shells compile.
- Shared protocol/state/sync/persistence tests pass.
- Android can produce local background location records during an explicitly active proof trip.
- Android Nearby code can discover, connect, exchange digests/batches, and persist received records.
- The Android proof UI never presents stale data as live.
- Simulation proves bounded A→B→C→leader propagation.
- Physical field procedures are ready.

Physical 5–10-phone evidence is not claimed by code completion. Google Maps, offline maps, production onboarding/security, separation warnings, SOS, alerts, check-ins, rally points, and Connection Cards remain later milestones.
