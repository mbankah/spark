# Spark master plan

Written 18 November 2026 at your request, as the last big prompt. **Nothing in this plan is built yet.** It waits for your go-ahead. A one-page version is in `docs/MASTER_PLAN_SUMMARY.md`.

**Part 2 (section 17 onward) was added after your second message and replaces the phases in section 13.** Read the summary first.

It follows `docs/METHOD.md`: state the class, find the missing layer, build the whole system at the lowest missing layer first, test the given case plus others, audit what else reads the change, and only then build the thin specific layers.

---

## 0. What I checked before writing this

These are facts I read from the code and the ledger today, not guesses.

| Finding | Where | Why it matters |
| --- | --- | --- |
| The Modify screen refuses any non-free block that starts before 06:00 or ends after 23:00 ("would be in the night"). The limits are fixed numbers. | `Modifiers.kt`, `checkPlan` (about line 849) | Your plan for tonight (study 00:00 to 02:30) cannot be entered. A late night is a normal part of your life, and sleep is already set by N1 from the wake time, so the fixed numbers are the wrong rule. |
| The CSV import never calls the reflow engine. | `ImportPlan.kt` has no reference to `Reflow`, `buildDay` or `compile` | The import planner works on its own and does not use the reflow engine. So a file's events are placed on the calendar without meals, study and free time being laid out around them the way the same event added in Modify Schedule is. I read the planning code, not the app wiring, so Phase 0 confirms it on a real import. Two doors into the plan, two behaviours. |
| An import can only add or tighten freely, or loosen through the wall. There is no choice to replace, no allowance, no cooldown. | `IM6`, `ImportScreen.kt` | What you asked for: free or cheap imports, weekly freebies, then lock or cooldown, and merge or take over by choice. |
| `android:allowBackup="false"` and no export of your data. | `AndroidManifest.xml` | Uninstalling Spark erases every plan, task and setting, with no way back. One of your six situations on 18 November was exactly this. |
| Release builds are signed with the debug key. | `app/build.gradle.kts` | An update installs over the old app only if the key is the same. Built on another machine, it would force an uninstall and a data loss. |
| Home page 1 is a vertical stack of about 16 conditional cards followed by a row of five text buttons. Now (the thing to do) sits below the Buffer dial and "moved for you". | `HomeScreen.kt` | There is no hierarchy, so the page reads like a dashboard of boxes, not a calm app. I judged this from the code. I have not looked at a fresh screenshot, so Phase 0 includes one. |
| The Change button I added on 18 November sits in that row. | `HomeScreen.kt` line 186 | You do not want it. It goes. |

---

## 1. What Spark is for, in your words

> "The app is to externally discipline myself, especially with my phone ... such is life, where things change ... that's what modify schedule is for. It's for when these events of life happen, you are prepared for that event."

That is two jobs that pull against each other:

1. **Friction for your intentions.** The wall, blocking, proof, the night cutoff. These protect you from the version of you who argues in the moment.
2. **Room for reality.** A guest, a late night, an illness, a changed lecture. These must never feel like a fight with the app, or the app gets deleted.

Every hole you have found sits where those two collide. So the plan has one organising rule, and everything below follows from it.

### The principles (proposed as ledger section 29's preamble)

| # | Principle | In plain words |
| --- | --- | --- |
| P1 | **Friction for intention, freedom for reality.** | Telling Spark what happened (I'm running over, I slept late, I'm out) is always one tap and free. Telling Spark you want less obligation costs something. Which one it is depends on what the change does to your obligations, not on which screen you used. |
| P2 | **Never trapped.** | Every refusal says why and gives you a next step. Emergencies are never gated. There is always one exit. |
| P3 | **Obligations are conserved.** | Work does not vanish when a day changes. It moves, it shrinks only through the wall, and the numbers say so. |
| P4 | **One engine.** | A file, a modifier, a late start and a re-plan all go through the same reflow engine and the same yield policy. |
| P5 | **Recovery is cheap.** | A bad day has a one-tap way back (fresh start, lite mode, undo). Your data survives an update. |
| P6 | **Ideas are free, changes are not.** | A new idea goes to a parking lot and changes nothing until you decide. |
| P7 | **Honest numbers.** | Before and after, shortfall shown, nothing claimed that is not proven. Real-phone items are listed as unproven. |

---

## 2. The classes of problem (the holes, as classes)

Your list of situations is not exhaustive, and it never will be. So I am not adding situations. I am building the layers that make situations of a kind work. Each row below is one class. The numbers in the last column point to the sections that build it.

| Class | The question it answers | What exists | The missing layer | Section |
| --- | --- | --- | --- | --- |
| **C1 Anchors move** | The day's edges (lights-out, wake) change: a late night, a late wake, out late, clocks change. | Wake time (WT1), night cutoff (N1), alarm (Z1), reflow from a start time. | The edges are not data the whole app reads. A fixed 23:00 to 06:00 guard rail contradicts them. | 4 |
| **C2 Reality reports** | Something took longer, started late or got cancelled. | Disturbances: start at, runs over, time taken, several, time freed. | Reachable only through Modify Schedule. No contextual door on the Now card. | 6.3 |
| **C3 Plan sources** | A file, a modifier or a quick add changes the plan. | Modifiers on the engine. A separate CSV path beside it. | One compile path for all sources. Merge or take-over choice. Restore points. | 5 |
| **C4 Change economics** | How much should changing the plan cost? | Walls for loosening; no allowance, no cooldown. | A change policy with classes, freebies, cooldown, trial period and break-glass. | 5.2 |
| **C5 Backlog** | A pile of missed work and a span to clear it. | Catch-up preset places what fits. | Spreading over the span (D5 deadline inside a span), shortfall as a number. | 7 |
| **C6 Place and time zone** | You are somewhere else, or abroad. | Away preset. London zone fixed; attendance checks assume home city. | Zone per span, attendance exemptions while away. | 7.2 |
| **C7 Emergency and out late** | A real need, or you are out. | X1 never-blocked list, essentials row, override wall, web and PC mode. | One emergency door, an "out" state tied to location, a way back from a lock. | 8 |
| **C8 Interface** | Does the home read like a real app? | Pager with two pages, 17 card types, dial and rings. | Modes by moment, a hero, a third page that works things out without changing anything. | 9 |
| **C9 Continuity** | An update, a new phone, a crash. | Crash guard. | Backup file, restore, update-in-place signing, permission checklist. | 10 |
| **C10 Retention** | Why would you keep it? | Nothing specific. | Lite mode, fresh start, shame-free wording, one-tap hole report, a feature freeze. | 10 |
| **C11 Ideas** | New ideas keep arriving. | None. | A parking lot, a weekly review, and the rule that parked ideas change nothing. | 10.4 |
| **C12 Assurance** | How do we know no hole is waiting? | 840 core tests, 505 device tests. | A scenario bank as tests, invariants across random plans, a dead-end audit. | 12 |

---

## 3. Layers and build order

Lowest first. A higher layer is never presented as finished if a lower one is missing.

