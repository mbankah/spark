// Generates core/src/main/kotlin/efa/WeekB.kt from seed/master-plan-week-B-and-key-dates.md.
// Run from the efa folder: node tools/gen-seed.mjs
// Applies the ledger section 3 changes (D1..D7, O5, O6, Q-C9 in docs/QUESTIONS.md).
import fs from 'node:fs';

const md = fs.readFileSync('seed/master-plan-week-B-and-key-dates.md', 'utf8').split('\n');
const from = md.findIndex(l => l.startsWith('## B4.B Week type B'));
const to = md.findIndex(l => l.startsWith('## B8. Blocking'));
const days = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];

const toMin = s => { const [h, m] = s.split(':').map(Number); return h * 60 + m; };
let day = null, inSched = false, lastStart = -1, offset = 0;
const slots = [];
for (const raw of md.slice(from, to)) {
  const h = raw.match(/^### (\w+)/);
  if (h && days.includes(h[1])) { day = h[1]; inSched = false; lastStart = -1; offset = 0; continue; }
  if (/\*\*Time\*\*/.test(raw)) { inSched = true; continue; }
  if (/\*\*Meal\*\*/.test(raw)) { inSched = false; continue; }
  if (!inSched || !day) continue;
  const m = raw.match(/^\s+(\d\d:\d\d)-(\d\d:\d\d)\s{2,}(.*)$/);
  if (!m) continue;
  const parts = m[3].trim().split(/\s{2,}/);
  let s = toMin(m[1]) + offset;
  if (s < lastStart) { offset += 1440; s += 1440; }   // after midnight belongs to the same planned day
  lastStart = s;
  let e = toMin(m[2]) + offset; if (e <= s) e += 1440;
  slots.push({ day, s, e, what: parts[0], kind: (parts[1] || '').replace(', locked', '').trim(), notes: (parts[2] || '').replace(/\\'/g, "'") });
}

// ---- section 3 changes ----------------------------------------------------
const hm = t => toMin(t);
const drop = (d, start) => { const i = slots.findIndex(x => x.day === d && x.s === hm(start)); if (i < 0) throw new Error(`no slot ${d} ${start}`); slots.splice(i, 1); };
const freeAfterRemoval = (d, a, b) => { // replace [a,b) with one free block
  const first = slots.findIndex(x => x.day === d && x.s === hm(a));
  slots.splice(first, 0, { day: d, s: hm(a), e: hm(b), what: 'Free time - phone open', kind: 'free', notes: 'Freed by the removed football. Personal football (3 h a week) is placed in free time by the app.' });
};
// Monday 20:00-22:00 football and its travel/buffer: removed (O6). Campus League 13:00 stays (O5).
['19:15', '19:40', '20:00', '22:00'].forEach(t => drop('Monday', t));
freeAfterRemoval('Monday', '19:15', '22:25');
// Tuesday team training dropped, with its kit bag, travel and buffer.
['16:10', '16:30', '16:45', '17:00', '18:00'].forEach(t => drop('Tuesday', t));
freeAfterRemoval('Tuesday', '16:10', '18:15');
// Friday training ends 10:45 (D2 of section 3); the 15 minutes to the lecture is a walk.
{ const f = slots.find(x => x.day === 'Friday' && x.s === hm('09:00')); f.e = hm('10:45');
  slots.splice(slots.indexOf(f) + 1, 0, { day: 'Friday', s: hm('10:45'), e: hm('11:00'), what: 'Walk', kind: 'travel', notes: 'Leave training at 10:45.' }); }
// Saturday 16:45-18:45 football and its travel/buffer: removed (O6).
['16:15', '16:40', '16:45', '18:45'].forEach(t => drop('Saturday', t));
freeAfterRemoval('Saturday', '16:15', '19:10');

// ---- naming (W5, W7) --------------------------------------------------------
const place = n => (n.match(/^([^.]*?)\./)?.[1] ?? '').replace(/EDUC-/, '').replace(/[()]/g, '').replace(/,\s*/g, ' ').replace(/\s+/g, ' ').trim();
const out = slots.map(x => {
  let title = x.what, type = x.kind.split(',')[0].trim(), venue = '', hard = false;
  let m;
  if ((m = x.what.match(/^LECTURE: (.*)$/))) { title = `${m[1]} lecture - ${place(x.notes)}`; type = 'lecture'; venue = place(x.notes); }
  else if ((m = x.what.match(/^TUTORIAL: (.*)$/))) { title = `${m[1]} tutorial - ${place(x.notes)}`; type = 'tutorial'; venue = place(x.notes); }
  else if (/^MyTutor session/.test(x.what)) { type = 'mytutor'; hard = true; }
  else if (/^LANGUAGE LESSON/.test(x.what)) title = 'Language lesson';   // the language changes by milestone, so the name stays generic
  else if ((m = x.what.match(/^VRNP cycle \((std|light)\): (.*)$/))) { title = `Notes and problems: ${m[2].replace(/ lecture( \d)?$/, '').replace(/ tutorial fixes$/, ' tutorial')}`; type = m[1] === 'std' ? 'study-standard' : 'study-light'; }
  else if (/^CAMPUS LEAGUE FOOTBALL/.test(x.what)) { title = 'Campus League football'; type = 'match'; }
  else if (/^TEAM FOOTBALL TRAINING/.test(x.what)) { title = 'Team football training'; type = 'training'; }
  else if (/^GYM/.test(x.what)) { title = 'Gym'; type = 'gym'; }
  else if (/^CHURCH$/.test(x.what)) { title = 'Church'; type = 'church'; }
  else if (/^Wake and shower/.test(x.what)) title = 'Wake, shower';
  else if (/^(Walk|Travel)/.test(x.what) && type === 'travel') { /* keep */ }
  else if (/^FREE:/.test(x.what)) { title = 'Free time - phone open'; type = 'free'; }
  else if (x.kind === 'free') type = 'free';
  else if (type === 'class') type = 'mytutor-notes';
  else if (/^Shutdown/.test(x.what)) { title = 'Shutdown'; type = 'sleep'; }
  else if (/^CHORES|^CLEAN|^HOOVER|^LAUNDRY|^IRONING|^Wash dishes/.test(x.what)) type = 'chore';
  else if (/^[A-Z .\/]{6,}( block| session|\b)/.test(x.what) && x.what === x.what.toUpperCase()) title = x.what.charAt(0) + x.what.slice(1).toLowerCase();
  if (/^MyTutor session/.test(x.what)) title = x.what + ' - online';
  // Plain names (W5, W7) and proof kind: code blocks are proved by a git commit (G1).
  const rename = { 'CS PROJECT block': ['CS Project', 'code'], 'BATTL.IO': ['Battl.io', 'code'], 'MINI / SELF PROJECT': ['Mini and self project', 'code'],
    'CV / CONNECTIONS / INTERVIEW LAB / IMC': ['CV, connections, interview lab, IMC', 'study'], 'RED ENGINE session': ['Red Engine session', 'study'],
    'CLEAN THE BATHROOM': ['Clean the bathroom'], 'HOOVER THE FLOORS': ['Hoover the floors'], 'IRONING': ['Ironing'],
    'LAUNDRY: put the clean clothes away': ['Laundry: put the clean clothes away'],
    'CHORES: bins out, TAKE THE WASHING OUT and hang it': ['Chores: bins out, take the washing out and hang it'],
    'Lesson notes, break, WASH ON': ['Lesson notes, break, wash on'], 'ALDI run (walk included)': ['Aldi run (walk included)'] };
  if (rename[x.what]) { title = rename[x.what][0]; if (rename[x.what][1]) type = rename[x.what][1]; }
  if (title === 'Language lesson') type = 'code';
  return { ...x, title, type, venue, hard };
});

const k = s => JSON.stringify(s).replace(/\$/g, '\\$');
let kt = `package efa

// GENERATED by tools/gen-seed.mjs from seed/master-plan-week-B-and-key-dates.md. Do not edit by hand.
// Section 3 changes applied: Tuesday training dropped, Friday training ends 10:45, Monday 20:00 and Saturday 16:45
// football removed (replaced by free time), Campus League football Monday 13:00 only.
// Times are minutes after 00:00 of the planned day; values over 1440 fall after midnight on the next calendar day.

import java.time.DayOfWeek

data class PatternSlot(
    val day: DayOfWeek, val startMin: Int, val endMin: Int, val title: String, val type: String,
    val venue: String, val hardAlarm: Boolean, val notes: String,
)

object WeekB {
    val slots: List<PatternSlot> = listOf(
`;
const dow = { Monday: 'MONDAY', Tuesday: 'TUESDAY', Wednesday: 'WEDNESDAY', Thursday: 'THURSDAY', Friday: 'FRIDAY', Saturday: 'SATURDAY', Sunday: 'SUNDAY' };
for (const x of out) kt += `        PatternSlot(DayOfWeek.${dow[x.day]}, ${x.s}, ${x.e}, ${k(x.title)}, ${k(x.type)}, ${k(x.venue)}, ${x.hard}, ${k(x.notes)}),\n`;
kt += `    )\n}\n`;
fs.writeFileSync('core/src/main/kotlin/efa/WeekB.kt', kt);
const types = {}; out.forEach(x => types[x.type] = (types[x.type] || 0) + 1);
console.log(out.length, 'slots', JSON.stringify(types));
