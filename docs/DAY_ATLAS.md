# The day atlas: every small step, defined

Part 2 of `docs/MASTER_PLAN.md`. Nothing here is built. It uses the model in `docs/LIFE_MODEL.md` (blocks contain routines, routines contain steps, steps have a cue, a proof tier, a gate, a reward and a fallback).

It is based on your own week plan in `seed/master-plan-week-B-and-key-dates.md` (wake and shower 07:30, Bible 07:50, leave on the alarm, VRNP cycles, football, Aldi, evening prayer, shutdown), the ledger, and what you have told me. Times are the plan's defaults; the adaptation period (observe, suggest, enforce) replaces any that are wrong with your measured times.

## 1. How to read a routine

| Column | Meaning |
| --- | --- |
| **Step** | The one thing to do, in the words Spark will say |
| **Cue** | What starts it: the previous step, a place, a time, a sensor |
| **Time** | How long it should take |
| **Proof** | Tier 0 none, 1 tap, 2 sensor, 3 NFC tap, 4 photo |
| **Gate** | What it opens or closes on the phone while it is live |
| **Reward** | What arrives when it is proven |
| **If missed** | What happens, without blame |

**Tiers:** F is Full, S is Short, Fl is Floor. A step marked `F S Fl` is in all three; `F S` in the first two; `F` only in the full routine.

**Parallel steps** are allowed when a routine says so (showering while the pasta cooks). The engine checks they do not clash and the coach says which one to watch.

## 2. Universal rules for every routine

1. **Every step has a floor** or is marked optional. No routine can be empty.
2. **One cue per step.** Never two things at once unless declared parallel.
3. **Prompts fade.** A step that goes well is prompted less (scaffold levels, `LIFE_MODEL.md` section 6).
4. **A late start shortens the tier; it does not skip the routine.** The yield policy picks the tier.
5. **Phone away means the phone is not in your hand.** During walks, showers, and the first part of mornings the gate is shut except the never-blocked list (calls, maps, authenticator, Bible, banking, clock, camera, notes).
6. **A prompt escalates in three steps and then stops:** a quiet cue, a spoken cue with a haptic pattern, then the gate. It never repeats endlessly.
7. **A missed step is recorded as a fact, not a failure.** The only message is the number and what to do next.

## 3. The night before

| # | Step | Cue | Time | Proof | Gate | Reward | If missed | Tiers |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | Clear the study spot and set the desk: laptop, notes, water glass | Evening prompt (T minus 90 min from lights-out) | 5 | NFC on the desk seat tag | None | One chime and the tick | Setup open until window closes | F S Fl |
| 2 | Lay out clothes and pack the bag, except kit | After step 1 | 6 | Tap | None | Tick | Counts as unfinished, plan moves it to the morning | F S |
| 3 | Breakfast out: bowl, spoon, jar | After step 2 | 3 | Tap | None | Tick | The morning cuts breakfast first, never the Bible | F S |
| 4 | Take the reference photo of the setup | After 1 to 3 | 1 | Photo (M1) | None | The photo becomes tomorrow's alarm key | No new reference: yesterday's is used and the morning says so | F S Fl |
| 5 | Check tomorrow: the first thing, the leave-by time, the alarm time | After step 4 | 1 | Tap | None | One calm line of what tomorrow starts with | None | F S Fl |
| 6 | Phone medication and routine reminders you set (melatonin, anything your prescriber told you) | Your own times | 0 | Tap, taken or not taken | None | Log for you to show a prescriber | Logged as not taken, no judgement | Your list |

The reference photo is in `M1`; the point is the order: setup, then the photo, then bed.

## 4. Wind-down and lights-out

This is where the sleep programme lives (`docs/COACH_AND_PHONE.md` section 5). The routine is anchored to the lights-out time (SA1) and runs backwards from it.

