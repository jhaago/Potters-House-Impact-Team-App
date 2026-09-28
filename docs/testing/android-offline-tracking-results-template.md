# Android Offline Tracking Results

Copy this file for each test day. Leave a field blank when it was not measured. Do not enter real participant names or precise coordinates.

## Test identity

| Field | Result |
|---|---|
| Date/time and timezone |  |
| Test location description |  |
| Tester |  |
| Git commit SHA |  |
| GitHub Actions run |  |
| APK artifact digest |  |
| Shared trip ID |  |
| Shared team ID |  |

## Devices

| Label | Manufacturer/model | Android | Play services | App battery setting | Start % | End % | Notes |
|---|---|---|---|---|---:|---:|---|
| A |  |  |  |  |  |  |  |
| B |  |  |  |  |  |  |  |
| C |  |  |  |  |  |  |  |
| L |  |  |  |  |  |  |  |

## Two-phone smoke test

| Check | Pass / Fail / Not run | Evidence or elapsed time |
|---|---|---|
| Local location production |  |  |
| Peer discovery |  |  |
| Connection/authentication |  |  |
| Digest exchange |  |  |
| Location payload received |  |  |
| Direct-peer source label |  |  |
| Freshness changes truthfully |  |  |
| 10-minute locked-screen exchange |  |  |
| Stop ends active tracking |  |  |
| Redacted export contains no coordinates |  |  |

## Four-phone forced relay

| Run | A→B | B→C | C→L | Relayed label | Total propagation | No A↔L contact | Result |
|---:|---|---|---|---|---|---|---|
| 1 |  |  |  |  |  |  |  |
| 2 |  |  |  |  |  |  |  |
| 3 |  |  |  |  |  |  |  |

## Five-to-ten-phone locked-screen test

| Check | 10 min | 30 min | Notes |
|---|---|---|---|
| Phones participating |  |  |  |
| Current |  |  |  |
| Becoming stale |  |  |  |
| Last known |  |  |  |
| Missing entirely |  |  |  |
| Discovery failures |  |  |  |
| Transport failures |  |  |  |
| Maximum battery delta |  |  |  |
| Unusual heat |  |  |  |

## Recovery matrix

| Case | Location | Discovery | Connection | Digest | Payload | Freshness | Rejoin | Result/notes |
|---|---|---|---|---|---|---|---|---|
| App swiped away/reopened |  |  |  |  |  |  |  |  |
| Force-stop/restart |  |  |  |  |  |  |  |  |
| Location revoked/restored |  |  |  |  |  |  |  |  |
| Radios off/on |  |  |  |  |  |  |  |  |
| Reboot/restart |  |  |  |  |  |  |  |  |
| Leave/rejoin group |  |  |  |  |  |  |  |  |

## Failure log

| Time | Device | Screen/app state | Action | Last exchange age | Diagnostic message | Outcome |
|---|---|---|---|---|---|---|
|  |  |  |  |  |  |  |

## Decision

| Question | Answer |
|---|---|
| Two-phone gate passed? |  |
| Forced relay passed three consecutive runs? |  |
| Group test passed? |  |
| Ready to expand device count? |  |
| Blocking defects/issue links |  |
