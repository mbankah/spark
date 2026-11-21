"""Generates docs/CARD_AUDIT.fragment.html: every card and button of Spark, scored, with what the score says to do. Round 5 adds the similarity graph (tools/card_graph.py).
Rubric (decided with Michael, 20 Nov 2026, extended to 100 points the same day): unique purpose 15, distance to the other place that does the same job 20 (a copy is allowed only
at 4 or more taps), practical utility 20, moment 10, how often it is used 10, how little room it takes 5, what breaks without it 10, the rule behind it 10. Unique purpose is 15. A card a Locked rule names is pinned. Run: python tools/gen-card-audit.py"""
import html, pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
esc = lambda s: html.escape(s, quote=False)

# (screen, card, what it is for, also done by, taps to that other place or None, rule, U, M, what happens)
# rule: "P id" pinned by a Locked rule, "A id" required by a Default rule (amend it to remove), "F" free.
ROWS = [
    # ---- HOME
    ("Home", "Header: clock, date, phone open or closed", "What time it is and whether the phone is open", "", None, "A HS10", 3, 3, "Keep"),
    ("Home", "Now card", "What is on, the time left, and what to do when it goes wrong", "", None, "A HS10", 3, 3, "Keep. Owns Running over, Started late and Something came up"),
    ("Home", "Now card chips: Running over, Started late, Something came up", "Report what is happening now and move the day", "Change the plan choices, Run slide buttons", None, "A HS10", 3, 3, "Keep. The one place for these three"),
    ("Home", "VRNPM card (while a cycle runs)", "Control the cycle: ring, step, hold, pause", "Block screen card", None, "F", 3, 3, "Keep. Owns the controls; the block screen stops repeating them"),
    ("Home", "Next card", "The next three things, in order", "Schedule page shows the whole calendar", None, "A HS10", 3, 3, "Keep"),
    ("Home", "Photo card", "The photo to take now, or the next one", "Block PROOF card, notification", None, "A PH1", 3, 3, "Keep. Absorbs Proof due"),
    ("Home", "Proof due card", "A finished block still waiting for its photo", "Photo card, same screen", 0, "A PH1", 1, 3, "Becomes one row of the Photo card"),
    ("Home", "Morning card", "Ready photo needed; social blocked until it", "Alarm screen, blocked card", None, "P M8", 3, 3, "Keep (pinned)"),
    ("Home", "Meal question", "Ate or Not yet, ten minutes after a meal", "", None, "A MC1", 3, 2, "Keep"),
    ("Home", "Drift card", "A proposal when the day has drifted", "", None, "A AD1", 3, 2, "Stays in the waiting list, after the cards above it"),
    ("Home", "Launcher paused", "Put Spark back as a home choice after 3 crashes", "", None, "A LA3", 3, 1, "Keep (safety)"),
    ("Home", "Confirm card", "A question waiting for your answer", "Notification (another surface)", None, "A CF1", 3, 3, "Keep"),
    ("Home", "Three things that matter (Lite)", "What Lite mode shows instead of the full day", "", None, "A LT1", 3, 3, "Keep (only in Lite)"),
    ("Home", "Buffer card", "The Buffer and its working on tap", "Widget, lock screen, notification (B2 asks for these)", None, "P B2 B3", 3, 3, "Keep (pinned)"),
    ("Home", "Shopping card", "A shopping list one tap away on shopping day", "Setup, Links and tools, Shopping list", 3, "A SH1", 2, 1, "Turn it into a button on the shopping row of Next, not a card"),
    ("Home", "Where is it? card", "A room Spark cannot place", "Setup, Look and home, Lecture venues", 3, "A L6", 3, 1, "Turn it into a notification; the venue list in Setup stays"),
    ("Home", "Voice note line", "Says what voice could not do", "", None, "A V2", 1, 1, "Delete as a card; show it as a message that fades"),
    ("Home", "Help the estimates", "How long did a finished task take", "", None, "A B5", 3, 2, "Stays in the waiting list, last"),
    ("Home", "Held notifications", "Notifications waiting in the digest", "Digest screen", None, "A WG7", 3, 2, "Keep"),
    ("Home", "Week card", "The week at a glance", "Schedule page, one swipe", 1, "A WG", 1, 1, "Remove from Home; it stays a widget you can add"),
    ("Home", "Money card", "Free to spend this week, working on tap", "Money screen is the same card opened", None, "P R8", 3, 2, "Keep (pinned)"),
    ("Home", "Dock: Phone, Msgs, Maps", "Always-open apps", "Type to open, one tap; the Emergency chips", 1, "A HS6", 2, 2, "Join the Emergency chips in one Always open row"),
    ("Home", "Setup tile", "Open Setup", "", None, "A HS6", 3, 2, "Keep, as the small gear HS6 says"),
    ("Home", "Emergency chips", "Call 999, health, share location", "Right now, Emergency shelf (same three)", None, "A EM1", 3, 3, "Keep. Owns them; the copy on Right now goes"),
    ("Home", "Type to open", "Open any app by typing", "", None, "A LA5", 3, 3, "Keep"),
    ("Home", "Say (voice)", "Quick add by voice", "Quick add screen has the same voice input", 1, "A V1", 2, 2, "Moves into Quick add"),
    ("Home", "+ Quick add", "A new deadline in seconds", "", None, "P D2", 3, 3, "Keep (pinned)"),
    ("Home", "Page dots", "Show which page you are on, and a tap to go there", "", None, "F", 3, 3, "Keep (the only cue that there are other pages)"),
    # ---- SCHEDULE
    ("Schedule page", "The calendar by day", "Everything on, day by day", "", None, "A HS3", 3, 2, "Keep"),
    ("Schedule page", "Day strip (planned against what happened)", "Built and tested but not shown anywhere since 20 Nov", "", None, "A ST3", 0, 0, "Delete: dead code that nothing draws"),
    # ---- BLOCK
    ("Block screen", "Header: type, title, hours", "Which block this is", "", None, "F", 3, 3, "Keep"),
    ("Block screen", "Why this is work", "Says a free slot became work, with the way back", "", None, "A F3", 3, 1, "Keep (only on converted slots)"),
    ("Block screen", "Run card: start, finish, did not happen", "Start and end a block with no proof, and say why not", "", None, "A ST2", 3, 3, "Keep"),
    ("Block screen", "Run card: slide the rest of today, +10, +20", "Move the day by how late or over you are", "Now card chips, one tap away", 1, "A ST4", 1, 3, "Remove the buttons and keep a line saying how late or over you are; the Now card chips do the rest"),
    ("Block screen", "VRNPM card, running", "The cycle's ring and controls", "The Home card, one tap away", 1, "F", 1, 3, "While it runs, show one line saying where; the controls stay on the Home card"),
    ("Block screen", "VRNPM card, not started or done", "Start the cycle; read its summary", "", None, "F", 3, 3, "Keep"),
    ("Block screen", "Steps card", "The routine's steps, one tap each", "", None, "A BS1", 3, 3, "Keep"),
    ("Block screen", "Proof card", "Take the proof photo", "Photo card on Home, notification", None, "P W1", 3, 3, "Keep (pinned)"),
    ("Block screen", "Started: 2 min earned line", "Says what the start bought", "", None, "A SR1", 1, 1, "Delete the paragraph; keep a short label (HS7)"),
    ("Blocked card", "Why, what opening costs, go home", "Why an app is closed and the way out", "", None, "P K", 3, 3, "Keep (pinned)"),
    ("Blocked card", "Emergency", "The Emergency door on the blocked screen", "Home chips", None, "A EM1", 3, 3, "Keep (EM1 says it is on the blocked screen)"),
    # ---- RIGHT NOW
    ("Right now", "Moved for you", "What Spark moved, with the reason", "", None, "P RS8", 3, 2, "Keep (pinned)"),
    ("Right now", "Today looks like", "The day type, confirmed once", "", None, "A CX3", 3, 2, "Keep"),
    ("Right now", "Lite day", "Switch Lite on or off", "Home chip when Lite came from the ladder", None, "A LT1", 3, 2, "Keep"),
    ("Right now", "After a short night", "A nap offer", "", None, "A SL7", 3, 1, "Stays on its shelf; shown only after a short night"),
    ("Right now", "Today's wins", "Three true facts", "", None, "A CO9", 3, 2, "Keep"),
    ("Right now", "Shelf: What if I sleep later", "Preview a later night", "Change the plan, Wake time", 2, "A HS4", 2, 1, "Becomes a choice in Change the plan"),
    ("Right now", "Shelf: Winter vacation", "The winter arc as presets", "Change the plan, A week or a period", 2, "A HO3", 2, 1, "Becomes a choice in Change the plan"),
    ("Right now", "Shelf: Tomorrow's fun", "Give an urge a slot tomorrow", "Quick add, Change the plan", 2, "A SL4", 2, 1, "Becomes a choice in Change the plan"),
    ("Right now", "Shelf: How are you, Park an idea, Your rewards, Cannot sleep", "Four small helpers", "", None, "A CO5 PK1 CO7 SL3", 3, 1, "Keep on the shelf"),
    ("Right now", "Shelf: Emergency, the three buttons", "Call 999, health, share location", "Home Emergency chips", 2, "A EM1", 0, 1, "Remove the copy; Home has them"),
    ("Right now", "Shelf: I am out, Lost phone, University", "Out, lost-phone steps, university routes", "", None, "A EM2 EM5 HO4", 3, 1, "Keep on the shelf"),
    ("Right now", "Shelf: The coach", "Voice, tone, identity lines", "", None, "A CO3 CO10 CO11", 3, 1, "Keep on the shelf"),
    ("Right now", "Shelf: NFC, Sleep audio, Break glass", "Options and the one escape", "", None, "A SL2 PA7 CE7", 3, 1, "Keep on the shelf"),
    ("Right now", "Shelf: Your data and help", "Back up, restore, report", "", None, "A CT1", 3, 1, "Keep on the shelf"),
    # ---- SETUP
    ("Setup", "Change the plan card", "The only way into the door to every change", "", None, "F", 3, 2, "Keep"),
    ("Setup", "Blocking, Notifications, Sleep log, Browsers, Watch sleep", "Turn blocking and sleep logging on and check them", "", None, "A K U", 3, 2, "Keep"),
    ("Setup", "Permission list: Notifications and Blocking rows", "Same two permissions the cards above already ask for", "Blocking and Notifications cards, same shelf", 0, "A CT4", 1, 1, "Remove the two rows; the other five stay"),
    ("Setup", "Reminders card (photos, meals, codes, quiet mode)", "Switch reminders on and off", "", None, "A", 3, 2, "Keep"),
    ("Setup", "Your reminders", "Medication-style reminders you set", "", None, "A SL6", 3, 1, "Keep"),
    ("Setup", "Morning, Study cycle", "Morning photos; the cycle's steps and remedial", "", None, "A M VR", 3, 1, "Keep"),
    ("Setup", "Home, Family home, Lecture venues (three cards)", "Where home is, where the family home is, where rooms are", "Each other, same shelf", 0, "A L HO2", 2, 2, "Becomes one Places card with three rows"),
    ("Setup", "Type to open, Appearance, Widgets, Voice", "How the launcher looks and behaves", "", None, "A", 3, 1, "Stays on its shelf, lowest priority"),
    ("Setup", "University (Check MyUoB)", "Open the university's record", "", None, "P L7", 3, 1, "Keep (pinned)"),
    ("Setup", "Windows PC, GitHub", "Link the PC; link repos for code blocks", "", None, "A A2 G1", 3, 1, "Keep"),
    ("Setup", "Shopping list", "The weekly basket and pantry", "", None, "A SH", 3, 1, "Keep (owner once the Home card folds)"),
    ("Setup", "Walkthrough", "The tour, always available again", "", None, "P WK1", 3, 1, "Keep (pinned)"),
    # ---- CHANGE THE PLAN
    ("Change the plan", "Choices: Running over, Started late, Something came up", "Report what is happening now", "Now card chips", 2, "A HS10", 1, 3, "Remove them here; the Now card owns them"),
    ("Change the plan", "Choice: A new deadline", "Add work that is due", "+ Quick add on Home", 3, "F", 1, 3, "Remove it here; Quick add (pinned by D2) owns it"),
    ("Change the plan", "Choices: Day off, Library, Timetable file, A week or a period", "The changes with their own screens", "", None, "A", 3, 2, "Keep"),
    ("Change the plan", "Wake time card", "Choose tonight's alarm and move the morning with it", "", None, "A WT1 CW1", 3, 2, "Keep"),
    # ---- FLOWS AND SCREENS
    ("Flows", "Quick add, Came up, Modify, Import, Day off, Library, Essentials, Admin", "One task each, one screen each", "", None, "A", 3, 2, "Keep (each is the only place for its task)"),
    ("Flows", "Money, Hours and work due", "The working behind the money and the Buffer", "Cards that open them", None, "P R8 B3", 3, 2, "Keep (pinned)"),
    ("Flows", "Digest, Shopping, Widgets, PC link, Morning setup, Alarm, Walkthrough", "One job each", "", None, "A", 3, 2, "Keep"),
]

