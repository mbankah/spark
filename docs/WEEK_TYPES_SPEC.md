# Modify Schedule: schedule modifiers and presets (design spec, proposed, nothing here is built)

Status: **Partly built (14 to 16 November 2026).** The spec was written first and a first slice was then built from it. **Built:** the Modify Schedule screen and the presets Assignment (planned in anticipation, with priorities, planning modes, sequential, interleaved and Auto, adaptive lightening, milestones), Catch-up week (slots by module, rapid notes), Period off (start and end dates, pinned blocks), Focus week (with the hybrid toggle) and Lecture-free lighter period; Recommended on a button press; the guard-rail checker; the Admin library (the catalogue, load sets, on and off, the daily cap, Lighter, and trimming work with the wall); the essentials with the 5-minute, 3-app add window. **Not built:** Revision, Routine, Recovery, Away, Guests, Placement, Fresh start and Custom; the screen-time policy (it needs usage access); editing a job's frequency or window; adding your own admin job; triggered admin jobs; re-planning after a missed session; version 2 of the CSV. The rest of this document is the design, and where it goes beyond the built part it is still **Proposed**. See `QUESTIONS.md` Q-G113 to Q-G120 for the choices made while building. It extends C1 (planned periods), C2 (library), PW1 to PW3 (assignment and catch-up weeks), H1 to H6 (days off), D1 to D5 (deadlines and cycles), IM1 to IM12 (import), RS1 to RS9 (automatic catch-up) and K1 to K10 (blocking). It never lowers a rule: sleep, meals, the 6 protected hours a week, the £40 food money, the night cutoff and the typed wall stay as they are.

## 0. What this is, and what changed from the last version

**Modify Schedule** is a tool. You open it, pick a **preset**, and the preset **adapts your schedule**: it injects things, transforms things and replaces whole days, whole weeks or parts of a day. Each preset has its own **settings**, and some have **lists of items that each have their own settings** (each assignment has its own hours and priority).

Your answers, applied:

| You said | What is in this version |
| --- | --- |
| A week off in general | One preset, **Period off** (7.2). The reading or chapters week is gone. |
| Period off with a start and end date | **Period off** takes any start and end date (1 to 14 days). A weekend is just a two-day period. There is no separate Weekend off. |
| Start 2 weeks before, finish a day before | Assignment default: **lead time 14 days, finish 24 hours before the due time** (5.1). |
| Default screen time 4 hours total | Period off default: **a daily limit of 4 hours across every non-essential app** (9). |
| Lighten the cycles, but adapt | Assignment default **adaptive lightening**: cycles lighten more as the assignment load rises, and re-adapt when it changes (5.3). |
| One after the other or interleaved by parameters | A **planning order** setting with a rule that chooses (5.4). |
| Ask for 15 more minutes should be an option | A per-policy setting **"At the limit"** with three choices: **Block until tomorrow**, **Ask for 15 more minutes (typed wall)** and **Warn only**. Ask for 15 is always selectable (9). |
| Lecture cycles depend on whether Focus is hybridised with a period off (the Christmas holidays case); make it a toggle | **Hybrid with period off** toggle on the Focus week (7.3). Off: lecture cycles keep running, lightened. On: no lectures, no lecture cycles, project all day, rest free. |
| Adding an app to the essentials needs the wall, with a 5-minute window to add apps | **Add-essentials window** (10, MG1): one wall opens a 5-minute window in which any number can be added. |
| Cap the add-essentials window at 3 | **At most 3 apps** can be added in one 5-minute window (10, MG3a). |
| In a project week, chores and the daily routine still happen (dishes, tidying the room, setting up for the day, Bible reading) | A **Daily routine** setting kept in **every** modifier, Focus week and Period off included (4, MG18). |
| A week for no lectures, a lighter period; the list must be situationally exhaustive | A new preset, **Lecture-free lighter period** (7.4), and a **Situation guide** that maps real situations to presets and combinations (7.12). |
| Every possible admin job, even in the normal schedule (dishes, bathroom, room, bed, shops, bins and so on) | An **Admin library** (4A): a catalogue of every kind of upkeep job, each with its own frequency, length, window and settings, kept in every modifier. |
| Recommended runs after a button press | Recommended **only runs when you press the button**, never on its own (11, MR6). |
| Don't break the app with placement errors, e.g. blocking never includes essentials | A **guard-rail checker** that runs on every preview and refuses to apply a plan that breaks an invariant (section 10). |
| A Recommended button that sets parameters | **Recommended** on every preset (section 11). |
| A focus week for a project only, integrated | **Focus week** (7.3), built on the Assignment engine as an exclusive mode, and combinable with Period off. |

"See last" for the wall on a period off was not clear to me. I have used the rule in 7.2 and put it in the questions (section 14).

## 1. How you use it

**Setup, Modify Schedule** shows: **Active** (running now, with Edit and End early), **Planned**, **Presets** and **Custom**.

Every preset runs the same steps:

1. **Dates and parts of the day** (section 3).
2. **Recommended** (optional): one button that fills in every setting from your own data, with a reason beside each value (section 11). You can accept all, change any, or reset to the recommendation.
3. **Settings** for that preset. Lists (assignments, pinned blocks, items) are added one at a time, each with its own settings.
4. **Screen time and blocking** for the period (section 9).
5. **Preview**: what is added, moved, replaced and dropped with its reason, the days it changes, the Buffer before and after with the working. The **guard-rail checker** (section 10) runs here. If a rule is broken the preview says which and Apply is off.
6. **Apply**, all or nothing (IM11). Anything that loosens a rule asks for the typed wall once.

## 2. What a modifier is made of

| Part | What it holds |
| --- | --- |
| **Scope** | Dates, days of the week, and parts of the day (section 3) |
| **Actions** | Inject, transform, replace, pin, limit, remove (section 8) |
| **Settings** | The preset's own, each with a default, a range and a reason |
| **Items** | Lists, each item with its own settings (assignments, exams, pinned blocks) |
| **Screen-time policy** | Limits for the dates (section 9) |
| **Recommended** | A rule that proposes every setting (section 11) |
| **Protected rules** | What it can never touch: sleep, meals, the protected free hours, resets, hard alarms, food money, the cutoff, the essentials |
| **Class** | TIGHTEN, NEUTRAL or LOOSEN, worked out from its actions |
| **Order** | Its place when several run at once (section 12) |
| **End rule** | On the date, when its work is done, or by hand |

