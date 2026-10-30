// Spark browser extension, the second layer on the PC (A2, A3, K4, K8). The Windows service writes the hosts file; this blocks the same sites inside the browser,
// which also covers a browser using encrypted DNS (DNS over HTTPS), where the hosts file is skipped.
// This file is pure: it decides what the rules should be. background.js applies them. Tested with: node --test windows/extension/test

const DOMAIN = /^[a-z0-9]([a-z0-9.-]*[a-z0-9])?\.[a-z]{2,}$/;

/** One redirect rule per listed domain. requestDomains also matches every subdomain (www, m, music), as the hosts lines do. */
export function buildRules(domains) {
  return [...new Set(domains)].filter((d) => typeof d === 'string' && DOMAIN.test(d)).sort().map((d, i) => ({
    id: i + 1,
    priority: 1,
    action: { type: 'redirect', redirect: { extensionPath: '/blocked.html' } },
    condition: { requestDomains: [d], resourceTypes: ['main_frame', 'sub_frame'] },
  }));
}

/**
 * What to do after asking the service. state is {blocked, domains, until} or null when the service could not be reached.
 * X7: if the service cannot be reached, the rules already in place stay until the end of the window they were made for, and then go.
 * It never blocks on a guess, and never keeps blocking past the end the service gave.
 */
export function plan(state, kept, nowMs) {
  if (state && state.blocked === true && Array.isArray(state.domains)) {
    const until = Date.parse(state.until);
    return { rules: buildRules(state.domains), until: Number.isFinite(until) ? until : nowMs + 60_000, reason: 'blocked' };
  }
  if (state && state.blocked === false) return { rules: [], until: 0, reason: 'open' };
  if (kept && kept.until > nowMs && kept.rules.length) return { rules: kept.rules, until: kept.until, reason: 'service unreachable, keeping the rules until the window ends' };
  return { rules: [], until: 0, reason: 'service unreachable, nothing kept' };
}
