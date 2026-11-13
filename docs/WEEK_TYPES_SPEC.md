# Week types and modifiers: a design spec (proposed, nothing here is built yet)

Status: **Proposed.** Written 13 November 2026 at your request. Every line is a "Proposed" row until you confirm it. Nothing in this document is in the app, so none of it is tested. It extends C1 (planned periods), C2 (library), PW1 to PW3 (assignment and catch-up weeks), H1 to H6 (days off), IM1 to IM12 (import) and RS1 to RS9 (automatic catch-up). It never lowers a rule: sleep, meals, the 6 protected hours a week (G3), the £40 food money (X1), the night cutoff (N1 to N7) and the typed wall (M7, H1) all stay as they are.

## 1. The idea in one paragraph

Your week is a **base template** (today, Week B). A **week type** is a set of **operations** laid over that template for a range of dates: it can **inject** things (blocks, tasks, reminders), **transform** things (move them, resize them, change how they are blocked or proven) and **replace** things (swap a day or the whole template). Every week type, every operation and every block has its own **properties** (what it is) and **settings** (what you can change). Layers stack in a fixed order, the app shows the result and its cost to the Buffer before anything is applied, and anything that loosens a rule needs the wall.

## 2. What exists today, so the gaps are plain

| Today | What it really is | Verb |
| --- | --- | --- |
| Week B | The base template | none (the template) |
| Imported events | Single dated blocks, cancels | inject, remove (wall) |
| Imported deadlines | Tasks with a due time and a heaviness | inject |
| Assignment week, catch-up week (PW1, PW2) | A label that only orders work when deadlines tie. It moves nothing | order only |
| Library period (C2) | Moves work blocks to the library, adds walks | transform |
| Day off (C1, H1) | Removes a day's work, brings lectures back as work | remove + inject work |
| Automatic catch-up, conversions, resets, football | The app's own injects | inject |

**The gap:** a week type cannot yet change how many study cycles a day there are, move the wake time a little each day, swap a day's template, or carry its own settings. The CSV can only say "this is a catch-up week" and add dated rows.

## 3. Vocabulary

- **Template**: a full week of blocks. Week B is the base. You can have more (Revision week template, Holiday template).
- **Week type**: a named bundle of operations with settings, applied to a date range of 1 to 14 days (PW1). It may be applied to part of a week.
- **Operation**: one change. Verbs: INJECT, TRANSFORM, REPLACE, REMOVE (REMOVE only of things that are allowed to go, section 8).
- **Selector**: which things an operation touches (a weekday, a block type, a module, a time window, a title).
- **Setting**: a value you can change (a number, a choice, a time, a list). Every setting has a default, a range and a reason.
- **Preset**: a saved set of settings under a name.
- **Anchor**: a fixed point other things hang from: wake, first lecture, lunch, shutdown, the night cutoff.

## 4. The layer stack (what wins)

From the bottom, later layers act on the result of earlier ones:

1. **Base template** (or its replacement, layer 2).
2. **Template swap** (REPLACE a whole day or week).
3. **Week types**, in the order you list them (each shows its position; default order: Routine, Library, Catch-up, Assignment, Revision, Recovery).
4. **Day-level changes**: days off, a single day's swap.
5. **Imports** (dated rows from a file).
6. **Automation**: catch-up, conversions, resets, football, the library walks.

Rules that never change:
- **WT1** A layer can only act on what the layers below left. A removed block cannot be edited by a higher layer.
- **WT2** The protected set (sleep, meals, the 6 hours a week, resets, hard alarms, the safety floor) cannot be removed, shortened or moved by any layer, except by the existing walls (a day off keeps sleep and meals; a meal can move within its window, section 8).
- **WT3** If two operations want the same time, the higher layer wins only if the lower one is not protected. Otherwise the apply is refused with both named, never half done (IM11).
- **WT4** Every operation is **TIGHTEN** (more work, less free time), **NEUTRAL** (moves things, same total) or **LOOSEN** (less work or free time becomes rest). TIGHTEN and NEUTRAL apply freely, as an import does (IM6). LOOSEN needs the typed wall once for the whole week type.
- **WT5** Nothing is applied before the **preview**: what is added, moved and removed, each with its reason, and the Buffer before and after with the working (B3, IM5). A preview that makes the Buffer negative says NEGATIVE.

## 5. The objects and what you can change on each

"Fixed" means it is part of the rule and cannot be changed. "Setting" means it can. "Wall" means changing it loosens and needs the wall.

