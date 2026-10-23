/* Menu structure: working entries (Arbeitsbereich) first, then Administration in three groups. Pages add their own entries at the end of the menu, so the order is put right here: whatever
   exists is arranged, whatever a user may not see stays hidden by its page. Idempotent; runs again whenever an entry is added or shown. */
globalThis.LcNav = (() => {
 const WORK = ['sec:cockpit', 'appNavMyWork', 'appNavGroupInbox', 'sec:lcs', 'appNavGuarantees', 'appNavCreditLines', 'appNavCalendar', 'appNavLiquidity', 'appNavInbox', 'appNavImport', 'sec:imports', 'appNavTraining'];
 const ADMIN = [
  { id: 'master', fallback: 'Stammdaten & Regeln', items: ['appNavCompany', 'appNavTemplates', 'appNavRulePacks', 'appNavCorrespondent', 'appNavApprovalPolicy', 'appNavSla'] },
  { id: 'access', fallback: 'Zugriff & Schnittstellen', items: ['appNavUsers', 'appNavApiKeys', 'appNavWebhooks', 'appNavAudit'] },
  { id: 'ops', fallback: 'Betrieb & Anbindung', items: ['appNavEbics', 'appNavMonitoring', 'appNavTenants', 'appNavPlatform'] }];
 const keyOf = el => el.id || (el.dataset && el.dataset.appSection ? 'sec:' + el.dataset.appSection : null);
 /**
  * The wanted order. entries: [{key, hidden}] of the buttons in the menu. Returns the list of steps: {heading:'work'|'admin'|group id, visible} and {key}. Unknown entries stay with the
  * working entries; a group heading is visible only when one of its entries is visible; the Administration heading when any administration group is.
  */
 function plan(entries) {
  const present = new Map(entries.map(e => [e.key, e])), used = new Set(), steps = [];
  const take = keys => keys.filter(k => present.has(k)).map(k => { used.add(k); return present.get(k); });
  const work = take(WORK);
  const groups = ADMIN.map(g => ({ g, items: take(g.items) }));
  const unknown = entries.filter(e => !used.has(e.key));
  steps.push({ heading: 'work', visible: true });
  [...work, ...unknown].forEach(e => steps.push({ key: e.key }));
  const adminVisible = groups.some(x => x.items.some(e => !e.hidden));
  steps.push({ heading: 'admin', visible: adminVisible });
  groups.forEach(({ g, items }) => { steps.push({ heading: g.id, visible: items.some(e => !e.hidden) }); items.forEach(e => steps.push({ key: e.key })); });
  return steps;
 }
 function arrange() {
  const nav = document.querySelector('#appNav nav'); if (!nav) return;
  const buttons = [...nav.children].filter(el => el.tagName === 'BUTTON' && keyOf(el));
  const byKey = new Map(buttons.map(b => [keyOf(b), b]));
  const steps = plan(buttons.map(b => ({ key: keyOf(b), hidden: b.classList.contains('hidden') || b.hidden === true })));
  const workHeading = nav.querySelector('small:not(.admin-nav-label):not(.admin-nav-sub)'), adminHeading = nav.querySelector('.admin-nav-label');
  const subs = {};
  ADMIN.forEach(g => { let s = nav.querySelector('small.admin-nav-sub[data-nav-group="' + g.id + '"]'); if (!s) { s = document.createElement('small'); s.className = 'admin-nav-sub'; s.dataset.navGroup = g.id; s.dataset.i18n = 'nav.group.' + g.id; s.textContent = typeof tx === 'function' ? tx('nav.group.' + g.id, g.fallback) : g.fallback; } subs[g.id] = s; });
  const element = step => step.heading === 'work' ? workHeading : step.heading === 'admin' ? adminHeading : step.heading ? subs[step.heading] : byKey.get(step.key);
  const wanted = steps.map(element).filter(Boolean);
  const current = [...nav.children];
  if (wanted.length !== current.length || wanted.some((el, i) => el !== current[i])) wanted.forEach(el => nav.appendChild(el));
  steps.forEach(step => { if (step.heading && step.heading !== 'work') { const el = element(step); if (el) el.classList.toggle('hidden', !step.visible); } });
 }
 let timer = null; const schedule = () => { clearTimeout(timer); timer = setTimeout(arrange, 0); };
 function start() {
  const nav = document.querySelector('#appNav nav'); if (!nav || start.done) return; start.done = true;
  new MutationObserver(schedule).observe(nav, { childList: true, subtree: true, attributes: true, attributeFilter: ['class'] });
  arrange();
 }
 if (typeof document !== 'undefined' && document.readyState !== 'loading') start(); else if (typeof document !== 'undefined') document.addEventListener('DOMContentLoaded', start);
 return { plan, arrange, WORK, ADMIN };
})();
