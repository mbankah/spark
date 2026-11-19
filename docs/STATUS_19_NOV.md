# What was built on 19 November 2026, and what was not

You said "Go, everything", then "Build the rest and Push". This is the honest account of how far that got. The plan is `docs/MASTER_PLAN.md`; the holes are `docs/HOLE_REGISTER.md`; every rule's test is in `docs/TRACEABILITY.md`; the ledger status column says per rule what is built.

## Built, tested, and in the APK

| Area | What you get | Rules |
| --- | --- | --- |
| **Late nights** | **Sleep later or earlier** in Modify Schedule (also on the Now card in the evening and on the third page): lights-out and wake move together, the shutdown moves with them or runs at its 10 minute floor, the next morning starts at the wake, the alarm is set to it, a lecture before the wake is refused by name. An event after midnight is accepted once the night is applied. Tonight's plan works with lights-out 02:40 and wake 11:00. | SA1 to SA6, RT2 |
| **Backlog spread** | Inside a catch-up span a missed-lecture catch-up is due at the span's end; the preview says work, room and shortfall; the end of the span gives D5 back. | BK1 to BK5 |
| **Away** | A zone setting and a place-check exemption. The attendance place check now uses the exemption (the proof is marked weaker, never silent). Alarms and meal windows do not read the zone yet. | PL1 to PL4 |
| **No Change button** | Gone. Running over, Started late and (after 21:00) Bed later tonight are on the Now card. | HM1, HS5, MX4 |
| **WhatsApp cap** | 10 minutes at a time, a 30 minute cooldown, the wall ends it, a call is never cut off. | GT2 |
| **Imports** | Add or Replace chosen in the app; the weekly economy (2 free imports, a short string, a 24 hour cooldown); a meter; a free undo within 24 hours. | IX2 to IX5, IX7, IX8, CE1 to CE6, CE8, CE9 |
| **Moves and loosening are counted too** | Applying a modifier that loosens counts against the same week and the pool; a locked week or a used-up pool refuses it with the reason. A period off and the lighter modifiers draw obligation minutes; the WhatsApp wall and the night override draw gate minutes. | CE3, CE4, DP2, DP3 |
| **Emergency door** | Call 999, NHS 111, Phone, Maps and taxi, Bank, Share where I am, on the home, the blocked card and the third page. Never gated, no question, logged quietly. The lost-phone steps are on the third page. | EM1, EM5 |
| **I'm out** | "Out for 2, 4 or 6 hours" pauses blocking and the night rule, only while the phone is away from home. At home, or with no place, it does nothing. | EM2 |
| **The coach, small and real** | A haptic vocabulary (six patterns you can feel), a spoken line you can turn off, a tone (Calm, Plain, Light), three identity sentences, the wind-down prompt once a night. | CO3, CO10, CO11, CO2 (part) |
| **Sleep** | Tomorrow's fun (a real slot tomorrow at 17:00), the nap offer after a night under 4 hours, your own reminders with taken or not taken, the cannot-sleep list, greyscale at wind-down (needs one command from your PC). | SL3, SL4, SL6, SL7, SL9 |
| **The university and home** | The four support routes (extension, extenuating circumstances, adjustments, GP and 111), the winter vacation in spans, the day type proposed and confirmed once, the family home set where you stand and told apart from the flat. | HO2 to HO4, CX1 to CX3 |
| **Lite day** | The home shows now, next and the phone; blocking stays. | LT1 |
| **Modifiers by effect, home modes, third page** | As before. | MX1 to MX3, HS1, HS2, HS4 |
| **Your data** | A backup file nightly (when you open Spark) and on demand, a checked restore, a "this doesn't work" report, the permission list after a reinstall, and a build that refuses a different signing key. | CT1 to CT5, PK1, PK2 |

## Added in the last round

