# Device tests for Michael's phone

Scripted checks I cannot run for you. Each has an expected result. Tell me the phone model and Android version with every result. Before any test, uninstall or disable the old Companion app, or it will block this app.

Install the debug build: connect the phone with USB debugging on, then run `adb install -r app\build\outputs\apk\debug\app-debug.apk` from the `efa` folder.

## Work blocks and proof photos (slice 4)

| # | Do this | Expected |
|---|---|---|
| D1 | Open the app during a study block (type "Notes and problems"). Tap the Now card. | The block screen shows the timer counting down. There is no way to finish early. |
| D2 | After the timer ends, press and hold "Hold to finish" for half a second, then let go. | Nothing is recorded. The bar resets. |
| D3 | Press and hold it for a full second. | The bar fills and "Done is held." appears. |
| D4 | Tap "Take proof photo", allow the camera when asked. | The reason text appears first. After allowing, the camera preview shows within 5 seconds. |
| D5 | Tap "Take photo" once. | One photo is taken, the screen returns, and it says "Verified. N min confirmed." with no review screen. |
| D6 | Repeat on the next block using the same photo of the same page. | "This photo matches an earlier proof. Take a new photo." If an honest new photo of a different page is rejected, tell me (the duplicate threshold needs tuning). |
| D7 | Take a photo of a bright window, then a dim room. | Both are accepted. Note any that fail. |
| D8 | Turn the camera permission off in Android settings and try again. | A plain message says the permission is off. There is a Back button. |
| D9 | Open a gym, church, MyTutor or chore block outside its time window. | No photo button. It says the time counts as lost. |
| D10 | Find a block that is a code block (CS Project, Battl.io, mini project, language lesson). | It says the commit check is not set up and the block cannot be verified. |
| D11 | Open a lecture. | It says attendance cannot be verified yet. |
| D12 | Check the phone's gallery and file manager after a verified block. | No full-size photo anywhere. Only app-private thumbnails exist. |
| D13 | With TalkBack on, try the hold button. | Report whether you can complete the hold. |

## Earlier slices

| # | Do this | Expected |
|---|---|---|
| E1 | Open the app. | Time, Buffer, Now and Next show within 1 second. |
| E2 | Quick add a task, save. | The Buffer changes and tapping it shows the working. |
| E3 | Tap Check Canvas with Canvas installed. | The Canvas app opens. |

## Venues, home and lecture attendance (slice 5)

Do D12 first: uninstall or disable Companion. You will need location switched on and precise location allowed.

| # | Do this | Expected |
|---|---|---|
| V1 | Home screen, tap Setup. | Home says "Not set". Seven lecture venues are listed, each "OpenStreetMap". |
| V2 | Tap "Set home on the map", pan to your home area, tap "Use this point". | Back in Setup, home says "Set, to about 100 m". If any venue is within 250 m of home it is listed. |
| V3 | With the internet off, open the map. | Tiles are blank but the cross and "Use this point" still work. |
| V4 | Tap a venue, place it a few hundred metres off, then place it back. | Both placements work. Each is logged (this is checked in code, not shown on screen). |
| V5 | Walk to a real lecture. During its window open the block (Now card) and tap "Take proof photo". | A reason screen asks for location. After allowing, the camera opens. |
| V6 | Take the photo standing outside the building, then again standing inside it. | Outside: verified, full proof. Inside: tell me whether it verifies or says "No precise location". Indoor accuracy is the biggest unknown (E11). |
| V7 | Take the photo about 200 m from the building but far from home. | Verified as "Weaker proof". |
| V8 | Take the photo at home during a lecture window. | "You are too close to home. Retake the photo once you are out." |
| V9 | Switch Location off in Android, take the photo. | "No precise location." and a retake. Nothing is counted. |
| V10 | For each of the seven buildings, stand at its main door and take a lecture photo. | Verified as full proof. Report any that are not. Building centres are used, so large buildings such as the Arts Building may need a wider radius. |
| V11 | Check the Education venue (Vaughan Jeffreys 135). | The point is the Education Building as a whole. Say if the room is in a different building. |

## Code blocks and GitHub (slice 6)

Needs your GitHub username and public repos entered in Setup first.

