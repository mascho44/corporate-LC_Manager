/* Review findings: rule chips with counts, compact list with expandable details, and a bulk decision for the warnings of one rule. */
(() => {
 /** One entry per rule with open findings (warnings and discrepancies without a manual decision), largest first. */
 function summarize(rows) {
  const byCode = new Map();
  rows.forEach(r => {
   if (!r.code) return;
   const entry = byCode.get(r.code) || { code: r.code, title: r.title || r.code, open: 0, warnings: 0, discrepancies: 0 };
   if (r.severity !== 'OK' && !r.decided) { entry.open++; if (r.severity === 'WARNING') entry.warnings++; else if (r.severity === 'DISCREPANCY') entry.discrepancies++; }
   if (r.title && entry.title === r.code) entry.title = r.title;
   byCode.set(r.code, entry);
  });
  return [...byCode.values()].filter(e => e.open > 0).sort((a, b) => b.open - a.open || a.code.localeCompare(b.code));
 }
 /** Bulk decisions only ever cover open warnings of one rule; discrepancies always need a look of their own. */
 function bulkTargets(rows, code) { if (!code) return []; return rows.filter(r => r.code === code && r.severity === 'WARNING' && !r.decided); }
 globalThis.LcFindingsOverview = { summarize, bulkTargets };
 if (typeof document === 'undefined' || !document.body) return;

 const node = (tag, text, css) => { const n = document.createElement(tag); if (text !== undefined) n.textContent = text; if (css) n.className = css; return n; };
 const rowData = row => {
  const evidence = row.querySelector('[data-finding-evidence]'), decision = row.querySelector('[data-check-code]');
  const code = evidence?.dataset.findingEvidence || decision?.dataset.checkCode || '';
  const title = row.querySelector('details > p')?.textContent.split(' · ')[0] || '';
  return { row, code, title, severity: row.dataset.findingSeverity, decided: row.dataset.findingDecided === 'true',
   document: decision?.dataset.checkDocument ?? evidence?.dataset.findingDocument ?? '', fingerprint: decision?.dataset.checkFingerprint || '' };
 };

 function enhance(bar) {
  if (bar.dataset.overview) return;
  const list = bar.nextElementSibling;
  if (!list || !list.classList.contains('check-list')) return;
  const rows = [...list.querySelectorAll('[data-finding-severity]')].map(rowData);
  if (rows.length < 6) return;
  bar.dataset.overview = '1';
  const chips = node('div', undefined, 'finding-chips'); chips.setAttribute('aria-label', 'Offene Befunde nach Prüfregel');
  const tools = node('div', undefined, 'finding-tools');
  const compact = node('button', 'Kompakte Liste'); compact.type = 'button'; compact.className = 'secondary';
  compact.setAttribute('aria-pressed', 'true'); list.classList.add('findings-compact');
  compact.onclick = () => { const on = list.classList.toggle('findings-compact'); compact.setAttribute('aria-pressed', String(on)); compact.textContent = on ? 'Kompakte Liste' : 'Ausführliche Liste'; };
  const bulk = node('button', ''); bulk.type = 'button'; bulk.className = 'secondary'; bulk.hidden = true;
  tools.append(compact, bulk);
  bar.after(chips, tools);
  let active = '';

  const apply = () => {
   rows.forEach(r => { if (active && r.code !== active) r.row.classList.add('hidden'); });
   const visible = rows.filter(r => !r.row.classList.contains('hidden')).length;
   const count = bar.querySelector('[data-review-count]'); if (count) count.textContent = `${visible} von ${rows.length} Befunden${active ? ' · Regel ' + active : ''} · Gesamtbewertung unverändert`;
   chips.querySelectorAll('button').forEach(b => b.setAttribute('aria-pressed', String(b.dataset.ruleChip === active)));
   const targets = bulkTargets(rows, active).filter(r => !r.row.classList.contains('hidden'));
   bulk.hidden = !(active && targets.length >= 2 && typeof can === 'function' && can('DOCUMENT_REVIEW'));
   bulk.textContent = `Alle ${targets.length} offenen Hinweise dieser Regel als erfüllt bestätigen`;
  };
  const refilter = event => { if (event.target.matches?.('[data-review-filter],[data-review-search]') && bar.contains(event.target)) apply(); };
  document.addEventListener('input', refilter); document.addEventListener('change', refilter);

  const select = code => {
   active = active === code ? '' : code;
   bar.querySelector('[data-review-filter]')?.dispatchEvent(new Event('change', { bubbles: true }));
   apply();
  };
  summarize(rows).forEach(entry => {
   const chip = node('button', `${entry.title} (${entry.open})`, 'finding-chip'); chip.type = 'button'; chip.dataset.ruleChip = entry.code;
   chip.title = `${entry.code} · ${entry.warnings} Hinweise, ${entry.discrepancies} Abweichungen offen`; chip.setAttribute('aria-pressed', 'false');
   chip.onclick = () => select(entry.code); chips.append(chip);
  });
  if (!chips.children.length) chips.append(node('small', 'Keine offenen Hinweise.'));

  list.addEventListener('click', event => {
   const row = event.target.closest('.check'); if (!row || !list.classList.contains('findings-compact')) return;
   if (event.target.closest('button,a,summary,details,input,select,textarea')) return;
   const expanded = row.classList.toggle('expanded'); row.setAttribute('aria-expanded', String(expanded));
  });

  bulk.onclick = async () => {
   const targets = bulkTargets(rows, active).filter(r => !r.row.classList.contains('hidden'));
   if (targets.length < 2 || typeof activeLc === 'undefined' || !activeLc) return;
   const comment = prompt(`Pflichtbegründung für ${targets.length} Hinweise der Regel „${targets[0].title}“. Sie gilt für alle und wird je Befund protokolliert:`);
   if (comment === null) return;
   if (!comment.trim()) { alert('Bitte eine Begründung eingeben. Es wurde nichts gespeichert.'); return; }
   if (comment.length > 1000) { alert('Die Begründung darf maximal 1000 Zeichen enthalten.'); return; }
   if (typeof confirmAction === 'function' && !await confirmAction(`${targets.length} Hinweise als erfüllt bestätigen? Abweichungen sind nicht betroffen.`)) return;
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
