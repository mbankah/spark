# Questions, conflicts and gaps

Written 23 Oct 2026 after reading the whole ledger and seed. Each item says what I will build until Michael answers, using the rule in the build prompt: take the reading that makes the app harder to escape and cheaper to start. Nothing here blocks Layer 1 unless marked BLOCKS.

## Conflicts between ledger lines

| # | Lines | Conflict | Reading I will build |
|---|---|---|---|
| Q-C1 | WG1 / U5 vs AP1 | WG1 says the default home is the text-only layout of U5. AP1 (§24, later) adds a dial for the Buffer and `DialHome` is drawn. | §24 wins: text-only home with the Buffer dial. No icons, badges or grid. |
| Q-C2 (answered) | LA12 vs AP2 / AP4 | LA12 says the launcher is black with white text, light theme optional. AP2 makes Light, Dark, Night, Colourful and Auto equal modes. | ANSWERED 23 Oct: default mode is Auto, following the system. Light = ColdLight, Dark = ColdDark (cold is the default palette, overriding "Neutral" in the build prompt). No forced black-and-white. |
| Q-C3 | AP4 vs A8 | Auto uses Light or Dark "by daylight", which needs a location. A8 forbids continuous location. | Sunrise and sunset are computed offline from the stored home point (A8 allows storing it). No location read. |
| Q-C4 | U2 / U1 vs build prompt rule 9 | U2 requires the microphone permission at setup. Prompt says no mic use unless voice is on. | Permission is requested at setup (U1). The mic is opened only while the user holds the voice button and voice is switched on (G7). |
| Q-C5 | F1 to F5 (§20) vs F1 to F7 (§10) | Same IDs, different meanings. | §20 items are tracked as R20-F1 to R20-F5. |
| Q-C6 | A7 vs ST1 to ST3 | A7 says photos stay on the device. §22 says full photos are discarded, thumbnails kept 14 days. | §22 wins. |
| Q-C7 | D1 vs seed windows | D1 says a study cycle is about 2.5 h or 1 h. Seed windows vary (Monday standard cycle is 2h05). | Buffer uses the ledger estimate; the block keeps its seed window and the shortfall shows in the working. |
| Q-C8 | Seed B8 vs ledger M6 / K1 | Seed tiers include games, shopping and always-blocked apps. Ledger blocks only the social list chosen at setup, plus YouTube. | Only the ledger list. The picker pre-selects the seed "Social and video" tier. Games and shopping are not pre-selected. |
| Q-C9 | Seed Mon/Sat vs section 3 | Removing Monday 20:00-22:00 and Saturday 16:45-18:45 football leaves dependent blocks (pre-football meal, Aldi run, travel, buffers). Ledger does not say what happens to them. | Dependent travel and buffer blocks are removed with the football. Meals and the Aldi run stay. Freed time becomes free slots so personal football (3 h) can be placed. Editable in app. |

## Gaps (values the ledger does not give)

| # | Gap | Value I will use | Why |
|---|---|---|---|
| Q-G1 | Heaviness to hours (B4, B5). | Slider 1 to 10, hours = base per task kind × (0.5 + heaviness/10). Study cycle standard 2.5 h and light 1 h are fixed by D1. | Needed for any Buffer. Calibration (Layer 7) replaces it. |
| Q-G2 | "Hard" vs "easy" task for B8 (×1.5 / ×1.15). | Heaviness 6 or more is hard. | B8 says the number is adjustable, so it is a setting. |
| Q-G3 | Weight in R8 / Y4 ratio. | Weight = heaviness × planned hours. | Linear and weighted by heaviness. |
| Q-G4 | What "time available" counts in the Buffer (B1). | Waking hours to the deadline minus meals, fixed commitments, transitions, resets and protected rest. Working shown on tap. | Prompt fixture "+4.5h from 17.5 available minus 13.0 work" is not in the ledger. I will build to that fixture. |
| Q-G5 | Wall lengths beyond 75 (M7, N4, K2, LA9, F4, H1, H3). | M7, K2, LA9, F4: 75. N4: +25 per extra override in the night. H1: 75, 100, 130, 165, 205 (steps 25, 30, 35, 40). H3: +40 same-day. CONFIRMED by Michael 23 Oct. | Ledger gives "75, 100, 130, and so on" only. |
| Q-G6 | Target wake on days with no early fixed commitment (R20-F1). | Earlier of the seed wake time and first commitment minus getting-ready. Sat/Sun: seed 08:30 until set by hand. | Gives 07:30 on Monday, cutoff 23:10. |
| Q-G7 | Day boundary for "fully verified day" (N5). | Midnight local. | Needed for the test. |
| Q-G8 | Essentials (U6). | Build prompt adds an audio app; I include it. | Matches M9. |
| Q-G9 | Home radius (L3). | 250 m, the same distance as the L2 weaker-proof threshold, so there is no gap between rejected and accepted. Measured at setup against venue distances (O4). | "Inside the home radius" has no number. |
| Q-G10 | "Quiet stretch" detection ≥ 60 min (R20-F3) is given; fall-asleep estimate adapts how? | Fixed 20 min until Layer 2 data exists. | N1 Default. |

