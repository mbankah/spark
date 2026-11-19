# Hole register (Phase 0, 19 November 2026)

Each row was read in the code or the build files today. "Closed by" names the phase and rule that fixes it. Rows are updated as they close.

Baseline: `:core:test` passes (the suite from the last session, 840 tests). The emulator has no Spark installed, so no screenshots were taken; they are taken in phase 6 after the build is installed.

| # | Finding | Evidence | Status | Closed by |
| --- | --- | --- | --- | --- |
| H1 | The Modify screen refuses a non-free block before 06:00 or after 23:00 ("would be in the night"). Tonight's study 00:00 to 02:30 cannot be entered. | `Modifiers.kt` `checkPlan`, the `startMin < 6 * 60 \|\| endMin > 23 * 60` test | Open | Phase 2 and 3, SA2 |
| H2 | The import planner does not use the reflow engine. | `ImportV2.kt` `reflowEvents` calls `replanDay`; `app` passes `reflow = true` | **Closed** | Phase 6, IX1 |
| H3 | Imports have no merge or replace choice, allowance or cooldown. | `ImportScreen.kt`, `IM6` | Open | Phase 2 and 6, CE, IX |
| H4 | `android:allowBackup="false"` and no export of data. Uninstalling erases everything. | `AndroidManifest.xml` line 34 | Open | Phase 8, CT1 to CT3 |
| H5 | Release builds are signed with the debug key. | `app/build.gradle.kts` line 19 | Open | Phase 8, CT3 |
| H6 | The home page is a stack of about 16 conditional cards then five text buttons. Now sits under the Buffer dial. | `HomeScreen.kt` `HomePage` | Open | Phase 7, HS |
| H7 | A Change button sits on the home page. | `HomeScreen.kt` | Open | Phase 4, HS5 |
| H8 | Phone browsers: addresses are read from the bar of 9 named browsers, so a blocked site is closed there. A browser not on the list, and an in-app web view, are not covered. | `Domains.kt` `BROWSERS`, `K8`, `K10` | **Mostly closed already.** The residue is the unlisted browsers. | Phase 9 probe |
| H9 | One decision function exists for blocking (`decide`), but the morning phase, the allowance, earned access, the night window and quiet mode each reach it through separate inputs and a separate quiet-mode function. There is no place for a WhatsApp cap, a door pool or the new reasons. | `Blocking.kt` `decide`, `BlockingState.quietWindow` | Open | Phase 4, GT1 |
| H10 | Proof facts live in separate stores: verified work blocks, sleep nights, the meal log. | `BlockRepo.verifiedKeys`, `nights()`, `MealLog` | Open | Phase 2, FB1 |
| H11 | Doors (days off, override, ill day, away, recovery) each keep their own price; none shares a cap. | `DaysOff.kt`, `Night.kt`, `Modifiers.kt` | Open | Phase 2, DP2 |
| H12 | Away does not change the zone (fixed London) and does not exempt place checks. | Prior session notes; `Modifiers.kt` Away | Open | Phase 3, PL1 to PL3 |
| H13 | A backlog cannot be spread over a catch-up span; D5 puts the deadline at the end of the next day. | Prior session notes | Open | Phase 3, BK1 to BK5 |
| H14 | There is no Fact bus, context record (two homes), routine and step model, or coach. | Search of `core/src/main/kotlin/efa` | Open | Phases 2, 3, 7 |
| H15 | The never-blocked tier in the seed has WhatsApp open all the time. | `seed/...md` Blocking tiers | Decided 19 Nov: cap it | Phase 4, GT1, Q-G141 |

## Status on 19 November 2026, after the build

| # | Now | How |
| --- | --- | --- |
| H1 | **Closed** | The night preset (SA1 to SA5) moves lights-out and wake; the guard rail reads them (SA2). Tonight's plan is entered and previewed in core tests and a device test. |
| H2 | **Closed** | A file's events go through `replanDay` (IX1); a file can also carry modifiers, nights and keeps (IX6). |
| H3 | **Closed for imports** | Merge or replace in the app (IX2), the economy for imports (CE3 to CE6, CE9), a restore by 24 hour undo (CE8). Moves and loosening elsewhere are not counted yet. |
| H4 | **Closed** | A backup file in Downloads/Spark, nightly and on demand, and a restore with a safety copy (CT1, CT2). Android Auto Backup stays off (CT5). |
| H5 | Open | Release signing is still the debug key (CT3 not built). |
| H6 | **Part closed** | The Now card is the hero, the Buffer sits under it, and a running block, the wind-down and the night hide the secondary cards (HS1, HS2). Dock icons are not built (HS6). |
| H7 | **Closed** | The Change button is gone; reality reports are on the Now card and in the evening (HM1, MX4). |
| H8 | Open | Unlisted browsers and in-app web views are still not read. Not probed on a phone. |
| H9 | **Part closed** | The WhatsApp cap is in the one decision function (GT2). Other doors do not feed the gate yet (GT1). |
| H10 | Open | The fact bus exists in core and is not written to by the app (FB1 to FB7). |
| H11 | **Part closed** | One door pool exists; the WhatsApp wall uses it. Day off, override and the others still keep their own prices (DP3, DP4). |
| H12 | **Part closed** | Away has a zone setting and place-check exemption in core (PL1 to PL4). The alarm scheduler and the verifier do not read them yet. |
| H13 | **Closed** | Inside a catch-up span the catch-up is due at the span's end, the preview says the work, the room and the shortfall, and the end of the span gives D5 back (BK1 to BK5). |
| H14 | **Part closed** | Routines, places, day types, the coach's small pieces exist in core. No coach runs the steps (CO1 to CO3), no context record (CX1). |
| H15 | **Closed** | WhatsApp opens 10 minutes at a time with a 30 minute cooldown; the wall ends it; a call is never cut off. |
