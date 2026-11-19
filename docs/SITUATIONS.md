# Real-life situations and how Spark solves each

Part 2 of `docs/MASTER_PLAN.md`. Nothing new here is built. This is the document you asked for: real situations a student like you meets, each with what Spark does.

## How this was gathered, and how far to trust it

I tried to get these from Reddit and Google as you asked. **Reddit could not be read**: both the web fetcher and the built-in browser are blocked from it. The Student Room's threads returned errors when opened, so I have only the titles and the gist of their search results. What I could read: the University of Birmingham's own pages, NHS-style and clinical sleep sources, and ADHD and student-advice pages. The rest comes from your own messages, the ledger, your seed plan, and general knowledge of how student life goes. So:

- Each row is a real kind of situation, not a verbatim post.
- The themes that came up repeatedly in what I could read: getting out of bed and the phone across the room; a late body clock with early lectures; fixed bedtime cut-offs and a fixed wake time; working from bed backfiring; falling behind in first term and not telling anyone; low motivation as a slump versus something more; routines collapsing at home during holidays; starting with five minutes; telling a tutor early; revision in short sessions.
- **If you want real Reddit threads in this document, paste a few and I will solve each one.** That is the honest way to add them.

## Legend

| Status | Meaning |
| --- | --- |
| **Have** | A rule for it exists in the ledger and was built in earlier sessions. Phase 0 re-verifies each against the running app; I have not re-checked every row today. |
| **P2** to **P9** | New in this plan. P2 spine (facts, context, doors, anchors), P3 routines and engine, P4 modifiers and gate, P5 home and places, P6 entry points, P7 coach, sleep, sensors, interface, P8 continuity and retention, P9 assurance. |
| **Outside** | The app cannot do it. The row says what Spark does instead. |

Every row's "what Spark does" follows the rule that a refusal always gives a next step (P1, P2 in the master plan).

---

## A. Sleep and nights

