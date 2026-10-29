# Layer 1 report: PARTIAL (logic core + app shell)

Layer 1 is **not finished**. This is the first slice. The layer does not pass until every Layer 1 row in TRACEABILITY.md has a test, and the Android pieces exist.

## Built

Pure Kotlin/JVM `core` module (no Android imports), in `core/src/main/kotlin/efa/`:

- `Buffer.kt`: tasks, heaviness 1 to 10, short-sleep weights (B8), available hours on real instants, Buffer result with tap-for-working text, NEGATIVE wording, no-deadline state.
- `Money.kt`: pence arithmetic, £25 cap, £40 food, weighted ratio, 14-day record-only gate.
- `Night.kt`: cutoff (N1), hard stop (N7), override length, the 8-hour sum text, London zone.
- `Wall.kt`: wall lengths (confirmed values), fresh random strings, typed-entry rule (one character at a time, mistake restarts, multi-character input rejected).
- `Storage.kt`: storage numbers, thumbnail expiry and ceiling, forever-record, reuse fingerprint check.
- `Contrast.kt`: WCAG contrast and the mode/palette token sets.

Also: Gradle wrapper (9.8.2), traceability generator that links tests by ledger ID, QUESTIONS, DECISIONS, Defaults list, installer drawing.

## Tested

Command (from `efa/`): `.\gradlew.bat :core:test`
Result: **46 tests, 0 failed.** 35 of 253 ledger rows now have a written test. 218 rows still read "(not written)" and count as failing.

Findings the tests produced:
- **Accent is not text.** Neutral-light orange on a card is 2.9:1. Rule: the accent is a fill or ring only; text on it must pass 4.5:1. Night uses an outlined button.
- **Clock change:** the cutoff before Sunday 25 Oct wake 08:30 reads 01:10 on the clock, not 00:10, because real elapsed time is used. Sat to Sun is 25 h.

## Not built yet (Layer 1)

Weekly pattern seeded from `seed/`, Room database, quick-add, work-block timer/hold/photo flow, transitions as blocks, proof photo and location check, launcher and permission flow, blocking engine, notification digest, shopping list, widgets, Compose screens. None of the Android code exists.

## Not yet proven on a real device

Everything above is a JVM unit test. Nothing has run on a phone. This machine has no Android SDK; Android work needs it installed (about 2 GB). Section 10 risks 1 to 9 of the build prompt are all unverified. Device checklist (`DEVICE_TESTS.md`) is not written yet.

## Update: Android app shell (23 Oct 2026)

**Correction:** the earlier note that this machine had no Android SDK was wrong. An SDK was already installed at `%LOCALAPPDATA%\Android\Sdk` (platforms 34 to 36, emulator, adb); environment variables just were not set. I added platform 37 because the newest AndroidX needs it.

**Built:** `app/` module (AGP 9.3.3, Kotlin 2.4.0, Compose, min SDK 29, target 35, compile 37). It registers as a home app. The home screen shows time, Buffer dial card, now, next and free-to-spend, in the Cold Light/Dark palettes (Auto follows the system). It shows real states only: "No deadline" and "Nothing scheduled", since no tasks are stored yet.

**Tested:** `.\gradlew.bat :app:assembleDebug :core:test` succeeds (46 core tests still pass). The APK installs and launches on a fresh emulator (`efa_test`, Android 36.1). Checked by eye in one screenshot, Cold Light mode only. No automated UI tests yet.

**Found:** the existing emulator `Medium_Phone_API_36.1` has the old Companion app on it, which put its own lock screen over this app. I left it untouched and used a new emulator. Note for Michael's phone: uninstall or disable Companion before testing, or it will block this app.

**Not yet proven on a real device:** everything. No physical phone was connected.

**Still to do in Layer 1:** Room database, seeded weekly pattern, quick-add, work blocks with hold and proof photo, location check, transitions, permission flow, blocking service, notification digest, shopping list, widgets, bundled DM fonts, all modes selectable (Night, Colourful), TalkBack checks.

## Update: database, seeded week, real Now/Next

