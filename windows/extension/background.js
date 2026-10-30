import { plan } from './rules.js';

// Asks the Spark Guard service on this PC (the loopback address only) what is blocked now, and applies it. Runs every half minute and when the browser starts.
const STATE_URL = 'http://127.0.0.1:47831/state';

async function sync() {
  let state = null;
  try {
    const r = await fetch(STATE_URL, { cache: 'no-store' });
    if (r.ok) state = await r.json();
  } catch (e) { /* the service is not running: plan() decides what to keep */ }
  const { kept } = await chrome.storage.local.get('kept');
  const next = plan(state, kept, Date.now());
  const old = await chrome.declarativeNetRequest.getDynamicRules();
  await chrome.declarativeNetRequest.updateDynamicRules({ removeRuleIds: old.map((r) => r.id), addRules: next.rules });
  await chrome.storage.local.set({ kept: { rules: next.rules, until: next.until } });
}

chrome.runtime.onStartup.addListener(sync);
chrome.runtime.onInstalled.addListener(() => { chrome.alarms.create('sync', { periodInMinutes: 0.5 }); sync(); });
chrome.alarms.onAlarm.addListener((a) => { if (a.name === 'sync') sync(); });
