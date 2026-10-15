/* Review findings: filter chips by kind, document and rule with counts of open findings, a compact list with expandable details,
   and a bulk decision for the open warnings of the current selection. */
(() => {
 const KINDS = [
  [/^Regel verletzt/i, 'Regel verletzt'],
  [/^Manuelle fachliche Prüfung erforderlich/i, 'Manuelle Prüfung nötig'],
  [/^Regel nicht prüfbar/i, 'Nicht prüfbar (Angaben fehlen)'],
  [/^Interne Regel/i, 'Interne Regel']
 ];
 /** Kind of a finding, read from the outcome label the rule engine puts in front of the message. */
 function kindOf(message) { const m = String(message || ''); const hit = KINDS.find(([re]) => re.test(m)); return hit ? hit[1] : 'Sonstige'; }
 /** Message without severity word and outcome label, used as the readable name of a rule. */
 function ruleLabel(message) { return String(message || '').replace(/^(Hinweis|Abweichung|Erfüllt)\s*·\s*/i, '').replace(/^[^:]{3,60}:\s+/, '').trim(); }
 const open = r => r.severity !== 'OK' && !r.decided;
 /** One entry per value of a dimension (kind, document, code) with open findings, largest first. */
 function summarizeBy(rows, dimension) {
  const map = new Map();
  rows.forEach(r => {
   const key = r[dimension]; if (!key) return;
   const e = map.get(key) || { key, label: r.labels?.[dimension] || key, open: 0, warnings: 0, discrepancies: 0 };
   if (open(r)) { e.open++; if (r.severity === 'WARNING') e.warnings++; else if (r.severity === 'DISCREPANCY') e.discrepancies++; }
   if (r.labels?.[dimension]) e.label = r.labels[dimension];
   map.set(key, e);
  });
  return [...map.values()].filter(e => e.open > 0).sort((a, b) => b.open - a.open || String(a.key).localeCompare(String(b.key)));
 }
 /** Kept for the rule dimension. */
 function summarize(rows) { return summarizeBy(rows, 'code').map(e => ({ code: e.key, title: e.label, open: e.open, warnings: e.warnings, discrepancies: e.discrepancies })); }
 const matches = (r, filters) => Object.entries(filters).every(([dimension, value]) => !value || r[dimension] === value);
 /** Bulk decisions only ever cover open warnings of the selection; discrepancies always need a look of their own. */
 function bulkTargets(rows, filters) {
  const f = typeof filters === 'string' ? { code: filters } : (filters || {});
  if (!Object.values(f).some(Boolean)) return [];
  return rows.filter(r => r.severity === 'WARNING' && !r.decided && matches(r, f));
 }
 /** View state (filters, search, open groups, expanded rows) survives a reload of the dossier: captured before, applied to the next filter bar. */
 let live = null, pendingState = null;
 const rowKey = r => r.code + '|' + r.document;
 function captureState() {
  if (!live || !document.contains(live.bar)) return null;
  const { bar, list, filters, rows, groups } = live;
  return { ...filters, filter: bar.querySelector('[data-review-filter]')?.value || '', search: bar.querySelector('[data-review-search]')?.value || '',
   compact: list.classList.contains('findings-compact'), groups: groups.filter(g => g.details.open).map(g => g.name),
   expanded: rows.filter(r => r.row.classList.contains('expanded')).map(rowKey) };
 }
 globalThis.LcFindingsOverview = { summarize, summarizeBy, bulkTargets, kindOf, ruleLabel, matches, captureState, restoreOnNext: state => { pendingState = state; } };
 if (typeof document === 'undefined' || !document.body) return;

 const node = (tag, text, css) => { const n = document.createElement(tag); if (text !== undefined) n.textContent = text; if (css) n.className = css; return n; };
 const rowData = row => {
  const evidence = row.querySelector('[data-finding-evidence]'), decision = row.querySelector('[data-check-code]');
  const code = evidence?.dataset.findingEvidence || decision?.dataset.checkCode || '';
  const message = (row.querySelector(':scope > div > b')?.textContent || row.querySelector('b')?.textContent || '').replace(/^(Hinweis|Abweichung|Erfüllt)\s*·\s*/i, '');
  const document_ = decision?.dataset.checkDocument ?? evidence?.dataset.findingDocument ?? '';
  const title = row.querySelector('details > p')?.textContent.split(' · ')[0] || '';
  return { row, code, severity: row.dataset.findingSeverity, decided: row.dataset.findingDecided === 'true', document: document_, kind: kindOf(message),
   fingerprint: decision?.dataset.checkFingerprint || '', labels: { code: ruleLabel(message) || title || code, document: document_, kind: kindOf(message) } };
 };
 const GROUPS = [['kind', 'Art'], ['document', 'Dokument'], ['code', 'Regel']];
 const COLLAPSED = 10;

 function enhance(bar) {
  if (bar.dataset.overview) return;
  const list = bar.nextElementSibling;
  if (!list || !list.classList.contains('check-list')) return;
  const rows = [...list.querySelectorAll('[data-finding-severity]')].map(rowData);
  if (rows.length < 6) return;
  bar.dataset.overview = '1';
  const panel = node('div', undefined, 'finding-filters');
  const tools = node('div', undefined, 'finding-tools');
  const compact = node('button', 'Kompakte Liste'); compact.type = 'button'; compact.className = 'secondary';
  compact.setAttribute('aria-pressed', 'true'); list.classList.add('findings-compact');
  compact.onclick = () => { const on = list.classList.toggle('findings-compact'); compact.setAttribute('aria-pressed', String(on)); compact.textContent = on ? 'Kompakte Liste' : 'Ausführliche Liste'; };
  const reset = node('button', 'Auswahl aufheben'); reset.type = 'button'; reset.className = 'secondary'; reset.hidden = true;
  const bulk = node('button', ''); bulk.type = 'button'; bulk.className = 'secondary'; bulk.hidden = true;
  tools.append(compact, reset, bulk);
  // The filter bar must stay directly in front of the list: the existing filter code finds the list as its next sibling.
  bar.before(tools, panel);
  const filters = { kind: '', document: '', code: '' };

  // Findings that only say "data missing" are bundled per document into one collapsed line instead of a long run of rows.
  const NOT_CHECKABLE = KINDS.find(([re]) => re.test('Regel nicht prüfbar'))[1];
  const groups = [];
  const bundles = new Map();
  rows.filter(r => r.kind === NOT_CHECKABLE).forEach(r => { const l = bundles.get(r.document) || []; l.push(r); bundles.set(r.document, l); });
  bundles.forEach((members, document_) => {
   if (members.length < 3 || !members[0].row.parentNode) return;
   const details = node('details', undefined, 'finding-group');
   const summary = node('summary'); details.append(summary);
   members[0].row.before(details);
   members.forEach(m => details.append(m.row));
   groups.push({ details, summary, members, name: document_ || 'Dokument' });
  });
  const updateGroups = () => groups.forEach(g => {
   const visible = g.members.filter(m => !m.row.classList.contains('hidden')).length;
   const openCount = g.members.filter(m => open(m) && !m.row.classList.contains('hidden')).length;
   g.details.classList.toggle('hidden', visible === 0);
   g.summary.textContent = `${g.name}: ${visible} Angaben zur Prüfung fehlen${openCount !== visible ? ` (${openCount} offen)` : ''}`;
  });

  let apply = () => {
   rows.forEach(r => { if (!matches(r, filters)) r.row.classList.add('hidden'); });
   updateGroups();
   const visible = rows.filter(r => !r.row.classList.contains('hidden')).length;
   const selected = Object.entries(filters).filter(([, v]) => v).map(([d, v]) => rows.find(r => r[d] === v)?.labels[d] || v);
   const count = bar.querySelector('[data-review-count]'); if (count) count.textContent = `${visible} von ${rows.length} Befunden${selected.length ? ' · ' + selected.join(' · ') : ''} · Gesamtbewertung unverändert`;
   panel.querySelectorAll('select[data-dimension]').forEach(sel => { sel.value = filters[sel.dataset.dimension] || ''; });
   reset.hidden = !selected.length;
   const targets = bulkTargets(rows, filters).filter(r => !r.row.classList.contains('hidden'));
   bulk.hidden = !(targets.length >= 2 && typeof can === 'function' && can('DOCUMENT_REVIEW'));
   bulk.textContent = `Alle ${targets.length} offenen Hinweise der Auswahl als erfüllt bestätigen`;
  };
  const refilter = event => { if (event.target.matches?.('[data-review-filter],[data-review-search]') && bar.contains(event.target)) apply(); };
  document.addEventListener('input', refilter); document.addEventListener('change', refilter);
  reset.onclick = () => { Object.keys(filters).forEach(k => { filters[k] = ''; }); bar.querySelector('[data-review-filter]')?.dispatchEvent(new Event('change', { bubbles: true })); apply(); };

  GROUPS.forEach(([dimension, title]) => {
   const entries = summarizeBy(rows, dimension);
   if (!entries.length || (dimension !== 'code' && entries.length < 2)) return;
   const group = node('label', undefined, 'finding-select'); group.append(node('span', title + ':', 'finding-chip-title'));
   const select = node('select'); select.dataset.dimension = dimension; select.setAttribute('aria-label', `Offene Befunde nach ${title}`);
   const all = node('option', `Alle (${entries.reduce((n, e) => n + e.open, 0)})`); all.value = ''; select.append(all);
   entries.forEach(e => { const o = node('option', `${e.label.length > 60 ? e.label.slice(0, 57) + '…' : e.label} (${e.open})`); o.value = e.key; o.title = `${e.label} · ${e.warnings} Hinweise, ${e.discrepancies} Abweichungen offen`; select.append(o); });
   select.onchange = () => { filters[dimension] = select.value; bar.querySelector('[data-review-filter]')?.dispatchEvent(new Event('change', { bubbles: true })); apply(); };
   group.append(select);
   panel.append(group);
  });
  if (!panel.children.length) panel.append(node('small', 'Keine offenen Hinweise.'));

  list.addEventListener('click', event => {
   const row = event.target.closest('.check'); if (!row || !list.classList.contains('findings-compact')) return;
   if (event.target.closest('button,a,summary,details,input,select,textarea')) return;
   const expanded = row.classList.toggle('expanded'); row.setAttribute('aria-expanded', String(expanded));
  });

  /** Confirms the given open warnings with one shared reason; every finding is logged on its own. */
  const confirmTargets = async (targets, button) => {
   if (targets.length < 1 || typeof activeLc === 'undefined' || !activeLc) return;
   const kinds = [...new Set(targets.map(t => t.kind))].join(', ');
   const comment = await globalThis.LcReviewNote.ask({ decision: 'ACCEPTED', required: true, title: `${targets.length} Hinweise bestätigen`, text: `Pflichtbegründung für ${targets.length} Hinweise (${kinds}). Sie gilt für alle und wird je Befund protokolliert.` });
   if (comment === null) return;
   if (!comment.trim()) { alert('Bitte eine Begründung eingeben. Es wurde nichts gespeichert.'); return; }
   if (comment.length > 1000) { alert('Die Begründung darf maximal 1000 Zeichen enthalten.'); return; }
   if (typeof confirmAction === 'function' && !await confirmAction(`${targets.length} Hinweise (${kinds}) als erfüllt bestätigen? Abweichungen sind nicht betroffen.`)) return;
   const lcId = activeLc.id; let done = 0, failed = 0;
   button.disabled = true;
   for (const t of targets) {
    try { await json(`/api/lcs/${lcId}/document-checks/decisions`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ findingCode: t.code, documentName: t.document, decision: 'ACCEPTED', comment: comment.trim(), reviewFingerprint: t.fingerprint }) }); done++; }
    catch (error) { failed++; }
   }
   button.disabled = false;
   if (failed) alert(`${done} bestätigt, ${failed} konnten nicht gespeichert werden (z. B. geändert seit dem Laden). Bitte Prüfung neu laden.`);
   if (typeof activeLc !== 'undefined' && activeLc?.id === lcId) await (typeof refreshReviewInline === 'function' ? refreshReviewInline : showKeepingView)(lcId);
  };
  bulk.onclick = () => confirmTargets(bulkTargets(rows, filters).filter(r => !r.row.classList.contains('hidden')), bulk);

  // Pick single open warnings with a checkbox and confirm just those.
  if (typeof can === 'function' && can('DOCUMENT_REVIEW')) {
   const pickable = rows.filter(r => r.severity === 'WARNING' && !r.decided);
   pickable.forEach(r => {
    const box = node('input', undefined, 'finding-pick'); box.type = 'checkbox'; box.setAttribute('aria-label', 'Befund zur Sammelbestätigung auswählen');
    r.row.prepend(box); r.pick = box;
   });
   if (pickable.length >= 2) {
    const selectAll = node('button', 'Alle sichtbaren Hinweise auswählen'); selectAll.type = 'button'; selectAll.className = 'secondary';
    const confirmPicked = node('button', ''); confirmPicked.type = 'button'; confirmPicked.hidden = true;
    const picked = () => pickable.filter(r => r.pick.checked && !r.row.classList.contains('hidden'));
    const update = () => { const n = picked().length; confirmPicked.hidden = n === 0; confirmPicked.textContent = `${n} ausgewählte Hinweise als erfüllt bestätigen`; };
    list.addEventListener('change', event => { if (event.target.matches?.('.finding-pick')) update(); });
    selectAll.onclick = () => { const visible = pickable.filter(r => !r.row.classList.contains('hidden')); const all = visible.every(r => r.pick.checked); visible.forEach(r => { r.pick.checked = !all; }); update(); };
    confirmPicked.onclick = () => confirmTargets(picked(), confirmPicked);
    tools.append(selectAll, confirmPicked);
    const baseApply = apply; apply = () => { baseApply(); update(); };
   }
  }
  live = { bar, list, filters, rows, groups };
  if (pendingState) {
   const st = pendingState; pendingState = null;
   ['kind', 'document', 'code'].forEach(d => { filters[d] = st[d] && summarizeBy(rows, d).some(e => e.key === st[d]) ? st[d] : ''; });
   if (st.compact === false && list.classList.contains('findings-compact')) compact.onclick();
   groups.forEach(g => { if ((st.groups || []).includes(g.name)) g.details.open = true; });
   rows.forEach(r => { if ((st.expanded || []).includes(rowKey(r))) { r.row.classList.add('expanded'); r.row.setAttribute('aria-expanded', 'true'); } });
   const f = bar.querySelector('[data-review-filter]'), q = bar.querySelector('[data-review-search]');
   if (f && st.filter) f.value = st.filter; if (q && st.search) q.value = st.search;
   f?.dispatchEvent(new Event('change', { bubbles: true }));
  }
  apply();
 }

 let scheduled = false;
 const scan = () => { scheduled = false; document.querySelectorAll('.review-filter-bar').forEach(enhance); };
 new MutationObserver(() => { if (!scheduled) { scheduled = true; setTimeout(scan, 50); } }).observe(document.body, { childList: true, subtree: true });
 scan();
})();
