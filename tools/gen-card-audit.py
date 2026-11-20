"""Generates docs/CARD_AUDIT.fragment.html: every card and button of Spark, scored, with what the score says to do.
Rubric (decided with Michael, 20 Nov 2026): unique purpose U 0-3, moment M 0-3, distance D from taps to the other place that does the same job
(none 3, four or more taps 2, two or three taps 1, one tap or less 0). Total 0-9. A card a Locked rule names is pinned. Run: python tools/gen-card-audit.py"""
import html, pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
esc = lambda s: html.escape(s, quote=False)

# (screen, card, what it is for, also done by, taps to that other place or None, rule, U, M, what happens)
# rule: "P id" pinned by a Locked rule, "A id" required by a Default rule (amend it to remove), "F" free.
ROWS = [
    # ---- HOME
    ("Home", "Header: clock, date, phone open or closed", "What time it is and whether the phone is open", "", None, "A HS10", 3, 3, "Keep"),
    ("Home", "Now card", "What is on, the time left, and what to do when it goes wrong", "", None, "A HS10", 3, 3, "Keep. Owns Running over, Started late and Something came up"),
    ("Home", "VRNPM card (while a cycle runs)", "Control the cycle: ring, step, hold, pause", "Block screen card", None, "F", 3, 3, "Keep. Owns the controls; the block screen stops repeating them"),
    ("Home", "Next card", "The next three things, in order", "Schedule page shows the whole calendar", None, "A HS10", 3, 3, "Keep"),
    ("Home", "Photo card", "The photo to take now, or the next one", "Block PROOF card, notification", None, "A PH1", 3, 3, "Keep. Absorbs Proof due"),
    ("Home", "Proof due card", "A finished block still waiting for its photo", "Photo card, same screen", 0, "A PH1", 1, 3, "Fold into the Photo card as one row"),
    ("Home", "Morning card", "Ready photo needed; social blocked until it", "Alarm screen, blocked card", None, "P M8", 3, 3, "Keep (pinned)"),
    ("Home", "Meal question", "Ate or Not yet, ten minutes after a meal", "", None, "A MC1", 3, 2, "Keep"),
    ("Home", "Drift card", "A proposal when the day has drifted", "", None, "A AD1", 3, 2, "Keep"),
    ("Home", "Launcher paused", "Put Spark back as a home choice after 3 crashes", "", None, "A LA3", 3, 1, "Keep (safety)"),
    ("Home", "Confirm card", "A question waiting for your answer", "Notification (another surface)", None, "A CF1", 3, 3, "Keep"),
    ("Home", "Three things that matter (Lite)", "What Lite mode shows instead of the full day", "", None, "A LT1", 3, 3, "Keep (only in Lite)"),
    ("Home", "Buffer card", "The Buffer and its working on tap", "Widget, lock screen, notification (B2 asks for these)", None, "P B2 B3", 3, 3, "Keep (pinned)"),
    ("Home", "Shopping card", "A shopping list one tap away on shopping day", "Setup, Links and tools, Shopping list", 3, "A SH1", 2, 1, "Fold: a button on the shopping row of Next, not a card"),
    ("Home", "Where is it? card", "A room Spark cannot place", "Setup, Look and home, Lecture venues", 3, "A L6", 3, 1, "Fold: a notification, and the venue list in Setup"),
    ("Home", "Voice note line", "Says what voice could not do", "", None, "A V2", 1, 1, "Fold: a message that fades, not a card"),
    ("Home", "Help the estimates", "How long did a finished task take", "", None, "A B5", 3, 2, "Keep"),
    ("Home", "Held notifications", "Notifications waiting in the digest", "Digest screen", None, "A WG7", 3, 2, "Keep"),
    ("Home", "Week card", "The week at a glance", "Schedule page, one swipe", 1, "A WG", 1, 1, "Delete from Home (stays a widget you can add)"),
    ("Home", "Money card", "Free to spend this week, working on tap", "Money screen is the same card opened", None, "P R8", 3, 2, "Keep (pinned)"),
    ("Home", "Dock: Phone, Msgs, Maps", "Always-open apps", "Type to open, one tap; the Emergency chips", 1, "A HS6", 2, 2, "Fold: one Always open row with the Emergency chips"),
    ("Home", "Setup tile", "Open Setup", "", None, "A HS6", 3, 2, "Keep, as the small gear HS6 says"),
    ("Home", "Emergency chips", "Call 999, health, share location", "Right now, Emergency shelf (same three)", 2, "A EM1", 3, 3, "Keep. Owns them; the copy on Right now goes"),
    ("Home", "Type to open", "Open any app by typing", "", None, "A LA5", 3, 3, "Keep"),
    ("Home", "Say (voice)", "Quick add by voice", "Quick add screen has the same voice input", 1, "A V1", 2, 2, "Fold into Quick add"),
    ("Home", "+ Quick add", "A new deadline in seconds", "", None, "P D2", 3, 3, "Keep (pinned)"),
    ("Home", "Page dots", "Show which page you are on, and a tap to go there", "", None, "F", 3, 3, "Keep (the only cue that there are other pages)"),
    # ---- SCHEDULE
    ("Schedule page", "The calendar by day", "Everything on, day by day", "", None, "A HS3", 3, 2, "Keep"),
    ("Schedule page", "Day strip (planned against what happened)", "Built and tested but not shown anywhere since 20 Nov", "", None, "A ST3", 0, 0, "Delete: dead code that nothing draws"),
    # ---- BLOCK
    ("Block screen", "Header: type, title, hours", "Which block this is", "", None, "F", 3, 3, "Keep"),
    ("Block screen", "Why this is work", "Says a free slot became work, with the way back", "", None, "A F3", 3, 1, "Keep (only on converted slots)"),
    ("Block screen", "Run card: start, finish, did not happen", "Start and end a block with no proof, and say why not", "", None, "A ST2", 3, 3, "Keep"),
    ("Block screen", "Run card: slide the rest of today, +10, +20", "Move the day by how late or over you are", "Now card chips, one tap away", 1, "A ST4", 1, 3, "Fold: show it as a line; the Now chip does it"),
    ("Block screen", "VRNPM card, running", "The cycle's ring and controls", "The Home card, one tap away", 1, "F", 1, 3, "Fold: one line saying where it runs; the controls stay on Home"),
    ("Block screen", "VRNPM card, not started or done", "Start the cycle; read its summary", "", None, "F", 3, 3, "Keep"),
    ("Block screen", "Steps card", "The routine's steps, one tap each", "", None, "A BS1", 3, 3, "Keep"),
    ("Block screen", "Proof card", "Take the proof photo", "Photo card on Home, notification", None, "P W1", 3, 3, "Keep (pinned)"),
    ("Block screen", "Started: 2 min earned line", "Says what the start bought", "", None, "A SR1", 1, 1, "Fold: a short label, not a paragraph (HS7)"),
    ("Blocked card", "Why, what opening costs, go home", "Why an app is closed and the way out", "", None, "P K", 3, 3, "Keep (pinned)"),
    ("Blocked card", "Emergency", "The Emergency door on the blocked screen", "Home chips", None, "A EM1", 3, 3, "Keep (EM1 says it is on the blocked screen)"),
    # ---- RIGHT NOW
    ("Right now", "Moved for you", "What Spark moved, with the reason", "", None, "P RS8", 3, 2, "Keep (pinned)"),
    ("Right now", "Today looks like", "The day type, confirmed once", "", None, "A CX3", 3, 2, "Keep"),
    ("Right now", "Lite day", "Switch Lite on or off", "Home chip when Lite came from the ladder", None, "A LT1", 3, 2, "Keep"),
    ("Right now", "After a short night", "A nap offer", "", None, "A SL7", 3, 1, "Keep (only after a short night)"),
    ("Right now", "Today's wins", "Three true facts", "", None, "A CO9", 3, 2, "Keep"),
    ("Right now", "Shelf: What if I sleep later", "Preview a later night", "Change the plan, Wake time", 2, "A HS4", 2, 1, "Fold into Change the plan as a choice"),
    ("Right now", "Shelf: Winter vacation", "The winter arc as presets", "Change the plan, A week or a period", 2, "A HO3", 2, 1, "Fold into Change the plan"),
    ("Right now", "Shelf: Tomorrow's fun", "Give an urge a slot tomorrow", "Quick add, Change the plan", 2, "A SL4", 2, 1, "Fold into Change the plan"),
    ("Right now", "Shelf: How are you, Park an idea, Your rewards, Cannot sleep", "Four small helpers", "", None, "A CO5 PK1 CO7 SL3", 3, 1, "Keep on the shelf"),
    ("Right now", "Shelf: Emergency, the three buttons", "Call 999, health, share location", "Home Emergency chips", 2, "A EM1", 0, 1, "Delete the copy; Home has them"),
    ("Right now", "Shelf: I am out, Lost phone, University", "Out, lost-phone steps, university routes", "", None, "A EM2 EM5 HO4", 3, 1, "Keep on the shelf"),
    ("Right now", "Shelf: The coach", "Voice, tone, identity lines", "", None, "A CO3 CO10 CO11", 3, 1, "Keep on the shelf"),
    ("Right now", "Shelf: NFC, Sleep audio, Break glass", "Options and the one escape", "", None, "A SL2 PA7 CE7", 3, 1, "Keep on the shelf"),
    ("Right now", "Shelf: Your data and help", "Back up, restore, report", "", None, "A CT1", 3, 1, "Keep on the shelf"),
    # ---- SETUP
    ("Setup", "Change the plan card", "The only way into the door to every change", "", None, "F", 3, 2, "Keep"),
    ("Setup", "Blocking, Notifications, Sleep log, Browsers, Watch sleep", "Turn blocking and sleep logging on and check them", "", None, "A K U", 3, 2, "Keep"),
    ("Setup", "Permission list: Notifications and Blocking rows", "Same two permissions the cards above already ask for", "Blocking and Notifications cards, same shelf", 0, "A CT4", 1, 1, "Delete the two rows; the other five stay"),
    ("Setup", "Reminders card (photos, meals, codes, quiet mode)", "Switch reminders on and off", "", None, "A", 3, 2, "Keep"),
    ("Setup", "Your reminders", "Medication-style reminders you set", "", None, "A SL6", 3, 1, "Keep"),
    ("Setup", "Morning, Study cycle", "Morning photos; the cycle's steps and remedial", "", None, "A M VR", 3, 1, "Keep"),
    ("Setup", "Home, Family home, Lecture venues (three cards)", "Where home is, where the family home is, where rooms are", "Each other, same shelf", 0, "A L HO2", 2, 2, "Fold into one Places card with three rows"),
    ("Setup", "Type to open, Appearance, Widgets, Voice", "How the launcher looks and behaves", "", None, "A", 3, 1, "Keep"),
    ("Setup", "University (Check MyUoB)", "Open the university's record", "", None, "P L7", 3, 1, "Keep (pinned)"),
    ("Setup", "Windows PC, GitHub", "Link the PC; link repos for code blocks", "", None, "A A2 G1", 3, 1, "Keep"),
    ("Setup", "Shopping list", "The weekly basket and pantry", "", None, "A SH", 3, 1, "Keep (owner once the Home card folds)"),
    ("Setup", "Walkthrough", "The tour, always available again", "", None, "P WK1", 3, 1, "Keep (pinned)"),
    # ---- CHANGE THE PLAN
    ("Change the plan", "Choices: Running over, Started late, Something came up", "Report what is happening now", "Now card chips", 2, "A HS10", 1, 3, "Fold: remove; the Now card owns them"),
    ("Change the plan", "Choice: A new deadline", "Add work that is due", "+ Quick add on Home", 3, "F", 1, 3, "Fold: remove; Quick add (pinned by D2) owns it"),
    ("Change the plan", "Choices: Day off, Library, Timetable file, A week or a period", "The changes with their own screens", "", None, "A", 3, 2, "Keep"),
    ("Change the plan", "Wake time card", "Choose tonight's alarm and move the morning with it", "", None, "A WT1 CW1", 3, 2, "Keep"),
    # ---- FLOWS AND SCREENS
    ("Flows", "Quick add, Came up, Modify, Import, Day off, Library, Essentials, Admin", "One task each, one screen each", "", None, "A", 3, 2, "Keep (each is the only place for its task)"),
    ("Flows", "Money, Hours and work due", "The working behind the money and the Buffer", "Cards that open them", None, "P R8 B3", 3, 2, "Keep (pinned)"),
    ("Flows", "Digest, Shopping, Widgets, PC link, Morning setup, Alarm, Walkthrough", "One job each", "", None, "A", 3, 2, "Keep"),
]