| # | Step | Cue | Time | Proof | Gate | Reward | If missed | Tiers |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | **Last jobs:** dishes, bins, room, 10 minute tidy | T minus 60 | 10 | Photo (chore) or tap | Social closes at the cutoff (N2) | Tick | Moves to the morning list | F S |
| 2 | **Screen softens:** grey and warm | T minus 45 | 0 | Auto | Social stays closed; screen tint on | Quiet | Skipped if the setting is off | F S Fl |
| 3 | **Brain dump:** type what is on your mind in 3 lines. It is parked, not acted on. | T minus 40 | 5 | Tap | None | "Parked. It will be there tomorrow." | Optional | F S |
| 4 | **Tomorrow's fun:** if there is something you want to do tonight, put it in a slot tomorrow | After step 3 | 1 | Tap | None | A real slot on tomorrow's calendar | None | F |
| 5 | **Prayer and stretches** (your plan: 10 minutes and 10 minutes) | T minus 30 | 20 | Tap | Phone away | Tick | Floor is 3 minutes of each | F S Fl |
| 6 | **Warm milk, teeth, wash** | T minus 15 | 10 | Tap | Phone away | Tick | Floor is teeth | F S Fl |
| 7 | **Phone to the charger across the room** | T minus 10 | 1 | NFC on the charger tag | Phone locked to the never-blocked list | "Docked" with the haptic pattern for goodnight | The night is logged as "phone not docked", the night rule still holds | F S Fl |
| 8 | **Shutdown photo** of the setup and the docked phone | T minus 8 | 1 | Photo (S1) | None | The night starts counting | If forgotten, sensors still track sleep, no penalty (S1) | F S Fl |
| 9 | **Lights out** | T | 0 | Light sensor drops, charger, still | None | None | Logged | F S Fl |

If the phone is not docked at the cutoff, the night rule blocks the social list exactly as it does today (N2); docking is the easy, free way to satisfy it.

## 5. In bed, through the night

| Situation | What Spark does | Proof it uses |
| --- | --- | --- |
| You are asleep | Nothing. Sleep onset and end are recorded (S2). | Screen off, still, dark, charging |
| You pick the phone up at night | It opens a quiet card: "It is 03:10. Back to bed? Tap Awake if you cannot sleep." The social list stays closed after the cutoff. The screen is warm and dim. | Accelerometer, screen on |
| You tap Awake | The awake list: get up, low light, one calm thing from your own list, no phone. A 20 minute gentle reminder. Not more than twice. | Tap |
| You pick the phone up for more than 15 minutes | It is counted as screen-on time against sleep (S3). The coach says nothing; the morning summary says the number. | Screen time |
| The alarm backstop (G2) | Rings at the latest wake time, always | Clock |

## 6. Waking and getting out of bed

The micro-steps you asked about. The phone is across the room (your plan), so the first step is physical.

| # | Step | Cue | Time | Proof | Gate | Reward | If missed | Tiers |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | **Alarm rings.** Volume rises over 60 seconds. Vibration pattern A: long, short, long. | Wake time | 0 | None | Everything social closed (M5, M6) | None | Escalates: ring, louder, then Z-rules | F S Fl |
| 2 | **Stand up and cross the room** to the phone | The ring | 1 | Steps or activity: walking | Gate stays | None | Alarm keeps going | F S Fl |
| 3 | **Carry it to the setup spot** (not visible from your bed) | After step 2 | 1 | Steps carrying the phone | Gate stays | None | No snooze exists | F S Fl |
| 4 | **Photo of the setup** stops the alarm (M2) | At the spot | 0 | Photo | Gate stays | Alarm stops, "Up" haptic | Fallback ladder: second try, then steps, then a short wait (M10) | F S Fl |
| 5 | **Drink the water** | After step 4 | 1 | Tap | Gate stays | Tick | Moves on | F S |
| 6 | **Light:** curtains open, or the main light on, or step outside | After step 5 | 1 to 10 | Light sensor rises | Gate stays | Tick. If you set a morning light dose (DSPS), it counts minutes of bright light. | Moves on | F S |

Everything up to here takes about 4 minutes. It is the whole job of the alarm; the next routines do the rest.

**A bad-sleep morning (Z-rules).** If you slept under about 5 hours, every morning routine drops to its Short tier (the Bible to Floor if you slept under 4), light comes first, and the day's catch-up is added to the Buffer with the reason shown (Z4, D5). You are not asked to decide; the engine picks the tier from the sleep number.

