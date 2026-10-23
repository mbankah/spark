# Build prompt: Executive Function App (Android, Web, Windows)

You are building a new app from scratch for one user, Michael: a third-year University of Birmingham student with ADHD and autism. The app covers his executive dysfunction. It has nothing to do with his older app "Companion" and must carry nothing over from it.

Everything is decided. Your job is to build it exactly, prove each part works, and report honestly what is not yet proven.

## 1. Files you have

| File | What it is |
|---|---|
| `DECISIONS_LEDGER.md` | The source of truth. 24 numbered sections, every line has an ID (P1, B7, N3 and so on). |
| `design/` | 37 HTML screen drawings and `canvas.json`. Visual reference only. Not production code. |
| `seed/master-plan-week-B-and-key-dates.md` | Raw seed data: week type B timetable, key dates, blocking tiers. |

Read the whole ledger before writing code. Then write `docs/TRACEABILITY.md` (see section 6).

### Precedence when lines disagree

1. Sections 19 to 24 beat everything earlier.
2. Section 22 (storage, browsers, launcher) beats A7. Section 23 (widgets) beats U5 and U9 for widgets. Section 24 (look and modes) beats U10.
3. Section 15 is superseded by section 19. Section 16 is explanation, not specification.
4. Section 21 (what counts and what is proven) beats earlier lines about attendance and proof.
5. Statuses: Locked = Michael decided it. Default = confirmed by him in review. Treat both as binding. Lines in sections 22 to 24 marked Default were set after his last full review; build them, and list them in your first report so he can confirm.
6. If two lines still conflict, do not guess. Stop, write the conflict in `docs/QUESTIONS.md`, take the reading that makes the app harder to escape and cheaper to start, and carry on.

## 2. The idea in one paragraph

Michael's motivation works as net reward: reward minus effort and time cost. Caring more does not help. The app changes those numbers at the moment of choice. Setup happens earlier, when he is in a good state. Stressors are shown as concrete arithmetic. The one number that matters is the **Buffer**: hours available before a deadline minus hours of work still to do. Friction goes on escaping (edits, overrides, days off), never on starting or finishing. Nothing counts until it has proof. The phone is the competitor, so the app also acts as the launcher and blocks chosen social apps. A hard rule everywhere: no fake pressure. Every number can be tapped to show its working.

## 3. Hard rules for how you work

1. **No stubs, no placeholders, no TODO screens.** If something cannot be finished, say so in the report and leave the feature off, not faked (X7, Q2).
2. **Every ledger line has a test** (Q1, Q4). A line without a test is a missing feature. `docs/TRACEABILITY.md` maps every ID to its test file and test name. Build the table first, then make it green.
3. **Every feature has a stated failure behaviour** (X7, Q5). Write it next to the code and test it.
4. **Layers are built in order and each must work alone** (section 13). Do not start a layer until the previous one passes its tests.
5. **End every layer with a report** (Q3) in `docs/LAYER_n_REPORT.md` with three headings: *Built*, *Tested* (with the command and result), *Not yet proven on a real device*. Never write "done" for something you only ran in an emulator or a unit test.
6. **Be honest about Android limits.** Special permissions can only be switched on by the user. Force-stop and power-off cannot be prevented, only detected. Boot behaviour changes by Android version. Do not claim otherwise anywhere in the app, the code comments or the reports.
7. **Never add features, copy or behaviours that are not in the ledger.** No streaks, no motivational text, no goals-speak, no confetti (P6). If you think something is missing, write it in `docs/QUESTIONS.md`.
8. **Never loosen a rule to make a test pass.** Fix the code, or record the conflict.
9. **Privacy:** photos never leave the phone (ST6). Cloud sync carries numbers only. No microphone use except the voice feature when the user has switched it on. Location is read only when a proof photo is taken (A8).
10. **Plain, exact labels** (W5, W6, W7). Titles are simple and descriptive, with the location where there is one. Detail goes in the description.

## 4. Platforms and suggested stack

You may change a choice if you write down why in `docs/DECISIONS.md`. The reasons below are fixed by what the ledger needs.

