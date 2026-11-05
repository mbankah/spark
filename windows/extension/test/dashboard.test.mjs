import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

// The dashboard page is one HTML file; this runs its script with a fake page to check what it says (WG9, A6).
const html = readFileSync(new URL('../../SparkGuard.Service/dashboard.html', import.meta.url), 'utf8');
const script = /<script>([\s\S]*)<\/script>/.exec(html)[1];

function run(snapshot, nowMs) {
  const els = {};
  const el = (id) => (els[id] ??= { id, textContent: '', className: '', hidden: false, children: [], appendChild(c) { this.children.push(c); } });
  const document = { getElementById: el, createElement: () => ({ textContent: '' }) };
  const fn = new Function('document', 'fetch', 'setInterval', `${script.replace(/\ntick\(\); setInterval\(tick, 15000\);\s*$/, '')}\nreturn { render, view };`);
  const api = fn(document, async () => ({ ok: false }), () => {});
  api.render(snapshot, nowMs);
  return { els, api };
}

const snap = (issued, extra = {}) => ({ issuedAtUtc: issued, buffer: 'Buffer +24.0h', until: 'Wed 4 Nov 17:00', negative: false, working: ['Essay 6.3h'], now: 'Lecture', next: ['12:30 Lunch'], money: '£12.50', shopping: 12, week: ['Mon 2'], ...extra });
const T = Date.parse('2026-11-02T10:00:00Z');

test('A6: fresh data is shown', () => {
  const { els } = run(snap('2026-11-02T09:58:00Z'), T);
  assert.equal(els.buffer.textContent, 'Buffer +24.0h'); assert.equal(els.money.textContent, '£12.50'); assert.equal(els.shopping.textContent, '12 left to buy'); assert.equal(els.stale.hidden, true);
  assert.equal(els.working.children.length, 1);
});

test('WG9: data older than ten minutes says stale since and shows no number', () => {
  const { els } = run(snap('2026-11-02T09:40:00Z'), T);
  assert.match(els.stale.textContent, /^Stale since \d\d:\d\d/); assert.equal(els.stale.hidden, false);
  assert.equal(els.buffer.textContent, '...'); assert.equal(els.money.textContent, '...'); assert.equal(els.working.children.length, 0);
});

test('A5: before anything arrives the page says so and invents nothing', () => {
  const { els } = run(null, T);
  assert.match(els.stale.textContent, /Nothing has arrived/); assert.equal(els.buffer.textContent, '...');
});

test('B1: a negative Buffer is marked so it can be shown in the warning colour', () => {
  const { els } = run(snap('2026-11-02T09:59:00Z', { buffer: 'Buffer -2.8h', negative: true }), T);
  assert.match(els.buffer.className, /neg/);
});

test('A6: a snapshot with a time that cannot be read is not trusted', () => {
  const { els } = run(snap('garbage'), T); assert.match(els.stale.textContent, /could not be read/); assert.equal(els.buffer.textContent, '...');
});

test('ST6: the page has no form, input or link that could change or send anything', () => {
  const lower = html.toLowerCase();
  assert.ok(!lower.includes('<form') && !lower.includes('<input') && !lower.includes('<button') && !/method:\s*['"]post/.test(lower));
});