| Layer | Contains | Phase |
| --- | --- | --- |
| L0 Rules | Ledger sections 29 to 37 and amendments | 1 |
| L1 Model | Anchors per night, change classes, restore points, parking lot, zone per span | 2 |
| L2 Policy | `ChangePolicy`, `AnchorPolicy`, `BacklogPolicy`, `EmergencyPolicy`, existing `YieldPolicy` | 2 |
| L3 Engine | Reflow reads anchors, backlog spreader, zone and DST days, conservation check | 3 |
| L4 Modifiers | Five effects, recipes on top, custom composer | 4 |
| L5 Entry points | Import v2, reality reports on the Now card, What-if page, Modify with the budget meter | 5 |
| L6 Interface | Home modes, hero, third page, dock, polish | 6 |
| L7 Continuity and retention | Backup, restore, signing, lite mode, fresh start, parking lot, hole report | 7 |
| L8 Assurance | Scenario bank, invariants, dead-end audit, device tests, release | 8 |

---

## 4. Anchors: the day's edges are data (class C1)

### 4.1 The problem, generalised

Tonight you want to study 00:00 to 02:30, then sleep. The Modify screen refuses it as "in the night". Earlier you asked to wake at 10:30 and I built a wake-time setting. Both are the same class: **the day's two edges move**, and everything that depends on them must move together.

### 4.2 What reads the edges today (so I know what to change)

| Reader | Currently | Should read |
| --- | --- | --- |
| Night cutoff (N1) | Wake target minus 8h minus 20 min | The night's anchors |
| Alarm (Z1, WT1) | From onset plus 8h, or a chosen wake | The night's anchors |
| Guard rail "would be in the night" | Fixed 06:00 and 23:00 | Between wake and lights-out |
| Meal windows (RF4 and Reflow) | From wake; breakfast cut-off 13:00; last meal 21:30 | Wake and lights-out |
| Reflow day bounds | Wake to a fixed late limit | Wake to lights-out |
| Blocking night rule | Cutoff only | Cutoff from the same anchors |
| Home date and Now | Planned day, slots after midnight shown as +1 | Same, plus lights-out countdown |
| Away | London | Zone of the span |

### 4.3 Build

- Model: `NightAnchors(date, lightsOut, wake)`. Defaults from Setup. A night may be overridden for one night or a span.
- Policy: `AnchorPolicy` enforces the existing locked rule (8 hours of sleep, N1) and refuses an anchor pair that breaks it or hides a commitment (a lecture at 10:00 and a wake at 11:00). The refusal names the commitment and offers the options.
- Engine: Reflow uses wake to lights-out as the day's span, so a 00:00 to 02:30 block is simply a block before lights-out.
- Daylight-saving days: a 23 or 25 hour day loses and duplicates nothing.

Proposed rules: **SA1 to SA6** (section 29 below).

### 4.4 Tonight as the first test of this class

With lights-out at 02:40 and wake at 11:00, N1 gives a cutoff of 02:40, so 02:30 is inside it. (02:45 would be five minutes over, so tonight uses 02:40.) That is why the plan in section 11 works. If tomorrow has anything before about 10:55, then tonight's plan is the wrong plan and the app should say so by name.

---

## 5. Plan sources: one compile path, and the cost of change (classes C3, C4)

### 5.1 One path

A file is not a second planner. It is a written list of the same things Modify Schedule holds: events, deadlines, periods and, new, modifier instances and anchors. It compiles to the same `Instance` list, goes through the same engine and the same yield policy, and is previewed with the same numbers. A quick add, a modifier and a file then behave identically, and a CSV event reflows meals and study like any other.

### 5.2 Two modes, chosen by you in the app (never by the file)

| | **Add to my plan** (merge) | **Replace my plan for these days** (take over) |
| --- | --- | --- |
| What it does | File rows become events and modifiers on top of what is there. | Inside the file's span, everything movable is replaced by the file's items. |
| Stays whatever the file says | Commitments, sleep, hard alarms, meals, football and anything marked keep, protected free time. | The same list. Replace never touches them. |
| Obligations | Placed around the file's events. A study block is never shortened. | Re-placed into whatever the file leaves free. If there is not enough room, the shortfall is shown as a number. |
| Cost class | Add or Move (free or cheap). | Move if every obligation still fits. Loosen if any obligation is dropped. |
| Always | Restore point first, preview with numbers, undo for 24 hours. | Same. |

The file never chooses the mode. A file from an AI cannot take over your plan by itself (this keeps IM5).

### 5.3 The change policy: four classes, decided by effect

| Class | What it does to your obligations | Examples | Cost |
| --- | --- | --- | --- |
| **Reality (R)** | Nothing. It reports what happened. | Running over, started late, slept late, out late, cancelled by the university. | Always free, unlimited. |
| **Add (A)** | Adds work, events or rules. | A guest weekend, an assignment, a new event, a file that only adds. | Always free, unlimited. |
| **Move (M)** | Same work, different times. | Moving a block, a take-over that fits everything. | 5 free a week. The 6th and later need a short typed string (25 characters). |
| **Loosen (L)** | Removes or shortens an obligation. | Cancel a study block, take a day off, drop an assignment, a take-over that drops work. | The existing wall (75 characters). The 3rd in a week needs a longer string (as N4 does). The 4th starts a lockdown on loosening until Monday 00:00. |

The class is computed from the preview (obligation hours before and after, what moved), not from which button you pressed.

### 5.4 Imports specifically (what you asked for)

- **Checking a file is always free.** You can preview as often as you like and nothing changes.
- **Applying:** 2 free a week if the file is Reality, Add or Move. The 3rd to 5th need the short string. The 6th starts a 24 hour cooldown on applying imports, with a countdown. Checking still works during the cooldown.
- **The week** resets Monday 00:00, London time.
- **Trial:** for the first 7 days after install the counts are shown but nothing locks, so you find your own level before the gates matter.
- **Break glass:** once every 30 days, the longest string lifts one lockdown or cooldown. It is logged. This is what stops the system becoming a trap (P2).
- **Undo:** every apply writes a restore point. Undo within 24 hours is free and refunds the count.
- **The meter** ("Moves 2 of 5, Loosen 0 of 2, Imports 1 of 2") shows in Setup, Modify Schedule and the import preview. It is not on the home page, because the home page should not invite tinkering.
- **Loosening the economy itself** (raising a limit) takes effect after 24 hours, so a weak moment cannot loosen it.

The numbers are my defaults. They are marked Default and you can change them, with the 24 hour delay when you change toward looser.

### 5.5 File format v2

Version 1 files keep working. v2 adds three row kinds, so a file can say everything a modifier can:

| Kind | Meaning |
| --- | --- |
| `modifier` | A preset or custom span with settings (`preset`, `start`, `end`, `key=value;key=value`). |
| `anchor` | Lights-out and wake for a night or span. |
| `keep` | A block that must not move or be replaced (football, church). |

The AI prompt that Spark copies is updated, and the shared fixtures in `fixtures/` cover every kind so the web page, the Windows service and the app read the file the same way.

Proposed rules: **IX1 to IX9** and **CE1 to CE10** (sections 31 and 30).

---

## 6. Modifiers: needed, required, exhaustive? (class C2 and the audit you asked for)

### 6.1 The honest answer

