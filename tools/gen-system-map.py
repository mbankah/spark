"""Generates docs/SYSTEM_MAP.html: the systems of Spark, how they connect, and where they do not.
Every box and arrow is drawn from the code as read on 20 November 2026 (commit c9f345e). Run: python tools/gen-system-map.py
The PDF is made from the HTML with a headless browser (see docs/SYSTEM_MAP.md)."""
import html, pathlib, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent


def esc(s):
    return html.escape(s, quote=False)


class Dg:
    n = 0

    def __init__(self, w, h, claim):
        Dg.n += 1
        self.id = Dg.n; self.w = w; self.h = h; self.claim = claim
        self.nodes = {}; self.back = []; self.edges = []; self.boxes = []; self.labels = []; self.badges = []

    # --- shapes -------------------------------------------------------------
    def node(self, id, x, y, w, h, title, sub=(), k='sur', style=''):
        self.nodes[id] = (x, y, w, h)
        s = [f'<g class="nd s-{k} {style}"><rect x="{x}" y="{y}" width="{w}" height="{h}" rx="8"/>',
             f'<text class="nt" x="{x + 12}" y="{y + 21}">{esc(title)}</text>']
        for i, l in enumerate(sub):
            s.append(f'<text class="ns" x="{x + 12}" y="{y + 38 + i * 14}">{esc(l)}</text>')
        s.append('</g>')
        self.boxes.append(''.join(s))

    def sysbox(self, id, x, y, w, h, k, title, role, bullets):
        self.nodes[id] = (x, y, w, h)
        s = [f'<g class="sb s-{k}"><rect class="sbr" x="{x}" y="{y}" width="{w}" height="{h}" rx="10"/>',
             f'<path class="sbh" d="M{x} {y + 34}V{y + 10}a10 10 0 0 1 10 -10H{x + w - 10}a10 10 0 0 1 10 10V{y + 34}z"/>',
             f'<text class="st" x="{x + 14}" y="{y + 23}">{esc(title)}</text>',
             f'<text class="sr" x="{x + 14}" y="{y + 54}">{esc(role)}</text>']
        for i, b in enumerate(bullets):
            s.append(f'<text class="ns" x="{x + 14}" y="{y + 76 + i * 16}">{esc(b)}</text>')
        s.append('</g>')
        self.boxes.append(''.join(s))

    def panel(self, x, y, w, h, title):
        self.back.append(f'<g class="pn"><rect x="{x}" y="{y}" width="{w}" height="{h}" rx="12"/><text x="{x + 14}" y="{y + 22}">{esc(title)}</text></g>')

    def badge(self, x, y, n):
        self.badges.append(f'<g class="bg"><circle cx="{x}" cy="{y}" r="11"/><text x="{x}" y="{y + 4.5}">{n}</text></g>')

    # --- connectors -----------------------------------------------------------
    def pt(self, id, side, f=.5):
        x, y, w, h = self.nodes[id]
        return {'r': (x + w, y + h * f), 'l': (x, y + h * f), 't': (x + w * f, y), 'b': (x + w * f, y + h)}[side]

    def _label(self, pts, label):
        best = None
        for a, b in zip(pts, pts[1:]):
            ln = abs(a[0] - b[0]) + abs(a[1] - b[1])
            if best is None or ln > best[0]:
                best = (ln, a, b)
        _, a, b = best
        lines = label.split('|')
        mx, my = (a[0] + b[0]) / 2, (a[1] + b[1]) / 2
        if abs(a[1] - b[1]) < 1:       # horizontal: above the line
            y0 = my - 6 - (len(lines) - 1) * 12
            tsp = ''.join(f'<tspan x="{mx}" y="{y0 + i * 12}">{esc(t)}</tspan>' for i, t in enumerate(lines))
            self.labels.append(f'<text class="lab" text-anchor="middle">{tsp}</text>')
        else:                           # vertical: to the right of the line
            y0 = my + 4 - (len(lines) - 1) * 6
            tsp = ''.join(f'<tspan x="{mx + 8}" y="{y0 + i * 12}">{esc(t)}</tspan>' for i, t in enumerate(lines))
            self.labels.append(f'<text class="lab">{tsp}</text>')

    def edge(self, a, sa, b, sb, label='', kind='', fa=.5, fb=.5, via=None, mx=None, my=None):
        p1 = self.pt(a, sa, fa); p2 = self.pt(b, sb, fb)
        if via:
            pts = [p1] + via + [p2]
        else:
            h1 = sa in 'rl'; h2 = sb in 'rl'
            if h1 and h2:
                if abs(p1[1] - p2[1]) < 1: pts = [p1, p2]
                else:
                    m = mx if mx is not None else (p1[0] + p2[0]) / 2
                    pts = [p1, (m, p1[1]), (m, p2[1]), p2]
            elif not h1 and not h2:
                if abs(p1[0] - p2[0]) < 1: pts = [p1, p2]
                else:
                    m = my if my is not None else (p1[1] + p2[1]) / 2
                    pts = [p1, (p1[0], m), (p2[0], m), p2]
            elif h1: pts = [p1, (p2[0], p1[1]), p2]
            else: pts = [p1, (p1[0], p2[1]), p2]
        d = 'M' + ' L'.join(f'{x:.1f} {y:.1f}' for x, y in pts)
        m = 'g' if kind == 'gap' else 'a'
        self.edges.append(f'<path class="e {kind}" d="{d}" marker-end="url(#{m}{self.id})"/>')
        if label: self._label(pts, label)

    def stub(self, a, side, label, fa=.5, length=44):
        x, y = self.pt(a, side, fa)
        dx = {'r': 1, 'l': -1}.get(side, 0); dy = {'b': 1, 't': -1}.get(side, 0)
        x2, y2 = x + dx * length, y + dy * length
        self.edges.append(f'<path class="e gap" d="M{x:.1f} {y:.1f} L{x2:.1f} {y2:.1f}"/>')
        self.edges.append(f'<path class="e gap x" d="M{x2 - 5:.1f} {y2 - 5:.1f} l10 10 M{x2 + 5:.1f} {y2 - 5:.1f} l-10 10"/>')
        lines = label.split('|')
        y0 = y2 - 10 - (len(lines) - 1) * 12 if dy == 0 else (y2 + 18 if dy == 1 else y2 - 12 - (len(lines) - 1) * 12)
        tsp = ''.join(f'<tspan x="{x2}" y="{y0 + i * 12}">{esc(t)}</tspan>' for i, t in enumerate(lines))
        self.labels.append(f'<text class="lab gapt" text-anchor="middle">{tsp}</text>')

    def svg(self):
        i = self.id
        defs = (f'<defs><marker id="a{i}" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto"><path d="M0 0L10 5L0 10z" class="ah"/></marker>'
                f'<marker id="g{i}" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto"><path d="M0 0L10 5L0 10z" class="ah gh"/></marker></defs>')
        body = ''.join(self.back) + ''.join(self.edges) + ''.join(self.boxes) + ''.join(self.labels) + ''.join(self.badges)
        return (f'<figure class="fg"><div class="sv"><svg viewBox="0 0 {self.w} {self.h}" role="img" aria-label="{esc(self.claim)}" xmlns="http://www.w3.org/2000/svg">{defs}{body}</svg></div>'
                f'<figcaption>{esc(self.claim)}</figcaption></figure>')


# ===== the eight systems =====================================================
W = 1120
C = [15, 310, 605, 900]   # four columns
NW = 205


