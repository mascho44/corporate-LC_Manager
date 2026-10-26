/* "First steps" checklist on the start page for tenant admins: company, templates, users, first file; banks also SLA rule and correspondent bank.
   Uses the existing readiness endpoint plus the loaded file list; disappears once every step is done. */
(() => {
 const tr = (key, de, values = {}) => { const i = globalThis.LcI18n, s = i?.t ? i.t(key, values) : null; return s && s !== key ? s : String(de).replace(/\{(\w+)\}/g, (m, k) => values[k] ?? m); };

 /** The steps with their state; `status` is the readiness answer, `files` the number of LC files, `bank` adds the steps for banks. */
 function steps(status, files, bank = false) {
  return [
   { key: 'company', done: Number(status.companies) > 0, target: '#appNavCompany', label: tr('fs.company', 'Firmendaten anlegen') },
   { key: 'templates', done: Number(status.templates) > 0, target: '#appNavTemplates', label: tr('fs.templates', 'Dokumentvorlagen hinterlegen') },
   { key: 'users', done: Number(status.activeMembers) > 1, target: '#appNavUsers', label: tr('fs.users', 'Weitere Benutzer einladen') },
   { key: 'sla', bank: true, done: Number(status.slaRules) > 0, target: '#appNavSla', label: tr('fs.sla', 'SLA-Regel anlegen') },
   { key: 'correspondent', bank: true, done: Number(status.correspondentBanks) > 0, target: '#appNavCorrespondent', label: tr('fs.correspondent', 'Korrespondenzbank erfassen') },
   { key: 'file', done: files > 0, target: '[data-app-section="lcs"]', label: tr('fs.file', 'Erste LC-Akte anlegen') }
  ].filter(step => !step.bank || bank);
 }
 globalThis.LcFirstSteps = { steps };
 if (typeof document === 'undefined' || !document.body) return;

 async function render() {
  document.querySelector('#firstSteps')?.remove();
  const host = document.querySelector('#cockpitSection');
  if (!host || typeof can !== 'function' || !currentUser || !can('USER_MANAGE') || !can('SETTINGS_MANAGE')) return;
  let status;
  try { const response = await fetch('/api/tenants/current/readiness', { credentials: 'same-origin' }); if (!response.ok) return; status = await response.json(); } catch { return; }
  const bank = globalThis.LcWorkspaceModules?.bank === true;
  const list = steps(status, typeof data === 'undefined' ? 0 : data.length, bank).filter(step => !(step.key === 'templates' && document.querySelector(step.target)?.classList.contains('hidden')));
  const open = list.filter(step => !step.done);
  document.querySelector('#firstSteps')?.remove();
  if (!open.length) return;
  const panel = document.createElement('section'); panel.id = 'firstSteps'; panel.className = 'first-steps';
  const title = document.createElement('b'); title.textContent = tr('fs.title', 'Erste Schritte') + ' · ' + tr('fs.progress', '{done} von {total} erledigt', { done: list.length - open.length, total: list.length });
  panel.append(title);
  for (const step of list) {
   const button = document.createElement('button'); button.type = 'button'; button.className = 'secondary' + (step.done ? ' done' : '');
   button.textContent = (step.done ? '✓ ' : '') + step.label; button.disabled = step.done;
   button.onclick = () => document.querySelector(step.target)?.click();
   panel.append(button);
  }
  host.append(panel);
 }
 let tries = 0;
 const wait = setInterval(() => { tries++; if (typeof currentUser !== 'undefined' && currentUser) { clearInterval(wait); render(); } else if (tries > 60) clearInterval(wait); }, 1000);
 document.addEventListener('lc-language-changed', render);
})();
