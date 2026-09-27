# Offline Tracking Field-Test Procedure

This procedure is for the first app-only tracking proof. Do not begin the physical test until background location, on-device persistence, nearby exchange, and the diagnostics screen are implemented.

## Objective

Determine whether 2–3 Android phones can preserve and propagate useful last-known locations while mobile data is disabled, screens are off, and participants are moving. The later acceptance test expands to 5–10 phones and includes iPhones.

## Safety and privacy

- Use a temporary test trip and synthetic member names.
- Tell every participant when tracking starts and stops.
- Test in a safe public area; do not separate anyone beyond normal visual or voice contact for this proof.
- Stop the active trip at the end and confirm location sharing stops.
- Do not publish precise test coordinates or diagnostic exports to GitHub.

## Equipment and setup

- Three charged Android phones, ideally from at least two manufacturers.
- Battery optimisation disabled for the test build where the OS requires it.
- Location and nearby-device permissions granted.
- Airplane mode enabled, followed by manually enabling Bluetooth and Wi-Fi if the phone permits it.
- The same temporary trip and team joined on all phones.
- One phone designated `Member A`, one `Relay B`, and one `Leader C`.

Record each phone model, Android version, starting battery percentage, and the test-build commit SHA.

## Test stages

### 1. Two-phone foreground exchange

Keep A and C awake and within 5 metres. Confirm both phones discover each other and C receives increasing A sequence numbers. Pass criterion: five consecutive records arrive without duplicates replacing newer state.

### 2. Two-phone screen-off exchange

Lock both screens for 10 minutes while walking together. Wake C without reopening A. Pass criterion: C shows A's last-known location, truthful age, arrival path, and no crash or silent tracking stop.

### 3. Three-phone store-and-forward

Start together, then position the phones so A can exchange with B and B can later exchange with C, but A and C do not meet during the stage. Pass criterion: C receives A's newest record through B and labels it as relayed.

### 4. Separation and reconnection

Move A out of nearby range for 5 minutes, then return. Pass criterion: C visibly changes A from current to becoming stale or stale, then accepts newer records after reconnection without an app restart.

### 5. Process and permission degradation

Force-stop one non-leader phone, then restart it. Repeat after temporarily revoking location permission. Pass criterion: the app reports degraded health rather than presenting old data as live, and resumes only after the user restores permission and tracking.

### 6. Battery observation

Run the screen-off test for 30 minutes. Record start/end battery percentages and device temperature observations. This short run is diagnostic only; it does not establish all-day battery suitability.

## Evidence to capture

- Discovery, exchange, and relay event timestamps.
- Origin sequence and record ID received by each phone.
- Direct or relayed arrival path and relay count.
- Last-update age shown to the leader.
- Starting and ending battery percentage.
- Any permission, radio, process-death, or reboot recovery failure.

Use relative movements and redacted screenshots. Do not commit real participant details or GPS coordinates.

## Initial acceptance gate

The proof is ready to expand beyond three phones when:

- A→B→C propagation succeeds in three consecutive runs.
- No stale or lower-sequence record replaces newer state.
- Screen-off tracking continues for 10 minutes on every test phone.
- Loss of permissions or radio access produces an explicit degraded state.
- Reconnection works without clearing app data.

Failures are expected during the proof. Record the exact phone, OS version, elapsed time, screen state, and last diagnostic event before changing the implementation.