| # | Do this | Expected |
|---|---|---|
| G1 | Setup, GitHub card: enter your username and repos (owner/name per line), Save. | "Saved." and "Set for <you>, N repos." Bad input shows a plain message. |
| G2 | Start a CS Project block. Make a commit during it and push it. | Block screen shows the timer. |
| G3 | After the timer ends, hold "Hold to finish" for one second, then tap "Check commit". | "Verified. N min confirmed." |
| G4 | Next block: make no commit, check after the timer. | "No commit of yours was found in this block's time. Push your commit, then check again." |
| G5 | Make the commit but do not push, check. | Same "No commit" message. Push, check again: verified. |
| G6 | Use one commit for two back-to-back code blocks. | The second says its commits already proved an earlier block. |
| G7 | Turn the internet off and check. | "GitHub could not be reached." The block stays unverified and checkable. |
| G8 | Put a private repo in Setup and check. | "A repo ... was not found or is private. Only public repos can be checked." |
| G9 | Check a block many times in an hour (more than 60 checks across all blocks). | After the limit, the unreachable message appears. Tell me if you hit it in normal use. |

## Blocking, the wall and tamper (slice 7)

Android makes you switch the blocking service on yourself. On Android 13 or later, if "Spark blocking" is greyed out in Accessibility, open Settings, Apps, Spark, the three dots at the top right, and tap "Allow restricted settings", then try again (B1).

| # | Do this | Expected |
|---|---|---|
| B1 | Spark, Setup. Blocking card says "Off". Tap "Open accessibility settings", find Spark blocking, switch on. | Back in Spark, the card says "On". |
| B2 | Setup, "Choose the apps to block". | Your installed social and video apps are ticked. Banking, calls, maps, authenticator and WhatsApp are not. Save. |
| B3 | During a study block, open a blocked app (for example Instagram). | The card appears within about a second: why, what opening costs, the Buffer arithmetic. |
| B4 | Tap Go home, then open the app again. | Home screen, then the card again. No loop. |
| B5 | Open the app, press the back button on the card. | You go to the home screen, not into the app. |
| B6 | Open the app, tap "Open anyway: type 75 characters", type the string by hand. | The app opens for 30 minutes. Your current work block is marked broken. A second try after 31 minutes shows the card again. |
| B7 | Try to paste, use swipe typing or voice on the wall string. | It restarts. Report which keyboard you use. |
| B8 | Open a blocked app through each route: search, a notification, recents, a link from another app, the share sheet, a widget, split screen. | Every route ends at the same card. Tell me any that do not (LA8, LA7). |
| B9 | Put a blocked app in a floating window or picture-in-picture. | The card appears (LA7). Report if it does not. |
| B10 | After the night cutoff, open a blocked app. | Night card with "Wake HH:MM, so sleeping after HH:MM means under 8h". An override uses 75 characters, the next one tonight 100, then 125. |
| B11 | Within 6 hours of your wake time, open a blocked app. | "No override is possible now." No wall offered. |
| B12 | Switch the Spark service off in Android settings. | On opening Spark, home is one screen: "Blocking is off" with a button to settings. Switching it on restores home. |
| B13 | Open a blocked app inside a lecture, training, a match, gym, church or a meal. | The app opens normally. |
| B14 | Open banking, the dialler, maps and your authenticator during a work block. | They always open. |
| B15 | Restart the phone. | Blocking works again after boot without opening Spark (U3). Tell me if not. |
| B16 | Chrome and other browsers: open a blocked site in a normal and a private tab. | Not covered yet (K10 is built separately). Tell me what you see. |

## Held notifications, the digest and the Buffer notification (slice 8)