# U M Q C K, each 0 to 5, in the order of ROWS above. Second round, scored harshly: 5 is exceptional (the only place AND essential; safety, proof or blocking), 4 is strong, 3 is ordinary, 2 and below is weak.
# A card is rated on what it does for you now, not on what it might do. Same order as ROWS.
RATE = [
    (4, 4, 5, 4, 2), (5, 5, 5, 2, 3), (4, 5, 3, 5, 3), (4, 4, 3, 1, 2), (3, 4, 5, 3, 2), (4, 5, 4, 3, 5), (1, 3, 3, 3, 2), (4, 5, 3, 3, 5), (4, 3, 4, 3, 1), (3, 2, 2, 3, 1), (4, 2, 1, 3, 4),
    (4, 4, 2, 3, 2), (3, 3, 2, 3, 1), (4, 3, 5, 2, 4), (1, 2, 2, 3, 1), (2, 1, 1, 3, 0), (1, 1, 1, 4, 0), (3, 2, 2, 3, 1), (3, 2, 3, 3, 1), (0, 1, 2, 2, 0), (3, 2, 3, 3, 2),
    (1, 3, 4, 3, 2), (4, 2, 2, 5, 2), (4, 5, 1, 4, 5), (4, 4, 5, 4, 3), (1, 2, 2, 5, 0), (4, 4, 5, 5, 3), (3, 3, 5, 5, 1),
    (4, 3, 4, 2, 2), (0, 0, 0, 1, 0),
    (4, 4, 5, 5, 2), (4, 2, 2, 3, 1), (4, 4, 4, 2, 2), (0, 3, 3, 3, 0), (0, 3, 3, 1, 0), (4, 4, 3, 2, 2), (4, 4, 4, 2, 2), (4, 5, 4, 3, 5), (0, 1, 3, 4, 0), (4, 5, 3, 2, 5), (4, 5, 1, 4, 5),
    (4, 3, 3, 3, 2), (3, 2, 4, 3, 1), (4, 2, 2, 4, 1), (4, 1, 1, 4, 0), (3, 2, 3, 4, 0), (1, 1, 1, 3, 0), (1, 1, 1, 3, 0), (1, 1, 2, 3, 0), (3, 1, 2, 4, 1), (0, 1, 1, 3, 0), (4, 1, 1, 3, 3), (3, 1, 2, 4, 0), (4, 1, 1, 4, 2), (4, 1, 1, 4, 3),
    (4, 2, 2, 4, 2), (4, 2, 1, 3, 5), (0, 1, 1, 4, 0), (4, 2, 2, 3, 1), (4, 2, 3, 3, 2), (4, 2, 2, 3, 2), (1, 2, 1, 3, 1), (4, 1, 1, 3, 0), (4, 1, 2, 5, 0), (4, 1, 1, 3, 2), (4, 2, 3, 3, 1), (4, 1, 1, 5, 0),
    (0, 3, 3, 3, 0), (0, 3, 3, 4, 0), (4, 2, 2, 3, 3), (4, 3, 4, 3, 3),
    (4, 2, 3, 2, 3), (4, 2, 3, 2, 3), (4, 2, 2, 2, 2),
]
assert len(RATE) == len(ROWS), (len(RATE), len(ROWS))