## 3. Scope: dates, days, parts of days

| Setting | Options |
| --- | --- |
| Dates | A start and an end, 1 to 14 days at a time (PW1), or a repeating pattern |
| Days | Any set of weekdays |
| Part of the day | **Morning** (wake to 12:00), **Afternoon** (12:00 to 18:00), **Evening** (18:00 to the night cutoff), **Night** (cutoff to wake), a **clock window**, or **the whole day** |
| Selector inside the scope | Block type, module, title, venue, a named block, or "everything that is not protected" |

## 4. Settings every modifier has

| Setting | Meaning | Default |
| --- | --- | --- |
| Name and home label | "Assignments, until Fri 4 Dec" (PW3) | The preset's name |
| Intensity | Gentle, normal, heavy (scales hours, never protected rules) | Normal |
| Work hours a day | A minimum and a maximum | From the template |
| Keep lectures | Keep, recordings, or drop (drop is LOOSEN) | Keep |
| Keep football | 3 hours, 1 hour or none (less is LOOSEN) | 3 hours |
| Keep church, gym, MyTutor | Each yes or no, as pinned blocks | Yes |
| **Daily routine and admin** | **Kept in every modifier.** Every job switched on in the **Admin library** (4A), and the routine: Bible reading, getting ready and setting up for the day, breakfast and bag, washing dishes and the 10-minute tidy, tidying the room (the room reset), cleaning and laundry chores, evening prayer, stretches, and the 10-minute resets. Each can be Keep (default), Lighter (shorter, never below its minimum), or Drop (LOOSEN, wall) | Keep |
| Place | Home, library, or by day (C2) | Home |
| Blocking level | Which reasons block the phone (K3). More is TIGHTEN, less LOOSEN | As now |
| Reminders | Any number, each with an anchor and offset (7.10) | None |
| Pinned blocks | Important blocks (7.2) | None |
| Free-time floor | At least the 6 protected hours a week | 6 hours |
| Work that does not fit | Show NEGATIVE, move it to later free days, or ask | Show NEGATIVE |
| Spill-over | Unfinished work stays in the Buffer. Nothing is forgiven (RS4, RS7) | Stays |
| End rule | On the date, when done, or by hand | On the date |

## 4A. Admin library (every kind of upkeep job, in every schedule)

**Why.** The normal week today has dishes, a Saturday clean, a Sunday room reset and a Monday shop, but not everything. Making the bed, bins on other days, wiping the kitchen, bed sheets, bills, post, appointments, prescription refills and the rest are not in it. The Admin library is a **catalogue** of upkeep jobs. Each job is an item with its own settings, can be switched on or off, and **is kept in every modifier**, in the normal schedule too.

### 4A.1 What a job has

| Setting | Meaning | Default |
| --- | --- | --- |
| Title and area | By area (4A.3) | Required |
| Checklist | Optional sub-steps, each with minutes ("bathroom: toilet 3, sink 3, shower 5, floor 4") | None |
| **Frequency** | Daily, weekdays, N times a week, weekly on a day, every N days, fortnightly, monthly (a date or "after pay"), once, **as needed** (set off by an event, for example before guests or when the bin is full) | By job |
| Days | Which weekdays | By job |
| **Window** | The earliest and latest time it may sit, or a part of the day, or an anchor ("5 minutes after wake") | By job |
| Length and **minimum** | Usual minutes and the shortest it may be squeezed to (Lighter) | By job |
| Can split | May be done in two parts the same day | No |
| Can combine with | Jobs it may share a block with (bins out with the washing out) | None |
| Place | Home, a room, the shop, the post office | Home |
| Priority | Low, normal, high (high is placed first and dropped last) | Normal |
| Supplies | Items it needs, linked to the shopping list (bin bags, bathroom cleaner) | None |
| Proof | A photo in the window (Y1) or none | **Chores: photo, as now. Other admin: none** |
| Phone during it | Open (as chores are today, K3) or blocked | Open |
| Reminder | Before the window opens, or at the end of the window | Off |
| Counts for money | No (Y4 counts work and lectures only) | No |
| Active dates | A season (for example "winter: change the bedding weight") | Always |
| State | On, paused, or off | On for the standard set |

### 4A.2 How jobs are placed (so the week does not break)

- **Spread, do not pile up.** Jobs are spread over the week by their frequency, window and the free time that exists; **no day gets more than a daily admin cap** (default 90 minutes, a setting). Saturday is not the only dumping ground.
- **Batch by area** when a job allows it (all the kitchen jobs together after dinner).
- **Anchors first**: making the bed goes after wake; dishes after the meal; bins out the evening before collection; the weekly shop after the plan's shopping block.
- **Where it sits**: only in free time, chore blocks and empty time, never on top of anything else, never in the night (MG6), and a day off keeps them (C5).
- **If the window is full**: move within the window, move to the next day, or Lighter (its minimum). A job that still cannot be placed says which day and why. It is not silently dropped.
- **Missed jobs** come back **once** (RS5) at the next free slot with the reason shown, but never as "work in the Buffer": admin does not change the Buffer.
- **Away, ill, guests, a period off**: each preset says what it does to admin (section 4A.5).

### 4A.3 The catalogue (every kind of job, with defaults you can change)

A job is **Standard** (on by default for the normal schedule), **Optional** (off until you switch it on) or **Triggered** (as needed).

