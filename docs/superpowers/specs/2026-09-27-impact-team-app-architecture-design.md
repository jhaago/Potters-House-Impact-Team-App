# Potter's House Impact Team App — Architecture Design

**Date:** 2026-09-27  
**Status:** Proposed specification for user review  
**Repository:** `jhaago/Potters-House-Impact-Team-App`

## 1. Purpose

The Potter's House Impact Team App is an installed mobile application for international Impact Team trips. Its primary operational purpose is to help leaders account for members, exchange urgent information, and locate or assist separated members even when cellular data is unavailable or unreliable.

The long-term product combines two independently bounded capabilities:

1. **Impact Team operations:** active-trip location sharing, offline peer exchange, leader awareness, SOS, alerts, check-ins, rally points, and map/radar presentation.
2. **Connection Cards:** a separate module that will later integrate selected functionality from the existing Connection Card system without coupling that system to tracking.

The first engineering objective is not a polished interface. It is to determine whether ordinary phones can maintain useful, timely team state while people move, screens are locked, and mobile data is disabled.

## 2. Product principles

1. **Offline first:** loss of internet must degrade cloud synchronization and online imagery, not stop local tracking.
2. **iPhone is first-class:** Android is the initial instrumentation and proof platform, but shared code and protocol decisions must support iOS from the first commit.
3. **Tracking is trip-scoped:** normal location sharing exists only during an explicitly active trip session.
4. **Stale data is never presented as live:** every location has a visible freshness state and last-known semantics.
5. **Members see simplicity; leaders see operations:** member and leader experiences are separate navigation roots, not one screen with extra buttons.
6. **Tracking is map-independent:** team state exists independently of Google Maps, offline maps, or the radar fallback.
7. **Transport is replaceable:** internet, Nearby Connections, and future transports implement a common interface. LoRa is not implemented in V1.
8. **Evidence before expansion:** real multi-phone results determine whether the app-only approach is reliable enough before secondary features are polished.

## 3. Scope

### 3.1 V1 target

V1 will support:

- trip creation and secure joining
- teams and role-based membership
- member, team leader, and trip leader experiences
- foreground/background trip tracking within platform rules
- opportunistic local peer discovery and exchange
- store-and-forward relaying
- last-known state and freshness
- leader map and operational status
- online Google Hybrid/Satellite presentation
- licensed offline map presentation
- mapless radar/compass presentation
- separation warnings
- SOS with acknowledgement
- targeted leader alerts with acknowledgement
- check-ins
- rally points
- battery and network health
- explicit start/stop and privacy controls
- Connection Card navigation/module boundary

### 3.2 Deferred

The following are explicitly outside V1:

- LoRa hardware
- general-purpose chat
- long-term movement history and analytics
- detailed breadcrumbs
- advanced geofencing
- full web administration
- social features
- dependence on large cloud infrastructure

## 4. Success criteria

### 4.1 Android feasibility gate

Using approximately 5–10 Android phones with mobile data disabled and screens locked:

- devices continue producing locations during an active trip
- devices discover and exchange state without an internet connection
- a forced relay path such as A → B → C → leader propagates a record
- duplicate records do not cause uncontrolled retransmission
- stale records cannot replace newer records
- devices converge after temporarily leaving and rejoining
- persisted state survives ordinary process recreation
- tracking restarts safely after reboot only when the active-trip policy permits it
- diagnostic data can explain when and how records moved
- battery consumption is measured and reported, not guessed

A useful initial service target is that, while a direct or encounter-based relay path exists, 95% of ordinary location records reach the leader within 60 seconds. SOS delivery is measured separately with a higher-priority target. Results must report device models, OS versions, permission state, battery policy, movement pattern, and radio state.

### 4.2 Mixed-platform feasibility gate

Before investing heavily in leader UI polish, test at least:

- 3 or more iPhones
- 2 or more Android phones
- an Android-only group
- an iPhone-only group
- a mixed relay path

The mixed-platform gate must repeat locked-screen, movement, separation, reconnection, process lifecycle, and battery tests. If iOS background discovery cannot meet the operational target, the architecture remains valid but the product claim and transport strategy must be revised before proceeding. Candidate revisions include Core Bluetooth background adapters, scheduled/opportunistic exchange, stronger internet fallback, or an explicitly documented operating constraint. Reliability must not be implied merely because an API is available.