def d_map():
    d = Dg(1190, 720, 'Seven systems feed one another in a line: a change edits the plan, the plan gives the blocks, the blocks produce proof, and proof comes back to the plan as minutes and missed work. Surfaces at the bottom show all of them. Red badges mark the eight places where a link is missing or doubled.')
    bw, bh = 230, 140
    X = [30, 330, 630, 930]
    d.sysbox('change', X[0], 70, bw, bh, 'change', 'Change', 'How the plan moves', ['Modify presets, Days off, Library', 'File import, Quick add, Voice', 'Late, over, came up, Adapt'])
    d.sysbox('plan', X[1], 70, bw, bh, 'plan', 'Plan', 'What is on, and when', ['Weekly pattern and dated slots', 'Reflow engine (modifiers)', 'Anchors, work placement'])
    d.sysbox('do', X[2], 70, bw, bh, 'do', 'Do', 'What you do in a block', ['Run: hold to start, end, say', 'VRNPM cycle on study blocks', 'Routines and steps, proof photo'])
    d.sysbox('count', X[3], 70, bw, bh, 'count', 'Count', 'What it is worth', ['Buffer, tasks, deadlines', 'Verified minutes, money guide', 'Earned access, rewards, Doors'])
    d.sysbox('ctx', X[0], 320, bw, bh, 'ctx', 'Context', 'Where and when you are', ['Home, venues, places', 'Clock trust, time zone', 'Day type, location fix'])
    d.sysbox('night', X[1], 320, bw, bh, 'night', 'Night and morning', 'Sleep, wake, ready', ['Sleep sensing, night cutoff', 'Wake alarm and how it stops', 'Morning photo, wind-down'])
    d.sysbox('guard', X[2], 320, bw, bh, 'guard', 'Guard', 'What is closed to you', ['Blocker, block list, essentials', 'Quiet mode, held digest', 'Wall, clock-tamper guard'])
    d.edge('change', 'r', 'plan', 'l', 'edits slots')
    d.edge('plan', 'r', 'do', 'l', "today's|blocks")
    d.edge('do', 'r', 'count', 'l', 'proof becomes|minutes')
    d.edge('count', 't', 'plan', 't', 'missed work and tasks come back as work', via=[(1045, 34), (445, 34)])
    d.edge('plan', 'b', 'night', 't', 'anchors:|wake,|lights-out', fa=.14, fb=.14)
    d.edge('night', 't', 'plan', 'b', 'wake choice:|alarm only', 'gap', fa=.6, fb=.6)
    d.edge('plan', 'b', 'guard', 't', 'the live block decides|what is closed', fa=.96, fb=.2, my=268)
    d.edge('do', 'b', 'guard', 't', 'a started step|closes social', fa=.78, fb=.78)
    d.edge('count', 'b', 'guard', 'r', 'earned|access', fa=.35, fb=.5)
    d.edge('ctx', 'r', 'night', 'l', 'place, clock,|time zone')
    d.node('sur', 30, 520, 1130, 160, 'Surfaces: where you see and tap all of the above', ['Several systems are drawn on more than one surface, so one fact can be shown in five places.'], k='sur')
    names = [('Home', 'now, next, needs'), ('Schedule page', 'the calendar'), ('Work it out', 'about 20 sections'), ('Block screen', 'up to 4 cards'), ('Setup', '23 cards'), ('Lock screen', 'cycle, alarm, reminders'), ('Alarm screen', 'steps, photo')]
    for i, (n, s) in enumerate(names):
        d.node(f's{i}', 44 + i * 160, 596, 148, 62, n, [s], k='sur', style='ext')
    d.badge(668, 70, 1); d.badge(700, 70, 2); d.badge(732, 70, 3); d.badge(764, 70, 5)
    d.badge(468, 304, 4)
    d.badge(30, 70, 6)
    d.badge(44 + 4 * 160 + 74, 596, 7); d.badge(44 + 2 * 160 + 74, 596, 8)
    return d.svg()


def d_plan():
    d = Dg(1120, 480, 'Everything that can put a block on the calendar goes through one reflow engine, which gives one answer to what is on. That answer is what Do, Guard and every screen read.')
    d.node('chg', C[0], 10, NW, 56, 'From Change', ['presets, files and chips write here'], k='change', style='ext')
    ins = [('weekly', 'Weekly pattern', ['the seeded week, by day']), ('dated', 'Dated slots', ['events, imports, converted free time']),
           ('periods', 'Periods', ['assignment, catch-up, library, off']), ('daysoff', 'Days off', ['a day, a weekend, ill']),
           ('anchors', 'Anchors', ['wake and lights-out of each night'])]
    for i, (id, t, s) in enumerate(ins):
        d.node(id, C[0], 96 + i * 68, NW, 54, t, s, k='plan')
    d.node('reflow', C[1], 96, NW, 300, 'Reflow engine', ['a modifier is a span of time;', 'the day is built again from', 'what cannot move.', '', 'Commitments stay put.', 'Work and routines move.'], k='plan')
    d.node('slots', C[2], 96, NW, 110, 'Slots for a day', ['slotsFor: the one answer to', "'what is on'. The Buffer, the", 'alarms, the blocker and the', 'calendar all read it.'], k='plan')
    d.node('place', C[2], 250, NW, 90, 'Work placement', ['free planner: free time becomes', 'work by the Buffer rule;', 'allocation: tasks into blocks'], k='plan')
    d.node('ecount', C[2], 384, NW, 56, 'From Count', ['tasks and deadlines'], k='count', style='ext')
    d.node('night', C[1], 420, NW, 52, 'To Night and morning', ['reads the anchors'], k='night', style='ext')
    for id, t, s, y, k in [('edo', 'To Do', ["today's blocks"], 60, 'do'), ('eguard', 'To Guard', ['which block is live'], 140, 'guard'), ('esur', 'To Surfaces', ['Home, Schedule page, widgets'], 220, 'sur')]:
        d.node(id, C[3], y, NW, 56, t, s, k=k, style='ext')
    for id, _, _ in ins:
        x, y, w, h = d.nodes[id]
        d.edge(id, 'r', 'reflow', 'l', fb=min(.96, max(.04, (y + 27 - 96) / 300)))
    d.edge('chg', 'r', 'reflow', 't', 'edits', fb=.5)
    d.edge('reflow', 'r', 'slots', 'l', 'the day,|built', fa=.12, fb=.5)
    d.edge('place', 't', 'slots', 'b', 'work placed')
    d.edge('ecount', 't', 'place', 'b', 'tasks')
    d.edge('anchors', 'b', 'night', 'l', 'read by', via=[(C[0] + NW / 2, 446), (C[1], 446)])
    for id, f in [('edo', .2), ('eguard', .5), ('esur', .8)]:
        d.edge('slots', 'r', id, 'l', fa=f)
    d.badge(C[1] + NW, 446, 4)
    return d.svg()


def d_change():
    d = Dg(1120, 560, 'Eight doors lead to the same engine. They live on five screens, and four of them are cards in Setup. The engine, the preview and the wall are shared, so the doors are the part that can be merged.')
    doors = [('d1', 'Running over, started late', ['Home hero chips']), ('d2', 'Something came up', ['Home hero chip, Work it out']), ('d3', 'Quick add, Voice', ['a new deadline, the Say button']),
             ('d4', 'Modify Schedule', ['Setup card: presets, week types']), ('d5', 'Days off', ['Setup card, typed wall']), ('d6', 'Library period', ['Setup card']),
             ('d7', 'Timetable file', ['Setup card: CSV, Add or Replace']), ('d8', 'Adapt', ['automatic: drift, the Lite ladder'])]
    for i, (id, t, s) in enumerate(doors):
        d.node(id, C[0], 20 + i * 64, NW, 54, t, s, k='change')
    d.node('policy', C[1], 150, NW, 260, 'Four classes of change', ['reality: what happened', 'add: more work, events, rules', 'move: same work, other time', 'loosen: less obligation', '', 'decided by what the change', 'does, not by which screen', 'it came from (CE1)'], k='change')
    d.node('preview', C[2], 80, NW, 100, 'Preview', ['the Buffer before and after;', 'nothing moves until you', 'apply it'], k='change')
    d.node('wall', C[2], 250, NW, 110, 'Wall or cost', ['a loosening needs a typed', 'string; a day off costs the', 'Buffer; the Doors pool caps', 'how many can be open'], k='guard')
    d.node('eplan', C[3], 110, NW, 70, 'To Plan', ['the reflow engine lays the', 'day out again'], k='plan', style='ext')
    d.node('eguard', C[3], 280, NW, 70, 'To Guard and alarms', ['blocking and alarms are', 'refreshed after every apply'], k='guard', style='ext')
    for id, _, _ in doors:
        x, y, w, h = d.nodes[id]
        d.edge(id, 'r', 'policy', 'l', fb=min(.95, max(.05, (y + 27 - 150) / 260)), mx=265)
    d.edge('policy', 'r', 'preview', 'l', 'every change', fa=.2)
    d.edge('policy', 'r', 'wall', 'l', 'loosen only', fa=(305 - 150) / 260)
    d.edge('wall', 't', 'preview', 'b', 'after the wall')
    d.edge('preview', 'r', 'eplan', 'l', 'apply', fa=.7)
    d.edge('eplan', 'b', 'eguard', 't', 'refresh')
    d.badge(C[0] + NW, 20, 6)
    return d.svg()


