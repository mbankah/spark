# Coherence and bypass: do the systems fit, and can they be gone round

Part 2 of `docs/MASTER_PLAN.md`. Nothing here is built. It answers your points: "everything must work together", "they can be bypassed", "you cannot be illogical with this app".

**How honest this is.** The first table lists where the same question is answered in more than one place, working from the ledger. I have not yet traced each one in the code; Phase 0 does that and turns this list into a verified register. Anything I could not check is labelled.

## 1. The one question that finds incoherence

If the same question is answered in two places, they can disagree. A loophole is nearly always a disagreement.

| Question | Where the ledger answers it today | One owner in the plan |
| --- | --- | --- |
| Is the phone allowed to open X right now? | Work blocks (K1), morning gate (M6, M8), night rule (N2), transitions (T3), quiet mode (QM1), earned access (R2), free slots (F1), override (N3), essentials (U6), meal and sleep never-blocked lists (X1, LA1) | **Gate** (reads day model, context, facts, doors) |
| Was this done? | Photo match (M2), block proof (G1), attendance photo (L1), shutdown photo (S1), meal check (MC1), chore photo, sleep sensors (S2) | **Fact Bus** |
| Am I allowed less today? | Day off (H1), illness (X3, H4), recovery, lite mode, away, library period (C2) | **Door Pool** |
| What are today's edges? | Wake target (WT1), alarm (Z1), night cutoff (N1), meal windows, reflow bounds, the guard rail | **Anchors** (SA1) |
| Where am I? | Home radius (L3), venues (L2), away | **Context** |
| What do I owe? | Buffer (section 4), study cycles (D1), catch-up (D5, Z4), deadlines (D2) | **Obligation ledger** (the Buffer's inputs, one list) |
| What is the tier of this routine today? | Meal windows, squeeze order (section 10), the Bible rule | **Yield policy** (YP1) picks the tier |

## 2. Precedence

When two rules meet, the higher one wins. This is the order the gate, the yield policy and the door pool all use.

1. **Safety:** emergency, calls, medication reminders, banking and authenticator (X1, LA1).
2. **Sleep protection:** the 8 hour rule, the cutoff, the hard stop (N1, N7, SA3).
3. **Hard commitments:** hard alarms, exams, paid sessions (D4).
4. **Routine floors:** the Bible floor, meals, water, the reset after a block.
5. **Study and work.**
6. **Free time:** protected first, the rest yields first (YP1).
7. **Rewards and extras.**

## 3. Forty collisions and who wins

| # | Collision | Winner and why |
| --- | --- | --- |
| 1 | The alarm against an automatic ill day (X3) | The alarm still rings; the ill day lowers the tier. Sleep and wake stay intact. |
| 2 | A hard alarm against a chosen wake time | The hard alarm (D4, WT1). |
| 3 | A late-night anchor against a lecture next morning | The anchor is refused, naming the lecture (SA5). |
| 4 | "I'm out" against the night cutoff | Out wins only if you are not at the home point (EM2); at home the night rule stands. |
| 5 | Lite mode against blocking | Blocking stays. Lite removes obligations, never gates. |
| 6 | A take-over import against a hard alarm | The hard alarm stays (IX4). |
| 7 | A take-over against football marked keep | Football stays. |
| 8 | The Bible floor against a late start | The Bible is first, shortened to its tier. |
| 9 | The Bible floor against a lecture in 30 minutes | Floor (10 minutes); breakfast is cut first. |
| 10 | A meal against a lecture | The meal slides inside its window; dropped last (YP1). |
| 11 | Showering while the pasta cooks against safety | Safety: a timer, the hob low, the door open; the coach says so. |
| 12 | A skipped photo against the reward | No reward until proven. |
| 13 | A camera failure against the gate | The fallback ladder (M10): not your fault, no loss. |
| 14 | An override against the hard stop | The hard stop (N7). |
| 15 | Break glass against the hard stop | The hard stop. Break glass never touches sleep protection. |
| 16 | The Emergency door against a cooldown | The Emergency door. |
| 17 | A cooldown against a Reality report | The Reality report. |
| 18 | "Running over" against lights-out | Lights-out. Work yields (YP1). |
| 19 | An ill day plus Out plus lite mode together | One pool; the sum is capped. |
| 20 | A clock change against an alarm | Absolute time; a 23 or 25 hour day (SA6). |
| 21 | Away against the home geofence | The exemption shows; it is never silent (PL2). |
| 22 | A take-over that makes the Buffer negative | Refused with the numbers, or the wall (H2). |
| 23 | A catch-up span against an exam | The exam (BK3). |
| 24 | Guests against the study floor | The event wins; the study floor shrinks; sleep stays. |
| 25 | Guests staying against docking the phone | The phone docks in your own room. The step is the same. |
| 26 | Church against a library day | Church (fixed commitment). |
| 27 | Football against catch-up | Football (RF2). |
| 28 | The meal check against a study block | The check waits until the block ends. |
| 29 | Quiet mode against the authenticator | The authenticator is never held (QM1, LA1). |
| 30 | A call at night against the night rule | The call (X1). |
| 31 | A wrong character in the wall against the string | Up to 3 forgiven, the fourth restarts (WS1). |
| 32 | Lockdown against a real illness | The verified ill door (3 hours of sleep or less), or break glass for non-sleep matters; the real-world route is an extension or extenuating circumstances. |
| 33 | A zone change against an alarm | Absolute time (PL3). |
| 34 | The observe phase against a safety rule | The safety rule runs. |
| 35 | Scaffold level "Yours" against a gate step | The gate stays. "Yours" only retires prompts. |
| 36 | Wi-Fi against GPS for place | Agree: strong. Disagree: lower confidence; a tier 2 proof needs one strong or two weak. |
| 37 | An NFC tag read away from its place | Flagged by the geofence; accepted at lower confidence only. |
| 38 | The parking lot against the plan | No effect. An idea changes nothing until you turn it into a change. |
| 39 | A restore against newer data | Asked; never overwritten silently. |
| 40 | An update against your data | Installs over; data and permissions kept (CT3). |

Each row becomes a core test, named by its rule ID where one exists, and a scenario in the bank.

## 4. The doors

A **door** is any feature that relaxes a rule. Listing them is how you see how they combine.

| Door | Who opens it | Cost today | Draws from the pool | Verified by | Abuse pattern | Closure |
| --- | --- | --- | --- | --- | --- | --- |
| Day off (H1) | You, planned | Wall, longer with each use in 30 days | Obligation minutes | None | Many days off | The pool; the 30 day tally |
| Automatic ill day (X3, H4) | Sleep of 3 hours or less | Free, counts in the tally | Obligation minutes, at a discount | Sleep facts | Staying awake all night on purpose | It needs 3 hours or less measured; the tally rises |
| Override (N3) | You | Wall, 30 or 45 minutes | Gate minutes | None | Repeated overrides | The string lengthens (N4); N7 hard stop |
| Late-night anchor (SA) | You | Free if the 8 hours hold | None | The 8 hour sum | Moving lights-out and wake in lockstep every night | Wake drifts return by 15 minutes a day (Z3); the weekly review shows the drift |
| Out (EM2) | You | Free | Gate minutes at half price | Location: not at home | Claiming "out" at home | Location check; at home it fails |
| Lite mode (LT1) | You or Spark after 3 missed blocks | Free for the first, then the wall | Obligation minutes | Missed blocks or mood | Using it as a standing rest | Days per month capped; counts in the pool |
| Recovery (RC) | You | A preview with numbers | Obligation minutes | None | The same as a day off | The pool |
| Away (AW) | You | A preview | Gate minutes (attendance exempt) | A trip date | Away at home | Location and travel facts |
| Take-over import (IX) | You | Move or Loosen class | Obligation minutes if it drops work | The preview | Using a file to delete study | The wall; the pool; restore point |
| Cancel (time freed) | You | Reality class | Obligation minutes if the free time is not re-used | The calendar | Cancelling real work | Only the block types the engine lists as cancellable, never study or sleep |
| Break glass | You | The longest string, once in 30 days | One lockdown or cooldown | None | Using it every month | Once in 30 days; never touches sleep |
| Block list or essentials edit (M7) | You | The wall; a 5 minute window to add essentials | Gate minutes | None | Adding social to essentials | The wall |
| Economy edit (CE10) | You | 24 hour delay | None | None | Loosening at 2am | The delay |
| Reality reports | You | Free | None | The clock | "Running over" every block to push work later | Lights-out and the yield policy; the pattern shows in the weekly review |
| Emergency door | You | Free, logged | None | None | Misusing it for social | It opens only the emergency list, nothing else |

### 4.1 The pool, in one paragraph

One **Door Pool** in two units, obligation minutes removed and gate minutes lifted, counted over a rolling 7 days. Each door has a price. **Truthful, verifiable doors are cheap and unverifiable doors are dear**: an ill day backed by a 3 hour night costs less than a planned day off; "out" with the phone away from home costs half an override. The first half of the pool is free of extra cost; the second half raises the wall as N4 and H1 do now; at the cap, doors lock except the Emergency door, a verified ill day, and break glass. The numbers are Defaults and are tuned in the observe phase; I will show them in the meter. The key property is **no combination of doors can exceed the cap**, and a property test tries every combination in random order.

## 5. The bypass register

Ways round the app. For each: whether it can be prevented, made costly, detected, or only accepted. I list what Spark does, and the honest limit.

| # | Route | Closure | What Spark does | Honest limit |
| --- | --- | --- | --- | --- |
| 1 | Uninstall Spark | Cannot prevent | Tamper counts (K6, X5); the wall for stopping the service; the next start shows the gap in "hours protection was off"; backups mean you do not lose data | Android lets the owner uninstall. |
| 2 | Force stop | Detect | A loud "blocking is off" notice and a start from boot; the gap is logged | Cannot be prevented. |
| 3 | Turn off the accessibility service | Detect and cost | A persistent notice and a gate back at the next open of Spark; logged | Cannot be prevented. |
| 4 | Revoke a permission (usage access, notifications, exact alarm, overlay, location) | Detect | A permissions screen, a daily check, a notice | The OS decides. |
| 5 | Disable the app in Settings | Detect | As 3 | |
| 6 | Safe mode | Accept | The gap is logged | Third-party apps are off in Safe mode. |
| 7 | Factory reset | Accept | Backup restores the plan | |
| 8 | Another device (PC, tablet, second phone) | Partly | PC: the hosts file and the extension (K4, K5). Phone to phone: accepted. | Another device is outside the phone. |
| 9 | Browser versions of blocked apps (instagram.com in Chrome) | **Check in Phase 0** | The ledger blocks the apps. I do not yet know whether the phone browser is covered. If not, block the domains in browsers during blocks (accessibility reads the address) or close the browser except an allow list. | A gap I must verify, not assume. |
| 10 | The Share sheet and links opening a blocked app | Check | The accessibility service closes a blocked app whatever started it | |
| 11 | A reply from a notification shade | Check | Held notifications (QM1, U8) already remove the shade for blocked apps | |
| 12 | The assistant ("open Instagram") | Check | The blocked app closes when it opens | |
| 13 | A second user, work profile or cloned app | Accept and detect | Package list checked; a cloned app of a blocked one is added to the block | OS features differ. |
| 14 | Changing the clock | Prevent | Elapsed real time and network time (G10) | |
| 15 | Airplane mode | Prevent | The rules run offline; the clock is monotonic | |
| 16 | Battery saver killing the service | Detect | The permission checks and the notice (E6) | OEMs differ; real phone test. |
| 17 | The wall: paste, autofill, voice typing, a keyboard macro | Prevent | The field refuses (WS1), accessibility text-injection is detected | |
| 18 | A photo of a photo; a gallery photo | Make costly | Window and place and match; the in-app camera only (L1) | A good fake costs more than the task. |
| 19 | Shaking the phone for steps | Make costly | Photo is the main proof (M3) | |
| 20 | A moved NFC tag | Detect | The geofence and place check (collision 37) | A determined person can fake a tag. It costs more than doing the thing. |
| 21 | Opening doors in combination | Prevent | The Door Pool and its property test | |
| 22 | Reality reports to push work later | Prevent | Lights-out and the yield policy | |
| 23 | Importing a file to delete work | Prevent | The wall and the pool; a restore point shows it | |
| 24 | Editing the economy at 2am | Prevent | 24 hour delay | |
| 25 | Adding a social app to essentials | Prevent | The wall (M7) | |
| 26 | The parking lot as procrastination | Not a loophole | It changes nothing | |
| 27 | WhatsApp becoming the scroll | **Decided 19 Nov** | WhatsApp is capped: 10 minutes at a time, then a cooldown (default 30 minutes). Ending the cooldown early needs the wall. An active call is never cut off. Messages (SMS) and phone calls are not capped. See MASTER_PLAN section 21. | The cap is yours to loosen, with the 24 hour delay. |
| 28 | Using Spark's own screens to scroll | Not a loophole | There is nothing to scroll | |

## 6. Dead ends: every state has an exit

Rule P2: every refusal says why and gives a next step. The states I will walk in Phase 8:

| State | Exit |
| --- | --- |
| Stuck on the wall, cannot type the string | Cancel; the Emergency door; break glass; the PC or web |
| Camera unavailable or broken | The fallback ladder (M10): second try, steps, a short wait |
| Storage full | A card says what to clear; the database is not written until there is room (X7) |
| A permission revoked | One screen, one button to the right setting |
| No home point set | The first-run card; the plan runs with a warning |
| The database unreadable | The backup file (CT1); a read-only view of today (X7) |
| A cooldown with a real need | Check is free; Reality reports are free; Emergency; break glass |
| A lockdown on loosening with a real illness | The verified ill door; the extension and extenuating circumstances route is shown |
| A file that is rejected | The line and column and what to change (IM4) |
| Lost phone | The PC and web view and the restore steps (EM5) |
| A dead battery in the morning | The alarm is rebuilt at boot; the morning is flagged without penalty (X2) |
| A zone or clock change | Absolute times, a 23 or 25 hour day |
| The wall does not accept 3 wrong characters | A fourth restarts, with a fresh 3 (WS1) |

## 7. What I will verify in Phase 0, for real

1. The duplicated decisions in section 1, traced in the code, with a file and line each.
2. The 28 bypass routes, probed on the emulator and a list for your phone, with a pass or fail for each. Rows 9 to 12 are the ones I am least sure about.
3. The 40 collisions, written as core tests; each either passes now or is listed as a hole.
4. The property test over door combinations, run against today's doors to show whether any combination currently exceeds what you intended.
5. A result file with numbers, so the hole register is measured and not felt.