**Built:**
- `tools/gen-seed.mjs` reads `seed/master-plan-week-B-and-key-dates.md` and writes `core/.../WeekB.kt` (179 slots). Section 3 changes applied: Tuesday training dropped, Friday training 09:00 to 10:45 then a walk, Monday 20:00 and Saturday 16:45 football removed (with their own travel and buffers, Q-C9) and replaced by free time. Campus League football Monday 13:00 stays. Names follow W7 ("Neural Computation lecture - Haworth HWTH-101", "Notes and problems: ...").
- `core/.../Schedule.kt`: places slots on real dates in London time (after-midnight slots belong to the day before), and finds Now and Next.
- Room database (`app/.../data/Db.kt`): pattern slots (editable later, O9) and a meta table with first-use time and zone. Seeded once on first run in one transaction.
- Home screen reads Now and Next from the database every 30 seconds. If the database fails it says "Schedule unavailable" (X7).

**Tested:**
- `.\gradlew.bat :core:test`: 61 tests, 0 failed.
- `.\gradlew.bat :app:connectedDebugAndroidTest` on the `efa_test` emulator (Android 36.1): 4 tests, 0 failed (seed once, first-use time kept, round trip, Now/Next from stored rows).
- One screenshot on the emulator at 04:13 Friday: Now "Nothing scheduled", Next "07:30 Wake, shower". Cold Light only. Status bar icons are readable after edge-to-edge.

**Decisions to check (also in QUESTIONS.md):** language lessons are titled "Language lesson", not "Learn Python", because the language changes by milestone. Removed football's travel/buffers are dropped; its meals and the Aldi run stay.

**Not done:** the Buffer is still "No deadline" because there is no quick-add or task table yet. Personal football (3 h) is not placed. Planned periods (library week, 24 and 25 Oct off) are not entered. Nothing has run on a real phone.

## Update: tasks, quick-add and a real Buffer

