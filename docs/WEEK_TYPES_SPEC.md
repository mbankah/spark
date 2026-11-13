# Modify Schedule: schedule modifiers and presets (design spec, proposed, nothing here is built)

Status: **Proposed.** Redone on 13 November 2026 after your correction. Every line is a "Proposed" row until you confirm it, and nothing here is in the app yet. It extends C1 (planned periods), C2 (library), PW1 to PW3 (assignment and catch-up weeks), H1 to H6 (days off), D1 to D5 (deadlines and cycles), IM1 to IM12 (import), RS1 to RS9 (automatic catch-up) and K1 to K10 (blocking). It never lowers a rule: sleep, meals, the 6 protected hours a week, the £40 food money, the night cutoff and the typed wall stay as they are.

## 0. What I got wrong and how I read you now

My first version treated a "week type" as a label with a few operations. What you mean is simpler and bigger:

> **Modify Schedule** is a tool. You open it, pick a **preset** (Assignment, Catch-up week, Week off, Weekend off, and so on), and the preset **adapts your schedule**: it injects things, transforms things and replaces whole days, whole weeks or parts of a day. Each preset has its own **settings**, some of them a **list of items that each have their own settings** (each assignment has its own hours and priority). Nothing is fixed except what protects you.

How I read the specific points. Tell me if any is wrong (section 15 has the questions):

1. **Assignments are separate from revision**, and they are planned **in anticipation**: work starts well before the due date and is spread back from it, not squeezed in at the end. Revision (for an exam) is its own preset.
2. **Is that already built?** Partly, and not the part you mean. An assignment today is a deadline row or a quick-add task (D2). It counts in the Buffer from the day it is added (P5) and is placed earliest deadline first (RS2). There is no start date, no lead time, no milestones, no hours-per-assignment beyond the heaviness, and no per-assignment priority. The "assignment week" (PW2) only reorders work when deadlines tie. So **this spec adds anticipation**.
3. **"A week off chapters"**: I read this as a **reading or chapters week** (you work through chapters, not lectures), as its own preset. If you meant a plain week off, that is the Week off preset below. Both exist in this spec.
4. **Catch-up week** asks how many **VRNP slots** (study cycles) per day and an **intensity** choice, for example **rapid notes** (shorter, faster notes) against full notes.
5. **Week off** lets you add **important blocks in between** (gym, church, MyTutor, a reminder), and they are **placed** at times or in windows you set. Your sentence for where they are placed was garbled ("placed e.g. gpt s jp;ofsu"), so I have given every way a block can be placed (section 7.5) and you can tell me which you meant.
6. **Weekend off, but still set screen time limits.** A modifier can carry a **screen-time policy** that stays on even when the work is off. This is new: Spark blocks apps in blocks and at night today, and has no daily limits yet (U2 lists usage access as a planned permission).

## 1. How you use it

**Setup, Modify Schedule** shows four lists:

- **Active**: modifiers running now, with their dates, and Edit, End early.
- **Planned**: modifiers set for later.
- **Presets**: Assignment, Revision, Catch-up week, Reading week, Week off, Weekend off, Library, Routine, Recovery, Away, Guests, Placement, Fresh start.
- **Custom**: build one from the verbs in section 8.

Choosing a preset runs the same five steps, every time:

1. **Dates and parts of the day** (section 3).
2. **Settings** for that preset, with defaults filled in. Lists (assignments, pinned blocks, chapters) are added one item at a time, each with its own settings.
3. **Screen time and blocking** for the period (section 9), with the preset's default.
4. **Preview**: what is added, moved, replaced and dropped, each with its reason, the days it changes, and the Buffer before and after with the working (B3, IM5). If it does not fit it says **NEGATIVE** and shows which settings to change.
5. **Apply**, all or nothing (IM11). Anything that loosens a rule asks for the typed wall once.

## 2. What a modifier is made of

Every modifier, whatever the preset, has the same parts. This is what makes the list exhaustive: a preset is just a modifier with some parts filled in.

