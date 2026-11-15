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

- **Generalise lectures on/off (DONE).** Assignment, Focus and Catch-up now each have a "Lectures during the period" setting (running or none). None cancels lectures and tutorials in the period under the plan, so sessions can use the time and nothing comes back as catch-up. It needs the wall, like lecture-free, because it loosens. Period off keeps auto/running/none; Lecture-free stays as the preset that also lightens work. Was: Lecture-free and period-off now both carry a lecture setting. Cleaner would be one "term state" (lectures running or not) that any preset can use, so a hybrid week can mix lecture-free with assignments, revision, catch-up or focus without a separate preset. Not done: it changes the design.
- **Period off wall:** +25 characters per day beyond two is still an assumption.
- **Admin jobs typed "chore" ask for a photo** (Y1). Jobs like bins or dishes may be better as "errand" (no photo). Not changed.

## Not checked yet

- Applying the same modifier twice, and overlapping period-offs sharing day-off rows on removal.
- Admin placement shifting as "today" moves.
- Anything on a real phone (E1-E64 in docs/DEVICE_TESTS.md).

## Rerun (system wide)

Core 693 tests, Windows service 46 tests, app and device tests compile, traceability 214 of 277 rows with tests (the rest need the phone or a build not yet made).

Fixed in the rerun:
3. **The same modifier could be applied twice, and two periods over the same days shared their days off,** so removing one took the other's away. A new instance is now refused if it is identical to an active one, a period of the same kind covers some of the same days (period off also against lecture-free), or an assignment or project with the same title and due date is already planned. Different assignments can still be added.
4. **Admin placement could shift as the week went on:** its weekly free-time accounting started from today. It now starts from the week's Monday, so a day's jobs are the same whichever day you look from.

Still open: Admin chores ask for a photo (Y1 says chores do; kept), the period-off wall rule (+25 per day beyond two), and everything on a real phone (E1-E64). The release APK has still never been launched.

## Second rerun: stress tests instead of hand-picked cases

The first two passes found problems one at a time because I only tested what I thought of. This pass added randomised tests (FuzzProbeTest): 2000 random presets with random settings and dates, 400 random stacks of two to four presets, and 300 random Admin calendars, each checked against the rulebook's invariants (no new overlaps, protected blocks kept, nothing on a day off, lectures none means no lectures, wall needed when loosening, hours asked equal hours planned or the shortfall is shown, alarms off on days off and a backstop on every other day). Seeds are fixed so a failure repeats. Coverage was measured (about 250 non-empty plans per preset), not assumed.

Found and fixed:
5. **The planner could produce a session shorter than 15 minutes,** which the guard rail then refused, so about 1 in 12 assignments could not be applied through no fault of the user. It now never plans a piece under 15 minutes and never leaves a remainder under it.
6. **Hours that did not fit were not shown.** An assignment could be applied covering 17 of 25 hours with nothing on screen saying so. The preview now says "Not fully planned: ... is Nh short".
7. **Nonsense session settings were accepted** (shortest session longer than the longest, or longer than the daily cap). They are now refused with a reason.
8. **Removing a period off refunded the use in the 30-day tally** (H1), because it deleted the day-off rows and nothing else in the app deletes days off. The days now stay as "cancelled": off the calendar, still counted. Planning the same dates again replaces that date's earlier record. The old device test had written the refund in as expected behaviour; it is rewritten.
9. **The calendar had no fallback** if the modifier or Admin layer failed (MG17). It now falls back to the base calendar.

No violations were found in the stacking, Admin or alarm invariants.

Results: core 700+ tests, 39 device tests on the emulator (modifier, days off, library), Windows 46, traceability 214+/277.

Still open: the design questions (Admin chore photos, wall +25 per day), expired modifiers still listed as active, and everything on a real phone.

## Third rerun: what happens after a modifier is applied

The earlier passes all tested "apply a modifier now". This pass tested time and change: the calendar changing under an applied modifier, awkward text, daylight-saving dates, Recommended's own output, and long random sequences against a real database.

Found and fixed:
10. **A modifier's stored sessions were frozen when applied.** A day off planned afterwards, or a file imported afterwards, left the sessions sitting on the day off (MG12) or on top of the new event (MG4). The library periods avoid this by being worked out each time (C2), but a modifier cannot be recompiled safely, because the compile does not know how much work was already done. So the stored sessions are now checked against the calendar each time it is read (`reconcile`): one that now sits on a day off or on a real event is left out, its work stays due and counted in the Buffer, and the Modify screen says how many were left out. Cuts into work blocks, which sessions are meant to make, are not treated as clashes. A stress check confirms reconcile never drops what compile just produced.
11. **A carriage return in a title** (pasted text) was not escaped, so it split a stored line and could make a modifier unreadable, which would leave its days off behind on removal. Escaped now; all awkward characters round-trip (| = tab newline backslash emoji, 300 characters).

Checked, no fault found: Recommended's output is always valid and applicable for every preset and length; plans across both 2027 clock changes (3000 random plans, 600 stacks, 28,735 Admin placements); 25 random apply, remove and day-off sequences on a real database (87 applies, 87 removals): the tally never fell, nothing was left behind, an ordinary day off was never taken by removing a modifier, and with everything removed the calendar was the original.

Honest limit: the tests prove the rules hold in combination and over time. They cannot show that the plans feel right across a real week.