**Built:**
- `core/.../Plan.kt`: Buffer from the plan. Available time = study and project blocks before each deadline. Work = stored quick-add tasks plus a study cycle per lecture still ahead (D1, due that night's cutoff, standard 2.5 h / light 1 h). The tightest deadline sets the number and is named in the working. Overdue tasks are not hidden. "No deadline" only when nothing is due (D6).
- Room: `task` table, schema version 2, tested migration 1 to 2. Times stored as epoch plus zone (X4). `doneAtMs` stays empty until a proven block exists (P8).
- Quick-add screen: title, deadline day and time chips, heaviness 1 to 10, defaults (tomorrow 17:00, heaviness 5), a live "if you save" Buffer, Check Canvas (opens the app if installed, otherwise says so), typed entry only.
- Buffer working screen (B3), opened by tapping the Buffer card. The dial now shows the share of due work that fits, so it is never decorative.

**Tested:**
- `.\gradlew.bat :core:test`: all pass (77 tests, including 17 new plan tests with hand-checked numbers).
- `.\gradlew.bat :app:connectedDebugAndroidTest` on the `efa_test` emulator: 9 tests, 0 failed. They cover quick-add then save then a real Buffer then its working, the empty-title message, Check Canvas without the app, no microphone use for typed entry, seeding, and the 1 to 2 migration.
- One screenshot: Buffer "+1.7h to Sat 24 Oct 00:10" from the study cycles alone, Cold Light only.
- Traceability: 47 of 253 rows have a test.

**Dependencies changed:** Espresso 3.7.0 for tests (the older one crashes on Android 16), and kotlinx-serialization 1.12.0 in the app (Room 2.8 needs it).

**Open points (docs/QUESTIONS.md Q-G11 to Q-G14):** which deadline sets the Buffer, what the dial shows, hours from heaviness (1.25 x heaviness), what counts as available time, and a row of steps instead of a true slider. Past study cycles are not tracked until work blocks give them proof, so a missed cycle does not yet reduce the Buffer.

**Not yet proven on a real device:** all of it. The emulator is Android 36.1 only.

## Update: work blocks, one-second hold and in-app proof photo

**Built:**
- `core/.../Block.kt`: proof kinds by block type (code = git commit, study = photo of notes or solutions, fixed commitments = one photo in their window, lectures and tutorials = attendance), the one-second unbroken hold, the verdict rules (timer, hold, window, duplicate, backfill capped at 1 hour and marked recovered), location classes and the distance band, the 64-bit photo fingerprint, and "proof due" listing.
- Room schema 3: `block` and `photo` tables with a tested migration 2 to 3.
- In-app camera (CameraX, no gallery picker anywhere), photo kept in memory only, processed on the phone: fingerprint, about 30 KB thumbnail with no EXIF or location, full photo discarded. Thumbnails older than 14 days or above 25 MB are deleted on launch, and a clear-thumbnails function exists (not yet shown in a settings screen).
- Block screen: timer, hold bar, one capture, one verdict, no review. Home shows the Now card as tappable and a "Proof due" card for finished work blocks still waiting for a photo.
- Seed names tidied (Red Engine session, CS Project, and so on) and a `code` block type added.

**Tested:**
- `.\gradlew.bat :core:test`: 110 tests, 0 failed.
- `.\gradlew.bat :app:connectedDebugAndroidTest` on the `efa_test` emulator (Android 36.1): 25 tests, 0 failed. They include a real camera capture end to end (front camera, the emulator has no back camera), a 500 ms hold that does not finish, a 1.2 s hold that does, no location data in the thumbnail, thumbnails at or under 30 KB with only the thumbnail on disk, a reused photo rejected, late proof recovered and capped, expired thumbnails deleted while verification stays, and both migrations.
- Traceability: 61 of 253 rows have a test.
- No screenshot of the block screen yet; it was driven only by the UI tests.

**Bugs found by the tests and fixed:** the capture button worked before the camera was bound (a "not bound" failure); the app now says "Starting camera" until it is ready. A phone with no back camera now falls back to the front camera.

**Not built, so these cannot be verified (in the app and in `Block.kt`):**
- **Code blocks** need the automatic git commit check (G1). Until it exists the block screen says so. Which GitHub account and repos to check is Michael's decision (Q-G16). 17 of the week's blocks are code blocks.
- **Lectures and tutorials** need the venue directory (L4, L5), a home point, and the location read. The distance logic is built and tested, but there is no venue data, so attendance shows "cannot be verified yet".
- **Backfill** has the rules and tests but no screen to claim recovered time beyond the "Proof due" card.
- No phone blocking during a block yet (that is the launcher and accessibility work).

**Not yet proven on a real device:** all of it. Camera behaviour on Michael's phone, the hold on a real touchscreen, TalkBack use of the hold, duplicate-photo threshold tuning, battery. Steps are in `docs/DEVICE_TESTS.md`.

## Update: venue directory, home point and lecture attendance

**Built:**
- `tools/gen-venues.mjs` asks OpenStreetMap (Overpass) for each lecture and tutorial venue on the timetable and writes `core/.../Venues.kt` and `docs/VENUES_REPORT.md`. All 7 venues matched, 0 unmatched. Education matched as a whole building (Q-G21). Data is ODbL.
- Home point set on a map (osmdroid, OpenStreetMap tiles), saved at about 100 m. No location is read to set it.
- Setup screen: home status, venues within 250 m of home (O4), and every venue with where its point came from. Any venue can be placed on the map; each placement is added to a log table (L5).
- Attendance (L1 to L3): in the lecture window, one photo, then one location read (A8), then the verdict: near the building = full proof; at least 250 m from home = weaker proof; near home = rejected; no precise fix or no home set = a plain message. Only a distance band is stored, never coordinates.
- One-time location permission is asked in the app with its reason, only for attendance (A9).
- Room schema 4: `venue_point` table, tested migration 3 to 4.

**Tested:**
- `.\gradlew.bat :core:test`: 120 tests, 0 failed (distance classes, accuracy cut-off, home rounding, directory checks).
- `.\gradlew.bat :app:connectedDebugAndroidTest` on `efa_test` (Android 36.1): 43 tests, 0 failed. They include a real LocationManager read with a mock provider, the near, away, at-home, no-fix, no-home and outside-window cases against a real database, a user-placed venue overriding the directory with both placements logged, a map picker returning its centre, and all four migrations.
- Screenshots: the Setup screen listing 7 venues, and the home map with real OpenStreetMap tiles and the cross. Cold Light only. I cancelled the map without saving, so the emulator has no home point.

**Found and fixed:** a read from a fused provider that has nothing now falls through to GPS and network. Android only hands location to a foreground app, so the test shows an activity during the read, as the camera does in real use.

**Not tested:** the no-answer path of the location read (Android returns a cached recent fix to the next reader, so it cannot be made deterministic on the emulator). It is covered by the repo tests (no fix gives a retake) and by V9 on the phone.

**Still not built:** the git commit check for code blocks (needs your GitHub details, Q-G16), phone blocking, widgets, shopping list, permission flow. Attendance has never run on a real phone or a real campus; steps are V1 to V11 in `docs/DEVICE_TESTS.md`.

## Update: git commit check for code blocks

**Built:**
- Code blocks (17 a week: CS Project, Battl.io, mini and self projects, language lessons) now end like other work blocks: timer, one-second hold, then "Check commit" where a photo would go.
- The check asks the public GitHub API for your own commits (filtered to your username) in the repos set in Setup. A commit counts if it is dated from the block's start to 15 minutes after its end, and one commit proves one block. A late check is recovered and capped at 1 hour like a late photo (B7).
- Timer, hold and the proof window are checked first, so a too-early tap uses no GitHub request.
- Setup has a GitHub card (username and up to 10 repos, validated). No token or password is asked for or stored.
- Plain messages for: GitHub not set up, no commit found, commit already used, GitHub unreachable or rate limited, repo not found or private.
- Room schema 5: `commit_use` table, tested migration 4 to 5.

**Tested:**
- `.\gradlew.bat :core:test`: all pass (commit window, one commit one block, config parsing, every failure message).
- `.\gradlew.bat :app:connectedDebugAndroidTest` on `efa_test`: 62 tests, 0 failed, run twice. Includes a real request to GitHub from the emulator (octocat/Hello-World parses; a missing repo is reported as not found), the block screen hold then Check commit, the Setup card validating and saving, no token key in the database, and migration 4 to 5.
- Flaky test fixed: the mock-location permission was not in place before the provider was added.
- Not tested: a check against your own repos, because I do not have your username or repos yet.

**Limits, stated plainly:** git commit dates are written by your own computer, so a commit can be back-dated. This makes faking a commit take deliberate effort. It cannot make it impossible. Unauthenticated GitHub allows 60 requests an hour. Private repos cannot be checked.

**Not yet proven on a real device:** all of it. Steps G1 to G9 are in `docs/DEVICE_TESTS.md`.

## Update: blocking, the wall, the blocked card and tamper

**Built:**
- `core/.../Blocking.kt`: the blocking policy as plain arithmetic. Which block types close the phone (K3), the night rule after the cutoff (N2), overrides (N3) and the hard stop (N7), 30-minute app allowances (LA9), the unproven minutes a block would lose (W2), and the default social list (K1).
- An accessibility service that reads only which app windows are in front, on every window change and across all visible windows, so split screen, floating windows and picture-in-picture are covered by design (LA7).
- The blocked card (U7, LA8, LA10): why it is blocked, what opening it costs as arithmetic ("Buffer now +0.4h. With this block lost: NEGATIVE -1.3h."), Go home, and the wall to open anyway. Back goes home, never into the app.
- The wall: a fresh random string, typed by hand, one character per keyboard event; a paste, autofill or a wrong character restarts it; no paste menu, no suggestions.
- Opening an app through the wall in a work block marks the block broken: its time is lost and it can no longer be proved (LA9, W2).
- Night overrides, each with a longer string than the last (N4), and no override within 6 hours of the wake (N7).
- Setup: a Blocking card showing whether the service is on, with a button to Android's accessibility settings; a block list picker (social and video apps pre-ticked), fixed after setup and editable only through the wall (K2, M7).
- Tamper: if the service is switched off, home becomes one "Blocking is off" restore screen, and the off and restore events are logged once each and kept a year (U4, X5, ST4).
- Room schema 6: allowances, broken blocks, tamper log; tested migration 5 to 6.

**Tested:**
- `.\gradlew.bat :core:test`: all pass (policy by block type, night window, overrides, allowances, default list).
- `.\gradlew.bat :app:connectedDebugAndroidTest`: 92 tests, 0 failed, on the `efa_test` emulator with the real accessibility service switched on and real Chrome opened. They cover: the card appearing for a blocked app in a work block, Go home and Back with no loop, an app not on the list opening normally, typing the 75-character wall one key at a time and Chrome then opening, the block marked broken and un-provable, the 30-minute window opening and closing, a night override then a 100-character second override, no override within 6 hours of wake, and restore and tamper logging.
- A screenshot of the real blocked card on the emulator (Cold Light only).

**Found by testing:** UiAutomator switches other accessibility services off while it is connected, so the end-to-end tests read the screen through a UiAutomation with the "do not suppress" flag. Typing the whole wall string in one burst through `adb input text` restarts it, which is the intended behaviour, so the test types one character at a time.

**Not built or not proven:**
- Not tested on a phone. Not tested: the other LA8 routes (a notification, recents, a link, the share sheet, a widget), split screen, floating windows and picture-in-picture, though the service watches every visible window.
- Browsers (K10: blocking sites inside browsers by reading the address bar, including private tabs) is not built.
- Held notifications and the digest (U8, LA2), the persistent notification, widgets, the launcher as the default home with the permission sequence at boot (U1 to U3, LA3, LA14), and the morning block until the ready photo (M6, M8) are not built.
- Earned phone access (R2) and the night override's 45-minute variant on a fully verified day are not wired: the card always offers 30 minutes.
- Boot behaviour, battery and Android 15 restrictions are untested (checklist B15).

## Update: held notifications, the digest, the Buffer notification, fonts and the app look

**Built:**
- `core/.../Notifications.kt`: which notifications are held (only from an app that is blocked right now; never calls, missed calls, alarms, ongoing or group summaries, authenticator, banking or MyUoB packages), the digest (time order, app name, "still blocked until" with no count), and the notification text.
- A notification listener that stores each held notification on the phone first and only then removes it from the shade, so a failed store leaves it where it was. Own notifications are ignored.
- The digest screen and a home card "Held notifications. Waiting for you." with no count (WG7). Seen items are kept 30 days; unseen items are never deleted.
- A persistent, silent Buffer notification on the lock screen with the flame icon, "Updated HH:mm", and a Quick add action that opens Quick add. Refreshed every minute by the blocking service and when Spark opens. No badge, no held count, no motivational words.
- Setup: a Notifications card for the two switches (notification access, the Buffer notification) with a plain reason for each.
- DM Sans and DM Mono bundled in the app (SIL Open Font Licence, files and licences in `app/src/main/res/font/` and `LICENSES/`), used for every piece of text.
- The home screen rebuilt from the drawing: date and "Phone open/closed", a big clock, the Buffer dial with Available, Work and the deadline, a Now card with its place and what it will ask for, a Next card, free to spend, and Phone, Msgs and Maps one tap away. Tactile cards (a light top edge and a solid edge below), tap highlights clipped to the card, and one type scale on every screen.
- `accentText` colour token so the accent used as text passes 4.5:1 in every palette.

**Tested:**
- `.\gradlew.bat :core:test`: 176 tests, 0 failed.
- `.\gradlew.bat :app:connectedDebugAndroidTest`: 120 tests, 0 failed on the `efa_test` emulator. They include a real notification listener with real notifications posted into the shade (held in a work block, not held in free time or for an app not on the list, 15 at once all kept, digest in order, waiting until 12:15, seen items kept 30 days then removed, unseen never deleted), the real Buffer notification (ongoing, silent, no badge, public on the lock screen, replaced not stacked, NEGATIVE shown, Quick add action opens Quick add), and the new home screen and digest.
- Screenshots on the emulator (Cold Light): the new home screen and the Buffer notification in the shade.

**Found by testing:** the DM Sans variable font crashed on start (a duplicate weight setting), which only the UI tests caught. Revoking the notification permission kills the test process, so the "no permission" case is tested through a test switch in `BufferNotifier`.

**Not built or not proven:**
- The quick-settings tile for Quick add (LA11) is not built; the notification action is.
- Restore screen does not yet list notification access being switched off; Setup shows it.
- The 45-minute night override on a fully verified day, earned phone access, and the launcher as default home with boot start are still to do.
- Everything above is emulator-only. See `docs/DEVICE_TESTS.md` N1 to N15 and L1 to L4.

## Update: the launcher as the default home, the permission sequence and boot start (U1 to U3, LA3, LA14)

**Built:**
- `core/.../Launcher.kt`: the setup steps and their order (the default home app is last, so an unfinished setup never traps you), which step is next, what is "later", the crash rule (3 crashes in 10 minutes), and app search ranking.
- A home entry (`HomeAlias`) so Spark can be chosen as the home app, and the home role flow in Setup.
- First-run setup, one step at a time with its reason and one button; the same screen becomes the restore screen when something is later switched off (U4). Phone, Msgs and Maps stay one tap away throughout.
- "Type to open" search: the keyboard opens by itself, a few letters find an app, no recent list (LA5). Blocked apps show as muted text with the reason.
- Boot start: a boot receiver refreshes blocking and the Buffer notification after a restart or an update.
- Crash hand-over: after 3 crashes in 10 minutes Spark switches its own home entry off so the phone's own launcher takes over, blocking keeps running, it is logged once, and home offers "Put Spark back as a home choice".
- Tamper log rows when a setup step goes off and when it is back (`step_off`, `step_restored`).
- Release build with shrinking, for speed testing.

**Tested:**
- `:core:test`: 192 tests, 0 failed.
- `:app:connectedDebugAndroidTest`: 140 tests, 0 failed on the `efa_test` emulator. New: setup flow screens, search screens, making Spark the home role and pressing Home, the role changing away, the 3-crash hand-over and restore, use with wifi and data off.
- LA14 reboots: 100 restarts of the emulator (`tools/launcher-check.py`, results in `docs/launcher-check-1-83.csv`, `docs/launcher-check-84-90.csv` and `docs/launcher-check-91-100.csv`): 83 in the first file, 7 in the second and 10 in the third. After every one Spark was the home app, the accessibility service was bound, the Buffer notification was posted, the schedule snapshot loaded, and there were no crashes.

**Found by testing:** my first reboot runs failed because the script force-stopped Spark, which makes Android switch its accessibility service off. That was the script, not Spark. The emulator also gets slower after about a dozen restarts, so the run was done in three parts.

**Not met or not proven:**
- LA4 speed is not met: cold start median 1.2 s on the emulator (see Q-G38), and the 100 ms keystroke figure is only proven for the matching, not the screen (Q-G39).
- Nothing here is proven on a real phone. A real phone may block or reset accessibility and notification access differently (restricted settings), and real battery management is untested. See `docs/DEVICE_TESTS.md` H1 to H12.
- Every route in LA8 other than opening from the launcher and search is still not tested, and the digest has not been checked for completeness over a real day.

## Update: timetable import, assignment and catch-up weeks, automatic catch-up, and launcher speed (section 25)

Built early at your request. It fits the app (see `docs/IMPORT_COMPLIANCE.md`), with limits the ledger already sets.

**Built:**
- One CSV format, version 1 (`docs/TIMETABLE_CSV.md`): events (add or cancel), deadlines and periods (assignment week, catch-up week). The reader checks the whole file, names the line and column of every problem, and applies nothing if there is one. It accepts commas, semicolons or tabs, quotes, UK or ISO dates, a byte-order mark, up to 1 MB and 2000 rows, and never calculates a cell.
- The table and prompt an AI needs, in the document and behind a "Copy the format for an AI" button, tested to be the same text.
- Import from Share (a file or plain text from an AI app), Open with, or Setup. Always a preview first: what is added or cancelled, the Buffer before and after, and the warnings. A file that cancels a commitment or takes time from a study or code block needs the 75-character wall once. Applying is one transaction. The same file twice changes nothing. An import can be removed (wall).
- Dated changes to the weekly pattern (imported events, cancels, free time turned into work), kept apart from the pattern, so a pattern with none behaves exactly as before.
- Missed lectures, tutorials, study blocks and code blocks come back as work in the Buffer once the backfill window has closed, due the end of the next day, with the reason on home ("Moved for you"). The lost time is not forgiven.
- Free time turns into work under F2 and F3 (from tomorrow, whole slots, under the 17.5% or 2-hour margin, before the driving deadline), never the protected 6 hours a week. Tasks are placed earliest deadline first and split across blocks; a block says what is placed in it. An assignment week or a catch-up week changes only which work goes first and what home says.
- A proven study or code block now credits the open tasks (earliest deadline first), so work can finally be finished.
- Launcher speed: a baseline profile for Spark's own code.
- Database version 7 with its migration. Ledger section 25 (IM1 to IM12, PW1 to PW3, RS1 to RS9), questions Q-G40 to Q-G47.

**Tested:**
- `:core:test`: 298 tests, 0 failed. They include the reader on every kind of bad file and 4000 random garbage files, the shared example files, the wall rule over 300 random files, idempotence over 100 random files, protected free time over a 9-week term, and placing random tasks without losing a minute.
- `:app:connectedDebugAndroidTest` on the `efa_test` emulator: 179 tests, 0 failed (was 140). New: storage, atomic apply, migration 6 to 7, credit from a verified block, the import screens, Share and Open with end to end including the wall, and the blocker's refresh doing the automatic work.
- Cold start, release build with the profile installed (`tools/cold-start.py 10 --compile`): median 0.4 s, all 10 under 1 s. Frame time for each search keystroke is under 100 ms.

**Found by testing:** the Buffer is the smallest slack across deadlines, so adding a later deadline can leave the number unchanged. The preview therefore names the deadline the Buffer is measured to. Real use also showed the automatic step creating catch-up work from earlier test days on the emulator, which is the feature working, and my first tests had assumed an empty list.

**Not built or not proven:**
- Web and Windows read the same format only once they exist (Q-G47). Sharing a file to the web needs the sync layer.
- No walks are added for an imported lecture (Q-G45). A day off and a library week are still planned in the app, which is layer 8.
- What a week does beyond ordering work (Q-G40), how much free time converts (Q-G41, Q-G42) and which missed things return (Q-G43) are defaults for you to confirm.
- Speed and every share route are emulator results only. See `docs/DEVICE_TESTS.md` T1 to T15.

## Update: passive sleep logging, record only (layer 2: S1 to S7, ST4)

**Built:**
- `core/.../Sleep.kt`: a night read from sensor events alone (S1): sleep starts at the first screen-off followed by 60 minutes of screen-off, small night checks under 15 minutes are joined and taken off, a screen-on of 15 minutes or more ends the night (S2, S3), awake time in bed (S4), the charging share, movement count, dark share and logging gaps (S5, S7), and the 30-day clean-up rule (ST4).
- An Android collector inside the blocking service: screen on and off, charging, Android's significant-motion trigger, and a light reading about every 10 minutes while the screen is off. No microphone and no location. A heartbeat every 15 minutes shows the logger stayed alive.
- Database version 8 with its migration: raw events and one summary line per night. Finished nights are summarised, raw events older than 30 days are deleted, and an old summary is never rewritten.
- A Sleep log card in Setup with last night, night checks and logging gaps. It is a readout only: no rule uses sleep yet.

**Tested:**
- `:core:test`: 319 tests, 0 failed, and `:app:connectedDebugAndroidTest`: 197 tests, 0 failed. The core tests include the 25-hour night of the clock change and 500 random event sets.
- Emulator tests: storage, summary, retention, the migration, the card, and the real service recording screen off and on from the power key, charging and unplugging, a light reading, no microphone permission, and a minute of logging writing only a handful of rows.

**Not built or not proven:**
- Nothing here has run overnight on a real phone (Q6, `docs/DEVICE_TESTS.md` P1 to P8). Whether Android keeps the service alive all night on your phone, and how much battery it costs, are unknown until then.
- Android's Sleep API and Health Connect (S5, S6) are not built (Q-G50). The shutdown and wake photos that bound a night are layer 3.
- Sleep drives no rule yet: no alarm, no cutoff change, no short-sleep Buffer weighting.