| Part | What it holds |
| --- | --- |
| **Scope** | Dates, days of the week, and parts of the day it acts on (section 3) |
| **Actions** | A list of operations: inject, transform, replace, pin, remove (section 8) |
| **Settings** | The preset's own settings (sections 5 to 7), each with a default and a range |
| **Items** | Optional lists, each item with its own settings (assignments, exams, chapters, pinned blocks) |
| **Screen-time policy** | Limits that apply for the dates (section 9) |
| **Protected rules** | What it can never touch (sleep, meals, protected free hours, resets, hard alarms, food money, the cutoff) |
| **Class** | TIGHTEN, NEUTRAL or LOOSEN, worked out from its actions; LOOSEN needs the wall |
| **Order** | Its place when several run at once (section 10) |
| **End rule** | End on the date, end when its work is done, or end by hand |

## 3. Scope: dates, days, parts of days

| Setting | Options |
| --- | --- |
| Dates | A start and an end, 1 to 14 days at a time (PW1), or a repeating pattern ("every Saturday for 6 weeks") |
| Days | Any set of weekdays |
| Part of the day | **Morning** (wake to 12:00), **Afternoon** (12:00 to 18:00), **Evening** (18:00 to the night cutoff), **Night** (the cutoff to wake), a **clock window** you type, or **the whole day** |
| Selector inside the scope | By block type (work, lecture, free, chore...), by module, by title, by venue, by block, or "everything that is not protected" |

So "replace every weekday afternoon in this week" or "inject two cycles into every morning" are both one modifier.

## 4. Settings every modifier has (common settings)

| Setting | Meaning | Default |
| --- | --- | --- |
| Name and home label | "Assignment week, until Wed 4 Nov" (PW3) | The preset's name |
| Intensity | Gentle, normal, heavy (scales the hours, never the protected rules) | Normal |
| Work hours a day | A minimum and a maximum, so it is not left to chance | From the template |
| Keep lectures | Keep, move to recordings, or drop for the period (drop is LOOSEN) | Keep |
| Keep football | 3 hours a week, 1 hour, or none (less is LOOSEN) | 3 hours |
| Keep church, gym, MyTutor | Each a yes or no, as pinned blocks | Yes |
| Place | Home, library, or by day (C2) | Home |
| Blocking level | Which reasons block the phone (K3). More is TIGHTEN, less is LOOSEN | As now |
| Reminders | Any number, each with an anchor and an offset (section 7.14) | None |
| Pinned blocks | Important blocks that must stay or be added (section 7.5) | None |
| Free-time floor | The 6 protected hours a week stay as the minimum | 6 hours |
| What to do with work that does not fit | Show NEGATIVE only, move it to the next free days, or ask | Show NEGATIVE |
| Spill-over | What happens to unfinished work when the modifier ends (it stays in the Buffer; nothing is forgiven, RS4, RS7) | Stays |
| End rule | On the date, when done, or by hand | On the date |

## 5. Assignment preset (separate from revision, in anticipation)

**Purpose:** finish assignments well before they are due. It plans **backward from each due date**, so work starts early and is spread, and the due date is a safe end, not the plan.

### 5.1 Per assignment (an item with its own settings)

| Setting | Meaning | Default |
| --- | --- | --- |
| Title and module | By module, like cycles | Required |
| Due date and time | The deadline | Required |
| Size | Hours and minutes of work you expect, or a heaviness (D2) | Heaviness 5 (a first guess until calibrated, B5) |
| Priority | 1 (most) to 5, or a rank among the others | 3 |
| Start rule | **Anticipate** (start N days before), **Start on** a date, or **As soon as there is room** | Anticipate |
| Lead time | N days before the due date when work begins | Size ÷ the daily share, at least 3 days |
| Finish early by | Done N hours before the deadline (a submission buffer) | 3 hours |
| Session length | Minimum and maximum minutes of one sitting | 45 to 120 |
| Sessions a day | At most N for this assignment | 2 |
| Preferred part of the day | Morning, afternoon, evening, any | Any |
| Place | Home, library, or any | Any |
| Milestones | Outline, draft, final, each with a share of the hours and a day | Outline 15%, draft 55%, final 30% |
| Review pass | A short block after the draft | Off |
| Rest before due | Nothing on the evening before | Off |
| Depends on | Another assignment that must come first | None |
| Proof | A photo of the work, or a commit (G1) | As the block's type |
| Reminder to submit | A reminder at a time before it is due | 1 hour before |

