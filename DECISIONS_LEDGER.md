# Decisions Ledger: Executive Function App

Oct 22, 2026 · @Michael

## 1. Design principles

Every feature must obey these rules, and a feature that breaks one is a design error. Status: **Locked** means you decided it; **Default** means I set it. In the final review you accepted every Default, so a Default line is now confirmed too. Only lines marked Open are unsettled.

| # | Decision | Status |
| --- | --- | --- |
| P1 | Motivation is net reward (reward minus effort and time cost). The app changes those numbers at the moment of choice and never relies on you caring more. | Locked |
| P2 | Setup happens before the moment of choice, when you are in a good state, so the later moment is cheap. | Locked |
| P3 | One competitor: the phone wins whenever a task's net reward drops, in bed or at the desk. The design targets it everywhere. | Locked |
| P4 | Everything is real and checkable. No fake pressure. Every number can be tapped to show its working, and every rule has a reason you can inspect. | Locked |
| P5 | Stressors are concrete arithmetic (work left against time left), shown early instead of only when a date gets close. | Locked |
| P6 | No long-term-purpose copy: no goals-speak, future-based streaks or motivational text. Everything pays out now or costs less now. | Locked |
| P7 | Friction goes on escaping (edits, overrides), never on starting or finishing. | Locked |
| P8 | Nothing counts until proof exists. Unconfirmed time counts as lost, mostly strict. | Locked |
| P9 | Breaking a block is made expensive by an effort wall, not banned, because a hard ban becomes its own reward to break. | Locked |
| P10 | Every hour belongs to a block with a stop rule. There is no floating time, and free slots are blocks too. | Locked |
| P11 | Late-night messages with close friends are a real reward. The app prices them and never bans them. | Locked |

## 2. Platforms and architecture

The app is built from scratch for Android and the web, with a Windows PC component, and carries nothing over from Companion.

| # | Decision | Status |
| --- | --- | --- |
| A1 | Platforms: an Android app and a web app, built clean with no influence from Companion. | Locked |
| A2 | Windows PC component: a protected background service plus a browser extension, Windows only for now. | Locked |
| A3 | A web page cannot block other sites on its own, so the browser extension and the Windows service are required for PC blocking. | Locked |
| A4 | A work block started on either device applies to both, using one shared block list. | Locked |
| A5 | Sleep detection needs an Android background service and is phone-only. The web version only reads what the phone sends. | Locked |
| A6 | The phone is the source of truth and the web reads from it. | Default |
| A7 | Photos are taken with the in-app camera only (no gallery) and stay on your device only (section 22 sets how long). | Default |
| A8 | Location is read only at the moment a proof photo is taken and is never tracked continuously. | Locked |
| A9 | Permissions are requested in the app, each with a plain reason. | Locked |

## 3. Timetable, commitments and deadlines

The weekly pattern comes from your Master Plan (week type B, the template for week 4 onward), updated with what you told me. Deadlines come from two sources: the timetable and what you add by hand.

**Weekly fixed commitments**

| Day | Fixed commitments | Source |
| --- | --- | --- |
| Mon | Randomness and Computation lecture 09:00-10:00 (Murray Learning Centre LC-UG05); Campus League football 13:00-14:00; Numerical Methods and NLA lecture 16:00-17:00 (Watson LT A) | Plan, football confirmed |
| Tue | Game Theory lecture 10:00-11:00 (Physics West PHYW-LT); Neural Computation lecture 13:00-14:00 (Winifred Hackett WHB-G31). Team training dropped. | Plan, training updated |
| Wed | Neural Computation lecture 11:00-12:00 (Haworth HWTH-101); MyTutor 17:00-19:00 online | Plan |
| Thu | Neural Computation tutorial 09:00-10:00 (Education, Vaughan Jeffreys 135); Game Theory lecture 10:00-11:00; gym 18:20-19:05 | Plan, gym not reconfirmed |
| Fri | Team training 09:00 until you leave at 10:45; Neural Computation lecture 11:00-12:00 (Haworth); Randomness lecture 13:00-14:00 (Arts ARTS-LR2); Numerical Methods lecture 14:00-15:00 (Physics West) | Plan, training updated |
| Sat | MyTutor 10:00-11:00; chores | Plan, not reconfirmed |
| Sun | Weekly review 14:30; church 18:00-19:30 with about 45 minutes' travel each way | Plan, not reconfirmed |
| Any | Personal football: 3 hours a week, flexible, placed by the app wherever it fits | Locked |

**Deadlines and hard alarms**