| # | Situation | What Spark does | Status |
| --- | --- | --- | --- |
| A1 | Lying awake for 40 minutes after lights-out | The phone is docked across the room. Tap **Awake** and it offers the awake list (get up, low light, no phone, back at 20 minutes). The number shows in the morning, no judgement. | P7 |
| A2 | Scrolling in bed | After the cutoff the social list is closed (N2). Picking the phone up off the dock shows a quiet card and counts screen-on against sleep (S3). | Have, P7 |
| A3 | "Just one more video" at 01:00 | The cutoff closes the list. A 60 second pause screen: breathe, "what will you do instead?". The only way through is the wall (N3). | Have, P7 |
| A4 | A friend texts at 01:00 | Messages are never blocked. One tap sends a prepared reply: "Going to sleep, free tomorrow at 17:00?" Spark drafts, you send. | P7 |
| A5 | You want to do something fun at midnight | "Tomorrow's fun" puts it in a real slot tomorrow. If you go out, "I'm out until ..." (location-checked) reflows the day. | P2, P6 |
| A6 | Hyperfocus on code at 02:00 | Work apps stay open (the night rule closes social only). At lights-out minus 20 a prompt. If you keep going, Spark offers to move lights-out and wake together (free if 8 hours hold) and shows tomorrow's cost. | P2 |
| A7 | Woke at 04:00 and cannot go back | The awake list; the screen stays dim; tomorrow drops a tier; a 20 minute nap window before 15:00 is offered once. | P7 |
| A8 | Asleep at 05:00 with a 09:00 lecture | The alarm follows onset plus 8 hours unless you chose a time (Z1, WT1, Z2). A bad-night card shows the lecture against the recording; the catch-up goes to the Buffer with the reason (Z4). | Have |
| A9 | Slept through the alarm | The alarm rings until the photo; the backstop rings at 12:00 (G2); the morning is flagged; the day reflows from now (RN1). | Have |
| A10 | Woke at 14:30 | Start from now: Bible shortened, meals within their windows, work and free time around your commitments (RN1). | Have |
| A11 | An accidental 3 hour nap | Lights-out and wake can move together; the what-if shows the cost ("sleep at 03:00, wake 11:00, lose the 09:00"); apply or not. | P2, P6 |
| A12 | Forgot melatonin | Your reminder repeats once. Logged as not taken. Spark gives no advice on doses or times. | P7 |
| A13 | Took modafinil late and cannot sleep | Spark does not judge. Tell it, and tonight's anchors can move with a log note. The question of timing is for your prescriber or a pharmacist. | Outside, P7 |
| A14 | Noisy neighbours | The awake list has headphones and white noise as options you set. Nothing to track. | P7 |
| A15 | Room too hot or cold | The awake list. Nothing else. | Outside |
| A16 | Mind racing at night | Brain dump at lights-out minus 40; at night, the Awake tap lets you type it and park it; a 10 minute worry slot tomorrow. | P7 |
| A17 | Exam stress week | Exam mode: a stricter lights-out the night before (the hard stop holds), Short tiers, a wind-down 15 minutes longer. | P5, P7 |
| A18 | Clocks change | Alarms are absolute time; a 23 or 25 hour day; no block lost or doubled (SA6). | P2 |
| A19 | You want to fix a late body clock | A gradual shift: wake 15 minutes earlier every 2 to 3 days, a bright light step, wind-down moves too, never more than 30 minutes a day, weekends within an hour of the anchor. | P3 |
| A20 | Jet lag after a trip | The Away zone, a gradual shift, the light step in local morning. | P5 |
| A21 | A night out | Planned as an Event with an Out anchor. The next day's tier is lowered automatically. | P2 |
| A22 | Sleeping at someone else's place | Place "Elsewhere": docking is a tap, the alarm and night rule work, the reference photo is of that room. | P5 |
| A23 | Ill with a cough | The ill door: Floor tiers, Buffer penalties paused (X3), reminders for what you set. | Have, P4 |
| A24 | Phone died overnight | The alarm is rebuilt at boot and the morning flagged without penalty (X2). A "plug in" nudge at 20% and at docking. | Have, P7 |
| A25 | Reaching for the phone to check the time | A lock screen clock widget shows it; the Clock is never blocked. | P7 |

## B. Mornings

| # | Situation | What Spark does | Status |
| --- | --- | --- | --- |
| B1 | Alarm rings; five more minutes | There is no snooze (M2). The phone is across the room; stopping it needs the photo at the setup spot. | Have |
| B2 | The setup photo fails in the dark | The fallback ladder (M10): a retry with a torch prompt, then steps, then a short wait. Logged as not your fault. | Have |
| B3 | The camera is broken | Steps, then a wait; NFC at the desk is accepted instead. | Have, P7 |
| B4 | Battery 3% | The alarm still rings; the first card says plug in. | P7 |
| B5 | You go back to bed after the alarm | Social stays closed until the ready photo (M6, M8). Ten minutes of no movement raises the prompt to spoken, then the leave-by buzz still comes. | Have, P7 |
| B6 | You did not set up last night | Yesterday's photo is used and the morning says so (M4); no other penalty; the setup is first in tonight's wind-down. | Have |
| B7 | No clean clothes | A laundry cadence reminder every 3 to 4 days; today: wear what is clean; the step floor is one item. | P3 |
| B8 | Cold shower or broken boiler | Floor: a 5 minute wash. A task to tell the landlord with a deadline. | P3 |
| B9 | 10 minutes late | The Bible drops to Short; breakfast is eaten on the move; the leave-by holds (YP). | Have |
| B10 | 40 minutes late | Start from now or Running late; the lecture decision card (go or recording); the plan reflows. | Have |
| B11 | Keys or ID missing at the door | The pocket check at the door tag; "missing" pauses the leave-by and reflows 10 minutes. | P3 |
| B12 | No breakfast food | Breakfast is cut; one tap adds it to the shopping list; lunch shifts. | Have, P3 |
| B13 | Hungover | The Out anchor the night before sets Short tiers; water, light, the Bible floor; a lighter study day. | P5 |
| B14 | The Bible reading runs 20 minutes over | The timer ends at the tier; "Finish or close?" with one 2 minute extension; the rest reflows (RN2). | P3 |
| B15 | The desk is cluttered | The room reset has a floor; the Bible seat is portable: sit anywhere with the tag. | P3 |
| B16 | Sunday and you want to sleep in | The Sunday anchor within an hour; the Bible moves; the church bag steps stay. | Have, P3 |
| B17 | No Wi-Fi in the morning | Everything runs offline. | Have |
| B18 | You forgot your medication | The reminder repeats once; logged. | P7 |
| B19 | Doomscroll after the alarm stops | M5 and M6 close the social list until you are ready. | Have |
| B20 | You are cold and want to stay in bed | The light step (curtains or a lamp), water, the first step is "sit up". | P7 |