### 5.2 For the whole preset

| Setting | Meaning | Default |
| --- | --- | --- |
| Number of assignments | The list above, each added in turn | 1 or more |
| Planning mode | **Anticipate** (spread back from the dates), **Even** (equal each day), **Front-load**, **Crunch** (last, with a warning that it is the risky choice) | Anticipate |
| Daily cap | Most assignment hours in a day | 4 hours |
| Order when assignments compete | By due date, by priority, by size, or round robin | By due date, then priority |
| What happens to the study cycles | Keep, lighten (light cycles only), pause with the cycles coming back as catch-up (never forgiven) | Keep |
| Quiet mode | More blocking while assignment blocks run (TIGHTEN) | Off |
| Re-plan | When a session is missed the remaining work is spread again over the days left, and the preview of the change is shown | On |
| Warn if a start date has passed | Says how many hours must now be done per day | On |

**How anticipation works.** For each assignment the app works backward from "due minus the submission buffer", places the milestones, and fills the **work blocks that exist**, then free time that is allowed to convert (RS3), earliest day first, respecting the daily cap and your session settings. The preview shows each assignment's start date, number of sessions and finish date. If it cannot fit before the due date, it says by how many hours (NEGATIVE) and what to change (lead time, daily cap, intensity, drop a lower priority).

## 6. Revision preset (separate)

| Setting | Meaning | Default |
| --- | --- | --- |
| Exams (items) | Module, date and time, length, hard alarm (D4), topics list | Required |
| Per exam | Hours to spend, priority, topics per day, past papers, a mock exam block, a rest block after | Equal split |
| Method | Spaced rotation of modules, blocked by module, or by weakest topic first | Spaced rotation |
| Slots a day | The number of study cycles a day | 3 |
| Past papers | Number, under time or open | 2 per exam |
| Rest day before an exam | Yes or no | Yes |
| Protected sleep before an exam | Night cutoff stays | Fixed |

Assignments and revision can run together; each keeps its own settings (section 10).

## 7. The other presets

### 7.1 Catch-up week

| Setting | Meaning | Default |
| --- | --- | --- |
| Backlog | All missed work, or chosen modules, or a date range | All |
| **VRNP slots a day** (study cycles) | The floor, and a stretch on a good day. Lecture cycles count toward it | Floor 3, stretch 4 |
| Slot length | Standard 2.5 hours or light 1 hour | Standard |
| **Intensity** | **Full** (video or slides, reading, full notes, problems) or **Rapid notes** (faster video, skim reading, short notes, fewer problems) | Full |
| Rapid notes detail | Video speed (1.25, 1.5, 2), reading minutes, note style (key points only), problems to do | 1.5, 15, key points, 5 |
| Structure of a slot | The minutes of video, read, notes, problems | 15, 25, 60, the rest |
| Module weights | Which modules get slots first (by what is most behind) | Most behind first |
| Module cap a day | At most N slots of one module a day | 2 |
| Recordings for missed lectures | At lecture length, or faster | At length |
| Weekend | Same floor, lower, or none | Same floor |
| If a stretch does not fit | Drop it and say so, or show NEGATIVE | Drop and say so |
| Ends | At the date, or when the backlog is cleared | At the date |

Slots are shown **by module** ("Neural Computation: notes and problems"), never as "VRNP".

### 7.2 Reading or chapters week (my reading of "a week off chapters")

| Setting | Meaning |
| --- | --- |
| Chapters (items) | Title or book, module, pages or minutes, due date |
| Per chapter | Sessions, minutes a session, notes style (none, key points, full), a recall block after, priority |
| Lectures | Kept, or replaced by this reading |
| Daily reading | Minutes a day, and a part of the day |
| Template | Your normal week, or a lighter reading template |

