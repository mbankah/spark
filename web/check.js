// A quick check of a timetable CSV in any browser, before it goes to Spark (IM1 to IM3, IM9). Spark always does the full check itself, against your calendar;
// this one only catches the mistakes an AI often makes: a wrong header, a missing cell, a bad date or time, a type that does not exist.
// The same rules as docs/TIMETABLE_CSV.md. Tested with: node --test web/test

export const COLUMNS = ['kind', 'date', 'end_date', 'start', 'end', 'title', 'type', 'venue', 'heaviness', 'study', 'action', 'notes'];
export const EVENT_TYPES = ['lecture', 'tutorial', 'mytutor', 'training', 'match', 'gym', 'church', 'errand', 'travel', 'reminder'];
export const PERIOD_TYPES = ['assignment_week', 'catch_up_week'];
export const FILE_PRESETS = ['catch_up', 'lecture_free', 'late_start', 'run_over', 'add_event', 'cancel', 'recovery', 'guests', 'away', 'night'];
export const MAX_BYTES = 1_000_000;
export const MAX_ROWS = 2000;

/** Splits text into rows of cells. Commas, semicolons or tabs between cells; double quotes around a cell that contains one. */
export function parseCsv(text) {
  const firstLine = text.split(/\r?\n/, 1)[0] || '';
  const sep = [',', ';', '\t'].map((s) => [s, firstLine.split(s).length]).sort((a, b) => b[1] - a[1])[0][0];
  const rows = []; let row = []; let cell = ''; let quoted = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (quoted) {
      if (c === '"' && text[i + 1] === '"') { cell += '"'; i++; } else if (c === '"') quoted = false; else cell += c;
    } else if (c === '"') quoted = true;
    else if (c === sep) { row.push(cell); cell = ''; }
    else if (c === '\n' || c === '\r') { if (c === '\r' && text[i + 1] === '\n') i++; row.push(cell); rows.push(row); row = []; cell = ''; }
    else cell += c;
  }
  if (cell !== '' || row.length) { row.push(cell); rows.push(row); }
  return rows.filter((r) => r.some((c) => c.trim() !== ''));
}

function date(s) {
  const t = s.trim(); let y; let m; let d;
  let r = /^(\d{4})-(\d{2})-(\d{2})$/.exec(t);
  if (r) { y = +r[1]; m = +r[2]; d = +r[3]; } else if ((r = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(t))) { d = +r[1]; m = +r[2]; y = +r[3]; } else return null;
  const dt = new Date(Date.UTC(y, m - 1, d));
  return dt.getUTCFullYear() === y && dt.getUTCMonth() === m - 1 && dt.getUTCDate() === d ? dt : null;
}

function minutes(s) {
  const r = /^(\d{1,2}):(\d{2})$/.exec(s.trim());
  if (!r || +r[1] > 23 || +r[2] > 59) return null;
  return +r[1] * 60 + +r[2];
}

