/* Guided order in a bank file: 1. check the draft of the credit (optional), 2. compare the credit that arrived with the draft, 3. check the credit itself, 4. examine the documents later.
   A card at the top of the file overview; the state comes from /api/lcs/{id}/flow (only the draft is stored). */
(() => {
 const tr = (key, de, values = {}) => { const i = globalThis.LcI18n, s = i?.t ? i.t(key, values) : null; return s && s !== key ? s : String(de).replace(/\{(\w+)\}/g, (m, k) => values[k] ?? m); };
 const english = () => globalThis.LcI18n?.language?.() === 'en';

 /** The four steps with their state (done | open | attention | skipped | later) and a one-line text; `flow` is the server answer. */
 function steps(flow) {
  const draft = flow.draft, diffs = flow.differences, check = flow.creditCheck;
  const count = (d, w) => d ? tr('lf.counts', '{d} Abweichung(en), {w} Hinweis(e)', { d, w }) : w ? tr('lf.warnings', '{w} Hinweis(e)', { w }) : tr('lf.noFindings', 'keine Auffälligkeit');
  const first = !draft
   ? { key: 'draft', state: 'skipped', text: tr('lf.draft.none', 'Kein Entwurf hinterlegt (optional).') }
   : { key: 'draft', state: draft.discrepancies ? 'attention' : 'done', text: tr('lf.draft.set', 'Entwurf hinterlegt ({name}): {counts}', { name: draft.filename || tr('lf.draft.text', 'Text'), counts: count(draft.discrepancies, draft.warnings) }) };
  const second = !draft
   ? { key: 'compare', state: 'skipped', text: tr('lf.compare.none', 'Entfällt ohne Entwurf.') }
   : { key: 'compare', state: diffs && diffs.length ? 'attention' : 'done', text: diffs && diffs.length ? tr('lf.compare.diffs', '{n} Feld(er) weichen vom Entwurf ab.', { n: diffs.length }) : tr('lf.compare.same', 'Die LC stimmt mit dem Entwurf überein.') };
  const third = !check
   ? { key: 'check', state: 'open', text: tr('lf.check.none', 'Nicht prüfbar: kein SWIFT-Text in der Akte.') }
   : { key: 'check', state: check.discrepancies ? 'attention' : 'done', text: count(check.discrepancies, check.warnings) };
  const fourth = flow.reviewCompleted
   ? { key: 'documents', state: 'done', text: tr('lf.docs.done', 'Prüfung der Dokumente abgeschlossen.') }
   : { key: 'documents', state: 'later', text: tr('lf.docs.later', 'Folgt, sobald Dokumente eingereicht sind.') };
  return [first, second, third, fourth];
 }
 globalThis.LcFlow = { steps };
 if (typeof document === 'undefined' || !document.body || typeof show !== 'function') return;

 const el = (tag, props = {}, ...children) => { const node = Object.assign(document.createElement(tag), props); node.append(...children); return node; };
 const ICON = { done: '✓', attention: '!', open: '○', skipped: '–', later: '…' };
 const NAME = () => ({ draft: tr('lf.s.draft', 'Entwurf prüfen (optional)'), compare: tr('lf.s.compare', 'LC mit Entwurf abgleichen'), check: tr('lf.s.check', 'LC prüfen'), documents: tr('lf.s.documents', 'Dokumente prüfen') });

 function draftDialog(lcId, done) {
  const dialog = el('dialog', { className: 'wide-dialog' }); dialog.onclose = () => dialog.remove();
  const text = el('textarea', { rows: 12, placeholder: ':20:...\n:32B:...' }), file = el('input', { type: 'file', accept: '.txt,.pdf,text/plain,application/pdf' }), error = el('div', { className: 'error', role: 'alert' });
  const save = el('button', { type: 'button', textContent: tr('lf.draft.save', 'Entwurf hinterlegen') });
  save.onclick = async () => {
   save.disabled = true; error.textContent = '';
   try {
    if (file.files[0]) { const body = new FormData(); body.append('file', file.files[0]); await json(`/api/lcs/${lcId}/draft`, { method: 'PUT', body }); }
    else await json(`/api/lcs/${lcId}/draft`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ text: text.value }) });
    dialog.close(); done();
   } catch (e) { error.textContent = e.message; save.disabled = false; }
  };
  const close = el('button', { type: 'button', className: 'ghost', textContent: '×' }); close.onclick = () => dialog.close();
  dialog.append(el('div', { className: 'dialoghead' }, el('div', {}, el('h2', { textContent: tr('lf.draft.title', 'Entwurf zur Akte hinterlegen') }), el('p', { textContent: tr('lf.draft.intro', 'MT700-Text des Entwurfs einfügen oder Datei wählen. Er wird mit der LC dieser Akte abgeglichen.') })), close),
   el('label', {}, tr('ld.text', 'Entwurf als Text'), text), el('label', {}, tr('ld.file', 'oder als Datei (PDF oder Text)'), file), error, el('div', { className: 'actions' }, save));
  document.body.append(dialog); dialog.showModal();
 }

 function render(card, lcId, flow, reload) {
  card.replaceChildren(el('h3', { textContent: tr('lf.title', 'Ablauf der Prüfung') }));
  const names = NAME(), list = el('ol', { className: 'lc-flow-steps' });
  for (const step of steps(flow)) {
   const row = el('li', { className: 'lc-flow-step lc-flow-' + step.state }, el('b', { textContent: `${ICON[step.state]} ${names[step.key]}` }), el('small', { textContent: ' – ' + step.text }));
   const actions = el('span', { className: 'lc-flow-actions' });
   if (step.key === 'draft' && can('LC_EDIT')) {
    actions.append(el('button', { type: 'button', className: 'secondary', textContent: flow.draft ? tr('lf.draft.replace', 'Ersetzen') : tr('lf.draft.add', 'Entwurf hinterlegen'), onclick: () => draftDialog(lcId, reload) }));
    if (flow.draft) actions.append(el('button', { type: 'button', className: 'secondary', textContent: tr('lf.draft.remove', 'Entfernen'), onclick: async () => { try { await json(`/api/lcs/${lcId}/draft`, { method: 'DELETE' }); reload(); } catch (e) { alert(e.message); } } }));
   }
   if (step.key === 'documents' && globalThis.LcReviewAssistant?.open && can('DOCUMENT_REVIEW')) actions.append(el('button', { type: 'button', className: 'secondary', textContent: tr('lf.docs.open', 'Prüfungsassistent öffnen'), onclick: () => LcReviewAssistant.open(lcId) }));
   row.append(' ', actions);
   if (step.key === 'compare' && flow.differences?.length) row.append(differences(flow.differences));
   if (step.key === 'check' && flow.creditCheck?.findings?.length) row.append(findings(flow.creditCheck.findings));
   list.append(row);
  }
  card.append(list);
 }
 function differences(diffs) {
  const details = el('details', {}, el('summary', { textContent: tr('lf.show', 'Details anzeigen') })), table = el('table', { className: 'lc-flow-diff' });
  table.append(el('thead', {}, el('tr', {}, el('th', { textContent: tr('lf.col.field', 'Feld') }), el('th', { textContent: tr('lf.col.draft', 'Entwurf') }), el('th', { textContent: tr('lf.col.credit', 'LC') }))));
  const body = el('tbody'); for (const d of diffs) body.append(el('tr', {}, el('td', { textContent: `:${d.tag}: ${d.label}` }), el('td', { textContent: d.draft || '–' }), el('td', { textContent: d.credit || '–' })));
  table.append(body); details.append(table); return details;
 }
 function findings(list) {
  const details = el('details', {}, el('summary', { textContent: tr('lf.show', 'Details anzeigen') })), ul = el('ul');
  for (const f of list) ul.append(el('li', { textContent: (english() && f.messageEn) || f.message }));
  details.append(ul); return details;
 }

 async function mount(id) {
  const overview = document.querySelector('#detail .dossier-section[data-section="overview"]');
  if (!overview || globalThis.LcWorkspaceModules?.bank !== true) return;
  overview.querySelector('.lc-flow')?.remove();
  if (typeof activeLc !== 'undefined' && activeLc?.instrumentType && activeLc.instrumentType !== 'LC') { /* guarantees have no draft flow */ }
  const flow = await json(`/api/lcs/${encodeURIComponent(id)}/flow`).catch(() => null);
  if (!flow || typeof activeLc === 'undefined' || activeLc?.id !== id) return;
  const card = el('section', { className: 'lc-flow' }); overview.insertAdjacentElement('afterbegin', card);
  const reload = () => mount(id);
  render(card, id, flow, reload);
 }
 const base = show;
 show = async function (id) { await base(id); try { await mount(id); } catch (ignored) { } };
})();