## 7. Getting ready

| # | Step | Cue | Time | Proof | Gate | Reward | If missed | Tiers |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | **Shower** | NFC at the bathroom door | 15 (your plan) | Tap on the tag, then a timer | Gate stays | Tick | Floor is a 5 minute wash | F S Fl |
| 2 | **Dress** | After shower | 5 | Tap | Gate stays | Tick | None | F S Fl |
| 3 | **Bible** (section 8) | The seat | 30 / 20 / 10 | Seat tag and timer, or the Bible app open for the time | Bible app open, everything else closed | The morning's first reward | Never dropped, only shortened | F S Fl |
| 4 | **Breakfast** | After Bible | 15 | Tap | Gate stays | Meal check later (MC1) | Cut first, never the Bible | F S |
| 5 | **Pocket check:** phone, keys, ID, wallet, charger, headphones | At the front door tag | 1 | NFC tap or a four-item tick | Gate stays | Tick | If you leave without, a reminder buzz at the door geofence | F S Fl |
| 6 | **Bag by the door and ready photo** (M8) | After pocket check | 1 | Photo | Social opens when proven | Phone unlocks for the day's rules | Social stays closed until the ready photo (M6, M8) | F S Fl |

## 8. The Bible routine and how it adapts

You asked how the Bible reading adapts here and there. The rule you wrote is the spine: **first, always; minimum ten minutes on the worst morning; breakfast is cut before it is.** Everything else follows from the three tiers.

### 8.1 Steps

| Tier | Steps | Time |
| --- | --- | --- |
| **Full** | Sit in the seat (NFC tag or place). Open the plan's next passage. Read. Write three lines: what it says, what it means, what to do. A 3 minute prayer. Close. | 30 |
| **Short** | Sit. Read the passage. One line: what it says to you today. | 20 |
| **Floor** | Sit. Read one chapter, or the first section of the passage. One sentence. | 10 |

Spark does not judge content. It tracks time and opening. It holds a **reading plan pointer** you set ("Gospel of John, next: chapter 7"); it moves on when a reading is proven; it can show the next passage on the card. Your Bible app and your physical Bible are both fine.

### 8.2 Proof options (you pick one per place)

| Proof | How | Strength |
| --- | --- | --- |
| Bible app foreground time | The Bible app is open for the tier's minutes | Tier 2, measured |
| Seat tag plus a timer | NFC tap at the seat, the timer runs, a tap at the end | Tier 3, a physical Bible works |
| Photo of your three lines | A photo of the journal page | Tier 4, optional |
| Audio Bible | A media app playing in the foreground for the time | Tier 2, for walks and trains |

### 8.3 Adaptation by context

| Context | What changes | Tier | Why |
| --- | --- | --- | --- |
| Normal weekday | 07:50, the flat seat, Full | Full | Your plan |
| You woke late (later start, RN1) | The Bible follows the wake, then breakfast. If time is short, Short. | Short | Never dropped; breakfast goes first |
| The worst morning (bad sleep, under 5 hours) | Floor | Floor | Your own rule |
| Sunday (church later) | Same time, plus church bag steps in the afternoon (Bible, notebook, student card, transport card) | Full | Your plan |
| Family home (vacation) | The seat is the portable tag in your family-home setup; it runs at the same clock time; if the household is up, it moves to the first quiet 30 minutes | Full | Same habit, different room |
| Train or travel day | Audio or app on the train; the physical Bible waits | Short | Proof is the media or the app |
| Ill | Floor, audio allowed | Floor | Kept, light |
| Exam day | Short, before the last revision block | Short | Protects the exam |
| Out late the night before | The Bible starts at the first upright moment after the late wake, then everything else | Short | The first thing in the day, still |
| A late-night plan (like tonight, wake 11:00) | 11:05, Full if nothing before 12:30 | Full | The day starts when you wake |
| Guests staying | Early, before they are up, or a quiet corner; Floor on the worst day | Short | A real life without a fight |
| Away, abroad | Local time; Short | Short | Zone follows (PL1) |
| A spiritually hard stretch | Tier stays; Spark asks nothing about content; the floor still counts | Floor | Spark never forces feeling |

