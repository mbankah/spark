# Executive Function

An executive-function app for one user, built from `DECISIONS_LEDGER.md`: the Buffer (hours available before a deadline minus work still to do), work blocks that end with proof, and blocking of chosen apps. Android first, then web and a Windows component.

Status: Layer 1 in progress. See `docs/LAYER_1_REPORT.md` for what is built, tested and not yet proven on a real device.

## Layout

| Path | What it is |
|---|---|
| `DECISIONS_LEDGER.md` | The source of truth. Every behaviour has an ID. |
| `BUILD_PROMPT.md` | How the app is built and proved. |
| `core/` | Pure Kotlin logic (Buffer, money, night, proof rules). Tested on the JVM. |
| `app/` | Android app (Compose, Room, CameraX). |
| `design/`, `seed/` | Screen drawings and the week type B timetable. |
| `docs/` | Traceability, questions, decisions, device test checklist, reports. |
| `tools/` | Generators for traceability, the seeded week and the OpenStreetMap venue directory. |

## Run the tests

```
.\gradlew.bat :core:test
.\gradlew.bat :app:connectedDebugAndroidTest   # needs an emulator or phone
```

Needs JDK 17 and the Android SDK (`local.properties` with `sdk.dir`).

Venue data: OpenStreetMap contributors, ODbL.