def d_do():
    d = Dg(1120, 520, 'One block can show up to four cards, and each has its own button and its own record. Only the Cycle and the Proof reach the screens and the Buffer. The start-and-end record is read by the Coach and nothing else.')
    d.node('blk', C[0], 175, NW, 130, 'Block screen', ['one block, up to four cards:', 'study blocks show VRNPM and START;', 'routine blocks show Run and START;', 'blocks with proof show PROOF'], k='sur')
    rows = [20, 140, 260, 380]
    cards = [('c1', 'VRNPM card', ['study blocks only', 'Start, hold, Pause, Page done']), ('c2', 'Run card', ['routine, meal, errand, planning', 'hold to start, finish, say']),
             ('c3', 'START card', ['routine and study blocks', "'Start ...', one step at a time"]), ('c4', 'PROOF card', ['photo, git commit, attendance'])]
    for (id, t, s), y in zip(cards, rows):
        d.node(id, C[1], y, NW, 90, t, s, k='do')
    d.node('cycle', C[2], rows[0], NW, 90, 'Stored cycle', ['one record, V R N P M,', 'read every second by the', 'screens'], k='do')
    d.node('rec', C[2], rows[1], NW, 90, 'Run record', ['real start, real end,', 'and the reason when it', 'did not happen'], k='do')
    d.node('run', C[2], rows[2], NW, 90, 'Routine runner', ['third page of Home; steps by', 'tier. Found by title text:', "'Bible', 'Wake', ..."], k='do')
    d.node('ver', C[2], rows[3], NW, 90, 'Verified block', ['the proof, checked,', 'and the minutes credited'], k='do')
    d.node('seen', C[3], rows[0], NW, 90, 'Seen on', ['Home card, lock screen,', 'step-end alarm'], k='sur', style='ext')
    d.node('coach', C[3], rows[1], NW, 210, 'Coach prompts', ['when a routine block starts and', 'you have not started it:', 'the feel, a spoken line, a', 'notification, then the gate.', '', 'It counts a routine started,', 'or a Run record started.'], k='do')
    d.node('count', C[3], rows[3], NW, 90, 'To Count', ['Buffer, money guide,', 'rewards'], k='count', style='ext')
    for (id, _, _), y in zip(cards, rows):
        d.edge('blk', 'r', id, 'l', fa=[.15, .38, .62, .85][rows.index(y)], fb=.5, mx=262)
    d.edge('c1', 'r', 'cycle', 'l'); d.edge('c2', 'r', 'rec', 'l'); d.edge('c3', 'r', 'run', 'l'); d.edge('c4', 'r', 'ver', 'l')
    d.edge('cycle', 'r', 'seen', 'l', 'every second', fa=.18, fb=.3)
    d.edge('rec', 'r', 'coach', 'l', 'counts as|started', fa=.25, fb=.14)
    d.edge('run', 'r', 'coach', 'l', 'counts as|started', fa=.4, fb=.72)
    d.edge('ver', 'r', 'count', 'l', 'proof becomes|minutes')
    d.stub('cycle', 'r', 'not counted|as started', fa=.82)
    d.stub('rec', 'r', 'no link to|rewards', fa=.82, length=30)
    d.badge(C[1] + NW, rows[2], 1)
    d.badge(C[2] + NW, rows[0] + 74, 2)
    d.badge(C[2] + NW, rows[1] + 74, 3)
    d.badge(C[2], rows[2], 5)
    return d.svg()


def d_count():
    d = Dg(1120, 470, 'Count turns proof into minutes and minutes into the Buffer, the money guide and earned phone access. It also holds the pool that every loosening draws from.')
    d.node('tasks', C[0], 30, NW, 70, 'Tasks and deadlines', ['quick add, voice,', 'study cycles from lectures'], k='count')
    d.node('sleep', C[0], 120, NW, 56, 'From Night and morning', ['a short night raises estimates'], k='night', style='ext')
    d.node('cal', C[0], 196, NW, 70, 'Calibration', ['your real hours teach the', 'estimate, quietly'], k='count')
    d.node('proof', C[0], 300, NW, 56, 'From Do', ['proof of a block'], k='do', style='ext')
    d.node('buffer', C[1], 50, NW, 150, 'Buffer', ['spare time before the nearest', 'deadline, as a share of the', 'work due. Estimates come from', 'heaviness and calibration.'], k='count')
    d.node('ver', C[1], 250, NW, 80, 'Verified minutes', ['weighted by heaviness; days', 'off are left out'], k='count')
    d.node('money', C[2], 30, NW, 80, 'Money guide', ['free to spend = 25 x verified', 'over planned, linear, a guide'], k='count')
    d.node('earned', C[2], 140, NW, 80, 'Earned phone access', ['relief opens the block list', 'for a while'], k='count')
    d.node('rewards', C[2], 250, NW, 60, 'Reward menu', ['up to five of your own'], k='count')
    d.node('doors', C[2], 340, NW, 70, 'Doors pool', ['every loosening draws from one', 'pool, so two cannot stack'], k='count')
    d.node('eplan', C[3], 30, NW, 80, 'To Plan', ['Catch-up: missed work', 'returns as work; Allocation'], k='plan', style='ext')
    d.node('eguard', C[3], 140, NW, 60, 'To Guard', ['earned access opens apps'], k='guard', style='ext')
    d.node('esur', C[3], 230, NW, 60, 'To Surfaces', ['Home money card, widgets'], k='sur', style='ext')
    d.node('echg', C[3], 340, NW, 70, 'To Change and Guard', ['the cost of a loosening'], k='change', style='ext')
    d.edge('tasks', 'r', 'buffer', 'l', 'work due', fb=.2)
    d.edge('sleep', 'r', 'buffer', 'l', 'x1.5 hard,|x1.15 easy', fb=.5)
    d.edge('cal', 'r', 'buffer', 'l', 'estimate', fb=.85)
    d.edge('proof', 'r', 'ver', 'l', 'credits', fb=.5)
    d.edge('ver', 'r', 'money', 'l', fa=.15)
    d.edge('ver', 'r', 'earned', 'l', fa=.5)
    d.edge('ver', 'r', 'rewards', 'l', fa=.85)
    d.edge('buffer', 'r', 'money', 'l', 'planned', fa=.3, fb=.5)
    d.edge('money', 'r', 'esur', 'l', fa=.5, via=[(850, 70), (850, 260), (C[3], 260)])
    d.edge('earned', 'r', 'eguard', 'l')
    d.edge('buffer', 'r', 'eplan', 'l', 'missed work, tasks', fa=.1, via=[(C[1] + NW + 20, 65), (C[1] + NW + 20, 18), (870, 18), (870, 60), (C[3], 60)])
    d.edge('doors', 'r', 'echg', 'l')
    return d.svg()


def d_guard():
    d = Dg(1120, 480, 'Guard answers one question, is this app closed right now and why, from four inputs. Everything else here is a way of enforcing the answer or of loosening it.')
    for i, (id, t, s, k) in enumerate([('i1', 'From Plan', ['which block is live'], 'plan'), ('i2', 'From Night and morning', ['the cutoff; social closed', 'until the ready photo'], 'night'), ('i3', 'From Count', ['earned access'], 'count'), ('i4', 'From Do', ['a step can close social or', 'put the phone away'], 'do')]):
        d.node(id, C[0], 30 + i * 85, NW, 66, t, s, k=k, style='ext')
    d.node('state', C[1], 110, NW, 140, 'Blocking state', ['one answer: is this app closed', 'right now, and why. Shown on', 'the blocked card with the', 'reason.'], k='guard')
    d.node('svc', C[2], 20, NW, 70, 'Blocker service', ['reads the foreground app', '(accessibility)'], k='guard')
    d.node('web', C[2], 110, NW, 70, 'Sites on phone and PC', ['one site list: phone address', 'bar, PC extension, hosts file'], k='guard')
    d.node('quiet', C[2], 200, NW, 70, 'Quiet mode', ['notifications held for the', 'digest, except essentials'], k='guard')
    d.node('scoped', C[2], 290, NW, 70, 'Part of an app', ['a podcast yes, Shorts no;', 'off by default'], k='guard')
    d.node('wall', C[2], 390, NW, 80, 'The wall', ['a typed string that gets longer', 'with use. Every loosening of', 'blocking goes through it.'], k='guard')
    d.node('ess', C[3], 20, NW, 70, 'Essentials', ['never blocked, never counted;', 'the fixed core cannot change'], k='guard')
    d.node('time', C[3], 400, NW, 70, 'Clock trust', ['wall clock vs elapsed vs', 'network time; tamper is logged'], k='ctx', style='ext')
    for i, id in enumerate(['i1', 'i2', 'i3', 'i4']):
        d.edge(id, 'r', 'state', 'l', fb=.15 + i * .23)
    for id, f in [('svc', .15), ('web', .4), ('quiet', .65), ('scoped', .88)]:
        d.edge('state', 'r', id, 'l', fa=f)
    d.edge('ess', 'l', 'svc', 'r', 'never', fb=.5)
    d.edge('wall', 'l', 'state', 'b', 'loosens', fb=.5)
    d.edge('time', 'l', 'wall', 'r', 'tamper')
    return d.svg()