## C. Walking, transit and travel

| # | Situation | What Spark does | Status |
| --- | --- | --- | --- |
| C1 | The walk takes 28 minutes, not 25 | Measured times replace guesses (T2); the leave-by moves earlier. | Have, P3 |
| C2 | A bus or train is late | "Running late" (free) reflows the day; a decision card: go, or the recording. | Have |
| C3 | A Cross-City line cancellation | The same; Trainline and Citymapper are never blocked. | Have |
| C4 | A strike day | The composer: lectures on campus off, recordings and study from home. | P4 |
| C5 | Rain or snow | A tap adds 5 minutes to the leave-by; an optional "coat" cue. | P7 |
| C6 | Lost bus pass or ID | The pocket check prevents it; if lost, a task and a deadline. | P3 |
| C7 | Wrong building | L6 asks; weaker location proof allowed; a retake. | Have |
| C8 | 40 minutes early | A light block on campus: the recall step, then reading. | P7 |
| C9 | The room changed this morning | L6 and a Reality report; the venue updates. | Have |
| C10 | GPS drifts indoors | Wi-Fi (eduroam) as a second signal; the wider radius. | P7 |
| C11 | Left the phone on a bus | PC and web view; contact; Google's Find My Device is outside Spark; restore from backup. | Outside, P8 |
| C12 | The train to the family home | A Travel day: audio Bible, a reading block, a reset on arrival. | P5 |
| C13 | Missed the train home | A Reality report; calls never blocked; the arrival moves. | Have, P5 |
| C14 | A late walk home alone | "I'm out"; Maps never blocked; one tap opens location sharing in your maps app. | P7 |
| C15 | Cycling | Cycling counts as transit; the phone is away. | P7 |
| C16 | A walk with audio | A recording or audio Bible plays in the media app during the walk. | P3 |
| C17 | The shortcut is closed | The measured time rises; the observe phase adjusts. | Have |
| C18 | A lift is broken | A Reasonable Adjustment Plan if needed; otherwise a time note. | Outside |
| C19 | Lost on campus | Maps is never blocked. | Have |
| C20 | Location permission lost | The permission card. | P8 |

## D. Lectures, tutorials and labs