### 5.1 Block (every block in any template)

| Property | Fixed or setting | Notes |
| --- | --- | --- |
| Title, notes | Setting | Plain words (W5, W7). A module name is used for study cycles. |
| Type | Setting within a family | Work (study, code, planning), fixed commitment (lecture, tutorial, mytutor, training, match, gym, church), routine, meal, travel, chore, free, sleep. Changing between families can LOOSEN. |
| Start, end, length | Setting | Within the block's own minimum and maximum (a cycle: 60 to 150 minutes). |
| Venue | Setting | Home, library, a named room. A new room asks for its location (L6). |
| Proof | Setting | Photo, commit, attendance photo, none. Lowering proof is LOOSEN (wall). |
| Blocking level | Setting | Which reasons block the phone (K3). Lowering is LOOSEN. |
| Hard alarm | Setting | D4 list. |
| Convertible to work | Setting | Free slots only; the protected 6 hours are fixed as never convertible. |
| Cuttable by an import | Fixed by type | Free and work blocks only (IM6). |
| Removed by a day off | Fixed by type | The list in the days off code. |
| Weight in the money ratio | Fixed by type (Y4) | Lecture 4, standard cycle 6, light 3, other work 5. |
| Earns phone access (R2) | Fixed by type | 10 minutes an hour of work. |
| Reset after it (W4) | Fixed | Always for work blocks. |

### 5.2 Study cycle (a "cycle" for one module)

You asked that cycles be noted by module and not by method. A cycle has an internal structure (video or slides, reading, notes, problems) kept as a property, never as the title.

| Property | Setting | Default |
| --- | --- | --- |
| Module | Setting | One of your modules |
| Size | Setting | Standard 2.5 hours, light 1 hour (D1) |
| Structure and minutes of each part | Setting | Video or slides 15, read 25, notes 60, problems the rest |
| Title shown | Fixed | "Module: notes and problems" |
| Due | Setting | End of day (D1), or a chosen time |
| Counts toward the day's quota | Fixed | Yes |

### 5.3 Cycle quota (a rule that makes cycles exist)

| Setting | Meaning | Default |
| --- | --- | --- |
| Floor per day | The lowest number of cycles on a working day, counting the ones lectures already make | 3 |
| Stretch per day | The extra you would like on a good day | 4 |
| Days | Which weekdays it applies to | All |
| Module weights | Which modules get the extra cycles first | Equal, or by what is behind |
| Source of the extras | Deadlines in the Buffer (as a file does today) or placed blocks | Deadlines |
| Weekend rule | Same, lower, or none | Same floor, no stretch |
| When the Buffer would go negative | Warn only, drop the stretch, or ask | Drop the stretch and say so |

### 5.4 Reminder (a medicine, a habit, a check)

A reminder is a small block with a name you type. Spark gives **no medical advice**: it never chooses a dose or says when you should take something.

| Property | Setting |
| --- | --- |
| Text | Your words, for example "Take modafinil (as prescribed)" |
| Anchor | Wake, breakfast, lunch, a clock time, shutdown, the night cutoff |
| Offset | Minutes before or after the anchor, for example 5 before wake |
| Days | Every day, weekdays, a list |
| Length | 5 minutes by default |
| Where it may sit | Free time or empty time only; or "may trim a work block" (LOOSEN, wall) |
| Ramp | Optional: moves a number of minutes a day toward a target (for a routine you are building) |
| Needs proof | No (reminders are not proven) |
| If it cannot be placed | Tell you which day and why; never place it on top of something |

### 5.5 Template and day template

A template is seven day templates. A day template is an ordered list of blocks with anchors. Settings: name, which days it covers, wake anchor, shutdown anchor, whether it is used on days off, and the blocks. A day template can be applied to one day (a swap) or to a whole range.

### 5.6 Week type

Common properties of every week type:

| Property | Setting | Notes |
| --- | --- | --- |
| Name and label on home | Setting | "Catch-up week, until Fri 20 Nov" (PW3) |
| Date range | Setting | 1 to 14 days (PW1) |
| Operations | Setting | List, each with its own settings |
| Position in the stack | Setting | Section 4 |
| Presets | Setting | Save, load, duplicate |
| Preview | Fixed | Always, before applying |
| LOOSEN operations | Fixed rule | Need the wall (WT4) |
| End | Setting | Ends on its date; can be ended early, which is itself NEUTRAL |
| What happens to unfinished work | Fixed | It stays in the Buffer. Nothing is forgiven (RS4, RS7) |