## 5. Technology decision

### 5.1 Selected approach

Use **Kotlin Multiplatform for shared business logic**, with native platform shells and services:

- **Shared core:** Kotlin Multiplatform
- **Android UI:** Jetpack Compose
- **Android services:** native Kotlin foreground service, location, notifications, Nearby Connections
- **iOS UI:** SwiftUI
- **iOS services:** native Swift location/background integration, notifications, Nearby Connections initially
- **Persistence:** a multiplatform-compatible local database behind repository interfaces
- **Serialization:** versioned, deterministic wire models with cross-platform fixture tests

This balances shared protocol correctness with native control over platform lifecycle, permissions, radios, maps, and accessibility.

### 5.2 Compatibility rules

- `commonMain` must not import Android or Apple frameworks.
- Location, transport, notification, cryptography, battery, heading, lifecycle, and map integrations are platform adapters.
- Wire fixtures produced on Android must decode identically on iOS and vice versa.
- Protocol fields are additive by default; unknown fields must not crash older clients.
- The iOS target must compile in continuous integration from the initial scaffold.
- Features are not considered V1 complete until their shared logic has platform-neutral tests and both platform adapters have an explicit status.
- Shared UI is not required. Native UI is preferred where it improves platform behaviour or reduces lifecycle risk.

## 6. Architecture

### 6.1 Layers

```text
Native Member / Leader UI
        |
Presentation Models
        |
Tracking State Engine
  |       |       |
Ledger  Policy  Domain Events
        |
Transport Router
  |           |
Nearby      Internet
        |
Platform Services
Location / Battery / Notifications / Crypto / Lifecycle

Map presentations consume state snapshots:
Google online / MapLibre offline / Radar without tiles
```

### 6.2 Initial repository modules

- `shared/domain`: trips, teams, people, roles, rally points, alerts, SOS, check-ins
- `shared/protocol`: envelopes, wire records, versions, validation, digests
- `shared/state`: merge rules, freshness, last-known state, warnings
- `shared/sync`: anti-entropy planning, prioritisation, retention, relay rules
- `shared/security`: platform-neutral credential and audience models
- `shared/testing`: protocol fixtures, clocks, fake transports, scenario simulation
- `androidApp`: Compose UI and Android adapters
- `iosApp`: SwiftUI shell and iOS adapters
- `docs`: architecture decisions, field-test procedures, results, and operational limitations

The exact Gradle module count may initially be smaller to avoid premature build complexity, but these boundaries must remain visible in packages and interfaces.

## 7. Domain and permission model

### 7.1 Roles

**Member**

- shares own trip-scoped state
- receives permitted alerts
- acknowledges alerts
- sends and manages own SOS
- responds to check-ins
- accesses Connection Cards
- views limited team/rally information

**Team Leader**

- receives operational state for assigned teams
- sees assigned-team map and dashboard
- sends team/person alerts
- requests team check-ins
- manages assigned-team SOS responses
- sees separation, battery, freshness, and network health
- views and uses rally points

**Trip Leader / Admin**

- views all teams
- filters operational views
- targets one person, teams, leaders, or everyone
- sees all SOS events
- manages membership, team assignment, rally points, and thresholds
- initiates multi-team and trip-wide check-ins

Permissions are evaluated in shared domain policy and reinforced through cryptographic audience scope where practical. UI hiding alone is not treated as authorization.

### 7.2 Separate UI roots

The member home prioritises:

- active trip and team
- tracking status
- internet/nearby summary
- Connection Cards
- team/rally direction
- check-in response
- deliberate hold-for-SOS

The leader root prioritises:

- team/all-team map
- accounted-for status
- active SOS
- separation warnings
- stale or lost contacts
- check-ins
- alert delivery/acknowledgement
- battery and network health
- rally points

Team leaders default to their assigned team. Trip leaders default to an all-team overview with filters.

## 8. Tracking state

### 8.1 Location record

A location record contains at minimum:

- protocol version
- immutable record ID
- trip ID
- team ID
- member ID
- origin device ID
- origin sequence number
- latitude and longitude
- horizontal accuracy
- optional speed, bearing, and altitude when useful
- source wall-clock timestamp
- battery percentage and charging state when sampled
- event priority
- expiry policy
- origin signature
- relay metadata outside the signed origin payload