| # | Situation | What Spark does | Status |
| --- | --- | --- | --- |
| D1 | A lecture is cancelled | The freed block is labelled "Free time (cancelled)"; study re-places; no Buffer loss (YP4). | Have |
| D2 | A lecturer ill, moved online | A recording or online step; location check waived; attendance by code only. | P4 |
| D3 | A lecture overruns 15 minutes with the next in 5 | Running over (RN2); the walk shrinks to its floor; no penalty. | Have |
| D4 | Two lectures clash | Flagged at import; you pick one; the other becomes a recording catch-up. | Have, P6 |
| D5 | A fire alarm | Time taken; the idea step is skipped, no loss. | Have |
| D6 | The recording is missing | The catch-up waits; a chase task; the due date waits; the note says why. | P3 |
| D7 | The attendance code does not work | Outside Spark; a "tell the lecturer" step with a prepared line; your photo still counts in Spark. | Outside, P3 |
| D8 | Forgot to enter the code | The hourly reminder and the end reminder (LC1). | Have |
| D9 | Arrived late | The attendance window stays open; the photo counts inside it. | Have |
| D10 | Fell asleep in the lecture | The end step "one idea" shows what you caught; the recording fills the rest. | P7 |
| D11 | Cannot concentrate | One idea at the end; the recall step next time; the VRNP cycle catches up. | Have |
| D12 | An unprepared tutorial | A tutorial prep step the day before; if missed, a 20 minute Short prep. | P3 |
| D13 | A lab report due | A deadline; spread across days; a submit step an hour early. | Have |
| D14 | Group project members silent | A "chase the group" task and a block for your own part so the group cannot hold you. | P3 |
| D15 | Presentation day | The Exam-day routine: Short tier, an arrival buffer, rehearsal the day before. | P5 |
| D16 | A supervisor meeting | An Event, a prep step the day before, a notes step after. | P3 |
| D17 | A whole week of lectures missed | A catch-up span with the backlog spread and the shortfall as a number. | P3 |
| D18 | A tight gap between rooms | Warned at import; the transit is measured (T1). | Have |
| D19 | An attendance warning email | An exportable record of your attendance proofs; a "talk to your tutor" card. | P8 |
| D20 | An online lecture at home | The code reminders are identical; phone rules for a lecture. | Have |

## E. Study and the library

| # | Situation | What Spark does | Status |
| --- | --- | --- | --- |
| E1 | You cannot start | The step is "sit and open the thing", then "just five minutes"; NFC at the desk; prompts at 2 and 5 minutes; at 10 the block is late and the plan reflows. | P7 |
| E2 | Distracted after 10 minutes | The block list is closed (K1); the urge pause; the progress ring. | Have, P7 |
| E3 | Stuck on a problem for 90 minutes | A "stuck" tap: switch part, write the question, move it to the error log; it becomes an office-hours question. | P7 |
| E4 | The block overran | Running over (RN2). | Have |
| E5 | The library is full | A second place on your list; transit recalculated. | P3 |
| E6 | Forgot the charger or laptop | The bag list and the pocket check. | P3 |
| E7 | The laptop dies | A paper fallback; the proof is a photo of the pages. | P3 |
| E8 | The internet is down | Offline practice from memory is the block's floor. | P3 |
| E9 | Canvas is down | The submit step runs early; a screenshot as evidence; the extension route. | P3 |
| E10 | A file is lost | An end-of-block "save and back up" step. | P3 |
| E11 | Two deadlines on one day | Heaviness and deadline order; work spreads earlier; the Buffer shows the numbers. | Have |
| E12 | Due tonight and not started | The "deadline today" composer: the minimum viable submission, a what-if of the hours; extensions cannot be asked inside 24 hours, so the extenuating circumstances route is shown. | P4, P6 |
| E13 | The upload fails at 16:58 | The submit step always ends an hour before; screenshots; the routes. | P3 |
| E14 | Referencing worry | A "check references" step before submit. | P3 |
| E15 | January revision is overwhelming | The Revision modifier (daily cap, cycles, session length) and Lite mode. | Have, P8 |
| E16 | A low past-paper average | Your own seed's rule: below 55, change the method (Red Engine and office hours), not just the hours. | P7 |
| E17 | Noise in the library | A second place; headphones. | P3 |
| E18 | A study group wants a session | An Event; it cannot push your floors. | Have |
| E19 | Tidying instead of working | Only the first action is a step; no "organise" steps; the coach: "Start. Five minutes." | P7 |
| E20 | Working on Spark instead of studying | The weekly review shows study hours against plan; the parking lot and the freeze take the new ideas. | P7, P8 |

