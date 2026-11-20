"""The similarity graph of Spark's cards (docs/CARD_AUDIT). Every card is described by four things: F what job it does, D what stored data it reads or writes, T when it shows, I how you use it.
Two cards that share no job score 0. Two that share a job score at least half, and more when the data, the moment and the way you use them also match; near 1 means the same card twice.
The taps between two cards come from the screens graph below, plus one tap to open a shelf.
A pair is 'high' when its similarity is at least HIGH and the cards are within 3 taps of each other (the 4-tap rule): the pair is simplified into one system."""

HIGH = 0.60          # a pair this similar, within 3 taps, is simplified
SHOW = 0.45          # pairs this similar are drawn as edges

W_F, W_D, W_T, W_I = 0.50, 0.20, 0.15, 0.15

# the screens and the taps between neighbours
SCREEN_EDGES = [("Home", "Schedule page"), ("Home", "Right now"), ("Home", "Block screen"), ("Home", "Setup"), ("Home", "Flows"), ("Home", "Blocked card"), ("Setup", "Change the plan"), ("Change the plan", "Flows")]

# F; D; T; I  in the order of ROWS in gen-card-audit.py
TAGS = [
    "time-now;plan;always;info",                       # Header: clock, date, phone
    "on-now;plan,runrec;always;info",   # Now card
    "late,change;plan,runrec;always;button",   # Now card chips
    "cycle,run;cycle,runrec;block-running;hold,button",  # VRNPM card on Home
    "next;plan;always;info",                           # Next card
    "proof;blocks,plan;after-event;button",            # Photo card
    "proof;blocks;after-event;info,button",            # Proof due
    "morning,proof;sleep,blocks;morning;info,button",  # Morning card
    "ask;facts;after-event;button",                    # Meal question
    "ask,change;plan;rare;button",                     # Drift
    "ask,setting-launcher;meta;rare;button",                   # Launcher paused
    "ask;confirm;after-event;button",                  # Confirm card
    "next,time-now;plan;lite;info",                    # Three things
    "buffer;buffer,tasks;always;info",                 # Buffer card
    "shopping;plan;shopping-day;button",               # Shopping card
    "places,ask;places;rare;button",                   # Where is it
    "voice,ask;none;after-event;info",                 # Voice note
    "ask;tasks;after-event;button",                    # Help the estimates
    "ask;notif;after-event;info,button",               # Held notifications
    "calendar,next;plan;always;info",                  # Week card
    "money;money;always;info",                         # Money card
    "nav-apps;none;always;button",                     # Dock
    "nav-setup;none;always;button",                     # Setup tile
    "emergency;none;always;button",                    # Emergency chips
    "nav-apps;none;always;form",                       # Type to open
    "quick-add,voice;tasks;always;button",             # Say
    "quick-add;tasks;always;button",                   # + Quick add
    "nav;none;always;button",                          # Page dots
    "calendar;plan;always;info",                       # Calendar
    "time-now,calendar;plan,runrec;none;info",         # Day strip
    "explain;plan;block;info",                         # Block header
    "explain,change;plan;block;info,button",           # Why this is work
    "run;runrec;block;hold",                           # Run card
    "late,change;runrec,plan;block-running;button",    # Run slide buttons
    "cycle,run;cycle;block-running;hold",              # VRNPM block running
    "cycle-start;cycle;block;button",   # VRNPM not started or done
    "steps,run;facts;block;button",                    # Steps card
    "proof;blocks;block;button",                       # Proof card
    "money,explain;money;block-running;info",          # Earned line
    "blocked-why,explain;plan;blocked;info,button", # Blocked card
    "emergency;none;blocked;button",                   # Blocked emergency
    "explain,ask;plan;after-event;info",               # Moved for you
    "time-now,explain;plan,places;always;button",      # Today looks like
    "setting-lite;meta;always;switch",                     # Lite day
    "nap-offer;sleep;after-event;info",   # After a short night
    "wins;facts;night;info",   # Today's wins
    "change;plan;rare;form",                           # What if
    "change;plan;rare;form",                           # Winter vacation
    "change,quick-add;plan;rare;form",                 # Tomorrow's fun
    "helper;meta;rare;form",                           # Helpers
    "emergency;none;always;button",                    # Emergency copy
    "emergency,settings-block;meta;rare;button",       # I am out etc
    "setting-coach;meta;rare;switch",                       # Coach
    "setting-tools;meta;rare;switch",                       # Tools
    "data;meta;rare;button",                           # Data and help
    "nav-change;none;rare;button",   # Change card
    "settings-block;meta;rare;switch",                 # Blocking etc
    "settings-block;meta;rare;info",                   # Permission rows
    "reminders;meta;rare;switch",                      # Reminders card
    "reminders;meta;rare;form",                        # Your reminders
    "setting-morning,setting-cycle;meta;rare;form",                 # Morning, Study cycle
    "places;places;rare;form",                         # Places three
    "settings-look;meta;rare;switch",                  # Type to open, appearance...
    "link-uni;none;rare;button",                  # University
    "link-dev;meta;rare;form",                    # PC, GitHub
    "shopping;plan,meta;shopping-day;form",            # Shopping list
    "explain-tour;none;rare;button",                        # Walkthrough
    "late,change;plan,runrec;always;button",           # Door: over, late, came up
    "quick-add;tasks;always;button",                   # Door: new deadline
    "change;plan,money;rare;form",                     # Door: day off etc
    "change,morning;plan,sleep;night;form",            # Wake card
    "screens;tasks,plan;rare;form",   # Flows
    "money,buffer;money,buffer;rare;info",             # Money and hours
    "ask,digest;notif,meta;rare;form",               # Digest etc
]


def parse(tag):
    f, d, t, i = tag.split(";")
    return set(f.split(",")), set(d.split(",")), t, set(i.split(","))


def jac(a, b):
    if a == {"none"} and b == {"none"}: return 0.5
    u = a | b
    return len(a & b) / len(u) if u else 0.0


def t_match(a, b):
    if a == b: return 1.0
    if "always" in (a, b) or "none" in (a, b): return 0.4
    return 0.0


def similarity(ta, tb):
    """Zero when the two share no job. Sharing a job gives at least half the score; the data, the moment and the way you use them give the rest."""
    fa, da, ta_, ia = ta; fb, db, tb_, ib = tb
    same_job = jac(fa, fb)
    return same_job * (0.5 + 0.5 * (jac(da, db) + t_match(ta_, tb_) + jac(ia, ib)) / 3)


def screen_taps(a, b):
    if a == b: return 0
    seen = {a: 0}; q = [a]
    while q:
        x = q.pop(0)
        for u, v in SCREEN_EDGES:
            for p, r in ((u, v), (v, u)):
                if p == x and r not in seen:
                    seen[r] = seen[x] + 1; q.append(r)
    return seen.get(b, 3)


def card_taps(a_scr, a_shelf, b_scr, b_shelf):
    """Taps from one card to the other, the smaller of the two ways, a shelf costing one tap to open."""
    d = screen_taps(a_scr, b_scr)
    return min(d + (1 if b_shelf else 0), d + (1 if a_shelf else 0)) if a_scr != b_scr else (0 if (a_shelf == b_shelf) else 1)


def prox(taps):
    """How much the same job is a problem at this distance: full within one tap, none at four or more (the 4-tap rule)."""
    return 1.0 if taps <= 1 else 0.7 if taps == 2 else 0.4 if taps == 3 else 0.0