| # | Do this | Expected |
|---|---|---|
| N1 | Setup, Notifications card, "Open notification access", switch Spark on. Android may grey it out: open Settings, Apps, Spark, the three dots, "Allow restricted settings", then try again. | The card says "Holding is on." |
| N2 | During a study block, get a message in a blocked app (for example Instagram). | The notification does not stay in the shade. It is gone within about a second. |
| N3 | Home shows a card "Held notifications. Waiting for you." | No number anywhere. |
| N4 | Tap it while the block is still on. | "Still blocked until HH:mm." and nothing is listed yet. |
| N5 | After the block ends (or in free time), open the digest. | Every held notification, oldest first, with the app name and time. Count them yourself against what you sent: none may be missing. |
| N6 | Tap one item. | The app opens (it is no longer blocked). |
| N7 | Tap Seen. | They leave the digest. |
| N8 | Receive a phone call, an alarm, an authenticator code and a banking alert during a block. | All arrive normally and none is ever held (LA1). |
| N9 | Receive a message in an app that is not on your block list during a block. | It comes through normally. |
| N10 | Setup, "Allow the Buffer notification", allow it. | A silent notification "Buffer +x.xh" with the flame icon and a Quick add button appears, also on the lock screen. |
| N11 | Wait two minutes. | "Updated HH:mm" in the expanded notification moves on. |
| N12 | Tap Quick add on the notification. | Spark opens at Quick add. |
| N13 | Switch the blocking service off in Android settings and wait 5 minutes. | The notification stops updating. Its "Updated" time shows how old it is. |
| N14 | Swipe the notification away. | It cannot be dismissed (ongoing). Tell me if your phone lets you. |
| N15 | Deny the notification permission. | No Buffer notification, no error, and the rest of the app works. |

## The new look (slice 8)

| # | Do this | Expected |
|---|---|---|
| L1 | Open Spark in Light, then Dark system mode. | DM Sans for titles and numbers, DM Mono for labels, tactile cards, a big clock and the Buffer dial, following the drawing. |
| L2 | Set the phone font size to the largest. | Text scales and nothing is cut off or overlaps. |
| L3 | Turn on TalkBack and swipe through home. | Each card is read as one item with its meaning ("Buffer. +4.5h. Available 17.5h. Work 13.0h. Tap for the working."). |
| L4 | Tap Phone, Msgs and Maps on home. | Your phone, messaging and maps apps open. Tell me if one does nothing. |

## The launcher as the home app (slice 9)

| # | Do this | Expected |
|---|---|---|
| H1 | Install Spark and open it for the first time. | "Set up Spark" with one step at a time, each with its reason in plain words and one button. Phone, Msgs and Maps are one tap away the whole time. |
| H2 | Follow the steps: accessibility, block list, notification access, notification permission, camera, location, battery. | Each step shows "Done" when finished and the next one appears. The default home app is last. |
| H3 | Last step: "Make Spark my home app", choose Spark, then Always. | Pressing Home shows Spark. |
| H4 | Switch the accessibility service off in Android settings, then open Spark. | Home becomes one screen "Something is switched off" with one button to put it back. Phone, Msgs and Maps still work. |
| H5 | Change the home app back to your old launcher in Android settings. | Spark notices on next open and the restore screen offers "Make Spark my home app". |
| H6 | Restart the phone. Do not open Spark. | After unlocking: Home shows Spark, the Buffer notification is there, and a blocked app is blocked. |
| H7 | On home, tap "Type to open" and type "cam". | The keyboard is already open. Camera appears on every key. No recent list is shown when nothing is typed. |
| H8 | During a work block, type the name of a blocked app. | It is shown as muted text with the reason. Tapping it ends at the blocked card. |
| H9 | Turn on aeroplane mode and open Spark. | Everything on home still shows. Nothing says "unavailable" because of the network. |
| H10 | Cold start: swipe Spark away from recents, press Home. | Home is on screen in about a second. Tell me how long it really takes. |
| H11 | If Spark ever crashes 3 times in 10 minutes. | The phone's own launcher takes over, Spark keeps blocking, and a notice offers to put Spark back. (Hard to cause on purpose: this is covered on the emulator by a test that records three crashes.) |
| H12 | After 100 restarts (or a week of normal use). | Tell me if the launcher or blocking ever failed to start by itself, and when. |

## Timetable import and automatic catch-up (slice 10)

