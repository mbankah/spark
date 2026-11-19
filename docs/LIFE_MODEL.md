# The life model: one spine under everything

Written 18 November 2026, as Part 2 of `docs/MASTER_PLAN.md`, after you asked for every small step to be defined, for the app to adapt to you in every place and state, and for the systems to work together with no way round them. Nothing here is built.

You said: "if a system doesn't work with this one, and it doesn't work with that one, then you're just going to have a whole convoluted mess ... and they can be bypassed." That is a description of an architecture problem, not a feature problem. So this document is the architecture.

## 1. Why the systems do not yet fit together

Today Spark has good parts that each own their own state:

| Part | Owns |
| --- | --- |
| Alarm and morning | When it rings, the photo that stops it |
| Photo proof | Whether a photo matched |
| Blocking | Which apps are closed, by the schedule and the night cutoff |
| Night rule and overrides | The cutoff and the wall |
| Buffer | Hours available against hours due |
| Rewards and money | Earned phone time, the £25 |
| Modifiers and import | Changes to the calendar |
| Sleep | Onset and wake from sensors |
| Meals | The meal check |

Each part asks "is this allowed now?" in its own way, from its own copy of the facts. That is how a loophole appears: part A believes you are ill, part B does not, so the app is both strict and loose about the same minute. It is also why a feature built beside the others (like the Change button) can undercut a rule built elsewhere.

## 2. The spine: four things everything reads and writes

```
 PRODUCERS                      THE SPINE                       CONSUMERS
 sensors, taps, NFC,    ->   1. Day Model   (what should   ->   Gate  (what is open)
 photos, calendar,                happen, as steps)              Coach (what to say)
 files, modifiers       ->   2. Fact Bus    (what did      ->   Rewards, Buffer, money
                                 happen, with proof)             Sleep programme
                        ->   3. Context     (where and in   ->   Review, scaffolding
                                 what state you are)
                        ->   4. Door Pool   (every way a
                                 rule is relaxed, one cap)
```

**The rule that keeps it logical:** producers write facts and changes; the four spine parts decide; consumers only read. No consumer keeps its own idea of "are you ill", "are you out", "was that done". The gate does not decide anything; it reads the day model, the context and the facts, and shows the result. Then no two systems can disagree, because there is one answer.

### 2.1 The Day Model: blocks, routines, steps

Three levels. Today Spark has the first and part of the second.

| Level | What it is | Example | Owner |
| --- | --- | --- | --- |
| **Block** | A span on the calendar | 07:50 to 08:20 "Bible study" | The reflow engine (exists) |
| **Routine** | An ordered list of steps inside a block, in tiers | The Bible routine | New (this plan) |
| **Step** | The atom: one instruction | "Sit in the seat", "Open the Bible app", "Read" | New |

**A step has:** a cue (what starts it), an instruction in one short sentence, a time it should take, a completion signal and the proof tier it needs, what it opens or closes on the phone, its reward, what happens if it is not done, and variants for contexts.

**A routine has three tiers**, and the reflow engine picks the tier from the time available, using the yield policy:

| Tier | When | Rule |
| --- | --- | --- |
| **Full** | There is time | Everything in the routine |
| **Short** | Time is tight | The steps marked core |
| **Floor** | The worst day | The least that still counts. It is never dropped. |

This is the idea you already wrote in your own plan ("minimum 10 minutes on the worst morning; breakfast is cut before this is"), made general. Every routine has a floor. A day can never be a zero day.

### 2.2 The Fact Bus: what happened, with how much proof

Every signal becomes a **fact**: `type, time, source, confidence`. A step is **done** if there is at least one fact of the required proof tier inside the step's window. Everything that asks "was it done?" asks the bus.

**The proof ladder** (a step names the tier it needs; stronger tiers are always accepted):

| Tier | Signal | Skippable by ignoring? | Typical use |
| --- | --- | --- | --- |
| 0 | None, tracked by time only | Yes, and it does not matter | Low-stakes steps |
| 1 | A tap | Yes | Light steps, reflections |
| 2 | Sensor: movement, steps, place, light, charger | No, it happens or it does not | Walking to a lecture, getting up, being in bed |
| 3 | NFC tap on a sticker at the place | No | Bathroom, desk seat, front door, charger |
| 4 | Photo, matched | No | Setup, ready, shutdown, chores |

**Skipping is not a loophole because the step stays open.** If a required proof is ignored, the step remains *open* until its window closes; while open, whatever gate hangs on it stays shut (for example, social stays blocked until the ready proof). When the window closes the step is *unproven*: no reward, it counts as unconfirmed time for the week, and the coach says so plainly, with no scolding. Because the gate stays shut and the reward does not arrive, ignoring a prompt is never the easy path. And because most proofs can now be a one-second NFC tap or a sensor, doing it is easier than skipping it.

**When the technology fails**, not you: the fallback ladder (M10) applies. The step is closed as "not your fault" and nothing is lost.

### 2.3 Context: where you are and what state you are in

Context is one record that every other part reads.

| Field | Values | How it is known |
| --- | --- | --- |
| **Place** | Flat, Family home, Campus (and which building), Library, Church, Pitch, Shop, Train, Elsewhere | Geofence, Wi-Fi, the venue directory |
| **Day type** | Uni, Library, Home study, Rest, Travel, Match, Church, Exam, Deadline, Guests, Out, Ill, Lite | Calendar, modifiers, place; proposed automatically and confirmed once |
| **State** | Awake and upright, in bed, walking, in a vehicle, still | Activity recognition, accelerometer, charger |
| **Body** | Last night's sleep, time awake, meals, medication taken | Sleep engine, meal check, taps |
| **Mood** | A 1 to 5 tap, twice a day, optional | You |
| **Phase** | Observe, Suggest, Enforce (section 5) | The adaptation period |