def d_night():
    d = Dg(1120, 440, 'The night sets the wake time and the wake time sets the alarm. The wake chooser (now in Change the plan) writes the alarm and a Late start, so the Plan moves the morning blocks with it.')
    d.node('sleep', C[0], 30, NW, 70, 'Sleep sensing', ['screen, charging, movement,', 'light. No microphone.'], k='night')
    d.node('hc', C[0], 130, NW, 70, 'Health Connect', ['optional watch sleep, shown', 'beside the phone night'], k='night')
    d.node('eplan', C[0], 240, NW, 70, 'From Plan', ['the anchors: wake and', 'lights-out of the night'], k='plan', style='ext')
    d.node('ectx', C[0], 340, NW, 56, 'From Context', ['time zone, trusted clock'], k='ctx', style='ext')
    d.node('choice', C[1], 20, NW, 90, 'Wake choice (Change the plan)', ['one night, 15-minute steps;', 'later than normal needs the', 'wall; the morning follows it'], k='night', style='warn')
    d.node('night', C[1], 150, NW, 110, 'Night', ['cutoff = wake - 8 h - time to', 'fall asleep. Lights-out is the', 'cutoff. Lights-out later than', 'it needs the wall.'], k='night')
    d.node('mix', C[1], 310, NW, 70, 'To Count and Plan', ['short sleep raises estimates;', 'Lite day after a short night'], k='count', style='ext')
    d.node('alarm', C[2], 20, NW, 110, 'Wake alarm', ['rings at the wake of the night.', 'Stops with the step count or a', 'second way. Blocks slept', 'through come back as work.'], k='night')
    d.node('morning', C[2], 170, NW, 90, 'Morning', ['the setup photo stops the', 'alarm; the ready photo opens', 'social'], k='night')
    d.node('wind', C[2], 300, NW, 80, 'Wind-down', ['evening prayer, the sleep', 'block, the night routine'], k='night')
    d.node('eguard', C[3], 180, NW, 150, 'To Guard', ['social closed until the ready', 'photo; a wind-down step can', 'put the phone away'], k='guard', style='ext')
    d.edge('sleep', 'r', 'night', 'l', 'onset', fb=.25)
    d.edge('eplan', 'r', 'night', 'l', 'wake', fb=.75, mx=262)
    d.edge('sleep', 'b', 'hc', 't', '')
    d.edge('choice', 'r', 'alarm', 'l', 'alarm only', 'gap', fa=.5, fb=.36)
    d.edge('night', 'r', 'alarm', 'l', 'wake', fa=.2, fb=.85)
    d.edge('night', 'b', 'mix', 't', 'short night')
    d.edge('alarm', 'b', 'morning', 't', 'photo stops it')
    d.edge('morning', 'r', 'eguard', 'l', 'social', fa=.5, fb=.2)
    d.edge('wind', 'r', 'eguard', 'l', 'step gates', fa=.5, fb=.85)
    d.badge(C[1] + NW, 20, 4)
    return d.svg()


def d_ctx():
    d = Dg(1120, 420, 'Context is small: where you are, whether the clock can be trusted, and what kind of day it is. Each result is read by another system, and none reads anything back.')
    d.node('places', C[0], 30, NW, 80, 'Places', ['flat, family home, each with', 'a point, a radius and Wi-Fi names'], k='ctx')
    d.node('venues', C[0], 150, NW, 80, 'Lecture venues', ['from OpenStreetMap; you can', 'place one yourself, logged'], k='ctx')
    d.node('fix', C[1], 30, NW, 80, 'Location fix', ['read once with a photo and', 'never kept (A8)'], k='ctx')
    d.node('daytype', C[2], 150, NW, 80, 'Day type', ['proposed from the calendar', 'and place, confirmed once'], k='ctx')
    d.node('clock', C[1], 280, NW, 80, 'Clock trust', ['wall clock vs elapsed vs', 'network time'], k='ctx')
    d.node('edo', C[3], 42, NW, 56, 'To Do', ['attendance proof'], k='do', style='ext')
    d.node('eplan', C[3], 162, NW, 56, 'To Plan and Change', ['flat or family home; library'], k='plan', style='ext')
    d.node('eguard', C[3], 262, NW, 56, 'To Guard', ['tamper log, night cutoffs'], k='guard', style='ext')
    d.node('enight', C[3], 332, NW, 56, 'To Night and morning', ['time zone, wake date'], k='night', style='ext')
    d.edge('places', 'r', 'fix', 'l', 'home', fb=.3)
    d.edge('venues', 'r', 'fix', 'l', 'room', fb=.8)
    d.edge('fix', 'r', 'edo', 'l', 'for attendance')
    d.edge('daytype', 'r', 'eplan', 'l')
    d.edge('clock', 'r', 'eguard', 'l', fa=.3, mx=870)
    d.edge('clock', 'r', 'enight', 'l', fa=.7, mx=870)
    return d.svg()


def d_t1():
    d = Dg(1120, 420, 'One block, one hold. Today a study block has a start for the cycle and a different start for the routine, and the Coach reads neither the cycle nor the routine runner. In the proposal one hold writes one run record and everything else reads it.')
    d.panel(0, 0, 548, 420, 'Today, on a study block')
    d.panel(572, 0, 548, 420, 'Proposed')
    d.node('b1', 15, 130, 100, 70, 'Block', ['study'], k='sur')
    d.node('v', 160, 40, 170, 70, 'VRNPM card', ['its own hold'], k='do')
    d.node('s', 160, 130, 170, 70, 'START card', ['opens the routine runner'], k='do', style='warn')
    d.node('p', 160, 220, 170, 70, 'PROOF card', ['photo at the end'], k='do')
    d.node('cy', 375, 40, 158, 70, 'Stored cycle', ['Home, lock screen'], k='do')
    d.node('ru', 375, 130, 158, 70, 'Routine runner', ['third page'], k='do')
    d.node('vf', 375, 220, 158, 70, 'Verified', ['to Count'], k='count', style='ext')
    d.node('co', 160, 320, 373, 70, 'Coach prompts', ['read the routine record and the Run record,', 'not the stored cycle, so a started cycle can be asked about'], k='do', style='warn')
    d.edge('b1', 'r', 'v', 'l', fa=.2, mx=138); d.edge('b1', 'r', 's', 'l', mx=138); d.edge('b1', 'r', 'p', 'l', fa=.8, mx=138)
    d.edge('v', 'r', 'cy', 'l'); d.edge('s', 'r', 'ru', 'l'); d.edge('p', 'r', 'vf', 'l')
    d.badge(330, 130, 1); d.badge(160, 320, 2)
    d.node('b2', 587, 175, 100, 70, 'Block', ['any type'], k='sur')
    d.node('h', 725, 175, 150, 70, 'One hold', ['to start the block'], k='do')
    d.node('en', 925, 40, 175, 100, 'Engine by block type', ['study: VRNPM cycle', 'routine: its steps', 'anything else: a plain run'], k='do')
    d.node('rr', 925, 170, 175, 80, 'One run record', ['start, end, reason'], k='do')
    d.node('rd', 925, 280, 175, 110, 'Everything reads it', ['Coach, Count, Guard gate,', 'Home, lock screen, alarm,', 'the day strip'], k='sur', style='ext')
    d.edge('b2', 'r', 'h', 'l'); d.edge('h', 'r', 'en', 'l', 'starts', fa=.3, mx=900); d.edge('en', 'b', 'rr', 't', 'writes'); d.edge('rr', 'b', 'rd', 't', 'read')
    d.edge('h', 'r', 'rr', 'l', fa=.7, mx=900)
    return d.svg()