| # | Do this | Expected |
| --- | --- | --- |
| T1 | In Setup, press "Copy the format for an AI". Paste it into an AI app with a screenshot or text of your timetable. | The AI answers with only a CSV whose first line is `kind,date,end_date,start,end,title,type,venue,heaviness,study,action,notes`. |
| T2 | In the AI app, use Share on that CSV, as a file or as text, and choose Spark. | Spark opens at "Import a timetable file" with a preview. Nothing is stored yet. Tell me if Spark is missing from the Share list. |
| T3 | Do the same from a file manager with Open with. | The same preview. |
| T4 | Edit one line of the CSV so a time is `25:00`, and share it again. | A list of problems, each saying the line and the column, "Nothing was changed", and no Apply button. |
| T5 | Share a good file. Read the preview. | What is added, what is cancelled, "Buffer now" and "Buffer with this file", and any warnings. |
| T6 | Press Apply on a file that only adds. | "Done". The new lectures appear in Now and Next on their days, the deadline appears in the Buffer working. |
| T7 | Share the same file again. | "Nothing in this file changes anything". No duplicates. |
| T8 | Share a file that cancels a lecture. | The preview says "This file loosens" and the button says "Apply, with the wall". Typing the 75 characters applies it, and not before. |
| T9 | Setup, Import a timetable file, press Remove on the import and pass the wall. | Its events and deadlines go. The weekly pattern is as it was. |
| T10 | Skip a study block or lecture without taking its photo. Next morning, open home. | A card "Moved for you" says what came back, how long, and when it is due. The Buffer working has a "Catch-up: ..." line. It appears once. |
| T11 | Add a big deadline so the Buffer goes under its margin. Look at tomorrow. | Some free time shows as "Work (was free time)" and the card says why. Your protected free time (Monday evening and Saturday afternoon in Week B) is untouched. Tell me if too much or too little turned into work. |
| T12 | Plan an assignment week in a file. | Home says "Assignment week, until ...". Equal-deadline work is placed deadline first. |
| T13 | Do a proven study block with a task open. | The task's remaining time falls by the minutes the block was credited. |
| T14 | Open a block that has a task placed in it. | Its line says "Includes: ..." with the minutes, and a big task shows split across several blocks. |
| T15 | Time how long Spark takes from tapping its icon to the home screen after a restart, and again a few hours after installing. | About half a second after the app has had time to compile itself (the baseline profile). Tell me the real numbers. |

## Passive sleep logging (layer 2) and the first overnight test (Q6)

| # | Do this | Expected |
| --- | --- | --- |
| P1 | Setup, check the Sleep log card with the blocking service on. | "No night recorded yet" at first, and a line saying logging has started. With the service off it says Off. |
| P2 | Put the phone on charge by the bed as usual. Use it normally until bed. Turn the screen off when you lie down. Do not touch it all night. | Nothing happens. No alarm, no sound. |
| P3 | Wake as normal and look at the Sleep log card after 12:00. | "Last night: Xh Ym asleep, HH:MM to HH:MM." Tell me how far the start and the end are from what you remember. |
| P4 | Check the night checks line. If you looked at the phone for a minute in the night. | "1 night check, taken off", and the sleep time is that much shorter. |
| P5 | Check "Logging gaps". | 0. If it says 1 or more, Android stopped Spark during the night. Tell me the make and model, and whether battery saver was on. |
| P6 | Scroll in bed for 30 minutes before sleeping. | The start is when the screen went off for the hour, not when you got into bed. |
| P7 | Look at the battery use for Spark after a full day (Settings, Battery). | At or under about 5% (LA13). Tell me the number. |
| P8 | Repeat for a week. | One summary line per night, and the card keeps counting nights. |

## The morning (layer 3)

| # | Do this | Expected |
| --- | --- | --- |
| A1 | Setup, "Set up mornings". Take the ready spot photo (the bag by the door) once. | "Saved." The photo shows on the screen. |
| A2 | The night before, set up your study space and breakfast equipment and press "Take tonight's setup photo" from where you will stand. Use a spot you cannot see from your bed. | Saved, with the time. It replaces last night\'s. |
| A3 | Allow step counting if you want the fallback. | The card says On. |
| A4 | Go to sleep as normal. In the morning the alarm should ring about 8 hours after you fell asleep, or at 12:00 at the latest. Tell me the time it rang and when you fell asleep. | It rings loud, vibrates, and the alarm screen is over the lock screen. Tell me if it did not. |
| A5 | Take the photo from where you stood the night before. | It stops. Tell me the score if it did not (it says "score N, it needs 65"). |
| A6 | Stand somewhere else and photograph a wall. | "That does not match." The alarm keeps ringing. A second wrong try leads to the steps or the wait. |
| A7 | Shake the phone hard while it rings. | Nothing stops it. Steps only count after two photo attempts have failed. |
| A8 | Press Back, press Home, pull down the notification shade. | The alarm cannot be left. |
| A9 | After the alarm stops, try Instagram or another app on your list. | The blocked card says social stays blocked until the ready photo, with no typed wall. Phone calls, messages, maps and music still work. |
| A10 | Take the ready photo at the bag by the door. | The block ends at once. The home card goes. |
| A11 | Do not take it. | The block ends by itself at 14:00, and the card said so. |
| A12 | Switch the phone off before the alarm and on again after it. | The morning is flagged missed with no penalty and no social block, and the next alarm is set. |
| A13 | Setup, Sleep log, after a night with an alarm. | "Awake in bed before the alarm photo: N min." |