/** Returns {errors, rows}. Each error is {line, column, text}. */
export function checkCsv(text) {
  const errors = [];
  const err = (line, column, t) => errors.push({ line, column, text: t });
  if (new TextEncoder().encode(text).length > MAX_BYTES) return { errors: [{ line: 1, column: '', text: 'The file is over 1 MB.' }], rows: 0 };
  const rows = parseCsv(text.replace(/^﻿/, ''));
  if (rows.length === 0) return { errors: [{ line: 1, column: '', text: 'The file is empty.' }], rows: 0 };
  const header = rows[0].map((h) => h.trim().toLowerCase());
  for (const c of COLUMNS) if (!header.includes(c)) err(1, c, `The header has no "${c}" column. The first line must be: ${COLUMNS.join(',')}`);
  if (errors.length) return { errors, rows: rows.length - 1 };
  if (rows.length - 1 > MAX_ROWS) return { errors: [{ line: 1, column: '', text: `The file has more than ${MAX_ROWS} rows.` }], rows: rows.length - 1 };
  const at = (r, name) => (r[header.indexOf(name)] ?? '').trim();
  rows.slice(1).forEach((r, i) => {
    const line = i + 2;
    if (r.length !== header.length) { err(line, '', `This line has ${r.length} cells and the header has ${header.length}. Every cell must be present, even if empty.`); return; }
    const kind = at(r, 'kind').toLowerCase();
    if (!['event', 'deadline', 'period', 'replace', 'modifier', 'anchor', 'keep'].includes(kind)) { err(line, 'kind', 'kind must be event, deadline, period, replace, modifier, anchor or keep.'); return; }
    const d = date(at(r, 'date'));
    if (!d) err(line, 'date', 'The date must be like 2026-11-02 or 02/11/2026, and a real day.');
    const action = at(r, 'action').toLowerCase();
    if (!['', 'add', 'cancel'].includes(action)) err(line, 'action', 'action must be empty, add or cancel.');
    const study = at(r, 'study').toLowerCase();
    if (!['', 'standard', 'light', 'none'].includes(study)) err(line, 'study', 'study must be empty, standard, light or none.');
    if (['event', 'deadline'].includes(kind) && at(r, 'title') === '') err(line, 'title', 'A title is needed.');
    if (at(r, 'title').length > 120) err(line, 'title', 'The title is over 120 characters.');
    if (kind === 'event') {
      const s = minutes(at(r, 'start')); const e = minutes(at(r, 'end'));
      if (s === null) err(line, 'start', 'The start must be a 24-hour time like 09:00.');
      if (e === null) err(line, 'end', 'The end must be a 24-hour time like 10:00.');
      if (s !== null && e !== null && e <= s) err(line, 'end', 'The end must be after the start, on the same day.');
      if (!EVENT_TYPES.includes(at(r, 'type').toLowerCase())) err(line, 'type', `type must be one of: ${EVENT_TYPES.join(', ')}.`);
    } else if (kind === 'keep') {
      const s = minutes(at(r, 'start')); const e = minutes(at(r, 'end'));
      if (s === null) err(line, 'start', 'The start must be a 24-hour time like 20:00.');
      if (e === null) err(line, 'end', 'The end must be a 24-hour time like 23:59.');
      if (s !== null && e !== null && e <= s) err(line, 'end', 'The end must be after the start, on the same day.');
    } else if (kind === 'anchor') {
      if (minutes(at(r, 'start')) === null) err(line, 'start', 'Lights out must be a 24-hour time like 02:30.');
      if (minutes(at(r, 'end')) === null) err(line, 'end', 'Wake must be a 24-hour time like 10:30.');
      for (const part of at(r, 'notes').split(';').filter((p) => p.trim() !== '')) if (!/^[^=]+=.+$/.test(part.trim())) err(line, 'notes', `"${part.trim()}" is not key=value. Separate settings with ;`);
    } else if (kind === 'modifier') {
      const e = date(at(r, 'end_date'));
      if (!e) err(line, 'end_date', 'A modifier needs a last day.');
      else if (d && e < d) err(line, 'end_date', 'The last day is before the first day.');
      if (!FILE_PRESETS.includes(at(r, 'type').toLowerCase())) err(line, 'type', `type must be one of: ${FILE_PRESETS.join(', ')}.`);
      for (const k of ['start', 'end']) if (at(r, k) !== '' && minutes(at(r, k)) === null) err(line, k, 'A time must be 24-hour like 09:00, or empty.');
      for (const part of at(r, 'notes').split(';').filter((p) => p.trim() !== '')) if (!/^[^=]+=.+$/.test(part.trim())) err(line, 'notes', `"${part.trim()}" is not key=value. Separate settings with ;`);
    } else if (kind === 'replace') {
      const s = minutes(at(r, 'start')); const e = minutes(at(r, 'end'));
      if (s === null) err(line, 'start', 'The start must be a 24-hour time like 20:00.');
      if (e === null) err(line, 'end', 'The end must be a 24-hour time like 23:59.');
      if (s !== null && e !== null && e <= s) err(line, 'end', 'The end must be after the start, on the same day.');
    } else if (kind === 'deadline') {
      if (minutes(at(r, 'start')) === null) err(line, 'start', 'The due time must be a 24-hour time like 17:00.');
      const h = at(r, 'heaviness');
      if (h !== '' && !(/^\d+$/.test(h) && +h >= 1 && +h <= 10)) err(line, 'heaviness', 'heaviness must be a whole number from 1 to 10, or empty.');
    } else {
      const e = date(at(r, 'end_date'));
      if (!e) err(line, 'end_date', 'A period needs a last day.');
      else if (d) {
        const days = Math.round((e - d) / 86_400_000) + 1;
        if (days < 1) err(line, 'end_date', 'The last day is before the first day.');
        else if (days > 14) err(line, 'end_date', 'A period is 1 to 14 days.');
      }
      if (!PERIOD_TYPES.includes(at(r, 'type').toLowerCase())) err(line, 'type', `type must be ${PERIOD_TYPES.join(' or ')}. A day off or a library period is planned in the app.`);
    }
  });
  return { errors, rows: rows.length - 1 };
}