def d_t2():
    d = Dg(1120, 420, 'One night, one record. Today three things write the wake time, and the Setup chooser reaches only the alarm. In the proposal they all write tonight\'s anchors, and the alarm, the morning blocks and the guard window read them.')
    d.panel(0, 0, 548, 420, 'Today')
    d.panel(572, 0, 548, 420, 'Proposed')
    d.node('w1', 15, 40, 180, 60, 'Weekly Wake block', ['Plan'], k='plan')
    d.node('w2', 15, 150, 180, 60, 'Night preset', ['Modify Schedule'], k='change')
    d.node('w3', 15, 260, 180, 60, 'Wake chooser', ['Setup'], k='night', style='warn')
    d.node('bl', 365, 35, 168, 70, 'Wake and getting-ready', ['blocks of the day'], k='plan')
    d.node('al', 365, 255, 168, 70, 'Wake alarm', ['reads one key'], k='night')
    d.edge('w1', 'r', 'bl', 'l', 'defines')
    d.edge('w2', 'r', 'bl', 'l', 'reflow', fa=.3, fb=.82, mx=265)
    d.edge('w2', 'r', 'al', 'l', fa=.7, fb=.2, mx=300)
    d.edge('w3', 'r', 'al', 'l', 'wake key')
    d.stub('w3', 'b', 'blocks do not|move', fa=.5, length=40)
    d.badge(195, 260, 4)
    d.node('p1', 587, 50, 160, 60, 'Weekly Wake block', ['Plan'], k='plan')
    d.node('p2', 587, 150, 160, 60, 'Night preset', ['Modify Schedule'], k='change')
    d.node('p3', 587, 250, 160, 60, 'Wake chooser', ['Setup'], k='night')
    d.node('an', 800, 130, 140, 100, "Tonight's anchors", ['wake and lights-out,', 'one record, in Plan'], k='plan')
    d.node('r1', 985, 30, 120, 56, 'Alarm', [], k='night')
    d.node('r2', 985, 100, 120, 56, 'Morning blocks', [], k='plan')
    d.node('r3', 985, 170, 120, 56, 'Guard window', [], k='guard')
    d.node('r4', 985, 240, 120, 56, 'Night cutoff', [], k='night')
    d.node('r5', 985, 310, 120, 56, 'Morning photo', [], k='night')
    for id in ['p1', 'p2', 'p3']:
        d.edge(id, 'r', 'an', 'l', fb={'p1': .2, 'p2': .5, 'p3': .8}[id], mx=775)
    for i, id in enumerate(['r1', 'r2', 'r3', 'r4', 'r5']):
        d.edge('an', 'r', id, 'l', fa=.1 + i * .2, mx=962)
    return d.svg()


def d_t3():
    d = Dg(1120, 440, 'One door for every change. The eight doors become eight things you can say, and each goes through the same four steps. Setup loses four cards and Work it out loses its change sections.')
    ch = ['It is running over', 'I started late', 'Something came up', 'A new deadline', 'A day off or a weekend', 'A library period', 'A file from my timetable', 'Spark noticed a drift']
    d.node('door', C[0], 10, NW, 420, 'Change the plan', [], k='change')
    for i, t in enumerate(ch):
        d.node(f'c{i}', C[0] + 12, 40 + i * 48, NW - 24, 38, t, [], k='change', style='ext')
    steps = [('s1', 'Say what', ['what happened, or what you want'], 'change'), ('s2', 'Preview', ['the Buffer before and after'], 'change'), ('s3', 'Cost', ['wall if it loosens;', 'the Doors pool if a door'], 'guard'), ('s4', 'Apply', ['one reflow, one refresh'], 'plan')]
    for i, (id, t, s, k) in enumerate(steps):
        d.node(id, C[1] + 50, 30 + i * 100, NW + 30, 80, t, s, k=k)
    for a, b in [('s1', 's2'), ('s2', 's3'), ('s3', 's4')]:
        d.edge(a, 'b', b, 't')
    d.edge('door', 'r', 's1', 'l', '', fa=.5, mx=275, fb=.5)
    d.node('plan', 760, 335, 250, 70, 'Plan, Guard, alarms', ['the same engine as today;', 'nothing behind it changes'], k='plan', style='ext')
    d.edge('s4', 'r', 'plan', 'l', 'apply', fa=.5, fb=.5)
    d.node('gone', 760, 40, 330, 200, 'What goes away', ['Setup: the Modify Schedule, Days off,', 'Library and Timetable file cards.', '', 'Work it out: What if sleep later,', "Tomorrow's fun, Winter vacation.", '', 'Home: the three hero chips stay, but', 'each opens this door at its step 2.'], k='sur')
    return d.svg()



def d_simple():
    d = Dg(1190, 470, 'Spark as one loop. A change edits the one calendar, the calendar gives the blocks, each block has one Start, the work is counted, and the count feeds the calendar again. Guard closes apps from what is live. Every screen reads the same record.')
    bw, bh = 230, 120
    X = [30, 330, 630, 930]
    d.sysbox('change', X[0], 40, bw, bh, 'change', 'Change', 'One door for every edit', ['late, over, came up, day off,', 'library, file, new deadline'])
    d.sysbox('plan', X[1], 40, bw, bh, 'plan', 'Plan', 'One calendar', ['blocks, routines and their steps;', 'wake and lights-out; places'])
    d.sysbox('do', X[2], 40, bw, bh, 'do', 'Do', 'One Start per block', ['the block type picks the engine:', 'cycle, steps or a plain run'])
    d.sysbox('count', X[3], 40, bw, bh, 'count', 'Count', 'What it is worth', ['minutes, Buffer, money,', 'earned access, rewards'])
    d.sysbox('guard', 480, 230, bw, 110, 'guard', 'Guard', 'What is closed to you', ['reads what is live and what', 'has been earned'])
    d.sysbox('ask', 30, 230, bw, 110, 'sur', 'Confirm', 'One place that asks', ['any system can raise a question;', 'raised once, answered once'])
    d.edge('change', 'r', 'plan', 'l', 'edits')
    d.edge('plan', 'r', 'do', 'l', 'blocks')
    d.edge('do', 'r', 'count', 'l', 'proof')
    d.edge('count', 't', 'plan', 't', 'missed work and tasks come back', via=[(1045, 18), (445, 18)])
    d.edge('plan', 'b', 'guard', 't', 'live block', fa=.8, fb=.25)
    d.edge('do', 'b', 'guard', 't', 'a started step', fa=.2, fb=.75)
    d.edge('count', 'b', 'guard', 'r', 'earned access', fa=.3, fb=.5)
    d.node('sur', 30, 380, 1130, 70, 'Screens: Home, Block, Setup, Work it out, lock screen, alarm', ['Each reads the same stored record. None keeps its own copy.'], k='sur')
    d.edge('ask', 'b', 'sur', 't', 'Home card, lock screen', fa=.3, fb=.1)
    return d.svg()