# Practical utility, 0 to 5: how much the card really helps you get something done in your day, measured by effort, time or mistakes it saves.
# 5 changes what you do every day; 4 does so most weeks; 3 helps sometimes; 2 is mostly information; 1 is nice to know; 0 changes nothing you do.
RATE_PU = [
    4, 5, 4, 4, 4, 4, 3, 4, 3, 2, 2,
    3, 3, 4, 1, 1, 0, 2, 2, 2, 2, 3, 2, 4, 5, 2, 5, 2,
    4, 0,
    3, 2, 4, 3, 3, 4, 4, 5, 1, 4, 4,
    3, 2, 3, 2, 2, 2, 2, 2, 3, 4, 3, 3, 2, 3,
    3, 4, 3, 3, 4, 3, 2, 2, 2, 3, 3, 1,
    4, 4, 4, 4,
    4, 4, 3,
]
assert len(RATE_PU) == len(ROWS), (len(RATE_PU), len(ROWS))

# the 100 points: practical utility 20, unique purpose 15, distance to the other place 20, moment 10, how often 10, room it takes 5, what breaks without it 10, the rule behind it 10
W = {"U": 3, "M": 2, "Q": 2, "C": 1, "K": 2, "R": 2, "P": 4}
KEEP_FROM = 60
FOLD_FROM = 40


def dist_pts(taps):
    if taps is None: return 20
    if taps >= 6: return 16
    if taps >= 4: return 12
    if taps == 3: return 5
    if taps == 2: return 2
    return 0


def rule_rating(rule):
    return 5 if rule.startswith("P") else (2 if rule.startswith("A") else 0)




# ===================== Round 5: harsher, and the similarity graph (tools/card_graph.py) =====================
import json, math, random, sys
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import card_graph as G

N = len(ROWS)
assert len(G.TAGS) == N, (len(G.TAGS), N)
PT = [G.parse(t) for t in G.TAGS]
SH = [r[1].startswith("Shelf:") or (r[0] == "Setup" and not r[1].startswith("Change the plan")) for r in ROWS]   # a shelf costs one tap to open
SIM = [[0.0] * N for _ in range(N)]
TAP = [[0] * N for _ in range(N)]
for i in range(N):
    for j in range(N):
        if i != j:
            SIM[i][j] = G.similarity(PT[i], PT[j])
            TAP[i][j] = G.card_taps(ROWS[i][0], SH[i], ROWS[j][0], SH[j])

BASE = 0.40          # below this a card only shares a little with its nearest neighbour, which costs nothing


def over(x, top):
    """How far x is from the baseline towards top, 0 to 1."""
    return min(1.0, max(0.0, (x - BASE) / (top - BASE)))


def nearest(i, skip=()):
    """The most similar card (similarity, index), and the worst the 4-tap rule says about it: similarity times how near it is."""
    best = (0.0, None); worst = 0.0
    for j in range(N):
        if j == i or j in skip: continue
        if SIM[i][j] > best[0]: best = (SIM[i][j], j)
        worst = max(worst, SIM[i][j] * G.prox(TAP[i][j]))
    return best, worst


def harsh(x):
    """Round five: every rating but the exceptional 5 drops one."""
    return x if x >= 5 else max(0, x - 1)


def pts_for(i, u, m, q, c, k, pu, rule, skip=()):
    (s, _), worst = nearest(i, skip)
    ug = min(u, round(5 * (1 - over(s, 1.0))))                         # unique purpose: my rating, capped by how little the nearest card overlaps
    dg = round(20 * (1 - over(worst, G.HIGH)))                         # distance: 20 when nothing similar is near, 0 when a near card is 0.6 similar
    return {"P": harsh(pu) * W["P"], "U": ug * W["U"], "D": dg, "M": harsh(m) * W["M"], "Q": harsh(q) * W["Q"], "C": c * W["C"],
            "K": (k if k >= 4 else max(0, k - 1)) * W["K"], "R": rule_rating(rule) * W["R"]}, ug


