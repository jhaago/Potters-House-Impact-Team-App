# Android Offline Tracking Field Test

Use this procedure to answer the first feasibility question: can Android phones keep useful team state moving while mobile data is disabled, screens are locked, and people move between nearby groups?

This is a proof test, not a safety system. Keep participants within normal visual or voice support, use synthetic names, and carry a separate emergency communication method.

## Build under test

1. Open the latest successful `Verify` run for `feature/offline-tracking-proof`.
2. Download the `impact-team-android-debug-apk` artifact.
3. Record the workflow run, commit SHA, APK digest, test date, and tester in the [results template](android-offline-tracking-results-template.md).
4. Install the same APK on every phone. Android may require approval to install an unknown app.

Do not mix APK versions in one run.

## Prepare every phone

1. Charge above 70% and record phone model, Android version, Google Play services version, and starting battery.
2. Disable battery optimisation for the proof app where the manufacturer provides that control.
3. Turn mobile data off. For the strict offline stages, use airplane mode and then manually turn Wi-Fi and Bluetooth back on.
4. Grant precise location, background location, notifications, nearby devices/Bluetooth, and nearby Wi-Fi permissions.
5. Confirm Wi-Fi, Bluetooth, and device location services are on.
6. Open the app and enter the same trip ID and team ID on every phone. Give each phone a different member ID. Do not alter the generated device ID.
7. Tap `START TRACKING`, read the disclosure, and tap `CONFIRM START`.
8. Keep `STOP TRACKING` visible and verify the foreground tracking notification appears.

When a Nearby authentication code appears, compare it on the two participating phones. Record a mismatch as a failure and stop that connection attempt.

## Test 1 — Two-phone smoke test

Use phones `Member A` and `Leader L`.

### Foreground

1. Keep both screens awake and phones 2–5 metres apart.
2. Open Diagnostics on both phones.
3. Wait up to 90 seconds for `Nearby phones` to become at least 1 and for `Last exchange` to stop showing `Never`.
4. Walk together for five minutes.
5. On L, verify A appears in recent team state, its age refreshes, its source is `Direct peer`, and accepted records increase.

### Locked screen

1. Lock both phones without swiping the apps away.
2. Walk together for 10 minutes.
3. Wake L but leave A locked.
4. Verify A still appears with a truthful age. If updates stopped, it must become `Becoming stale` and then `Last known`; it must never look current indefinitely.
5. Copy redacted diagnostics from both phones into the results record. The export must not contain coordinates.

Pass only if discovery, exchange, freshness, and explicit stop control all behave as expected.

## Test 2 — Four-phone forced relay

Use `Member A`, `Relay B`, `Relay C`, and `Leader L`. The route under test is A → B → C → L.

Choose separated rooms, vehicles, or building sides where A and L do not discover each other. Radio range is variable, so verify isolation from Diagnostics rather than relying on distance alone.

1. Start with A and B together for 90 seconds. C and L stay isolated.
2. Confirm B receives A as `Direct peer`, then separate A and switch A's Wi-Fi and Bluetooth off.
3. Bring B to C for 90 seconds. Keep both away from L.
4. Confirm C receives A as `Relayed`, then separate B and switch B's radios off.
5. Bring C to L for 90 seconds.
6. Confirm L receives A as `Relayed (N hops)` with a plausible age. A must not have met L during the run.
7. Reconnect A later and verify a newer record replaces the relayed last-known record without clearing app data.

The target propagation window is five minutes from A's final exchange with B to A appearing on L. Run the complete route three times. Record actual times; do not convert a near-pass into a pass.

## Test 3 — Five-to-ten-phone locked-screen group

1. Use 5–10 phones from at least two manufacturers. Record them in the [capability matrix](device-capability-matrix.md).
2. Start all phones together and verify each shows nearby peers and successful exchanges.
3. Lock every screen and walk as a loose group for 10 minutes. Keep ordinary group spacing; do not create unsafe separation.
4. Wake the designated leader phone. Record how many members are current, becoming stale, last known, or absent.
5. Continue to 30 minutes for battery observation. Record battery delta and unusual heat; this does not establish all-day suitability.
6. Repeat once with the group splitting into two clusters for five minutes and rejoining.

## Recovery cases

Run each case on one non-leader phone while the others remain active:

- Swipe the app away, then reopen it.
- Force-stop the app in Android settings, then reopen and explicitly restart tracking.
- Revoke precise location while tracking, then restore it.
- Turn Bluetooth and Wi-Fi off for five minutes, then restore them.
- Reboot the phone, reopen the app, and explicitly restart the proof trip.
- Leave the group for five minutes and rejoin.

Record whether location production, discovery, connection, digest exchange, payload receipt, freshness, and rejoin each recover. A process or reboot is not expected to silently reactivate tracking without an explicit active trip.

## Stop and clean up

1. Tap `STOP TRACKING` on every phone.
2. Verify the foreground notification disappears and diagnostics stop changing.
3. Re-enable mobile data and restore normal battery settings.
4. Remove the proof app if the phone will not participate in another run.
5. Store only redacted diagnostics and relative movement notes. Do not commit names or precise coordinates.

## Acceptance gate

Expand the proof only when:

- Two-phone screen-off exchange succeeds for 10 minutes on every tested phone.
- The forced A → B → C → L route succeeds three consecutive times.
- No stale or lower-sequence state replaces a newer state.
- Permission, radio, and process failures are visible rather than silently shown as live.
- Rejoin succeeds without clearing application data.

Physical failures are useful evidence. Capture the exact device, OS, elapsed time, screen state, last successful exchange age, and diagnostic failure before changing code.