| Area | Job | Default frequency | Default length | Set |
| --- | --- | --- | --- | --- |
| **Bed and bedroom** | Make the bed | Daily, 5 minutes after wake | 3 | Standard |
| | Open the window or air the room | Daily after wake | 2 | Optional |
| | Tidy the room (surfaces, clothes off the floor) | Daily, evening | 10 | Standard |
| | Room reset (surfaces, bed, dishes) | Weekly, Sunday | 10 | Standard |
| | Change the bed sheets | Fortnightly | 20 | Standard |
| | Change the pillow cases | Weekly | 5 | Optional |
| | Wipe the desk and clear the study space | Daily, before the first block | 5 | Optional |
| | Set up the study space for tomorrow (M1) | Daily, evening | 5 | Standard |
| **Kitchen** | Wash dishes and the 10-minute tidy | Daily, after dinner | 15 | Standard |
| | Wipe the counters and hob | Daily | 5 | Standard |
| | Clean the microwave | Fortnightly | 5 | Optional |
| | Clean the fridge and bin the old food | Weekly | 10 | Standard |
| | Clean the oven | Monthly | 20 | Optional |
| | Meal prep and batch cook | Weekly, Sunday | 45 | Standard |
| | Pack tomorrow's lunch | Daily, evening | 5 | Standard |
| **Bathroom** | Wipe the sink and surfaces | Twice a week | 5 | Optional |
| | Clean the bathroom (toilet, sink, shower, floor) | Weekly, Saturday | 25 | Standard |
| | Clean the shower drain and tiles | Monthly | 15 | Optional |
| **Floors and surfaces** | Hoover the floors | Weekly, Saturday | 20 | Standard |
| | Mop | Fortnightly | 20 | Optional |
| | Dust | Weekly | 10 | Optional |
| | Clean the windows and mirrors | Monthly | 15 | Optional |
| **Laundry and clothes** | Put a wash on | By need, about twice a week | 5 | Standard |
| | Take the washing out and hang it | After each wash | 10 | Standard |
| | Put the clean clothes away | Weekly | 15 | Standard |
| | Ironing | Weekly, Saturday | 30 | Standard |
| | Lay out clothes and the bag for tomorrow | Daily, evening | 5 | Standard |
| | Gym and football kit washed and packed | Before each session | 5 | Standard |
| **Bins and recycling** | Take the bins out | Weekly, the evening before collection | 5 | Standard |
| | Empty the room and kitchen bins | Twice a week | 5 | Standard |
| | Recycling and glass | Weekly | 5 | Optional |
| **Shopping and food** | The weekly shop | Weekly, Monday (the plan's Aldi block) | 60 | Standard |
| | Top-up shop for a missing item | Triggered | 20 | Triggered |
| | Check the shopping list and prices (SH1, SH3) | Weekly, before the shop | 10 | Standard |
| | Put the shopping away | After each shop | 10 | Standard |
| **Money and bills** | Weekly money check (the guide, the £40 food money) | Weekly | 10 | Standard |
| | Pay bills and rent | Monthly, on the due date | 15 | Optional |
| | Check the bank and statements | Weekly | 10 | Optional |
| | Receipts into the app | Weekly | 5 | Optional |
| **Post and parcels** | Collect the post and parcels | Twice a week | 5 | Optional |
| | Return an item | Triggered | 20 | Triggered |
| **Health and body** | Shower, teeth, face (the routine) | Daily | in the routine | Standard |
| | Refill a prescription (your medicines) | Monthly, a date you set | 15 | Optional |
| | Book or attend an appointment (doctor, dentist, optician) | Triggered, with a date | 30 | Triggered |
| | Haircut, nails | Every N weeks | 30 | Optional |
| | Stretches and a walk | Daily | in the routine | Standard |
| **Digital** | Charge the phone and devices | Nightly, at the sleep anchor | 1 | Standard |
| | Clear the email inbox | Daily or twice a week | 10 | Optional |
| | Back up files and the laptop | Weekly | 10 | Optional |
| | Phone storage and updates | Monthly | 10 | Optional |
| **University admin** | Check the timetable for changes (L6) | Weekly, Sunday | 10 | Standard |
| | Check Canvas (D3) and MyUoB (L7) | Weekly | 10 | Standard |
| | Registration, forms and extensions | Triggered, with a date | 20 | Triggered |
| **Faith and life** | Pack the church bag | Weekly, Sunday | 10 | Standard |
| | Call or message family | Weekly | 15 | Optional |
| | Birthdays and cards | Triggered, with a date | 10 | Triggered |
| **Household** | Water the plants | Weekly | 5 | Optional |
| | Replace a bulb, a battery, bin bags | Triggered | 10 | Triggered |
| **Transport** | Top up the bus pass or buy tickets | Triggered, with a date | 10 | Triggered |
| **Guests and events** | Room prep for guests | Triggered, before guests | 150 | Triggered |
| | Clear up after guests | Triggered, after guests | 60 | Triggered |

If a job you do is not here, **Add a job** makes one from the settings in 4A.1, so the library is exhaustive through modifiability.

### 4A.4 Load sets and Recommended

- **Load sets:** **Light upkeep** (the Standard jobs at their minimum lengths), **Standard** (the Standard column as written), **Thorough** (Standard plus Optional). Pick one as the starting point, then switch any job on or off.
- **Recommended** (section 11, button press only) spreads the chosen jobs over the days from your free time, your existing chores and the daily admin cap, and shows the reason for each placement ("bins on Thursday evening because collection is Friday"). It never places a job where it breaks a guard rail.

### 4A.5 What each modifier does to admin

| Modifier | Admin |
| --- | --- |
| None (normal week) | The enabled library, placed by 4A.2 |
| Assignment, Revision, Catch-up, Focus, Lecture-free lighter | Kept. Lighter (minimum lengths) only if you choose it |
| Period off | Kept; the period's free hours can take the Optional jobs if you choose |
| Recovery or illness | Light upkeep (the minimums) by default; the rest wait |
| Away or travel | Home jobs paused, travel jobs (packing, tickets) injected; paused jobs do not pile up on return unless you choose |
| Guests | Room prep and clear-up injected (Triggered); other jobs kept |
| Placement or job week | Light upkeep, with the weekly jobs moved to the free days |
| Fresh start | Standard, with a slow start |

Dropping a job for a period is **LOOSEN** (the wall). Pausing a Triggered job nobody has set off is not.

### 4A.6 Guard rails for admin (added to section 10)

- **MG19** Admin is never placed on a protected block, in the night, or on a hard alarm, and two jobs never share a time unless the job says it can combine.
- **MG20** No day exceeds the daily admin cap; a job that does not fit is moved or lightened and the move is shown. It is never dropped silently.
- **MG21** A job never goes below its minimum length, and a job with a checklist keeps every step unless you remove it.
- **MG22** Anchored jobs only exist where their anchor exists: no "make the bed after wake" on a day with no wake block, no "bins out the evening before collection" without a collection day set.
- **MG23** Health jobs (a prescription refill, an appointment) are **reminders with your own text and dates**. Spark never chooses a medicine, a dose or a date.
- **MG24** Admin never changes the Buffer, the money ratio or the earned phone access (R2, Y4): it is upkeep, not work.

### 4A.7 Tests (proposed)

Each catalogue job places at its default; a frequency of every kind places on the right days; the daily cap holds; batching by area; anchors; an unplaceable job is reported with its day and reason; a missed job returns once; every modifier keeps the enabled library; Lighter respects minimums; Drop needs the wall; Recommended spreads Saturday's load; admin leaves the Buffer unchanged; guard rails MG19 to MG24 each refuse a plan that breaks them.

## 5. Assignment preset (separate from revision, in anticipation)

**Purpose:** finish assignments well before they are due. It plans **backward from each due date**, so work starts early and is spread, and the due date is a safe end and not the plan.

### 5.1 Per assignment (an item with its own settings)

| Setting | Meaning | Default |
| --- | --- | --- |
| Title and module | By module | Required |
| Due date and time | The deadline | Required |
| Size | Hours and minutes of work, or a heaviness (D2) | Recommended from heaviness and your calibration (B5) |
| Priority | 1 (most) to 5, or a rank | 3 |
| Start rule | **Anticipate** (start N days before), **Start on** a date, or **As soon as there is room** | Anticipate |
| **Lead time** | N days before the due date when work begins | **14 days** |
| **Finish early by** | Done this long before the deadline | **24 hours** |
| Session length | Minimum and maximum minutes of one sitting | 45 to 120 |
| Sessions a day | At most N for this assignment | 2 |
| Preferred part of the day | Morning, afternoon, evening, any | Any |
| Place | Home, library, or any | Any |
| Milestones | Outline, draft, final, each with a share of the hours and a day | 15%, 55%, 30% |
| Review pass | A short block after the draft | Off |
| Rest before due | Nothing on the evening before | Off |
| Depends on | Another assignment that must come first | None |
| Proof | A photo of the work, or a commit (G1) | By block type |
| Reminder to submit | A reminder before it is due | 3 hours before |

### 5.2 For the whole preset

| Setting | Meaning | Default |
| --- | --- | --- |
| Number of assignments | Items, added in turn | 1 or more |
| Planning mode | **Anticipate** (spread back from the dates), **Even**, **Front-load**, **Crunch** (last, with a warning it is the risky choice) | Anticipate |
| Daily cap | Most assignment hours in a day | Recommended (section 11), at most 6 |
| Planning order | **Sequential**, **Interleaved** or **Auto** (5.4) | Auto |
| Study cycles during it | **Adaptive lightening** (5.3), keep, lighten, or pause with the cycles returning as catch-up | Adaptive lightening |
| Focus share | The share of work time that goes to this preset (7.3) | Focus week hybrid with a period off: 100%. Focus week not hybrid: what is left after the lightened lecture cycles. Other presets: what is left after the cycles |
| Quiet mode | More blocking while assignment blocks run (TIGHTEN) | Off |
| Re-plan on a miss | Spread the remaining work again over the days left, with the change shown | On |
| Start date already passed | Say how many hours a day are now needed | On |

**How anticipation works.** For each assignment the app works backward from "due time minus the finish-early margin", places the milestones, then fills the **work blocks that exist** and the free time that is allowed to convert (RS3), earliest day first, within the daily cap and your session settings. With the default 14 days, a Friday 4 December 17:00 deadline starts on Friday 20 November and is planned to finish by Thursday 3 December 17:00. The preview shows each assignment's start date, sessions and finish date. If it cannot fit it says by how many hours (NEGATIVE) and what to change.

### 5.3 Adaptive lightening (how the cycles adapt to the assignments)

Study cycles keep running, but they **lighten as the assignment load rises**, and re-adapt whenever the load changes.

- Each day: assignment hours placed that day = H. Normal cycles that day = N (lectures' cycles plus any quota).
- Lightening order, until H fits within the daily cap: first turn **standard cycles into light** (2.5 hours to 1 hour); then **drop the quota's extra cycles**; then, only if still needed, **drop a light cycle of the module with the least recent lecture** to the following free day.
- It **never goes below** one light cycle on a lecture day (so a lecture is never left with nothing), and **never removes** the cycle for a lecture the same day.
- The cycles it lightens or moves are **shown as lightened, with the reason**, and the hours taken out come **back as catch-up work** after the preset ends (RS1, RS7): nothing is forgiven.
- When an assignment is finished early, slipped, added or removed, the day's lightening is **worked out again** and the preview of the change is shown.
- Settings: lightening rule (adaptive, fixed, none, pause), the floor per lecture day, which module to protect first, whether lightened hours return as catch-up (default yes).

### 5.4 One after the other, or interleaved (planning order)

| Setting | Meaning | Default |
| --- | --- | --- |
| Order | **Sequential** (finish one, then the next, by priority and due date), **Interleaved** (alternate blocks between assignments), **Auto** | Auto |
| Auto rule | Interleave when two assignments' windows overlap by more than N% **and** their due dates are within D days; otherwise sequential | N = 50, D = 3 |
| Interleave ratio | Blocks per assignment in proportion to priority and remaining hours | By priority and remaining hours |
| Minimum run | Fewest consecutive minutes on one assignment before switching | 60 |
| Most switches a day | To avoid hopping | 3 |
| Same due day | Planned together by priority (interleaved) or one after the other by priority | Interleaved if Auto says so |

## 6. Revision preset (separate)

| Setting | Meaning | Default |
| --- | --- | --- |
| Exams (items) | Module, date and time, length, hard alarm (D4), topics | Required |
| Per exam | Hours, priority, topics a day, past papers, a mock exam block, a rest block after | Equal split |
| Method | Spaced rotation of modules, blocked by module, or weakest topic first | Spaced rotation |
| Slots a day | The number of study cycles a day | 3 |
| Past papers | Number, timed or open | 2 per exam |
| Rest day before an exam | Yes or no | Yes |
| Lead time | Days before the first exam when revision begins | Recommended |

## 7. The other presets

### 7.1 Catch-up week

| Setting | Meaning | Default |
| --- | --- | --- |
| Backlog | All missed work, chosen modules, or a date range | All |
| **VRNP slots a day** (study cycles) | The floor and a stretch on a good day. Lecture cycles count toward it | Recommended; floor 3, stretch 4 |
| Slot length | Standard 2.5 hours or light 1 hour | Standard |
| **Intensity** | **Full** or **Rapid notes** (faster video, skim reading, short notes, fewer problems) | Full |
| Rapid notes detail | Video speed (1.25, 1.5, 2), reading minutes, note style (key points only), problems to do | 1.5, 15, key points, 5 |
| Structure of a slot | Minutes of video, reading, notes, problems | 15, 25, 60, the rest |
| Module weights | Which modules go first, by what is most behind | Most behind first |
| Module cap a day | Most slots of one module a day | 2 |
| Recordings for missed lectures | At length, or faster | At length |
| Weekend | Same floor, lower, or none | Same floor |
| If a stretch does not fit | Drop it and say so, or show NEGATIVE | Drop and say so |
| Ends | On the date, or when the backlog is cleared | On the date |

Slots are shown **by module** ("Neural Computation: notes and problems"), never as "VRNP".

### 7.2 Period off (one preset for a week off, a weekend off or any days off)

You pick a **start date and an end date** (1 to 14 days). Work and lectures are off for the period, and **important blocks stay or are added in between**.

| Setting | Meaning | Default |
| --- | --- | --- |
| Start and end | Any dates, 1 to 14 days. A weekend is two days | Required |
| What is paused | Work blocks, lectures, cycles, planning (the list a day off removes) | Work, lectures, planning |
| **Daily routine** | Kept: Bible reading, setting up for the day, dishes, tidying, chores, prayer, meals (4). This is also how a day off works today, which removes only work, lectures and planning | Kept |
| Pinned blocks (items) | Important blocks with their own settings (below) | Church, gym, MyTutor, football as in your week |
| Light goals | Optional small things (read 20 minutes a day, a walk) | None |
| **Screen-time policy** | Section 9 | **4 hours a day across every non-essential app** |
| Blocking level | Social apps blocked in no block, in pinned work blocks only, or as now | In pinned work blocks only |
| Alarms | No backstop alarm on a day off (G2); optional soft wake | No alarm |
| Sleep and meals | Always kept | Fixed |
| Cycles and assignments due in the period | They stay in the Buffer. The preview says what the period costs (C4), for example "Period off Sat to Mon: Buffer -9h", and shows NEGATIVE with the working if it does not fit | Shown |
| Wall | A period off is one request and counts as **one use** toward the 30-day tally (H5). The string is the base length for one or two days and **25 characters longer for each further day**, rising with uses in the last 30 days as H1. Same-day or after-the-fact adds the usual 40 (H3, H6) | Proposed, see section 14 |

**Pinned blocks (important blocks) and how they are placed**

| Setting | Options |
| --- | --- |
| Title, type, length | Free text, any block type, minutes |
| **Placement** | **Fixed time**, **Window** (placed anywhere in it, in free or empty time), **After an anchor** (30 minutes after wake, 1 hour before sleep), **Part of the day**, **Repeat** (every day, weekdays, a list), **Once** |
| If the time is taken | Move within the window, move to the next day, or tell me and not place |
| Proof | Photo, none (Y1) |
| Blocking | Whether the phone is blocked during it |
| Hard alarm | Yes or no |
| Counts for money | Default by type (Y4) |

### 7.3 Focus week (a project only), integrated

**Focus** is a **mode of the Assignment engine**, not a separate engine. A Focus week is the Assignment preset with **one project item** and **exclusive** switched on, so it uses the same lead time, milestones, sessions, daily cap, adaptive lightening and recommended settings, with these differences:

| Setting | Meaning | Default |
| --- | --- | --- |
| Project | One item: title, module, deliverable, due date | Required |
| **Hybrid with period off** (toggle) | Turn on when there are no lectures but you are still working, for example the Christmas holidays. It runs the Focus week **inside a Period off** (7.2): lectures and lecture cycles are paused, the pinned blocks stay, the screen-time default of the period off applies, and the project is the only work | **Off** |
| **Exclusive** | **All** work time goes to the project, or a share (for example 70% project, 30% other work) | Hybrid on: 100%. Hybrid off: the project takes what the lightened lecture cycles leave |
| Lecture cycles | **Hybrid off:** they keep running, lightened as in 5.3 and never below the floor on a lecture day. **Hybrid on:** there are no lectures, so there are none to catch up and none are made. The project's hours are the work, and the rest of the day is **free time** (the protected free hours are untouched) | By the toggle |
| What is paused | Other assignments (hours come back as catch-up afterwards, RS1, RS7). Hybrid on: lectures, cycles and planning as in a period off | Other assignments |
| **Daily routine** | **Still happens** in a Focus week, hybrid or not: Bible reading, getting ready and setting up for the day, washing dishes and tidying, tidying the room, chores, prayer, meals. The project takes only the work time, never the routine (4, MG18) | Kept |
| Lectures | Hybrid off: kept, recordings, or dropped (dropped is LOOSEN). Hybrid on: none | Kept |
| Deep-work sessions | Length, breaks, a daily goal, a check-in at the end of each day | 90 minutes, 15-minute breaks |
| Milestones | Per day or per phase | Three phases |
| Distractions | A screen-time policy on top (section 9), quiet mode | Quiet mode on |
| Place | Home, library | Library if set |
| Ends | On the date, or when the project is done | On the date |

**Integration:** the **Hybrid with period off** toggle is how Focus combines with Period off: it creates the period off for the same dates (so it is one preview and one apply), and its LOOSEN actions (lectures paused, screen-time or blocking changes) need the wall once. Focus also combines with **Library**. It cannot combine with a second Focus week at the same time, and it can run beside a Catch-up week only with a share below 100%.

### 7.4 Lecture-free lighter period (no lectures, but not off)

For the days or weeks when **there are no lectures and the load is lighter, but you are still working**: a reading week, the holidays, strike days, exam leave, a gap between modules. It is **not** a Period off (work does not stop) and **not** a Focus week (there is no single project): it is the ordinary week with lectures removed and the work eased.

| Setting | Meaning | Default |
| --- | --- | --- |
| Dates | Start and end, 1 to 14 days | Required |
| Lectures and tutorials | **None** in the period (they are not in the calendar, so there are no lecture cycles and nothing to catch up) | None |
| Work level | Gentle, normal or heavy (scales the hours a day) | **Gentle** |
| Work hours a day | Minimum and maximum | 3 to 5 |
| Cycles | A quota of study cycles a day, none, or by module (catching up or reading ahead) | 1 a day |
| Reading or light goals (items) | Each with a title, minutes a day, part of the day | None |
| What fills the freed lecture time | Free time, a light goal, a longer break, or a task you choose | Free time |
| **Daily routine** | Kept (4) | Kept |
| Pinned blocks | Church, gym, MyTutor, football as set | As in your week |
| Screen-time policy | Section 9 | **Your block list, no daily limit** (a lighter period is not an off period); you can set one |
| Blocking level | As now, pinned work only, or none in the freed time | As now |
| Wake and sleep | Unchanged by default; a Routine modifier can ramp them | Unchanged |
| Alarms | Keep the backstop alarm (G2); a lecture alarm is not needed | Backstop kept |
| Cost | Shown in the preview. It frees lecture hours, so it can only help the Buffer; it never lowers the cutoff or the protected hours | Shown |
| Class | NEUTRAL or TIGHTEN when lectures are removed by the timetable; LOOSEN only if you also shorten work | |

It can run **with an Assignment preset** (the freed hours go to the assignments, which is the point of a reading week) or **with a Catch-up week** (the freed lecture hours become slots).

### 7.5 Library period (built today as C2)
Moves work to the library, adds walks, brings the walk minutes back as catch-up. It is one action inside any other modifier. Settings: opening hours (asked once), dates, which blocks, walk minutes.

### 7.6 Routine
**Build** (move wake and sleep toward a target by a step a day) or **Keep**. Settings: target wake and sleep, step in minutes (default 15), direction, anchors, reminders, what to do on a bad night (hold the step), tolerance before drift is shown. The ramp never moves a hard alarm or fixed commitment.

### 7.7 Recovery or illness
Shorter blocks, protected rest, recordings, an optional day off (the wall, H1 to H6). Settings: days, how much work to keep, what follows (a catch-up week, default off).

### 7.8 Away or travel
Swap days to a travel template, cancel clashing commitments (wall), protected rest, place changes. Settings: dates, place, commitments to keep, offline behaviour.

### 7.9 Guests or social
A "Guests" block over free time, shorter work blocks (shown), work moved earlier (TIGHTEN), a room-prep block. Settings: arrival, leave, prep length, what must still happen.

### 7.10 Reminders (inside any modifier)
Text you type, anchor (wake, breakfast, lunch, a clock time, sleep, the night cutoff), offset, days, length (5 minutes), where it may sit (free or empty time, or trim a work block as LOOSEN), an optional ramp. Spark never advises on medicines: you type the timing.

### 7.11 Placement or job; Fresh start
Placement: swap days to a work template, cancel clashing lectures (wall), a smaller quota. Fresh start: record-only money again (G6), a low quota, a slow ramp; blocking is never record-only (U12).

### 7.12 Custom
An empty modifier you build from the verbs (section 8), the scope (section 3), the settings (section 4), items and a screen-time policy. Every preset can be duplicated into a custom one and edited, so the list is exhaustive through modifiability.

### 7.13 Situation guide (so the list is exhaustive for real life)

Every situation maps to a preset, a combination, or Custom. "Combination" means up to three modifiers at once (MS3).

| Situation | Use |
| --- | --- |
| Ordinary term week | None (the base template) |
| An assignment is coming | **Assignment** (anticipation) |
| Several assignments, one soon | **Assignment**, order Auto |
| Exams are coming | **Revision**, plus **Assignment** if one is also due |
| You are behind on lectures | **Catch-up week** |
| Behind and one assignment due | **Catch-up week** + **Assignment** |
| No lectures, lighter, still working (reading week, strike, exam leave) | **Lecture-free lighter period** |
| No lectures and one big project | **Focus week**, hybrid on |
| Term time and one big project | **Focus week**, hybrid off (lecture cycles stay, lightened) |
| Christmas or Easter holidays, working | **Lecture-free lighter period**, or **Focus week** hybrid on |
| Christmas or Easter holidays, resting | **Period off** |
| A weekend off | **Period off** (two days) |
| A week off | **Period off** (up to 14 days) |
| A week off with a few fixed things (church, gym) | **Period off** with pinned blocks |
| A day off, ill | **Recovery**, or a day off (H1) |
| Bad night or poor sleep stretch | **Routine** build, and the automatic day off (H4) |
| Getting back into a routine | **Routine** build |
| Holding a routine you have | **Routine** keep |
| Working from the library | **Library** |
| Travelling or away | **Away** |
| Guests staying | **Guests** |
| A job, placement or interview week | **Placement** |
| A hackathon or event | **Custom** (inject the event, lighten other work) |
| A fresh start after a bad stretch | **Fresh start** |
| Your own mix | **Custom** |

If a situation is not here, **Custom** can build it, because every preset is made of the same parts (section 2) and any of them can be added, removed or changed.

## 8. The actions (what a modifier can do)

| Verb | Actions | Class |
| --- | --- | --- |
| **INJECT** | Add a block; add a task or deadline; add a study cycle or a quota; add a milestone; add a reminder; add a protected block; add a recording block | TIGHTEN, or NEUTRAL if it takes free time |
| **TRANSFORM** | Move to a place; resize blocks (longer TIGHTEN, shorter LOOSEN); shift times; ramp wake and sleep; change the order of work; change intensity; change blocking (more TIGHTEN, less LOOSEN); change proof; change the conversion margin; lighten cycles | by effect |
| **REPLACE** | Swap a day's template; swap a week; swap a part of a day; a lecture for a recording; a block's content | by content |
| **PIN** | Keep or add an important block when everything else is off | NEUTRAL |
| **LIMIT** | Set a screen-time policy | TIGHTEN or LOOSEN |
| **REMOVE** | Remove work for a period (the wall, escalating); cancel a commitment (wall) | LOOSEN |

**Never available:** remove sleep, a meal, the 6 protected free hours, a reset, a hard alarm, the night cutoff, or **an essential** (section 10).

## 9. Screen-time policy (works inside any modifier, and on its own)

| Setting | Meaning | Default |
| --- | --- | --- |
| Applies to | An app group (social, video, games, shopping), chosen apps, or **every non-essential app** | Period off: every non-essential app. Others: your block list |
| **Never counted, never blocked** | The **essentials** (section 10) | Fixed |
| **Daily limit** | Minutes a day across the group | **Period off: 240 minutes (4 hours) total**. Other presets: none until you set one |
| Per-session limit | Longest single stretch | None |
| Quiet window | Hours when the group is blocked | The night rule |
| Day type | Work days, off days, or both. A period off keeps it on | Both |
| **At the limit** | Three choices you can pick for each policy: **Block until tomorrow**, **Ask for 15 more minutes** (the typed wall, a longer string each time in the same day, N4), or **Warn only**. "Ask for 15 more minutes" is always one of the choices, and when the policy is on it the limit screen shows the button | Block until tomorrow |
| Most extra requests a day | How many times "Ask for 15 more minutes" can be used in a day | 3 |
| Counts in work blocks too | Yes | Yes |
| Reset time | When the day's count restarts | 04:00 |
| Class | A lower limit is TIGHTEN; raising or removing one is LOOSEN (wall) | |

**How it is measured.** Android's usage access (a permission U2 already plans) gives foreground minutes per app, not content. If the permission is off or fails, the limit says it cannot count and the existing blocking rules continue (X7). "4 hours total" counts the minutes of **all non-essential apps added together**.

## 10. Guard rails: keeping the app from breaking itself

Every preview runs a **checker** against the plan. A plan that fails any rule below cannot be applied, and the preview names the rule and the thing that broke it. The same checker runs again each time the calendar is computed, so a rule that a later change would break is caught then. Every rule has a test.

**Essentials (the case you named)**

- **MG1** The **essentials set** is never blocked and never counted by any modifier, block, quiet window, screen-time limit or "every non-essential app" choice. It is: phone and emergency calls, the default messages app, maps, banking and payment apps, authenticators, the camera, the clock and alarms, Android Settings (LA6 only walls Spark's own pages), the launcher itself, and Spark. You can **add** to it, and you can never remove the fixed core. Adding loosens blocking, so it goes through the typed wall, with one convenience: **passing the wall once opens a 5-minute add-essentials window**. During it you can add **up to 3 apps** with no further typing; when the 3 are added or the 5 minutes end, whichever comes first, the window closes and the next add needs the wall again. The window shows the time left and lists what was added. Apps you added can be taken out of the essentials at any time with no wall (that only tightens). Every window and every addition is logged.
- **MG2** "Every non-essential app" is **worked out when it is enforced**, not saved as a list, so a newly installed banking app is covered the moment it is marked essential and is never caught in the meantime if it is on the essentials set.
- **MG3** A block list that already contains an essential is refused, with the app named.
- **MG3a** The add-essentials window is 5 minutes from the moment the wall is passed, and takes at most 3 apps, counted on trusted time (G10), is closed by leaving the screen, and cannot be extended. It never lets you remove the fixed core and never lets an essential be added to a policy as a "blocked" app.

**Time and placement**

- **MG4** No two blocks overlap after a modifier is applied, except a pinned block into free or empty time.
- **MG5** No block has a length below its type's minimum or above its maximum; no negative or zero length.
- **MG6** Nothing is placed in the night (after the cutoff until wake) except sleep, a reminder and what is already there. A ramp that would put a block there stops and says so.
- **MG7** Every protected block (sleep, meals, the protected free hours, resets, hard alarms) is in the calendar exactly as before, or moved only inside its own window.
- **MG8** Start dates, due dates and ranges are real: a start before a due date, a period of 1 to 14 days, nothing in the past except as a claim (H6).
- **MG9** Dependencies have no cycle (A before B before A is refused).
- **MG10** A daily cap is never exceeded by the placed work; if work cannot fit it is shown as NEGATIVE and not squeezed in.
- **MG11** A lecture is never left with no cycle by lightening (5.3); a lecture is never dropped unless you chose it, with the wall.
- **MG12** Nothing is placed on a day off, except pinned blocks and reminders.
- **MG13** A hard alarm is never moved, removed or covered.
- **MG14** A screen-time limit is never so low that it blocks the essentials (MG1) or Spark's own screens, and cannot be set below 15 minutes a day.
- **MG15** Two modifiers that ask for the same time are resolved by order (section 12); if they ask for something protected, the apply is refused with both named.
- **MG16** The result is the same however many times the checker runs (applying twice changes nothing, IM8).
- **MG18** The **daily routine and every enabled Admin library job** (Bible reading, getting ready and setting up for the day, breakfast and bag, washing dishes and the tidy, the room reset, making the bed, bins, chores, prayer, stretches, resets) is never removed or shortened below its minimum by a preset. Only the person can drop or lighten one, with the wall for a Drop. A Focus week, a Period off and a Lecture-free lighter period all keep it.
- **MG17** If anything cannot be computed, the app shows the base template and says so, and blocking continues as before (X7). A modifier never leaves the phone with **less** blocking than before unless you passed the wall.

## 11. Recommended (automatic parameters)

Every preset has a **Recommended** button. It fills in each setting from **your own data**, shows the **reason beside every value** ("14 days because the assignment is about 12 hours and you have about 1 hour a day free of cycles"), and marks each value "recommended". Nothing is applied until you press Apply, and every value can be changed after.

| Preset | What it recommends, and from what |
| --- | --- |
| Assignment | **Size** from heaviness and your calibration (B5); **lead time** from size ÷ your free work hours a day, never less than 7 days and never more than the days left; **daily cap** from free work hours after cycles; **sessions a day** and **session length** from your block lengths; **planning order** from overlaps (5.4); **priority** from due date, then size |
| Revision | **Lead time** and **topics a day** from the number of exams, days left and hours free; **slots a day** from your cycle history |
| Catch-up week | **Slots a day** from the **size of the backlog** (hours missed ÷ days available ÷ slot length), floor and stretch from your last 4 weeks of proven cycles; **intensity** (Rapid notes if the backlog is more than 1.5 times your capacity) |
| Period off | **Pinned blocks** from your fixed commitments (church, gym, MyTutor, football) and reminders from the ones you already use; **screen-time default** 4 hours; **blocking level** pinned only; and the **cost** to the Buffer |
| Focus week | **Daily hours** from free hours; **session length** from your best proven block length; **milestones** from days left; **focus share** 100% unless lectures take most of the day |
| Routine | **Step** from the gap and your last bad nights (smaller after a bad week), within 5 to 30 minutes |
| Library | **Walk time** from your measured walks, otherwise 15 minutes |

**Rules for Recommended (all proposed):**
- **MR1** It reads only what is already on the phone (the plan, calibration, proven blocks, the Buffer). It sends nothing anywhere.
- **MR2** It never recommends anything that fails a guard rail (section 10). It recommends **tighter** or **equal**, never a looser rule than you have.
- **MR3** If there is too little data (fewer than 3 finished tasks in a band, no proven cycles yet) it says so and uses the defaults in this document.
- **MR4** It never changes a setting you set by hand. "Recalculate" asks first.
- **MR6** It runs **only when you press the Recommended button**. It never runs when a preset opens, when dates change, or in the background. Each press fills the settings you have not set by hand and shows the reason beside every value; pressing again asks before it changes any value.
- **MR5** It is deterministic: the same data gives the same recommendation, so it can be tested.

## 12. Several modifiers at once and the layer order

From the bottom: **base template**, **replacements** (swapped days and weeks), **modifiers** in your order, **day-level changes** (days off, a day swap), **imports**, **automation** (catch-up, conversions, resets, football, library walks).

- **MS1** A higher layer acts on what the lower ones left. A protected block is never removed or moved by any layer.
- **MS2** If two modifiers want the same time, the earlier in the list wins, unless that displaces a protected block; then the apply is refused with both named.
- **MS3** Up to three modifiers run at once. Their daily caps add up and the preview shows the day's total.
- **MS4** Priority between assignments is inside the Assignment preset; between modifiers, by their order.
- **MS5** Ending a modifier early restores the calendar exactly as the layers below it produce it.
- **MS6** Focus (7.3) and a Catch-up week at 100% cannot overlap in time.

## 13. TIGHTEN, NEUTRAL, LOOSEN, the wall, CSV version 2, data

**Wall.** Each action has a class and a modifier's class is its loosest action. TIGHTEN and NEUTRAL apply freely, like an import (IM6). LOOSEN needs the typed wall once for the modifier, with the escalating string where days off are involved (H1 to H6). LOOSEN includes: raising or removing a screen-time limit, shortening work, dropping lectures, trimming work for a reminder, weaker proof, adding to the essentials, fewer blocking reasons.

**CSV version 2.** Version 1 stays valid. Version 2 adds the kinds `modifier`, `item`, `pin` and `limit`, and the columns `preset` and `settings` (`key=value` pairs separated by `;`). An `item`, `pin` or `limit` row belongs to the nearest `modifier` above it.

```
kind,date,end_date,start,end,title,type,venue,heaviness,study,action,notes,preset,settings
modifier,2026-11-16,2026-12-04,,,Assignments,,,,,,,assignment,"recommended=yes;order=auto;cycles=adaptive;daily_cap_h=4"
item,2026-12-04,,17:00,,GT assignment,,,5,,,,assignment,"size_h=6;priority=2;lead_days=14;finish_early_h=24;sessions_per_day=2"
item,2026-12-04,,17:00,,RC assignment,,,5,,,,assignment,"size_h=5;priority=1;lead_days=14;finish_early_h=24"
modifier,2026-11-16,2026-11-22,,,Catch-up week,,,,,,,catch_up,"recommended=yes;slots_floor=3;slots_stretch=4;intensity=rapid;video_speed=1.5"
modifier,2026-12-12,2026-12-14,,,Period off,,,,,,,period_off,"blocking=pinned_only"
pin,2026-12-13,,18:00,19:30,Church,church,,,,,,period_off,"placement=fixed"
pin,2026-12-12,2026-12-14,,,Gym,gym,,,,,,period_off,"placement=window;from=10:00;to=17:00;length_min=45"
limit,2026-12-12,2026-12-14,,,Screen time,,,,,,,period_off,"group=all_non_essential;daily_min=240;at_limit=block;day=both"
modifier,2027-01-04,2027-01-10,,,Project focus,,,,,,,focus,"exclusive=yes;share=100"
item,2027-01-10,,17:00,,CS Project,,,8,,,,focus,"size_h=30;lead_days=7;finish_early_h=24"
```

Admin jobs travel as rows of kind `admin` (version 2), for example:

```
admin,,,18:00,18:10,Take the bins out,,,,,,,,"area=bins;frequency=weekly;days=thu;window=18:00-21:00;minutes=5;proof=photo"
admin,,,07:35,07:38,Make the bed,,,,,,,,"area=bed;frequency=daily;anchor=wake+5;minutes=3;proof=none"
```

Rules: an unknown preset or setting, or a value out of range, is an error with its line and column (IM4); a file with a LOOSEN action needs the wall once (IM6); applying twice changes nothing (IM8); the checker page (`web/csv-prompt.html`) and the app check the same rules from shared test files (IM2). A setting called `recommended=yes` asks the app to fill every setting you did not give.

**Data.** `modifier` (preset, dates, parts of the day, settings JSON, class, order, state), `modifier_item`, `pinned_block`, `screen_policy`, `usage_day`, `essential` (the added ones), `modifier_log`. The calendar is **computed** from the base template and the active modifiers each time, as the library period is today, so it always agrees with imports and days off.

## 14. Failure behaviour and tests

- A modifier that cannot be applied changes nothing (IM11). A calendar that cannot be computed shows the base template and says so (MG17).
- A pinned block that cannot be placed names the day and why. If usage access is off, screen-time limits say they cannot count; blocking continues as now.
- Proposed tests, each named with its row ID: every verb on a sample week; each class with and without the wall; **every guard rail MG1 to MG17 with a plan that breaks it (refused) and one that does not**; **the essentials are never blocked or counted by any preset, group or "every non-essential app"**; anticipation (first session at or before the lead time, last before due minus the margin); lead time 14 days and finish 24 hours before as the defaults; an assignment that cannot fit says NEGATIVE with the shortfall; adaptive lightening for several loads and its re-adaptation; sequential, interleaved and Auto; priorities and caps; a missed session re-spread once (RS5); catch-up slot counts, intensity and module caps; period off keeps its pinned blocks and drops the rest; the screen-time default of 240 minutes across all non-essential apps; Focus exclusive and shared; Recommended is deterministic, reads only local data and never fails a guard rail or loosens a rule; CSV v2 errors with line and column; the clock change on 25 October.

## 15. Questions still open

None blocks the design. Two things I have **assumed** because you moved on without correcting them. Tell me if either is wrong:

1. A **period off** is one request and one use toward the 30-day tally. Up to two days costs the base string and each extra day adds 25 characters.
2. In the **Admin library**: chores keep the photo proof they have now (Y1) and other admin needs none; the daily admin cap is 90 minutes; the Standard set is the starting load. Tell me if you want different defaults.
3. In a **Focus week**: hybrid on means a period off with the project as the only work and the rest free; hybrid off means term time with lecture cycles kept and lightened. The daily routine (chores, Bible reading, setting up for the day) happens in both.