# ===== page ====================================================================
GAPS = [
    (1, 'A block had two starts.', 'Study blocks show the VRNPM card and, under it, a START card for the study routine. Routine blocks show the hold-to-start Run card and the same START card. Each has its own button and its own record.',
     'BlockScreen.kt:137, :145, :155; RoutinePrompts.kt:15', 'Done 20 Nov (VR15): study blocks and blocks with the Run card have one Start, and the hold hands a routine to its runner.'),
    (2, 'Starting a cycle did not count as starting.', 'The Coach asks whether a routine was started by reading the routine record and the Run record. It never reads the cycle, so a running cycle can still be told it has not started.',
     'Coach.kt:169', 'Done 20 Nov (VR15): a cycle start writes the one run record that the Coach reads.'),
    (3, 'Start, end and say went nowhere.', 'The Run record is read by the Coach and by the day strip, and the strip is no longer on Home. Nothing in Rewards or the Buffer reads it. You asked how an unproven block earns a reward, and today a held start earns none.',
     'RunRepo and VrRepo are used only in BlockScreen, MainActivity, VrNotifier and Coach', 'Done 20 Nov (SR1): a held start earns 2 minutes of open phone, for 5 starts a day.'),
    (4, 'Wake time has three owners.', 'The weekly Wake block, the night preset (it writes the wake key from the anchors), and the Setup wake chooser. The chooser moves the alarm only. The Wake and getting-ready blocks stay where they were.',
     'ModifierRepo.kt:39; MainActivity.kt:776-790', "Done 20 Nov (CD2, CW1): the wake chooser is in the Change door and a later wake now moves the Wake block and the morning with it, through one Late start (picture 2)."),
    (5, 'Routines are found by title text.', "A block gets its routine because its title starts with Bible, Wake or Breakfast, dress, or because its type is sleep or study. Rename a block and its routine goes. The runner is also a page of its own, not part of the block.",
     'RoutinePrompts.kt:10-17', 'A block carries its routine id and its steps. The runner page goes.'),
    (6, 'The plan had eight doors.', 'Home hero chips, Something came up, Quick add and Voice, and four Setup cards: Modify Schedule, Days off, Library, Timetable file. Modify Schedule describes a period off and the library again in its own text. Adapt is the eighth, and it is automatic.',
     'HomeScreen.kt:241-244; SetupScreen.kt:74-84, 207-225', 'Done 20 Nov (CD1): one Change the plan door; the four Setup cards moved into it. Work it out and Quick add still have their own entries.'),
    (7, 'Setup is 27 cards of three kinds.', 'Do-it-now actions (Modify, Days off, Library, Timetable, Shopping, Widgets), settings (Appearance, Quiet mode, Voice) and permission checks (Blocking, Notifications, Sleep, Browsers). Reminders are spread over five cards: Photo, Meals, Lecture code, Study cycle and Quiet mode.',
     'SetupScreen.kt:63-253', 'Part done 20 Nov (CD3): the change cards and the wake card are out of Setup (27 cards down to 23). Reminders and the permission checks are still to group (table below).'),
    (8, 'Work it out is a drawer of about 20 sections.', 'Emergency, mood, parked ideas, rewards, winter vacation, what-if sleep, Lite day, coach voice, your reminders, family home, permissions and more. Permissions are also in Setup, Lite day is also automatic through Adapt, and Emergency is also three chips on Home.',
     'WorkItOutScreen.kt, WorkItOutMore.kt, WorkItOutExtra.kt', 'Shrink it to Right now; the rest goes where it belongs (table below).'),
]

SYSTEMS = [
    ('plan', 'Plan', 'What is on, and when', 'Owns the calendar. Reads: Change (edits), Count (tasks to place), Night (anchors). Writes: the one answer slotsFor, read by Do, Guard and every screen.', d_plan),
    ('change', 'Change', 'How the plan moves', 'Owns the ways of editing the plan and what each one costs. Reads: nothing but you. Writes: the Plan, through one reflow engine, then refreshes Guard and the alarms.', d_change),
    ('do', 'Do', 'What you do in a block', 'Owns starting, running and finishing a block. Reads: the block from Plan. Writes: proof to Count, a gate to Guard, prompts to the Coach. This is where the systems fail to meet most.', d_do),
    ('count', 'Count', 'What it is worth', 'Owns minutes, the Buffer, money, earned access and the Doors pool. Reads: proof from Do, sleep from Night. Writes: missed work back into Plan, access into Guard.', d_count),
    ('guard', 'Guard', 'What is closed to you', 'Owns the blocking answer and everything that enforces or loosens it. Reads: Plan, Night, Count, Do. Writes: what the phone and PC will open.', d_guard),
    ('night', 'Night and morning', 'Sleep, wake, ready', 'Owns sleep, the night cutoff, the alarm and the morning photos. Reads: anchors from Plan. Writes: the wake key (the weak link), social closed, short-sleep factors.', d_night),
    ('ctx', 'Context', 'Where and when you are', 'Owns places, the location fix, the trusted clock and the day type. It only gives; no other system writes into it.', d_ctx),
]

# which thing is shown where: verified against HomeScreen.kt, SetupScreen.kt, BlockScreen.kt, WorkItOut*.kt, VrNotifier.kt
MATRIX = [
    ('Study cycle', 'card, while running', '', '', 'card (study blocks)', 'settings card', 'ongoing notice, step alarm'),
    ('Wake time', 'Wake block in Now', 'Wake block', '', 'Wake block, START card', 'Wake time card, Morning card', 'alarm'),
    ('Running late', 'three chips', '', '', 'Run card offers Slide', '', ''),
    ('Reminders', '', '', 'your reminders', '', 'photo, meals, lecture code, study cycle', 'every notification'),
    ('Lite day', 'automatic card', '', 'switch', '', '', ''),
    ('Permissions', '', '', 'a section', '', 'Blocking, Notifications, Sleep', ''),
    ('Emergency', 'three chips', '', 'a section', '', '', ''),
    ('Routines', '', '', 'the runner page', 'START card', '', ''),
]

SETUP_ROWS = [
    ('Permissions and health', 'Blocking, Notifications, Sleep, Browsers, Health Connect, and Permissions from Work it out'),
    ('Reminders', 'One list with a switch each: photo, meals, lecture code, study steps, quiet mode, your own reminders'),
    ('Morning and wake', 'Wake time and Morning setup in one card; both write tonight\'s anchors'),
    ('Study cycle', 'The preset (Optimal, Ideal, 2.5 hours), the longest cycle, the remedial or Red Engine switch'),
    ('Look and Home', 'Appearance, Type to open, Widgets, Home place, Family home, Lecture venues'),
    ('Links', 'GitHub, Windows PC, MyUoB, the university help'),
    ('Moves to the Change door', 'Modify Schedule, Days off, Library, Timetable file'),
    ('Moves to Home or a tool shelf', 'Shopping list, Walkthrough, Your data and backup'),
]

WORK_ROWS = [
    ('Moved for you; Today looks like', 'Stay: this is Right now'),
    ('Lite day; After a short night', 'Stay, and say when Adapt turned it on'),
    ('What if sleep later; Tomorrow\'s fun; Winter vacation', 'Move to the Change door as things you can say'),
    ('Emergency; I am out; Lost phone', 'One Emergency shelf, reached from the Home chips'),
    ('Routine runner', 'Goes: the block carries its steps (gap 5)'),
    ('How are you; Park an idea; Cannot sleep; Coach; Your rewards', 'One calm shelf: Notes and calm'),
    ('Your reminders; Permissions; Family home; Your data', 'Move to Setup groups'),
]

VR_ROWS = [
    ('The block ends while a cycle is running', 'The cycle keeps running. It is looked up across yesterday and today, so it can run after the block.', 'Stop, run on, or offer to move the rest of the day?'),
    ('The cycle is longer than the block', 'Never compared. Starting a cycle does not read the block\'s length or time, only your settings.', 'Shrink it, warn, or start at the step that fits?'),
    ('You start late', 'The Run card says so at 5 minutes and offers to slide the day. The cycle does not.', 'Should a late cycle say so and offer the same slide?'),
    ('A step is skipped', 'Only V and R can be skipped, and only by the override setting for the whole cycle.', 'Skip one step on the day, with a reason?'),
    ('Pause is long', 'Time paused is not counted and is shown. There is no limit.', 'After how long does a pause become a missed block?'),
    ('The phone restarts', 'The cycle is stored, so it survives. The step-end alarm was not checked after a restart.', 'To be tested on the phone.'),
]

GATE = [
    ('Which system owns it?', 'One only. If two seem to, one is a surface.'),
    ('What does it read and write?', 'Draw the arrows on the map before writing code. An arrow with no label does not go in.'),
    ('Where is it shown?', 'List every surface. All read the one stored value; none keeps its own copy.'),
    ('What starts it and what stops it?', 'Does that also tell the Coach, Count, Guard and the strip? If not, it is a second start.'),
    ('What does it replace?', 'A new card, button or setting needs an old one folded or removed in the same change.'),
    ('What happens when the block ends, the day moves, the phone restarts or you pause?', 'Write the answer into the ledger row, not just the happy path.'),
    ('How is it proven?', 'One test that drives the real app through the new arrows, not a test of the part alone.'),
]


def table(head, rows, cls=''):
    h = ''.join(f'<th>{esc(x)}</th>' for x in head)
    b = ''.join('<tr>' + ''.join(f'<td>{esc(c)}</td>' for c in r) + '</tr>' for r in rows)
    return f'<div class="tw"><table class="{cls}"><thead><tr>{h}</tr></thead><tbody>{b}</tbody></table></div>'


