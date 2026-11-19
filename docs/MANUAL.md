# Spark manual, and how Spark speaks to you

Part 2 of `docs/MASTER_PLAN.md`. This describes Spark as it will be when the plan is built, so that you can read the whole thing, and the words it uses, before anything is built. Things that exist today and things that are planned are marked: **(planned)**. If a section is not marked, it is in the ledger and built.

## 1. What Spark is, in six lines

1. Spark is your outside brain. It holds the plan so you do not have to.
2. It tells you the next small thing, one at a time.
3. It makes the thing you are trying to avoid slightly harder, and the thing you are trying to do slightly easier.
4. It adapts when life happens. Telling it what happened is free.
5. Asking for less costs something, so a bad moment cannot undo a good week.
6. It is never a dead end. There is always a next step, and always a way back.

## 2. Your day

**Night before.** Set up the desk, lay out clothes, take the setup photo. Wind-down: tidy, the screen goes warm, you park what is on your mind, prayer and stretches, warm milk, dock the phone across the room, shutdown photo, lights out. **(wind-down tag and brain dump are planned)**

**Morning.** The alarm rings. You cross the room. You photograph the setup. Water. Light. Shower. Bible, first, always. Breakfast. Pocket check at the door. Bag photo. Social opens when the bag is ready.

**Out.** Leave-by buzz. The phone goes away for the walk. You arrive. Ten minute recall. Lecture. At each full hour, the attendance code reminder. At the end, one idea.

**Study.** Sit, open the thing, five minutes only, then the block, then the proof photo, then the reset. The proof opens your earned phone time.

**Food, chores, body.** Meals have a check. Chores have a floor. Football is where it is. Church is where it is.

**Night.** The same as the night before, adapted to your edges for that night.

## 3. The voice guide: how Spark speaks to you

You asked how I am addressing you and the options. These are the rules, with examples. Every message in the app is written to them.

### 3.1 Rules

1. **Second person, plain, short.** "Sit in the seat." Not "The user should now begin the reading."
2. **The verb first.** "Water, then light."
3. **One thing at a time.** One prompt. One question.
4. **Numbers, not adjectives.** "Up 12 minutes late" not "you were very late".
5. **No blame, no shame, no praise that sounds like a teacher.** "6 of 10 steps done" is the whole message.
6. **Say what you can do next.** Every refusal, every cost, every error ends with an action.
7. **Say what happened, once.** No repeating.
8. **Your words where you set them.** Your identity sentences, your rewards.
9. **A tone setting:** Calm (default), Plain (shortest), Light (a little warmth). It changes the style, never the content.
10. **Nothing about your body, your faith or your mood is ever commented on.** Spark can ask and show numbers. It never interprets.

### 3.2 Words Spark does not use

*Failed, lazy, bad, should, must, streak, broken, behind schedule, overdue (for anything in a catch-up span), you always, you never, disappointing.*

### 3.3 Examples: what Spark says, in situations

| Situation | Spark says |
| --- | --- |
| The alarm rings | "07:30. Up. Cross the room." |
| You stop the alarm with the photo | (a soft pulse) "Up. Water." |
| The Bible step | "Seat. John 7. 30 minutes." (or "10 minutes, short morning") |
| Running late at the door | "Leave in 5. Shoes." then "You are 4 minutes late. Walk now. The lecture still counts." |
| You tapped Running over | "Done. Today moved 20 minutes. Next: lunch at 12:40." |
| A step is not proven yet | "Photo not taken yet. Social stays closed until it is." |
| A step was not proven in its window | "Not proven. No reward. Next: study 10:10." |
| A block is proven | "Block done. 10 minutes of open phone earned. £1.40." |
| A cost: you want a day off | "A day off removes 6 hours from the week. The Buffer goes to -1.5h. Type the code to confirm." |
| The wall | "Type this exactly. 3 wrong characters are forgiven." |
| A cooldown | "Imports are closed for 22 hours. You can still check a file. Emergencies are not affected." |
| A lockdown | "You have used your 2 changes this week. The next one is Monday, or use break glass (once a month)." |
| Night, after the cutoff | "02:40. Back to bed? Tap Awake if you cannot sleep." |
| You tap Awake | "Up. Low light. No phone. Back at 20 minutes." |
| The morning card | "7h 40. Up on time. Today: Full." |
| A bad night | "3h 50. Today is Short. Lecture or recording: you decide at 08:30." |
| The urge pause | "Open Instagram? Breathe, three times. Then tell me what you will do instead." |
| A refused plan | "Wake 09:30 is after the 09:00 lecture 'Randomness'. Choose: move the wake to 08:30, or use the recording." |
| An import preview | "37 events added. 3 moved: lunch to 12:50, study to 14:10. Buffer +0.5h. Apply or check again." |
| A take-over preview | "This replaces Mon 23 to Wed 25. Kept: football, sleep, meals, the hard alarm. Work placed: 11.5h of 12h. 0.5h short. Apply with the code." |
| A fresh start | "Starting from now. 6 of 10 steps done earlier. Next: water." |
| A low run | "The last few days have been hard. A lighter week? Or talk to someone." |
| The emergency door | "Calls, maps, bank, authenticator. Always open." |
| The weekly review | "12 of 14 blocks proven. 6 nights docked. £21.40. 2 ideas parked." |

