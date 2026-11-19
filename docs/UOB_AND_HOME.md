# The University of Birmingham, and being at home

Part 2 of `docs/MASTER_PLAN.md`. Nothing here is built. It covers the university year as it really is for you, the small things that happen at university, and what Spark does when you are at home and not at lectures.

**Sources and limits.** Dates and procedures below come from the University of Birmingham's own pages, read on 18 November 2026: [undergraduate academic year](https://www.birmingham.ac.uk/undergraduate/courses/academicyear), [key dates for students 2026/27](https://intranet.birmingham.ac.uk/student/dates/index.aspx), [extenuating circumstances](https://intranet.birmingham.ac.uk/as/registry/policy/extcircs/index.aspx) and [reasonable adjustments](https://www.birmingham.ac.uk/study/student-experience/advice-support/disability/reasonable-adjustments). I could not read Reddit (the tools I have are blocked from it) or most Student Room threads (403), so the "what students run into" parts are from the search summaries of those threads, the university pages, and general knowledge of student life, and they are labelled. Details can change; Spark's calendar always wins over this file, and anything about university rules should be confirmed on the university's pages.

## 1. Your year: the real dates

| Date | What | What Spark does |
| --- | --- | --- |
| Mon 28 Sep to Fri 11 Dec 2026 | **Autumn term.** The university says all students are expected to be in attendance throughout term time. | Uni days as now |
| Mon 2 to Fri 6 Nov | Teaching, academic and assessment support week (already past) | An example of a week where lectures may be lighter. A *lecture-free lighter* span (the modifier you asked for) applies. |
| Mon 7 to Fri 11 Dec | Teaching and revision; winter graduations Mon 7 to Tue 15 Dec | Campus is busier. Allow extra travel and library time. |
| **Mon 14 Dec to Fri 8 Jan** | **Winter vacation, 26 days.** | **Home mode.** The biggest change of context in the next six weeks. |
| Mon 11 to Sat 16 Jan 2027 | January assessment period | Exam days and revision. Only if you have January assessments; I do not know. |
| From Mon 18 Jan | Teaching continues, Spring term | Back to uni days |
| Mon 22 to Fri 26 Feb | Teaching, academic and assessment support week | Lecture-free lighter span |
| Mon 29 Mar to Fri 23 Apr | Spring vacation | Home mode |
| Mon 26 Apr; Mon 3 to Fri 7 May | Teaching continues; support week | |
| Mon 10 May to Sat 5 Jun | Main summer assessment period | Exam mode |
| Mon 7 to Fri 25 Jun | Teaching and UoB Xtra; term ends Fri 25 Jun | |

**What this means for the build.** Home mode is needed before **Mon 14 December**, which is 26 days away. It is the first real test of whether Spark works for you outside a lecture timetable. That is why the master plan puts Home and the Places model into an early slice (section 17 of `docs/MASTER_PLAN.md`).

**A date I cannot know:** your January assessments (the seed lists coursework and exam dates as unknown, D7). Spark asks once, in a plain card, and builds revision from whatever you enter.

## 2. The university's own support, as it applies to you

Spark shows these as a short editable list, never as advice. They are real routes and the facts below are from the pages above.

| Route | When | What it is |
| --- | --- | --- |
| **Coursework extension** | *Before* a deadline, for exceptional, unforeseeable circumstances beyond your control; an online form; you do not usually need evidence. You cannot request one for an exam, class test, timed assessment or presentation. | The right route if something is going to stop you submitting |
| **Extenuating circumstances** | *After* the impact, a claim to your School by an advertised deadline, usually with a medical certificate or a letter from the Mental Health and Wellbeing Service. Looked at by a panel; the Board of Examiners may take it into account. | The right route for an exam, or when a deadline has passed |
| **Reasonable Adjustment Plan (RAP)** | Through the Student Disability Service. Available for a condition that meets the legal definition of a disability (a "significant, adverse and long-term effect"). ADHD is named. Adjustments can include extended deadlines, exam rest breaks, extra time, a separate room, and funded specialist mentoring or study skills support. The university does not diagnose ADHD; a GP can refer. Exam adjustment cut-offs are published (Fri 9 Oct 2026 for January 2027 exams; Fri 22 Jan 2027 for the main summer exams); later applications are still processed. | Relevant if your ADHD is diagnosed or being assessed. Contact: disability@contacts.bham.ac.uk, 0121 414 5130. |
| **Your GP, NHS 111, the Mental Health and Wellbeing Service** | When it is about how you are, not only your work | |

I have no knowledge of whether you have a diagnosis or a RAP. If you do, a RAP's extended deadlines are an input Spark can use: you enter the adjusted date and Spark plans to it.

