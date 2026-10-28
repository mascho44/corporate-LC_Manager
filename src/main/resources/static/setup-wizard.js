/* Setup wizard for tenant admins: asks for the company data step by step and points to the next set-up pages.
   The steps differ by workspace: corporates need tax data, bank account and logo for their documents; banks need the institute data and the bank-only set-up (SLA rule, correspondent banks).
   Saves through the existing company API (/api/companies); opened from the "First steps" panel. */
(() => {
 const tr = (key, de, values = {}) => { const i = globalThis.LcI18n, s = i?.t ? i.t(key, values) : null; return s && s !== key ? s : String(de).replace(/\{(\w+)\}/g, (m, k) => values[k] ?? m); };

 const field = (name, label, extra = {}) => ({ name, label, ...extra });

 /** The steps for a workspace; `bank` and `corporate` are the module flags (a combined workspace has both). */
 function plan(bank, corporate) {
  const name = bank && !corporate ? tr('sw.f.nameBank', 'Name der Bank') : tr('sw.f.nameCompany', 'Firmenname');
  const steps = [
   { key: 'identity', title: bank && !corporate ? tr('sw.s.institute', 'Ihre Bank') : tr('sw.s.company', 'Ihre Firma'), fields: [
    field('legalName', name, { required: true, max: 255 }), field('addressLine', tr('sw.f.address', 'Straße und Hausnummer'), { max: 255 }),
    field('postalCode', tr('sw.f.postal', 'Postleitzahl'), { max: 30 }), field('city', tr('sw.f.city', 'Ort'), { max: 120 }), field('country', tr('sw.f.country', 'Land'), { max: 120 }),
    ...(bank ? [field('bic', 'BIC', { max: 50 })] : [])] },
   { key: 'contact', title: tr('sw.s.contact', 'Kontakt und Register'), fields: [
    field('contactPerson', tr('sw.f.contact', 'Ansprechpartner'), { max: 255 }), field('email', tr('sw.f.email', 'E-Mail'), { type: 'email', max: 255 }), field('phone', tr('sw.f.phone', 'Telefon'), { max: 80 }),
    field('registrationNumber', tr('sw.f.register', 'Handelsregisternummer'), { max: 100 }),
    ...(corporate ? [field('taxId', tr('sw.f.tax', 'Steuernummer / USt-ID'), { max: 100 })] : [])] }
  ];
  if (corporate) steps.push({ key: 'bankdata', title: tr('sw.s.bank', 'Bankverbindung'), hint: tr('sw.h.bank', 'Die Angaben erscheinen auf Rechnung und Begleitschreiben.'), fields: [
   field('bankName', tr('sw.f.bankName', 'Bank'), { max: 255 }), field('iban', 'IBAN', { max: 100 }), ...(bank ? [] : [field('bic', 'BIC', { max: 50 })])] });
  steps.push({ key: 'logo', title: tr('sw.s.logo', 'Logo'), logo: true, hint: tr('sw.h.logo', 'Das Logo erscheint im Briefkopf der erzeugten Dokumente (PNG oder JPEG, höchstens 2 MB). Sie können diesen Schritt überspringen.') });
  steps.push({ key: 'next', title: tr('sw.s.next', 'Weitere Einrichtung'), links: [
   { label: tr('sw.l.users', 'Benutzer einladen'), target: '#appNavUsers' },
   ...(corporate ? [{ label: tr('sw.l.templates', 'Dokumentvorlagen hinterlegen'), target: '#appNavTemplates' }] : []),
   ...(bank ? [{ label: tr('sw.l.sla', 'SLA-Regel anlegen'), target: '#appNavSla' }, { label: tr('sw.l.correspondent', 'Korrespondenzbank erfassen'), target: '#appNavCorrespondent' }] : [])] });
  return steps;
 }
 globalThis.LcSetupWizard = { plan };
 if (typeof document === 'undefined' || !document.body) return;

 let profileId = null, profile = {}, index = 0, steps = [];

 const el = (tag, props = {}, ...children) => { const node = Object.assign(document.createElement(tag), props); node.append(...children); return node; };

 async function load() {
  const list = await json('/api/companies');
  if (list.length) { profileId = list[0].id; profile = await json('/api/companies/' + profileId); } else { profileId = null; profile = {}; }
 }

 async function save(values) {
  profile = { ...profile, ...values };
  const body = JSON.stringify(Object.fromEntries(Object.entries(profile).filter(([key]) => key !== 'id' && key !== 'logoAvailable')));
  const saved = await json(profileId ? '/api/companies/' + profileId : '/api/companies', { method: profileId ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' }, body });
  profileId = saved.id; profile = { ...profile, ...saved };
 }

 function render(dialog) {
  const step = steps[index], last = index === steps.length - 1, error = el('div', { className: 'error' });
  const form = el('form'), head = el('div', { className: 'dialoghead' }, el('div', {}, el('h2', { textContent: tr('sw.title', 'Einrichtung') + ' · ' + step.title }), el('p', { textContent: tr('sw.progress', 'Schritt {n} von {total}', { n: index + 1, total: steps.length }) })));
  const close = el('button', { type: 'button', className: 'ghost', textContent: '×' }); close.onclick = () => dialog.close(); head.append(close);
  form.append(head);
  if (step.hint) form.append(el('p', { className: 'muted', textContent: step.hint }));
  if (step.fields) {
   const grid = el('div', { className: 'form-grid' });
   for (const f of step.fields) { const input = el('input', { name: f.name, type: f.type || 'text', value: profile[f.name] || '', required: f.required === true }); if (f.max) input.maxLength = f.max; grid.append(el('label', { className: f.name === 'legalName' || f.name === 'addressLine' ? 'wide' : '' }, f.label, input)); }
   form.append(grid);
  }
  if (step.logo) {
   const file = el('input', { type: 'file', accept: 'image/png,image/jpeg' }); file.name = 'file';
   const note = el('p', { className: 'muted', textContent: profile.logoAvailable ? tr('sw.logoSet', 'Ein Logo ist hinterlegt.') : '' });
   file.onchange = async () => { if (!file.files[0] || !profileId) return; const body = new FormData(); body.append('file', file.files[0]); try { await json('/api/companies/' + profileId + '/logo', { method: 'POST', body }); profile.logoAvailable = true; note.textContent = tr('sw.logoSet', 'Ein Logo ist hinterlegt.'); } catch (e) { error.textContent = e.message; } };
   form.append(el('label', {}, tr('sw.f.logo', 'Logo (PNG/JPEG, max. 2 MB)'), file), note);
  }
  if (step.links) {
   form.append(el('p', { textContent: tr('sw.nextText', 'Die Firmendaten sind gespeichert. Diese Schritte folgen noch:') }));
   const row = el('div', { className: 'actions' });
   for (const link of step.links) { const b = el('button', { type: 'button', className: 'secondary', textContent: link.label }); b.disabled = !document.querySelector(link.target); b.onclick = () => { dialog.close(); document.querySelector(link.target)?.click(); }; row.append(b); }
   form.append(row);
  }
  form.append(error);
  const actions = el('div', { className: 'actions' }), back = el('button', { type: 'button', className: 'secondary', textContent: tr('sw.back', 'Zurück') }); back.disabled = index === 0;
  back.onclick = () => { index--; render(dialog); };
  const next = el('button', { type: 'submit', textContent: last ? tr('sw.finish', 'Fertig') : tr('sw.next', 'Weiter') });
  actions.append(back, next); form.append(actions);
  form.onsubmit = async event => {
   event.preventDefault();
   if (last) { dialog.close(); document.dispatchEvent(new Event('lc-language-changed')); return; }
   try {
    if (step.fields) await save(Object.fromEntries(new FormData(form).entries()));
    else if (!profileId) throw new Error(tr('sw.needCompany', 'Bitte zuerst die Firmendaten speichern.'));
    index++; render(dialog);
   } catch (e) { error.textContent = e.message; }
  };
  dialog.replaceChildren(form);
 }

 async function open() {
  const dialog = document.querySelector('#setupWizard') || el('dialog', { id: 'setupWizard', className: 'wide-dialog' });
  if (!dialog.isConnected) document.body.append(dialog);
  steps = plan(globalThis.LcWorkspaceModules?.bank === true, globalThis.LcWorkspaceModules?.corporate === true); index = 0;
  try { await load(); } catch (e) { alert(e.message); return; }
  render(dialog); dialog.showModal();
 }
 globalThis.LcSetupWizard.open = open;
})();