## F. Deadlines, support and the university

| # | Situation | What Spark does | Status |
| --- | --- | --- | --- |
| F1 | You need an extension | A card: ask *before* the deadline, online, usually no evidence; not for exams (University of Birmingham guidance). | P8 |
| F2 | You missed a deadline | The extenuating circumstances route; the plan for the next steps. | Outside |
| F3 | Ill before an exam | Extenuating circumstances with evidence; Spark's sleep and ill-door log for the evidence you choose to share. | Outside, P8 |
| F4 | Failing a module | A trend card; your tutor; the resit window (the supplementary assessment period is normally in the summer vacation); revision planned. | P5 |
| F5 | Suspected burnout | The mood card; Lite; the people list (GP, the Mental Health and Wellbeing Service). | P7 |
| F6 | A crisis | The Emergency door; the list (999, NHS 111, Samaritans 116 123, Shout text 85258); Spark stops asking for anything. | P7 |
| F7 | A Reasonable Adjustment Plan is approved | Enter the adjusted dates; Spark plans to them. | P6 |
| F8 | A disappointing mark | The test log trend; a reframe from your own numbers; a method review. | P7 |
| F9 | You must email your tutor | A task with a draft template. | P3 |
| F10 | Money is low | The £40 food is never tied to work (R6); the money page. | Have |
| F11 | Rent or bills | Admin tasks with reminders. | P3 |
| F12 | Job application deadlines | Your Sunday check and blocks. | Have |

## G. Phone, social and self-control

| # | Situation | What Spark does | Status |
| --- | --- | --- | --- |
| G1 | The urge to open Instagram in a block | Closed. The 60 second pause: three breaths, "what will you do instead?", an item from your reward menu, or the wall. Triggers are logged. | Have, P7 |
| G2 | An urgent Snapchat message | Messages and WhatsApp are open; the blocked app needs the wall (30 minutes). Spark offers "send it by Messages". | Have |
| G3 | Remove the blocker at 02:00 | The wall and the tamper log (K6); the hard stop (N7); the gap shows in the weekly review. | Have |
| G4 | Accessibility switched off | A persistent notice; the gate returns on the next open; logged. | Have, P7 |
| G5 | Instagram in Chrome | **A gap I must verify** (I do not know that the phone browser is covered). Phase 0 checks; the fix is to block the domains in browsers. | P9 |
| G6 | Someone else's phone | Outside the app. | Outside |
| G7 | The wall is hard on a small keyboard | Three wrong characters forgiven (WS1); a larger-key field is planned. | Have, P7 |
| G8 | Too tired at 03:00 to type the wall | That is the wall working. The awake list is the alternative. | Have |
| G9 | Boredom | Your five-item list; the reward menu; a free-slot alarm. | P7 |
| G10 | Loneliness | The mood card; a friend slot scheduled; Out events; the people list. Spark never replaces a person. | P7 |
| G11 | FOMO about a group chat | Open in free slots; the digest (U8); a "reply later" slot. | Have |
| G12 | Doomscrolling after bad news | The pause screen; grey; an optional 10 minute timer. News is not blocked by default; that is your call. | P7 |
| G13 | A gaming binge | If games are on the block list, they close in blocks; a free slot ends with an alarm (F1). | Have |
| G14 | A YouTube rabbit hole | Closed in work blocks (K1); the free-slot alarm. | Have |
| G15 | A flood of notifications | Quiet mode and the digest (QM1). | Have |
| G16 | You swear at the app | Nothing is held against you. "That was hard. Next step: water." | P7 |
| G17 | You keep skipping the photo | The steps stay open; rewards wait; the review shows your proof rate; the coach asks whether the tag would be easier. | P7 |
| G18 | A fake photo | Window, place and match (L1 to L3). A fake costs more than the task. | Have |
| G19 | A buzzing phone in a lecture | Quiet mode and Do Not Disturb. | Have, P7 |
| G20 | You want to switch Spark off because it feels bad | The "this doesn't work" button, Lite, a pause with a reason, and a backup so you can recover instead of deleting. | P8 |
| G21 | A new phone | A restore and a permission checklist; the tags still work. | P8 |
| G22 | Three hours of scrolling between midnight and 03:00 | The weekly review shows it by hour; the wind-down adapts; the scaffold level goes up. | P7 |
| G23 | A new distracting app installed | Spark asks once whether to add it to the block list. Adding is tightening, so it is free. | P7 |