## 3. What happens to students at the University of Birmingham, and what Spark does

Marked **[U]** from the university's pages, **[S]** from forum themes in the search summaries (thread titles and gists, not the threads), and **[G]** from general student experience. These are the ordinary frictions; the full list with solutions is `docs/SITUATIONS.md`.

| Friction | Source | Spark |
| --- | --- | --- |
| A lecture room or building changes | [G], and your own L6 | Asks once for the new place; transit and the arrival geofence update |
| A lecture is cancelled by email or Canvas | [G] | The "something is cancelled" door; the freed time is labelled; study re-places |
| A lecture overruns or you overrun | [G] | Running over (RN2) |
| The recording is late or missing | [G] | The catch-up waits; the note says why; no penalty |
| A fire alarm interrupts | [G] | The Reality report "Time taken"; the lecture's idea step is skipped, no loss |
| Attendance codes at every hour | Your LC1 | The reminder runs each full hour; it is the same at home as in the room |
| Group-project members not responding | [G] | A task row "Chase the group", a deadline, a scheduled block for your own part so the group cannot hold you |
| A supervisor meeting (CS Project) | Your plan | A fixed event with a prep step the day before and a notes step after |
| Office hours | Your plan (Red Engine) | A weekly slot with a prepared question list |
| Canvas or the submission site is down near a deadline | [G] | The plan puts the submit step a day early ("upload check") |
| A deadline at 17:00 that you planned for 16:50 | [G] | The submit step always ends 60 minutes before the deadline |
| Extension needed | [U] | Spark shows the route and the rule: before the deadline, online, usually no evidence, not for exams |
| A bad result | [G] | The test log shows the trend; the plan changes the method, not only the hours (your own seed) |
| Winter graduations week (7 to 15 Dec) | [U] | A note on the Mon 7 Dec week: campus and transport busier, plan buffer |
| Cross-City line or bus disruption, strikes, snow | [G] | "Running late" Reality report, the day reflows; if it is a whole-day disruption the Recovery or Away style preview shows what moves |
| Working from bed | [S] (a University of Bath advice page, and others) | The study seat is a tag at a desk; work in bed is not a place Spark counts |
| Falling behind in first weeks and not telling anyone | [S] | Spark's weekly review shows the gap and offers the extension and wellbeing routes |
| Low motivation that is more than motivation | [S] | The mood check and the "people who can help" card (`docs/COACH_AND_PHONE.md` section 2.4) |

## 4. Day types

A day type is a recipe of the five effects (`MASTER_PLAN.md` section 6). It is **proposed automatically** from the calendar, the place and your plans, and confirmed once, so you never assemble a day.

| Day type | Proposed when | What changes | What stays |
| --- | --- | --- | --- |
| **Uni day** | Lectures on the calendar and you wake in Birmingham | Your week plan, with walking, lectures, VRNP cycles | Everything |
| **Library day** | A library period or a study day planned there | Study blocks move to the library, transit recalculated (C2), library opening hours respected (O11) | |
| **Home study day** | A weekday in term break, or a day with no lectures | Lectures become study blocks (the lecture-free lighter span), revision and projects; the home place's routines | The floors, meals, sleep |
| **Rest day** | A planned weekend off (H5) or Saturday in your plan | No study; movement and free time; the floors | Bible, meals, sleep, the safety floor (C5) |
| **Travel day** | Place changes (train home) | A transit block with a light plan: audio Bible, a reading block on the train, a reset on arrival | Wake, meals, sleep |
| **Exam day** | An exam on the calendar | Wake on time, the Short routine tier, an arrival buffer, no new study within 2 hours of the exam, a light last review | Sleep the night before |
| **Deadline day** | A deadline within 24 hours | A submit step 60 minutes early, an "upload check" the day before | |
| **Match day** | Football in the calendar | Kit, travel, warm-up, post-match meal | |
| **Church day** | Sunday in your plan | The church bag, the service, and the rest day around it | |
| **Guests day** | The Guests modifier | Prep before, tidy after (`MASTER_PLAN.md`) | |
| **Ill day** | The verified ill door or your tap | Floor tiers everywhere; the Buffer penalties pause (X3) | Meals, medication reminders, sleep |
| **Lite day** | The Lite door | Three things | Sleep, meals, one study block |

## 5. At home (vacations and weekends away)

You said: "when I go home, what's going to happen? I'm not going to be at lectures." Spark treats home as a place with its own context and day types, not as a missing timetable.

### 5.1 What changes at home