Every behaviour of the thirteen presets is required. The list as thirteen peers is not. A list of named presets can never be exhaustive, because life produces situations faster than names. What can be exhaustive is the set of things a modifier can do. I found that every preset is a mix of five effects:

| Effect | Meaning | Presets built from it |
| --- | --- | --- |
| **Demand** | Adds work with a size and a deadline. | Assignment, Revision, Catch-up, Focus (project) |
| **Supply** | Takes time away or caps work. | Period off, Recovery, Away, Lecture-free (lighter) |
| **Event** | A fixed thing in the calendar, with optional prep and after. | Add event, Guests, Away (travel) |
| **Anchor** | Moves wake, lights-out or the day's start. | Late start, Run over, tonight's late night |
| **Policy** | Changes the rules for a span: lectures on or off, chores on or off, screen-time limits, essentials. | Lecture-free, Focus, Period off ("weekend off but still set screen time limits", your 13 November wish) |

### 6.2 Verdict per preset

| Preset | Needed? | Verdict |
| --- | --- | --- |
| Assignment | Yes, it carries real logic (anticipation, per-assignment hours). | Keep as a recipe on Demand. |
| Revision | Yes (exam spacing and cycles). | Keep as a recipe on Demand. Share the code path with Assignment. |
| Catch-up | Yes. | Keep as a recipe on Demand, plus spreading (section 7). |
| Focus | Partly. | Recipe: Policy (lectures paused) plus Demand (project). |
| Lecture-free (lighter) | Partly. | Recipe: Policy (lectures off) plus lighter Demand. It must show hours and notes, as you said. |
| Period off | Yes. | Recipe: Supply (zero) with keeps and optional screen-time Policy. |
| Late start | Yes. | One "Right now" entry with Run over. |
| Run over | Yes. | Same entry. |
| Add event | Yes. | The Event primitive. |
| Cancel | Yes. | The Event primitive (time freed). |
| Recovery | Yes. | Supply cap recipe. |
| Guests | Yes (prep and tidy logic). | Event plus prep plus after recipe. |
| Away | Yes. | Supply cap, travel Event, zone. |
| Revision with exams | Yes. | Stays. |

### 6.3 What I will build

