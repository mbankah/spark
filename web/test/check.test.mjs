import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { checkCsv, parseCsv, COLUMNS } from '../check.js';

const H = COLUMNS.join(',');
const doc = readFileSync(new URL('../../docs/TIMETABLE_CSV.md', import.meta.url), 'utf8');
const example = /## Example\s+```\n([\s\S]*?)```/.exec(doc)[1];

test('IM1: the example in the format document passes the quick check', () => assert.deepEqual(checkCsv(example).errors, []));

test('IM3: a file with a wrong header says which column is missing', () => {
  const r = checkCsv('kind,date\nevent,2026-11-02');
  assert.ok(r.errors.some((e) => e.column === 'end_date'));
});

test('IM4: every error names the line and the column', () => {
  const r = checkCsv(`${H}\nevent,2026-13-40,,09:00,10:00,Lecture,lecture,,,,,\nevent,2026-11-02,,9am,10:00,Lecture,lecture,,,,,\n`);
  assert.deepEqual(r.errors.map((e) => [e.line, e.column]), [[2, 'date'], [3, 'start']]);
});

test('IM4: a line with a missing cell is reported as a whole', () => {
  const r = checkCsv(`${H}\nevent,2026-11-02,,09:00,10:00,Lecture,lecture\n`);
  assert.equal(r.errors[0].line, 2); assert.match(r.errors[0].text, /cells/);
});

test('IM2: a type that does not exist, a day off and a library period are refused', () => {
  const r = checkCsv(`${H}\nevent,2026-11-02,,09:00,10:00,Party,party,,,,,\nperiod,2026-11-02,2026-11-03,,,Off,day_off,,,,,\nperiod,2026-11-02,2026-11-03,,,Lib,library,,,,,\n`);
  assert.deepEqual(r.errors.map((e) => [e.line, e.column]), [[2, 'type'], [3, 'type'], [4, 'type']]);
});

test('PW1: a period is 1 to 14 days', () => {
  assert.equal(checkCsv(`${H}\nperiod,2026-11-02,2026-11-15,,,A,assignment_week,,,,,\n`).errors.length, 0);
  assert.equal(checkCsv(`${H}\nperiod,2026-11-02,2026-11-16,,,A,assignment_week,,,,,\n`).errors[0].column, 'end_date');
  assert.equal(checkCsv(`${H}\nperiod,2026-11-05,2026-11-02,,,A,assignment_week,,,,,\n`).errors[0].column, 'end_date');
});

test('D2: a deadline needs a due time and a heaviness from 1 to 10 or empty', () => {
  assert.equal(checkCsv(`${H}\ndeadline,2026-11-04,,17:00,,GT assignment,,,,,,\n`).errors.length, 0);
  assert.equal(checkCsv(`${H}\ndeadline,2026-11-04,,17:00,,GT assignment,,,11,,,\n`).errors[0].column, 'heaviness');
  assert.equal(checkCsv(`${H}\ndeadline,2026-11-04,,,,GT assignment,,,,,,\n`).errors[0].column, 'start');
});

test('IM1: both date styles, semicolons, tabs and quoted commas are read', () => {
  assert.equal(checkCsv(`${H}\nevent,02/11/2026,,09:00,10:00,"Maths, part 1",lecture,,,,,\n`).errors.length, 0);
  assert.equal(checkCsv(`${H.replaceAll(',', ';')}\nevent;2026-11-02;;09:00;10:00;Maths;lecture;;;;;\n`).errors.length, 0);
  assert.equal(parseCsv('a\tb\n1\t2').length, 2);
});

test('IM9: an empty file, a huge file and a real date check are handled', () => {
  assert.match(checkCsv('').errors[0].text, /empty/);
  assert.match(checkCsv('x'.repeat(1_000_001)).errors[0].text, /1 MB/);
  assert.equal(checkCsv(`${H}\nevent,2026-02-30,,09:00,10:00,A,lecture,,,,,\n`).errors[0].column, 'date');
});

test('IM1: the page offers the same prompt as the app and the format document', () => {
  const prompt = /## Give this to an AI[\s\S]*?```\n([\s\S]*?)```/.exec(doc)[1].trim();
  const page = readFileSync(new URL('../csv-prompt.html', import.meta.url), 'utf8');
  const embedded = /<textarea id="prompt"[^>]*>([\s\S]*?)<\/textarea>/.exec(page)[1].replaceAll('&lt;', '<').replaceAll('&gt;', '>').replaceAll('&quot;', '"').replaceAll('&amp;', '&').trim();
  assert.equal(embedded, prompt);
});