Battery data is attached at a sensible cadence or meaningful change, not every location update.

### 8.2 Freshness

Each receiver stores:

- origin timestamp
- local first-received wall-clock time
- local monotonic receipt time for the current process
- most recent peer exchange time
- arrival transport and relay count

Displayed freshness is based primarily on trustworthy local observation and bounded clock-skew handling, not blindly on another phone's clock.

Default presentation states will be configurable and initially approximate:

- **Current:** recently received and within expected update cadence
- **Becoming stale:** several expected updates have been missed
- **Stale / last known:** old enough that the marker must visibly change
- **Lost contact concern:** stale combined with prior separation or poor network evidence

Exact thresholds are established through field testing.

### 8.3 Merge rules

- Location records from the same origin use a persisted monotonic sequence.
- A lower sequence cannot replace a higher sequence.
- Record IDs provide global deduplication.
- Reinstallation or identity rotation creates a new device identity rather than reusing an unsafe sequence epoch.
- Wall-clock time is descriptive and bounded for validation; it is not the sole ordering authority.
- Safety events such as SOS, acknowledgement, cancellation, and resolution are immutable events reduced into current state.
- A relay may add bounded routing metadata but may not rewrite the signed origin event.

## 9. Peer exchange and store-and-forward

### 9.1 Initial transport

Android initially uses Google Nearby Connections with the cluster strategy because the topology allows multiple devices to advertise, discover, and exchange small payloads without internet. The iOS adapter begins with Nearby Connections for Swift so both platforms share the same conceptual transport.

The transport interface exposes:

- start/stop for an active trip
- discovered peer events
- authenticated session events
- send/receive envelope batches
- connection and failure diagnostics
- capability information
- no assumptions about map or UI state

### 9.2 Encounter protocol

When two enrolled devices connect:

1. Verify trip compatibility and peer membership.
2. Authenticate the session using transport verification plus app credentials.
3. Exchange a compact digest describing known origin sequences and important event IDs.
4. Calculate the missing delta.
5. Send highest-priority missing events first.
6. Validate signature, audience, size, expiry, and sequence.
7. Persist accepted records transactionally.
8. Reduce records into current team state.
9. Retain eligible records for later encounters.
10. Record diagnostics without storing sensitive content in ordinary logs.

### 9.3 Flood and loop controls

- immutable record IDs
- per-origin high-water marks
- bounded recent-ID cache
- maximum payload and batch sizes
- event expiry
- configurable maximum relay count
- bounded retention per record class
- priority queues
- connection backoff
- no immediate echo to the peer that supplied a record
- anti-entropy digests rather than blind broadcast
- storage quotas and compaction

Location synchronization normally retains only recent useful samples plus the newest accepted state. SOS and acknowledgement events receive longer retention and higher priority.

### 9.4 Priority order

1. SOS activation and current SOS location
2. SOS acknowledgement/resolution
3. urgent leader alerts
4. alert acknowledgements and check-ins
5. current locations and warning state
6. rally/configuration updates
7. routine health/diagnostic summaries

An SOS remains active until explicitly cancelled or resolved. Successful transmission does not cancel it.

## 10. Security and privacy

### 10.1 Trip identity

Production trips use cryptographically random identifiers and invitation credentials, not guessable names or short IDs alone. QR codes or secure links can carry a one-time or constrained invitation token.

A joining device creates its own device key in Android Keystore or iOS Keychain/Secure Enclave-compatible storage. Membership credentials bind:

- trip
- member
- device
- role
- team assignments
- validity period
- issuing leader/admin

### 10.2 Record protection

Origin records are signed so relays cannot impersonate the sender or alter safety events. Payload encryption and audience keys are designed so a phone can relay an opaque record without necessarily being permitted to display its precise contents.

The first laboratory proof may use test-trip credentials to minimise setup work, but it must not be described or shipped as production security. Production field testing requires role-scoped credentials and encrypted storage.

### 10.3 Tracking lifecycle

- The user explicitly joins and activates a trip.
- A persistent platform indicator shows when trip tracking is running.
- Ending or leaving a trip stops normal sharing and peer advertising.
- Reboot recovery follows the user's active-trip state and platform policy.
- Ordinary members do not automatically receive unrestricted precise locations.
- Team leaders receive assigned-team operational data.
- Trip leaders receive trip-wide operational data.
- Safety data retention is documented and configurable.

