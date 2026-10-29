# Section 25 against the rest of the ledger

Checked on 29 October 2026, before and after building. Each row is something section 25 could have broken, the rule it is held against, how the build keeps it, and the test that fails if it stops being kept. Test names start with the ledger IDs they prove, so `docs/TRACEABILITY.md` links them.

## Did the request fit the app?

Yes, with four limits the ledger itself sets. They are built in and not options.

| What was asked | Fits because | The limit that comes with it |
| --- | --- | --- |
| Assignment weeks and catch-up weeks | C1 already lists a planned "focus (catch-up)" period | A week only orders work and labels home (PW2). A day off and a library week stay planned in the app, where a day off goes through its wall (H1). |
| Import the timetable from a CSV | D3 says a timetable feed can come later, and section 3 names the timetable as a source | A file that loosens anything needs the typed wall (IM6). |
| An AI can build the file | The format is one written table (`docs/TIMETABLE_CSV.md`), also copied by a button | The reader checks the whole file and rejects any error (IM4), so an AI mistake cannot reach the schedule. |
| Missed things move to buffer areas and split | D5, Z4 and H6 already bring missed work back, and F2 and F3 already turn free time into work | The lost time stays lost (P8, B6, W2). The 6 protected free hours (G3) and everything in section 10's "never squeezed" list are never touched. If it all still does not fit, the Buffer says NEGATIVE with the working (RS4). Nothing guarantees "everything gets done", and the screen never pretends it does. |
| Available on all platforms | The format is platform-neutral | Only Android is built. The web app and the Windows service read the same format and test files when they exist (Q-G47). |

## Held against the existing rules

| Could break | Rule | How it is kept | Test |
| --- | --- | --- | --- |
| A file removes study time or makes a day off | P8, H1, C5 | Study, code, meal, sleep, routine, free, planning and chore are not event types. Day off and library are refused in a file. Cancel is limited to fixed commitments and walks. | `IM6 a file cannot create a study block...`, `IM6 a file cannot shorten or remove a study block...`, `H1 no file can make a day off...` |
| A file moves the night cutoff or the wake time | N1, F1 | Routines, meals and sleep cannot be added or cancelled, so the first fixed commitment and the wake time never change. | `N1 a file cannot add or cancel a routine...`, `section 10 no file and no conversion changes sleep, meals, routines or the wake time on any day` |
| A loosening import slips through without the wall | M7, K2, V4 | Needs the wall exactly when a file cancels or cuts into a study or code block, checked over 300 random files. Removing an import also needs it. | `IM6 across many random files the wall is needed exactly when...`, `IM6_a_file_that_cancels_a_lecture_is_applied_only_after_the_wall`, `IM12_removing_an_import_needs_the_wall...` |
| Cancelling a lecture removes its study cycle | D1, D5 | The cycle comes from the study block, which a cancel cannot touch. | `D5 IM6 cancelling a lecture does not remove its study block...` |
| A new lecture has no study cycle | D1 | An added lecture or tutorial creates its cycle unless the file says none. | `IM7`, `IM6 moving a lecture is one file...` |
| An imported event behaves like the wrong type | K3, Y1, L1 | Blocking and proof follow the slot type, so a lecture leaves the phone open and needs attendance, a walk closes it, a converted block closes it as work. | `K3 an imported lecture leaves the phone open...`, `Y1 L1 an imported slot needs the proof of its type` |
| A hostile or broken file corrupts the schedule | X7, IM4 | Whole-file check, 1 MB and 2000 rows, text only (a leading = is never calculated), reading never throws on random bytes, and applying is one transaction. | `X7 IM4 reading never throws...`, `IM9`, `IM11_a_failure_part_way_leaves_nothing_behind` |
| A file arrives from another app and changes things on its own | A9, LA8 | Share and Open with go to a preview only. The filters are not browsable, so a web link cannot send a file. | `IM5_IM10_shared_text_from_an_AI_app_is_previewed_and_nothing_is_stored`, `IM10_a_web_link_cannot_send_a_file` |
| The same file twice doubles the work | IM8 | Every row has a fingerprint of its meaning, not its spelling. | `IM8` tests (core, storage and shared-file), checked over 100 random files |
| Missed work comes back forgiving the lost time | P8, B6, W2 | The catch-up adds work to the Buffer and takes no capacity away. | `RS7 W2 B6 a catch-up adds work and takes no capacity away...`, `W2_RS1_bringing_missed_work_back_adds_to_the_Buffer_working_and_forgives_nothing` |
| Missed work comes back twice, or as a backlog from before the app was used | RS5 | One row per missed block, and nothing before first use. | `RS1_RS5_a_missed_lecture_comes_back_once...`, `RS1_installing_the_app_does_not_invent_a_backlog` |
| Missed work comes back while an honest backfill is still possible | B7 | Only after the next morning's wake. | `RS1 B7 nothing comes back while the backfill window is still open` |
| Free time turns into work today, or too much of it | F2, F3, G3 | From tomorrow, whole slots, only under the margin, earliest first, never protected, at least 6 hours a week always left. | `F2 free time turns into work only from tomorrow...`, `G3 over a full term of weeks the protected free time is never converted...` |
| Placing work overfills a block or loses minutes | RS2 | Every task\'s minutes are placed or listed as not fitting, none is lost, no block is over its length, nothing after its deadline (random tasks). | `RS2 placing tasks never gives two tasks the same minutes...` |
| Work is ticked off without proof | P8, W2 | Only a verified study or code block credits tasks, earliest deadline first. | `RS6_a_verified_study_block_reduces_the_open_tasks_and_a_cycle_does_not` |
| The clock change on 25 October 2026 | X4 | Slots are wall-clock minutes. A time that does not exist is an error and a time that happens twice is a warning. | `X4 IM4 a time that does not exist...`, `X4 an imported event and a conversion either side of the clock change...` |
| A new database version loses data | X4 | Migration 6 to 7 keeps every task and adds the calendar tables. | `X4_migration_6_to_7_keeps_tasks_and_adds_the_calendar_tables` |
| The blocker stops because of the new work | LA1, LA3 | Automation runs inside the blocker\'s refresh and a failure there is ignored. | `RS1_RS5_each_refresh_of_the_blocker_looks_for_missed_work...`, `RS3_RS8_a_tight_Buffer_turns_free_time_into_work_and_the_blocker_then_blocks_it_as_work` |

## What this check could not cover

- Nothing here has run on a real phone. Share and Open with depend on which apps your phone lists them in.
- The first time F3 exists, the Buffer counts converted free time. If it converts more or less than feels right, that is a default to change (Q-G41, Q-G42).
- Real timetables will contain cases the tests do not. The reader rejects what it does not understand instead of guessing, so the failure is a clear error and not a wrong schedule.