### 8.4 The gate

The Bible app is on the never-blocked list. During the Bible step everything else is closed except the never-blocked list. When the reading is proven the morning's next step opens.

### 8.5 Evening prayer

A shorter Fixed step (your plan has it 22:50 to 23:00 with the phone away), anchored to the wind-down (section 4). Floor is 2 minutes.

## 9. Leaving and walking

| # | Step | Cue | Time | Proof | Gate | Reward | If missed | Tiers |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | **Leave-by** buzz: "Leave in 5. Shoes, coat." | Leave-by minus 5 | 0 | None | Gate stays | None | Repeats once | F S Fl |
| 2 | **Out of the door** | The leave-by time | 0 | Geofence exit of the flat | Phone away (T3) | The walk step starts | If 5 minutes late: Reality report offered on the card (Running late), the day reflows; the lecture is not skipped for you | F S Fl |
| 3 | **Walk**, phone away | Out of the door | Route time (OSM, T2) | Activity: walking, and the route's progress | Closed except never-blocked | Tick, and measured real time is kept for next time (T2) | Late: shown, plan reflows | F S Fl |
| 4 | **Arrive** | Campus geofence or building | 0 | Geofence | Gate follows the building rules | Tick | If the room is unknown, L6 asks once | F S Fl |
| 5 | **10 minute recall** (your plan): blank page, what you learned last lecture, three questions | At arrival | 10 | Tap or photo of the page | Phone away | A card with the last lecture's idea | Floor is 3 minutes | F S |

## 10. Lectures, tutorials and labs

| # | Step | Cue | Time | Proof | Gate | Reward | If missed | Tiers |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | **Phone in the bag** | At the room | 0 | Proximity covered, screen off | Open per K3 (lectures are unblocked) but your preference of "phone in the bag" is honoured with a gentle check | None | A quiet note | F S |
| 2 | **Attendance code** reminder every full hour and at the end (LC1) | Clock | 0 | The in-app photo in the window at the place (L1, L2) | None | Tick | The code is yours to enter; Spark checks the photo only | F S |
| 3 | **One idea** at the end: one sentence, the single idea | End | 1 | Tap or voice | None | The idea is saved to the recall card | Skipped, no penalty | F S |
| 4 | **Leave and walk** | End | Route time | Activity | Phone away on the walk | None | None | F S Fl |

## 11. The VRNP study cycle and any study block

From your seed: video or slides 15, read 25, notes 60 (45 to 90), practice 30, error log 10, then a reset.

| # | Step | Cue | Time | Proof | Gate | Reward | If missed | Tiers |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | **Sit and open the thing** | The block start | 1 | NFC at the desk, or the first work app opens | Work block closes the social list (K1) | The timer starts | Start prompt at 2 minutes, then 5; at 10 the block is "late" and the plan reflows | F S Fl |
| 2 | **First 5 minutes only:** "Just start." The step is deliberately tiny. | After step 1 | 5 | Foreground time of a work app | Gate stays | A short chime at 5 | Stays open | F S Fl |
| 3 | **The block** with its parts (V, R, N, P, E) and a visible ring | After step 2 | As planned | Foreground time plus the end proof | Gate stays | Progress ring fills | A part not reached becomes a 15 minute block tonight (your plan) | F S |
| 4 | **Stop on the stop time** | The end | 0 | Alarm and haptic | Gate stays until proof | Pattern B: two short | An overrun is reported once with Running over (RN2), free | F S Fl |
| 5 | **Proof at the end:** the notes or code photo (G1) | After step 4 | 1 | Photo | Earned phone time opens (R2) | The reward: minutes of open phone, the money (R8), and your choice from the reward menu | Open until the window closes, then unproven | F S |
| 6 | **Reset** 10 minutes: stand, water, walk, no phone (W4) | After step 5 | 10 | Steps | Phone away | Tick | Floor is 3 minutes | F S Fl |