rows = []
for i, ((scr, card, purpose, also, taps, rule, _u, _m, act), (u, m, q, c, k), pu) in enumerate(zip(ROWS, RATE, RATE_PU)):
    was = pu * 4 + u * 3 + dist_pts(taps) + m * 2 + q * 2 + c + k * 2 + rule_rating(rule) * 2            # last round's total
    pts, ug = pts_for(i, u, m, q, c, k, pu, rule)
    (s, j), _w = nearest(i)
    rows.append(dict(i=i, scr=scr, card=card, purpose=purpose, rule=rule, pts=pts, t=sum(pts.values()), u=ug, was=was, old=act,
                     rates=(u, m, q, c, k, pu), near=j, nsim=s, ntaps=(TAP[i][j] if j is not None else None), after=None, cl=None, owner=False))

# ---- the high edges: similar enough to be one system, and close enough (3 taps or fewer) that the 4-tap rule forbids two
HI = sorted([(SIM[i][j], i, j) for i in range(N) for j in range(i + 1, N) if SIM[i][j] >= G.HIGH and TAP[i][j] <= 3], reverse=True)
par = list(range(N))
def find(x):
    while par[x] != x:
        par[x] = par[par[x]]; x = par[x]
    return x
for _s, a, b in HI:
    par[find(a)] = find(b)
groups = {}
for i in range(N):
    groups.setdefault(find(i), []).append(i)
clusters = [sorted(g, key=lambda x: (-(rows[x]["rule"].startswith("P")), -rows[x]["t"])) for g in groups.values() if len(g) > 1]
clusters.sort(key=lambda g: (-len(g), g[0]))

# the systems the clusters become: any member's card name -> (system name, the owner card if I override the score, the one button it gets)
SYSTEMS = {
    "Meal question": ("Questions", "Confirm card", "One card, Spark asks, one question at a time. The meal question, the estimate question and the held-notification count become kinds of it."),
    "Now card chips: Running over, Started late, Something came up": ("Late or over", None, "One set of three buttons, on the Now card. The Change door and the Run card stop repeating them."),
    "Proof card": ("Proof", None, "One proof flow, on the block. The Home photo card and Proof due become one line that opens it."),
    "Emergency": ("Emergency", "Emergency chips", "One place: the three chips on Home. The copy on Right now goes. The blocked screen's Emergency goes too, because Home is one tap from it (go home) and the rule allows a copy only 4 taps away. EM1 puts it on the blocked screen and that is a safety call, so I want your decision on this one."),
    "Choices: Day off, Library, Timetable file, A week or a period": ("Change door", None, "What if I sleep later and Winter vacation stop being shelves and become two choices in the door."),
    "Now card": ("Now", None, "The clock line and the Now card are one card."),
    "VRNPM card (while a cycle runs)": ("Cycle card", None, "One cycle card with the ring and the controls. The block screen shows one line saying where the cycle is."),
    "Shopping list": ("Shopping", None, "One list. Home's shopping card becomes a button on the shopping row of the Next card."),
    "Type to open": ("Apps", None, "One type-to-open box. Phone, Msgs and Maps become its first three suggestions, so the dock goes."),
    "+ Quick add": ("Add work", None, "One way to add work that is due. The Change door's deadline choice opens it."),
    "Blocking, Notifications, Sleep log, Browsers, Watch sleep": ("Blocking", None, "One Blocking card. The two permission rows are its own status lines."),
    "Your reminders": ("Reminders", None, "One Reminders card: the switches and your own reminders in one list."),
}
SYSNAME = {}
SYSBTN = {}
for ci, g in enumerate(clusters):
    key = next((rows[x]["card"] for x in g if rows[x]["card"] in SYSTEMS), None)
    name, own, btn = SYSTEMS[key] if key else (rows[g[0]]["card"][:30] + " system", None, "Merge into the strongest card.")
    if own is not None:
        o = next(x for x in g if rows[x]["card"] == own)
        g.remove(o); g.insert(0, o)
    SYSNAME[ci] = name; SYSBTN[ci] = btn
    for x in g: rows[x]["cl"] = ci
    rows[g[0]]["owner"] = True
    r = rows[g[0]]
    pts, ug = pts_for(g[0], *r["rates"][:5], r["rates"][5], r["rule"], skip=set(g))       # once the others are folded in, nothing similar is left near it
    r["after"] = sum(pts.values()); r["u_after"] = ug


def std_verdict(rule, total, u, pu):
    if rule.startswith("P"): return "keep"
    if total >= KEEP_FROM: return "keep"
    return "delete" if (u <= 1 or (total < FOLD_FROM and harsh(pu) <= 2)) else "demote"


for r in rows:
    if r["cl"] is None:
        r["v"] = std_verdict(r["rule"], r["t"], r["u"], r["rates"][5])
        if r["old"].startswith("Becomes") and r["v"] in ("delete", "demote"): r["v"] = "merge"       # it is simplified inside its own screen, not removed
    elif r["owner"]: r["v"] = std_verdict(r["rule"], r["after"], r["u_after"], r["rates"][5])
for g in clusters:
    own = rows[g[0]]
    for x in g[1:]:
        r = rows[x]
        r["v"] = "keep" if r["rule"].startswith("P") else ("delete" if own["v"] == "delete" else "merge")
for r in rows:
    tt = r["after"] if r["after"] is not None else r["t"]
    r["edge"] = abs(tt - KEEP_FROM) <= 4 or abs(tt - FOLD_FROM) <= 4


def short(card):
    s = card.split(" (")[0]
    return s if len(s) <= 44 else s[:42] + "..."


def action(r):
    v, old = r["v"], r["old"]
    first = old.split()[0].rstrip(":,.").lower()
    if r["cl"] is not None:
        g = clusters[r["cl"]]; own = rows[g[0]]; sysname = SYSNAME[r["cl"]]
        if r["owner"]:
            others = "; ".join(short(rows[x]["card"]) for x in g[1:])
            tail = "" if v == "keep" else " It scores under 60 even once they are folded in, so it is the first to give up room."
            return f"Owner of the {sysname} system. Takes in: {others}.{tail}"
        if v == "keep": return f"Pinned by a Locked rule, so it stays. It becomes part of the {sysname} system with {short(own['card'])}."
        if v == "delete": return f"Goes with the {sysname} system."
        return f"Merge into {short(own['card'])} ({sysname} system)."
    if v == "merge": return old
    if v == "keep": return old if first == "keep" else "Keep."
    if v == "delete": return old if first in ("delete", "remove", "turn") else "Delete."
    if first in ("stays", "move"): return old
    if first == "keep": return "Keep in place. It scores under 60, so it is the first to give up room if its screen gets crowded."
    return "Move down: lower on its screen, or onto a shelf."


for r in rows: r["act"] = action(r)
counts = {k: sum(1 for r in rows if r["v"] == k) for k in ("keep", "demote", "merge", "delete")}
screens = []
for r in rows:
    if r["scr"] not in screens: screens.append(r["scr"])