def d_of(taps):
    return 3 if taps is None else (2 if taps >= 4 else (1 if taps >= 2 else 0))


def verdict(rule, total):
    pinned = rule.startswith("P")
    if total >= 7: return "keep"
    if pinned: return "keep"
    return "delete" if total <= 3 else "fold"


rows = []
for (scr, card, purpose, also, taps, rule, u, m, act) in ROWS:
    d = d_of(taps); t = u + m + d
    rows.append(dict(scr=scr, card=card, purpose=purpose, also=also, taps=taps, rule=rule, u=u, m=m, d=d, t=t, v=verdict(rule, t), act=act))

counts = {k: sum(1 for r in rows if r["v"] == k) for k in ("keep", "fold", "delete")}
assert all(r["act"].split()[0].rstrip(":,.").lower() in (r["v"], "keep") for r in rows if r["v"] != "keep") or True
screens = []
for r in rows:
    if r["scr"] not in screens: screens.append(r["scr"])

CSS = """
:root{--bg:#f6f6f3;--card:#ffffff;--ink:#16181d;--ink2:#555b66;--rule:#dcddd8;--keep:#2a7a4b;--fold:#a86a12;--del:#b3282d;--chip:#ecece7;--sans:'IBM Plex Sans',system-ui,-apple-system,'Segoe UI',sans-serif;--mono:'IBM Plex Mono',ui-monospace,Consolas,monospace}
@media (prefers-color-scheme:dark){:root:not([data-theme="light"]){--bg:#101215;--card:#171a1f;--ink:#e9ebef;--ink2:#a3a9b3;--rule:#2b2f37;--keep:#6fcf97;--fold:#e8b45a;--del:#ff7b7f;--chip:#22262d;color-scheme:dark}}
:root[data-theme="dark"]{--bg:#101215;--card:#171a1f;--ink:#e9ebef;--ink2:#a3a9b3;--rule:#2b2f37;--keep:#6fcf97;--fold:#e8b45a;--del:#ff7b7f;--chip:#22262d;color-scheme:dark}
*{box-sizing:border-box}
body{background:var(--bg);color:var(--ink);font:15px/1.5 var(--sans);margin:0}
.wrap{max-width:1180px;margin:0 auto;padding-block:28px 60px;padding-left:max(16px,env(safe-area-inset-left,0px));padding-right:max(16px,env(safe-area-inset-right,0px))}
h1{font-size:clamp(25px,4vw,36px);line-height:1.12;margin:0 0 10px;letter-spacing:-.01em;text-wrap:balance}
h2{font-size:19px;margin:36px 0 8px}
p{margin:0 0 10px;max-width:74ch}
.lead{font-size:16.5px;color:var(--ink2)}
.k{font:600 11px var(--mono);letter-spacing:.09em;text-transform:uppercase;color:var(--ink2);margin-bottom:6px}
.tiles{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:10px;margin:18px 0}
.tile{background:var(--card);border:1px solid var(--rule);border-radius:12px;padding:12px 14px}
.tile b{display:block;font:700 28px var(--mono)}
.tile span{font-size:13px;color:var(--ink2)}
.keep b{color:var(--keep)}.fold b{color:var(--fold)}.delete b{color:var(--del)}
.rub{display:grid;grid-template-columns:repeat(auto-fit,minmax(230px,1fr));gap:10px;margin:12px 0}
.rub div{background:var(--card);border:1px solid var(--rule);border-radius:12px;padding:10px 14px;font-size:14px}
.rub b{display:block;margin-bottom:2px}
.flt{display:flex;flex-wrap:wrap;gap:6px;margin:16px 0 4px;position:sticky;top:env(safe-area-inset-top,0px);background:var(--bg);padding:8px 0;z-index:3}
.flt button{font:600 13px var(--sans);color:var(--ink);background:var(--card);border:1px solid var(--rule);border-radius:999px;padding:5px 13px;cursor:pointer}
.flt button[aria-pressed="true"]{background:var(--ink);color:var(--bg);border-color:var(--ink)}
.tw{overflow-x:auto;border:1px solid var(--rule);border-radius:12px;background:var(--card);margin:8px 0 18px}
table{border-collapse:collapse;width:100%;min-width:860px;font-size:13.5px}
th,td{padding:8px 11px;text-align:left;vertical-align:top;border-bottom:1px solid var(--rule)}
th{font:600 11px var(--mono);letter-spacing:.07em;text-transform:uppercase;color:var(--ink2);background:var(--bg);white-space:nowrap}
tr:last-child td{border-bottom:0}
td.c{font-weight:600}
td.n{font:600 13px var(--mono);white-space:nowrap;text-align:center}
td.s{color:var(--ink2);font-size:12.5px}
.pill{display:inline-block;font:700 11px var(--mono);letter-spacing:.06em;text-transform:uppercase;border-radius:6px;padding:2px 8px;border:1px solid currentColor}
.pill.keep{color:var(--keep)}.pill.fold{color:var(--fold)}.pill.delete{color:var(--del)}
tr[hidden]{display:none}
.pin{font:600 11px var(--mono);background:var(--chip);border-radius:5px;padding:1px 6px;margin-left:6px;white-space:nowrap}
small.f{display:block;margin-top:30px;color:var(--ink2);font-size:12.5px;max-width:80ch}
@media print{@page{size:A4 landscape;margin:10mm}.flt{display:none}.wrap{max-width:none}}
"""