## 11. Background execution and battery

### 11.1 Android

During an active trip, Android uses a foreground service with the required location/connected-device service types and a clear ongoing notification. Permission loss, location-disabled state, Bluetooth-disabled state, and battery optimisation are surfaced prominently.

Initial adaptive location targets:

- moving normally: approximately 15 seconds
- stationary: approximately 45–60 seconds
- active SOS: approximately 5 seconds
- low battery: reduced normal cadence while preserving SOS behaviour

Discovery duty cycles are tuned from measurements. The initial feasibility build may run aggressively to expose reliability limits, then add battery-aware scheduling.

### 11.2 iOS

The iOS application uses native background location capability and an explicit active-trip indicator consistent with App Store rules. Peer exchange will be opportunistic and measured under:

- screen lock
- app backgrounding
- system suspension
- low power mode
- Bluetooth and local-network permission changes
- force quit
- reboot
- varying iPhone generations and iOS versions

No specification statement assumes that Multipeer Connectivity, Nearby Connections, or Core Bluetooth can run indefinitely in every background state. The mixed-platform field gate determines the supported operational behaviour.

## 12. Mapping

### 12.1 State contract

The tracking engine produces provider-neutral map items containing:

- stable entity ID
- coordinate and accuracy
- team colour
- role
- freshness state
- SOS/warning state
- heading when available
- arrival path
- leader-permitted detail

Map providers render this state but do not store or merge it.

### 12.2 Online map

When data and Google services are available:

- Google Maps SDK
- Hybrid default for leaders
- Satellite and normal alternatives
- custom overlays for members, leaders, rally points, bus, SOS, warnings, and freshness
- API keys supplied through local/CI secret configuration and restricted by app identity

Google imagery is not treated as a downloadable offline dataset.

### 12.3 Offline map

MapLibre Native renders a legally downloadable trip package. The likely packaging format is PMTiles or a supported offline region generated from a provider/data source whose licence permits offline distribution.

OpenStreetMap data attribution and the tile/style provider's separate terms must both be respected. Bulk download from the public OpenStreetMap standard tile service is not assumed.

### 12.4 Mapless radar

The radar/compass view uses only accepted coordinates, device heading, and distance/bearing calculations. It displays:

- self at the centre
- direction and distance
- team identity
- rally points
- freshness
- separation warning
- SOS emphasis

It remains available when all map tiles fail.

## 13. Separation detection

Separation is calculated against a meaningful team cluster rather than only the leader.

The initial algorithm:

1. Reject or down-weight positions whose accuracy is too poor for the threshold.
2. Build a current set from sufficiently fresh team members.
3. Estimate the main cluster using neighbour connectivity and a robust centre/medoid.
4. Calculate the person's nearest meaningful-neighbour distance and distance from the robust cluster.
5. Track movement trend across multiple accepted samples.
6. Require sustained evidence before raising a warning.
7. Escalate when serious distance combines with movement away or loss of fresh updates.
8. Clear warnings with hysteresis to prevent rapid toggling.

Presets provide Tight, Normal, Loose, and Custom configurations. Proposed normal starting values are roughly 150–200 m for sustained warning and 250–300 m for serious warning, subject to field validation.

## 14. Alerts, SOS, check-ins, and rally points

These use the same immutable event and acknowledgement model as location exchange.

- Targets can be a person, team, set of teams, leaders, or everyone.
- Important alerts include expected recipients, delivery observations, and explicit acknowledgements.
- Check-ins create a request event and per-member response events.
- SOS uses a deliberate approximately three-second hold, immediate priority propagation, increased location cadence, persistent active state, leader acknowledgement, and explicit resolution.
- Rally points are versioned trip entities available to online maps, offline maps, and radar.
- Delivery counts distinguish locally created, observed delivered, and explicitly acknowledged; they must not claim delivery merely because a relay accepted an event.

## 15. Connection Card boundary

Connection Cards are represented as a feature module and navigation destination. The tracking core does not import Connection Card code or data models.

A future integration adapter may:

- call the existing service/API
- provide offline capture and later synchronization
- reuse validation and user-facing concepts
- share authenticated trip/member identity where appropriate

No existing Connection Card repository is modified during this project unless separately authorised.

## 16. Diagnostics

The feasibility build includes a leader/developer diagnostics view and redacted export containing:

- location production times and accuracy
- discovery/advertising state
- peer session start/end and reason
- digest and delta counts
- accepted/rejected/duplicate record counts
- relay counts and latency
- database compaction
- permission and lifecycle transitions
- battery samples
- app/OS/device version

Logs must avoid names, precise coordinates, invite tokens, keys, and unencrypted payloads unless an explicit test mode is enabled and clearly marked.

## 17. Delivery milestones

### Milestone 0 — Cross-platform foundation

- initialise repository and feature branch workflow
- Kotlin Multiplatform shared core
- compilable Android and iOS shells
- domain and protocol models
- deterministic merge and anti-entropy tests
- cross-platform wire fixtures
- fake transport simulation
- architecture and field-test documentation

### Milestone 1 — Android offline tracking proof

- active-trip foreground tracking
- Nearby Connections discovery/exchange
- persistent ledger
- store-and-forward
- current/last-known list
- freshness and arrival-path display
- diagnostics and export
- 5–10 phone field test

### Milestone 1B — iPhone and mixed-network proof

- iOS location and lifecycle adapter
- iOS Nearby transport adapter
- equivalent diagnostics
- iPhone-only and mixed-platform tests
- documented capability matrix and decision on any alternate iOS transport

This milestone is a release gate because most expected users may carry iPhones.

### Milestone 2 — Operational core

- real trip creation/joining
- secure membership and role assignment
- multiple teams
- member and leader navigation roots
- SOS
- alerts and acknowledgements
- check-ins
- rally points
- battery/network health

### Milestone 3 — Leader awareness

- provider-neutral leader map model
- Google Hybrid/Satellite map
- freshness marker states
- separation detection
- operational warnings
- map actions and person detail

### Milestone 4 — Offline navigation

- licensed offline map package workflow
- MapLibre presentation
- map download/readiness checks
- radar/compass fallback
- rally navigation

### Milestone 5 — Connection Cards and production hardening

- Connection Card adapter/module
- security review
- privacy and retention controls
- accessibility
- broader device test matrix
- deployment and support documentation

## 18. Git workflow

- `main` remains the reviewed integration branch.
- Substantial implementation occurs on named feature branches.
- The specification may initialise `main` because the repository is empty; product code begins on a feature branch.
- Commits are small and logically scoped.
- No secrets, API keys, personal trip data, or signing material are committed.
- Pull requests include verification evidence and field-test results when applicable.
- The existing Connection Card repository is out of scope.

## 19. Principal risks

1. **iOS suspension limits peer discovery.** Mitigation: early Milestone 1B, native adapters, capability matrix, alternate transport decision before UI investment.
2. **Android OEM battery policies interrupt service.** Mitigation: foreground service, diagnostics, manufacturer test matrix, explicit setup guidance.
3. **Radio range does not form a continuous mesh.** Mitigation: store-and-forward semantics, honest last-known status, internet transport when available, no immediate-delivery claim without a path.
4. **GPS jumps trigger false separation.** Mitigation: accuracy gating, sustained evidence, robust clustering, movement trend, and hysteresis.
5. **Clock differences corrupt freshness.** Mitigation: monotonic origin sequence plus local receipt observations; wall clocks are not sole ordering authority.
6. **Map licensing blocks offline imagery.** Mitigation: Google only online; independently licensed MapLibre packages offline.
7. **Security becomes too complex for V1.** Mitigation: keep crypto behind explicit interfaces, separate laboratory credentials from production readiness, and prevent UI-only authorization.
8. **Battery cost is unacceptable.** Mitigation: measure first, then tune adaptive location and discovery duty cycles without weakening SOS priority.

## 20. First implementation slice

After this specification and its implementation plan are approved, the first retained code slice will:

1. scaffold the shared KMP core plus Android and iOS shells
2. define a versioned location envelope and validation
3. implement a deterministic in-memory ledger and merge reducer test-first
4. implement digest/delta planning and relay-loop tests
5. persist the minimum state needed to survive restart
6. add an Android active-trip foreground location service
7. add an Android Nearby Connections adapter
8. display a deliberately basic member list showing current/last-known state and arrival path
9. expose diagnostics sufficient for the first two-phone and relay tests

It will not begin with Google Maps, offline tiles, Connection Card integration, or visual polish. Those features depend on the feasibility result.