## 4. Options, with defaults and what each costs to change

Cost: **free** (any time), **wall** (the typed string), **24h** (takes effect after 24 hours because it loosens), **delay** (an observe period follows).

### 4.1 Edges and days

| Option | Default | What it does | Change |
| --- | --- | --- | --- |
| Wake target | Your plan | The anchor for the alarm and the cutoff | free (tonight), 24h (default) |
| Lights-out target | Wake minus 8h 20 min | The anchor for wind-down | free if 8h holds |
| Hours of sleep | 8 | The sum in SA3 and N1 | Locked |
| Home points | Flat; add Family home | Where "home" is | free |
| Day types | Proposed automatically | Which routines run | free |

### 4.2 Routines and steps

| Option | Default | What it does | Change |
| --- | --- | --- | --- |
| Routine tiers | Full, Short, Floor | What shrinks when time is short | free (floors need the wall to shrink) |
| Bible | 30 / 20 / 10 minutes | The routine | free for the plan pointer; the floor needs the wall |
| Floors of other routines | As the atlas | The least that counts | wall |
| Proof per step | As the atlas | The tier needed | tightening free; loosening wall |
| Prompt levels | 4 | How loud | free to quieten after a week of Tracked |
| Scaffold | Guided to Yours | How much Spark prompts | free |

The six routines Spark knows, each with its own steps, tiers and floor:

- **Bible**: sit, open, read, a line to carry. 30, 20 or 10 minutes.
- **Wake**: the alarm, the photo, up.
- **Getting ready**: shower, dress, bag, out the door.
- **Wind-down**: the shutdown, the evening prayer, the phone away, lights out.
- **Cook and shower**: the pasta goes on, the shower runs while it cooks, eat.
- **Study block**: sit down, phone away, one task, a break at the end.


### 4.3 The phone

| Option | Default | What it does | Change |
| --- | --- | --- | --- |
| Block list | Your list | What closes in blocks and at night | wall to loosen, free to add |
| Essentials | Phone, Messages, Maps, Camera, Bank | One tap from home | wall to add |
| Never blocked | Calls, maps, authenticator, banking, Bible, clock, camera, notes | The safety floor | Locked |
| Quiet mode | On | Holds non-essential notifications in blocks and at night | free |
| WhatsApp cap | 10 minutes at a time, then a 30 minute cooldown | An active call is never cut off; SMS and calls are not capped | wall to end a cooldown; 24h to loosen the cap |
| Sleep audio (YouTube) | Off | At night a video you play and your searches stay open, Shorts stay closed, and finding is paid for from a scrolling credit of 40 (search 1 a flick, a feed 2) that listening earns back at 1 a minute; when it runs dry YouTube pauses for 3 minutes. Work blocks and the morning stay shut | wall to turn on, free to turn off |
| Greyscale and warm screen at wind-down | On if available | The screen loses its pull | free |
| Do Not Disturb in blocks | On | Silence | free |

### 4.3b Adapting

| Option | Default | What it does | Change |
| --- | --- | --- | --- |
| Something came up | On the Now card | Say what happened, how long, and whether it can move. Spark finds a place and shows what it costs | free; work that cannot fit comes back as work |
| You are behind | Offered when 20 minutes behind | One proposal: slide the rest of today so the block in front can still run whole | free to take, tap Not now to send it away |
| The day steps down | Automatic | Short day, Floor day, then Three things (Lite). It steps back up when you catch up | tap Back to the full day, or switch Lite on yourself to keep it |

### 4.3c Walkthrough

| Option | Default | What it does | Change |
| --- | --- | --- | --- |
| Walkthrough | Opens once on first use | Nine short pages on what Spark does, the numbers taken from the rules themselves. Skip any time. Open it again from Setup, Show the walkthrough | free |

### 4.4 Sleep

| Option | Default | What it does | Change |
| --- | --- | --- | --- |
| Wind-down length | 60 minutes | The routine | free |
| Dock tag | On if you have a tag | The charger step | free |
| Brain dump | On | Park the mind | free |
| Tomorrow's fun | On | A slot tomorrow | free |
| Awake list | Your five things | What to do when awake | free |
| Light dose in the morning | Off | The step for a delayed clock | free |
| Gradual shift | Off | Moves wake earlier by steps | free |
| Reminders | Yours | Medication and similar | free |
| Nap offer | On | One 20 minute window before 15:00 after under 4h | free |

### 4.5 Rewards and the coach

| Option | Default | What it does | Change |
| --- | --- | --- | --- |
| Reward menu | Five of yours | What you are offered | free |
| Identity sentences | Three of yours | Quoted back | free |
| Tone | Calm | Style | free |
| Mood check | Twice a day | The optional 1 to 5 | free |
| The coach voice | On | Spoken prompts | free |
| Haptics | On | The vocabulary | free |