## The night (layer 4)

| # | Do this | Expected |
| --- | --- | --- |
| B1 | After a day where every lecture, work block and commitment has its photo, open a blocked app after the night cutoff. | The card says an override opens your blocked apps for 45 minutes. |
| B2 | After an ordinary day with one proof missing. | 30 minutes. |
| B3 | Be 60 minutes late stopping the alarm one morning. That night. | The cutoff is 60 minutes later than usual, and the 8-hour sum on the card names the later wake. The extra is a quarter of an hour less each day after. |
| B4 | Take the night-before photo at 21:30 after scrolling in bed from 19:00 to 21:00. Next day look at the Sleep log. | Sleep starts at the last screen-off after the photo, not at 19:00. |
| B5 | Forget the night-before photo. | The Sleep log still gives a night from the sensors. |
| B6 | Try an override within 6 hours of your wake. | It is refused. |

## Rewards and free slots (layer 6)

| # | Do this | Expected |
| --- | --- | --- |
| E1 | After the first 14 days and the next Monday, look at the home screen. | "Free to spend this week" shows pounds and "14.0 of 60.0 hours done". Before that it says Recording and when money starts. |
| E2 | Tap the free-to-spend card. | The working: food 40 pounds, each confirmed block with its weight, the sum, the payout and what moved to savings. |
| E3 | Prove a study block. | The block screen says what it kept out of the Buffer, how many minutes of open phone it earned, and what is next. |
| E4 | After a proven block, during a walk or a planning block, open a blocked app. | It opens for the minutes earned, then closes again. |
| E5 | Try the same in a work block, while getting ready, after the night cutoff and in the morning block. | It stays closed in all four. |
| E6 | When a free slot ends. | A notification with the alarm sound says what is next. |
| E7 | Check the plan for personal football. | 3 hours a week in sessions of 1 to 2 hours, in free time, with a photo window. |
| E8 | Make the Buffer tight. Look at a block that was free time. | It says why, ends with a 10 minute reset, and offers "Make it a break" with the 75 characters. |
| E9 | After a night under 6.5 hours. | Remaining hard work counts x1.5 in the Buffer working. |
| E10 | Look at Tuesday 19:10, Thursday 11:10 and 15:50, Friday 17:50 and 20:25 and Sunday 10:00. | Each block ends 10 minutes earlier and a "Reset (stand, water, no phone)" follows it before the next block starts. |

## Planned days off (layer 8: C1, C4, C5, H1 to H6, X3, G2)

| # | Do this | Expected |
| --- | --- | --- |
| E11 | Setup, Days off, pick Tomorrow. | A card shows "Day off Wed: Buffer -X.Xh" (or says the Buffer does not change and why) and the length of the string before you type anything. |
| E12 | Type the string. | Tomorrow has no study, lectures or resets. Sleep, meals and free time stay. Home says "Day off today" on the day. |
| E13 | Pick the Saturday weekend. | One use, one string. The next request is longer. |
| E14 | Press "I am ill today". | The same string with 40 extra. |
| E15 | After a night of 3 hours of sleep or less with the logger running. | Today is a day off with no typing. |
| E16 | Claim a day from the last week. | Pending until its catch-up work is done by its due times, then held. The week is not frozen for money until then. |
| E17 | The evening before a day off. | No alarm is set for the day off. |