## H. Food, home and money

| # | Situation | What Spark does | Status |
| --- | --- | --- | --- |
| H1 | You cannot be bothered to cook | A floor meal list of three no-cook options; one tap swaps; the meal check follows. | Have, P3 |
| H2 | No food in the flat | One tap adds it to the shopping list; the nearest shop on Maps; a floor meal. | P3 |
| H3 | The budget runs out | The money card; the food budget never touches work (R6). | Have |
| H4 | The first meal is at 19:00 | The meal windows allow it (RN1). | Have |
| H5 | Skipping meals while hyperfocused | The meal check asks up to 3 times (MC1). | Have |
| H6 | Cooking and showering together | Parallel steps with timers and a safety line. | P3 |
| H7 | Dishes pile up | The dishes chore floor; the photo; the reward waits. | Have |
| H8 | The room is a mess | Four passes; the floor is one; a before and after photo. | Have |
| H9 | The laundry basket is full | A cadence reminder; the Floor is wash and hang. | P3 |
| H10 | Bins | A weekly step the night before. | P3 |
| H11 | The Aldi trip is cancelled | The shop reflows; a five item list from the corner shop. | P3 |
| H12 | Money runs out before payday | The £40 food guarantee; the rest is outside. | Have |
| H13 | A corner shop collection (tonight) | An errand with the 7 minute walk each way and a collect tap. | P3 |
| H14 | Supplements and water | Your reminders. | P7 |
| H15 | A delivery arrives in a block | Time taken (RN2). | Have |
| H16 | A burnt dinner | Time taken; a floor meal; no photo is asked for. | Have |
| H17 | No heating | A task to the landlord; study moves to the library for the day. | P4 |
| H18 | A power cut | Offline plan; the library; alarms are on the phone. | P4 |

## I. Body, health and football

| # | Situation | What Spark does | Status |
| --- | --- | --- | --- |
| I1 | An ankle injury | An injured door: football becomes a rehab or movement floor you set; a physio or GP reminder; study is untouched. | P4 |
| I2 | A match is cancelled | The freed time is labelled; the weekly minimum shows short. | Have |
| I3 | Late for training | Running late. | Have |
| I4 | Very sore after training | A rest option; the movement floor. | P3 |
| I5 | A cold | The ill door; Floor. | Have |
| I6 | Food poisoning | The ill door by tap; hydration reminders. | P4 |
| I7 | A migraine | Ill; dark and Do Not Disturb. | P4 |
| I8 | A GP or dentist appointment | An Event with travel and a reminder. | Have |
| I9 | A day after very little sleep | The tier drop; the nap offer; the squeeze order. | Have, P7 |
| I10 | The gym day shifts | A Move. | Have |
| I11 | Post-football meal at 22:25 | In your plan; the cutoff still holds. | Have |

## J. Faith and values