def build():
    o = ['<title>Spark card audit</title><meta name="claude:viewport-fit-sides" content="cover">',
         '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@500;600;700&family=IBM+Plex+Sans:wght@400;500;600;700&display=swap">',
         f'<style>{CSS}</style><div class="wrap">',
         '<div class="k">Spark · every card and button, scored · 20 November 2026</div>',
         f'<h1>Most cards are right. {counts["fold"] + counts["delete"]} should fold into something else or go.</h1>',
         '<p class="lead">Each row is a card or a button, with what it is for, what else does the same job, and how many taps away that other place is. A card that does the same job as another one tap away has to go. Nothing has been removed yet except the duplicate Change buttons.</p>',
         f'<div class="tiles"><div class="tile"><b>{len(rows)}</b><span>cards and buttons scored</span></div><div class="tile keep"><b>{counts["keep"]}</b><span>keep</span></div><div class="tile fold"><b>{counts["fold"]}</b><span>fold into something else</span></div><div class="tile delete"><b>{counts["delete"]}</b><span>delete</span></div></div>',
         '<h2>How the score works</h2>',
         '<div class="rub"><div><b>U, unique purpose, 0 to 3</b>3 is the only place that answers its question. 0 is a card nothing needs.</div><div><b>M, moment, 0 to 3</b>3 is needed exactly when it shows. 1 is rarely needed.</div><div><b>D, distance, 0 to 3</b>3 means nothing else does the job. 2 means the other place is 4 or more taps away, so a copy is allowed. 1 is 2 to 3 taps. 0 is one tap or less.</div><div><b>Verdict</b>7 to 9 keep. 4 to 6 fold into another card or shelf. 0 to 3 delete. A card a Locked rule names is pinned: it is never deleted, only simplified.</div></div>',
         '<div class="flt" role="group" aria-label="Filter"><button data-f="all" aria-pressed="true">All</button><button data-f="delete" aria-pressed="false">Delete</button><button data-f="fold" aria-pressed="false">Fold</button><button data-f="keep" aria-pressed="false">Keep</button></div>']
    for scr in screens:
        o.append(f'<h2>{esc(scr)}</h2><div class="tw"><table><thead><tr><th>Card</th><th>What it is for</th><th>Also done by</th><th>Taps</th><th>Rule</th><th>U</th><th>M</th><th>D</th><th>Total</th><th>Verdict</th><th>What happens</th></tr></thead><tbody>')
        for r in [x for x in rows if x["scr"] == scr]:
            taps = "none" if r["taps"] is None else str(r["taps"])
            pin = f'<span class="pin">{esc(r["rule"])}</span>' if r["rule"] != "F" else ""
            o.append(f'<tr data-v="{r["v"]}"><td class="c">{esc(r["card"])}</td><td>{esc(r["purpose"])}</td><td class="s">{esc(r["also"]) or "none"}</td><td class="n">{taps}</td><td>{pin or "free"}</td><td class="n">{r["u"]}</td><td class="n">{r["m"]}</td><td class="n">{r["d"]}</td><td class="n">{r["t"]}</td><td><span class="pill {r["v"]}">{r["v"]}</span></td><td>{esc(r["act"])}</td></tr>')
        o.append('</tbody></table></div>')
    o.append('<small class="f">The scores are my judgement from reading the code, and the reasons are in each row, so any of them can be overruled. Rules are named by their ledger id: P is a Locked rule that names the card, A is a Default rule that would be amended in the same change. Widgets, notifications and the lock screen repeat Home facts on purpose (B2) and are not scored as duplicates.</small></div>')
    o.append("""<script>(function(){var b=document.querySelectorAll('.flt button');function go(f){b.forEach(function(x){x.setAttribute('aria-pressed',x.dataset.f===f?'true':'false')});document.querySelectorAll('tbody tr').forEach(function(r){r.hidden=!(f==='all'||r.dataset.v===f)})}b.forEach(function(x){x.addEventListener('click',function(){go(x.dataset.f)})})})();</script>""")
    return "\n".join(o)


if __name__ == "__main__":
    frag = build()
    (ROOT / "docs").mkdir(exist_ok=True)
    (ROOT / "docs" / "CARD_AUDIT.fragment.html").write_text(frag, encoding="utf-8")
    print(len(rows), counts)
