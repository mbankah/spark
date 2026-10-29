// Generates docs/TRACEABILITY.md from DECISIONS_LEDGER.md.
// Run: node tools/gen-traceability.mjs   (from the efa folder)
// Layer and test mapping live here so the table can be regenerated when tests are added.
import fs from 'node:fs';

const ledger = fs.readFileSync('DECISIONS_LEDGER.md', 'utf8').split('\n');

// ---- parse ledger rows -------------------------------------------------
const rows = new Map(); // key -> {id,key,text,status,sections:Set}
let section = 0;
for (const line of ledger) {
  const h = line.match(/^## (\d+)\./);
  if (h) { section = +h[1]; continue; }
  const m = line.match(/^\| ([A-Z]{1,2}\d+) \| (.*?) \|(?: (.*?) \|)?(?: (.*?) \|)?\s*$/);
  if (!m) continue;
  const id = m[1];
  // Section 20 "F1..F5" are review gaps and clash with free-slot F1..F7 (section 10).
  const key = section === 20 && /^F\d$/.test(id) ? `R20-${id}` : id;
  // Section 16 E-table has 4 columns: id | where | what you see | mitigation
  const isE = /^E\d+$/.test(id);
  let text = m[2], status = m[3] ?? '';
  if (isE) { text = `${m[2]} (mitigation: ${m[4] ?? m[3]})`; status = 'Risk'; }
  if (section === 19) status = 'Answer (§19)';
  const prev = rows.get(key);
  if (prev) {
    // later sections override earlier text; keep the earlier status when the later is an answer
    prev.sections.add(section);
    if (section === 19) { prev.text = `${text} [was: ${prev.text.slice(0, 60)}]`; prev.status = prev.status + ' → §19 answer'; }
    else if (section > Math.max(...[...prev.sections].filter(s => s !== section))) { prev.text = text; prev.status = status; }
    continue;
  }
  rows.set(key, { id, key, text, status, sections: new Set([section]) });
}
// Section 20 numbered findings 1..7 (no ID in ledger)
const r20 = [
  'N2 contradicted work blocks / getting-ready / transitions (fixed in N2)',
  'F3 could convert every free slot against the protected 6 h (fixed in F3)',
  'X3 vs H4: day off after ≤3 h sleep is automatic (fixed in X3)',
  'Sections 15/16 stale defaults replaced by §19',
  'Build order missed launcher, transitions, voice, days off, shopping list (added to layer 1 and 8)',
  'U12: blocking from day one vs build order (clarified)',
  'G2: backstop vs onset+8 h (backstop rings first)',
];
r20.forEach((t, i) => rows.set(`R20-${i + 1}`, { id: `R20-${i + 1}`, key: `R20-${i + 1}`, text: t, status: 'Review finding', sections: new Set([20]) }));

// ---- layer mapping -----------------------------------------------------
const L = new Map();
const put = (layer, ids) => ids.forEach(i => L.set(i, layer));
const range = (p, a, b) => Array.from({ length: b - a + 1 }, (_, i) => `${p}${a + i}`);
put(0, [...range('Q', 1, 6), ...range('E', 1, 15), ...range('R20-', 1, 7).map(x => x.replace('R20--', 'R20-'))]);
put(1, [...range('P', 1, 11), ...range('A', 1, 9), ...range('D', 1, 7), ...range('B', 1, 4), 'B6', 'B7', 'B8',
  ...range('W', 1, 7), ...range('T', 1, 3), ...range('U', 1, 12), ...range('LA', 1, 14), ...range('ST', 1, 7),
  ...range('SH', 1, 5), ...range('AP', 1, 8), ...range('WG', 1, 10), ...range('L', 1, 8), 'Y1', 'Y2', 'Y3', 'Y6',
  'K1', 'K2', 'K3', 'K10', 'G1', 'G4', 'G5', 'X1', 'X4', 'X5', 'X6', 'X7', 'O4', 'O9', 'O5', 'O6', 'O7', 'O8',
  'O1', 'O14', 'R20-F4', 'R20-F2']);
put(1, [...range('IM', 1, 12), ...range('PW', 1, 3), ...range('RS', 1, 9)]);   // section 25, built early at your request
put(2, range('S', 1, 7));
put(3, [...range('M', 1, 10), ...range('Z', 1, 4), 'G2', 'Y7', 'X2', 'R20-F1', 'O2']);
put(4, [...range('N', 1, 7), 'R20-F3']);
put(5, range('K', 4, 9));
put(6, [...range('R', 1, 9), ...range('F', 1, 7), 'B9', 'Y4', 'Y5', 'Y8', 'G3', 'O3', 'R20-F5']);
put(7, ['B5', 'R10']);
put(8, [...range('C', 1, 5), ...range('H', 1, 6), ...range('V', 1, 4), 'G6', 'G7', 'G8', 'G9', 'G10', 'X3', 'Y9',
  'O10', 'O11', 'O12', 'O13']);

// Device test needed
const dev = new Set([
  ...range('S', 1, 7), ...range('M', 1, 3), 'M5', 'M6', 'M8', 'M10', 'Z1', 'Z2', 'X2', 'X5', 'X4',
  'U1', 'U2', 'U3', 'U4', 'U8', 'U11', 'U12', ...range('LA', 1, 14), 'K10', 'K1', 'K3', 'L2', 'L3', 'A8', 'A9',
  'ST4', 'ST7', 'WG5', 'WG6', 'WG10', 'AP3', 'AP4', 'AP8', 'F2', 'G2', 'E3', 'E4', 'E6', 'E7', 'E11', 'R20-F2',
  'K5', 'K6', 'K8', 'Y2', 'N2', 'N7', 'V2', 'T3', 'IM10',
]);

// Planned test home by layer + area. Test names are filled in as tests are written.
const testFile = (r) => {
  const k = r.key, lay = L.get(k);
  if (lay === 0) return 'docs/QUESTIONS.md · process (no code test)';
  if (/^(AP|WG)/.test(k)) return /^AP8|^AP[67]/.test(k) ? 'core/src/test/kotlin/efa/design/ContrastTest.kt' : 'app/src/androidTest/kotlin/efa/ui/' + k.replace(/\d+/, '') + 'Test.kt';
  if (/^LA|^U\d/.test(k)) return 'app/src/androidTest/kotlin/efa/launcher/LauncherTest.kt';
  if (/^ST/.test(k)) return 'core/src/test/kotlin/efa/storage/StoragePolicyTest.kt';
  if (/^SH/.test(k)) return 'core/src/test/kotlin/efa/shopping/ShoppingTest.kt';
  if (/^(B|D|T|R|F|Y|Z|N|S|H|C|G|X|L|M|K|P|A|V|W|O)\d/.test(k) || k.startsWith('R20')) return `core/src/test/kotlin/efa/layer${lay}/${k.replace(/\W/g, '')}Test.kt`;
  return 'TBD';
};

const missing = [...rows.values()].filter(r => !L.has(r.key));
if (missing.length) { console.error('UNMAPPED:', missing.map(r => r.key).join(', ')); process.exit(1); }

// ---- link written tests: test names start with the ledger IDs they prove ----
const found = new Map();
const walk = d => fs.readdirSync(d, { withFileTypes: true }).flatMap(e => e.isDirectory() ? walk(d + '/' + e.name) : [d + '/' + e.name]);
for (const f of fs.existsSync('core/src/test') ? walk('core/src/test').filter(f => f.endsWith('.kt')) : []) {
  for (const m of fs.readFileSync(f, 'utf8').matchAll(/fun `((?:[A-Z]{1,2}\d+ )+)([^`]*)`/g)) {
    for (const id of m[1].trim().split(' ')) {
      if (!found.has(id)) found.set(id, []);
      found.get(id).push({ file: f.replace('core/src/test/kotlin/', 'core/src/test/kotlin/'), name: (m[1] + m[2]).trim() });
    }
  }
}
// Device tests (Kotlin, underscores): fun D2_B3_quick_add_...(): IDs are the leading tokens that look like ledger IDs.
for (const f of fs.existsSync('app/src/androidTest') ? walk('app/src/androidTest').filter(f => f.endsWith('.kt')) : []) {
  for (const m of fs.readFileSync(f, 'utf8').matchAll(/fun ((?:[A-Z]{1,2}\d+_)+)([A-Za-z0-9_]*)\(/g)) {
    for (const id of m[1].split('_').filter(Boolean)) {
      if (!found.has(id)) found.set(id, []);
      found.get(id).push({ file: f, name: (m[1] + m[2]).replace(/_/g, ' ') + ' (device)' });
    }
  }
}
const withTests = [...rows.keys()].filter(k => found.has(k)).length;
const esc = s => s.replace(/\|/g, '/').replace(/\s+/g, ' ').trim();
const order = [...rows.values()].sort((a, b) => [...a.sections][0] - [...b.sections][0]);
let out = `# Traceability

Generated by \`tools/gen-traceability.mjs\` from DECISIONS_LEDGER.md. One row per ledger ID (${rows.size} rows, ${withTests} with a written test).
A row with test name "(not written)" is a **failing row** (Q4). Layer 0 = process, not code.
Section 20 review gaps F1 to F5 clash with free-slot IDs F1 to F7, so they are listed as R20-F1 to R20-F5.
Sections 15/16 O-questions and G-gaps appear once, with the section 19 answer applied.

| ID | Behaviour | Status | Test file (planned) | Test name | Layer | Device test needed |
|---|---|---|---|---|---|---|
`;
for (const r of order) {
  out += `| ${r.key} | ${esc(r.text).slice(0, 190)} | ${esc(r.status)} | ${found.has(r.key) ? [...new Set(found.get(r.key).map(t => t.file))].join('<br>') : testFile(r)} | ${found.has(r.key) ? found.get(r.key).map(t => t.name).join('<br>') : '(not written)'} | ${L.get(r.key)} | ${dev.has(r.key) ? 'yes' : 'no'} |\n`;
}
fs.writeFileSync('docs/TRACEABILITY.md', out);

// ---- Default lines in sections 22 to 24 --------------------------------
const defs = [];
let sec = 0;
for (const line of ledger) {
  const h = line.match(/^## (\d+)\./); if (h) sec = +h[1];
  const m = line.match(/^\| ([A-Z]+\d+) \| (.*) \| (Default.*|Locked.*) \|$/);
  if (m && sec >= 22) defs.push({ id: m[1], status: m[3], text: m[2] });
}
fs.writeFileSync('docs/_defaults.json', JSON.stringify(defs, null, 1));
const byL = {}; for (const r of rows.values()) byL[L.get(r.key)] = (byL[L.get(r.key)] || 0) + 1;
console.log('rows', rows.size, 'with tests', withTests, 'by layer', byL, 'defaults(22-24)', defs.filter(d => d.status.startsWith('Default')).map(d => d.id).join(' '));
