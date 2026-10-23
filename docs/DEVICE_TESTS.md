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