| # | Situation | What Spark does | Status |
| --- | --- | --- | --- |
| J1 | You missed the Bible this morning | It moves to the first upright moment; Floor in the evening; no penalty. | P3 |
| J2 | Church runs long | Running over. | Have |
| J3 | Too ill for church | The ill door; an online or audio service; the Floor reading. | P4 |
| J4 | Sunday at the family home | The church place changes; the same steps. | P5 |
| J5 | Bible app notifications | The app is never blocked; other notifications are held. | Have |
| J6 | Piano practice missed | Moves to a free slot; the floor is 10 minutes. | Have |
| J7 | Evening prayer at the wrong time | It is anchored to the wind-down. | P3 |
| J8 | A hard spiritual season | The tier and floor stay. Spark asks nothing about content. | P7 |
| J9 | A midweek group | An Event. | Have |
| J10 | A fasting day | You tell Spark: meal checks pause; water and medication reminders stay; it gives no advice. | P4 |

## K. At home and on holiday

| # | Situation | What Spark does | Status |
| --- | --- | --- | --- |
| K1 | You arrive home with no routine | The home place's first-run; the observe phase; a Home study day. | P5 |
| K2 | Family meals at fixed times | Fixed events; no cooking steps. | P5 |
| K3 | Your parents wake at a different time | Your own anchors; headphones; the Bible moves if needed. | P5 |
| K4 | No quiet place at home | A library or café block; headphones; Floor. | P5 |
| K5 | Slow Wi-Fi | Offline-first; downloads overnight. | P5 |
| K6 | Family asks for two hours of help | "Family helping" (free). | P5 |
| K7 | Christmas Day | A holiday day type: floors only, phone open. | P5 |
| K8 | New Year's Eve | A late-night anchor; the next day Short. | P5 |
| K9 | No structure and boredom | The Home study day, a walk, the reward menu, a friend slot. | P5 |
| K10 | MyTutor from home | An Event; a place setting for the Wi-Fi and a quiet room. | Have, P5 |
| K11 | Going back to Birmingham | The leaving checklist and a gradual sleep shift 4 to 5 days earlier. | P5 |
| K12 | Someone moves your charger tag | Detected by the geofence and reference photo; retake. | P5 |
| K13 | Sharing a room | Docking in the room; the awake list. | P5 |
| K14 | Family does not know about Spark | Nothing is shown to anyone; the Emergency door works. | Outside |
| K15 | Coursework due over the vacation | A deadline; the winter arc. | P5 |
| K16 | January assessments approaching and behind | The Revision modifier and Lite; the extension, extenuating circumstances and adjustment routes shown. | Have, P5 |
| K17 | A weekend at home during term | A weekend home day type with reduced obligation. | P5 |

## L. People, guests and nights out

| # | Situation | What Spark does | Status |
| --- | --- | --- | --- |
| L1 | Guests for the weekend | Prep days, the room tidy steps, the aftermath (the Guests modifier). | Have |
| L2 | An unannounced visitor | An Event is added; meals and study reflow. | Have |
| L3 | A birthday or night out | An Event with an Out anchor; the next day's tier drops. | P2 |
| L4 | A wedding or funeral | An Event with travel; Lite afterwards; the people list for bereavement. | P4 |
| L5 | A friend in crisis | Time taken; calls never blocked; Lite later. | Have |
| L6 | A last-minute invite | The what-if shows the cost; adding is free. | P6 |
| L7 | Plans cancelled | The time is freed and Spark offers to use it. | Have |
| L8 | Hosting dinner | The Guests recipe, cooking steps, the shopping list. | Have |
| L9 | A date | An Event with Out; no tracking beyond the calendar; nothing shown. | P2 |
| L10 | The phone out at a social | Free slots keep it open; the evening is protected. | Have |
| L11 | Someone sleeps over | Docking in your room; no change. | Have |

## M. When the technology fails