# ---- the graph, laid out once here so the page needs no layout code
GSHOW = 0.30         # the graph draws every line at least this strong; the ones at HIGH are the ones simplified
SHOWN = sorted({i for i in range(N) for j in range(N) if i != j and SIM[i][j] >= GSHOW})
EDGES = [(i, j) for i in SHOWN for j in SHOWN if i < j and SIM[i][j] >= GSHOW]
VW, VH = 1200, 1100


def components():
    p = {i: i for i in SHOWN}
    def f(x):
        while p[x] != x:
            p[x] = p[p[x]]; x = p[x]
        return x
    for a, b in EDGES: p[f(a)] = f(b)
    g = {}
    for i in SHOWN: g.setdefault(f(i), []).append(i)
    return sorted(g.values(), key=lambda c: (-len(c), c[0]))


def spring(nodes, w, h, seed):
    """Fruchterman-Reingold inside a w by h box; edges pull by similarity, and nodes closer than 60 push apart."""
    rnd = random.Random(seed)
    pos = {i: [rnd.uniform(.2 * w, .8 * w), rnd.uniform(.2 * h, .8 * h)] for i in nodes}
    if len(nodes) == 1: return {nodes[0]: [w / 2, h / 2]}
    k = math.sqrt(w * h / len(nodes)) * 0.9
    ed = [(a, b) for a, b in EDGES if a in pos and b in pos]
    for it in range(600):
        t = (1 - it / 600) * w / 10 + 0.4
        d = {i: [0.0, 0.0] for i in nodes}
        for a in nodes:
            for b in nodes:
                if a >= b: continue
                dx, dy = pos[a][0] - pos[b][0], pos[a][1] - pos[b][1]
                dist = math.hypot(dx, dy) or 0.01
                f = k * k / dist + (2500.0 / dist if dist < 80 else 0.0)
                d[a][0] += dx / dist * f; d[a][1] += dy / dist * f
                d[b][0] -= dx / dist * f; d[b][1] -= dy / dist * f
        for a, b in ed:
            dx, dy = pos[a][0] - pos[b][0], pos[a][1] - pos[b][1]
            dist = math.hypot(dx, dy) or 0.01
            f = dist * dist / k * (0.1 + 1.6 * SIM[a][b] ** 2)
            d[a][0] -= dx / dist * f; d[a][1] -= dy / dist * f
            d[b][0] += dx / dist * f; d[b][1] += dy / dist * f
        for a in nodes:
            d[a][0] += (w / 2 - pos[a][0]) * 0.02; d[a][1] += (h / 2 - pos[a][1]) * 0.02
            m = math.hypot(*d[a]) or 0.01
            sc = min(m, t) / m
            pos[a][0] += d[a][0] * sc; pos[a][1] += d[a][1] * sc
    xs = [q[0] for q in pos.values()]; ys = [q[1] for q in pos.values()]
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    for q in pos.values():
        q[0] = (q[0] - x0) / ((x1 - x0) or 1) * w
        q[1] = (q[1] - y0) / ((y1 - y0) or 1) * h
    return pos


def layout():
    """The biggest group of linked cards gets the left of the canvas; the small groups are stacked in a column on the right. The canvas grows to fit."""
    global VH
    comps = components()
    pos = {}
    small = []; y = 80
    for c in comps[1:]:
        ch = 30 if len(c) == 2 else 48 * (len(c) - 1)
        small.append((c, y, ch)); y += ch + 78
    VH = max(1000, y + 20)
    for i, q in spring(comps[0], 780, VH - 150, 5).items(): pos[i] = [60 + q[0], 70 + q[1]]
    for c, y0, ch in small:
        for i, q in spring(c, 120, ch, 3).items(): pos[i] = [905 + q[0], y0 + q[1]]
    return pos


POS = layout()


CSS = """
:root{--bg:#f6f6f3;--card:#ffffff;--ink:#16181d;--ink2:#555b66;--rule:#dcddd8;--keep:#2a7a4b;--demote:#6b6fb5;--merge:#a86a12;--del:#b3282d;--chip:#ecece7;--sans:'IBM Plex Sans',system-ui,-apple-system,'Segoe UI',sans-serif;--mono:'IBM Plex Mono',ui-monospace,Consolas,monospace}
@media (prefers-color-scheme:dark){:root:not([data-theme="light"]){--bg:#101215;--card:#171a1f;--ink:#e9ebef;--ink2:#a3a9b3;--rule:#2b2f37;--keep:#6fcf97;--demote:#a9adf0;--merge:#e8b45a;--del:#ff7b7f;--chip:#22262d;color-scheme:dark}}
:root[data-theme="dark"]{--bg:#101215;--card:#171a1f;--ink:#e9ebef;--ink2:#a3a9b3;--rule:#2b2f37;--keep:#6fcf97;--demote:#a9adf0;--merge:#e8b45a;--del:#ff7b7f;--chip:#22262d;color-scheme:dark}
*{box-sizing:border-box}
body{background:var(--bg);color:var(--ink);font:15px/1.5 var(--sans);margin:0}
.wrap{max-width:1240px;margin:0 auto;padding-block:28px 60px;padding-left:max(16px,env(safe-area-inset-left,0px));padding-right:max(16px,env(safe-area-inset-right,0px))}
h1{font-size:clamp(25px,4vw,36px);line-height:1.12;margin:0 0 10px;letter-spacing:-.01em;text-wrap:balance}
h2{font-size:19px;margin:36px 0 8px}
p{margin:0 0 10px;max-width:74ch}
.lead{font-size:16.5px;color:var(--ink2)}
.k{font:600 11px var(--mono);letter-spacing:.09em;text-transform:uppercase;color:var(--ink2);margin-bottom:6px}
.tiles{display:grid;grid-template-columns:repeat(auto-fit,minmax(140px,1fr));gap:10px;margin:18px 0}
.tile{background:var(--card);border:1px solid var(--rule);border-radius:12px;padding:12px 14px}
.tile b{display:block;font:700 28px var(--mono)}
.tile span{font-size:13px;color:var(--ink2)}
.keep b{color:var(--keep)}.demote b{color:var(--demote)}.merge b{color:var(--merge)}.delete b{color:var(--del)}
.rub{display:grid;grid-template-columns:repeat(auto-fit,minmax(200px,1fr));gap:10px;margin:12px 0}
.rub div{background:var(--card);border:1px solid var(--rule);border-radius:12px;padding:10px 14px;font-size:13.5px}
.rub b{display:flex;justify-content:space-between;margin-bottom:2px;font-size:14px}
.rub b i{font:700 13px var(--mono);font-style:normal;color:var(--ink2)}
.flt{display:flex;flex-wrap:wrap;gap:6px;margin:16px 0 4px;position:sticky;top:env(safe-area-inset-top,0px);background:var(--bg);padding:8px 0;z-index:3}
.flt button{font:600 13px var(--sans);color:var(--ink);background:var(--card);border:1px solid var(--rule);border-radius:999px;padding:5px 13px;cursor:pointer}
.flt button[aria-pressed="true"]{background:var(--ink);color:var(--bg);border-color:var(--ink)}
.tw{overflow-x:auto;border:1px solid var(--rule);border-radius:12px;background:var(--card);margin:8px 0 18px}
table{border-collapse:collapse;width:100%;min-width:1000px;font-size:13.5px}
th,td{padding:8px 11px;text-align:left;vertical-align:top;border-bottom:1px solid var(--rule)}
th{font:600 11px var(--mono);letter-spacing:.07em;text-transform:uppercase;color:var(--ink2);background:var(--bg);white-space:nowrap}
tr:last-child td{border-bottom:0}
td.c{font-weight:600}
td.n{font:600 12.5px var(--mono);white-space:nowrap;text-align:center}
td.s{color:var(--ink2);font-size:12.5px}
.sc{display:flex;align-items:center;gap:8px;min-width:104px}
.sc b{font:700 15px var(--mono);min-width:30px}
.bar{flex:1;height:7px;background:var(--chip);border-radius:4px;overflow:hidden}
.bar i{display:block;height:100%;background:var(--ink2)}
.bd{font:12px var(--mono);color:var(--ink2);white-space:nowrap}
.pill{display:inline-block;font:700 11px var(--mono);letter-spacing:.06em;text-transform:uppercase;border-radius:6px;padding:2px 8px;border:1px solid currentColor}
.pill.keep{color:var(--keep)}.pill.demote{color:var(--demote)}.pill.merge{color:var(--merge)}.pill.delete{color:var(--del)}
.edge{display:block;font:600 10.5px var(--mono);color:var(--ink2);margin-top:3px}
tr[hidden]{display:none}
.pin{font:600 11px var(--mono);background:var(--chip);border-radius:5px;padding:1px 6px;white-space:nowrap}
small.f{display:block;margin-top:30px;color:var(--ink2);font-size:12.5px;max-width:84ch}
@media print{@page{size:A4 landscape;margin:10mm}.flt{display:none}.wrap{max-width:none}}
"""