### 7.3 Week off

For a whole week (up to 14 days) off lectures and work blocks, **with important blocks kept or added**.

| Setting | Meaning | Default |
| --- | --- | --- |
| Days | The dates | Required |
| What is paused | Work blocks, lectures, cycles, planning (the list a day off removes) | Work, lectures, planning |
| Pinned blocks (items) | Section 7.5 | Church, gym, MyTutor, football as set |
| Light goals | Optional small things (read 20 minutes a day, a walk) | None |
| Screen-time policy | Section 9 | The preset's default |
| Blocking level | Social apps blocked in no block, in pinned blocks only, or as now | In pinned work blocks only |
| Alarms | No backstop alarm on a day off (G2); optional soft wake | No alarm |
| Sleep and meals | Always kept | Fixed |
| Wall | A week off is a loosening: one request for the whole week, with a string that rises with days used in 30 days (H1, H5). The exact length is a question (section 15) | Proposed |

### 7.4 Weekend off

The same as the week off, for Saturday and Sunday (or any 1 to 3 days). **One use** toward the 30-day tally (H5). **Screen-time limits stay on** and are set in the same flow (section 9): this is the case you described. The default policy for a weekend off is "social and video limited to a daily amount you choose, no limit on calls, maps or banking".

### 7.5 Important blocks (pinned blocks) and how they are placed

A pinned block is an item with its own settings. It stays or is added even when everything else is off.

| Setting | Options |
| --- | --- |
| Title, type, length | Free text, any block type, minutes |
| **Placement** | **Fixed time** (09:30 to 10:15), **Window** (somewhere between 10:00 and 12:00, placed in free or empty time), **After an anchor** (30 minutes after wake, 1 hour before sleep), **Part of the day** (any evening), **Repeat** (every day, weekdays, a list), **Once** (a date) |
| If the time is taken | Move within the window, move to the next day, or tell me and not place |
| Proof | Photo, none (Y1) |
| Blocking | Whether the phone is blocked during it |
| Hard alarm | Yes or no |
| Counts for money | Yes or no (default per type, Y4) |

### 7.6 Library period (built today as C2)
Moves work to the library, adds walks, brings the walk minutes back as catch-up. Settings: hours (asked once), dates, which blocks, walk minutes. It becomes one action inside any other modifier.

### 7.7 Routine
**Build** (move wake and sleep toward a target by a step a day) or **Keep** (hold a routine you have). Settings: target wake and sleep, step in minutes (default 15), direction, anchors to fix, reminders, what to do on a bad night (hold the step), tolerance before drift is shown.

### 7.8 Recovery or illness
Shorter blocks, protected rest, lectures to recordings, an optional day off (the wall, H1 to H6). Settings: days, how much work to keep, what follows (a catch-up week, default off).

### 7.9 Away or travel
Swap days to a travel template, cancel clashing commitments (wall), protected rest, place changes. Settings: dates, place, commitments to keep, offline behaviour.

### 7.10 Guests or social
A "Guests" block over free time, shorter work blocks (shown), work moved earlier (TIGHTEN), a room-prep block. Settings: arrival, leave, prep length, what must still happen (sleep, reminders).

### 7.11 Placement or job
Swap days to a work template, cancel clashing lectures (wall), a smaller cycle quota. Settings: days, hours, travel.

### 7.12 Fresh start
A gentle start: the 14 days of record-only money again (G6), a low quota, reminders, a slow ramp. Blocking is never record-only (U12).

### 7.13 Custom
An empty modifier. You add actions from section 8, scope from section 3, settings from section 4, items and a screen-time policy. Every preset above can be duplicated into a custom one and edited, so the list is exhaustive through modifiability even where a use is not named.

### 7.14 Reminders (inside any modifier)
Text you type (for example "Take melatonin (as prescribed)"), anchor (wake, breakfast, lunch, a clock time, sleep, the night cutoff), offset in minutes, days, length (5 minutes), where it may sit (free or empty time, or trim a work block as LOOSEN), an optional ramp (minutes a day). Spark never advises on medicines: you type the timing.