| # | Situation | What Spark does | Status |
| --- | --- | --- | --- |
| M1 | The app crashes in a block | The crash guard; the timer resumes from stored times (LA3). | Have |
| M2 | Battery saver kills the blocker | A notice, the permission screen, a logged gap. A real-phone test is needed (E6). | Have, P8 |
| M3 | The alarm did not ring | The backstop at 12:00 (G2); the morning flagged (X2). | Have |
| M4 | Notifications permission lost | The permission screen. | P8 |
| M5 | A phone update resets settings | The permission checklist. | P8 |
| M6 | A restart | The boot receiver restores alarms and the meal alarm. | Have |
| M7 | Storage full | X7 failure behaviour. | Have |
| M8 | Wrong time zone | Absolute times; a zone card. | P2 |
| M9 | An update loses data | It cannot: update in place, and a backup file. | P8 |
| M10 | Reinstall | The backup and the permission checklist. | P8 |
| M11 | A new phone | A restore; the tags still work. | P8 |
| M12 | The PC service stops | The tamper log and the wall; it restarts itself (K6). | Have |
| M13 | The browser extension is off | A notice; the hosts file is the other layer. | Have |
| M14 | An NFC tag peels off | A QR code or a tap fallback; the proof drops a tier with a note. | P7 |
| M15 | A sensor reads wrong | Confidence is kept; two weak signals or one strong; a tap fallback. | P7 |
| M16 | GPS wrong at home | Wi-Fi; the wider radius; a retake. | Have |
| M17 | A blank widget | A refresh; it is never the only place. | Have |
| M18 | Spark itself has a hole | The "this doesn't work" button writes a local report and you carry on. | P8 |
| M19 | The database is corrupt | X7; the backup. | Have, P8 |
| M20 | Camera permission revoked | The permission screen. | P8 |

## N. Mind and emotion

| # | Situation | What Spark does | Status |
| --- | --- | --- | --- |
| N1 | Low mood, nothing feels worth it | The mood card; the floors; Lite; the people list. Spark does not push. | P7 |
| N2 | Anxiety before a deadline | A brain dump; a 10 minute plan; the first step is tiny; the plan shows what remains. | P7 |
| N3 | A shame spiral after a missed day | **Fresh start**: re-plan from now; no red; the coach shows facts: "6 of 10 steps done; the next one is water." | P7 |
| N4 | "I'll start on Monday" | The plan starts from now (RN1); the first step is 5 minutes; Monday is not special. | Have, P7 |
| N5 | One hole spoils the whole app | The "this doesn't work" button; the evidence card: "9 of 10 steps worked this week." | P8 |
| N6 | Overwhelm | Lite, or a "what matters today" card of three. | P7 |
| N7 | Time blindness | The minutes-left ring, the leave-by buzz, haptic cues. | Have, P7 |
| N8 | Stimulation seeking | The reward menu rotates; novelty is built in. | P7 |
| N9 | Hyperfocus overrun | The stop haptic and the gate; the water and meal checks. | Have, P7 |
| N10 | A burst of motivation then a crash | The coach never raises your plan after a good day, and caps a weekly rise at about 10%. | P7 |
| N11 | A conflict or rejection | The mood card; the floors; the Lite door. | P7 |
| N12 | Grief | A pause door with no penalty, sleep and meals only; no pressure to explain. | P8 |
| N13 | Guilt about wasted weeks | The weekly review shows recovery, not blame; a catch-up span. | P7 |
| N14 | Redesigning the app instead of doing the work | The parking lot and the freeze. | P8 |
| N15 | Craving a new app | The same. | P8 |

## O. Admin and life

| # | Situation | What Spark does | Status |
| --- | --- | --- | --- |
| O1 | A lost bank card | Banking is never blocked; a task and a deadline; the Emergency door. | Have |
| O2 | A tenancy or landlord issue | A task with a reminder. | P3 |
| O3 | A housing search | Tasks and deadlines. | P3 |
| O4 | CV and applications | The Sunday block (your plan). | Have |
| O5 | A parcel pick-up | An errand. | P3 |
| O6 | Waiting for an ADHD assessment | A "chase" task; an export of your sleep and routine log to bring. | P8 |
| O7 | A medication review | The reminder log printed for your prescriber. | P8 |