CSS = """
:root{--bg:#f5f6f8;--card:#ffffff;--ink:#141a22;--ink-2:#4a5563;--rule:#d8dde4;--gap:#d1242f;--plan:#2f6fb0;--change:#b0601a;--do:#2a8650;--count:#8453bd;--guard:#17808a;--night:#4a4fc2;--ctx:#7b7b1f;--sur:#5a6670;
--sans:'IBM Plex Sans',system-ui,-apple-system,'Segoe UI',sans-serif;--mono:'IBM Plex Mono',ui-monospace,Consolas,monospace}
@media (prefers-color-scheme:dark){:root:not([data-theme="light"]){--bg:#0e1217;--card:#161b22;--ink:#e8ecf1;--ink-2:#a2acb9;--rule:#2a323d;--gap:#ff7b7b;--plan:#7fb2ea;--change:#e8a468;--do:#6fcf97;--count:#b99be6;--guard:#5fcfcf;--night:#9a9df0;--ctx:#cfcf6a;--sur:#a9b4bc;color-scheme:dark}}
:root[data-theme="dark"]{--bg:#0e1217;--card:#161b22;--ink:#e8ecf1;--ink-2:#a2acb9;--rule:#2a323d;--gap:#ff7b7b;--plan:#7fb2ea;--change:#e8a468;--do:#6fcf97;--count:#b99be6;--guard:#5fcfcf;--night:#9a9df0;--ctx:#cfcf6a;--sur:#a9b4bc;color-scheme:dark}
*{box-sizing:border-box}
body{background:var(--bg);color:var(--ink);font:15px/1.55 var(--sans);margin:0}
.wrap{max-width:1180px;margin:0 auto;padding-block:28px 64px;padding-left:max(16px,env(safe-area-inset-left,0px));padding-right:max(16px,env(safe-area-inset-right,0px))}
h1{font-size:clamp(26px,4vw,38px);line-height:1.12;margin:0 0 10px;letter-spacing:-.01em;text-wrap:balance}
h2{font-size:22px;margin:54px 0 6px;letter-spacing:-.005em;text-wrap:balance;scroll-margin-top:64px}
h3{font-size:17px;margin:34px 0 4px;display:flex;align-items:center;gap:9px;scroll-margin-top:64px}
p{margin:0 0 12px;max-width:72ch}
.lead{font-size:17px;color:var(--ink-2);max-width:68ch}
.dot{width:12px;height:12px;border-radius:3px;background:var(--c);flex:none}
.kicker{font:600 11px var(--mono);letter-spacing:.09em;text-transform:uppercase;color:var(--ink-2);margin-bottom:6px}
nav.nv{position:sticky;top:env(safe-area-inset-top,0px);z-index:5;background:var(--bg);border-bottom:1px solid var(--rule);margin:18px calc(-1 * max(16px,env(safe-area-inset-left,0px))) 0;padding:8px max(16px,env(safe-area-inset-left,0px));display:flex;gap:6px;overflow-x:auto}
nav.nv a{white-space:nowrap;font-size:13px;color:var(--ink);text-decoration:none;border:1px solid var(--rule);border-radius:999px;padding:4px 12px;background:var(--card)}
nav.nv a:hover,nav.nv a:focus-visible{border-color:var(--ink-2);outline:none}
.fg{margin:14px 0 6px}
.sv{overflow-x:auto;border:1px solid var(--rule);border-radius:12px;background:var(--card);padding:6px}
.sv svg{display:block;width:100%;min-width:760px;height:auto}
figcaption{font-size:13px;color:var(--ink-2);margin-top:8px;max-width:80ch}
.s-plan{--c:var(--plan)}.s-change{--c:var(--change)}.s-do{--c:var(--do)}.s-count{--c:var(--count)}.s-guard{--c:var(--guard)}.s-night{--c:var(--night)}.s-ctx{--c:var(--ctx)}.s-sur{--c:var(--sur)}
.nd rect{fill:var(--card);stroke:var(--c);stroke-width:1.6}
.nd.ext rect{stroke-dasharray:5 4;fill:var(--bg)}
.nd.warn rect{stroke:var(--gap);stroke-width:2.2}
.nt{font:600 13px var(--sans);fill:var(--ink)}
.ns{font:11px var(--sans);fill:var(--ink-2)}
.sbr{fill:var(--card);stroke:var(--c);stroke-width:1.8}
.sbh{fill:var(--c);opacity:.22}
.st{font:700 15px var(--sans);fill:var(--ink)}
.sr{font:600 12px var(--sans);fill:var(--c)}
.e{fill:none;stroke:var(--ink-2);stroke-width:1.5;stroke-linejoin:round}
.e.gap{stroke:var(--gap);stroke-dasharray:6 4;stroke-width:1.8}
.e.x{stroke-dasharray:none;stroke-width:2.2}
.ah{fill:var(--ink-2)}.ah.gh{fill:var(--gap)}
.lab{font:11px var(--sans);fill:var(--ink-2);paint-order:stroke;stroke:var(--card);stroke-width:5px;stroke-linejoin:round}
.lab.gapt{fill:var(--gap);font-weight:600}
.bg circle{fill:var(--gap)}.bg text{font:700 12px var(--sans);fill:var(--bg);text-anchor:middle}
.pn rect{fill:none;stroke:var(--rule);stroke-width:1.5;stroke-dasharray:2 5}
.pn text{font:600 12px var(--mono);fill:var(--ink-2);letter-spacing:.06em;text-transform:uppercase}
.gl{display:grid;gap:12px;margin-top:16px}
.gi{display:grid;grid-template-columns:34px 1fr;gap:6px 14px;background:var(--card);border:1px solid var(--rule);border-radius:12px;padding:14px 16px}
.gn{width:28px;height:28px;border-radius:50%;background:var(--gap);color:var(--bg);font:700 14px var(--sans);display:grid;place-items:center}
.gi h4{margin:2px 0 4px;font-size:15.5px}
.gi p{margin:0 0 6px;font-size:14px;max-width:none}
.gi .ev{font:12px var(--mono);color:var(--ink-2);overflow-wrap:anywhere}
.gi .fx{font-size:14px}
.gi .fx b{font-weight:600}
.tw{overflow-x:auto;border:1px solid var(--rule);border-radius:12px;background:var(--card);margin:12px 0}
table{border-collapse:collapse;width:100%;font-size:13.5px}
th,td{padding:8px 12px;text-align:left;vertical-align:top;border-bottom:1px solid var(--rule)}
th{font:600 11px var(--mono);letter-spacing:.07em;text-transform:uppercase;color:var(--ink-2);background:var(--bg);white-space:nowrap}
tr:last-child td{border-bottom:0}
td:first-child{font-weight:600}
table.mx td:not(:first-child){color:var(--ink-2)}
ol.gate{padding:0;margin:14px 0;list-style:none;counter-reset:g;display:grid;gap:8px}
ol.gate li{counter-increment:g;background:var(--card);border:1px solid var(--rule);border-radius:12px;padding:10px 14px 10px 50px;position:relative}
ol.gate li::before{content:counter(g);position:absolute;left:14px;top:10px;font:700 13px var(--mono);color:var(--ink-2)}
ol.gate b{display:block;font-weight:600}
ol.gate span{font-size:14px;color:var(--ink-2)}
.note{border-left:3px solid var(--gap);padding:6px 0 6px 14px;margin:14px 0;font-size:14.5px;max-width:74ch}
.legend{display:flex;flex-wrap:wrap;gap:6px 18px;font-size:12.5px;color:var(--ink-2);margin:12px 0}
.legend span{display:inline-flex;align-items:center;gap:6px}
.legend i{width:12px;height:12px;border-radius:3px;background:var(--c);display:inline-block}
details.more>summary{cursor:pointer;font-weight:600;margin:40px 0 0;padding:12px 16px;border:1px solid var(--rule);border-radius:12px;background:var(--card)}
details.more[open]>summary{margin-bottom:0}
small.foot{display:block;margin-top:40px;color:var(--ink-2);font-size:12.5px;max-width:80ch}
@media print{
 @page{size:A4 landscape;margin:10mm}
 :root{--bg:#fff}
 body{font-size:12.5px;background:#fff}
 nav.nv{display:none}
 details.more>summary{display:none}
 .wrap{max-width:none;padding:0}
 h2{break-before:page;margin-top:0}
 h2#map{break-before:auto}
 h3,figure,.gi,.tw,.note{break-inside:avoid}
 h3{break-after:avoid}
 .sv{overflow:visible;border:0;padding:0}.sv svg{min-width:0;max-height:150mm}
}
"""