CSS += """
.gs{background:var(--card);border:1px solid var(--rule);border-radius:12px;overflow-x:auto}
.gs svg{display:block;min-width:1000px;width:100%;height:auto;color:var(--ink)}
.eg{stroke:var(--ink2);opacity:.28}
.eg.hi{stroke:var(--merge);opacity:.9}
.nd{cursor:pointer}
.nd circle{stroke:var(--card);stroke-width:2}
.nd text{font:10px var(--sans);fill:var(--ink)}
.nd.pin circle{stroke:var(--ink);stroke-width:3}
.n-keep circle{fill:var(--keep)}.n-demote circle{fill:var(--demote)}.n-merge circle{fill:var(--merge)}.n-delete circle{fill:var(--del)}
.dim{opacity:.1}
.halo circle{fill:var(--merge);fill-opacity:.08;stroke:var(--merge);stroke-opacity:.55;stroke-dasharray:5 4}
.halo text{font:700 12px var(--sans);fill:var(--merge)}
#pn{background:var(--card);border:1px solid var(--rule);border-radius:12px;padding:12px 14px;font-size:13.5px;min-height:96px;margin:8px 0 10px}
#pn h3{font-size:15px;margin:0 0 4px}
#pn ul{margin:6px 0 0;padding-left:18px}
#pn li{margin:2px 0}
.sl{display:flex;align-items:center;gap:10px;margin:8px 0;font-size:13.5px}
.sl input{flex:1;max-width:320px}
.sl b{font:700 14px var(--mono);min-width:38px}
.leg{display:flex;flex-wrap:wrap;gap:6px 16px;font-size:12.5px;color:var(--ink2);margin:6px 0 0}
.leg i{display:inline-block;width:10px;height:10px;border-radius:50%;margin-right:5px;vertical-align:-1px}
.sysb{font-size:13px;color:var(--ink2)}
td.w{font:700 14px var(--mono);text-align:center}
ul.cl{margin:6px 0;padding-left:18px;font-size:13.5px}
@media print{.gs svg{min-width:0}}
"""

LABEL = {"keep": "keep", "demote": "move down", "merge": "merge", "delete": "delete"}
VC = {"keep": "keep", "demote": "demote", "merge": "merge", "delete": "delete"}


def graph_svg():
    o = [f'<svg viewBox="0 0 {VW} {VH}" id="gsvg" role="img" aria-label="Similarity graph of {N} cards: lines join cards that do the same job, thicker for more similar; orange lines are pairs at or above {G.HIGH} within three taps, which the one-button rule says to merge.">']
    for ci, g in enumerate(clusters):
        xs = [POS[x][0] for x in g]; ys = [POS[x][1] for x in g]
        cx, cy = sum(xs) / len(xs), sum(ys) / len(ys)
        rad = max(math.hypot(POS[x][0] - cx, POS[x][1] - cy) for x in g) + 14
        o.append(f'<g class="halo"><circle cx="{cx:.0f}" cy="{cy:.0f}" r="{rad:.0f}"/><text x="{cx:.0f}" y="{cy - rad - 5:.0f}" text-anchor="middle">{esc(SYSNAME[ci])}</text></g>')
    for a, b in EDGES:
        w = SIM[a][b]; hi = w >= G.HIGH and TAP[a][b] <= 3
        o.append(f'<line class="eg{" hi" if hi else ""}" data-a="{a}" data-b="{b}" data-w="{w:.2f}" x1="{POS[a][0]:.0f}" y1="{POS[a][1]:.0f}" x2="{POS[b][0]:.0f}" y2="{POS[b][1]:.0f}" stroke-width="{0.6 + w * 3.4:.1f}"/>')
    for i in SHOWN:
        r = rows[i]; x, y = POS[i]
        pin = " pin" if r["rule"].startswith("P") else ""
        o.append(f'<g class="nd n-{VC[r["v"]]}{pin}" data-i="{i}" tabindex="0"><circle cx="{x:.0f}" cy="{y:.0f}" r="8"/><text x="{x + 12:.0f}" y="{y + 3.5:.0f}">{esc(short(r["card"])[:30])}</text><title>{esc(r["card"])}</title></g>')
    o.append("</svg>")
    return "".join(o)


def data_json():
    d = {}
    for i in SHOWN:
        r = rows[i]
        nb = sorted(((SIM[i][j], j) for j in range(N) if j != i and SIM[i][j] >= GSHOW), reverse=True)[:7]
        d[str(i)] = {"n": r["card"], "s": r["scr"], "t": r["t"], "a": r["after"], "v": LABEL[r["v"]], "r": r["rule"], "f": G.TAGS[i].split(";")[0],
                     "nb": [[rows[j]["card"], round(s, 2), TAP[i][j]] for s, j in nb]}
    return json.dumps(d).replace("</", "<\\/")


