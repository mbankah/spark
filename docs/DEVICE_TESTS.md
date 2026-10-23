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
