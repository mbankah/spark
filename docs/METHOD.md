# How I solve a problem from now on

Written 18 November 2026 at your request, to show I have understood. It is the working method for every situation, example or problem you give me. If a later answer of mine does not follow it, this is the document to hold me to.

## What I was doing wrong

You give me a situation. I fix that situation. The fix is a new feature shaped exactly like the situation, and it breaks or leaves a gap somewhere I did not build a system for. Examples from this project:

| You said | What I built | What it left broken |
| --- | --- | --- |
| "I want to wake at 10:30" | A wake time that moves the alarm | Meals, routine and work did not follow it. You had to ask again for the day to adapt |
| "Catch-up week is scattered" | Study blocks, for catch-up only | Lecture-free, assignment and focus still placed work the old way |
| "Re-plan from now" | Two buttons: Start from now, Running over | The first one refuses when a commitment is within 65 minutes of now, because the system has no rule for what gives way when time runs out |

The cause is the same each time: I treated the example as the problem. The example is one point in a family of situations, and the family needs a system.

## The rule in one picture: a full set of teeth

If you ask me for one tooth, I build the whole set. One tooth bites, but the food reaches the gums, and every hole is a place the system hurts you. A request for one situation is a request for the complete system that covers the family of that situation, so that the next one, and the one after, already works. In practice:

- I build every member of the family that the system can reasonably hold in the same change: for "the plan changes in the middle of a day" that is a later start, a block running longer, time being taken by something new, time being freed, and demand arriving, not only the one you named.
- A feature is not finished while any neighbour of it fails in a way you would meet. I find those neighbours myself, by listing them and running them, before I tell you it works.
- What I cannot build in the same change I list by name as a hole, with what it would take, and I do not present the partial set as the whole.

## The method

1. **Turn the example into a class.** Strip out the particular time, event and person. Write the situation as parameters: what triggered it, from when, over what span, which resources are short, which constraints hold. "I'm going from 6 to 10 and there's an issue" becomes: *a span of time is taken or changed, from any starting point, for any reason.*
2. **Find the missing system.** Ask what, if it existed, would make every situation in the class solvable, and which of those situations cannot be solved today.
3. **Build the system at the most general layer first.** The model, the invariants, then the policy (what gives way, in what order, at what cost). Not a feature.
4. **Test it on a spread, not the one example.** I write several situations of the same class, and some from neighbouring classes, and run all of them. If I can only make the one you gave me pass, the system is not built.
5. **Check what else reads what I changed.** Every change lists the other things that read it (alarms, blocker, Buffer, reminders, the schedule page, the import file format) and I look at each.
6. **Only then go down a layer.** Presets, screens and shortcuts are thin mappings onto the system. They are cheap once the system exists, and wrong when built before it.
7. **Stay specific only when you say it is specific.** "Tonight's plan from 20:00" is a one-off and is data. "I go from 6 to 10" is a class.

## The layers

The lower the number, the more general. I build the lowest layer that is missing before I touch a higher one.

| Layer | What it is | In this app |
| --- | --- | --- |
| L0 Rules | What must always hold | The ledger: sleep and hard alarms never move, nothing is deleted, loosening needs the wall, honest reports |
| L1 Model | How time and work are represented | The calendar layers, fixed and movable blocks, spans with a time at each end, tasks, the Buffer |
| L2 Policy | What gives way, in what order, and what it costs | The yield order below, and which yields are loosening |
| L3 Engine | Computes a plan from the model and policy | `buildDay` and the preview, guard rails and numbers |
| L4 Modifiers | A named set of parameters for the engine | Catch-up, Late start, Running over, assignment, focus, Guests, Away, Recovery |
| L5 Entry points | How you reach it | Screens, quick buttons, the file format |
| L6 One-offs | Data for one occasion | A CSV for tonight's plan |

Most of my mistakes in this project were building an L4 or L5 piece with no L2 under it.

## The system for "something changes the plan"

The class behind situations like waking late, running over, a guest arriving, an event added, an illness, a travel day, a cancelled lecture:

> A **disturbance** happens: something at a point in time adds, removes, delays or shifts time or demand. The plan must reflow from that point, keep everything that must not move, give way in a fixed order, say what it cost, and ask for the wall only when it loosens a rule.

A disturbance has these parts, and every modifier is a choice of them:

| Part | Values |
| --- | --- |
| When | A start and an end, each a date and a time, or "from now" |
| What changes | The day starts later; a block runs longer; time is added (an event); time is removed (a cancellation, an illness); demand is added (a deadline, a backlog) |
| What is fixed | Always the L0 list and every commitment; the rest is the engine's to move |
| Who gives way | The yield order |

### Yield order (L2)

When time is short, things give way in this order. Each step is tried fully before the next. This table is the policy, and it is a Default until you confirm it.

| Order | Gives way | How | Loosening (wall)? |
| --- | --- | --- | --- |
| Never | Sleep, hard alarms, commitments, protected free time | Not touched | n/a |
| 1 | Free time that is not protected | Shortened or removed first | No |
| 2 | Resets and transitions | Trimmed to their minimum | No |
| 3 | Meals | Slid inside their window; dropped only if none is left | Dropped: yes |
| 4 | The routine | Each item trimmed to its stated minimum (Bible: 10 minutes), the optional ones (breakfast past the cut-off) dropped | Trimmed below its minimum or dropped: yes |
| 5 | Chores | Slid; never dropped, so a plan without room for them is refused with the reason | n/a |
| 6 | Work | Shortened to no less than 30 minutes, then dropped; dropped work returns as work (RS1) | Yes |
| Last | A refusal | Only when the above cannot make a plan, and then it says what is in the way and what would make room | n/a |

A refusal is the last resort. The system never refuses a situation it could have made room for.

### How the situations map onto it

| Situation | Disturbance | Modifier (L4) | Entry point (L5) |
| --- | --- | --- | --- |
| Wake at 2pm | The day starts later, from now | Late start, from now | Right now |
| A task runs over | A block runs longer | Running over | Right now |
| Guests arrive Friday 6 to 10 | Time is taken, with preparation demand before it | Guests (not built) | Modify Schedule |
| Last-minute event | Time is added | An event, in the app (not built) | Add an event |
| Lecture cancelled | Time is removed | A cancellation, in the app (not built) | Tap the lecture |
| Illness | Time is removed and demand is held back | Recovery (not built) | Modify Schedule |
| Travel | Time zone and commitments change | Away (not built) | Modify Schedule |
| A week missed | Demand is added | Catch-up | Modify Schedule |

## What I do before I answer

For every situation or problem you give me, in this order:

1. Say the class in one sentence, in general terms.
2. Say which layer is missing. If the layer under it is missing, I build that first.
3. Build or change the system.
4. Run the situation you gave me, and at least four others of the same class. Report all of them, including the ones that fail.
5. List what else reads what I changed and what I checked.
6. Build the higher layers (modifier, entry point) last, and say they are thin.
7. Say what is still specific, and why I left it so.

My replies will say which layer a change is in. If I have built something at a higher layer than the system under it can support, I will say so plainly rather than present it as finished.

## Where this applies to the work in progress

Running over and Start from now (RN1, RN2) are L4 and L5 pieces. They sit on the reflow engine (L3), but the yield order (L2) was never written down as one policy. It lives as scattered rules inside `buildDay`, and the one that is missing, the routine giving way before the engine refuses, is what makes Start from now fail when a commitment is near. The right fix is the policy, not a patch for that day: the routine yields by the order above, so every disturbance gets it. That is the next change.
