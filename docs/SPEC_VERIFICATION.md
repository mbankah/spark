# Specification verification, 30 October 2026

Run by `tools/gen-traceability.mjs`, which reads every ledger ID and looks for a test whose name starts with it (Kotlin core tests, phone tests, Windows service tests in C#, and browser extension tests). Result: **277 ledger rows, 199 have a written test, 78 do not.** This page says what each of the 78 is, so none is hidden. The full table is `TRACEABILITY.md`.

A test existing means the behaviour was checked on the emulator or in unit tests. It does not mean it has been proven on your real phone: `DEVICE_TESTS.md` is that list.

## 1. Not code: principles, process rules, risks, answered questions (38 rows)

Nothing to build or test as a single behaviour; features elsewhere carry them out.

- Principles: P1, P2, P3, P4, P7, P9, P10, P11.
- Process: Q1, Q2, Q3, Q4, Q5. Q6 (the overnight sleep test) needs your phone and is in `DEVICE_TESTS.md`.
- Risks and their mitigations (the mitigations are other rows): E1 to E8, E12 to E15.
- Answered questions, applied elsewhere: O1, O7, O8, O10, O12, O14, G4, D7.
- Review notes: R20-1 to R20-7, R20-F1, R20-F2, R20-F4.
- Wording rules checked by reading, not testing: W5, W6.
- U11: device-owner mode is stated as not part of the first build.

## 2. Built, but no test name carries the ID (not yet linked)

- SH2: the shopping trip is the Monday Aldi block in the plan. Not reconfirmed (Q-G89).
- U12: blocking runs from the first run and the 14 day period covers money only (G6, tested as G6 and R20-F5).
- U5, U10: the launcher is text only and monochrome (checked by `launcher-check` screenshots, not an ID-named test).
- X6: the first-run setup flow exists (`SetupFlow`, `BlockSetupTest`). I have not checked it line by line against every item in X6, so treat it as not verified.
- Y2: location checks for named places use the same code as lectures (`LocationFixTest`). Not verified line by line.
- Y9: the passed library week and weekend off are not entered (Q-G84).

## 3. NOT BUILT. These are real gaps.

| ID | What is missing |
| --- | --- |
| A1, A5, A6, G8, G9, E12, ST6 | The **web app and the phone to web sync**. No backend exists. The PC link (below) is phone to PC only, on the local network. |
| T2 | Transition times from OpenStreetMap routing, corrected from measured times. Walks are fixed numbers. |
| S6 | Health Connect input for sleep. |
| G10, E14 | A trusted time source against clock tampering. |
| L6 | Asking for a new location when a lecture's room changes. |
| L7 | The "Check MyUoB" button. (The Canvas one, D3, is built.) |
| U9 | App icons and a grid layout as settings, with the next-day delay. |
| LA6 | The wall on Android's own accessibility, app-info, uninstall and default-home pages. |
| LA7 | Tests for split screen, floating windows and picture-in-picture. The service looks at every window, but no test proves it. |
| LA12 | An optional light theme, and a TalkBack audit. |
| AP1, AP2, AP4, AP5 | The four appearance modes (Light, Dark, Night, Colourful) and Auto. The app has one palette (Cold), chosen earlier. AP1's warm-grey industrial look was replaced by that decision. |
| WG5, WG6 | Widgets from other apps. |

## 4. Built this session and linked

Planned days off (C1, C4, C5, H1 to H6, X3, G2), library periods (C2, C3, O11, O13, PW1), calibration (B5, R10, E9), the shopping list (SH1, SH3 to SH5), widgets (WG1 to WG4, WG7 to WG10, B2), voice (V1 to V4, G7, E10), browser blocking (K10, K7), and the Windows service, protocol and extension (A2, A3, A4, K4, K5, K6, K8, K9). The PC link is proved end to end by `tools/pc-link-e2e.sh`.

## 5. What the Windows part cannot do (K8, E7)

An administrator can stop or uninstall the service; it is logged on restart, and Windows offers no typed phrase for that. A VPN, a proxy or encrypted browser DNS can go around the hosts file; the extension covers Chrome-family browsers only. Antivirus may flag a service that edits the hosts file (E7); this has not been tested against any antivirus. The installer has been built (`windows/dist/SparkGuard-Setup.exe`) but **not run** on this PC, because installing it would edit your real hosts file and services.