| Area | What you get | Rules |
| --- | --- | --- |
| **Break glass** | Once in 30 days, the 150 character string, on the third page and on a blocked card that the weekly pool has locked. It lifts this week's loosening lockdown, an import cooldown and the WhatsApp cooldown. It never touches sleep. | CE7, EM4 |
| **The night override is locked by the pool** | It draws gate minutes from the same pool as every other door; when the pool is used up the card says so and offers break glass. | DP2, DP4 |
| **Screen-time limits** | Period off has a daily limit for the apps on your block list: open for that many minutes after the first open, then closed until midnight. | MX6 |
| **NFC tags, as an option** | Off by default. Switch it on, register a sticker for the desk, Bible seat, bathroom, charger or front door by tapping it, and a tap proves that step in a second. Read only while Spark is open. A tag is stored by a hash, never its id. Photos stay the proof until you turn it on. | SL2, FB6, FB8 |
| **The routine runner** | Third page, Run a routine: the Bible, wake, getting ready, study, cooking with a shower, wind-down, leaving and arriving, one step at a time at the tier the time and your night allow, a tap or a tag for each step, a haptic and your own reward at the end, and today's wins. Photo steps say so and are taken on the home screen. A step Spark cannot sense yet says it is confirmed by tap. | RT1 to RT8, CO3, CO9 |
| **Facts** | NFC taps and the runner write facts you can see in today's wins. | FB1 |
| **Dock icons** | Drawn icons for Phone, Msgs, Maps and Setup. | HS6 |
| **Backup in the background** | The minute loop writes the backup when the last one is about a day old, so it no longer waits for you to open Spark. | CT1 |
| **After midnight, tonight is still yesterday's night** | A night, or an event after midnight on it, can start yesterday, so tonight's plan can be made at 01:45. | SA1 |

## Built in this push, last

- **File format v2 (IX6, IX9) and one path for a file (IX1).** A file can carry `modifier`, `anchor` and `keep` rows; its events go through the same reflow engine as Modify Schedule; the web checker and the AI prompt read the same format and the same fixtures. Applying a file applies its modifiers, and removing the import removes them.
- **Routines are prompted at their time (CO1, CO2)** and the Away zone is read by the day (PL1, PL3).
- **Rules with tests**: GT1, DP5, HS7, HS8, VG1, VG3.

## Built after the push: sleep audio (PA)

- **Part-of-an-app access, first use YouTube.** At night, with the option on, a video you play and your searches stay open, Shorts are closed, and finding is paid for from a scrolling credit that listening earns back, and a playing video has a burst limit. Off by default; turning it on needs the typed wall. It is the general layer (a surface table, a scroll budget, a verdict in the one gate), so another app is one table row.
- **Not checked on a real phone, and the part most likely to need a change:** the names of YouTube's views were not read off a phone, because the emulator has no YouTube. If Shorts are not closed, or a video is treated as something else, the card on the Work it out page lists what Spark recognised. Until then an unrecognised screen costs double from the scrolling credit, so the failure is a stricter YouTube, never a free feed.

## Built after that: adapting to the situation (AD)

- **Drift, "Something came up" and the day ladder.** Spark notices when you are behind and offers one free proposal; you can say what happened in a few words and it finds a place and shows the cost; and a day you are behind on steps down by itself (short, floor, three things) and back up. Work that has to give way comes back as work, so moving it is free.
- **Tested** on a thirty-situation bank in core and on the emulator. **Not tried on your phone.** What is still open: the proposal takes two taps (Use this, then Apply on the normal preview), unwell still goes through the lighter-day pool and wall, and the weekly count of moves (5 free) can still ask for the wall in a very busy week. It says nothing about being behind until it has seen you hold or finish a block in the last week, so a new install is not told it is behind all day. Learning from your patterns (the next step) needs real days of data first.

## Built after that: the walkthrough (WK)

- **Nine pages on first open, drawn in the app's own theme, and Show the walkthrough in Setup.** Skip from any page; the last page has Start and Open Setup. The numbers on it come from the rules, so it cannot go out of date. Checked on the emulator by eye and by test.
- **Not tried on your phone.** On a very small screen the last page may need a scroll; the pictures are flat shapes, so nothing depends on a particular device.

## Built after that: the home layout (HS10)

- **One order on the home page:** clock, Now (with the three reports inside its card), Next as a timeline of three, what is waiting for you (two, the rest one tap away), the Buffer, then the quieter cards. "Moved for you" is on Work it out now. The full emulator suite passed: 569 tests, none failed.

## Not built, by name

- **The proof ladder is not one store yet**: photo, sleep and meal proofs are still separate stores; NFC taps and the runner write facts.
- **Prompts at their time are untested on a real phone**: the routine prompts, the greyscale at wind-down (needs the one-time permission grant) and the NFC reader were checked in core and on the emulator only.
- **The older doors (a plain day off, the Essentials window) keep their own walls**; they are not in the pool.

## What no emulator can prove

Alarms across a reboot, the lock screen, notifications and reminders at their time, location indoors, battery saver killing the blocker, the real greyscale setting, the haptic patterns and the spoken line on your phone, the WhatsApp cap on your real WhatsApp (checked once a minute, so up to a minute over), and "I'm out" with a real location. These are for your phone.