def build(simple=True):
    out = []
    out.append('<title>Spark systems map</title><meta name="claude:viewport-fit-sides" content="cover">')
    out.append(f'<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@500;600&family=IBM+Plex+Sans:wght@400;500;600;700&display=swap"><style>{CSS}</style>')
    out.append('<div class="wrap">')
    out.append('<div class="kicker">Spark · read from the code, 20 November 2026</div>')
    out.append('<h1>Spark is one loop, and the fix is three moves.</h1>')
    out.append('<p class="lead">Read from the code. Moves 1 and 2 (the door) and the confirmation system are built (20 November 2026); move 3 and the rest of the gaps are proposed. The detail behind this page, with every system and the eight gaps, is folded at the bottom.</p>')
    out.append('<nav class="nv" aria-label="Sections"><a href="#one">One picture</a><a href="#moves">Three moves</a><a href="#need">Decisions made</a></nav>')

    out.append('<h2 id="one">Spark in one picture</h2>')
    out.append('<p>Seven systems collapse to four once the doubles are removed. Night and morning and Context fold into Plan and Guard, and Change becomes one door.</p>')
    out.append(d_simple())
    out.append('<h2 id="moves">Three moves</h2>')
    out.append('<div class="gl">')
    for n, t, b in [(1, 'One Start per block (built)', 'A block has one hold to start it. Its type picks the engine: study runs the cycle, a routine runs its own steps, anything else is a plain run. It writes one run record, and the Coach, Count, Guard and every screen read that.'),
                    (2, 'One door for change (built)', 'Late, over, came up, a new deadline, a day off, the library, a file and a week or period are all said in one place, Change the plan, and each shows what it does, what it costs and applies it. The wake time now moves the morning too. Still to do: a block carrying its own steps so the runner page can go.'),
                    (3, 'Fewer, plainer screens', 'Setup becomes five groups and loses its action cards. Work it out becomes Right now. Reminders become one list.')]:
        out.append(f'<div class="gi"><div class="gn" style="background:var(--ink)">{n}</div><div><h4>{esc(t)}</h4><p>{esc(b)}</p></div></div>')
    out.append('</div>')
    out.append('<h2 id="need">Decisions made</h2>')
    out.append('<ol class="gate"><li><b>A held start earns a small reward.</b><span>2 minutes of earned open phone, for the first 5 starts of a day, so at most 10 minutes. It is not proof and earns nothing in the money guide.</span></li><li><b>When a study block ends mid-cycle, Spark asks.</b><span>Stop the cycle, let it run on, or move the rest of the day. The asking is a confirmation system: anything else that needs confirming raises a question and gets the Home card and the lock-screen buttons without more being built.</span></li></ol>')
    out.append('<details class="more"' + ('' if simple else ' open') + '><summary>All the detail: the full map, eight gaps, each system, three traces, tables</summary>')
    out.append('<div class="legend"><span style="--c:var(--plan)"><i></i>Plan</span><span style="--c:var(--change)"><i></i>Change</span><span style="--c:var(--do)"><i></i>Do</span><span style="--c:var(--count)"><i></i>Count</span><span style="--c:var(--guard)"><i></i>Guard</span><span style="--c:var(--night)"><i></i>Night and morning</span><span style="--c:var(--ctx)"><i></i>Context</span><span style="--c:var(--sur)"><i></i>Surfaces</span><span>Dashed box: a system seen from another one</span><span>Red dashed line with a cross: a link that is missing</span></div>')
    out.append('<h2 id="map">The whole map today</h2>')
    out.append('<p>Work flows left to right: a change edits the plan, the plan gives you today\'s blocks, the blocks produce proof, and proof becomes minutes that feed back into the plan. Guard sits underneath and closes apps according to the live block. The numbers are the gaps in the next section.</p>')
    out.append(d_map())

    out.append('<h2 id="gaps">Eight places where the systems do not connect</h2>')
    out.append('<p>These are what you have been hitting one feature at a time. Seven of them are not bugs in a feature. They are a feature added without joining it to the system around it.</p>')
    out.append('<div class="note"><b>About the home screen.</b> The cycle card on Home is drawn only while a cycle is running (HomeScreen.kt:192). When it is not running, the card you see for a study block is the Now card, which opens the block screen, and that screen shows the VRNPM card and the Start card together (gap 1). If a different card is showing on Home when no cycle is running, tell me which, because the code says it should not.</div>')
    out.append('<div class="gl">')
    for n, t, body, ev, fx in GAPS:
        out.append(f'<div class="gi"><div class="gn">{n}</div><div><h4>{esc(t)}</h4><p>{esc(body)}</p><p class="ev">{esc(ev)}</p><p class="fx"><b>Fix:</b> {esc(fx)}</p></div></div>')
    out.append('</div>')

    out.append('<h2 id="systems">Each system and its parts</h2>')
    out.append('<p>One picture per system: what is inside, what comes in, and what leaves. Boxes with a dashed edge belong to another system and are shown only to say where the arrow goes.</p>')
    for k, name, role, text, fn in SYSTEMS:
        out.append(f'<h3 style="--c:var(--{k})"><span class="dot"></span>{esc(name)}<span style="font-weight:400;color:var(--ink-2);font-size:14px">&nbsp;· {esc(role)}</span></h3><p>{esc(text)}</p>')
        out.append(fn())

    out.append('<h2 id="screens">Surfaces: one fact in several places</h2>')
    out.append('<p>The screens are not a system. They draw the systems, and the same thing is drawn in many places. A cell is blank when that screen does not show it.</p>')
    out.append(table(['Thing', 'Home', 'Schedule page', 'Work it out', 'Block screen', 'Setup', 'Lock screen and alerts'], MATRIX, 'mx'))
    out.append('<p>The study cycle is the one thing built to be a single stored value read by every surface (VR14). Wake time, reminders and lateness are not: each is a separate copy or a separate control.</p>')

    out.append('<h2 id="traces">One action, one system</h2>')
    out.append('<p>A button should change a system, not a component. Three actions drawn as they work now and as they would work if the button owned the whole path. These are proposals; none is built.</p>')
    out.append('<h3 style="--c:var(--do)"><span class="dot"></span>1. Starting a block</h3>')
    out.append(d_t1())
    out.append('<h3 style="--c:var(--night)"><span class="dot"></span>2. Setting the wake time</h3>')
    out.append(d_t2())
    out.append('<h3 style="--c:var(--change)"><span class="dot"></span>3. Changing the plan</h3>')
    out.append(d_t3())

    out.append('<h2 id="setup">Setup and Work it out, regrouped</h2>')
    out.append('<p>Both pages mix three kinds of thing: actions you take now, settings you leave alone, and checks that a permission is on. Grouping by kind, and moving the actions into the Change door, gives Setup five groups instead of 27 cards.</p>')
    out.append(table(['Setup group (proposed)', 'What goes in it'], SETUP_ROWS))
    out.append(table(['Work it out today', 'Proposed'], WORK_ROWS))

    out.append('<h2 id="vr">VRNPM: what it has to answer</h2>')
    out.append('<p>You asked for the settings to be precise about what happens in each circumstance. The ledger rows VR1 to VR14 say how the cycle runs when nothing goes wrong. They do not say what to do in these cases, so the app does whatever the code happens to do. Each needs a decision before more settings are added.</p>')
    out.append(table(['Circumstance', 'What the code does today', 'Decision needed'], VR_ROWS))

    out.append('<h2 id="gate">The check every new feature has to pass</h2>')
    out.append('<p>From now on each request gets these seven answers before any code, and they go into the ledger row for it. If an answer is "it adds a second one", the feature is joined to the existing one or it waits for your word.</p>')
    out.append('<ol class="gate">' + ''.join(f'<li><b>{esc(q)}</b><span>{esc(a)}</span></li>' for q, a in GATE) + '</ol>')
    out.append('</details>')
    out.append('<small class="foot">How this was made: every box was read from the source files named in the gaps, and each count (27 Setup cards, about 20 Work it out sections) is a count of the code. The red lines are what I could not find in the code, not a claim that something is broken on the phone. Nothing here was run on the phone.</small>')
    out.append('</div>')
    return '\n'.join(out)


if __name__ == '__main__':
    frag = build(simple=True)
    frag_pdf = build(simple=False)
    docs = ROOT / 'docs'
    docs.mkdir(exist_ok=True)
    (docs / 'SYSTEM_MAP.fragment.html').write_text(frag, encoding='utf-8')
    full = '<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>' + frag_pdf + '</html>'
    (docs / 'SYSTEM_MAP.html').write_text(full, encoding='utf-8')
    print('ok', len(frag))