The day type is **proposed by the app**, not chosen by you. If you are at the family home, it is the 20th of December and there is no lecture on the calendar, Spark proposes "Home study day" and asks once. That is the executive function being taken off you: you confirm, you do not assemble.

### 2.4 The Door Pool: every relaxation draws on one cap

A **door** is any feature that relaxes a rule: a day off, lite mode, an illness day, Out, a late night, a take-over import, an override, Away, break glass. Today each door has its own price, and nothing stops two doors being opened together to get what neither allows alone (for example, an ill day, then lite mode, then Out, with social open the whole time).

So all doors draw from **one pool**, counted in two units: *obligation minutes removed* and *gate minutes lifted* in a rolling 7 days. Each door has a price in those units and a cap; the wall length rises as the pool drains (as N4 and H1 already do per door); at zero the pool locks until it refills, except the Emergency door and break glass. Section 7 of `docs/COHERENCE_AND_BYPASS.md` has the exact doors and a property test that tries every combination.

## 3. Places

A place is a named location with its own settings. Two homes matter to you.

| Place | Has | Differs |
| --- | --- | --- |
| **Flat (Birmingham)** | Home point, Wi-Fi, charger tag, desk seat tag, bathroom tag, setup reference photos | You cook; you live alone; the full routines |
| **Family home** | A second home point, its own Wi-Fi, a portable seat tag and charger tag, its own setup photos | Shared meals, other people's noise and timings, no cooking duty, travel day to and from |
| **Campus** | The venue directory, eduroam Wi-Fi | Lecture phone rules, attendance codes |
| **Library** | Opening hours, quiet rules | Study blocks happen here on library days |
| **Church, Pitch, Shop, Gym** | Venue points | Their own travel and arrival steps |

Each routine can say which steps change by place. The Bible routine is the model (section 3 of the Day Atlas): the same routine, a different seat, a different charger, shared meals, and the same floor.

## 4. Precedence: what wins when rules meet

One order, used by the gate, the yield policy and the door pool.

1. **Safety**: emergency calls, emergency services, medication reminders, banking and authenticator (X1).
2. **Sleep protection**: the 8 hour rule and the night cutoff (N1, SA3).
3. **Hard commitments**: hard alarms, exams, paid sessions (D4).
4. **Routine floors**: the Bible floor, meals, water, the reset after each block.
5. **Study and work**: blocks, then the lecture cycles.
6. **Free time**: protected first, the rest yields first (YP1).
7. **Rewards and extras**: they never beat anything above.

`docs/COHERENCE_AND_BYPASS.md` lists 40 collisions and which rule wins.

## 5. The adaptation period: observe, suggest, enforce

You asked for a period where Spark sees where you are before it acts. Three phases, per routine, not per app:

| Phase | Length | What happens | Why |
| --- | --- | --- | --- |
| **Observe** | 7 days from install, or from adding a routine | Nothing new is gated. Spark measures real durations (wake to upright, shower, walk times, Bible, how long you take to wind down), sleep onset, and phone use by hour. Existing safety rules and alarms still run. | The plan is currently a guess. A tool that enforces a guess feels unfair and gets deleted. |
| **Suggest** | 7 days | Spark proposes changes in one card ("You take 22 minutes in the shower, I planned 15. Change to 20?"). Soft prompts only. You accept with one tap. | You confirm the fit instead of building it. |
| **Enforce** | From then on | Gates and costs apply as designed. | The numbers are now yours. |

A routine moves back to Observe if you add it or change it by more than a third. The change economy's 7 day trial (CE6) is the same period, so there is one adaptation story, not two.

## 6. Scaffold levels: building the habit, not a crutch

You want the executive function taken off you. The best version of that is a scaffold that carries the weight now and is taken down as you get stronger, so the habit becomes yours.

| Level | What Spark does | Moves up when | Moves down when |
| --- | --- | --- | --- |
| **Guided** | Cue, spoken or haptic prompt, a gate, a reward | New routine | 21 of 28 days on time |
| **Cued** | One quiet cue, no gate | 21 of 28 days | 3 misses in 7 days |
| **Tracked** | Silent. It only records. | 28 days at Cued with 24 on time | 3 misses in 7 days |
| **Yours** | The routine is retired from prompts. It stays in the plan. | Chosen by you | Any week you ask |

A step that goes well gets quieter. A step that slips gets louder again, automatically. Nothing is judged; the only message is the number.

## 7. The architecture in one paragraph, for the build

A `Fact` table and one writer. A `DayModel` (the existing reflow output plus routines and steps). A `Context` record kept current by a collector. A `DoorPool`. A single `Gate` that is the only thing allowed to change what the phone allows, reading those four. A `Coach` that schedules prompts from the day model and context. `Rewards`, `Buffer`, `Sleep` and `Review` as read-only views. Every existing feature is moved to write facts or changes, and its private decision code is removed (this is the audit in `docs/COHERENCE_AND_BYPASS.md`). The migration is staged so that each stage ships with the old behaviour proven unchanged by the existing tests.

## 8. What this changes in the master plan

| New layer | Replaces or sits under | Phase |
| --- | --- | --- |
| Fact Bus and proof ladder | Photo proof, sleep and meal checks as separate stores | 2 |
| Context record and Places | Home point only; Away zone | 2 and 3 |
| Routines, steps and tiers | Weekly blocks with notes | 3 |
| Door Pool | Per-door walls | 2 and 4 |
| Gate unification | Four places that decide what opens | 4 |
| Coach, scaffold, adaptation | None | 6 |
| Day types proposed automatically | Presets applied by hand | 5 |

The sequence and the sizing are in section 17 of `docs/MASTER_PLAN.md`.