JS = """<script>(function(){
var D=JSON.parse(document.getElementById('gd').textContent),svg=document.getElementById('gsvg'),pn=document.getElementById('pn');
var nodes=svg.querySelectorAll('.nd'),edges=svg.querySelectorAll('.eg'),sl=document.getElementById('thr'),sv=document.getElementById('thv');
function thr(){var t=parseFloat(sl.value);sv.textContent=t.toFixed(2);edges.forEach(function(e){e.style.display=parseFloat(e.dataset.w)>=t?'':'none'})}
sl.addEventListener('input',thr);thr();
function pick(i){var d=D[i];if(!d)return;var nbs={};d.nb.forEach(function(x){nbs[x[0]]=1});
 nodes.forEach(function(n){var m=D[n.dataset.i];n.classList.toggle('dim',n.dataset.i!==i&&!nbs[m.n])});
 edges.forEach(function(e){e.classList.toggle('dim',e.dataset.a!==i&&e.dataset.b!==i)});
 var h='<h3></h3><div class="sysb"></div><ul>';d.nb.forEach(function(x){h+='<li></li>'});h+='</ul>';pn.innerHTML=h;
 pn.querySelector('h3').textContent=d.n;
 pn.querySelector('.sysb').textContent=d.s+' · job: '+d.f+' · score '+d.t+(d.a?' ('+d.a+' once merged)':'')+' · '+d.v+(d.r!=='F'?' · rule '+d.r:'');
 var li=pn.querySelectorAll('li');d.nb.forEach(function(x,k){li[k].textContent=x[1].toFixed(2)+' similar, '+x[2]+(x[2]===1?' tap':' taps')+': '+x[0]})}
function clear(){nodes.forEach(function(n){n.classList.remove('dim')});edges.forEach(function(e){e.classList.remove('dim')})}
nodes.forEach(function(n){n.addEventListener('click',function(ev){ev.stopPropagation();pick(n.dataset.i)});n.addEventListener('keydown',function(ev){if(ev.key==='Enter'||ev.key===' '){ev.preventDefault();pick(n.dataset.i)}})});
svg.addEventListener('click',function(){clear();pn.textContent='Tap a card to see what it overlaps, and how many taps away each overlap is.'});
var b=document.querySelectorAll('.flt button');function go(f){b.forEach(function(x){x.setAttribute('aria-pressed',x.dataset.f===f?'true':'false')});document.querySelectorAll('#tabs tbody tr').forEach(function(r){r.hidden=!(f==='all'||r.dataset.v===f)})}
b.forEach(function(x){x.addEventListener('click',function(){go(x.dataset.f)})})})();</script>"""