| # | Decision | Status |
| --- | --- | --- |
| D1 | Timetable-derived deadlines: every lecture creates a study cycle (about 2.5 hours standard, about 1 hour light, per module as in the old plan), due by the end of that day, meaning that night's cutoff, and counted in the Buffer. | Locked |
| D2 | Manual deadlines (assignments, exams, project dates) are added the moment you learn of them through quick-add on the lock screen or home screen: title, deadline and heaviness slider, with defaults so it takes seconds. | Locked |
| D3 | A Check Canvas button in quick-add opens the Canvas app so you can see what you have. The app cannot read Canvas. An optional calendar feed import can come later. | Locked |
| D4 | Hard alarms: exams, paid MyTutor sessions and anything else you mark. Lectures, tutorials and training are not hard alarms. | Default |
| D5 | A lecture missed because of bad sleep stays missed. The catch-up (the recording, at the lecture's length) is added to the Buffer and is due by the end of the next day. The study cycle is still due. Inside a catch-up span the catch-up is due at the span's end (BK1). | Catch-up deadline: Default |
| D6 | In weeks with no deadlines the Buffer shows "no deadline" and not a huge misleading number. | Default |
| D7 | Coursework dates, exam dates, CS Project deadlines and the IMC hackathon date are unknown and entered by hand. | Locked |

## 4. The Buffer

The Buffer is the time you have until a deadline minus the work still to do, in hours. A positive number is margin, and a negative number means the work no longer fits.

```latex
Buffer = time available before the deadline - work still to do
```

| # | Decision | Status |
| --- | --- | --- |
| B1 | The number is called Buffer. "Buffer +20h" is protective margin and "Buffer -3h" means the work does not fit. | Locked |
| B2 | Always visible: lock screen, home screen widget, persistent notification and web dashboard. | Locked |
| B3 | Tapping it shows the full working: each task, its heaviness, its time estimate and the sum. Nothing is hidden. | Locked |
| B4 | Each task gets one heaviness slider (size and difficulty merged). Entering a task costs a few seconds. | Locked |
| B5 | The app turns heaviness into a time estimate and quietly corrects it from how long tasks really take you. | Locked |
| B6 | Unconfirmed time counts as lost and the Buffer falls on its own. You stop the fall by completing a proven block. | Locked |
| B7 | Strictness is about 70% strict, 30% forgiving: a short, capped backfill window lets you fix an honest mistake, and backfilled time is visibly marked as recovered. | Locked; backfill until next morning, up to 1 hour |
| B8 | After a night of 6.5 hours of sleep or less, remaining work counts heavier: hard tasks x1.5 and easy tasks x1.15, because the same task really costs more. The Buffer drops to match. | Locked; the number is adjustable |
| B9 | The nearest deadline drives the margin test used for free slots (section 10). | Default |

## 5. Work blocks, proof and labels

A work block ends only when three things have happened, and the proof photo is the check-in, so there is no separate button to remember.

| # | Decision | Status |
| --- | --- | --- |
| W1 | A block ends when the timer has run out, you have held "done" for one second, and you have taken a proof photo. | Locked |
| W2 | Confirmed time is the sum of completed, proven blocks. An unfinished or unproven block counts as lost. | Locked |
| W3 | Every hour of the day belongs to a block with a stop rule. | Locked |
| W4 | A reset (stand, water, no phone) follows every work block regardless of the Buffer. It is fatigue protection, not a reward. | Locked |
| W5 | Titles are simple and descriptive, with the location where there is one. The outcome, stop time and Buffer stake sit in the description or alt text. | Locked |
| W6 | Plain, exact tone in labels. No nagging or blunt wording. | Default |
| W7 | Names replace the old codes: "Learn Python" for a language lesson, "Notes and problems: Neural Computation" for a study cycle, "Neural Computation lecture - Haworth HWTH-101" for a lecture, "Wake, shower", "Walk to Murray Learning Centre", "Free time - phone open" and "Shutdown". | Locked (examples) |

## 6. Morning

The morning runs on proof. A photo of the setup you prepared the night before stops the alarm, and social apps stay blocked until a second photo shows you are ready.

| # | Decision | Status |
| --- | --- | --- |
| M1 | Setup happens the night before (study space, breakfast equipment) and you photograph it as the reference. The spot is not visible from your bed. | Locked |
| M2 | The alarm stops when a live in-app photo matches the reference. Steps run in parallel as an optional fallback, accepted as the weaker check. | Locked |
| M3 | The photo is the main unlock because it proves something physical happened. Slamming the phone to register steps does not fool it. | Locked |
| M4 | The morning photo is matched against the most recent valid reference photo. If your setup moves around, a fixed standing reference is added. | Default |
| M5 | The phone stays limited until you are upright and the alarm is stopped. Night audio (sermons, binaural beats) is never touched, and the limit starts only when the alarm fires. | Locked |
| M6 | After the alarm stops, all social apps you chose are blocked completely. You pick the list once at first setup, not in the moment. | Locked |
| M7 | The list is fixed. Editing it is allowed but only through the effort wall: a fresh 75-character random string, hand-typed, no pasting or autofill. Three wrong characters per attempt are forgiven (changed 15 Nov 2026, see section 26); the fourth restarts it. | Locked |
| M8 | The block lasts until you are ready, confirmed by a photo of the last getting-ready step at a fixed spot, such as the bag by the door. It is an event, not a timer. | Locked; spot is Default |
| M9 | While social is blocked the phone still allows the Buffer, sermons and music, calls, calendar and maps. | Default |
| M10 | If the camera or the photo match fails: a second attempt, then steps, then a short wait. Calls and messages are never locked. | Default |

## 7. Night and sleep

The night is where your loop starts, so it is priced and not banned. Sleep is measured from the phone, bounded by the two photos, and the day adapts when the night goes badly.

**The night rule**

| # | Decision | Status |
| --- | --- | --- |
| N1 | Cutoff = tomorrow's target wake time minus 8 hours, minus the time you take to fall asleep (starting estimate 20 minutes), so you are asleep by target wake minus 8 hours. | 8 hours Locked; estimate Default |
| N2 | Before the cutoff the night rule blocks nothing (work blocks, the getting-ready window and transitions still block as usual). After the cutoff, the same social list is blocked. | Locked |
| N3 | An override is a 75-character random string: fresh each time, hand-typed, no pasting or autofill, three wrong characters per attempt forgiven and the fourth restarts it (changed 15 Nov 2026, section 26). Each override buys 30 minutes. | Locked |
| N4 | The string gets longer on each extra override in the same night. | Default |
| N5 | After a fully verified day, each override window is 45 minutes instead of 30 for the same string. | Default |
| N6 | The screen shows the 8-hour sum, for example "Wake 09:00, so sleeping after 01:00 means under 8h". | Locked |
| N7 | A final hard stop 6 hours before the target wake time, after which no override is possible. | Locked |

**Measuring sleep**

| # | Decision | Status |
| --- | --- | --- |
| S1 | The shutdown photo and the wake photo bound the sleep window. If the shutdown photo is forgotten, sleep tracking still works from the sensors, with no penalty for sleep, but the setup earns no credit and the morning match uses the last valid reference. | Locked |
| S2 | Sleep start is the last screen-off after the shutdown photo that is followed by a long quiet stretch. Sleep end is the first long screen-on before the wake photo, so scrolling in bed is not counted as sleep. | Locked |
| S3 | A small night check (under 15 minutes of screen-on) is joined into the same night and its screen-on time is subtracted from sleep. | Locked |
| S4 | Awake time in bed between waking and the wake photo is shown as its own number. | Locked |
| S5 | Extra signals: charging state, accelerometer, light sensor and Android's sleep detection where supported. No microphone. | Locked |
| S6 | Health Connect (a watch or band) is an optional input. | Locked |
| S7 | The Android background service must stay alive overnight. A real overnight test is required early, and passive logging starts in the first build. | Locked |

**A bad night**

| # | Decision | Status |
| --- | --- | --- |
| Z1 | The alarm time is worked out from when you actually fall asleep (onset plus 8 hours), unless you choose a wake time (WT1, changed 15 Nov 2026). It does not ring early because an alarm would stop you sleeping again. | Locked |
| Z2 | Only a hard alarm rings early. No alarm rings sooner than about 3 hours after sleep onset unless it is a hard alarm. | Floor Default |
| Z3 | Wake time can drift later by one night, then is pulled back toward your target by 15 minutes a day. | Default |
| Z4 | A missed lecture because of bad sleep adds catch-up work to the Buffer with the reason shown, for example "Bad night, missed Randomness lecture, Buffer -1h". | Locked |

## 8. Blocking on phone and PC

Blocking makes the competitor expensive without banning it. The same fixed list applies on both devices, and every edit goes through the 75-character wall.

| # | Decision | Status |
| --- | --- | --- |
| K1 | In work blocks, all the social apps you chose are blocked, plus YouTube during the day. | Locked; "during the day" read as work blocks and getting-ready time |
| K2 | The block list is chosen at first setup and fixed. Changing it requires the effort wall. | Locked |
| K3 | The phone is unblocked during lectures, training, matches, church, gym, meals and free time. It is blocked during sleep, the wake-up and getting-ready window, transitions, and independent study or work blocks (from your saved preference). | Locked |
| K4 | The PC gets the same block during work blocks: a browser extension plus a native Windows service that writes the hosts file. | Locked |
| K5 | The hosts file is locked, and any change is reverted within about a second. Each attempt is logged. No Buffer penalty for now. | Locked |
| K6 | Stopping the service or uninstalling the app counts as tampering and goes through the same 75-character wall. The service restarts itself. | Default |
| K7 | Blocked domains are listed carefully (www, mobile and video domains) so other Google services are not broken. | Default |
| K8 | Known limits: encrypted browser DNS, VPNs and proxies can route around the hosts file, which is why the extension is a second layer. The standard is to make bypassing expensive, since it cannot be made impossible. | Locked |
| K9 | Starting a work block on either device applies it to both. | Locked |

## 9. Rewards and money

Completing a proven block pays out straight away in several ways at once. The money is a guide only: the app never touches your bank accounts.

| # | Decision | Status |
| --- | --- | --- |
| R1 | Stressor relief: finishing a block visibly improves the Buffer or stops it falling. | Locked |
| R2 | Earned phone access: a completed block opens a defined window where everything on the phone is free. It never overrides the night cutoff or the getting-ready block. | Locked; 10 minutes of open phone per hour of work |
| R3 | A physical reward tied to the block (the next meal, music, the walk). | Locked |
| R4 | Visible progress, such as "14 of 60 hours done". | Locked |
| R5 | Late night is not a reward, because it costs the next day. A good day only makes it cheaper (N5). | Locked |
| R6 | The weekly £80 splits into £40 food, guaranteed every week and never tied to work, and £40 of other money. | Locked |
| R7 | Free to spend is capped at £25 a week. The rest of the £40 goes to savings, so at least £15 is saved every week. | Locked |
| R8 | Payout is linear and weighted by heaviness, with the working shown on tap. | Locked |
| R9 | Money appears the moment a block is verified. The week runs Monday to Sunday, and the unearned part moves to savings at the end of the week. | Default |
| R10 | Inflating heaviness gains nothing, because heaviness also drives the planned total and the time estimates are corrected from your real times. | Locked |

```latex
free to spend = 25 * (weighted verified work / weighted planned work)
```

| Verified / planned | Free to spend | Moved to savings |
| --- | --- | --- |
| 100% | £25.00 | £15.00 |
| 90% | £22.50 | £17.50 |
| 80% | £20.00 | £20.00 |
| 70% | £17.50 | £22.50 |
| 60% | £15.00 | £25.00 |
| 50% | £12.50 | £27.50 |

## 10. Free slots, fatigue and squeezing the day

A free slot is free for the phone but is still a block, so it cannot turn into rot. When the day goes wrong, the app squeezes it in a fixed order and the Buffer absorbs the loss last.

| # | Decision | Status |
| --- | --- | --- |
| F1 | A free slot has a start, a stop time and a next block waiting. All apps are open during it, and an alarm sounds at the stop. | Locked |
| F2 | Each free slot is assigned the night before as a break or a work block. | Locked |
| F3 | It stays a break if the Buffer is at least 17.5% of the work due before the nearest deadline, with a 2-hour floor. Otherwise it becomes a work block, except for the 6 hours a week of protected free time, which is never converted (section 19, G3). | Locked |
| F4 | The assignment cannot be changed in the moment without the effort wall, and the screen shows why. | Default |
| F5 | Late-night chat is free time only inside a free slot. After the cutoff the night rule (section 7) applies. | Locked |
| F6 | Fatigue: a reset after every block (W4), and short-sleep days count remaining work heavier (B8). | Locked |
| F7 | Personal football: 3 hours a week, placed by the app wherever it fits. | Locked |

**Squeeze order** when you wake later or with reduced capacity:

1. Free slots that no longer clear the margin turn into work.
2. The lowest-value blocks drop first, such as extra project time.
3. Standard study cycles become light ones where the module allows.
4. Walk and transition buffers shrink to a safe minimum.
5. Only then does the Buffer take the remaining hit, and the screen shows how many hours each step absorbed.

**Never squeezed:** sleep, meals, the £40 food budget, hard commitments, the reset after each block, and the night cutoff.

## 11. Attendance proof and location

A lecture counts as attended only with an in-app photo taken inside the lecture window at the right place. A typed attendance code is rejected because the app cannot verify it.

| # | Decision | Status |
| --- | --- | --- |
| L1 | The only attendance proof the app counts is an in-app photo (no gallery), taken within the lecture's own time window. | Locked |
| L2 | Location check, strongest first: within about 150 m of the named building; otherwise at least a 3-minute walk (about 250 m) from home, marked as weaker proof. | Locked |
| L3 | Inside the home radius the proof is rejected, and you can retake the photo once you are out. | Locked |
| L4 | Building locations are preset. The app ships with a directory of University of Birmingham venues and matches each timetable venue to it automatically. | Locked |
| L5 | The directory is built from an authoritative source (the university's campus map data or OpenStreetMap), and every venue on your timetable is checked. A venue that cannot be matched is reported as a failure and the app asks you about that one venue. Corrections are logged. | Locked |
| L6 | If a lecture's room or building changes, the app asks for the new location. | Locked |
| L7 | A Check MyUoB button opens the MyUoB app so you can see the university's record. It gates nothing and does not count as proof. | Locked |
| L8 | Your home is set once on a map, with no house-level detail. | Default |

## 12. Safety floor, failure behaviour and first run

These rules exist so the app never causes harm and never shows an error or a missing piece. You accepted these as defaults in the final review.

| # | Decision | Status |
| --- | --- | --- |
| X1 | Never blocked and never tied to work: calls, emergency calls, maps, banking and the £40 food budget. | Default |
| X2 | The phone is off or dead at alarm time: the alarm is rebuilt at the next boot, and the missed morning is flagged without penalty. | Default |
| X3 | Illness or a day off: one switch pauses the Buffer penalties and the money rules. Switching it on repeatedly needs the effort wall. It applies automatically after a night of 3 hours of sleep or less (H4). | Default |
| X4 | All times are stored with an explicit time zone. The clocks going back on Sunday 25 October 2026 is a required test case. | Default |
| X5 | The service is killed or the app is uninstalled: a loud notification appears, a tamper event is logged, and the effort wall applies. | Default |
| X6 | First-run setup is a short guided flow: blocked list, weekly pattern, the £80 split and food amount, permissions, home location, and the first reference photo. | Default |
| X7 | Every feature states what happens when it fails. No feature may ship as a stub or a placeholder. | Locked |

## 13. Build order and acceptance rules

The app is built in layers, and each layer must work on its own before the next starts. You approved this order. The morning layer and passive sleep logging are built together straight after the core loop (O8).

1. **Core loop:** the Buffer with its working, the weekly pattern seeded from your timetable, quick-add with Check Canvas, and work blocks that end with the timer, the one-second hold and the proof photo. It also includes transitions as counted blocks (T1), the launcher shell with the first-run permission flow (section 17), and the shopping list (section 18).
2. **Passive sleep logging (record only):** screen events, charging, movement and light, gathered from the first build with no rules attached. Layers 2 and 3 are built together straight after the core loop.
3. **Morning:** the night-before reference photo, the alarm that stops on a matching photo with steps as fallback, the social block until the ready photo, and the 75-character wall.
4. **Night:** the cutoff from tomorrow's wake time, 30-minute overrides (45 on an earned day), and the 8-hour sum on screen.
5. **PC blocking:** the browser extension, then the Windows service that locks and restores the hosts file.
6. **Rewards and free slots:** the £25 weekly guide, earned phone access, the 17.5% margin rule and the fatigue rules.
7. **Calibration:** the app learns how long tasks really take you and corrects its estimates.

**8. Planned changes and finishing:** planned periods and days off with their escalating wall (section 14), voice quick-add and navigation, and the launcher's polish and settings. This layer was missing from the original order and was added in the review.

| # | Acceptance rule | Status |
| --- | --- | --- |
| Q1 | Every feature has a written pass/fail test, and nothing counts as done until it passes. | Locked |
| Q2 | No stubs or placeholders. | Locked |
| Q3 | At the end of each layer the build reports what is built, what is tested, and what is not yet proven on a real device. | Locked |
| Q4 | Every line of this ledger maps to at least one test, so a missing feature shows up as an untested line. | Locked |
| Q5 | Each feature documents its failure behaviour (section 12, X7). | Locked |
| Q6 | The overnight sleep service gets a real overnight test on your phone early, because the old plan never proved it. | Locked |

## 14. Transitions, planned changes, days off and voice

Added after your first review. Every minute between blocks counts, you can plan ahead for a library week or a weekend off, and voice makes entry fast without opening a loophole.

**Transitions**

| # | Decision | Status |
| --- | --- | --- |
| T1 | Every transition is a block with a duration: walks, travel, buffers, changeovers, resets, rinses and cooking. All of it is counted out of the time available in the Buffer, and none of it is free or hidden. | Locked |
| T2 | Transition times start from the old plan's numbers, are calculated with OpenStreetMap routing for each route, and are corrected from measured real times. The library walk is about 15 minutes. | Default |
| T3 | The phone is blocked during transitions (from your saved preference, K3). | Locked |

**Planned changes**

| # | Decision | Status |
| --- | --- | --- |
| C1 | You can plan ahead for a date range with a type: a day off, a change of place (the library), or a focus (catch-up). The days adapt automatically. | Locked |
| C2 | A library period moves work blocks to the library, recalculates every transition to and from it, and schedules catch-up work. Blocking works as in any work block. | Locked; details Default |
| C3 | The current plan: library catch-up until next week, and a weekend off on Sat 24 and Sun 25 Oct 2026. The clocks go back early on Sun 25 Oct, which the app must handle (X4). | Locked |
| C4 | A planned day off removes its work hours from time available, and the Buffer shows the cost before you confirm, for example "Day off Sat: Buffer -6h". | Locked |
| C5 | A day off keeps sleep, meals, the £40 food budget and the safety floor. Nothing counts as lost, and its hours are left out of the weekly money ratio so it does not cut your payout. | Default |

**Days off cannot become a loophole**

| # | Decision | Status |
| --- | --- | --- |
| H1 | There is no fixed cap on days off. Every day off goes through the 75-character wall, and the string gets longer with each day off used in the last 30 days (about 75, 100, 130 characters, and so on). | Locked; lengths are Default |
| H2 | The arithmetic is the first brake: a day off that would push the Buffer negative says so, with the working shown. | Locked |
| H3 | A day off planned ahead costs the base string. A same-day request adds about 40 characters. | Default |
| H4 | An automatic day off after a night of 3 hours of sleep or less needs no typing, but it counts toward the same 30-day tally, so it makes the next day off cost more. | Default |

| # | Decision | Status |
| --- | --- | --- |
| H5 | A weekend off is requested as one unit. A single 75-character string covers both days, and it counts as one use toward the 30-day tally. | Locked |
| H6 | If you forget to request time off, that time counts as lost at first, like any unconfirmed time. You can claim it retroactively with the same string plus the same-day surcharge, but the claim only holds if the adapted following days that cover the missed content are completed by the times those tasks are assigned. If they are, the lost time is restored. If not, it stays lost. | Default |

**Voice**

| # | Decision | Status |
| --- | --- | --- |
| V1 | Speech recognition adds deadlines fast, for example "assignment due Friday, heavy". It extracts the title, the deadline and the heaviness. | Locked |
| V2 | Before saving, a card shows what was heard and parsed. Module names and your own words are added to the recogniser's vocabulary. Recognition on the device is used where available. Perfect accuracy cannot be promised, so the confirmation card is how errors are caught. | Default |
| V3 | Voice also navigates the app and starts planning, for example "show the Buffer" or "I need a day off". | Locked |
| V4 | Voice can start a loosening request (a day off, a block-list change, an override) but cannot finish one. Completion goes through the typed wall and its escalation, so voice is never the loophole. | Locked |

## 15. Questions as first asked (superseded by section 19)

Every item below was settled in the final review. Where an answer in section 19 differs from the default shown here, section 19 wins.

**Questions as first asked (all answered in section 19)**

| # | Question | Default if unanswered |
| --- | --- | --- |
| O1 | How long is the backfill window, and what is its cap? | Same day, capped at 2 hours |
| O2 | What time is the final hard stop at night, with no override? | 03:00 |
| O3 | How long is the earned phone-access window per completed block? | 15 minutes |
| O4 | Is your home within a 3-minute walk of any lecture building? If so, those venues use the named-building check only. | Named-building check everywhere |
| O5 | The Monday CS Project lecture at 13:00 clashed with Campus League football in the old plan. Which is right? | Campus League stays fixed |
| O6 | Do Monday 20:00-22:00 and Saturday 16:45-18:45 football still happen, or are they replaced by the flexible 3 hours? | Replaced by the 3 hours |
| O7 | Do hard tasks weigh more than easy ones on a short-sleep day, beyond the flat x1.25? | Flat x1.25 |
| O8 | Should the morning layer be built before passive sleep logging? | Sleep logging first |
| O9 | Does week type B hold for week 5 onward? Gym, church, the Saturday MyTutor session and chores were not reconfirmed. | Assume yes |

**Added after your first review**

| # | Question | Default if unanswered |
| --- | --- | --- |
| O10 | What string lengths should the days off use, and over what window: 75, 100, 130 over the last 30 days? | 75, 100, 130 and so on over 30 days; same-day adds 40 |
| O11 | What are the library's opening and closing hours? The walk is about 15 minutes and the app computes the exact route. | Pulled from the library's official page; the app asks you once if it cannot find them |
| O12 | Does a night of 3 hours of sleep or less count toward the 30-day tally? | Yes (H4) |
| O13 | Library week: does it also change meals and the weekly football placement? | No, only work blocks and transitions |

| # | Question | Default if unanswered |
| --- | --- | --- |
| O14 | Resolved: you raised the food budget to £40 a week, which covers the £36.42 basket. | None needed |

**Defaults accepted in the final review:** hard alarms (D4); catch-up due the end of the next day (D5); "no deadline" display (D6); phone as source of truth and on-device photos (A6, A7); the ready-photo spot being "bag by the door" (M8); morning allowances (M9); camera failure fallback (M10); escalating override strings (N4); 45-minute windows on an earned day (N5); 20-minute fall-asleep estimate (N1); the 3-hour alarm floor and 15-minute-a-day pull-back (Z2, Z3); tamper handling (K6, K7); free-slot lock (F4); reading "YouTube during the day" as work blocks and getting-ready time (K1); and the safety and failure rules in section 12.

## 16. What to expect and where issues could arise

Expect a useful core early, a rough first two weeks, and about fifteen places where the design can fail. The review also found ten gaps in this ledger, G1 to G10 (table at the end of this section). All ten were answered in section 19.

**A normal day as designed**

1. **Evening:** the app shows tonight's cutoff and tomorrow's wake time (8 hours back from it). You set up, photograph the setup, and take the shutdown photo.
2. **Night:** your audio plays as normal while the phone logs sleep from the screen and sensors.
3. **Morning:** the alarm rings 8 hours after you fell asleep, or earlier only for a hard alarm. A photo of the setup stops it, and social stays blocked until the ready photo.
4. **Day:** lectures need an attendance photo, work blocks need a proof photo, and the Buffer and the weekly money guide move as each block is verified.
5. **Free slots:** the phone is fully open, with an alarm at the stop time.
6. **Night again:** past the cutoff, social is blocked unless you pay the 75-character wall.

**The first two weeks.** The Buffer will be wrong in both directions until the app has learned how long your tasks really take, photo matches will sometimes fail, and the sleep estimates will be rough. Strict rules on top of numbers like that will feel unfair, and a rule that feels unfair is one your intellect drops. The mitigation is a record-only shadow period (G6).

**Where it can go wrong**

| # | Where it can go wrong | What you would see | Planned mitigation |
| --- | --- | --- | --- |
| E1 | Photo volume: setup, shutdown, wake, ready, every work block and every lecture is about 12 to 15 photos a day. This puts friction on finishing, which breaks principle P7. | The proof step feels like a chore and gets dropped. | One quick capture per block, no review screen (G5). |
| E2 | Proof for abstract work: a photo of a screen or notes is weak proof for coding or reading and easy to fake. | A block counts that should not, or the reverse. | A defined proof for each block type (G1). |
| E3 | The photo match fails at 7am because of light or angle. | You are stuck with the alarm ringing. | The fallback ladder (M10). Technical failures are logged as not your fault. |
| E4 | Sleep detection is wrong: the screen stays off while you lie awake, the phone moves, or Android's battery saver kills the service. | A wrong onset time, so the alarm comes too early or too late. | A shadow period, the early overnight test (Q6) and a backstop alarm (G2). |
| E5 | No ceiling on a late wake: with an alarm tied to when you fall asleep, one bad night can run into a bedrot day. | You sleep through a day you did not plan to lose. | A backstop alarm that always rings at a latest wake time (G2). |
| E6 | Android limits: manufacturers kill background apps, exact alarms and full-screen alarm screens need permissions, and app blocking through accessibility is restricted on the Play Store, so the app is likely installed directly. A force-stop or power-off cannot be prevented, only detected and logged. | A silent failure at night or an alarm that does not ring. | Permission checks at setup, a loud "blocking is off" notice, tamper logging (X5). |
| E7 | Windows: an admin can edit the hosts file in safe mode, use another device, or switch on encrypted DNS or a VPN. Antivirus may flag a service that edits the hosts file. | YouTube opens on the PC despite a work block. | The extension as a second layer and tamper logging. Expect expensive, not impossible (K8). |
| E8 | A structurally negative Buffer: your old plan found about 10 hours a week of work more than fits. The Buffer would start negative, and rule F3 turns every free slot into work. | Constant red numbers, no breaks and burnout. | A protected minimum of free time (G3) and a feasibility check at setup (G4). |
| E9 | Early Buffer accuracy: the heaviness slider is a guess until calibration catches up. | A number you know is wrong, which weakens it as a stressor. | The shadow period, and showing how many tasks the estimate is based on. |
| E10 | Voice cannot be used in the library or a lecture. | You cannot add a deadline when you most want to. | Typed quick-add always available (G7). |
| E11 | Indoor GPS is imprecise, so a location check can fail inside a building. | A true attendance rejected. | The wider radius, the weaker not-at-home fallback (L2), and a retake. |
| E12 | Web and phone sync needs a backend, with offline gaps and privacy questions for photos and location. | The web shows stale numbers. | A sync design with photos staying on the device (G8). |
| E13 | The whole system depends on one phone. A lost, dead or broken phone breaks it. | No alarm, no proofs, no Buffer. | A web and PC emergency mode (G9). |
| E14 | Clock tampering: changing the system time to beat a cutoff or a timer. | The rules can be bypassed with little effort. | Elapsed real time and a server time source (G10). |
| E15 | Build size: this is a large app, and building it can consume the hours it is meant to protect. | Hyperfocus on the build, not the work. | Layers that each work alone, and the core loop first (section 13). |

**Gaps found in the first review (all answered in section 19)**

| # | Gap | Proposed default |
| --- | --- | --- |
| G1 | There is no defined proof for each type of block. | Code: a git commit, checked automatically. Reading and notes: a photo of the notes. Problems: a photo of worked solutions. Lectures: the attendance photo. |
| G2 | There is no backstop alarm. | One alarm per day at a latest wake time, always set unless it is a planned day off, stopped by the same photo. Time not set. |
| G3 | Nothing protects a minimum of free time from rule F3. | At least 6 hours a week of free time that is never converted to work. |
| G4 | There is no plan for a Buffer that starts negative. | A feasibility check at first run that shows the shortfall and offers scope cuts, lowest-value blocks first. |
| G5 | The number of proof photos is unmanaged. | One quick capture per block, no review screen. |
| G6 | There is no rollout period before the rules bite. | The first 14 days record only: no penalties and no money. The blocking engine and permissions run from day one (U12). Then the penalties and money switch on. |
| G7 | There is no silent way to do what voice does. | Typed quick-add everywhere, and a setting that turns voice off in the library and lectures. |
| G8 | Sync between phone and web is undefined. | One account with cloud sync for the numbers, and photos staying on the device. |
| G9 | There is no fallback for a lost or broken phone. | A web and PC emergency mode that keeps the Buffer visible and allows planning. |
| G10 | There is no trusted time source. | Elapsed real time plus a server time check, with clock changes logged. |

## 17. Startup, permissions, launcher and design

Blocking and permissions are set up at first run, the app is its own launcher so Android starts it at boot, and the whole interface is brutalist and minimal. The design follows what existing minimalist launchers and focus apps do, shown at the end of this section.

| # | Decision | Status |
| --- | --- | --- |
| U1 | Every permission is requested during first-run setup, in one guided sequence, and setup does not continue until each is granted. Android makes you switch special permissions on yourself in system settings, so the app can guide and verify each step but cannot grant them. | Locked; Android limit stated |
| U2 | Planned permissions: accessibility service, notification access, display over other apps, usage access, exact alarms, camera, location, microphone (voice), physical activity (steps), battery-optimisation exemption, and being the default home app. | Default |
| U3 | Blocking starts by itself on first run and at every boot, with nothing to start by hand. The app is the home app, so Android starts it at boot. Boot-time services follow Android 15's limit on what a boot receiver may start. | Locked; mechanism Default |
| U4 | If a permission is switched off later, or the default home app is changed, the launcher turns into a single "restore this" screen, a persistent notice appears, and it is logged as tampering. Android cannot prevent either, only detect them. | Default |
| U5 | The launcher home screen is text only: time, Buffer, now and next block, and free-to-spend money. No icons, no badges, no colour. | Locked |
| U6 | Apps are reached by typing to search, not by browsing a grid. A short list of essentials is one tap from home, so everything stays accessible: phone, messages, maps, banking, authenticator and camera. | Locked; essentials Default |
| U7 | A blocked app appears as plain muted text with the reason. Opening it shows a plain card with the Buffer cost and the wall. | Default |
| U8 | Notifications from blocked apps are held while a block is on and shown in a digest afterwards, never deleted. Notifications from apps that are not blocked come through normally, unless quiet mode is on (QM1, changed 15 Nov 2026). | Locked; details Default |
| U9 | Notification badges never appear in the launcher, and motion in the app's own screens follows AN1 (changed 15 Nov 2026). App icons and a grid layout are off by default and can be turned on in settings. A change takes effect from the next day, so it is a decision made in advance (the delay is a Default). | Locked |
| U10 | Design: brutalist and highly minimalist. Monochrome, thick borders, one monospaced type family, large type, a hard grid, no gradients or shadows, motion only as AN1 allows, and one warning colour reserved for a negative Buffer. The web app uses the same system. | Locked; specifics Default |
| U11 | Device-owner mode would let the app enforce far more, but it needs a special one-time setup and is not part of the first build. | Default |
| U12 | Blocking runs from first run, and the 14-day record-only rollout (G6) covers penalties and money only. The blocking engine and all permissions run from day one, while penalties and money start after the 14 days. "Day one" means the first use of each blocking layer once it ships. The build order in section 13 does not change. | Locked |

**What other apps do**

| App | What it does | What we take from it |
| --- | --- | --- |
| [Olauncher](https://tanujnotes.substack.com/p/olauncher-minimal-af-launcher) | A text-only home screen. Apps are found by typing in a search drawer, not a grid. Apps can be renamed, even to emoji, and hidden apps live in settings. A plain black background replaces the wallpaper. Time limits are left to Android's Digital Wellbeing. | The text-only home, search-to-open and no grid. We add our own blocking, which Olauncher does not do. |
| [one sec](https://tutorials.one-sec.app/en/articles/3034754) | Needs the accessibility service and the display-over-other-apps permission, with usage access as an optional alternative. Setup is a guided sequence where you flip each toggle yourself. | The guided permission sequence, and the proof that the accessibility and overlay permissions are the standard route. |
| [Android foreground-service rules](https://developer.android.com/develop/background-work/services/fgs/changes) | From Android 12 apps cannot start foreground services from the background except in listed cases. Android 14 requires each service to declare its type and permission. Android 15 limits which services a boot receiver can start. | Boot-time behaviour must be tested on your phone's Android version, and being the home app is the safer route to a start at boot. |

Notification access (reading, holding and dismissing other apps' notifications, granted by you in system settings) is the standard Android route for U8. I am describing that from general knowledge, since the documentation page I opened did not include the details, so the build must verify it.

## 18. Shopping list

The app holds a shopping list so you know roughly what to buy. It comes from your Master Plan (Recipe Book, part C1), with prices from 19 September 2026, so check every price against your receipt. Items marked ESTIMATE were added to the app later and are not in the original book.

| # | Decision | Status |
| --- | --- | --- |
| SH1 | The shopping list lives in the app, one tap from the launcher on shopping day, and items can be ticked off in the shop. | Locked; placement Default |
| SH2 | The shopping trip is a block, with its walk or travel counted as a transition (T1). The old plan had Aldi on Monday at 18:15, which has not been reconfirmed. | Default |
| SH3 | Prices are editable and updated from your receipts, and estimates are shown as estimates. | Default |
| SH4 | The weekly basket (£36.42) fits inside the £40 weekly food budget (R6), leaving about £3.58 a week of room. The original book list on its own is £20.52. | Locked |
| SH5 | The pantry (£25.76) is bought once and used over several weeks, so it is a one-off cost and not part of the weekly budget. | Default |

**Weekly basket**

| Item | Price (GBP) | Note |
| --- | --- | --- |
| Chicken thigh and drumstick, about 1.5 kg (8 pieces) | 4.79 |  |
| Pork leg steaks, about 600 g | 3.99 |  |
| Mackerel fillets, about 300 g | 2.35 |  |
| Free-range eggs, 15 | 2.65 |  |
| Greek-style yogurt, 450 g x 2 | 2.30 |  |
| Apples, 6-pack | 1.65 |  |
| Bananas, 5-pack | 0.85 |  |
| Frozen mixed vegetables, 1 kg | 1.09 |  |
| Salad bag | 0.85 |  |
| Premade fried chicken strips, about 900 g | 4.50 | ESTIMATE |
| Cheese singles x 2 packs | 2.60 | ESTIMATE |
| Large tortilla wraps x 2 packs | 2.20 | ESTIMATE |
| Sweet salad or coleslaw tubs x 3 | 2.10 | ESTIMATE |
| Sauce ingredients: mayo, sweet chilli, honey, hot sauce | 4.50 | ESTIMATE |
| **Total** | **36.42** | The first nine items are 20.52 |

**Pantry (bought once)**

| Item | Price (GBP) |
| --- | --- |
| Basmati rice, 5 kg | 5.79 |
| Fusilli or penne, 1 kg x 3 | 2.85 |
| Granola, 750 g x 2 | 4.30 |
| Ground cinnamon | 0.85 |
| Garlic powder | 0.95 |
| Onion powder | 0.95 |
| Ground ginger | 0.85 |
| Soy sauce, 300 ml | 1.09 |
| Sweet chilli sauce, 285 ml | 1.15 |
| Black pepper | 0.99 |
| Chilli flakes | 0.75 |
| Mixed herbs | 0.65 |
| Smoked paprika | 0.75 |
| Vegetable oil, 1 L | 1.55 |
| Mild cheddar, 400 g block | 2.29 |
| **Total** | **25.76** |

The full meal-by-meal plan with macros is not in this ledger. Only the shopping list is.

## 19. Final review answers

Every open question and gap was answered on 22 October 2026. These answers win over any default shown earlier in the ledger, and every remaining Default was accepted.

| # | Question | Your answer |
| --- | --- | --- |
| O1 | Backfill window | Until next morning, up to 1 hour |
| O2 | Final hard stop at night | 6 hours before wake time, no override after it |
| O3 | Earned phone time per completed block | 10 minutes per hour of work |
| O4 | Home within 3 minutes of a lecture building | The app measures it at setup and uses the named-building check only for close venues |
| O5 | Monday 13:00 clash | Campus League football only. There is no CS Project lecture at that time. |
| O6 | Monday 20:00-22:00 and Saturday 16:45-18:45 football | Replaced by the flexible 3 hours a week |
| O7 | Short-sleep weighting (6.5 hours or less) | Hard tasks x1.5, easy tasks x1.15 |
| O8 | Build order for the morning layer and sleep logging | Core loop first, then both together |
| O9 | Week type B for week 5 onward, including gym, church, Saturday MyTutor and chores | Assume it holds, editable in the app |
| G1 | Proof for each block type | Code: a git commit, checked automatically. Reading and notes: a photo of the notes. Problems: a photo of the worked solutions. Lectures: the attendance photo. |
| G2 | Backstop alarm | 12:00 every day unless it is a planned day off, stopped by the same photo. If onset plus 8 hours would be later than 12:00, the backstop rings first. The backstop counts as a hard alarm, so the 3-hour floor (Z2) does not hold it back. |
| G3 | Protected free time | At least 6 hours a week that the Buffer rule can never convert to work |
| G4 | A Buffer that starts negative | The app shows the shortfall and the lowest-value blocks, and you choose the cuts. Nothing is cut without you. |
| G5 | Photos per block | One quick capture per block, no review screen |
| G6 | Rollout | The first 14 days record only for penalties and money. Blocking and permissions run from day one. |
| G7 | Voice in the library or a lecture | Typed quick-add is always available, and voice can be switched off at set places |
| G8 | Sync between phone and web | One account with cloud sync, with devices paired by a QR code. Photos stay on the phone. |
| G9 | Lost or broken phone | A web and PC emergency mode that keeps the Buffer visible and allows planning |
| G10 | Trusted time source | Elapsed real time plus a server check, with any clock change logged |
| All | Every other Default in the ledger | Accepted |

## 20. Review findings

I reread the whole ledger for contradictions on 23 October 2026. I fixed seven and found five gaps. You settled all five: F1 as described, and F2 to F5 accepted as proposed.

| # | Finding | Result |
| --- | --- | --- |
| 1 | N2 said nothing is blocked before the cutoff, which contradicted work blocks, the getting-ready window and transitions. | Fixed in N2 |
| 2 | F3 could turn every free slot into work, against the protected 6 hours (G3). | Fixed in F3 |
| 3 | X3 said a day off is offered after 3 hours of sleep or less, while H4 said it is automatic. | Fixed in X3: automatic |
| 4 | Sections 15 and 16 still showed old defaults (a 03:00 hard stop, x1.25, a 15-minute phone window) that section 19 replaced. | Section 15 renamed as superseded; section 19 wins |
| 5 | The build order left out the launcher, transitions, voice, days off and the shopping list. | Added to layer 1 and a new layer 8 |
| 6 | "Blocking from day one" (U12) seemed to contradict the build order. | Clarified in U12 |
| 7 | The backstop alarm and onset plus 8 hours could disagree. | Clarified in the G2 answer: the backstop rings first |

**Gaps found in this review, all settled (these F1 to F5 are review items, not the free-slot rules F1 to F7)**

| # | Gap | Proposed default |
| --- | --- | --- |
| F1 | There is no target wake time per day. The night cutoff (N1), the 15-minute-a-day pull-back (Z3) and free mornings all need one. | Settled: target wake = the first fixed commitment of the day minus the getting-ready time worked out from the morning blocks in the weekly pattern. On free mornings you set it yourself. |
| F2 | Android's lock screen cannot host a free-form input box, and lock-screen widgets depend on the Android version. | The Buffer shows as a persistent notification, and quick-add works through a notification action and a quick-settings tile. The build must verify this on your phone. |
| F3 | A "long quiet stretch" (S2) has no length. | At least 60 minutes of screen-off. |
| F4 | The full week type B block list (times, meals, blocks) lives in your Master Plan, not in this ledger. | The build prompt attaches it as seed data, with the changes recorded in section 3 applied. |
| F5 | R9 says money appears the moment a block is verified, but G6 says money starts after 14 days. | R9 applies once penalties and money go live. |

## 21. Second review: what counts and what is proven

Found on 23 October 2026 on a full reread, with your answers. Two wording errors were also corrected: N1 now subtracts the fall-asleep time from the cutoff (it said plus), and N7 refers to the target wake time.

| # | Decision | Status |
| --- | --- | --- |
| Y1 | Every fixed commitment other than a lecture needs one in-app photo, taken inside its time window: team training, matches, gym, church, MyTutor sessions, chores and personal football. Meals are not photographed. | Locked |
| Y2 | Where the commitment has a named place (training pitch, gym, church), the same location check as lectures applies (L2, L3). MyTutor sessions (online), chores and personal football (flexible place) need only the photo inside the window. | Default |
| Y3 | A fixed commitment without its photo counts as unconfirmed, like any block (W2): its time is lost to the Buffer. Hard commitments keep their hard alarm (D4). | Default |
| Y4 | The money ratio (R7, R8) is verified weighted time divided by planned weighted time, counting work blocks and lecture attendance, both weighted by heaviness. Other fixed commitments (Y1) are proven but stay out of the ratio. | Locked |
| Y5 | Personal football (3 hours a week) is a separate protected block. It is extra to the 6 protected free hours (F3, G3) and is never converted to work. | Locked |
| Y6 | Tutorials count as lectures for attendance (L1 to L7). | Default |
| Y7 | The backstop alarm counts as a hard alarm, so the 3-hour floor does not hold it back (G2, Z2). | Default |
| Y8 | Earned phone access never overrides the night cutoff or the getting-ready block (R2). | Default |
| Y9 | Dated plan items (the library week and the weekend off on 24 and 25 October 2026) are entered as planned periods when the app is used, since they will have passed by the time it ships. | Default |

The ledger now has no Open items.

## 22. Storage, browsers and the launcher

Added on 23 October 2026 after your point that the app must not grow without limit and the launcher must be close to perfect. Where this section differs from an earlier line, this section wins.

### Storage

| # | Decision | Status |
| --- | --- | --- |
| ST1 | A proof photo is processed on the phone (match, location, duplicate check), then the full photo is discarded. Only a thumbnail of about 30 KB is kept, and it is deleted after 14 days. | Locked |
| ST2 | Forever, per photo, only a small record is kept: time, block, type, location result (pass or fail with a distance band, no coordinates), match score and a fingerprint used to reject reused photos. A few hundred bytes each. | Default |
| ST3 | Reference photos (the night setup, the ready spot, the optional standing reference) are kept at working size, about 300 KB each. A new one replaces the old one. | Default |
| ST4 | Ceilings: thumbnails at most 25 MB in total, with the oldest deleted first. Raw sleep sensor events are kept 30 days, then reduced to nightly summaries. The tamper log is kept 1 year and the hosts-file log 90 days. All app data stays under 150 MB after 90 days of use, and this is a test. | Default |
| ST5 | Settings shows a storage meter and a clear-thumbnails button. Clearing thumbnails never changes whether a block was verified. | Default |
| ST6 | Cloud sync carries numbers only. Photos and thumbnails never leave the phone. | Locked |
| ST7 | Photos are compressed at capture. Location data is stripped from the image file, and coordinates are never stored apart from your home point and the venue directory (A8). | Default |

### Browsers

| # | Decision | Status |
| --- | --- | --- |
| K10 | Work blocks block the same domains inside phone browsers, including private tabs, by reading the address bar through the accessibility service. Limits, stated plainly: an unusual browser, an in-app web view or a mirror site can slip through, and the build must test the browsers you actually use. | Locked |

### Launcher

| # | Decision | Status |
| --- | --- | --- |
| LA1 | The launcher never traps you. Calls, emergency calls, alarms, authenticator codes, banking alerts and MyUoB notices are never held (extends X1 and U8). | Default |
| LA2 | Held notifications from blocked apps go into one digest, shown after the block, in time order with the app name. Nothing is deleted, and a test checks that none is lost. | Default |
| LA3 | If the launcher crashes it restarts within 2 seconds. After 3 crashes in 10 minutes it hands over to Android's default home, shows a notice and logs it. Blocking keeps running, since the accessibility service is separate. | Default |
| LA4 | Speed is a test: the home screen draws within 1 second of unlocking, search results update within 100 ms of each keystroke, and the launcher works offline and with sync down. | Locked |
| LA5 | Search opens the keyboard by itself and finds apps from a few letters. There is no recent-apps list, because a recent list is a grid by another name. | Default |
| LA6 | Android Settings stays openable. This app's own accessibility, app-info, uninstall and default-home pages go through the wall, detected by the accessibility service. This is best effort: it cannot be prevented, and a bypass is logged as tampering. | Default |
| LA7 | Blocked apps are also blocked in split screen, floating windows and picture-in-picture, because blocking reads whatever app is in front. A test covers each. | Default |
| LA8 | The ways to open a blocked app are all tested: search, a notification, recents, a link from another app, the share sheet, a widget and split screen. All of them end at the same plain card. | Locked |
| LA9 | On the blocked card, passing the wall during a work block ends that block as broken (its unproven time is lost, W2), opens that app for 30 minutes, then blocks it again. Outside a block, passing the wall opens it for 30 minutes. | Default |
| LA10 | Back, home and recents from the card return to the launcher with no loop. | Default |
| LA11 | Lock screen: a persistent notification shows the Buffer and the now and next block. Quick-add works from a notification action and a quick-settings tile. The build must verify this on your phone (F2). | Default |
| LA12 | The launcher is black with white text for OLED, follows the system text size, labels everything for TalkBack, and keeps strong contrast. A light theme is optional in settings. | Default |
| LA13 | Whole-app battery use stays at or under about 5% of the battery per day, measured on your phone. The overnight sleep service is included. | Default |
| LA14 | Launcher acceptance tests: 100 reboots with the launcher and blocking starting each time, every route in LA8, essentials one tap away, the digest complete, and the launcher usable with no network. | Locked |


## 23. Widgets

Added on 23 October 2026 at your request. Widgets are allowed on the launcher home and you can edit them. Where this section differs from U5 (text only home) or U9 (icons off by default), this section wins for widgets. Adding a widget loosens the launcher, so it waits a day. Removing one tightens it, so it is immediate.

| # | Decision | Status |
| --- | --- | --- |
| WG1 | The launcher home accepts widgets. The default home still shows the text-only layout of U5, built from the first four widgets below. | Locked |
| WG2 | Built-in widgets, all in the same text-only style: Buffer, Now and next, Free to spend, Day plan (next 4 blocks), Shopping list, Quick-add, Week glance. The same built-ins can also be placed as Android widgets on a stock launcher (B2). | Locked |
| WG3 | Editing is in-app, in an Edit widgets screen opened from settings or by typing "widgets": add, remove, reorder, resize (1x1, 2x1, 2x2) and choose which data a widget shows. | Locked |
| WG4 | Adding or enlarging a widget takes effect at the next 00:00, like icons and grid (U9). Removing or shrinking one takes effect at once. Pending changes are marked with their start date. | Locked |
| WG5 | Third-party Android widgets are off by default. Turning them on in settings takes effect the next day. A widget from an app on your block list can never be added. | Default |
| WG6 | Third-party widgets are hidden during work blocks, the getting-ready window, transitions and after the night cutoff, and shown again when the block ends. | Default |
| WG7 | No widget shows a badge, an unread count, a held-notification count or any animation. | Locked |
| WG8 | Tapping a built-in widget opens its full screen: Buffer opens the working (B3), Day plan opens the day, Shopping opens the list. | Default |
| WG9 | If data is stale (sync down, phone just booted), the widget shows "stale since HH:MM" in muted text and never a wrong number. | Default |
| WG10 | Tests: add, remove, reorder and resize each widget; the next-day delay on adding; immediate removal; third-party widgets hidden in each blocked state; stale data; and survival of a reboot. | Locked |

The ledger has no Open items. Lines marked Default here are still to be confirmed by you.

## 24. Look and appearance modes

Added on 23 October 2026 after you picked the industrial style. The industrial dial look replaces the brutalist look in U10. Where this section differs from U5, U9 or U10, this section wins.

| # | Decision | Status |
| --- | --- | --- |
| AP1 | Visual style: industrial. A warm-grey, device-like face with rounded cards, one dial for the Buffer, a monospaced label face and a sans-serif face for numbers and titles, and a single orange accent. The web app uses the same style. | Locked |
| AP2 | Four modes: Light, Dark, Night and Colourful, plus Auto. All four share the same layout, so changing mode never moves anything. | Locked |
| AP3 | Night mode is warm amber on near-black. It has no white and no blue light, and its brightness is capped at about 20%. | Locked |
| AP4 | Auto uses Light or Dark by daylight. Night starts from the shutdown photo or the night cutoff, whichever comes first, and ends when the morning alarm stops. | Default |
| AP5 | Colourful uses colour-coded cards (Buffer yellow, now pink, next green, spend blue) with black outlines. It adds no motion and no badges. | Default |
| AP6 | A negative Buffer is the only red in every mode, and it always shows the word NEGATIVE, so it never depends on colour alone. | Locked |
| AP7 | Mode changes apply at once. A mode cannot hide a block or lower any rule. | Default |
| AP8 | Tests: text contrast of 4.5:1 or better in all four modes, readability in Night mode at its dim level, the Auto switch around the cutoff, the clock change on 25 October 2026, and the same dial value shown in every mode. | Locked |

## 25. Timetable import, assignment and catch-up weeks, and automatic catch-up

Added on 29 October 2026 at your request. The timetable can adapt from a CSV file that an AI or you build, assignment weeks and catch-up weeks can be planned, and missed work comes back by itself and is placed in free time. This section only adds. It reuses D1, D2, D3, D5, Z4, C1, F2, F3, G3, H1, H6, P8, B6, W2 and section 10, and where it seems to differ from them, they win.

**The file**

| # | Decision | Status |
| --- | --- | --- |
| IM1 | One CSV format, version 1, with one header and three kinds of row: event (a lecture, tutorial, MyTutor session, training, match, gym, church, errand or walk on a date, or the cancelling of one), deadline (a title, the date and time it is due, and a heaviness) and period (an assignment week or a catch-up week). | Locked |
| IM2 | The same file is read the same way on every platform, from one written format and one set of shared test files. It works on Android now. The web app and the Windows service read it when they exist, from the same format and the same test files. | Locked; web and Windows not built yet |
| IM3 | A table an AI can use to build the file is in `docs/TIMETABLE_CSV.md` with a ready prompt, and the app has a button that copies the same table and prompt. | Locked |
| IM4 | The whole file is checked before anything is applied. Any error rejects the whole file, and each error says the line, the column and what to change. Nothing is guessed or fixed silently. | Locked |
| IM5 | A preview comes before applying: what is added, what is cancelled, the Buffer before and after, and the warnings. Applying always asks. A file that arrives from another app never applies on its own. | Locked |
| IM6 | A file can add and tighten freely. Anything that loosens needs the typed wall of 75 characters once for the file: cancelling a fixed commitment, or an event that takes time from a work block. A file can never create, shorten or remove a study block, a day off or a meal. In Replace mode (IX5) a file may remove or shorten a study block, only inside its span and through the wall. | Locked; length Default |
| IM7 | A deadline row is the same as quick-add (D2). A lecture or tutorial that a file adds creates its study cycle (D1), standard unless the file says light or none. | Locked |
| IM8 | Importing the same row or the same file twice changes nothing the second time. | Locked |
| IM9 | The reader accepts files up to 1 MB and 2000 rows, in UTF-8, with commas, semicolons or tabs, and quoted cells. It never calculates a cell, so a cell that starts with = + - or @ is only text. The file is read on the phone and sent nowhere. | Locked |
| IM10 | On Android a file reaches Spark by Open with, by Share from any app (a file or plain text), or by the Import button in Setup. | Locked |
| IM11 | Applying is all or nothing. If it fails part way, everything is as it was before and the screen says so (X7). | Locked |
| IM12 | An import can be removed from a list of imports. Removing one needs the typed wall, because it can remove a commitment or a deadline. | Default |

**Assignment weeks and catch-up weeks**

| # | Decision | Status |
| --- | --- | --- |
| PW1 | An assignment week and a catch-up week are planned periods (C1) of 1 to 14 days in a file, and up to 28 days in the app's Modify Schedule (RF1, changed 16 Nov 2026). A day off and a library period stay planned in the app, where a day off goes through its wall (H1). | Locked |
| PW2 | A period changes only the order in which work is placed: in an assignment week, deadline work comes before catch-up work when deadlines are equal, and in a catch-up week it is the other way round. It never moves a deadline, never removes work and never lowers a rule. | Default |
| PW3 | Home says which period is running and when it ends, for example "Assignment week, until Fri 6 Nov". | Default |

**Missed work and free time**

| # | Decision | Status |
| --- | --- | --- |
| RS1 | A lecture, tutorial or work block that ended without its proof, once its backfill window has closed (B7, next morning), comes back as work in the Buffer. A lecture comes back at its own length and a study cycle at its cycle size, due by the end of the next day (D5), with the reason shown (Z4). | Default |
| RS2 | Work is placed earliest deadline first into the work blocks that end before its deadline, and a task too big for one block is split across several. | Locked |
| RS3 | When the Buffer is under its margin (F3), free time turns into work from tomorrow onward, one whole free slot at a time, earliest first, only if it ends before the deadline that drives the Buffer. The 6 hours a week of protected free time (G3) never turn into work, and sleep, meals, hard commitments, resets and transitions are never touched (section 10). | Locked |
| RS4 | If everything still does not fit, the Buffer goes negative with the working shown, and nothing is dropped, hidden or forgiven. | Locked |
| RS5 | A missed block comes back once, however many times the app looks at it. | Locked |
| RS6 | Work counts only with proof. A proven work block reduces the open tasks, earliest deadline first, by the minutes it credited (P8). | Locked |
| RS7 | Missed time stays lost (P8, B6, W2). Bringing the work back does not restore the time. Only the claim in H6 can. | Locked |
| RS8 | Every automatic move is shown with its reason, and the free time that turned into work is shown as work. | Locked; amended by Michael on 19 Nov 2026: it is shown on the Work it out page (third page), not on the home page |
| RS9 | Tests: a bad file of every kind is rejected with its line and column, a good file applies, the same file twice changes nothing, a failed apply changes nothing, a loosening file needs the wall, missed work comes back once and is placed and split, protected free time is never converted, and the clock change on 25 October 2026 holds. | Locked |

## 26. Changes of 15 November 2026

Asked for by you on 15 November 2026: the wall allows for a small keyboard and big thumbs, the home screen gets a second page for the whole schedule, the screens are redrawn with less text, and a reminder to enter the lecture attendance code, motion where it helps, a clear way of knowing when to take a photo, rings for shares of a whole, and a way to replace the rest of a day to ring a reminder from a timetable file, and a quiet mode for notifications.

| # | Decision | Status |
| --- | --- | --- |
| WS1 | The wall forgives up to 3 wrong characters in one attempt. A forgiven character adds nothing, so every character of the string still has to be typed. The fourth wrong character restarts from the first. The 3 do not come back during an attempt and a restart gives a fresh 3. A paste, autofill or any several-character input restarts at once and uses no allowance. | Locked (Michael, 15 Nov 2026) |
| SC1 | The home screen has a second page one swipe to the left, or one tap on its dot, showing the whole calendar for the next 28 days: every block in time order, the work placed in each work block, deadlines on their day, days off marked, and the running block marked NOW. It is built from the same slots as the Buffer, the alarms and the blocker, so it cannot disagree with them. The home button returns to the first page. The search bar and the add buttons stay on both pages. | Locked (Michael, 15 Nov 2026) |
| SC2 | Calculations are drawn as well as written: the Buffer working compares available and work hours as bars, the running block shows how far through it you are, and money shows confirmed against planned work. Every bar is drawn from a number that is also shown in words, and the working lines are kept. | Locked (Michael, 15 Nov 2026) |
| LC1 | A reminder to enter the lecture attendance code: a notification with the alarm sound at every full hour of a lecture and at its end, saying which lecture. It is the same at home as in the room, since the code is asked for either way. It reads the calendar, so a cancelled lecture or a day off gives none and an imported lecture gives one. On by default, with a switch in Setup. | Locked (Michael, 15 Nov 2026) |
| AN1 | Motion is allowed in the app's own screens when it explains something you did: a day folding open or shut, the week carousel and its dots, the home page swipe, a bar or the Buffer dial filling to its value when it appears, and a short shake of the wall field when a character is wrong. Every movement is one-shot and at most 0.7 seconds, nothing loops, pulses or runs on its own, no number is counted up, and nothing moves on a widget or a notification (WG7). When Android's animations are off (Developer options or Remove animations) nothing animates. | Locked (Michael, 15 Nov 2026) |
| PH1 | It is always clear when to take a photo. The home screen shows, first, the photo to take now (a filled card saying what to take, where, by when and with the minutes left as a ring) or else when the next one opens. Every block that needs a photo says what and when on the schedule. A notification with the alarm sound arrives when a photo opens and five minutes before it stops counting in full, and is not sent for one already taken. The times are the verifier's own: a lecture or other commitment from its start to its end; a study block from its end, in full for 15 minutes, then as recovered time until the next wake; a code block the same with a commit; the night setup photo in the two hours before the night cutoff. There is a switch in Setup. | Locked (Michael, 15 Nov 2026); the two hours for the night photo is Default |
| CH1 | A chart for a share of a whole is a ring or donut (how much of the week's work is confirmed, how much of a block has gone, how work divides between tasks, how a day divides); a bar is for comparing amounts side by side (work per day). Every chart is drawn from a number that is also shown in words. | Locked (Michael, 15 Nov 2026) |
| RP1 | A timetable file may contain a reminder: an event of type reminder that sounds a notification, with the alarm sound, when it starts, saying its title. It may sit on top of anything and takes no time from anything, so it never blocks or cuts a block and is never the block you are in. | Locked (Michael, 15 Nov 2026) |
| RP2 | A timetable file may replace part of one day: a row of kind replace with a date, a start and an end takes away everything planned in that time, meals, walks, planning and sleep included, and the events of the same file stand in its place. It always needs the typed wall (IM6), the preview names everything it removes, it cannot take in the wake (so the morning alarm stays planned), and it works on one day at a time. This is the only way a file may touch a meal or sleep. | Locked (Michael, 15 Nov 2026) |
| QM1 | Quiet mode, a switch in Setup, holds every notification except essentials while the phone is in a work block or the night: held ones wait in the digest and show when the window ends, with no count. It never holds an essential (MG1), a call, an alarm, an authenticator code, a banking alert or a MyUoB notice (LA1), the phone's own packages (emergency alerts, system, permission and settings messages, Google Play services), ongoing notifications, group summaries, or the categories that are the phone working (navigation, progress, errors, system, services, status, media transport, stopwatch, voicemail, shared location), and never Spark's own. Getting ready, transitions and planning do not make the phone quiet. A night override or the end of the window lifts it. Turning it on is free; turning it off needs the typed wall (75 characters), because it lets notifications through again. Off until you turn it on. With it off, U8 is exactly as before. | Locked (Michael, 15 Nov 2026) |
| SC3 | The schedule page has today at the top on its own, open, with the day's finished blocks folded into one line, and underneath it a carousel of the next 7 days after today, then the 7 after, and so on, each day a tap-to-open dropdown. The home screen's Now and Next cards are read from the same rows as the schedule page, so they show the same titles, rooms, work placed in each block and times, and a block that runs past midnight from yesterday's plan is on today's page and is the block you are in. A reminder never shows as Now or Next. | Locked (Michael, 15 Nov 2026) |
| WT1 | You may choose when the next alarm rings, in Setup under Wake time, in steps of 15 minutes. It replaces onset plus 8 hours for that one morning. It can never be later than the 12:00 backstop (G2) or sooner than 3 hours after you fell asleep (Z2), a hard alarm still rings first (D4), and a planned day off still has no alarm. A time later than the one Spark would have set is a loosening and needs the typed wall (75 characters); an earlier or equal time is free. It is forgotten once the alarm has rung or is long past, and Use the normal alarm cancels it. The blocks you sleep through are missed as usual (W2, RS1): choosing a wake time moves the alarm and nothing else. | Locked (Michael, 16 Nov 2026) |

## 27. The schedule reflows: modifiers are spans that lay the day out again

Asked for by you on 16 November 2026. A modifier is not a pile of tasks laid over a fixed timetable. The schedule adapts to it as a whole, from any point in time, and the same way for every modifier: a late start, a catch-up week of three days, an assignment week. It amends MG7, MG8 and MG18 of docs/WEEK_TYPES_SPEC.md as RF1 and RF4 say.

| # | Decision | Status |
| --- | --- | --- |
| RF1 | A modifier is a span with a start and an end, each a date and a time, so it can begin this afternoon and end tomorrow noon. It is 1 to 28 days (MG8 said 1 to 14). A span that starts today never changes what has already passed, and a block that began before the span's start or ends after its end is left as it is. | Locked (Michael, 16 Nov 2026); 28 days is Default |
| RF2 | Whatever the span does, these stay exactly where they are: lectures and tutorials still running, the lecture study cycles, matches, training, gym, church, MyTutor and errands, anything dated (imports, football, other modifiers' blocks), hard alarms, sleep, the protected free time (G3) and the walks and travel that go with them. A day on a planned day off is not touched. | Locked (Michael, 16 Nov 2026) |
| RF3 | Devote: a span can give its days to whole study blocks by module. They are laid in the gaps the fixed things leave, each the chosen length (never under 45 minutes), with a reset after it, so a block is one block and never scattered in pieces. The ordinary work blocks and free time in the window become study; the project and code blocks too unless you turn that off. Meals, chores and the routine keep their place. Lecture cycles count toward the day's floor. A module has a cap a day. If the floor cannot fit, the day says how many blocks are missing and why. | Locked (Michael, 16 Nov 2026); 45 minutes and the reset are Default |
| RF4 | A day can start at a time you choose, and everything movable follows. The morning routine runs from that time. A meal keeps its length and its place if it still fits, else it slides up to 1 hour earlier or 3 hours later but not past 21:30 and at least 2.5 hours after the meal before, else it is dropped and the preview says so. A day that starts after 13:00 has no breakfast. Work keeps its order and its length where there is room, is trimmed to no less than 30 minutes where there is not, and is dropped with the reason when there is none. A start that runs into a commitment is refused with the commitment named. Sleep and hard alarms never move. Anything dropped, and a start later than planned, is a loosening and needs the typed wall (IM6). This is how MG7 and MG18 read: a meal or the routine may move or be dropped only as this says. | Locked (Michael, 16 Nov 2026); the numbers are Default |
| RF5 | In a catch-up span the floor of study blocks a day is always placed if it fits. The stretch is placed only while backlog is left to clear, so a clear backlog does not fill the week with more. | Default |
| RF6 | Late start is a modifier of its own: choose the dates and the time the day starts, and it applies RF4 to each day. It cannot start at a time that has passed today. | Locked (Michael, 16 Nov 2026) |
| RF7 | The same span, settings and calendar always give the same plan, for any length of span and any start. The commitments of RF2 are in the same place afterwards. A plan that breaks a guard rail cannot be applied. | Locked (Michael, 16 Nov 2026) |
| RF8 | Every preview says what the span comes to in numbers, before and after: study and work hours over the days and a day, study cycles and hours of notes, lectures still on, free time, hours by module, and each day's hours and blocks. A block says how its time is spent (video or slides, reading, notes, practice, error log). | Locked (Michael, 16 Nov 2026) |
| RF9 | A day laid out again is all or nothing. If part of it can no longer stand because the calendar changed (an import or a day off), the whole day goes back to the plan underneath, so a moved meal is never left cancelled with nothing in its place. Lectures the modifier cancelled stay cancelled. | Default |

## 28. Re-plan the day from now

Asked for by you on 18 November 2026, after going through situations: waking at 2 or 3pm and needing a plan, a last-minute change, and a task that runs over. Both use the reflow of section 27.

| # | Decision | Status |
| --- | --- | --- |
| RN1 | Late start can start the day from now, today only. The day starts at the current time rounded up to 5 minutes: wake, Bible and breakfast follow, then meals, work and free time, around your commitments. What was planned before now is already missed (W2, RS1) and its work comes back as ever. Starting later than planned gives nothing up that was not already given up, so it needs the typed wall only for what it drops or shortens, and a breakfast that is past is not one of them. When nothing has been eaten yet that day, the first meal may be placed as late as any meal (21:30) instead of being dropped. | Locked (Michael, 18 Nov 2026) |
| RN2 | Running over: when what you are doing is taking longer, you say by how many minutes (5 to 180). What has started is not touched, nothing movable is laid before the new end, and the rest of today slides after it: free time is used first, then meals move inside their windows, then work is shortened to no less than 30 minutes or dropped, and each of those is said with the reason and needs the typed wall. A commitment inside the overrun stays where it is and the preview says so. Sleep and hard alarms never move. It is for today only, once a day; to run over more, remove it and set it again. Both are one tap from Modify Schedule under Right now, straight to the preview. | Locked (Michael, 18 Nov 2026) |
| YP1 | One yield policy decides what gives way whenever time is short, for every modifier and every change to the plan. In order: free time that is not protected, resets, meals (slid inside their window, dropped last), the routine (breakfast first, then the Bible and the wake each cut to 10 minutes), chores (slid, never dropped), work (shortened to no less than 30 minutes, then dropped, and it returns as work, RS1). Never: sleep, hard alarms, commitments, protected free time. A refusal is the last step and says what is in the way and what it would need. Anything below the minimum, dropped or shortened is a loosening and needs the typed wall. | Locked (Michael, 18 Nov 2026); the order and the minimums are Default |
| YP2 | Everything that changes the plan in the middle of a day goes through one entry, a disturbance: the day starts at a time or from now, a block runs over by some minutes, or time is taken by something new. Each is a choice of numbers on the same engine and the same policy. Add an event: a title, a type, a start and an end on one day; everything movable gives way around it by YP1, and it is refused over a commitment, sleep, the morning routine or the night routine, over a time that is already over, or with no title. It may take protected free time because it is your own choice. A start that falls inside a commitment that is running begins when that commitment ends, and says so. | Locked (Michael, 18 Nov 2026) |
| YP3 | Every kind of disturbance, on every day of a month and at many times, either makes a plan that keeps every commitment and sleep where it was, or is refused with a reason. None goes silent. | Locked (Michael, 18 Nov 2026) |
| YP4 | The rest of the family of changes to the plan is built on the same engine and policy, each a choice of numbers: **Something is cancelled** (time is freed: a lecture or commitment on one day that matches your words is not happening, its time becomes free, no work comes back from it, and it needs the typed wall; sleep, meals and the routine cannot be cancelled), **Recovery** (lighter days: routine, meals, lectures and sleep stay, work is held to a number of hours a day, and every hour set aside comes back as work due after the last day, so nothing disappears; typed wall), **Away** (lectures and tutorials pause, work is lighter, the way out and the way home are placed as events; lectures come back as work unless they are not running, which is a holiday; typed wall), **Guests** (the visit is the time spent with them, not the whole day; each preparation job is placed in free time before they arrive, closest days first, and a job with no free time is placed where the day can give way for it, else it is said that there is no room; tidying follows; a visit may be on a day off) and **Revision** (each exam planned backwards in short interleaved sessions, with the assignment planner). A transition that belongs to no commitment gives way to what is added. Events on one day are laid out together. | Locked (Michael, 18 Nov 2026); the numbers are Default |
| MC1 | Ten minutes after a meal block ends Spark asks "Did you eat?" with two answers, Ate and Not yet, on the notification and on the home screen. Not yet asks again in 30 minutes, up to 3 asks; Ate closes it; a question nobody answered for 3 hours is dropped, not carried to the next meal. The bedtime drink is not asked about. It costs nothing, changes no plan and never needs the wall, because it is about your health. The answers are kept for a week. There is a switch in Setup. On by default. | Locked (Michael, 18 Nov 2026) |
| HM1 | The home screen has no Change button, because a button that edits the plan invites changing it. What happened (running over, started late, out, going to bed later) is reported from the Now card and the evening, which is free (MX4). Everything else is changed in Modify Schedule, where the cost is shown (CE9). This replaces the Change button of 18 Nov 2026. | Locked (Michael, 19 Nov 2026) |

The ledger has no Open items. Lines marked Default here are still to be confirmed by you.

## 29. Anchors: the day's edges are data

Added on 19 November 2026 at your go, from docs/MASTER_PLAN.md and docs/LIFE_MODEL.md. It reuses the earlier sections and where it seems to differ from them, the earlier locked rule wins unless a row below says it amends it.

| # | Decision | Status |
| --- | --- | --- |
| SA1 | Every night has two anchors, lights-out and wake. Setup gives the defaults and any night or span can override them. Everything that depends on the night reads them: the night cutoff, the alarm, meal windows, the end of the planned day, and the guard rail. | Default |
| SA2 | The guard rail "in the night" becomes: no non-free block before wake or after lights-out. It replaces the fixed 06:00 and 23:00. | Default. Amends MG guard rail |
| SA3 | Lights-out to wake is at least 8 hours (N1 stays Locked). An anchor pair under 8 hours is refused. | Default, reuses N1 |
| SA4 | Moving lights-out and wake together keeping 8 hours is a Reality change, free. Moving lights-out later alone is a Loosen and needs the wall. | Default |
| SA5 | An anchor that hides a commitment (a lecture before the wake time) is refused naming the commitment, with the choices. | Default |
| SA6 | A day with a clock change is 23 or 25 hours long and no block is lost or doubled. | Default |

## 30. Change economy

Added on 19 November 2026 at your go, from docs/MASTER_PLAN.md and docs/LIFE_MODEL.md. It reuses the earlier sections and where it seems to differ from them, the earlier locked rule wins unless a row below says it amends it.

| # | Decision | Status |
| --- | --- | --- |
| CE1 | Changes have four classes: Reality, Add, Move, Loosen. The class comes from the effect on obligations in the preview, not from the screen. | Default |
| CE2 | Reality and Add are free and unlimited. | Default |
| CE3 | Move: 5 free a week, then a 25 character string. | Default |
| CE4 | Loosen: the existing wall; the 3rd in a week longer; the 4th locks loosening until Monday 00:00. | Default |
| CE5 | Imports: checking is free; applying has 2 free a week, then the short string, then a 24 hour cooldown on the 6th. | Default |
| CE6 | The first 7 days after install count without locking. | Default |
| CE7 | Break glass: once per 30 days, the longest string lifts one lockdown or cooldown. Logged. | Default; built: Work it out and the blocked card, once in 30 days, 150 characters |
| CE8 | A restore point is written before every apply. Undo within 24 hours is free and refunds the count. | Default |
| CE9 | The meter shows in Setup, Modify Schedule and the import preview, never on the home page. | Default |
| CE10 | A change that loosens the economy takes effect after 24 hours. | Default; core only: no screen offers it yet |

## 31. Plan sources: one path, merge or replace

Added on 19 November 2026 at your go, from docs/MASTER_PLAN.md and docs/LIFE_MODEL.md. It reuses the earlier sections and where it seems to differ from them, the earlier locked rule wins unless a row below says it amends it.

| # | Decision | Status |
| --- | --- | --- |
| IX1 | A file compiles to the same instances and goes through the same engine as Modify Schedule. There is one path. | Default; built: a file's events are laid out again with the same engine as Modify Schedule (`reflowEvents`): a meal, the routine or a chore gives way, what cannot fit is a warning, what is shortened is a loosening. Lectures, sleep and anything hard are never moved by a file |
| IX2 | Two modes, chosen in the app: Add to my plan, or Replace my plan for these days. A file cannot choose. | Default |
| IX3 | Add: commitments win, study blocks are never shortened, refusals have reasons. | Default |
| IX4 | Replace covers the file's span. Sleep, hard alarms, meals, protected free time, the wall and anything marked keep stay. Obligations are re-placed and any shortfall is shown. | Default |
| IX5 | Replace is a Move if every obligation fits and a Loosen if any is dropped. A file may remove or shorten a study block only in Replace mode, inside its span, through the wall. | Default. Amends IM6 (Locked): needs your yes |
| IX10 | The modifier rows of a file are checked in the order the file gives them, each on top of the ones before it and of the file's own events, which is also the order they are applied. A file can therefore carry a whole programme: lectures off, then nights that would otherwise be refused for hiding a lecture, then a catch-up laid out after the nights. Work a file adds counts as backlog for the rows after it. | Default; built |
| IX12 | The nights a file carries count for the rows after them: a catch-up row on a day with a late night is checked against that night's lights-out, not against the default 23:00, so study can run as late as the night allows. | Default; built, tested |
| IX11 | Order does not widen what a file may do: it still cannot cancel, shorten or create a study or project block (IM6), in any order. The one exception is an override (OV1), which says so in its own column. | Default; tested |
| IX6 | Format v2 adds `modifier`, `anchor` and `keep` rows; v1 files still read. | Default; built: `modifier` (ten presets), `anchor` and `keep` rows, checked with the same preview and guard rails as the app, applied as modifiers, remembered by row hash, removed with the import. A `keep` row protects its time from a Replace |
| IX7 | A restore point is written on every apply. | Default |
| IX8 | The preview shows placed, moved, refused, shortfall and the Buffer before and after. | Default |
| IX9 | All platforms read the same file the same way, from shared fixtures. | Default; built: one set of fixtures in `core/src/test/resources/csv` is read by the Kotlin reader and by `web/check.js` |

## 32. Backlog spread

Added on 19 November 2026 at your go, from docs/MASTER_PLAN.md and docs/LIFE_MODEL.md. It reuses the earlier sections and where it seems to differ from them, the earlier locked rule wins unless a row below says it amends it.

| # | Decision | Status |
| --- | --- | --- |
| BK1 | Inside a catch-up span a missed-lecture catch-up is due at the span's end. | Locked (Michael, 19 Nov 2026) |
| BK2 | The backlog is spread oldest first and balanced by day, with the floor and stretch of RF5, and the shortfall is a number. | Default |
| BK3 | Coursework dates, exam dates and hard alarms never move. | Default |
| BK4 | Outside a span D5 is unchanged. | Default |
| BK5 | A backlog left at the span's end falls back to D5 and a re-plan is offered. | Default |

## 33. Place and time zone

Added on 19 November 2026 at your go, from docs/MASTER_PLAN.md and docs/LIFE_MODEL.md. It reuses the earlier sections and where it seems to differ from them, the earlier locked rule wins unless a row below says it amends it.

| # | Decision | Status |
| --- | --- | --- |
| PL1 | A span can set a time zone. Alarms, meals and lights-out use it. | Default; built: `BlockingState.zone` is the zone of the active Away span and the day, alarms, meal checks, the night rule and prompts read now in it |
| PL2 | Attendance and place checks are exempt while Away covers the day, and the exemption is shown. | Default |
| PL3 | Alarms are kept in absolute time across a zone change. | Default; built with PL1: the wake alarm is stored as an instant, so a change of zone does not move it |
| PL4 | The clock change rule SA6 applies in every zone. | Default |

## 34. Emergency and out

Added on 19 November 2026 at your go, from docs/MASTER_PLAN.md and docs/LIFE_MODEL.md. It reuses the earlier sections and where it seems to differ from them, the earlier locked rule wins unless a row below says it amends it.

| # | Decision | Status |
| --- | --- | --- |
| EM1 | An Emergency door (call, emergency call, location share, maps and taxi, bank, authenticator, health) is never gated and is on the home page, the blocked screen and the lock. | Default. Extends X1, LA1 |
| EM2 | "I'm out until HH:MM" pauses blocking and the night rule only if the phone is not at the home point; the day reflows on return. | Default |
| EM3 | Urgent messaging stays on the existing override (N3). It is not loosened here. | Default; no new code: the existing override stays |
| EM4 | Break glass as CE7. | Default; built (CE7) |
| EM5 | A lost-phone checklist is one screen on the PC and web view and the first-run screen. | Default |

## 35. Modifiers are five effects

Added on 19 November 2026 at your go, from docs/MASTER_PLAN.md and docs/LIFE_MODEL.md. It reuses the earlier sections and where it seems to differ from them, the earlier locked rule wins unless a row below says it amends it.

| # | Decision | Status |
| --- | --- | --- |
| MX1 | A modifier is built from five effects: Demand, Supply, Event, Anchor, Policy. | Default |
| MX2 | The thirteen presets stay as named recipes. | Default |
| MX3 | A custom composer builds any combination with a preview. | Default |
| MX4 | Reality reports are on the Now card and in the evening, and they are free. | Default |
| MX5 | Stored instances migrate. Nothing is lost. | Default |
| MX6 | Policy can set screen-time limits and the essentials list for a span, so a weekend off can still have limits. | Default; built: a screen-time limit setting on Period off |

## 36. The interface

Added on 19 November 2026 at your go, from docs/MASTER_PLAN.md and docs/LIFE_MODEL.md. It reuses the earlier sections and where it seems to differ from them, the earlier locked rule wins unless a row below says it amends it.

| # | Decision | Status |
| --- | --- | --- |
| HS1 | The home page has modes by the moment: Morning, In a block, Between, Free, Wind-down, Night, Out, Paused. | Default |
| HS2 | Each mode has one hero and at most four cards above the fold. | Default |
| HS3 | Page 2 (Schedule) stays as it is. | Default |
| HS10 | The home page has one order, always: the clock, NOW (the hero, with Running over, Started late and Something came up in the card), NEXT as a timeline of the next three things, then what is waiting for an answer (two at most, the rest one tap away), then the Buffer, then the quieter cards. Nothing waiting for you can push Now off the first screen. | Default; built |
| HS4 | Page 3 is a what-if page that changes nothing until Apply. | Default |
| HS5 | There is no Change button. | Locked (Michael, 19 Nov 2026) |
| HS6 | The bottom row is a dock with icons: Phone, Messages, Maps, Camera, Bank. Setup is a small gear. | Default; built: drawn icons in the dock |
| HS7 | Text is label and value only. No loose paragraphs. | Default; checked as far as a machine can: labels are short and sentence-free, steps are 90 characters at most; the rest is a design rule |
| HS8 | Every refusal and empty state says what happened, why, and the next step. | Default; built: `refusalLine` writes every refusal in one place and a test checks each says what happened and when it ends |

## 37. Continuity, retention, ideas and assurance

Added on 19 November 2026 at your go, from docs/MASTER_PLAN.md and docs/LIFE_MODEL.md. It reuses the earlier sections and where it seems to differ from them, the earlier locked rule wins unless a row below says it amends it.

| # | Decision | Status |
| --- | --- | --- |
| CT1 | A versioned backup file is written nightly and on demand to `Downloads/Spark/`. | Default |
| CT2 | First run finds the newest backup and offers it with counts. | Default |
| CT3 | A persistent release key signs every build; the build refuses a different signature. | Default |
| CT4 | After a reinstall one screen lists every permission with a button to each. | Default |
| CT5 | Android Auto Backup stays off. | Default |
| LT1 | Lite mode shows three things for a chosen number of days; Spark offers it after three missed blocks. | Default |
| LT2 | Fresh start re-plans from now with no red wall and no streaks. | Default |
| LT3 | No blaming wording anywhere. | Default |
| PK1 | A parking lot for ideas that change nothing, with a Sunday review. | Default |
| PK2 | "This doesn't work" writes a local report to `Downloads/Spark/reports/`. | Default |
| AS1 to AS7 | Scenario bank as tests, invariants over random plans, dead-end audit, device tests, real-phone checklist, traceability. | Default |

## 38. Routines and steps

Added on 19 November 2026 at your go, from docs/MASTER_PLAN.md and docs/LIFE_MODEL.md. It reuses the earlier sections and where it seems to differ from them, the earlier locked rule wins unless a row below says it amends it.

| # | Decision | Status |
| --- | --- | --- |
| RT1 | A day is blocks, a block holds routines, a routine holds steps. | Default; the runner on the third page runs them one step at a time; nothing prompts them at their time yet |
| RT2 | Every routine has three tiers (Full, Short, Floor). The floor is never dropped. | Default; the runner on the third page runs them one step at a time; nothing prompts them at their time yet |
| RT3 | A step has a cue, an instruction, a time, a proof tier, a gate, a reward and a fallback. | Default; the runner on the third page runs them one step at a time; nothing prompts them at their time yet |
| RT4 | Steps may run in parallel only where a routine declares it. | Default; the runner on the third page runs them one step at a time; nothing prompts them at their time yet |
| RT5 | The yield policy picks the tier from time, sleep and context. | Default; the runner on the third page runs them one step at a time; nothing prompts them at their time yet |
| RT6 | The Bible routine is first, always, with a floor of 10 minutes; breakfast is cut before it (your plan). | Locked (from your seed, kept by Michael 19 Nov 2026); the runner on the third page runs them one step at a time; nothing prompts them at their time yet |
| RT7 | A new or changed routine has an observe week, then a suggest week, then it enforces. | Default; the runner on the third page runs them one step at a time; nothing prompts them at their time yet |
| RT8 | Prompts fade by scaffold level: Guided, Cued, Tracked, Yours; they return when a habit slips. | Default; the runner on the third page runs them one step at a time; nothing prompts them at their time yet |

## 39. Facts and proof

Added on 19 November 2026 at your go, from docs/MASTER_PLAN.md and docs/LIFE_MODEL.md. It reuses the earlier sections and where it seems to differ from them, the earlier locked rule wins unless a row below says it amends it.

| # | Decision | Status |
| --- | --- | --- |
| FB1 | Every signal becomes a fact on one bus. Everything that asks "was it done" reads the bus. | Default; partly: NFC taps and the routine runner write facts; photo, sleep and meal proofs are still separate stores |
| FB2 | The proof ladder has five tiers: none, tap, sensor, NFC, photo. A step names the tier it needs. | Default; partly: NFC taps and the routine runner write facts; photo, sleep and meal proofs are still separate stores |
| FB3 | A step is done if a fact of the needed tier is inside its window. | Default; partly: NFC taps and the routine runner write facts; photo, sleep and meal proofs are still separate stores |
| FB4 | An ignored required proof leaves the step open, and its gate stays shut; when the window closes it is unproven: no reward, unconfirmed time, no blame. | Default; partly: NFC taps and the routine runner write facts; photo, sleep and meal proofs are still separate stores |
| FB5 | A technical failure closes the step as not your fault (M10 stays). | Default; partly: NFC taps and the routine runner write facts; photo, sleep and meal proofs are still separate stores |
| FB6 | NFC taps and sensor facts are accepted where the step names them. A tag read away from its place is flagged and accepted at lower confidence only. | Default; partly: NFC taps and the routine runner write facts; photo, sleep and meal proofs are still separate stores |
| FB7 | Where two signals disagree, one strong or two weak are needed. | Default; partly: NFC taps and the routine runner write facts; photo, sleep and meal proofs are still separate stores |
| FB8 | Photos stay the proof for now. NFC stickers are bought later and the NFC tier is already in the ladder, so a tag drops in without rework. | Locked (Michael, 19 Nov 2026) |

## 40. Context and places

Added on 19 November 2026 at your go, from docs/MASTER_PLAN.md and docs/LIFE_MODEL.md. It reuses the earlier sections and where it seems to differ from them, the earlier locked rule wins unless a row below says it amends it.

| # | Decision | Status |
| --- | --- | --- |
| CX1 | One context record: place, day type, state, body, mood, phase. | Default |
| CX2 | Places are named, with their own tags, Wi-Fi and routine variants: the flat, the family home, campus, library, church, pitch, shop. | Default |
| CX3 | The day type is proposed from the calendar and place and confirmed once; you never assemble a day. | Default |
| CX4 | You have two homes, and "home" in the location rules is whichever you are at. | Default |

## 41. The gate and the door pool

Added on 19 November 2026 at your go, from docs/MASTER_PLAN.md and docs/LIFE_MODEL.md. It reuses the earlier sections and where it seems to differ from them, the earlier locked rule wins unless a row below says it amends it.

| # | Decision | Status |
| --- | --- | --- |
| GT1 | One gate decides what the phone allows, reading the day model, the context, the facts and the doors. No other part decides. | Default; built: one `decide` reads the day model, the cap, the screen limit, out, the allowances and the morning state; `RulesTest` shows each door closing or opening it |
| DP1 | A door is any feature that relaxes a rule. They are listed in `COHERENCE_AND_BYPASS.md` s4. | Default |
| DP2 | All doors draw from one pool in two units, obligation minutes removed and gate minutes lifted, over a rolling 7 days. | Default numbers: 600 obligation and 180 gate; a day off costs 240 obligation minutes, so a two day period off fits a fresh pool |
| DP3 | Verifiable doors cost less than unverifiable ones. | Default; partly: the WhatsApp wall, the night override, a period off and the lighter modifiers draw from the pool; the other doors do not |
| DP4 | At the cap, doors lock except the Emergency door, a verified ill day and break glass. Sleep protection is never relaxed by any door. | Default; partly: the pool locks the WhatsApp wall, the night override, a period off and the lighter modifiers; break glass lifts a lock |
| DP5 | Precedence: safety, sleep, hard commitments, routine floors, study, free time, rewards. | Default; built: `Precedence` and `precedenceOf` own the order; a test shows free time gives way before study |
| DP6 | A property test opens doors in every combination and order and shows the cap holds. | Default |
| GT2 | WhatsApp is capped: it opens for 10 minutes at a time and then a cooldown follows (30 minutes). Ending the cooldown early needs the typed wall. An active call is never cut off. Messages (SMS) and phone calls are not capped. It applies at all times. It replaces the seed's open WhatsApp in Tier 0. Loosening the cap takes effect after 24 hours. | Locked (Michael, 19 Nov 2026); cooldown length Default |

## 42. The coach and rewards

Added on 19 November 2026 at your go, from docs/MASTER_PLAN.md and docs/LIFE_MODEL.md. It reuses the earlier sections and where it seems to differ from them, the earlier locked rule wins unless a row below says it amends it.

| # | Decision | Status |
| --- | --- | --- |
| CO1 | One voice, one prompt at a time. | Default; built: one voice (Coach), one prompt at a time (20 seconds apart), routines prompted at their time by `RoutinePrompter` |
| CO2 | Four prompt levels (haptic, spoken, notification, gate), never more, never repeated endlessly. | Default; built: feel, spoken, notification, then the gate that already holds for the block; each level once; it stops after half an hour |
| CO3 | A fixed haptic vocabulary. | Default |
| CO4 | Every prompt is one sentence with the verb first. | Default |
| CO5 | An optional mood tap twice a day, used only for the review and to notice a run of low days. | Default |
| CO6 | A run of low days shows two actions: Lite mode, and a people list (GP, the university's Mental Health and Wellbeing Service, Student Disability Service, NHS 111, Samaritans 116 123, Shout 85258). Spark never diagnoses. | Default; built on the Work it out page |
| CO7 | A reward menu of five of your own, offered at random on a proven block or routine. | Default |
| CO8 | No streaks, points, badges or red for a missed day. | Default |
| CO9 | Today's wins: three true facts at wind-down. | Default |
| CO10 | Three identity sentences you write, quoted back sparingly with a fact. | Default |
| CO11 | A tone setting: Calm, Plain, Light. | Default |

## 43. The sleep programme

Added on 19 November 2026 at your go, from docs/MASTER_PLAN.md and docs/LIFE_MODEL.md. It reuses the earlier sections and where it seems to differ from them, the earlier locked rule wins unless a row below says it amends it.

| # | Decision | Status |
| --- | --- | --- |
| SL1 | A 60 minute wind-down anchored to lights-out: tidy, soft screen, brain dump, tomorrow's fun, prayer and stretches, warm milk, dock, shutdown photo. | Default |
| SL2 | Docking is a tag tap at the charger across the room; the night rule still applies if it is not done. | Default; built as an option: off by default, read only while Spark is open; photos stay the proof until it is on and a tag is registered |
| SL3 | An Awake tap offers a list: up, low light, no phone, back at 20 minutes. | Default |
| SL4 | "Tomorrow's fun" puts a night-time urge into a real slot tomorrow. | Default |
| SL5 | A gradual shift moves wake earlier by 15 minutes every 2 to 3 days, never more than 30 in a day, with a morning light step. | Default; core only: no screen uses it yet |
| SL6 | Your own reminders for medication and the like, logged as taken or not taken, with no advice from Spark. | Default |
| SL7 | One 20 minute nap window before 15:00 after a night under 4 hours. | Default |
| SL8 | Spark does not use sleep restriction. | Default |
| SL9 | Greyscale and a warm screen at wind-down use a permission granted once from the PC (`adb shell pm grant app.efa android.permission.WRITE_SECURE_SETTINGS`). Without it a warm overlay is used and greyscale is skipped, with a note. | Locked (Michael, 19 Nov 2026) |

## 44. Home and the university

Added on 19 November 2026 at your go, from docs/MASTER_PLAN.md and docs/LIFE_MODEL.md. It reuses the earlier sections and where it seems to differ from them, the earlier locked rule wins unless a row below says it amends it.

| # | Decision | Status |
| --- | --- | --- |
| HO1 | Home mode: no lectures, revision and project blocks, family meals as fixed events, a portable seat tag, a family-time window. | Default |
| HO2 | The family home is a place with its own home point, Wi-Fi and tags. | Default; built: set the family home where you stand; Spark tells which home you are at about every 15 minutes |
| HO3 | The winter vacation arc (14 Dec to 8 Jan) is a recommended recipe: land, holiday, ramp, final prep. | Default |
| HO4 | A support card with the university's real routes: coursework extension (before the deadline), extenuating circumstances (after), Reasonable Adjustment Plan. | Default |
| HO5 | A Travel day and a leaving checklist; a return routine and a sleep shift before going back. | Default; core only: no screen uses it yet |
| HO6 | The calendar carries the university's real term dates. | Default |

## 61. The block carries its steps

Added on 20 November 2026 at your request ("Block carries steps", from the systems map). The class: what a block asks you to do is on the block, so there is no second place to go and run it.

The coherence check for this change: it is owned by Do; it reads the block, last night's sleep and the facts of today and writes the same facts as before (a step, a routine done), so the Coach, Count and Right now see no difference; it is shown on the block screen; the hold starts the block and the last step finishes it, so there is one start and one finish; it removes the runner list on Right now, the Start card and the hand-over to another page; a block with a cycle, a block nobody started, a routine done earlier and a rename are handled below; it is proven on the real card.

| # | Decision | Status |
| --- | --- | --- |
| BS1 | A block that is a routine (the wake and getting ready, the Bible, the wind-down, a code or other work block) carries its steps on its own screen: one list at the tier the time left in the block and last night's sleep allow (RT5), each step done by a tap, a registered tag or its proof, with the tier chips to change it. A study block has the cycle for its steps and shows no list. The Start card is gone; the hold on a block that can be started only starts it, and a block with steps and no hold (the sleep block) has just its steps. | Default; built, tested on the emulator |
| BS2 | When the last step is done the routine is done: a fact is kept once for the block, a reward is offered, and a block that was started is finished by it, so no second hold is needed. A block nobody started is not given a start by finishing its steps. | Default; built, tested on the emulator |
| BS3 | The runner is no longer a list on Right now, and a Start button no longer sends you to another page. Today's wins stay on Right now. The Coach counts a step done on the block as it counted one on the runner (the same fact). | Default; built, tested on the emulator |
| ST1 | (amended again, 20 November 2026) There is no Start card. A routine block shows its steps (BS1). | Default; built, tested |
| VR15 | (amended) The hold that starts a block no longer hands its routine to another page; the steps are on the block. | Default; built, tested |

A block is still matched to its routine by its type and the start of its title (Bible, Wake, Breakfast dress, Evening prayer, a sleep block, a work block). Keeping a routine id with each block would need a change to the stored plan and its migration on the phone; it is left as it is, and a block renamed by hand would lose its steps.

## 60. The cycle is linked to its block, and drawn once

Added on 20 November 2026 at your request (the cycle linked to its current study block, accurate icons on the main card, and a simpler subsystem). The class: a cycle belongs to one block and every card that shows it says which; and one thing is drawn by one piece of code.

The coherence check for this change: it is owned by Do; it reads the block's hours and the stored cycle and writes nothing; it is shown on Home and on the block screen, from the same card; it replaces the block screen's own copy of the running cycle and the Now card for a block whose cycle is running; the cycle that outlives its block, two cycles at once, and no cycle are handled below; it is proven on the real card and the real app.

| # | Decision | Status |
| --- | --- | --- |
| VR16 | The cycle card on Home is linked to its study block: the block's name and hours are at the top, with where the cycle stands against it (the block ends in 40 min, or ended at 12:30, 15 min ago, and the cycle runs on). Tapping the header or the ring opens the block. When the block is the one on now, this card is the Now card for it and the block is not drawn a second time; it carries Something came up and Change the plan, which can still happen mid-cycle. When a cycle is left over from an earlier block and another runs in the block that is on now, the one in the block that is on now is the current one; with none on now, the one whose block ended last. | Default; built, tested on the emulator |
| VR17 | Each step has its own icon, drawn so it needs no font: V a screen with a play triangle, R an open book, N a pencil over a line, P a sheet with a question mark, M a tick and a cross, a break a cup with steam. The icon of the step in hand is in the middle of the ring, and every chip has its step's icon beside its letter. | Default; built, looked at on the emulator |
| VR18 | The running cycle is one card, drawn once, on the home screen and on the block screen: the ring with the step icon and the time, what to do, the five chips, Pause or Resume, Page done on notes, and the hold. The block screen no longer has its own copy; it adds only the line about timed pages and, when not started or done, the chips and the start or the summary. | Default; built, tested on the emulator |

## 59. Shelves: Setup and Right now

Added on 20 November 2026 at your request ("yes, build it", move 3 of the systems map). The class: a page with many things on it shows what matters and names the rest, so nothing has to be hunted for and nothing is shown that is not needed.

The coherence check for this change: it is owned by Surfaces; it reads and writes nothing new, since every card keeps its own behaviour; it replaces the page Work it out with Right now and moves three of its sections to Setup, where the same kind of setting already was; a thing that was in two places (the permission checks, the reminders, the places) is now in one; a folded shelf starts folded and remembers nothing, so the page is the same every time you open it; it is proven by tests with the shelves folded, and the other tests run with them open.

| # | Decision | Status |
| --- | --- | --- |
| SH1 | A shelf is a group of cards under one heading and a line saying what is inside. It starts folded, a tap opens it and a tap folds it. | Default; built, tested on the emulator |
| SH2 | Setup is the Change the plan card and five shelves: Permissions and health (blocking, notifications, the sleep log, browsers, Health Connect and the permission list), Reminders (photos, meals, lecture codes and quiet mode in one card, and your own reminders), Morning and study (the morning photos and the study cycle), Look and home (type to open, appearance, widgets, voice, your home, the family home and the lecture venues) and Links and tools (the university, a Windows PC, GitHub, the shopping list and the walkthrough). Your reminders, the family home and the permission list moved here from the third page. | Default; built, tested on the emulator |
| SH3 | The third page is Right now (it was Work it out). It shows what is true now: what Spark moved for you, what today looks like, lite day, the nap offer after a short night, the routine runner and today's wins. The rest is on shelves: Change something (sleep later or earlier tonight, the winter vacation), Calm and notes (how you are, a place for ideas, your rewards, when you cannot sleep), Your data and help, Emergency (the Emergency door, I am out, the lost phone steps and the university's routes), Plan tomorrow, The coach and Tools (NFC tags, sleep audio, break glass). The Emergency door is still one tap from Home. | Default; built, tested on the emulator |
| SH4 | The shelves start folded in the app. The tests switch them on so every card can be reached, and the shelf tests switch them off. | Default; built |

## 58. The wake time moves the morning

Added on 20 November 2026 at your request ("yes, do it", gap 4 of the systems map). The class: a wake time is one thing, so choosing it changes the alarm, the Wake block and everything that follows it together.

The coherence check for this change: it is owned by Plan (the morning) and Night and morning (the alarm); it reads the day's Wake block and writes the alarm and one Late start; it is shown where the wake time is chosen, in Change the plan, and everything else (Home, the Schedule page, the blocker, the alarm) reads the calendar and the alarm as before; choosing again or going back takes the old one away; it replaces the old alarm-only choice and the line that said the blocks you sleep through are missed; a day with no Wake block, a wake not later than the planned one, a wake that cannot stand and one that needs the wall are handled below; it is proven on a real database.

| # | Decision | Status |
| --- | --- | --- |
| CW1 | Choosing a wake time later than the day's Wake block (by more than 5 minutes) sets the alarm and makes a Late start for that date, so Wake, Bible, breakfast and the rest of the day follow the time chosen. It is checked and priced like any Late start: if it cannot stand (it clashes with another change on that day, or the time is outside 04:00 to 18:00) it is refused with the reason and the normal alarm and morning stay; if it gives something up it needs the wall, as a wake later than Spark's own alarm already did. A wake that is not later leaves the morning where it is. | Default; built, tested on the emulator |
| CW2 | Choosing again starts from the normal morning, so choices never stack, and Use the normal alarm takes the Late start away as well as the alarm. Only the Late start a wake choice made is taken away; a Late start you made yourself is never touched. | Default; built, tested on the emulator |
| CD2 | (amended 20 November 2026) The wake card says the morning starts when you wake, and that anything fixed before it is missed and comes back as work. | Default; built, tested |

## 57. One door for change

Added on 20 November 2026 at your request ("yes, build it", move 2 of the systems map). The class: every way of changing the plan is one place you go to and say what is happening, and each opens the screen that shows what it does, what it costs and applies it.

The coherence check for this change: it is owned by Change; it reads nothing new and writes nothing new, since each choice opens the screen it already had; it is shown on Home (a chip on the Now card) and in Setup (one card); a choice starts at the door and the screen it opens comes back to the door; it replaces four Setup cards and the wake time card; the screens that wait for their data and the back path are handled below; it is proven by a test through the real app.

| # | Decision | Status |
| --- | --- | --- |
| CD1 | There is one place for every change to the plan, Change the plan: It is running over, I started late, Something came up, A new deadline, A day off or a weekend, A library period, A file from my timetable, and A week or a period (Modify Schedule). Each choice opens the screen it had before, which shows what it does before anything moves, what it costs and applies it, and its Back comes back to the door. It is reached from the Now card on Home (the chip Change the plan, beside the three that were there) and from Setup. Adapt stays automatic. | Default; built, tested on the emulator |
| CD2 | The wake time for tonight is part of the same door and works as it did: a time in steps of 15 minutes, a later one than the normal alarm needs the wall, and going back to the normal alarm. (Amended the same day: it now moves the morning too, CW1.) | Default; built, tested on the emulator |
| CD3 | Setup no longer has the Modify Schedule, Days off, Library, Timetable and Wake time cards. It has one card, Change the plan, which opens the door. | Default; built, tested on the emulator |
| CD4 | The day off and library screens open before their data has been read, and used to go straight back when it had not; they now wait for it, and give up after 3 seconds. | Default; built, tested on the emulator |

## 56. Asking, and one Start per block

Added on 20 November 2026 at your request, from the systems map (docs/SYSTEM_MAP.html) and your answers: a held start is worth a small reward, and when a study block ends mid-cycle Spark asks what to do, through a confirmation system you can use for anything else that needs confirming. The class: a thing that cannot decide for you asks once, in one place; and a block has one start, which writes the one record everything else reads.

The coherence check (the seven questions) for this change: it is owned by Do (the start and the cycle) and Count (the reward), with the question itself a shared mechanism; it reads the cycle, the run record and the plan, and writes the run record, the question store and earned access; it is shown on Home, on a notification and on the block screen, all from the stored values; the hold starts it and the cycle's end or stop closes it, and the Coach, Count and Guard all read the run record; it removes the Start card from study blocks and from blocks that already have the Run card; the block ending, the cycle already stopped, a restart and a second ask are handled below; it is proven by one test through the real Home screen.

| # | Decision | Status |
| --- | --- | --- |
| CF1 | Anything that cannot decide for you raises a question: a kind (who handles the answer), a title, a body and up to three options. Questions waiting are shown at the top of the home screen's list of things waiting for you, one button per option, and on a notification with a button for each option that does not need the app (an option that does, such as a preview, opens the app instead). A new kind of question is a branch where answers are handled and a call that raises it; it gets the card and the notification without anything else being built. | Default; built, tested on the emulator |
| CF2 | A question is raised once for its id and never again, even after it is answered. | Default; built, tested |
| CF3 | An answer must be one of the question's options and counts once. | Default; built, tested |
| CF4 | A question whose subject is over is closed without an answer and its notification goes: the block-end question when its cycle is no longer running. | Default; built, tested on the emulator |
| CF5 | Questions are kept in the small key-value store (nothing in the schema changes) and listed oldest first. | Default; built, tested |
| VR15 | When a cycle that began inside its block is still running after the block has ended, Spark asks once: Stop the cycle, Let it run on, or Move the rest of the day. Stop ends the cycle now with no remedial and no tuned N time. Let it run on changes nothing and the question is not asked again. Move opens the existing Running over preview for the minutes the cycle still has to run, to the next 5, and nothing moves until it is applied. A cycle started after its block ended is chosen, so it is not asked about. The question is checked each second while Home or the block is open, and when a step ends with the screen off. | Default; built, tested on the emulator |
| VR15 | (one start) A cycle's start is also the block's start, written to the one run record, so the Coach's prompts that ask whether you started a routine stop once the cycle is started (this was a gap: it read only the routine and the Run record). A study block no longer has a separate Start card for its routine: the cycle is its start. A block that has the Run card (routine, meal, errand, planning) no longer has a separate Start card either: the hold that starts it also hands its routine to the runner when it has one. A block that is a routine and has no other start (the sleep block) keeps its Start card. | Default; built, tested on the emulator |
| SR1 | A held start earns a small reward: 2 minutes of earned open phone, for each of the first 5 starts of a day (a block started with the Run card, or a VRNPM cycle), at most 10 minutes. It is the same earned access as proof buys: it is spent only while a transition or planning block would have closed the phone, and it is gone at midnight. A start is not proof and earns nothing in the money guide. The block's card says what the start bought. | Default; built, tested on the emulator |
| ST1 | (amended 20 November 2026) The Start card is for a block that is a routine and has no other start. A study block and a block with the Run card do not have it (VR15). | Default; built, tested |

## 55. The VRNPM timer and the weekly remedial

Added on 20 November 2026 at your request ("build it all"), from the study loop spec you gave: V video, R read, N notes, P practice, M marking; "each timer is allowed to overrun, but it has to show that it's overrun". The class: a study cycle is a timed loop that is honest about its time.

| # | Decision | Status |
| --- | --- | --- |
| VR1 | A study block (study, study-standard, study-light) has a VRNPM card on its block screen. The optimal cycle is V 10, R 5, N 45, P 30, M 15 (105 minutes in the steps), with 5 minutes between the steps and 10 after N (130 with the breaks), in that order. It is started with a hold that fills over 6 seconds (the cap, ST2). | Default; built, tested on the emulator |
| VR2 | Five chips show V R N P M and where the cycle is. The step in hand shows its time left and a bar filling. A hold of 1 second ends a step and begins the next (a break, then the next step), and the last hold ends the cycle. | Default; built, tested on the emulator |
| VR3 | A step may overrun and never stops by itself. It then shows OVER and counts up (+m:ss) in the strongest contrast on the screen, its chip shows a plus, and the overrun stays on the chip and in the summary after the step is done (for example Over: V +3 min). Half a minute either way is on time. | Default; built, tested |
| VR4 | N is planned as pages times minutes a page, to the nearest 5, between 20 and 90. A Page done button on N times real pages. A finished cycle that timed pages sets the N time of the next one from four pages at the average, so the 45 is tuned by your own pages. | Default; built, tested on the emulator |
| VR5 | The block's own proof does not change: after the cycle the notes photo is taken as before (W1, G1). The timer is the structure of the block and does not replace its proof. | Default; built |
| VR6 | Each finished cycle books its remedial (30 minutes with the optimal cycle, set under VR7) (new similar questions from the week's mistakes, then the Confident tier) as a task, once, due on the Sunday evening of its week, or the Sunday after the catch-up span when it was done inside one. The week's remedial is then the 30 minutes times the cycles, in sessions of at most 90. | Default; built, tested |
| VR7 | The cycle has settings, in Setup under Study cycle (VRNPM): the minutes of each step V, R, N, P and M (5 to 120, in 5s), the break between steps (0 to 15) and after N (0 to 20), the longest cycle (the most the steps may add up to, 45 to 240, or no limit: over it every step shrinks in proportion, to the nearest 5 and never under 5), the pages in a cycle's notes (2 to 10, for tuning N), and the remedial a cycle earns (0 to 60). Two presets set the times: Optimal (V10 R5 N45 P30 M15, 105 minutes, 30 minutes of remedial) and Ideal (V15 R15 N65 P45 M25, 165 minutes, 10 minutes of remedial). A cycle keeps the plan it started with, so a change to the settings does not change one already running. Timed pages keep tuning the N setting. | Default; built, tested on the emulator |
| VR14 | The cycle is one thing in one place, and every screen follows it. The home card, the block screen and the lock screen notification all read the stored cycle each second, so a change made on any of them (a hold, Pause, Resume, Page done) shows on the others within a second or two. After a hold on the home card the card is set at once from the stored cycle and does not wait for the whole home screen to reload. A step cannot be ended twice by one hold: a second end within 0.8 seconds of the last change is ignored. | Default; built, tested on the real app on the emulator |
| W1 | (amended again, 20 November 2026) Just after a hold completes the button waits, for the new step to show or 0.8 seconds, and ignores presses, so a slow screen cannot be held again by mistake into the step after. | Default; built, tested on the emulator |
| VR13 | The cycle says what to do. Each step has its own instruction (V watch the video or slides, R read the section, N copy the notes by hand with the phone away, P do the Foundation and Building questions, M mark and correct each one, a cross by each wrong answer, C or U before the solution, one line per mistake) and a break says to stand, drink water and put the phone away. It is shown on the block's card, on the home card and in the lock screen notification. A reminder with a sound, public on the lock screen, goes when a step ends and says which step is up, what is next, and what to do in it; while a step stays over it comes again every 5 minutes (0 to 15, 0 is not again). Both are settings in Setup under Study cycle (on by default). The last step's reminder says to hold to finish the cycle and take the proof photo. A step done by override is skipped when saying what is next. No reminder is made while paused, and the last one goes when the cycle moves on. | Default; built, tested on the emulator |
| W1 | (amended 20 November 2026) A hold that completes starts again from empty, so one button can be held for the next thing it is asked to do. It used to stay full and could not be held again. | Default; built, tested on the emulator |
| VR12 | A running cycle is also on the lock screen: an ongoing, silent notification, public on the lock screen, with a live clock that counts down to the end of the step and, once it is over, counts up from when it should have ended and says OVER. It shows the step, the five steps (done, now, to come, done by override), and the block. Tapping it opens the app. Its one button is Pause, or Resume while paused; the next step is not on it, because that is a hold in the app (ST2) and must not be done by accident from a pocket. An alarm at the end of the step turns the clock into OVER on time with the screen off. It goes when the cycle is finished. Whether the lock screen shows it in full is also your phone's own setting for the app. | Default; built, tested on the emulator |
| VR11 | A running cycle is shown big at the top of the home screen: a ring that is the whole cycle (each step and break as wide as its time, finished ones full, the one running filling, an overrun in the strongest contrast), the cycle symbol and the time in the middle, the five step chips under it, and the controls: Pause or Resume, Page done while on notes, and the hold that ends the step. Tapping the ring opens the study block. It ticks by itself every second and is gone when no cycle is running. | Default; built, tested on the emulator |
| VR10 | V and R can be done by override (a setting, and the 2.5 hours preset sets it with the ideal N 65, P 45 and M 25: 65 + 10 + 45 + 5 + 25 is 150 minutes). A cycle then starts at N: V, R and the breaks before N are not in it. The timer shows V and R as V* and R*, says * done by override, not timed, and the finished summary says VR done by override, so it is never quiet. It applies to cycles started after it is set; a cycle already running keeps its own plan, and nothing in the timetable moves. A plain toggle, no wall (your decision). | Default; built, tested |
| VR9 | A running cycle can be paused and resumed from its card. The time paused is not counted in the step (a long pause is never an overrun), the card says PAUSED and how many minutes have been paused, the pauses are kept with the cycle, and the finished summary says Paused N min. The cycle's time leaves them out, and timed pages leave them out too. Moving to the next step while paused ends the pause first. A finished cycle cannot be paused. | Default; built, tested on the emulator |
| VR8 | A setting says the Red Engine is built. While it is on, no remedial is booked, and switching it on removes the remedial not yet started (work already begun stays). It is a plain toggle with no wall (your decision, 20 November 2026, against the usual rule that removing work is a loosening); switching it off does not bring the removed ones back. A remedial time of 0 also books none. | Default; built, tested on the emulator |
| ST3 | (amended) The day strip also tracks blocks with a proof: a study, code or lecture block is done when its proof is in, running while it runs, and not done once its time has gone without one. The sentence under the strip counts done, shifted, running, not done and did not happen. | Default; built, tested |

## 54. Start, end, say

Added on 20 November 2026 at your request: "a hold button that fills up proportional to its duration to start a non proof block ... a way that I can always confirm that or stay engaged with the timetable", with started late and running over put together and drawn. The class: a block with no proof can still be engaged with, truthfully, and the day is never silently wrong.

| # | Decision | Status |
| --- | --- | --- |
| ST2 | A block of type routine, meal, errand or planning has a Run card on its block screen. Not started: Hold to start, which fills as it is held and takes 2 seconds for a block up to 15 minutes, 6 seconds from 2 hours, in a straight line between (friction on starting is capped on purpose; letting go early starts nothing). Started: the bar fills through the planned time, and Hold to finish takes 1 second. The real start and end are kept. Free time, sleep, travel and blocks with a proof are left alone. | Default; built, tested on the emulator |
| ST4 | Late and over are the same events as Started late and Running over, and never silent. A start 5 minutes or more late says so and offers Slide the rest of today by the minutes (to the next 5); a block still running past its end says how far over and offers the same, and +10 or +20 when its end is near; a block finished over offers it for an hour after. Each opens the existing Running over preview, so nothing moves until it is applied. Under 5 minutes is on time. | Default; built, tested on the emulator |
| ST5 | A block that did not happen is said so with a reason (Too tired, Ran out of time, Something came up, Other). Something came up goes on to the existing Something came up flow. A block whose time has come with no start and no answer is shown as not started, and the routine prompts (CO2) stop once it is started by the hold. | Default; built, tested on the emulator |
| ST3 | (taken off the home screen on 20 November 2026 at your request; the strip is still built and tested, and is not shown) The home page showed today as a strip: the plan above, what happened below. A block started late sits to the right of its plan, one that ran over has a longer tail, one not started is a dashed outline, one that did not happen is a line, and a line marks now. A sentence says how many are on time, shifted, running, not started or did not happen. Lateness is not punished: what shows is a block with no answer. | Default; built, tested on the emulator |
| ST6 | The NOW card opens any block that can be started or run, not only the ones that need a proof photo, and the block screen scrolls so every answer can be reached. | Default; built, tested on the emulator |
## 53. Starting a block from the block

Added on 20 November 2026 at your request ("I have not been able to start it", then "build the Start button"). The class: any block that stands for a routine can be started from the block, not only from the list on the third page.

| # | Decision | Status |
| --- | --- | --- |
| ST1 | A block that is a routine (the wake and getting ready, the Bible study, the wind-down and the shutdown, a study or code block) has a Start card on its block screen. Tapping it goes to the home screen's third page with that routine already running at the tier the time and last night's sleep allow (RT5), one step at a time. A block that is not a routine (a lecture, a meal) has no Start card. | Default; built, tested on the emulator |
## 52. The alarm's steps are not a dead end

Added on 20 November 2026 from the first real morning on the phone: the alarm said steps could not be counted, because step counting had never been allowed, and the only way out was the wait. The class: every rung of the stop ladder (M10) says honestly why it is not available, can ask for what it needs, and has a way to the next rung.

| # | Decision | Status |
| --- | --- | --- |
| LA2 | On the steps rung a button says the steps are not counting. It starts the short wait (the weakest check, marked), as "the camera is not working" skips the photo. Steps that do reach 40 still stop the alarm as the stronger check. The photo still comes first. | Default; built, tested |
| LA3 | When the phone has a step sensor but Spark is not allowed to use it, the alarm screen says so and offers Allow step counting (it is no longer said that the phone cannot count). Both step sensors are listened to and the larger count is kept, and a new ring counts from nothing. | Default; built; phone check needed |

## 51. Cancelling the app's own football, and a Replace that cannot fail a file

Added on 20 November 2026 at your request ("cancel the three somehow and build such that the replace errors do not happen"). Two classes: a block the app placed itself was the one thing a file could not cancel, and a file that passed in Add mode could fail in Replace mode alone.

| # | Decision | Status |
| --- | --- | --- |
| FB1 | The app's own personal football is a protected block, so it is cancelled the way the others are (OV1): a cancel row titled Personal football with yes in the override column cancels it on that date, found by title wherever the app put it (the time may be anything). Without the override the row is an error that says how. It needs the typed wall and the preview names it (OV2). The cancel stands: the planner counts the cancelled hours as dealt with and does not place that week again. Nothing else the app placed can be cancelled this way. | Default; built, tested |
| RE1 | In Replace mode a block that the file cancels itself (a cancel row or an override row) is left to that row. Replace used to take it first and the row then found nothing, so a file could pass in Add mode and fail in Replace mode. A block the file does not name is still replaced. | Default; built, tested |
| IM13 | The import preview is short at the top and its long lists fold (what it does, what it loosens, warnings), each header saying how many lines are inside. The Apply button sits right under the headline, so it is on the first screen. The wall screen scrolls, the words above its field say how many things loosen and show the first three, and the full list folds below the field, so the field can always be reached (a file with dozens of loosenings used to push it off the screen). | Default; built, tested on the emulator |

## 50. A catch-up for a late night

Added on 19 November 2026 at your request ("find the last cycles"): a sleep reset moves the night later, so the evening has hours the 23:59 cut-off wasted, and the protected free time was the one big block left.

| # | Decision | Status |
| --- | --- | --- |
| LN1 | A catch-up row can say after_midnight: Study until is then the small hours of the next morning (for example 01:25), and the day runs to it, still stopping before the evening routine. Without it the day ends at 23:59 as before. | Default; built, tested |
| LN2 | A catch-up row can say use_free: it gives up the protected free time on its days. It is said in the preview (Uses protected free time on ...) and it needs the typed wall. Without it protected free time is never touched. | Default; built, tested |
| LN3 | A late wake keeps all three meals: a meal may go as late as it needs to keep its 2h30 after the one before it (never past the latest start of 21:30), so a late breakfast moves lunch later and costs the day neither. The first meal after a breakfast keeps its spacing from it even at its planned time (a breakfast at 11:40 does not sit next to a lunch at 12:15). The spacing is a preference: with no place at the full spacing a meal goes where it is closest to kept, and a meal is dropped only when the day has no place for it at all. Breakfast is still dropped only past its own cut-off (13:00) or when the day has no room. | Default; built, tested |
| LN4 | A dated free block (what an earlier replan left when a meal moved out of its slot) is free time again for the next replan, so a catch-up can study in it, unless it is protected free time. | Default; built, tested |

## 49. Overriding a protected block from a file

Added on 19 November 2026 at your request: "include an override function in the CSVs: an override column that allows me to override with my CSV". The class: you sometimes know better than the protection (a study block that hides the wake a gentle sleep reset needs, a project block you are not doing this week). The protection stays the default; you can lift it in your own file, openly.

| # | Decision | Status |
| --- | --- | --- |
| OV1 | A file may add an optional last column named `override`. A cancel row with yes in it cancels a block of type study, study-standard, study-light, code, planning, routine, chore or mytutor-notes that a file otherwise cannot touch (IM6, IX11). A file without the column is read exactly as before, and its earlier fingerprints stay (IM8). | Default; built, tested |
| OV2 | It is never quiet. It always needs the typed wall (it is a loosening), the preview names each block it overrides, and the change economy counts it as a loosening. | Default; built, tested |
| OV3 | A study cycle an override cancels comes back as work due the next working day, so the Buffer still carries it (the same rule as a move, AD2). Other blocks (code, planning, routine, chore) have no cycle to return. | Default; built, tested |
| OV4 | Never overridden, whatever the file says: sleep, reminders, the wake (the alarm is planned from it) and the Bible study (first, always). A meal is overridden only when the plan itself marks it optional (the Student Dinner). An override on an add row, or with any word but yes, is an error that says so. | Default; built, tested |
| OV5 | The same rules are in the format document, the text an AI is given (told to leave the column out unless asked), and the web checker, so every reader agrees. | Default; built, tested |

## 48. The walkthrough

Added on 19 November 2026 at your request: "a really nicely designed walkthrough on first open, and leave it as an option in the app". It reuses the earlier sections (the look follows section 24 and AN1; the words follow section 45).

| # | Decision | Status |
| --- | --- | --- |
| WK1 | The walkthrough opens by itself the first time Spark is opened, and never again by itself once it has been finished or skipped. A test build can turn it off. | Default; built |
| WK2 | Nine pages, one idea each: the idea, the Buffer, the phone, proof, when life happens, wanting less, sleep, your data, and what to do first. Next, Back, and a drawn picture on every page. | Default; built |
| WK3 | It can be skipped from any page except the last, which has Start and Open Setup. | Default; built |
| WK4 | It is always there again: Setup, Show the walkthrough. Finishing a replay returns to Setup. | Default; built |
| WK5 | The numbers on its pages come from the rules' own constants (the WhatsApp window and cooldown, the wall lengths, the free imports and moves), so a change to a rule changes the page. | Default; tested |
| WK6 | Every word follows the voice rules and carries no motivation. It says what Android does not let Spark do, and promises nothing the app does not do. | Default; tested |
| WK7 | It follows the look rules: flat colours from the current theme, no gradient, no looping motion, and none at all when Android's animations are off. It reads in about two minutes. | Default; built, with a word count test |

## 47. Adapting to the situation

Added on 19 November 2026 at your request: "a lack of adaptiveness is what makes me uninstall ... support the adaptivity". The class is a situation the app has no rule for. A menu of situations can never be complete, so Spark must not wait to be told in its own words. It reuses the earlier sections; where it seems to differ from them, this section says so.

| # | Decision | Status |
| --- | --- | --- |
| AD1 | Spark notices that the day and the plan have come apart: movable work that passed without a start, or a block that has not started. It offers one proposal, free to take and easy to send away, and never prompts in a lecture, at training, asleep or in the night. It says nothing until it has seen you start or finish at least one block in the last week, because before that it cannot tell not starting from not telling it. | Default; built |
| AD2 | Whatever happened can be said in a few words with a length and whether it can move. Spark places it now, in free time, or at the next time it fits without giving anything up, today or in the next three days, and shows the Buffer before and after. It is an ordinary modifier, so the preview, the guard rails and the change count are the app's own. | Default; built |
| AD3 | When you are behind the day steps down by itself: Full, Short, Floor, Three things. Nothing steps down while nothing is behind. It steps back up with a margin. A step caps routines at that tier and never raises one; the bottom step turns Lite on and shows the three things that matter. Your own Lite switch always wins, and "back to the full day" ends it for today. | Default; built |
| AD4 | The wall is for wanting less, not for life happening. A proposal that only moves work is free. Work that has to give way around an event comes back as work due the next working day, so it is a move (counted, free within the week's moves) and not a loosening. A meal or the routine is never traded for an event without the wall. | Default; amends IX5 and CE2 for events said through Something came up |
| AD5 | A drift never asks for the wall. If every way of replanning would give something up there is no proposal and no banner. | Default |
| AD6 | A thing shorter than 15 minutes is planned at 15 and says so. A thing that fits nowhere is refused by name with the reason, never silently. | Default |
| AD7 | Unwell is not an event but a lighter day (the Recovery modifier), with the existing pool and wall. This is the one adaptive path that can still need the wall, because a lighter day does give work up for a time. | Default; known limit |
| AD8 | A scenario bank of thirty things life does without asking is run through the loop as a test: each gets a proposal or a named reason, nothing that never moves is moved, nothing overlaps, and at least 24 can be had without giving anything up. | Default |

## 46. Part of an app

Added on 19 November 2026 at your request: "allow podcasts but prevent scrolling". It reuses the earlier sections; where it seems to differ from them, this section says so. It is the general case of "an app that holds two uses at once", of which YouTube is the first.

| # | Decision | Status |
| --- | --- | --- |
| PA1 | An app can be told apart by what it is showing: a surface (a video, Shorts, a feed, search), recognised from the names of its views and the label of the selected tab. A table holds the names per app. | Default; built for YouTube; the names are the app's own and were not read off a real phone, so the log on the Work it out page shows what was recognised |
| PA2 | Scrolling to find something is paid for from a finding credit: 40, a flick costs 1 in search and 2 on a feed or a surface nobody recognised, and every minute a video is in front earns 1 back. A playing video has a burst limit of 12 gestures a minute. Shorts has neither and is closed. | Default numbers; amended the same night: a per-minute limit on feeds and search made a slow search impossible |
| PA3 | A scroll is a gesture, not an event: events within 0.7 seconds are one. Running the credit dry, or going over the burst limit, pauses the app for 3 minutes, and a pause leaves the credit at zero until listening earns more. | Default numbers |
| PA4 | At night, and only then, a covered app is decided by its surface and its scroll trip: Allow, closed, or paused. | Default; built in `decide` |
| PA5 | It opens nothing else: a work block, the getting-ready block, the morning and every other listed app stay as they were, and a night override still works the old way. | Default; tested over random cases |
| PA6 | Both refusals say what happened and when it ends, in the voice of the manual. | Default |
| PA7 | Off by default. Turning it on is a loosening, so it needs the typed wall and is counted in the change economy; turning it off is free. | Default |
| PA8 | The accessibility service reads, for a covered app only and only with the option on, the names of the views and a count of scroll events. Never the text on the screen. | Default; amends the earlier statement that it reads only package names and a browser address bar |
| PA9 | A failure to recognise a surface never traps the phone and never opens a feed: it is the surface with the smallest budget. | Default |
| PA10 | The website of the app in a browser stays blocked at night as before; sleep audio covers the app only. | Default |

## 45. Voice and the manual

Added on 19 November 2026 at your go, from docs/MASTER_PLAN.md and docs/LIFE_MODEL.md. It reuses the earlier sections and where it seems to differ from them, the earlier locked rule wins unless a row below says it amends it.

| # | Decision | Status |
| --- | --- | --- |
| VG1 | Every message follows the voice rules in `MANUAL.md` s3. | Default; built: a test runs every routine step, support route, emergency line, lost-phone step and refusal through the banned-word check |
| VG2 | The banned words are never used. | Default |
| VG3 | The manual is kept in step with the app. | Default; process, with a test that the manual names every routine, every support route and the WhatsApp cap |

The ledger has no Open items. Lines marked Default here are still to be confirmed by you.