| Area | At the flat | At the family home |
| --- | --- | --- |
| **Cooking and meals** | You cook, meals are timed to the plan | Shared meals. Spark asks for the household's meal times once; meals become fixed events, not cooking steps |
| **Sleep and noise** | Alone | Others' timings. Lights-out and wake are set by you; the wind-down adapts to a shared house (TV, noise) with a headphone option |
| **Study place** | The desk and the seat tag | A portable seat tag and a named place (a dining table, a desk) that is not the bed |
| **Phone gating** | Work blocks and the night rule | The same, with a **family-time** window where the phone is open and a single "family helping" Reality tap |
| **Lectures** | VRNP cycles | None: the cycles become revision and project blocks |
| **Church and Bible** | Your usual | Whatever the family routine is; the Bible routine keeps its tier at the same clock time |
| **Football** | Training and matches | Whatever you can do: a run, a kickabout. The floor is 20 minutes of movement. |
| **Shopping and chores** | Aldi, bins, laundry | Family routines; Spark drops the flat's chores and keeps one "help at home" block if you want it |
| **MyTutor** | Paid sessions | Kept. Online sessions need a quiet place, and the Wi-Fi at the family home is a place setting. |
| **Wi-Fi and signal** | Known | A second Wi-Fi name; the place check uses it |

### 5.2 The home study day

| Time (adjustable) | Block |
| --- | --- |
| Wake anchor, within an hour of the term time wake | Wake, water, light |
| +0:30 | Shower |
| +0:50 | Bible (Full) |
| +1:30 | Breakfast, usually shared |
| +2:00 | Study block 1 (2 hours, the hardest thing) |
| Midday | Walk or movement (floor 20 minutes) |
| Lunch | Shared |
| Afternoon | Study block 2 (90 minutes), a reset, block 3 (60 minutes, lighter) |
| Evening | Family time, protected |
| Wind-down | As the atlas |

### 5.3 The winter vacation arc (Mon 14 Dec to Fri 8 Jan)

A recommended shape you can edit in the Modify screen's custom composer. It is a recipe, not a rule.

| Week | Purpose | Study load | Notes |
| --- | --- | --- | --- |
| 14 to 20 Dec | Land and rest | About 10 hours | Sleep reset, the floors, catch-up of any real backlog |
| 21 Dec to 1 Jan | Holiday | About 4 to 8 hours | Christmas and New Year as fixed events; light daily floor so the habit never stops |
| 2 to 8 Jan | Ramp up | About 20 hours | Revision cycles, past papers, the test log |
| 9 to 10 Jan | Final prep | Light | Early nights and the exam-day routine rehearsed |
| 11 to 16 Jan | January assessments, if you have them | Exam days | |

This only applies if you have January assessments. If not, the same weeks go to the CS Project and Battl.io, and the arc is lighter.

### 5.4 Leaving and coming back

| Moment | What Spark does |
| --- | --- |
| **The night before leaving** | A leaving checklist: charger, laptop, Bible, kit, medication, student card, travel card, tags for the home place; a travel plan (train times by you); a lights-out suitable for an early train |
| **The travel day** | The Travel day type: a transit block, audio Bible, a light reading block, a reset on arrival |
| **Arrival at the family home** | The home place's first-run: Wi-Fi, a seat tag, a charger tag, a reference photo of the setup spot; the observe phase for new routines |
| **The week before returning** | A gradual sleep shift back to the term anchor (`COACH_AND_PHONE.md` section 5.3), starting 4 to 5 days before |
| **Back at the flat** | A re-entry routine: restock (the shopping list), the setup reference photos retaken, the week's blocks re-laid. The first uni day is Short for the routine tier if you want. |

### 5.5 Weekends at home during term

The same, for one or two days, with a **reduced-obligation** option because you will not be doing a full study load: a Home study day of three blocks, the floors, and the Sunday return.

## 6. Sleeping difficulties at university: the specifics

The sleep programme is `docs/COACH_AND_PHONE.md` section 5. The university-specific points:

1. **9am lectures and a late body clock.** Spark's fixed wake anchor and the gradual shift are the main tools. When a lecture is worth it and the night was bad, the routines drop a tier; the recording and the catch-up in the Buffer are the safety net (Z4, D5). It does not ask you to decide at 07:30.
2. **Flat and neighbour noise.** The awake list includes a headphone option. There is nothing for Spark to track here.
3. **A society night, a Guild event or a night out.** Plan it in advance as an Event with an Out anchor. The next day's tier is lowered automatically.
4. **Exams and late revision.** The cutoff is firm the night before an exam (a stricter 8 hours rule, SA3, with N7 hard stop), because that is the night that matters most.
5. **Support.** If sleep stays hard for more than a few weeks, your GP is the route, and the university's wellbeing services are the second. Spark prints the sleep log for the appointment.
