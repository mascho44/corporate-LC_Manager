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
 globalThis.LcFindingsOverview = { summarize, summarizeBy, bulkTargets, kindOf, ruleLabel, matches };
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

  const apply = () => {
   rows.forEach(r => { if (!matches(r, filters)) r.row.classList.add('hidden'); });
   const visible = rows.filter(r => !r.row.classList.contains('hidden')).length;
   const selected = Object.entries(filters).filter(([, v]) => v).map(([d, v]) => rows.find(r => r[d] === v)?.labels[d] || v);
   const count = bar.querySelector('[data-review-count]'); if (count) count.textContent = `${visible} von ${rows.length} Befunden${selected.length ? ' · ' + selected.join(' · ') : ''} · Gesamtbewertung unverändert`;
   panel.querySelectorAll('[data-chip]').forEach(b => b.setAttribute('aria-pressed', String(filters[b.dataset.dimension] === b.dataset.chip)));
   reset.hidden = !selected.length;
   const targets = bulkTargets(rows, filters).filter(r => !r.row.classList.contains('hidden'));
   bulk.hidden = !(targets.length >= 2 && typeof can === 'function' && can('DOCUMENT_REVIEW'));
   bulk.textContent = `Alle ${targets.length} offenen Hinweise der Auswahl als erfüllt bestätigen`;
  };
  const refilter = event => { if (event.target.matches?.('[data-review-filter],[data-review-search]') && bar.contains(event.target)) apply(); };
  document.addEventListener('input', refilter); document.addEventListener('change', refilter);
  const choose = (dimension, key) => {
   filters[dimension] = filters[dimension] === key ? '' : key;
   bar.querySelector('[data-review-filter]')?.dispatchEvent(new Event('change', { bubbles: true }));
   apply();
  };
  reset.onclick = () => { Object.keys(filters).forEach(k => { filters[k] = ''; }); bar.querySelector('[data-review-filter]')?.dispatchEvent(new Event('change', { bubbles: true })); apply(); };

  GROUPS.forEach(([dimension, title]) => {
   const entries = summarizeBy(rows, dimension);
   if (!entries.length || (dimension !== 'code' && entries.length < 2)) return;
   const group = node('div', undefined, 'finding-chips'); group.setAttribute('role', 'group'); group.setAttribute('aria-label', `Offene Befunde nach ${title}`);
   group.append(node('b', title + ':', 'finding-chip-title'));
   const chip = e => {
    const b = node('button', `${e.label.length > 70 ? e.label.slice(0, 67) + '…' : e.label} (${e.open})`, 'finding-chip'); b.type = 'button';
    b.dataset.chip = e.key; b.dataset.dimension = dimension; b.title = `${e.label} · ${e.warnings} Hinweise, ${e.discrepancies} Abweichungen offen`; b.setAttribute('aria-pressed', 'false');
    b.onclick = () => choose(dimension, e.key); return b;
   };
   entries.slice(0, COLLAPSED).forEach(e => group.append(chip(e)));
   if (entries.length > COLLAPSED) {
    const more = node('button', `weitere ${entries.length - COLLAPSED} anzeigen`, 'finding-chip finding-more'); more.type = 'button'; more.setAttribute('aria-expanded', 'false');
    more.onclick = () => { entries.slice(COLLAPSED).forEach(e => group.insertBefore(chip(e), more)); more.remove(); apply(); };
    group.append(more);
   }
   panel.append(group);
  });
  if (!panel.children.length) panel.append(node('small', 'Keine offenen Hinweise.'));

  list.addEventListener('click', event => {
   const row = event.target.closest('.check'); if (!row || !list.classList.contains('findings-compact')) return;
   if (event.target.closest('button,a,summary,details,input,select,textarea')) return;
   const expanded = row.classList.toggle('expanded'); row.setAttribute('aria-expanded', String(expanded));
  });

  bulk.onclick = async () => {
   const targets = bulkTargets(rows, filters).filter(r => !r.row.classList.contains('hidden'));
   if (targets.length < 2 || typeof activeLc === 'undefined' || !activeLc) return;
   const kinds = [...new Set(targets.map(t => t.kind))].join(', ');
   const comment = prompt(`Pflichtbegründung für ${targets.length} Hinweise (${kinds}). Sie gilt für alle und wird je Befund protokolliert:`);
   if (comment === null) return;
   if (!comment.trim()) { alert('Bitte eine Begründung eingeben. Es wurde nichts gespeichert.'); return; }
   if (comment.length > 1000) { alert('Die Begründung darf maximal 1000 Zeichen enthalten.'); return; }
   if (typeof confirmAction === 'function' && !await confirmAction(`${targets.length} Hinweise (${kinds}) als erfüllt bestätigen? Abweichungen sind nicht betroffen.`)) return;
   const lcId = activeLc.id; let done = 0, failed = 0;
   bulk.disabled = true;
   for (const t of targets) {
    try { await json(`/api/lcs/${lcId}/document-checks/decisions`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ findingCode: t.code, documentName: t.document, decision: 'ACCEPTED', comment: comment.trim(), reviewFingerprint: t.fingerprint }) }); done++; }
    catch (error) { failed++; }
   }
   bulk.disabled = false;
   if (failed) alert(`${done} bestätigt, ${failed} konnten nicht gespeichert werden (z. B. geändert seit dem Laden). Bitte Prüfung neu laden.`);
   if (typeof activeLc !== 'undefined' && activeLc?.id === lcId) { await show(lcId); if (typeof activateDossierSection === 'function') activateDossierSection('checks'); }
  };
  apply();
 }

 let scheduled = false;
 const scan = () => { scheduled = false; document.querySelectorAll('.review-filter-bar').forEach(enhance); };
 new MutationObserver(() => { if (!scheduled) { scheduled = true; setTimeout(scan, 50); } }).observe(document.body, { childList: true, subtree: true });
 scan();
})();