### 4.6 Doors and the economy (defaults you can tune)

| Option | Default | What it does | Change |
| --- | --- | --- | --- |
| Moves | 5 free a week | Same work, new times | 24h to raise |
| Loosening | 2 a week, the 3rd longer, the 4th locks | Removing obligation | 24h to raise |
| Imports | 2 free a week, then the short string, the 6th cools for 24h | Files | 24h to raise |
| Trial | 7 days | Counts, no locks | free |
| Break glass | Once in 30 days | Lifts one lock | Locked |
| Door Pool | A weekly cap in slack minutes | All doors together | 24h to raise |

### 4.7 Imports

| Option | Default | What it does | Change |
| --- | --- | --- | --- |
| Mode | Chosen each time | Add to my plan, or Replace my plan for these days | n/a |
| Keep list | Football, church, sleep, meals, hard alarms | Survives a replace | free to add |
| Restore point | On | Undo for 24 hours | Locked |

### 4.8 Privacy and data

| Option | Default | What it does | Change |
| --- | --- | --- | --- |
| Photos | On the phone only | Nothing is uploaded (G8) | Locked |
| Backup file | Nightly to Downloads/Spark | Survives an uninstall | free |
| Reports | "This doesn't work" | A local file for me | free |
| People list | GP, University Mental Health and Wellbeing Service, Student Disability Service, NHS 111, Samaritans, Shout | Information only | free |

## 5. The doors, in plain words

A **door** is a way to ask for less. Spark has a few and they share one **pool** each week.

- **Free, always:** telling Spark what happened (running over, starting late, out, cancelled), adding things, the Emergency door, checking a file.
- **A little, then more:** moving things, a late night, a day off, a day of rest, a replace-import.
- **Locked after a point:** the pool. When it is empty, only the Emergency door, a verified ill day, and break glass remain.

You can see the meter in Setup. It never appears on the home page.

## 6. What happens when ...

| When | Spark |
| --- | --- |
| The alarm rings | You cross the room and take the photo. There is no snooze. |
| You are late | You tap Running over or Running late. The day reflows. It is free. |
| You want less | You see the cost, in hours, before you confirm. |
| You want a different plan | You open Work it out and see numbers. Nothing changes until Apply. |
| An idea arrives | Park it. It changes nothing. |
| A bad day | Fresh start or Lite. |
| Something is wrong with Spark | Tap "This doesn't work", and carry on. |
| An emergency | The Emergency door. It is always open. |

## 7. Your first 14 days

| Days | Phase | What happens |
| --- | --- | --- |
| 1 to 7 | **Observe** | Spark measures your real times and sleep. Nothing new is gated. Existing safety rules and alarms run. |
| 8 to 14 | **Suggest** | One card a day proposes a change to fit your real times. One tap to accept. |
| 15 on | **Enforce** | Gates and costs apply. Scaffold levels start to fade routines that go well. |

Add a routine, and it has its own observe week.

## 8. The tags: what to buy and where to stick them

**Later, when you buy them. Until then photos are the proof.** A pack of cheap NFC stickers. One tap each, a second.

| Tag | Where | What it proves |
| --- | --- | --- |
| Charger | By the charger across the room | The phone is docked |
| Desk | The study seat | You are at the desk |
| Bible seat | Your reading seat | You are in the seat |
| Bathroom | The door frame | The shower started |
| Front door | At the door | Pocket check and leaving |
| Kit bag | On the bag | Kit packed |
| Family-home pair | In your bag | A portable seat and charger tag for home |

If your phone has no NFC, the same places can use a printed QR code.

## 9. The week

| When | What |
| --- | --- |
| Every day | The routines; the wins at wind-down |
| Sunday, 10 minutes | The weekly review: the numbers, the ideas parked, the moves and loosening used, one thing to change |
| Monday | The counts reset; the new week |

## 9b. Where to get support at the university

Information only, from the University of Birmingham pages, read on 18 November 2026. Check the university's pages for the current rules. Spark lists them under Work it out, Support.

- **Coursework extension**: before the deadline, online, for exceptional and unforeseeable circumstances. Not for exams or timed assessments.
- **Extenuating circumstances**: after the impact, a claim to your School by its advertised deadline, usually with a certificate or a letter.
- **Reasonable Adjustment Plan**: any time in your course, through the Student Disability Service. ADHD is named. Can include extended deadlines, rest breaks and extra time.
- **Your GP and NHS 111**: a GP can refer you for an ADHD assessment; NHS 111 for urgent help that is not an emergency. In an emergency call 999.

## 10. What Spark will never do

1. Judge your faith, your mood, your body or your choices.
2. Upload your photos or location.
3. Hide a cost.
4. Trap you: every state has an exit.
5. Claim something works that it has not proved works. The list of what a real phone must confirm is in the release notes.
6. Change a rule you locked without asking you.