## 12. Meals and cooking, with parallel steps

| # | Step | Cue | Time | Proof | Notes |
| --- | --- | --- | --- | --- | --- |
| 1 | **Start the main** | Meal prep time | 12 to 15 | Tap | The timers are a set, not a list |
| 2 | **Water on** | At the same time | 8 | Tap | |
| 3 | **Pasta in** | After the main | 11 | Timer | |
| 4 | **Shower, in parallel** | Pasta in | 11 | Tap on the bathroom tag | Parallel with step 3. The coach says: "Door open. Timer on. Pasta is yours." |
| 5 | **Drain, sauce, plate** | The pasta timer | 3 | Tap | |
| 6 | **Eat** | Plated | 15 to 20 | Tap | Phone open (K3) |
| 7 | **Did you eat?** | 10 minutes after the block (MC1) | 0 | Tap | Not yet asks again in 30 minutes, up to 3 |
| 8 | **Wash up** | After the meal or the first break | 5 to 10 | Photo or tap | |

This is the same structure as tonight's plan.

## 13. Chores

Your plan has "wash dishes plus a 10 minute tidy", bins, laundry and the weekly shop. Each is a routine with a floor.

| Routine | Full | Floor |
| --- | --- | --- |
| Dishes and tidy (17:40 in your plan) | Dishes, counter, bin out, clothes off the floor, desk clear, bed made | Dishes only |
| Bathroom | Toilet, sink, shower, floor, 20 minutes | Toilet and sink, 5 minutes |
| Room reset | Four passes: rubbish and cups, clothes away, surfaces, floor | One pass: rubbish and cups |
| Laundry | Sort, wash, hang, fold | Wash and hang |
| Bins | Bins out on the night | Tomorrow's reminder |
| Shopping (Aldi) | List, walk, shop, put away | The 5 item list |

Proof is a before and after photo where it matters, a tap where it does not, and a reward that lands on the next meal or the music (R3).

## 14. Football, gym, church, worship, music

| Routine | Steps | Floor |
| --- | --- | --- |
| Football or training | Kit bag (boots, shin pads, water), leave, travel, warm up, play, travel back, rinse, post-football meal | The match or the session itself; kit steps are taps |
| Gym | The split for the day, a timer, one proof photo of the log | 20 minutes |
| Church | Bag (Bible, notebook, card, transport card), travel, the service, travel back | Attendance is geofence; Spark asks nothing about content |
| Worship at the piano | One song or set, a timer | 10 minutes |
| Music and design | One loop or section, a timer, save the project | 20 minutes |

These keep their place in the weekly pattern; they are the things a reflow never touches (RF2).

## 15. Free time

A free slot is a block with a start and a stop (F1). All apps are open. An alarm and a haptic sound at the stop. At the stop one step: "Next: ...", with the next block's first step visible. It never becomes rot because the stop is a real event and the next block's first step is a one-tap start.

## 16. The late-night day (tonight's shape)

When you choose a later lights-out (SA), the whole day moves. Tonight is the model:

| Time | Routine |
| --- | --- |
| 22:55 | Leave for the shop (walk) |
| 23:05 | Collect the biscuits |
| 23:12 | Cook (parallel: main, water) |
| 23:24 | Pasta in; shower in parallel |
| 23:40 | Eat |
| 00:00 | Study 1 |
| 00:50 | Break: wash up |
| 01:00 | Study 2 |
| 01:50 | Break |
| 02:00 | Study 3 |
| 02:30 | Wind-down at its 10 minute floor (shutdown photo, teeth, phone to the charger, melatonin as you place it) |
| 02:40 | Lights out (8 hours of sleep after 20 minutes to fall asleep needs wake at 11:00 or later) |
| 11:00 | Wake (8 hours 20 minutes in bed) |

It is a normal day with its edges moved, which is exactly what the anchors do.

## 17. Days that are not uni days

The same routines, different contexts. `docs/UOB_AND_HOME.md` defines home days, library days, travel days, rest days and exam days with the steps that differ. The shape is always: wake, the floor routines, the day's work in blocks, meals, movement, wind-down.
