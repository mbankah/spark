# Build decisions

Choices the ledger leaves open, with reasons. The ledger wins on any conflict.

| # | Choice | Why |
|---|---|---|
| D-1 | **Pure-logic `core` module in Kotlin/JVM** (Buffer, weights, squeeze order, money, cutoff, drift, sleep detection, wall lengths, storage policy, contrast). No Android imports. | Runs and tests on JDK 17 without the Android SDK, so every arithmetic rule has a real test even before the SDK is installed. |
| D-2 | **Shared JSON fixtures** in `fixtures/` (worked examples: +4.5 h Buffer, cutoff 23:10, money table, 25 Oct clock change). Kotlin and TypeScript both load them. | The web app needs the same arithmetic. Fixtures stop the two copies drifting. |
| D-3 | Android app in `app/`: Compose, Room, WorkManager, CameraX, foreground service, accessibility service, notification listener. Min SDK 29, target 35. Sideloaded (E6). | As build prompt section 4. |
| D-4 | Web: React + TypeScript PWA built with Vite in `web/`. | As build prompt. |
| D-5 | Windows service in C# (.NET 8) in `windows/`, extension MV3 in `extension/`. | The service must restart itself and lock files; .NET service hosting is the shortest route. .NET SDK is missing here, so it is unbuilt until installed. |
| D-6 | Backend: Supabase (Postgres + auth + row policies) with a numbers-only schema that has no photo or coordinate columns. Device pairing by QR. | G8, ST6. The schema itself enforces ST6. |
| D-7 | All times stored as UTC instant plus IANA zone `Europe/London`; local date derived. | X4. 25 Oct 2026 has 25 hours. |
| D-8 | Layer 1 includes proof photo capture and the location check (L1 to L8, Y1 to Y3), because W1 needs a proof photo. The on-device image match is Layer 3. | W1 cannot work without capture. |
| D-9 | Layer 1 builds a sync-ready store only. Cloud sync, the server time check and web emergency mode (G8 to G10) land in Layer 8. | Build prompt Layer 1 says "sync-ready storage". |
| D-10 | Fonts: DM Mono and DM Sans bundled in the app (no network). | LA4 offline. |
| D-11 | Colour tokens come from the `design/` files and the table in the build prompt. All palettes are tokens. Default palette is **Cold** (Michael, 23 Oct): Light #e3e9ef/#f3f6f9, accent #1f6feb; Dark #0c1218/#151d26, accent #3ec6e0. Auto follows the system light/dark setting. Night stays amber. Neutral and Warm remain as settings. | Prompt section 5. |
| D-12 | The 75-character wall is a Compose text field with paste, autofill, IME suggestions and the selection toolbar disabled, and any multi-character insert rejected (only single typed characters accepted; a mistake restarts). Limits: a third-party keyboard or accessibility automation that sends single characters can still type it. Android cannot prevent that. It is stated in the wall's code comments. | Cannot be made perfect on Android. Stated in build prompt hard rule 6. |

## Verification state of this machine

JDK 17.0.20, Node 24.21, Python 3.14, Git, Android SDK (platforms 34 to 37, emulator, adb) and Gradle 9.8.2 via the wrapper. Not present: .NET SDK, Rust.

## Windows installer (added 23 Oct 2026 at Michael's request)

| # | Choice | Why |
|---|---|---|
| D-13 | Installer uses the same industrial look: tokens, 14 px cards, DM Mono labels, one accent, Light/Dark/Night, no animation. Drawn in `design/WindowsInstaller.html` (4 steps: what is installed, administrator permission, pair by QR, status). | Request. Same tokens as the phone app. |
| D-14 | Built as a WiX bootstrapper with a custom UI that reads the shared tokens, installing the service, the locked hosts file and the extension link. Uninstall goes through the 75-character wall (K6). | One signed file, service registration, wall on removal. |
| D-15 | Copy states the limits plainly: admin approval is needed once, and an administrator can still bypass in safe mode or with a VPN (K8, E7). | Build prompt rule 6. |

The ledger has no installer line. This is a new requirement, so it needs a ledger ID and a test; proposed ID `INST1`, Layer 5. Not built: no .NET SDK or WiX on this machine.