## 6. The operation catalogue (exhaustive list of verbs and targets)

Each row is one operation a week type may contain. "Class" is TIGHTEN, NEUTRAL or LOOSEN.

**INJECT**

| # | Operation | Parameters | Class |
| --- | --- | --- | --- |
| I1 | Add a block | Date or weekday, time, length, type, title, venue | TIGHTEN if work, NEUTRAL if it takes free time |
| I2 | Add a task or deadline | Title, due, heaviness (D2) | TIGHTEN |
| I3 | Add a cycle quota | Floor, stretch, days, module weights | TIGHTEN |
| I4 | Add a reminder | Section 5.4 | NEUTRAL (LOOSEN if it trims work) |
| I5 | Add a catch-up block of recordings | Module, length, when | TIGHTEN |
| I6 | Add a recurring routine anchor | A fixed block every day at a ramped time | NEUTRAL |
| I7 | Add a protected block | Rest, a walk, meal prep | NEUTRAL |

**TRANSFORM**

| # | Operation | Parameters | Class |
| --- | --- | --- | --- |
| T1 | Move work to a place | Selector, venue (library), walk minutes | NEUTRAL (the walks cost time, shown) |
| T2 | Resize work blocks | Selector, scale or minutes, min, max | TIGHTEN if longer, LOOSEN if shorter |
| T3 | Shift times | Selector, minutes, optional ramp per day | NEUTRAL |
| T4 | Ramp wake and shutdown | Target time, minutes per day (default 15), direction | NEUTRAL |
| T5 | Change priority order | Deadline-first, backlog-first, module order | NEUTRAL (PW2 widened) |
| T6 | Change blocking | Add reasons, never remove (removing is LOOSEN) | TIGHTEN or LOOSEN |
| T7 | Change proof | Stronger is TIGHTEN, weaker is LOOSEN | either |
| T8 | Change conversion margin | Margin percent and floor (F3) | TIGHTEN if lower margin |
| T9 | Change reward rules | Phone access minutes per hour (R2) | LOOSEN if more |
| T10 | Change what counts for money | Which blocks and weights | LOOSEN if it raises payout |

**REPLACE**

| # | Operation | Parameters | Class |
| --- | --- | --- | --- |
| R1 | Swap a day template | Date, template | by content |
| R2 | Swap the whole week template | Template | by content |
| R3 | Replace a block's content | Selector, new block | by content |
| R4 | Replace lectures with recordings | Module, length (a lecture you cannot attend) | NEUTRAL |

**REMOVE** (only of things allowed to go)

| # | Operation | Parameters | Class |
| --- | --- | --- | --- |
| X1 | Remove work blocks for a day | Date (a day off, H1) | LOOSEN (wall, escalating) |
| X2 | Cancel a commitment | Selector (IM6) | LOOSEN (wall) |
| X3 | Drop a stretch cycle | Selector | LOOSEN |

Never available, because they are protected: remove sleep, remove a meal, remove the protected free hours, remove a reset, remove a hard alarm.

## 7. The week types (each is a preset of operations with settings)

Every week type below has the common properties of 5.6. The table shows its **purpose**, its **default operations** and the **settings it adds**. Any operation can be removed from a type and any type can be duplicated and edited, so the list is exhaustive through modifiability even where a use is not named.

### 7.1 Standard week
Purpose: Week B as it is. No operations. Settings: none.

### 7.2 Assignment week
Purpose: finish named assignments, at home or in the library.
- Operations: I2 for each assignment (title, due, heaviness), T5 deadline-first, T1 optional library, T2 optional longer work blocks, T8 optional lower margin.
- Settings: assignments (list), deep-work hours a day (default: existing work blocks only), place (home, library, mixed by day), keep lectures (yes), keep football (3 hours, can drop to 1, LOOSEN), free-time floor (never below the 6 protected hours), quiet mode (extra blocking reasons, TIGHTEN), stop adding new tasks until it ends (off).
- Home label: "Assignment week, until Wed 4 Nov".

### 7.3 Catch-up week
Purpose: clear a backlog (missed lectures, cycles, tasks).
- Operations: T5 backlog-first, I3 cycle quota, I5 recordings for missed lectures, T8 optional lower margin.
- Settings: backlog selection (all, by module, by date), cycles floor and stretch per day (3 and 4), module weights, weekend rule, recordings length, when the backlog counts as cleared (all done, or a number), what to do with a stretch that does not fit (drop it and say so).
- Home label: "Catch-up week, until Sun 22 Nov".