## 8. The actions (what a modifier can do)

| Verb | Actions | Class |
| --- | --- | --- |
| **INJECT** | Add a block; add a task or deadline; add a study cycle or a quota of cycles; add a milestone; add a reminder; add a protected block; add a recording block | TIGHTEN, or NEUTRAL if it takes free time |
| **TRANSFORM** | Move to a place; resize blocks (longer is TIGHTEN, shorter is LOOSEN); shift times; ramp wake and sleep; change the order of work; change the intensity; change blocking (more is TIGHTEN, less LOOSEN); change proof (stronger or weaker); change the conversion margin; change a screen-time limit | by effect |
| **REPLACE** | Swap one day's template; swap a whole week; swap a part of a day; replace a lecture with a recording; replace a block's content | by content |
| **PIN** | Keep a block or add an important one through a period when everything else is off | NEUTRAL |
| **REMOVE** | Remove work blocks for a period (a day off, wall escalating); cancel a commitment (wall) | LOOSEN |
| **LIMIT** | Set a screen-time policy for the dates | TIGHTEN or LOOSEN |

**Never available:** remove sleep, a meal, the 6 protected free hours, a reset, a hard alarm, or the night cutoff.

## 9. Screen-time policy (new; works on its own, inside any modifier)

| Setting | Meaning | Default |
| --- | --- | --- |
| Applies to | An app group (social, video, games, shopping), chosen apps, or every non-essential app | Your block list |
| Never counted | Calls, messages for emergencies, maps, banking, authenticator, the camera (X1, LA1) | Fixed |
| Daily limit | Minutes a day across the group, or per app | None until you set it |
| Per-session limit | Longest single stretch | None |
| Quiet window | Hours when the group is blocked (for example 23:00 to 09:00) | The night rule |
| Day type | Applies on work days, off days, or both. A weekend off keeps it on | Both |
| At the limit | **Block until tomorrow**, **ask for the wall for 15 more minutes** (the string longer each time, as N4), or **warn only** | Block until tomorrow |
| Counts during work blocks too | Yes (a work block already blocks) | Yes |
| Reset time | When the day's count restarts | 04:00 |
| Class | A limit that is lower is TIGHTEN; raising or removing one is LOOSEN (wall) | |

**How it is measured.** Android's usage access (a permission U2 already plans) tells the app how long each app was in front. It is a count of foreground minutes, not content. If the permission is off or fails, the limit says it cannot count and falls back to the blocking rules that already exist (X7). A limit can be gamed by an app that is not on the list, which is why "every non-essential app" is an option.

## 10. Several modifiers at once and the layer order

From the bottom: **base template**, **replacements** (swapped days and weeks), **modifiers** in the order you list them, **day-level changes** (days off, a single day swap), **imports**, **automation** (catch-up, conversions, resets, football, library walks).

- **MS1** A higher layer acts on what the lower ones left. A protected block can never be removed or moved by any layer.
- **MS2** If two modifiers want the same time, the earlier in the list wins, unless the one it would displace is protected; then the apply is refused with both named.
- **MS3** Up to three modifiers can run at once (Assignment, Catch-up and Library, for example). Their daily caps add up and the preview shows the day's total.
- **MS4** Priority between assignments is set inside the assignment preset; between modifiers, by their order.
- **MS5** Ending one modifier early restores the calendar exactly as the layers below it produce it.

## 11. TIGHTEN, NEUTRAL, LOOSEN and the wall

Each action has a class. A modifier's class is its loosest action. **TIGHTEN and NEUTRAL apply freely**, like an import (IM6). **LOOSEN needs the typed wall once for the whole modifier**, with its escalating string where a day off is involved (H1 to H6). Raising a screen-time limit, shortening work, dropping lectures, trimming a work block for a reminder, and weaker proof are all LOOSEN. The preview names each loosening action and its cost.

## 12. CSV version 2 (so an AI can build a modifier)

Version 1 stays valid. Version 2 adds the kinds `modifier`, `item`, `pin` and `limit`, and two columns, `preset` and `settings`. `settings` is `key=value` pairs separated by `;`. An `item` row belongs to the nearest `modifier` row above it.

