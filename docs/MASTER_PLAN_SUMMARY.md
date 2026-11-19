# Spark master plan: the summary

One page. The full plan is `docs/MASTER_PLAN.md`. Nothing is built yet. It waits for your go.

## The idea in one line

**Friction for your intentions, freedom for reality.** Telling Spark what happened is free and one tap. Wanting less obligation costs something. Nothing is ever a dead end.

## What I found that matters most

1. The Modify screen refuses any block before 06:00 or after 23:00, so tonight's study (00:00 to 02:30) cannot be entered. The day's edges (lights-out, wake) are not data the app reads.
2. The CSV import is a separate planner. It does not use the engine that reflows meals and study, and it has no merge or replace choice, no allowance, no cooldown.
3. The app has backup switched off and no export, and release builds use the debug key. Uninstalling erases everything.
4. Home page 1 is about 16 cards of equal weight. Now sits under the Buffer dial. The bottom row is five text boxes.
5. Your 13 presets are all needed in what they do. As a list they can never be exhaustive. They are all mixes of five effects.

## What gets built, in order (lowest layer first)

| Phase | What | You get |
| --- | --- | --- |
| 0 | Screenshots and a baseline of the scenario bank | A hole register with numbers |
| 1 | The rules, written (ledger sections 29 to 37) | You read every rule before code |
| 2 | Anchors, change classes, restore points, policies | The model under everything |
| 3 | Engine: anchors, backlog spread, zone and clock changes | Tonight's plan works; the backlog spreads |
| 4 | Five effects, recipes, custom composer, **Change button removed** | **APK A** |
| 5 | Import v2 (merge or replace, new row kinds, economy, undo), reality reports on the Now card, what-if page | Free, cheap, then lock |
| 6 | Home modes, hero, dock with icons, third page | **APK B** |
| 7 | Backup and restore, signing guard, lite mode, fresh start, parking lot, "this doesn't work" | Survivable failures |
| 8 | Dead-end audit, invariants, device tests, real-phone checklist | **APK C** |

## The import economy you asked for

- Checking a file is always free.
- Reality (I'm late), Add (a guest weekend) and Move (same work, new times) are free or nearly: 2 imports and 5 moves a week free.
- Loosening (dropping work) uses the wall: 2 a week, a longer string for the 3rd, a lockdown on the 4th until Monday.
- A 6th import in a week starts a 24 hour cooldown. Checking still works.
- First 7 days: counted, never locked.
- Break glass once a month, so it never becomes a trap. Every apply has an undo for 24 hours.
- Merge ("Add to my plan") or take over ("Replace my plan for these days"), chosen in the app, never by the file. Football, sleep, meals, hard alarms and protected free time stay either way.

## Home and the third screen

Modes by the moment (morning, in a block, between, free, wind-down, night, out, paused), one hero each, four cards or fewer. Page 2 stays. **Page 3 is "Work it out"**: set a lights-out, wake time, event or span and see the numbers, with nothing changed until Apply. No Change button. Reality reports ("running over", "started late", "going to bed later tonight") sit on the Now card.

## Keeping you from uninstalling

A nightly backup file you can restore from. Updates that install over the old app. A one-tap "this doesn't work" report. Lite mode and a fresh start for a bad stretch, no streaks, no red wall. A parking lot for new ideas that changes nothing. After Phase 8, a 14 day freeze with only reported holes accepted.

## Needs your explicit yes

1. IM6 amended: a file may remove or shorten study blocks only in Replace mode, inside its span, through the wall.
2. The fixed 06:00 to 23:00 guard rail is replaced by the anchors.
3. The economy numbers are defaults; you can change them (with a 24 hour delay when loosening).

You already said yes to removing the Change button and to relaxing D5 inside a catch-up span.

## What I cannot prove

Alarms across a reboot, the lock screen, notifications, location indoors and a real flight need your phone. They are listed in Phase 8 by name.

## Part 2: the life in detail (added after your second message)

You said the plan must reflect the complexity of your life and that the systems must fit together and not be bypassable. Part 2 is seven documents:

| Document | In one line |
| --- | --- |
| `LIFE_MODEL.md` | One spine: blocks hold routines hold steps; one fact bus with a proof ladder; one context record; one door pool; one precedence order |
| `DAY_ATLAS.md` | Every small step of a day (getting out of bed, shower, Bible with its three tiers and how it adapts, walking, lectures, study, meals, chores, wind-down), each with cue, proof, gate, reward, fallback |
| `COACH_AND_PHONE.md` | The coach, self-CBT methods, the phone's full capability (NFC tags, sensors, haptics, speech), rewards, the sleep programme, why the photo cannot be skipped |
| `COHERENCE_AND_BYPASS.md` | Where systems can disagree, 40 collisions with a winner, 15 doors, 28 bypass routes, dead ends |
| `UOB_AND_HOME.md` | Your real term dates, the university's support routes, day types, home and the winter vacation (Mon 14 Dec to Fri 8 Jan) |
| `SITUATIONS.md` | 249 situations with what Spark does (Reddit was blocked, so these are not verbatim posts) |
| `MANUAL.md` | How Spark speaks to you, every option with its default and cost, the first 14 days, the NFC tags |

**The core ideas**

- **A skipped photo is not a free pass.** The step stays open, its gate stays shut, no reward arrives, and NFC taps and sensors make the proof a one-second action.
- **One pool for every door.** A day off, lite mode, "I'm out", a late night and a replace-import all draw from one weekly pool, so no combination of them can add up to more than you meant.
- **Observe, suggest, enforce.** Spark measures you for a week, proposes fixes for a week, and only then enforces; prompts fade as a habit holds and come back if it slips.
- **A floor under every routine.** There is never a zero day. The Bible is first, always, with a 10 minute floor.
- **Home before the vacation.** Phases are reordered so Home arrives before Mon 14 December.

## Revised phases

0 ground truth, 1 rules, 2 the spine, 3 engine and routines, 4 modifiers and gate (**APK A**), 5 home and places (**APK B**, aimed before 14 Dec), 6 entry points, 7 coach and phone (**APK C**), 8 continuity and retention, 9 assurance (**APK D**).

## Go

Say **go** for everything, or **go first slice** (phases 0 to 5: the audit, the rules, the spine, routines, modifiers and gate, the two holes you named, the Change button removed, tonight's anchors, and Home).

**Decided on 19 Nov:** WhatsApp is capped at 10 minutes at a time, then a cooldown (default 30 minutes), and only the wall ends a cooldown early. Photos stay the proof for now; NFC stickers come later and drop in without rework. Greyscale is a yes: one `adb` command on your phone, which I will run when it is connected (only the emulator is connected today).