| Q-G11 | Which deadline sets the Buffer when there are several (B1, B9). | For each deadline: work-capable time before it minus all work due by it. The smallest is the Buffer and its deadline is named in the working. | Plain earliest-deadline feasibility. |
| Q-G12 | What the dial shows (AP1 draws it, no rule says what it encodes). | Share of due work that fits in the time available (full when it fits). Never decorative. | P4: no fake pressure. |
| Q-G1 (revised) | Quick-add has only a heaviness slider, so hours cannot come from a per-kind base. | Hours = 1.25 x heaviness (5 = 6.25 h, 8 = 10 h). Study cycles stay 2.5 h and 1 h (D1), heaviness 6 and 3. | Calibration (layer 7) replaces it. |
| Q-G13 | What counts as available time (Q-G4 made concrete). | Study and project blocks only (types study, study-standard, study-light). Free time, meals, travel, chores and routine are not counted. Past cycles are not tracked until work blocks give them proof. | Free slots convert only by the F3 margin rule in layer 6. |
| Q-G14 | The heaviness slider needs a Slider widget. | A row of 10 steps (TalkBack friendly) is used until a real slider is built. | Compose foundation has no slider. |

| Q-G15 | What "on time" means for a work-block proof (W1, B7). | A photo within 15 minutes after the timer ends is on time. Later, until the next planned wake, it is recovered: credited up to the 1 hour left that night and marked recovered. | The ledger gives the cap and the deadline but not the on-time window. |
| Q-G16 | Code blocks are proved by a git commit checked automatically (G1). | BUILT. Public repos only, no token stored. A private repo cannot be checked (it reports "not found or private"). STILL OPEN: do you want private repos? That needs a GitHub token stored on the phone, which the ledger does not allow without your decision. | Unauthenticated GitHub allows 60 requests an hour. |
| Q-G22 | Commit window and reuse (G1). | A commit counts if dated from the block start to 15 minutes after its end, and one commit proves one block (the seed says one commit per block). The author date is used. | Git dates are set by your own computer, so a commit can be back-dated with effort. This makes faking take deliberate effort but cannot make it impossible. |
| Q-G23 | Which repo proves which block. | Any configured repo proves any code block. | The ledger does not tie blocks to repos. |
| Q-G17 | Duplicate-photo threshold (ST2). | Reject when the 64-bit fingerprint differs by 4 bits or fewer. | Needs real-phone tuning: too loose rejects honest retakes of the same desk. |

| Q-G18 | Home point precision (L8). | Saved rounded to 3 decimal places, about 100 m. It is set by moving a map and never reads the phone's location, so A8 holds. | L8 says set once on a map with no house-level detail. |
| Q-G19 | Map tiles. | The map loads OpenStreetMap tiles, so the tile server sees which area was viewed. The tile cache is capped at 20 MB. | Needed for a map. Say if you would rather type coordinates. |
| Q-G20 | Indoor GPS (E11). | A fix worse than 250 m is treated as no fix and asks for a retake. The 150 m building test is measured from the building centre. | Needs real-phone tuning (V6, V10). |
| Q-G21 | The Vaughan Jeffreys room (Education). | Matched to the Education Building as a whole, because the room is not its own OpenStreetMap feature. | Check on the phone (V11). |

## Things I cannot do and will say so

- Android Studio SDK is not installed here (no `adb`, Gradle or Android SDK). Pure logic runs on JDK 17 and Node 24. Everything touching Android needs the SDK and a phone.
- .NET SDK and Rust are not installed, so the Windows service (Layer 5) cannot be built or run here yet.
- LA14 (100 reboots), S7 (overnight), K10 (browsers), E7 (antivirus) and all of section 10 of the build prompt need Michael's phone and PC.