def build():
    gone = counts["merge"] + counts["delete"]
    merged = [r for r in rows if r["v"] == "merge"]
    onlyfew = [i for i in range(N) if i not in SHOWN]
    o = ['<title>Spark card audit</title><meta name="claude:viewport-fit-sides" content="cover">',
         '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@500;600;700&family=IBM+Plex+Sans:wght@400;500;600;700&display=swap">',
         f'<style>{CSS}</style><div class="wrap">',
         '<div class="k">Spark · every card and button, scored out of 100 · round 5 · 20 November 2026</div>',
         f'<h1>Scored harsher again, {counts["keep"]} of {len(rows)} earn their place. {len(merged)} fold into {len(clusters)} systems, {counts["delete"]} go and {counts["demote"]} move down.</h1>',
         f'<p class="lead">Two changes since the last round. Every rating except the exceptional 5 dropped one point. And the overlap between cards is no longer my say-so: each card is described by the job it does, the data it uses, when it shows and how you use it, and every pair is given a similarity from 0 to 1. Unique purpose and Distance now come from that graph. A pair at {G.HIGH} or more and within 3 taps is simplified into one system. The Home and block-screen cuts were made on 21 November (ledger section 62); Right now, Setup and the Change door have not changed yet.</p>',
         f'<div class="tiles"><div class="tile"><b>{len(rows)}</b><span>cards and buttons scored</span></div><div class="tile keep"><b>{counts["keep"]}</b><span>keep</span></div><div class="tile merge"><b>{counts["merge"]}</b><span>fold into a system</span></div><div class="tile demote"><b>{counts["demote"]}</b><span>move down</span></div><div class="tile delete"><b>{counts["delete"]}</b><span>delete</span></div><div class="tile"><b>{len(clusters)}</b><span>systems from the graph</span></div></div>',
         '<h2>The similarity graph</h2>',
         f'<p>Each dot is a card or button; a line means two cards share a job, and the thicker the line the more alike they are. Orange lines are pairs at {G.HIGH} or more within 3 taps, so the one-button rule applies. The dashed rings are the systems they would become. Dot colour is the verdict; a dark ring means a Locked rule pins the card. Tap a dot to see what it overlaps; slide to hide the weaker lines.</p>',
         f'<div class="sl"><label for="thr">Show lines at least</label><input id="thr" type="range" min="{GSHOW}" max="1" step="0.05" value="{GSHOW}"><b id="thv">{GSHOW:.2f}</b></div>',
         f'<div id="pn">Tap a card to see what it overlaps, and how many taps away each overlap is.</div><div class="gs">{graph_svg()}</div>',
         '<div class="leg"><span><i style="background:var(--keep)"></i>keep</span><span><i style="background:var(--merge)"></i>merge</span><span><i style="background:var(--demote)"></i>move down</span><span><i style="background:var(--del)"></i>delete</span></div>',
         f'<script type="application/json" id="gd">{data_json()}</script>']
    # ---- the systems
    o.append('<h2>The systems the graph asks for</h2>')
    o.append(f'<p>{len(clusters)} groups of cards are alike enough, and close enough, that they should be one thing. The owner is the pinned card, or the strongest one; the others fold into it. {len(merged)} cards and buttons disappear as separate things and nothing is lost, because the owner does the job. Some of these are new systems, not just a merge: Questions, Late or over, Apps, Proof, Cycle card.</p>')
    o.append('<div class="tw"><table style="min-width:900px"><thead><tr><th>System</th><th>Owner</th><th>Takes in</th><th>Score of each, owner first</th><th>The one button it leaves</th></tr></thead><tbody>')
    for ci, g in enumerate(clusters):
        own = rows[g[0]]
        takes = "<br>".join(esc(short(rows[x]["card"])) + f' <span class="edge">{esc(rows[x]["scr"])}</span>' for x in g[1:])
        scores = "<br>".join(f'{rows[x]["t"]}' + (f' → {rows[x]["after"]}' if rows[x]["after"] else "") for x in g)
        o.append(f'<tr><td class="c">{esc(SYSNAME[ci])}</td><td>{esc(short(own["card"]))}<span class="edge">{esc(own["scr"])}</span></td><td>{takes}</td><td class="n">{scores}</td><td>{esc(SYSBTN[ci])}</td></tr>')
    o.append('</tbody></table></div>')
    # ---- the highest edges
    pairs = sorted(((SIM[i][j], i, j) for i in range(N) for j in range(i + 1, N) if SIM[i][j] >= 0.55), reverse=True)
    o.append(f'<h2>The {len(pairs)} strongest pairs</h2><p>Every pair at 0.55 or more, with the taps between them. Only a pair 4 or more taps apart may stay as two.</p>')
    o.append('<div class="tw"><table style="min-width:900px"><thead><tr><th>Card</th><th>Card</th><th>Similarity</th><th>Taps</th><th>What the rule says</th></tr></thead><tbody>')
    for s, i, j in pairs:
        t = TAP[i][j]
        verdict_ = "Simplify into one system" if (s >= G.HIGH and t <= 3) else ("Allowed: 4 or more taps apart" if s >= G.HIGH else "Below the line; watch")
        o.append(f'<tr><td>{esc(short(rows[i]["card"]))}<span class="edge">{esc(rows[i]["scr"])}</span></td><td>{esc(short(rows[j]["card"]))}<span class="edge">{esc(rows[j]["scr"])}</span></td><td class="w">{s:.2f}</td><td class="n">{t}</td><td>{verdict_}</td></tr>')
    o.append('</tbody></table></div>')
    o.append(f'<p>{len(onlyfew)} cards share almost nothing with any other, so they are the only place for their job: {esc("; ".join(short(rows[i]["card"]) for i in onlyfew))}.</p>')
    # ---- the rubric
    o += ['<h2>How the 100 points are made</h2>', '<div class="rub">'
          '<div><b>Practical utility <i>20</i></b>Rated 0 to 5 by the effort, time or mistakes it saves; 5 changes what you do every day. Dropped one point this round unless it was 5.</div>'
          f'<div><b>Unique purpose <i>15</i></b>The lower of my rating and what the graph allows: 5 when nothing else is more than {BASE:.1f} similar to it, falling to 0 for a twin.</div>'
          f'<div><b>Distance <i>20</i></b>20 when no similar card is near. It falls to 0 when a card {G.HIGH} similar is within 1 tap, and a similar card 2 taps away counts 70%, 3 taps 40%, 4 or more nothing.</div>'
          '<div><b>Moment <i>10</i></b>Rated 0 to 5: 5 is time-critical and shows exactly then. Dropped one unless 5.</div>'
          '<div><b>How often <i>10</i></b>Rated 0 to 5: 5 is many times a day, 3 a few times a week. Dropped one unless 5.</div>'
          '<div><b>Room it takes <i>5</i></b>Rated 0 to 5; less room scores higher.</div>'
          '<div><b>What breaks without it <i>10</i></b>Rated 0 to 5: 5 safety, proof or blocking; 4 the Buffer or money. Dropped one when it was 3 or less.</div>'
          '<div><b>Rule behind it <i>10</i></b>A Locked rule names it 10, a Default rule 4, no rule 0.</div>'
          '<div><b>Similarity</b>Each card is tagged with its job, its data, its moment and how you use it. Two cards that share no job score 0; sharing a job gives at least half, and the other three raise it to 1.</div>'
          f'<div><b>Verdict <i>{KEEP_FROM} / {FOLD_FROM}</i></b>Pinned by a Locked rule: keep. In a system: the owner is scored once the others are folded in, and the others merge. Alone: {KEEP_FROM} or more keeps; below it, a unique purpose of 0 or 1 (or under {FOLD_FROM} with little use) deletes, and anything else moves down.</div>'
          '</div>',
          '<h2>Every card, scored</h2>',
          '<div class="flt" role="group" aria-label="Filter"><button data-f="all" aria-pressed="true">All</button><button data-f="delete" aria-pressed="false">Delete</button><button data-f="merge" aria-pressed="false">Merge</button><button data-f="demote" aria-pressed="false">Move down</button><button data-f="keep" aria-pressed="false">Keep</button></div>',
          '<div id="tabs">']
    for scr in screens:
        o.append(f'<h2>{esc(scr)}</h2><div class="tw"><table><thead><tr><th>Card</th><th>What it is for</th><th>Most like</th><th>Rule</th><th>Score</th><th title="Practical utility, Unique purpose, Distance, Moment, How often, Room, What breaks, Rule">PU / U / D / M / Q / R / B / L</th><th>Verdict</th><th>What happens</th></tr></thead><tbody>')
        for r in [x for x in rows if x["scr"] == scr]:
            pin = f'<span class="pin">{esc(r["rule"])}</span>' if r["rule"] != "F" else "free"
            pt = r["pts"]
            bd = f'{pt["P"]} / {pt["U"]} / {pt["D"]} / {pt["M"]} / {pt["Q"]} / {pt["C"]} / {pt["K"]} / {pt["R"]}'
            edge = '<span class="edge">borderline</span>' if r["edge"] else ""
            near = f'{esc(short(rows[r["near"]]["card"]))}<span class="edge">{r["nsim"]:.2f} similar, {r["ntaps"]} tap{"" if r["ntaps"] == 1 else "s"}</span>' if r["near"] is not None and r["nsim"] >= BASE else '<span class="edge">nothing close</span>'
            once = f'<span class="edge">{r["after"]} once merged</span>' if r["after"] is not None else ""
            o.append(f'<tr data-v="{r["v"]}"><td class="c">{esc(r["card"])}</td><td>{esc(r["purpose"])}</td><td class="s">{near}</td><td>{pin}</td>'
                     f'<td><div class="sc"><b>{r["t"]}</b><div class="bar"><i style="width:{r["t"]}%"></i></div></div><span class="edge">was {r["was"]}</span>{once}</td><td class="bd">{bd}</td>'
                     f'<td><span class="pill {r["v"]}">{LABEL[r["v"]]}</span>{edge}</td><td>{esc(r["act"])}</td></tr>')
        o.append('</tbody></table></div>')
    o.append('</div>')
    o.append('<small class="f">The ratings are my judgement from reading the code, and the tags behind the graph are too: every tag is in tools/card_graph.py and every number is in its row, so any of them can be overruled and the totals move with it. 5 is exceptional and 3 is ordinary. Rules are named by their ledger id: P is a Locked rule that names the card, A is a Default rule that would be amended in the same change. Widgets, notifications and the lock screen repeat Home facts on purpose (B2) and are not scored as duplicates. The breakdown reads: practical utility, unique purpose, distance, moment, how often, room, what breaks, rule.</small></div>')
    o.append(JS)
    return "\n".join(o)


if __name__ == "__main__":
    frag = build()
    (ROOT / "docs").mkdir(exist_ok=True)
    (ROOT / "docs" / "CARD_AUDIT.fragment.html").write_text(frag, encoding="utf-8")
    print(len(rows), counts, len(clusters), "systems,", len(EDGES), "edges drawn")