- **Android app (primary):** Kotlin, Jetpack Compose, Room, WorkManager, a foreground service for sleep logging. It must be installable as the home app (launcher), use an accessibility service for blocking, a notification listener for holding notifications, exact alarms, a full-screen alarm activity, CameraX for in-app photos only (no gallery picker), and an on-device image match for the morning check. Assume it is installed directly (sideloaded) because Play Store policy restricts accessibility-based blocking (E6).
- **Web app:** TypeScript with React, as an installable PWA. Same visual system. It reads what the phone sends and supports planning and the emergency mode (G9). It cannot block other sites by itself (A3).
- **Windows component (A2, K4, K5):** a Windows service (C# or Rust) that writes and locks the hosts file, reverts any change within about a second, logs every attempt, and restarts itself; plus a Manifest V3 browser extension as the second layer. Windows only.
- **Backend (G8):** one account with cloud sync for numbers only. Devices pair by QR code. Choose a managed backend (for example Supabase or Firebase) to keep the build small. Include a server time check (G10).
- **Voice (V1 to V4):** on-device speech recognition where available. A confirmation card always appears before saving.

## 5. Visual design (section 24 and `design/`)

Look: **industrial dial**. A warm-grey, device-like face with rounded cards (14 px radius), a ring dial for the Buffer, DM Mono for labels, DM Sans for titles and numbers, and one accent colour. Open `design/*.dc.html` in a browser to see each screen; read the CSS custom properties in the Ind*, Cold*, Warm* and Colour* files for exact values.

Four modes, plus Auto (AP2, AP4): **Light, Dark, Night, Colourful**. All modes share one layout, so changing mode never moves anything. Mode and palette are separate settings: palette is Neutral, Warm or Cold. Michael has not yet picked between the Warm and Cold variants; implement all palettes as tokens and make them settings, defaulting to Neutral.

| Token | Light | Dark | Night |
|---|---|---|---|
| background | #e9e7e2 | #121211 | #0a0604 |
| card | #f6f5f1 | #1c1c1a | #130c08 |
| text | #1b1a18 | #ece9e2 | #d9945f |
| muted | #5d5b56 | #a09d95 | #b9824f |
| accent | #ff5a1f | #ff6a33 | #c4501f |
| ring track | #cfccc5 | #34332f | #2a1810 |

Palettes to implement as the same token set, from the files: `ColdLight`, `ColdDark`, `WarmLight`, `WarmDark`, and Colourful in neutral (`IndColour`), dark (`ColourDark`), warm (`ColourWarm`) and cold (`ColourCold`). Use the values written in those files.

Rules that must hold in every mode (AP3, AP5, AP6, AP7, AP8):
- Night mode: warm amber on near-black, no white, no blue light, brightness capped at about 20%. It starts from the shutdown photo or the night cutoff, whichever is first, and ends when the morning alarm stops.
- A negative Buffer is the only red, always with the word NEGATIVE.
- No animation, no badges, no counts of held notifications, no gradients except the dial's ring.
- Contrast of at least 4.5:1 for text in every mode and palette. Test it automatically.
- A mode can never hide a block or lower any rule.
- Text scales with the system font size. Everything is labelled for TalkBack.

Screens drawn in `design/` (use as the reference for layout and copy tone; data in them is example data):

| Screen | File |
|---|---|
| Launcher home | `Main`, `IndDark`, `IndNight`, `IndColour`, `DialHome` |
| Home with widgets, Edit widgets | `HomeWidgets`, `EditWidgets` |
| Search to open | `Search` |
| Buffer working | `Buffer` |
| Work block | `Block` |
| Morning alarm | `Morning` |
| Blocked app card | `Blocked` |
| Night cutoff | `Night`, `IndNight` |
| Day plan | `Day` |
| Appearance settings | `IndAppearance` |

The brutalist, techno, amber, Swiss, blueprint, pop, synthwave, HUD and broadsheet files were exploration only. Do not build them. Several drawings use the brutalist look; apply the industrial look to them.

Screens that are specified but not drawn (build them in the same system): first-run setup and permission flow, planned periods and days off, the wall (75-character entry), quick-add and voice card, digest of held notifications, shopping list, money and savings view, week view, settings, storage meter, tamper and restore screen, emergency mode (web).

## 6. Traceability and tests

1. Create `docs/TRACEABILITY.md`: one row per ledger ID, with columns *ID | Behaviour | Test file | Test name | Layer | Device test needed (yes/no)*. A row with no test is a failing row.
2. Unit tests cover all arithmetic (Buffer, weights, squeeze order, money table, cutoff, wake drift, sleep detection logic, escalation lengths). Use the ledger's worked examples as fixtures: `+4.5h` from 17.5 available minus 13.0 work; cutoff 23:10 for a 07:30 wake; money table in section 9.
3. Integration tests cover rules that cross features (see the list in section 9 of this prompt).
4. Device tests are scripted checklists in `docs/DEVICE_TESTS.md`, to be run on Michael's phone, with expected results. You cannot run them; say so in the report and list exactly what he must do.
5. The following are **required test cases** from the ledger:
   - Clocks go back on Sunday 25 October 2026 (X4): a full week of arithmetic and alarms across the change.
   - The 100-reboot test for the launcher and blocking (LA14).
   - All seven routes to a blocked app end at the same card (LA8).
   - A real overnight run of the sleep service on the phone (Q6, S7), with passive logging from the first build.
   - Photo storage ceilings and thumbnail expiry (ST1 to ST5).
   - Contrast in every mode and palette (AP8).
   - Every widget: add, remove, reorder, resize, next-day delay, immediate removal, stale data, reboot (WG10).

## 7. Build order (section 13, with layer 8)

Each layer lists what it contains. Exact behaviour is in the ledger IDs shown.

**Layer 1: Core loop.** Data model and sync-ready storage (all times stored with an explicit time zone, X4). Weekly pattern seeded from `seed/` with the section 3 changes applied (D1 to D7). The Buffer with tap-for-working (B1 to B6, B9). Quick-add with Check Canvas (D2, D3). Work blocks that end with timer + one-second hold + one proof photo (W1 to W7, G1, G5). Transitions as counted blocks (T1 to T3). The launcher shell as the home app, with the first-run permission flow (U1 to U12, LA1 to LA14, ST1 to ST7). Shopping list (SH1 to SH5). Industrial design system with all modes (AP1 to AP8). Widgets (WG1 to WG10). Photo storage policy.

**Layer 2: Passive sleep logging, record only (S1 to S7).** Screen events, charging, motion, light, Android sleep API where available, optional Health Connect. No microphone. Foreground service that survives the night. No rules attached yet. Built together with layer 3.

**Layer 3: Morning (M1 to M10, Z1 to Z4, G2).** Night-before setup and reference photo. Alarm stops on a live in-app photo match; steps as the weaker fallback. Social apps blocked until the ready photo. Alarm rule: sleep onset + 8 h, hard alarms only ring earlier, 3-hour floor, 12:00 backstop treated as a hard alarm (Y7). Wake time drift back toward target by 15 minutes a day. Target wake comes from the first fixed commitment (F1 of section 20).

**Layer 4: Night (N1 to N7).** Cutoff, 30-minute overrides (45 after a fully verified day) through the 75-character wall with escalating length, the 8-hour sum on screen, the hard stop six hours before the target wake time.

**Layer 5: PC blocking (K4 to K9).** Extension, then the Windows service with locked hosts file, reversion, tamper log, same 75-character wall for loosening.

**Layer 6: Rewards and free slots (R1 to R10, F1 to F7).** The £25 weekly guide, the money table, earned phone access (10 minutes per hour of work, never overriding the night cutoff or the getting-ready block, Y8), the 17.5% margin rule, protected free time (6 h) and personal football (3 h, separate, Y5), fatigue, squeeze order.

**Layer 7: Calibration (B5, R10).** Learn real task durations and correct estimates; show how many tasks the estimate rests on.

**Layer 8: Planned changes and finishing (C1 to C5, H1 to H6, V1 to V4, G6).** Planned periods and days off with the escalating wall, voice quick-add and navigation with confirmation card, the first-14-days record-only rollout for penalties and money (blocking and permissions run from the first use of each layer, U12), emergency web and PC mode (G9), final polish.

## 8. Requirements by area (read the ledger for every detail)

**Buffer and arithmetic (section 4, B8, D5, F3, squeeze order in section 10).** Heaviness is one slider per task. Short-sleep days (6.5 h or less) weigh remaining work heavier: hard ×1.5, easy ×1.15. Unconfirmed time counts as lost; backfill runs until next morning, capped at 1 hour, visibly marked. Free slots stay breaks only while the Buffer is at least 17.5% of work due before the nearest deadline (2-hour floor), except 6 protected hours a week. Squeeze order: free slots become work, lowest-value blocks drop, study cycles go light, transitions shrink, and only then the Buffer takes the loss. Never squeezed: sleep, meals, the £40 food budget, hard commitments, the reset after each block, the night cutoff.

**Proof (sections 5, 11, 21, 22).** One in-app photo per block (no gallery). Code blocks use a git commit checked automatically. Lectures and tutorials need a photo inside the lecture window plus the location check: within about 150 m of the preset named building, otherwise at least about 250 m from home (marked weaker), rejected inside the home radius. Training, matches, gym, church, MyTutor, chores and personal football each need one photo inside their window (Y1 to Y3); meals are not photographed. Photos are processed on the phone: match, location result, duplicate fingerprint. The full photo is discarded; a thumbnail of about 30 KB is kept for 14 days; a record of a few hundred bytes is kept forever (ST1, ST2). Reference photos are replaced, not accumulated (ST3). Build the UoB venue directory from the university map data or OpenStreetMap and report every venue that cannot be matched (L4, L5).

**The wall (M7, N3, K2, H1 to H5, F4, LA9).** A fresh random string of 75 characters, typed by hand, with no paste, no autofill and no suggestions, restarting on any mistake. Escalation lengths are in the ledger. Used for block-list edits, night overrides, days off, tamper and opening a blocked app. Voice can start a loosening request but never finish it (V4). Implement it so that it cannot be bypassed by accessibility services, keyboard apps that paste, or screen automation as far as Android allows, and document the limits.

**Blocking (sections 8, 17, 22).** Phone: accessibility service for foreground-app detection; blocked apps appear as muted text with the reason; opening one shows the plain card with the Buffer cost and the wall (LA9). Browsers: same domains blocked by reading the address bar, including private tabs (K10); state the limits. Notifications from blocked apps are held and shown as one digest after the block, never deleted (LA2); calls, alarms, authenticator codes, banking alerts and MyUoB notices are never held (LA1). Split screen, floating windows and picture-in-picture are covered (LA7). Tampering (permission switched off, default home changed, app force-stopped) is detected, logged and shown as a "restore this" screen (U4, X5).

**Launcher and widgets (U5 to U9, LA1 to LA14, WG1 to WG10).** Text-first home. Search to open. Essentials one tap away: phone, messages, maps, banking, authenticator, camera, and an audio app for sermons and music. The launcher never traps the user: after 3 crashes in 10 minutes it hands over to Android's default home and blocking keeps running (LA3). Adding a widget, an icon grid or third-party widgets takes effect at the next 00:00; removing takes effect at once.

**Money (section 9).** £80 a week = £40 food (guaranteed, never tied to work) + £40 other. Free to spend = £25 × (weighted verified ÷ weighted planned), linear; the rest goes to savings, so at least £15 is saved each week. The app never touches a bank. Money starts counting after the 14-day record-only period (R9, G6).

**Days off, planned periods and voice (section 14).** Days off have no fixed cap; each goes through the wall, and the string gets longer with each use in the last 30 days. A weekend counts as one use. Forgotten time off counts as lost unless the adapted following days are completed on time (H6). Voice adds deadlines with a confirmation card (V1, V2).

**Seed data.** Use `seed/` for the week type B timetable, venues, key dates and study-cycle sizes. Apply the section 3 changes: team training Friday 09:00 to 10:45 only, Campus League football Monday 13:00 only, personal football 3 flexible hours, Monday 20:00-22:00 and Saturday 16:45-18:45 football removed. The planned library week and the weekend off of 24 and 25 October 2026 will have passed by the time the app ships; enter them as planned periods when the app first runs, not as code.

## 9. Integration tests that must pass

1. After the night cutoff the social list is blocked, but a work block, the getting-ready window and transitions still block as usual (N2).
2. A free slot never converts into work inside the 6 protected hours, and personal football is never converted (F3, Y5).
3. A bad night (6.5 h or less) raises the Buffer cost of remaining work, shifts the wake time, and never rings an alarm earlier than 3 hours after onset unless it is a hard alarm; the 12:00 backstop still rings (Z1 to Z4, G2).
4. A day off from 3 hours of sleep or less is automatic, needs no typing, and counts toward the 30-day tally (H4, X3).
5. Opening a blocked app during a work block ends that block as broken, loses its unproven time, and opens the app for 30 minutes after the wall (LA9).
6. A photo reused from an earlier block is rejected by the fingerprint check (ST2).
7. Cloud sync carries no photo data; a lost phone leaves the Buffer visible on the web (ST6, G9).
8. Money is zero-penalty during the first 14 days and starts counting after (G6, R9).
9. Switching mode changes colours only; no element moves and no rule changes (AP7).
10. Earned phone access never opens blocked apps after the night cutoff or during getting ready (Y8).

## 10. Known risks to verify on the phone, early

Report each as verified or not, with the Android version and phone model.

1. The overnight sleep service survives battery management (S7, Q6, E4).
2. Boot start of the launcher and the blocking engine on his Android version, including Android 15 restrictions on boot receivers (U3).
3. Notification listener behaviour: holding, restoring and the digest (U8).
4. Lock screen and quick-settings quick-add, and a persistent notification for the Buffer (LA11, F2 of section 20).
5. Indoor GPS accuracy for attendance, and the fallback (E11, L2).
6. Camera match reliability in poor light, and the fallback ladder (M10, E3).
7. Browser domain blocking in the browsers he actually uses (K10).
8. Antivirus tolerance of the Windows service that edits the hosts file (E7).
9. Photo storage staying under the ceilings (ST4).

## 11. What to give back first

Before writing feature code, return:

1. `docs/TRACEABILITY.md` (every ID, no tests yet is fine, but every ID listed).
2. `docs/QUESTIONS.md` (conflicts or gaps you found; expect few).
3. `docs/DECISIONS.md` (stack choices and why).
4. A list of the Default lines in sections 22, 23 and 24 so Michael can confirm them: ST2 to ST5, ST7, K10 limits, LA1 to LA3, LA5 to LA7, LA9 to LA13, WG5, WG6, WG8, WG9, AP4, AP5, AP7.

Then build layer 1, run its tests, write its report, and continue to layer 2 only after layer 1 passes.
