# Architecture check

Five passes over the app against DECISIONS_LEDGER.md and BUILD_PROMPT.md. The rulebook was not changed. Nothing here is proven on a real phone.

## Fixed

1. **A holiday week off brought lectures back.** Period off treated every skipped lecture and study cycle as work due the next working day, even when lectures are not running. Period off now has a "Lectures during the period" setting: auto (none for 7 days or more), running, none. With none, nothing comes back and the preview says so. A short period in term time behaves as before (C4).
2. **Removing a modifier left the alarm stale.** Removing a period off now refreshes alarms as well as blocking, so the G2 backstop returns on days that are no longer days off.

Tests: ArchitectureProbeTest (5), core suite green.

## Makes sense, no change

- Layers (pattern, overlay, day-off markers, modifier slots, Admin placement) stay in one order, so a modifier cannot reach under a rule: day off still removes work, and modifier slots are the only exception, by MOD_NOTE.
- Lecture-free plus an assignment compose: lectures stay cancelled and the assignment sessions plan around the lighter days.
- Day-off wall rules (C1, H1-H5) still apply to period off; it is one use of the 30-day tally.

## Decisions for you

- **Generalise lectures on/off.** Lecture-free and period-off now both carry a lecture setting. Cleaner would be one "term state" (lectures running or not) that any preset can use, so a hybrid week can mix lecture-free with assignments, revision, catch-up or focus without a separate preset. Not done: it changes the design.
- **Period off wall:** +25 characters per day beyond two is still an assumption.
- **Admin jobs typed "chore" ask for a photo** (Y1). Jobs like bins or dishes may be better as "errand" (no photo). Not changed.

## Not checked yet

- Applying the same modifier twice, and overlapping period-offs sharing day-off rows on removal.
- Admin placement shifting as "today" moves.
- Anything on a real phone (E1-E64 in docs/DEVICE_TESTS.md).