test('IM1: the page buttons work: copy, example, check and save', async () => {
  const page = readFileSync(new URL('../csv-prompt.html', import.meta.url), 'utf8');
  const script = /<script>([\s\S]*)<\/script>/.exec(page)[1];
  const els = {}; const el = (id) => (els[id] ??= { id, value: id === 'prompt' ? 'THE PROMPT' : '', textContent: '', className: '', children: [], appendChild(c) { this.children.push(c); }, select() {} });
  let copied = null; let saved = null;
  const document = { getElementById: el, createElement: () => ({ children: [], appendChild(c) { this.children.push(c); }, click() { saved = this.download; }, remove() {} }), body: { appendChild() {} }, execCommand() {} };
  const navigator = { clipboard: { writeText: async (t) => { copied = t; } } };
  const urlApi = { createObjectURL: () => 'blob:x' };
  new Function('document', 'navigator', 'URL', 'Blob', 'confirm', 'TextEncoder', script)(document, navigator, urlApi, class {}, () => true, TextEncoder);
  await els.copy.onclick(); assert.equal(copied, 'THE PROMPT'); assert.match(els.copied.textContent, /Copied/);
  els.sample.onclick(); assert.ok(els.csv.value.startsWith('kind,date'));
  els.check.onclick(); assert.match(els.result.textContent, /look right/); assert.equal(els.result.className, 'good');
  els.csv.value = 'kind,date\nevent,2026-11-02'; els.check.onclick(); assert.equal(els.result.className, 'bad'); assert.match(els.result.textContent, /problem/);
  els.save.onclick(); assert.equal(saved, 'timetable.csv');
});

test('RP2: a replace row is read, and one that ends before it starts is an error at that line and column', () => {
  assert.deepEqual(checkCsv(`${H}\nreplace,2026-11-15,,20:00,23:59,Tonight,,,,,,\n`).errors, []);
  const bad = checkCsv(`${H}\nreplace,2026-11-15,,23:59,20:00,Backwards,,,,,,\n`).errors;
  assert.ok(bad.some((e) => e.line === 2 && e.column === 'end'));
});

test('RP1: a reminder is an event type', () => {
  assert.deepEqual(checkCsv(`${H}\nevent,2026-11-15,,23:00,23:05,Melatonin 3mg,reminder,,,none,,\n`).errors, []);
});

test('IX6: modifier, anchor and keep rows are read, and a bad one names its line and column', () => {
  assert.deepEqual(checkCsv(`${H}\nmodifier,2026-11-23,2026-11-25,,,,lecture_free,,,,,\nanchor,2026-11-23,,02:30,10:30,,,,,,,winddown_min=45\nkeep,2026-11-23,,15:00,16:00,Call,,,,,,\n`).errors, []);
  const bad = checkCsv(`${H}\nmodifier,2026-11-23,2026-11-25,,,,holiday,,,,,\nkeep,2026-11-23,,16:00,15:00,,,,,,,\nanchor,2026-11-23,,02:30,10:30,,,,,,,broken\n`).errors;
  assert.ok(bad.some((e) => e.line === 2 && e.column === 'type'));
  assert.ok(bad.some((e) => e.line === 3 && e.column === 'end'));
  assert.ok(bad.some((e) => e.line === 4 && e.column === 'notes'));
});

import fs from 'node:fs';
test('IX9: every shared fixture reads the same way here as in the app', () => {
  const dir = new URL('../../core/src/test/resources/csv/', import.meta.url);
  const files = fs.readdirSync(dir);
  assert.ok(files.length > 10);
  for (const f of files.filter((n) => n.startsWith('good-'))) {
    const e = checkCsv(fs.readFileSync(new URL(f, dir), 'utf8')).errors;
    assert.deepEqual(e, [], `${f} should be accepted`);
  }
  for (const f of files.filter((n) => n.startsWith('bad-'))) {
    const m = /^bad-(\d+)-([a-z_]+)-/.exec(f); const col = m[2] === 'file' ? '' : m[2];
    const e = checkCsv(fs.readFileSync(new URL(f, dir), 'utf8')).errors;
    assert.ok(e.some((x) => String(x.line) === m[1] && (m[2] === 'file' || x.column === col)), `${f} expected ${m[1]}:${col} got ${JSON.stringify(e.map((x) => `${x.line}:${x.column}`))}`);
  }
});
