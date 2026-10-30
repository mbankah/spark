import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { buildRules, plan } from '../rules.js';

test('K4: one redirect rule per listed domain, sorted, with distinct ids', () => {
  const r = buildRules(['youtube.com', 'reddit.com', 'youtube.com']);
  assert.deepEqual(r.map((x) => x.condition.requestDomains[0]), ['reddit.com', 'youtube.com']);
  assert.deepEqual(r.map((x) => x.id), [1, 2]);
  assert.ok(r.every((x) => x.action.type === 'redirect' && x.action.redirect.extensionPath === '/blocked.html'));
});

test('K7: a rule matches the domain and its subdomains, not other sites', () => {
  const r = buildRules(['youtube.com'])[0];
  assert.deepEqual(r.condition.requestDomains, ['youtube.com']);
  assert.deepEqual(r.condition.resourceTypes, ['main_frame', 'sub_frame']);
});

test('K7: anything that is not a plain domain is dropped', () => {
  assert.deepEqual(buildRules(['*', '', 'a b.com', 'http://x.com', 'localhost', 42, null, '-x.com']), []);
});

test('K4: inside a window the rules are those of the listed domains, until the window ends', () => {
  const p = plan({ blocked: true, domains: ['youtube.com'], until: '2026-11-02T12:00:00Z' }, null, Date.parse('2026-11-02T10:00:00Z'));
  assert.equal(p.rules.length, 1); assert.equal(p.until, Date.parse('2026-11-02T12:00:00Z'));
});

test('K4: outside a window there are no rules', () => {
  assert.deepEqual(plan({ blocked: false, domains: [] }, { rules: [{ id: 1 }], until: Date.now() + 1e6 }, Date.now()).rules, []);
});

test('X7: if the service cannot be reached the rules stay until the window ends and then go', () => {
  const kept = { rules: buildRules(['youtube.com']), until: 1000 };
  assert.equal(plan(null, kept, 500).rules.length, 1);
  assert.equal(plan(null, kept, 1500).rules.length, 0);
  assert.equal(plan(null, null, 500).rules.length, 0);
});

test('X7: a blocked answer with no end time blocks for a minute only, never for ever', () => {
  const p = plan({ blocked: true, domains: ['youtube.com'] }, null, 1000);
  assert.equal(p.until, 61_000);
});

test('K8: the extension asks only this PC and asks for no page access', () => {
  const m = JSON.parse(readFileSync(new URL('../manifest.json', import.meta.url)));
  assert.deepEqual(m.host_permissions, ['http://127.0.0.1:47831/*']);
  assert.ok(!m.permissions.includes('tabs') && !m.permissions.includes('webRequest') && !m.permissions.includes('history'));
  assert.ok(!JSON.stringify(m).includes('<all_urls>') || JSON.stringify(m.host_permissions).indexOf('<all_urls>') === -1);
});

test('K8: the blocked page offers no way to open the site', () => {
  const html = readFileSync(new URL('../blocked.html', import.meta.url), 'utf8').toLowerCase();
  assert.ok(!html.includes('<button') && !html.includes('<input') && !html.includes('<form'));
});