```
kind,date,end_date,start,end,title,type,venue,heaviness,study,action,notes,preset,settings
modifier,2026-11-16,2026-11-27,,,Assignments,,,,,,,assignment,"mode=anticipate;daily_cap_h=4;cycles=lighten;quiet=off"
item,2026-12-04,,17:00,,GT assignment,,,5,,,,assignment,"size_h=6;priority=2;lead_days=7;finish_early_h=3;sessions_per_day=2;milestones=outline:15|draft:55|final:30"
item,2026-12-04,,17:00,,RC assignment,,,5,,,,assignment,"size_h=5;priority=1;lead_days=7"
modifier,2026-11-16,2026-11-22,,,Catch-up and routine,,,,,,,catch_up,"slots_floor=3;slots_stretch=4;intensity=rapid;video_speed=1.5;weekend=same_floor"
modifier,2026-11-28,2026-11-29,,,Weekend off,,,,,,,weekend_off,"blocking=pinned_only"
pin,2026-11-28,2026-11-29,18:00,19:30,Church,church,,,,,,weekend_off,"placement=fixed"
limit,2026-11-28,2026-11-29,,,Social limit,,,,,,,weekend_off,"group=social;daily_min=60;at_limit=block;day=both"
```

Rules (Proposed): an unknown preset, a setting that does not exist, or a value out of range is an error with its line and column (IM4); a file that contains a LOOSEN action needs the wall once (IM6); applying twice changes nothing (IM8); the checker page (`web/csv-prompt.html`) and the app check the same rules from one set of shared test files (IM2).

## 13. Data model

`modifier` (preset, dates, parts of the day, settings JSON, class, order, state), `modifier_item` (kind, settings JSON), `pinned_block`, `screen_policy`, `usage_day` (app group, minutes, date), `modifier_log`. The calendar is **computed** from the base template plus the active modifiers each time, as the library period is today, so it always agrees with imports and days off. Removing a modifier removes nothing else.

## 14. Failure behaviour (X7) and tests

- If a modifier cannot be applied, nothing changes (IM11). If the calendar cannot be computed, the base template is shown and the app says so.
- A pinned block that cannot be placed names the day and the reason.
- If usage access is off, screen-time limits say they cannot count; blocking continues as now.
- A ramp never moves a time past a fixed commitment or hard alarm.
- Proposed tests, each named with its row ID: every verb on a sample week; each class with and without the wall; the layer order; a protected block untouched by every action; preview equals result; applying twice is a no-op; ending early restores the calendar; anticipation places an assignment's first session at or before its lead time and its last before due minus the buffer; an assignment that cannot fit says NEGATIVE with the shortfall; priorities and daily caps are respected; a missed session is re-spread once (RS5); catch-up slot counts, intensity and module caps; a week off keeps its pinned blocks and drops the rest; a weekend off keeps its screen-time limit; a limit counts only foreground minutes and never essentials; CSV v2 errors with line and column; the clock change on 25 October.

## 15. Questions for you

1. **"A week off chapters"**: did you mean a reading or chapters week (7.2), a plain week off (7.3), or something else?
2. **Your sentence about where important blocks are placed** was cut off ("placed e.g. gpt s jp;ofsu"). Which of the placements in 7.5 did you mean, or is there another?
3. **Week off wall:** a week off is up to 7 days. Should it be one use with a longer string (for example 75 plus 25 for each extra day), or each day as a use as today (H1), or one use like the weekend (H5)?
4. **Anticipation default:** is "start about 7 days before, spread, finish 3 hours early" right, or do you want to set a start date per assignment every time?
5. **Screen-time limits:** what daily default for social and video on a weekend off (the spec says none until you set one)? And should the limit at its end ask for the wall for 15 more minutes, or just block until tomorrow?
6. **Cycles during an assignment preset:** keep them, lighten them, or pause them (with the missed ones returning as catch-up)?
7. Should two assignments due on the same day be planned together by priority, or one after the other?