### 7.4 Routine-building week ("getting into routine")
Purpose: move sleep and wake toward a target by a small step a day and fix anchors.
- Operations: T4 wake and shutdown ramp, I6 anchor blocks, I4 reminders, T6 optional stronger night blocking (TIGHTEN).
- Settings: target wake and target sleep time, minutes per day (default 15, range 5 to 30), direction, first anchor to fix (wake, breakfast, shutdown), light and walk block after wake (optional), caffeine cut-off reminder (optional, your text), reminder list, what to do on a bad night (hold the step for a day, never advance).
- Safety: the app does not advise on medicines or sleep disorders (section 5.4). The ramp never moves a hard alarm or a fixed commitment.

### 7.5 Routine-keeping week ("establishing a routine")
Purpose: hold a routine already reached.
- Operations: I6 anchors at fixed times, I4 reminders, T3 no drift (the Z3 pull-back stays on).
- Settings: anchors, tolerance in minutes before a drift is shown (default 15), streak display off (P6: no future-based streaks).

### 7.6 Library week (C2, built today as a library period)
Operations T1. Settings: opening hours (asked once), days, which work blocks move, walk minutes. It becomes one operation inside any other type.

### 7.7 Revision or exam week
- Operations: I3 cycle quota (higher floor), I2 exam deadlines (hard alarm), T2 longer blocks, T6 stronger blocking, optional R4.
- Settings: exam dates and times (hard alarms), modules and weights, past papers per day, rest block after each exam (protected).

### 7.8 Recovery or illness week
- Operations: X1 day off (wall escalating, H1 to H6), T2 shorter blocks, I7 protected rest, lectures to recordings (R4).
- Settings: days, how much work to keep, catch-up after (a catch-up week begins on its own, default off). The automatic day off after 3 hours of sleep (H4) still applies.

### 7.9 Reading or holiday week
- Operations: R2 swap to a holiday template, I2 optional reading goals, T3 optional shifted times.
- Settings: template, what stays (sleep, meals, church, football), blocking on or off for social apps (off is LOOSEN, wall).

### 7.10 Travel or away week
- Operations: R1 for travel days, X2 cancel affected commitments (wall), I7 protected rest, T1 place changes.
- Settings: dates, place, commitments to keep, what is possible offline (the phone keeps the plan, the PC link pauses).

### 7.11 Guests or social week
- Operations: I1 a block of "Guests" over free time (uses free time first), T2 shorter work blocks (LOOSEN, shown), I2 tasks moved earlier (TIGHTEN, tasks come before the guests).
- Settings: arrival and leave times, room prep block (length), which work to move before, what must still happen (sleep, meds reminders).

### 7.12 Placement or job week
- Operations: R1 swap days to a work template, X2 cancel lectures if clashing (wall), I3 smaller quota.
- Settings: days, hours, travel.

### 7.13 Fresh start week (the first week of a new set of rules)
- Operations: the app's 14 day record-only rollout (G6) applied again for money, I3 low quota, I4 reminders, T4 gentle ramp.
- Settings: length, what is record-only (money, penalties, but never blocking, U12).

## 8. Exhaustiveness: what can and cannot be changed

| Thing | Existence | Modifiability |
| --- | --- | --- |
| Sleep, meals, resets, the 6 protected hours, hard alarms, the night cutoff, the £40 food money | Always exist | **Fixed.** A meal may move within a window you set (TIGHTEN or NEUTRAL only); a day off keeps them (C5) |
| Lectures, tutorials | Exist from the timetable | Cancel or move by file (IM6, wall); replace with recordings (R4) |
| Work and study blocks | Exist from the template | Resize, move, rename, change place and proof (changes that loosen need the wall) |
| Study cycles | Exist for each lecture (D1) | Quota, size, module, structure, due |
| Reminders | None by default | Any number, with anchors and ramps |
| Week types | Standard always exists; the others you add | Every setting in section 7; duplicate, edit, remove |
| Templates | Week B | Add, swap, edit |
| The wall and its escalation | Always exists | **Fixed** (M7, H1 to H6); only its length by use |
| Typed proof | Always exists | Weaker proof is LOOSEN (wall) |

## 9. Settings model

