// Writes web/csv-prompt.html: ONE file that works in any browser on any device (phone, PC, Mac, Linux, tablet). It has the prompt to copy for an AI, a place to paste
// what the AI made, a quick check, and a button that saves the .csv to import into Spark. The prompt is read from docs/TIMETABLE_CSV.md, which is generated from the
// app's own code, so the page, the document and the app cannot drift. Run from the efa folder: node tools/gen-prompt-page.mjs
import fs from 'node:fs';

const doc = fs.readFileSync('docs/TIMETABLE_CSV.md', 'utf8');
const prompt = /## Give this to an AI[\s\S]*?```\n([\s\S]*?)```/.exec(doc)[1].trim();
const example = /## Example\s+```\n([\s\S]*?)```/.exec(doc)[1].trim();
const check = fs.readFileSync('web/check.js', 'utf8').replace(/^export /gm, '');
const esc = (s) => s.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;');

const html = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Spark CSV Prompt</title>
<style>
  :root { --bg: #f4f6f8; --text: #0b0f14; --muted: #4a5560; --card: #ffffff; --line: #0b0f14; --warn: #b00020; --ok: #0a6b2d; }
  @media (prefers-color-scheme: dark) { :root { --bg: #0b0f14; --text: #f4f6f8; --muted: #a8b3bd; --card: #151b22; --line: #f4f6f8; --warn: #ff6b81; --ok: #5fd68a; } }
  * { box-sizing: border-box; }
  body { margin: 0; background: var(--bg); color: var(--text); font-family: ui-monospace, Consolas, Menlo, monospace; line-height: 1.5; }
  main { max-width: 46rem; margin: 0 auto; padding: 16px; }
  h1 { font-size: 1.8rem; margin: 8px 0; } h2 { font-size: 1.05rem; margin: 0 0 8px; letter-spacing: .04em; text-transform: uppercase; }
  section { background: var(--card); border: 3px solid var(--line); padding: 14px; margin: 14px 0; }
  p, li { margin: 6px 0; } .muted { color: var(--muted); font-size: .9rem; }
  textarea { width: 100%; min-height: 12rem; background: var(--bg); color: var(--text); border: 2px solid var(--line); padding: 8px; font: inherit; font-size: .85rem; }
  button { font: inherit; font-weight: 700; color: var(--bg); background: var(--text); border: 0; padding: 12px 16px; margin: 6px 8px 0 0; min-height: 48px; cursor: pointer; }
  button.plain { color: var(--text); background: transparent; border: 2px solid var(--line); }
  #result { margin-top: 10px; font-weight: 700; } .bad { color: var(--warn); } .good { color: var(--ok); }
  ul.errors { padding-left: 1.2rem; }
</style>
</head>
<body>
<main>
  <h1>Spark CSV prompt</h1>
  <p class="muted">Get an AI to turn your timetable or deadlines into a file Spark can import. Works in any browser, on any phone or computer. Nothing here is sent anywhere: the check runs on this page.</p>

  <section>
    <h2>1. Copy this prompt</h2>
    <textarea id="prompt" readonly>${esc(prompt)}</textarea>
    <button id="copy">Copy the prompt</button><span id="copied" class="muted"></span>
    <p class="muted">Paste it into any AI app, then paste your timetable or dates underneath. Ask it to reply with the CSV only.</p>
  </section>

  <section>
    <h2>2. Paste what the AI gave you</h2>
    <textarea id="csv" placeholder="kind,date,end_date,start,end,title,type,venue,heaviness,study,action,notes"></textarea>
    <button id="check">Check it</button><button id="sample" class="plain">Show an example</button>
    <div id="result" role="status"></div>
  </section>

  <section>
    <h2>3. Save it and give it to Spark</h2>
    <button id="save">Save timetable.csv</button>
    <ul>
      <li><b>Android:</b> open the saved file with Spark (Open with), or share it to Spark, or Setup, Import a timetable file.</li>
      <li><b>Windows, Mac, Linux:</b> send the file to your phone (email, a cloud drive, a USB cable, any messaging app) and open it with Spark there. The PC does not import files itself: your phone is the source of truth.</li>
    </ul>
    <p class="muted">Spark checks every line again against your calendar and shows a preview. Nothing changes until you press Apply. A file that cancels something needs the typed wall.</p>
  </section>
</main>
<script>
${check}

const $ = (id) => document.getElementById(id);
const EXAMPLE = ${JSON.stringify(example)};
$('copy').onclick = async () => {
  try { await navigator.clipboard.writeText($('prompt').value); $('copied').textContent = ' Copied.'; }
  catch (e) { $('prompt').select(); document.execCommand('copy'); $('copied').textContent = ' Copied (or select it and copy by hand).'; }
};
$('sample').onclick = () => { $('csv').value = EXAMPLE; $('result').textContent = ''; };
$('check').onclick = () => {
  const r = checkCsv($('csv').value);
  const out = $('result'); out.textContent = ''; out.className = '';
  if (r.errors.length === 0) { out.className = 'good'; out.textContent = r.rows + ' row' + (r.rows === 1 ? '' : 's') + ' look right. Spark will check them again.'; return; }
  out.className = 'bad';
  out.textContent = r.errors.length + ' problem' + (r.errors.length === 1 ? '' : 's') + '. Ask the AI to fix these lines:';
  const ul = document.createElement('ul'); ul.className = 'errors';
  r.errors.slice(0, 30).forEach((e) => { const li = document.createElement('li'); li.textContent = 'Line ' + e.line + (e.column ? ', ' + e.column : '') + ': ' + e.text; ul.appendChild(li); });
  out.appendChild(ul);
};
$('save').onclick = () => {
  const r = checkCsv($('csv').value);
  if (r.errors.length && !confirm('There are problems in this file. Spark will refuse a file with errors. Save it anyway?')) return;
  const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([$('csv').value], { type: 'text/csv;charset=utf-8' })); a.download = 'timetable.csv';
  document.body.appendChild(a); a.click(); a.remove();
};
</script>
</body>
</html>
`;
fs.writeFileSync('web/csv-prompt.html', html);
console.log('wrote web/csv-prompt.html', html.length, 'bytes');