1. **Five effects as the engine's modifier model**, with the thirteen presets kept as named recipes on top, so nothing you know disappears.
2. **A custom composer:** "Something else". You choose the effects (add work, take time away, a fixed thing, move the day's edges, change the rules) and a span, and see numbers before you apply. This is how the list becomes exhaustive: any situation is a combination.
3. **Reality reports on the Now card**, in place of the Change button: the running block offers "Running over" and "I started late"; the evening offers "Going to bed later tonight". They are contextual, free (Reality class), and they are not a general door to edit the plan.
4. **Stored instances migrate** to the new model. Nothing stored is lost.
5. **Situations that must work through the composer, not through new presets** (tested): ill for two days, exam day, travel day, a paid shift, a night out, moving house, wifi down for a day, a hard emotional day.

---

## 7. Backlog and place (classes C5, C6) — the two holes you asked me to build

### 7.1 Backlog spread

**Class:** a pile of overdue or soon-due work with a span to clear it. D5 puts a missed-lecture catch-up due by the end of the next day (D5's deadline part is a Default; the rest is locked and stays). Inside a catch-up span that deadline is the wrong rule.

**Amendment (BK1):** inside a catch-up span, a missed-lecture catch-up is due at the span's end. Outside one, D5 stands. Your "build the holes" is the yes for this.

**Build:**
- Policy: `BacklogPolicy` fixes what counts as backlog (recordings, unfinished study cycles) and what never moves (coursework dates, exam dates, hard alarms).
- Engine: a spreader lays the backlog across the span, oldest first, balanced by day, around commitments and the floor and stretch settings that already exist. It reports the work, the room and the shortfall ("11.5h of work, 9h of room, 2.5h over"), and never overbooks.
- When the span ends with backlog left, the remainder falls back to D5 and Spark offers a re-plan.
- Preview says what each day gets before you confirm.

### 7.2 Away: time zone and place checks

**Class:** you are not where the plan assumes.

- Zone per span, so alarms, meals and lights-out are expressed in local time of the zone. Absolute times stay correct across the border.
- Attendance and place checks are exempt while Away covers the day, with the exemption shown, never silent.
- Honest limit: the first version is proven in tests on the emulator with the zone changed. A real flight is not something I can test.

Proposed rules: **BK1 to BK5** (section 32) and **PL1 to PL4** (section 33).

---

## 8. Emergency and out late (class C7)

### 8.1 What exists

X1 never blocks calls, emergency calls, maps, banking, and the £40 budget. Home has Phone, Messages and Maps. The wall has a 75 character string and 30 minute overrides. G9 plans a web and PC mode for a lost phone.

### 8.2 Gaps

1. The essentials row omits banking, the authenticator and the camera that U6 promised.
2. A blocked screen offers an override but no direct emergency door.
3. Being out late is handled only by the generic night rule.
4. Nothing tells you what to do if the phone is lost, dead or in cooldown.

### 8.3 Build (EM1 to EM5)

| Door | What it is | Cost |
| --- | --- | --- |
| **Emergency** | Emergency call, call anyone, location share, maps and taxi, bank, authenticator, health. Reached from the home, the blocked screen and the lock. | Never gated. No question. Logged quietly. |
| **Out** | "I'm out until 03:00." Blocking and the night rule are suspended until then if the phone is not at the home point. At home, the claim fails and the normal override applies. On return the day reflows (Reality class). | Free. |
| **Urgent message** | Stays as the existing override (N3): your 75 character wall and 30 minutes. I am **not** loosening it on my own. | Unchanged. |
| **Break glass** | Once per 30 days, lifts a lockdown or cooldown. | The longest string. |
| **Lost phone** | A short checklist: web and PC status, how to see the day, how to restore on a new phone from the backup file (section 10). | None. |

---

## 9. The home page and a third screen (class C8)

### 9.1 My judgement of what you have, and what I could not judge

- **Page 1 (Home)** is a long stack of cards of equal weight. About 16 kinds appear, in a fixed order. The most useful thing, Now, is below the Buffer dial. The bottom row is five text boxes. This reads like a dashboard, not like a calm app.
- **Page 2 (Schedule)** you said you really like it. It stays: today open, a carousel of weeks, folded days, a rail with dots.
- **What I cannot judge from code:** spacing, rhythm, the feel of scrolling. Phase 0 includes screenshots of every screen on the emulator so the redesign works from images, not from my reading of code.

### 9.2 The system: home modes

The home page is the same cards, but a **mode** decides which appear and in what order. The mode comes from the moment, not from a setting.

| Mode | When | Hero | Under it |
| --- | --- | --- | --- |
| Morning | Wake to first block | The next step of the routine, the ready photo if due | First meal, today's first block |
| In a block | A work block is running | The timer ring and what the block is | Nothing else. One line of what is next. |
| Between | Transition or short gap | What to do now (walk, reset, meal) | Next |
| Free | Protected or earned free time | How long it lasts | Next, phone state |
| Wind-down | From an hour before lights-out | Lights-out countdown, shutdown photo, melatonin reminder | Tomorrow's first thing |
| Night | After lights-out | Lights-out and wake time only | Nothing |
| Out | You said you are out | Home-by time, Emergency door | Nothing |
| Paused | Lite mode or illness | A short list of what still runs | Resume |

Rules for every mode: one hero, at most four cards above the fold, all other cards one swipe away, text only as label and value, no loose paragraphs. The long list of cards (money, week glance, shopping, reviews) moves to the third page.

### 9.3 The third page: "Work it out"

You asked for a screen that can work something out. I propose a **what-if page**. You set inputs (lights-out, wake, a span, an event, a number of hours off) and it shows the result with the same numbers as any preview: what moves, what is refused, the Buffer before and after, the cost class and your meter. It **changes nothing**. If you like the result, a single Apply button runs it through the change policy. That makes it a safe place for divergent ideas, and the right place for tonight's plan.

### 9.4 What I will change on page 1 and the dock

- Remove the Change button (HM1 amended) and the rest of the row becomes a dock: Phone, Messages, Maps, Camera, Bank. Icons with labels, not text boxes. Setup moves to a small gear in the corner.
- Now is the hero. The Buffer dial becomes a compact line under it.
- Moved-for-you becomes a quiet line.
- Every refusal and every empty state says what happened, why, and the next step (P2).

Proposed rules: **HS1 to HS8** (section 36).

---

## 10. Continuity and retention (classes C9, C10, C11)

### 10.1 What makes people delete an app

From what you wrote and what I have seen: a refusal with no next step; a hole that shows in the first real situation; a bad day that turns into a wall of red; a lost data after an update; too many photos. The plan removes each.

### 10.2 Continuity (CT1 to CT5)

- **Backup file:** a versioned JSON (plan, tasks, settings, import log, restore points, no photos) written every night and on demand to `Downloads/Spark/`. It is a file, so it survives an uninstall, a new phone and a reinstall.
- **Restore:** on first run Spark finds the newest backup and offers it, with counts before you confirm.
- **Update in place:** a persistent release signing key kept in a known place. The build script refuses to produce an APK whose signature differs from the installed one, and says so. Installing over the old app keeps your data and permissions.
- **Permission checklist:** Android resets permissions on uninstall and I cannot prevent that. After a reinstall Spark shows one screen listing every permission with a button to each setting.
- **Android Auto Backup** stays off (privacy). The file backup is the route.

### 10.3 Retention (LT1 to LT3)

- **Lite mode ("rough day"):** for a chosen number of days, home shows three things (sleep, meals, one study block). Blocking stays. Everything else is paused without penalty and returns afterwards. Spark offers it after three missed blocks; you do not have to ask.
- **Fresh start:** re-plan from now, with overdue converted to Buffer work and no red wall. No streaks anywhere.
- **Wording audit:** no blaming words in any message.

### 10.4 Ideas (PK1 and PK2)

- **Parking lot:** Setup, Ideas. Type an idea; it changes nothing. A Sunday review lists them with three answers: drop, keep parked, or turn into a plan change (which then goes through the change policy).
- **This doesn't work:** one button in Setup that writes a report (screen, plan snapshot, last 20 actions, app version) to `Downloads/Spark/reports/`. No network. When a hole appears you tap it and carry on, instead of deciding the whole app is broken. I read the report next session.

### 10.5 Convergence

A plan like this one is itself a divergent act, so it needs a stopper:

- **Not doing** (decided now): cloud sync, any AI inside the app, streaks and points, accountability sharing, more presets (new situations go through the composer), auto backup to the cloud.
- **Freeze:** after Phase 8, 14 days of real use with only reported holes accepted. Ideas go to the parking lot.

---

## 11. Tonight, as the first acceptance scenario

Your wording: study 00:00 to 02:30 before sleeping; before that eat and shower, showering while the pasta cooks; pesto pasta and pork; then the corner shop to collect biscuits, a 7 minute walk away.

**Assumptions I made, because you did not say:** the shop is open when you leave; the 7 minutes is one way; collecting takes about 3 minutes; tomorrow has nothing before about 11:00.

I cannot see your clock, so the plan is anchored to when you leave the house. Leave at 22:55 to start studying at 00:00.

| Time | What | Note |
| --- | --- | --- |
| 22:55 | Leave for the shop | 7 minute walk |
| 23:02 to 23:05 | Collect biscuits | |
| 23:12 | Home. Hob on. Pork in the pan. Water on. | Pork cooked through, no pink |
| 23:24 | Pork out to rest. Pasta in. Timer 11 min. | |
| 23:24 to 23:35 | **Shower** while the pasta cooks | Door open, timer on, hob down so it cannot boil dry |
| 23:35 | Drain, stir the pesto in, plate | |
| 23:40 to 23:58 | Eat | |
| 00:00 to 00:50 | **Study 1** | |
| 00:50 to 01:00 | Break: wash up, water, biscuit | The washing up lives here |
| 01:00 to 01:50 | **Study 2** | |
| 01:50 to 02:00 | Break | |
| 02:00 to 02:30 | **Study 3** | |
| 02:30 to 02:40 | Shutdown photo, teeth, melatonin as you usually place it (the 10 minute floor) | Lights-out 02:40 |
| 11:00 | Wake (8 hours 15 minutes, and N1's cutoff 02:40 is respected) | |

If you leave later than 22:55, study starts later by the same amount, because the chain from leaving to study is 65 minutes. Sleep is protected first: I would still end at 02:30 and accept less study, not shift lights-out. The yield policy already works the same way (free time and study give way before sleep).

What the current APK does: the Modify screen refuses the study blocks as "in the night" (section 0). So tonight, run it from this table. After Phase 3 the same plan is entered in the what-if page, previewed, and applied.

---

## 12. Assurance: how we know no hole is waiting (class C12)

You said you do not want to come back and find a missing error. I cannot promise zero holes. I can make them found early, cheap, and survivable.

1. **Scenario bank as tests (AS1).** The 24 situations from 18 November plus the new ones below are written as fixtures and run as tests. The bank is the hole register. Phase 0 runs it against today's build to get a real baseline.
2. **Invariants over random plans (AS2).** Across thousands of generated days: commitments and sleep never move; obligations are conserved or the loss is reported; every refusal has a reason and a next step; no plan overlaps itself; a 23 and a 25 hour day lose and duplicate nothing.
3. **Dead-end audit (AS3).** Every screen and state has an exit. Every refusal ends with an action.
4. **Device tests (AS4)** for every screen flow added, as before.
5. **Real-phone checklist (AS6).** Anything I cannot prove on an emulator is listed by name for you to check on your phone: alarms across a reboot, the lock screen, notifications, location checks, a flight.
6. **Traceability (AS7).** Every rule has tests whose names start with its ID, as today.

### The scenario bank

| Group | Situation | Expected |
| --- | --- | --- |
| Anchors | Wake at 14:30 | Day starts from now; meals fit the windows; commitments untouched |
| | Tonight's plan (section 11) | Accepted; study 00:00 to 02:30 placed; N1 satisfied |
| | Late night with a 09:30 lecture next day | Refused, naming the lecture, with options |
| | Out until 03:00 | Blocking paused if away from home; reflow on return |
| | Slept through the alarm | Lecture lost, catch-up queued, honest wording |
| | Clocks go back and forward | A 25 and a 23 hour day with no loss or duplication |
| | Phone restarts at 03:00 | Alarms and the meal check come back |
| | Phone dies mid-day | PC and web view; restore on a new phone |
| Reality | Block runs over 40 minutes | The rest slides, commitments stay |
| | A 90 minute call in a study block | Time taken; study re-placed |
| | Lecture cancelled | Free time, labelled |
| | Unannounced guest for 2 hours | Event; meals and study reflow |
| | Ill, cannot work | Supply cap; work conserved in the Buffer |
| | "I can't do this week" | Lite mode offered; nothing lost |
| Sources | Merge a file for next week | Reflowed; numbers shown |
| | Take over three days with football in the file's way | Football stays; obligations re-placed; shortfall shown |
| | The same file twice | Nothing changes (IM8) |
| | Fourth import this week | Short string |
| | Sixth import this week | 24 hour cooldown, check still works |
| | A rule-breaking row | Whole file rejected with line and column |
| | A wrong take-over | Undo within 24 hours restores it |
| | A row at 02:00 to 03:00 | Accepted if the anchors allow, refused with a reason if not |
| Obligations | 12 missed recordings, a Monday to Sunday catch-up | Spread; shortfall as a number |
| | The same backlog, no span | D5 as written |
| | 3 assignments due Friday 17:00 | Spread before the deadline |
| | An exam in 3 days | Revision cycles |
| | Two deadlines on the same day | Priority shown |
| | A guest weekend | Prep before, tidy after |
| Safety | A taxi or an ambulance | One tap, never gated |
| | Urgent message | The existing override |
| | Banking app | Never blocked |
| | Wall typed wrong three times | Forgiveness as today |
| | Lockdown and a real emergency | Emergency door; break glass |
| Continuity | Uninstall and reinstall to update | Backup found; restore offered; permission checklist |
| | A new phone | Restore from the file |
| | A crash during a block | The block resumes |
| | Install over the old APK | Data and permissions kept |
| Interface | Home at 07:00, mid-block, 01:00, wind-down | The right mode, one hero, four cards or fewer |
| | What-if: lights-out 02:40, wake 11:00 | Shows the consequences, changes nothing |
| | An idea typed in the parking lot | Changes nothing |
| Retention | Three days missed | Fresh start offered, no red wall |
| | Five days ill | Paused, no shame wording |
| | Sunday review | Moves, loosens and imports used; ideas listed |

---

## 13. Phases

Each phase ends with its tests green, the traceability table regenerated, and the honest list of what is not proven. An APK is delivered after phases 4, 6 and 8, so you can try it.

| Phase | Layer | What | Exit |
| --- | --- | --- | --- |
| 0 | Ground truth | Screenshots of every screen on the emulator. The scenario bank written as fixtures and run against today's build. A baseline pass and fail list. | The hole register exists with numbers. |
| 1 | Rules | Ledger sections 29 to 37 and amendments written. `QUESTIONS.md` updated. | You can read every proposed rule before code exists. |
| 2 | Model and policy | Anchors, change classes, restore points, parking lot, zone per span; the four policies. | Core tests for each ID; nothing in the app changes yet. |
| 3 | Engine | Reflow reads anchors; backlog spreader; zone and DST days; conservation check. | Tonight's plan plans; the backlog spreads; invariants pass over random days. |
| 4 | Modifiers | Five effects, recipes on top, custom composer, migration, Change button removed. | Every existing preset gives the same plan as before, or a better one with a stated reason. **APK A.** |
| 5 | Entry points | Import v2 (merge, take over, new rows, economy, restore point), reality reports on the Now card, what-if page, the meter. | The scenario bank passes for Sources and Reality. |
| 6 | Interface | Home modes, hero, dock with icons, third page, polish. Screenshots reviewed. | Interface scenarios pass; one hero per mode. **APK B.** |
| 7 | Continuity and retention | Backup, restore, signing guard, permission checklist, lite mode, fresh start, wording audit, parking lot, hole report. | Continuity and Retention scenarios pass. |
| 8 | Assurance | Dead-end audit, invariants at scale, full device suite, real-phone checklist, docs, APK. | All bank scenarios pass; unproven list written. **APK C.** |

Size, honestly: phases 0 and 1 are small, 2 and 3 medium, 4 medium to large, 5 and 6 large, 7 medium, 8 medium. It is bigger than the reflow build. If you want the two holes you named first, phases 2 to 4 can be delivered ahead as a first slice.

---

## 14. Proposed ledger additions

All are Proposed until you say go. "Amends" marks the ones that touch an existing rule.

### Section 29. Anchors (SA)

| # | Decision | Status |
| --- | --- | --- |
| SA1 | Every night has two anchors, lights-out and wake. Setup gives the defaults and any night or span can override them. Everything that depends on the night reads them: the night cutoff, the alarm, meal windows, the end of the planned day, and the guard rail. | Proposed |
| SA2 | The guard rail "in the night" becomes: no non-free block before wake or after lights-out. It replaces the fixed 06:00 and 23:00. | Proposed. Amends MG guard rail |
| SA3 | Lights-out to wake is at least 8 hours (N1 stays Locked). An anchor pair under 8 hours is refused. | Proposed, reuses N1 |
| SA4 | Moving lights-out and wake together keeping 8 hours is a Reality change, free. Moving lights-out later alone is a Loosen and needs the wall. | Proposed |
| SA5 | An anchor that hides a commitment (a lecture before the wake time) is refused naming the commitment, with the choices. | Proposed |
| SA6 | A day with a clock change is 23 or 25 hours long and no block is lost or doubled. | Proposed |

### Section 30. Change economy (CE)

| # | Decision | Status |
| --- | --- | --- |
| CE1 | Changes have four classes: Reality, Add, Move, Loosen. The class comes from the effect on obligations in the preview, not from the screen. | Proposed |
| CE2 | Reality and Add are free and unlimited. | Proposed |
| CE3 | Move: 5 free a week, then a 25 character string. | Proposed, Default |
| CE4 | Loosen: the existing wall; the 3rd in a week longer; the 4th locks loosening until Monday 00:00. | Proposed, Default |
| CE5 | Imports: checking is free; applying has 2 free a week, then the short string, then a 24 hour cooldown on the 6th. | Proposed, Default |
| CE6 | The first 7 days after install count without locking. | Proposed, Default |
| CE7 | Break glass: once per 30 days, the longest string lifts one lockdown or cooldown. Logged. | Proposed |
| CE8 | A restore point is written before every apply. Undo within 24 hours is free and refunds the count. | Proposed |
| CE9 | The meter shows in Setup, Modify Schedule and the import preview, never on the home page. | Proposed |
| CE10 | A change that loosens the economy takes effect after 24 hours. | Proposed |

### Section 31. Plan sources (IX)

| # | Decision | Status |
| --- | --- | --- |
| IX1 | A file compiles to the same instances and goes through the same engine as Modify Schedule. There is one path. | Proposed |
| IX2 | Two modes, chosen in the app: Add to my plan, or Replace my plan for these days. A file cannot choose. | Proposed |
| IX3 | Add: commitments win, study blocks are never shortened, refusals have reasons. | Proposed |
| IX4 | Replace covers the file's span. Sleep, hard alarms, meals, protected free time, the wall and anything marked keep stay. Obligations are re-placed and any shortfall is shown. | Proposed |
| IX5 | Replace is a Move if every obligation fits and a Loosen if any is dropped. A file may remove or shorten a study block only in Replace mode, inside its span, through the wall. | Proposed. **Amends IM6 (Locked): needs your yes** |
| IX6 | Format v2 adds `modifier`, `anchor` and `keep` rows; v1 files still read. | Proposed |
| IX7 | A restore point is written on every apply. | Proposed |
| IX8 | The preview shows placed, moved, refused, shortfall and the Buffer before and after. | Proposed |
| IX9 | All platforms read the same file the same way, from shared fixtures. | Proposed |

### Section 32. Backlog (BK)

| # | Decision | Status |
| --- | --- | --- |
| BK1 | Inside a catch-up span a missed-lecture catch-up is due at the span's end. | Proposed. Amends D5's deadline (a Default); your "build the holes" |
| BK2 | The backlog is spread oldest first and balanced by day, with the floor and stretch of RF5, and the shortfall is a number. | Proposed |
| BK3 | Coursework dates, exam dates and hard alarms never move. | Proposed |
| BK4 | Outside a span D5 is unchanged. | Proposed |
| BK5 | A backlog left at the span's end falls back to D5 and a re-plan is offered. | Proposed |

### Section 33. Place and zone (PL)

| # | Decision | Status |
| --- | --- | --- |
| PL1 | A span can set a time zone. Alarms, meals and lights-out use it. | Proposed |
| PL2 | Attendance and place checks are exempt while Away covers the day, and the exemption is shown. | Proposed |
| PL3 | Alarms are kept in absolute time across a zone change. | Proposed |
| PL4 | The clock change rule SA6 applies in every zone. | Proposed |

### Section 34. Emergency and out (EM)

| # | Decision | Status |
| --- | --- | --- |
| EM1 | An Emergency door (call, emergency call, location share, maps and taxi, bank, authenticator, health) is never gated and is on the home page, the blocked screen and the lock. | Proposed. Extends X1, LA1 |
| EM2 | "I'm out until HH:MM" pauses blocking and the night rule only if the phone is not at the home point; the day reflows on return. | Proposed |
| EM3 | Urgent messaging stays on the existing override (N3). It is not loosened here. | Proposed |
| EM4 | Break glass as CE7. | Proposed |
| EM5 | A lost-phone checklist is one screen on the PC and web view and the first-run screen. | Proposed |

### Section 35. Modifiers (MX)

| # | Decision | Status |
| --- | --- | --- |
| MX1 | A modifier is built from five effects: Demand, Supply, Event, Anchor, Policy. | Proposed |
| MX2 | The thirteen presets stay as named recipes. | Proposed |
| MX3 | A custom composer builds any combination with a preview. | Proposed |
| MX4 | Reality reports are on the Now card and in the evening, and they are free. | Proposed |
| MX5 | Stored instances migrate. Nothing is lost. | Proposed |
| MX6 | Policy can set screen-time limits and the essentials list for a span, so a weekend off can still have limits. | Proposed |

### Section 36. Interface (HS)

| # | Decision | Status |
| --- | --- | --- |
| HS1 | The home page has modes by the moment: Morning, In a block, Between, Free, Wind-down, Night, Out, Paused. | Proposed |
| HS2 | Each mode has one hero and at most four cards above the fold. | Proposed |
| HS3 | Page 2 (Schedule) stays as it is. | Proposed |
| HS4 | Page 3 is a what-if page that changes nothing until Apply. | Proposed |
| HS5 | There is no Change button. | Proposed. **Amends HM1: your instruction** |
| HS6 | The bottom row is a dock with icons: Phone, Messages, Maps, Camera, Bank. Setup is a small gear. | Proposed |
| HS7 | Text is label and value only. No loose paragraphs. | Proposed |
| HS8 | Every refusal and empty state says what happened, why, and the next step. | Proposed |

### Section 37. Continuity, retention, ideas and assurance (CT, LT, PK, AS)

| # | Decision | Status |
| --- | --- | --- |
| CT1 | A versioned backup file is written nightly and on demand to `Downloads/Spark/`. | Proposed |
| CT2 | First run finds the newest backup and offers it with counts. | Proposed |
| CT3 | A persistent release key signs every build; the build refuses a different signature. | Proposed |
| CT4 | After a reinstall one screen lists every permission with a button to each. | Proposed |
| CT5 | Android Auto Backup stays off. | Proposed |
| LT1 | Lite mode shows three things for a chosen number of days; Spark offers it after three missed blocks. | Proposed |
| LT2 | Fresh start re-plans from now with no red wall and no streaks. | Proposed |
| LT3 | No blaming wording anywhere. | Proposed |
| PK1 | A parking lot for ideas that change nothing, with a Sunday review. | Proposed |
| PK2 | "This doesn't work" writes a local report to `Downloads/Spark/reports/`. | Proposed |
| AS1 to AS7 | Scenario bank as tests, invariants over random plans, dead-end audit, device tests, real-phone checklist, traceability. | Proposed |

### Needing your explicit yes (because they touch Locked or approved rules)

1. **IX5 amends IM6** (a file can remove or shorten study only in Replace mode, inside its span, through the wall).
2. **SA2 replaces** the fixed 06:00 to 23:00 guard rail with the anchors.
3. **HS5 amends HM1** (no Change button). You already said so.
4. **BK1 amends D5's deadline** inside a catch-up span. You already said so.
5. **The economy numbers** (CE3 to CE6) are defaults you can change.

---

## 15. Risks and what I cannot prove

| Risk | What I do |
| --- | --- |
| Android background limits kill alarms or the service on some phones. | Same as before: permission checks, the loud "blocking is off" notice, a real-phone checklist. I cannot prove it on an emulator. |
| Location checks indoors. | The wider radius and the retake already exist. Out mode uses the home point only. |
| A real flight across zones. | Tested by changing the zone in tests. Not provable here. |
| The economy numbers are wrong for you. | A 7 day trial with counts only; change them with the 24 hour delay. |
| Scope growth. | The Not doing list and the freeze in section 10.5. |
| Me missing something. | The hole report button, the bank, the invariants. I will say plainly what each covers. |

---

## 16. What I need from you

See section 22, which replaces this after Part 2.

---

# Part 2: the life, in detail (added the same day)

You then wrote that the plan must reflect the complexity of your life: walking, transit, getting out of bed, the small steps, how the Bible reading adapts, using the phone's full capability as a kind of self-CBT, a University of Birmingham student's niggles, being at home, sleep, the photo you can skip, systems that fit together and cannot be bypassed, a manual with how I address you, and a long document of real situations. Part 1 above handled the *changes to the schedule*. It was not deep enough on *the day itself*, and it said little about how the systems fit together. Part 2 fixes that. It is five documents and the sections below.

| Document | What it is |
| --- | --- |
| `docs/LIFE_MODEL.md` | The spine: blocks, routines, steps; the fact bus and proof ladder; context and places; the door pool; precedence; the adaptation period; scaffold levels |
| `docs/DAY_ATLAS.md` | Every small step of a day, with cue, time, proof, gate, reward and fallback; the Bible routine and how it adapts; the late-night day |
| `docs/COACH_AND_PHONE.md` | The coach, the self-CBT methods, the phone's full capability, rewards, the sleep programme, the photo question |
| `docs/COHERENCE_AND_BYPASS.md` | Where systems can disagree; precedence; 40 collisions; the doors; 28 bypass routes; dead ends; what Phase 0 verifies |
| `docs/UOB_AND_HOME.md` | Your real university year with dates, the university's support routes, day types, home and the winter vacation |
| `docs/SITUATIONS.md` | 249 situations, each with what Spark does |
| `docs/MANUAL.md` | The manual, the voice guide, every option with its default and cost |

## 17. The revised phases and slices

The phases in section 13 are replaced by these. Lowest layer first; each phase ends with its tests green, the traceability table regenerated and an honest list of what is not proven.

| Phase | Layer | What | Exit |
| --- | --- | --- | --- |
| 0 | Ground truth | Screenshots of every screen. The scenario bank (249 situations, 40 collisions, 28 bypass routes) written as fixtures and run against today's build. The duplicated decisions traced in the code. The bypass routes probed on the emulator, with a list for your phone. | A measured hole register |
| 1 | Rules | Ledger sections 29 to 45 and the amendments. `QUESTIONS.md`. | You can read every rule before code |
| 2 | The spine | Fact bus, context record and places, door pool, anchors, change classes, restore points, parking lot | Core tests for each ID; no visible change |
| 3 | Engine and routines | Anchors in the reflow; backlog spread; zone and clock changes; routines, steps, tiers, parallel steps; measured times | Tonight's plan plans; the backlog spreads; invariants pass |
| 4 | Modifiers and the gate | Five effects, recipes, the composer; one gate; the Change button removed | **APK A**, the first slice |
| 5 | Home and places | The family-home place, the Home study day, the Travel day, the winter arc recipe, the gradual sleep shift, the leaving and return checklists | **APK B**, aimed at before Mon 14 Dec |
| 6 | Entry points | Import v2 and the economy, reality reports on the Now card, the what-if page | Sources and Reality scenarios pass |
| 7 | The coach and the phone | The coach, haptics, NFC and sensors, rewards, the sleep programme, mood check, home modes, the third page, dock and polish | **APK C** |
| 8 | Continuity and retention | Backup and restore, signing guard, permission checklist, lite mode, fresh start, the parking lot review, "this doesn't work" | Continuity and Retention scenarios pass |
| 9 | Assurance | Dead-end audit, invariants at scale, the full device suite, the real-phone checklist, docs, the APK | **APK D**; all bank scenarios pass |

**The calendar.** Winter vacation starts Mon 14 December, 26 days from today. That is the first time Spark has to work for you with no lecture timetable. Slice 1 (phases 0 to 4) and Slice 2 (phase 5) are therefore ordered ahead of the rest, so that Spark has the model for home before the date. I cannot promise a calendar date because I cannot see how long each session runs; if it looks at risk I will tell you, and the first thing cut would be polish in phase 7, not the rules in phase 2.

**Size, honestly.** This is much larger than anything built so far: a new data layer, a rebuilt gate, routines and steps for every part of the day, a coach, sensors, a home screen redesign and a backup system. If it is built in one go the risk is exactly the one you describe: small things that do not fit. That is why it is in layers, why every layer has tests that name the rule, and why you get a working APK after phases 4, 5, 7 and 9.

## 18. The new classes

Added to the table in section 2.

| Class | The question | The missing layer | Where |
| --- | --- | --- | --- |
| **C13 The day itself** | What is the next small thing? | Routines, steps and tiers | `DAY_ATLAS.md` |
| **C14 Proof you cannot skip** | How do I know it happened, and what if I ignore the prompt? | The fact bus, the proof ladder, open and unproven steps, NFC and sensors | `LIFE_MODEL.md` s2.2 |
| **C15 Where and in what state** | Which routine fits here and now? | The context record, places, day types proposed automatically | `LIFE_MODEL.md` s2.3, `UOB_AND_HOME.md` |
| **C16 Many doors** | How do relaxations combine? | The door pool and precedence | `COHERENCE_AND_BYPASS.md` |
| **C17 Encouragement** | Does it feel good, and does the habit become mine? | The coach, rewards, scaffold levels, the adaptation period | `COACH_AND_PHONE.md` |
| **C18 Sleep** | How do I get to sleep, and stay on a schedule? | The sleep programme: docking, wind-down, awake list, tomorrow's fun, gradual shift | `COACH_AND_PHONE.md` s5 |
| **C19 The university and home** | What does term and vacation actually ask? | Real dates, support routes, day types, the home place | `UOB_AND_HOME.md` |
| **C20 Voice** | How does it talk to me? | The voice guide and the manual | `MANUAL.md` |

## 19. Proposed ledger additions, Part 2

All Proposed until you say go. Prefixes are chosen so they do not collide with any existing ledger prefix.

### Section 38. Routines (RT)

| # | Decision | Status |
| --- | --- | --- |
| RT1 | A day is blocks, a block holds routines, a routine holds steps. | Proposed |
| RT2 | Every routine has three tiers (Full, Short, Floor). The floor is never dropped. | Proposed |
| RT3 | A step has a cue, an instruction, a time, a proof tier, a gate, a reward and a fallback. | Proposed |
| RT4 | Steps may run in parallel only where a routine declares it. | Proposed |
| RT5 | The yield policy picks the tier from time, sleep and context. | Proposed |
| RT6 | The Bible routine is first, always, with a floor of 10 minutes; breakfast is cut before it (your plan). | Proposed, from your seed |
| RT7 | A new or changed routine has an observe week, then a suggest week, then it enforces. | Proposed |
| RT8 | Prompts fade by scaffold level: Guided, Cued, Tracked, Yours; they return when a habit slips. | Proposed |

### Section 39. Facts and proof (FB)

| # | Decision | Status |
| --- | --- | --- |
| FB1 | Every signal becomes a fact on one bus. Everything that asks "was it done" reads the bus. | Proposed |
| FB2 | The proof ladder has five tiers: none, tap, sensor, NFC, photo. A step names the tier it needs. | Proposed |
| FB3 | A step is done if a fact of the needed tier is inside its window. | Proposed |
| FB4 | An ignored required proof leaves the step open, and its gate stays shut; when the window closes it is unproven: no reward, unconfirmed time, no blame. | Proposed |
| FB5 | A technical failure closes the step as not your fault (M10 stays). | Proposed |
| FB6 | NFC taps and sensor facts are accepted where the step names them. A tag read away from its place is flagged and accepted at lower confidence only. | Proposed |
| FB7 | Where two signals disagree, one strong or two weak are needed. | Proposed |

### Section 40. Context and places (CX)

| # | Decision | Status |
| --- | --- | --- |
| CX1 | One context record: place, day type, state, body, mood, phase. | Proposed |
| CX2 | Places are named, with their own tags, Wi-Fi and routine variants: the flat, the family home, campus, library, church, pitch, shop. | Proposed |
| CX3 | The day type is proposed from the calendar and place and confirmed once; you never assemble a day. | Proposed |
| CX4 | You have two homes, and "home" in the location rules is whichever you are at. | Proposed |

### Section 41. The gate and the door pool (GT, DP)

| # | Decision | Status |
| --- | --- | --- |
| GT1 | One gate decides what the phone allows, reading the day model, the context, the facts and the doors. No other part decides. | Proposed |
| DP1 | A door is any feature that relaxes a rule. They are listed in `COHERENCE_AND_BYPASS.md` s4. | Proposed |
| DP2 | All doors draw from one pool in two units, obligation minutes removed and gate minutes lifted, over a rolling 7 days. | Proposed, Default numbers |
| DP3 | Verifiable doors cost less than unverifiable ones. | Proposed |
| DP4 | At the cap, doors lock except the Emergency door, a verified ill day and break glass. Sleep protection is never relaxed by any door. | Proposed |
| DP5 | Precedence: safety, sleep, hard commitments, routine floors, study, free time, rewards. | Proposed |
| DP6 | A property test opens doors in every combination and order and shows the cap holds. | Proposed |

### Section 42. The coach and rewards (CO)

| # | Decision | Status |
| --- | --- | --- |
| CO1 | One voice, one prompt at a time. | Proposed |
| CO2 | Four prompt levels (haptic, spoken, notification, gate), never more, never repeated endlessly. | Proposed |
| CO3 | A fixed haptic vocabulary. | Proposed |
| CO4 | Every prompt is one sentence with the verb first. | Proposed |
| CO5 | An optional mood tap twice a day, used only for the review and to notice a run of low days. | Proposed |
| CO6 | A run of low days shows two actions: Lite mode, and a people list (GP, the university's Mental Health and Wellbeing Service, Student Disability Service, NHS 111, Samaritans 116 123, Shout 85258). Spark never diagnoses. | Proposed |
| CO7 | A reward menu of five of your own, offered at random on a proven block or routine. | Proposed |
| CO8 | No streaks, points, badges or red for a missed day. | Proposed |
| CO9 | Today's wins: three true facts at wind-down. | Proposed |
| CO10 | Three identity sentences you write, quoted back sparingly with a fact. | Proposed |
| CO11 | A tone setting: Calm, Plain, Light. | Proposed |

### Section 43. The sleep programme (SL)

| # | Decision | Status |
| --- | --- | --- |
| SL1 | A 60 minute wind-down anchored to lights-out: tidy, soft screen, brain dump, tomorrow's fun, prayer and stretches, warm milk, dock, shutdown photo. | Proposed |
| SL2 | Docking is a tag tap at the charger across the room; the night rule still applies if it is not done. | Proposed |
| SL3 | An Awake tap offers a list: up, low light, no phone, back at 20 minutes. | Proposed |
| SL4 | "Tomorrow's fun" puts a night-time urge into a real slot tomorrow. | Proposed |
| SL5 | A gradual shift moves wake earlier by 15 minutes every 2 to 3 days, never more than 30 in a day, with a morning light step. | Proposed |
| SL6 | Your own reminders for medication and the like, logged as taken or not taken, with no advice from Spark. | Proposed |
| SL7 | One 20 minute nap window before 15:00 after a night under 4 hours. | Proposed, Default |
| SL8 | Spark does not use sleep restriction. | Proposed |

### Section 44. Home and the university (HO)

| # | Decision | Status |
| --- | --- | --- |
| HO1 | Home mode: no lectures, revision and project blocks, family meals as fixed events, a portable seat tag, a family-time window. | Proposed |
| HO2 | The family home is a place with its own home point, Wi-Fi and tags. | Proposed |
| HO3 | The winter vacation arc (14 Dec to 8 Jan) is a recommended recipe: land, holiday, ramp, final prep. | Proposed |
| HO4 | A support card with the university's real routes: coursework extension (before the deadline), extenuating circumstances (after), Reasonable Adjustment Plan. | Proposed |
| HO5 | A Travel day and a leaving checklist; a return routine and a sleep shift before going back. | Proposed |
| HO6 | The calendar carries the university's real term dates. | Proposed |

### Section 45. Voice and manual (VG)

| # | Decision | Status |
| --- | --- | --- |
| VG1 | Every message follows the voice rules in `MANUAL.md` s3. | Proposed |
| VG2 | The banned words are never used. | Proposed |
| VG3 | The manual is kept in step with the app. | Proposed |

## 20. What Part 2 changes in Part 1

| Part 1 said | Part 2 says |
| --- | --- |
| The Change button is replaced by reality reports on the Now card | Same, and the reports are facts on the bus |
| The CE economy has per-class costs | It sits inside one door pool (DP2) so doors cannot be combined |
| Home modes by the moment | The modes read the context record (CX1) and the routines' steps |
| Continuity and retention in phase 7 | In phase 8; Home is earlier (phase 5) because of 14 December |
| A scenario bank of about 45 | 249 situations, 40 collisions and 28 bypass routes |

## 21. The amendments and decisions needing your yes (updated)

1. **IX5 amends IM6** (a file may remove or shorten study only in Replace mode, inside its span, through the wall).
2. **SA2 replaces** the fixed 06:00 to 23:00 guard rail with the anchors.
3. **DP2 and DP4**: one pool across all doors, with the Emergency door, a verified ill day and break glass exempt.
4. **WhatsApp cap: decided by you on 19 Nov.** WhatsApp opens for 10 minutes at a time, then a cooldown, and ending the cooldown early is only through the wall. It applies at all times, not only after the cutoff. Defaults I chose, which you can veto: the cooldown is 30 minutes; an active call is never cut off; Messages (SMS) and phone calls are not capped; the cap lives in the gate, so it draws from the door pool when you use the wall to end a cooldown; loosening the cap takes 24 hours (CE10). This amends your seed's Tier 0.
5. **NFC tags: decided, later.** Photos stay the proof for now. The design keeps the proof tiers, so a tag drops in later without rework. You buy the stickers when you want them.
6. **Greyscale: decided, yes.** It needs `adb shell pm grant app.efa android.permission.WRITE_SECURE_SETTINGS` on your own phone, once. Only your emulator is connected to this PC today, so I have not run it; the first-run screen and the release notes will show the command, and I will run it for you the first time your phone is connected with USB debugging on.
7. The numbers in the economy, the pool, the nap offer and the gradual shift are Defaults you can change.

You already said yes to: removing the Change button; relaxing D5's deadline inside a catch-up span.

## 22. What I need from you

**Go**, or **go first slice** (phases 0 to 5: the audit, the rules, the spine, the engine and routines, the modifiers and gate, the two holes you named, the Change button removed, tonight's anchors, and Home). Everything above is defaulted and nothing blocks. Items 4 to 6 in section 21 are the only choices I would like your view on, and a default stands for each if you do not answer.

**What I will not do:** I will not build any of this until you say go, I will not push without "Push", and I will not tell you a thing works on a real phone until you have confirmed it on your phone.