- **Three levels:** global default, per week type, per instance of a week type (this week). An instance inherits its type, which inherits the global default. A day can override one setting for itself.
- **Every setting** has a name, a plain reason, a default, a range, and a class (TIGHTEN, NEUTRAL, LOOSEN) so the app knows whether the wall is needed.
- **Presets:** save an instance's settings as a preset; apply, duplicate, rename, delete.
- **Reset:** every setting can be put back to its default, one at a time or all.
- **History:** each applied week type is logged with its settings, so you can see what you ran.

## 10. How you install a week type (the screens)

1. **Plan a week type**: pick the type, the dates (1 to 14 days), or "start from a preset".
2. **Settings**: only the settings of that type, with defaults filled in and their reasons.
3. **Preview**: what is added, moved, removed, and what each costs; the Buffer before and after with the working; NEGATIVE if it does not fit; which operations need the wall.
4. **Apply**: all or nothing (IM11). Loosening operations ask for the typed wall once.
5. **Running**: home shows the type and when it ends. A list of active and planned types lets you edit, end early or remove one (removing a type that had loosened needs the wall, like IM12).

## 11. CSV and AI: how a week type travels (proposal for format version 2)

Version 1 files stay valid and unchanged. A version 2 file adds one new kind of row and two columns, so an AI can describe a week type and all its settings.

New columns (after `notes`): `weektype`, `settings`.
New kind: `weektype`, one row per week type applied, with `date`, `end_date`, `type` (assignment, catch_up, routine_build, routine_keep, library, revision, recovery, holiday, travel, guests, placement, fresh_start, custom), `title`, and `settings` as `key=value` pairs separated by `;` (a value with a semicolon is quoted).

Example, the week you asked for, written in version 2:

```
kind,date,end_date,start,end,title,type,venue,heaviness,study,action,notes,weektype,settings
weektype,2026-11-16,2026-11-22,,,Catch-up and routine week,catch_up,,,,,,,"cycles_floor=3;cycles_stretch=4;weekend=same_floor;modules=Randomness and Computation|Numerical Methods and NLA|Neural Computation|Game Theory;source=deadlines;stretch_if_negative=drop"
weektype,2026-11-16,2026-11-22,,,Routine,routine_build,,,,,,,"wake=07:30;sleep=23:10;step_min=15;reminders=modafinil@wake-5|melatonin@sleep-60"
```

Rules (all Proposed): a week type row that names a setting that does not exist is an error with line and column (IM4); a setting outside its range is an error; a type or operation that would loosen needs the wall once for the file (IM6); applying twice changes nothing (IM8); an unknown future setting is refused, never ignored.

This week's file today (version 1) does the same work with rows the app already reads: a catch-up period, deadline rows for the extra cycles, and reminder events. See `week/2026-11-16-catchup-routine-week.csv`.

## 12. Data model (for when it is built)

Tables: `week_template`, `day_template`, `template_block`, `week_type` (the type and its defaults), `week_instance` (type, dates, settings JSON, state), `operation` (verb, class, selector, params), `reminder`, `preset`, `week_log`. A week instance is read, not copied: the calendar is computed from base template plus layers each time, like the library period is today, so it always agrees with imports and days off.

## 13. Failure behaviour (X7) and tests

- If a layer cannot be applied the whole apply is refused and nothing changes (IM11). If the calendar cannot be computed the app shows the base template and says so.
- A reminder that cannot be placed names the day and the reason and is not placed.
- A ramp never moves a time past a fixed commitment or hard alarm; it stops and says why.
- Proposed tests: each verb on a sample week; every class (TIGHTEN, NEUTRAL, LOOSEN) with and without the wall; the layer order (a lower layer cannot undo a higher one's protected rule); a protected block cannot be touched by any operation; preview equals result; applying twice is a no-op; removing an instance restores the calendar exactly; ramps over the clock change on 25 October; a cycle quota with and without room; the CSV version 2 errors with line and column.

## 14. Open questions

1. Should an unfinished stretch cycle carry over to next week, or lapse (default here: lapse, the floor still stands)?
2. Should routine-building be allowed to change the night cutoff (default: no; the cutoff follows the wake target by the existing rule, so it moves with the ramp)?
3. How should "at least 3 or 4" cycles be counted for a day with a long lecture schedule (default: lecture cycles count toward the floor)?
4. Reminder timing: you type the anchor and offset; Spark will never suggest one.
5. Do you want presets for each week type shipped, or only the ones you save?
6. Should week types be allowed to overlap in time (default: yes, stacked in the order shown, up to three at once)?
