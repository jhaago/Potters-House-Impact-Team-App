# Device Capability Matrix

Update this only from physical tests. One row represents one phone/OS/build combination; create a new row after a material OS or Google Play services update.

| Device ID | Manufacturer/model | Android | Play services | Background location | Screen-off GPS | Screen-off discovery | Direct exchange | Relay | Rejoin | 30-min battery delta | Result/notes |
|---|---|---|---|---|---|---|---|---|---|---:|---|
|  |  |  |  |  |  |  |  |  |  |  |  |

Use `Pass`, `Fail`, or `Not run` for capability columns. Do not infer one device's result from another model made by the same manufacturer.

## Platform gates

| Gate | Required evidence | Status |
|---|---|---|
| Android smoke | Two phones, foreground and 10-minute locked-screen exchange |  |
| Android relay | A → B → C → leader, three consecutive runs |  |
| Android group | 5–10 phones, 10-minute locked screen plus 30-minute battery observation |  |
| Mixed platform | Android and iPhone direct exchange on real devices | Not implemented |
| iPhone background | Locked-screen Core Location and peer behaviour on real iPhones | Not implemented |

Blank means no evidence has been collected. It does not mean pass.
