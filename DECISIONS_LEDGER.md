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
| D5 | A lecture missed because of bad sleep stays missed. The catch-up (the recording, at the lecture's length) is added to the Buffer and is due by the end of the next day. The study cycle is still due. | Catch-up deadline: Default |
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
| M7 | The list is fixed. Editing it is allowed but only through the effort wall: a fresh 75-character random string, hand-typed, no pasting or autofill, and a mistake restarts it. | Locked |
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
| N3 | An override is a 75-character random string: fresh each time, hand-typed, no pasting or autofill, a mistake restarts it. Each override buys 30 minutes. | Locked |
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
| Z1 | The alarm time is worked out from when you actually fall asleep (onset plus 8 hours). It does not ring early because an alarm would stop you sleeping again. | Locked |
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
| U8 | Notifications from blocked apps are held while a block is on and shown in a digest afterwards, never deleted. Notifications from apps that are not blocked come through normally. | Locked; details Default |
| U9 | Notification badges and animations never appear in the launcher. App icons and a grid layout are off by default and can be turned on in settings. A change takes effect from the next day, so it is a decision made in advance (the delay is a Default). | Locked |
| U10 | Design: brutalist and highly minimalist. Monochrome, thick borders, one monospaced type family, large type, a hard grid, no gradients, shadows or animation, and one warning colour reserved for a negative Buffer. The web app uses the same system. | Locked; specifics Default |
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

The ledger has no Open items. Lines marked Default here are still to be confirmed by you.

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

The ledger has no Open items. Lines marked Default here are still to be confirmed by you.
