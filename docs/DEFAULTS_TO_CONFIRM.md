# Default lines to confirm (sections 22 to 24)

These were set after Michael's last full review. I will build them as written. Reply "ok" or change any line.

| ID | Line (short) |
|---|---|
| ST2 | Forever, per photo, only a record of a few hundred bytes (time, block, type, pass/fail with distance band, match score, fingerprint). |
| ST3 | Reference photos kept at ~300 KB; a new one replaces the old. |
| ST4 | Thumbnails ≤ 25 MB (oldest first); raw sleep events 30 days; tamper log 1 year; hosts log 90 days; all data < 150 MB after 90 days. |
| ST5 | Storage meter and clear-thumbnails button; clearing never changes verification. |
| ST7 | Compress at capture; strip location from image; no coordinates stored except home and venues. |
| LA1 | Never hold calls, emergency calls, alarms, authenticator codes, banking alerts, MyUoB notices. |
| LA2 | One time-ordered digest after the block; nothing deleted. |
| LA3 | Restart within 2 s; after 3 crashes in 10 min hand over to default home; blocking keeps running. |
| LA5 | Search opens the keyboard; no recent-apps list. |
| LA6 | Android Settings stays open; own accessibility, app-info, uninstall, default-home pages go through the wall (best effort). |
| LA7 | Blocking covers split screen, floating windows, picture-in-picture. |
| LA9 | Passing the wall mid-block ends the block as broken and opens the app 30 min. Outside a block: 30 min. |
| LA10 | Back, home, recents from the card return to launcher with no loop. |
| LA11 | Lock screen shows a persistent Buffer notification; quick-add via notification action and quick-settings tile. |
| LA12 | OLED black, system text size, TalkBack labels, strong contrast. (Conflicts with AP2: see Q-C2.) |
| LA13 | Whole-app battery ≤ ~5% per day. |
| WG5 | Third-party widgets off by default; turning on takes effect next day; blocked-list apps' widgets never allowed. |
| WG6 | Third-party widgets hidden in work blocks, getting ready, transitions and after the cutoff. |
| WG8 | Tapping a built-in widget opens its full screen. |
| WG9 | Stale data shows "stale since HH:MM", never a wrong number. |
| AP4 | Auto = Light/Dark by daylight; Night from shutdown photo or cutoff, ends when morning alarm stops. |
| AP5 | Colourful: Buffer yellow, now pink, next green, spend blue, black outlines. |
| AP7 | Mode changes apply at once and never hide a block or lower a rule. |

Note: the build prompt also lists **K10** among the Defaults; the ledger marks it **Locked**. Treated as binding either way.

**Needs your answer more than the rest:** Q-C2 (black-and-white launcher vs equal modes) and Q-G5 (wall lengths beyond 75). See `QUESTIONS.md`.
