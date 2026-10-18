/* Prüfungsassistent: leads through the examination of a file step by step (see review-steps.js). Progress is stored per file, so a colleague can continue. */
(() => {
 if (typeof document === 'undefined' || !document.body || !globalThis.LcReviewSteps) return;
 const S = globalThis.LcReviewSteps;
 const node = (tag, text, css) => { const n = document.createElement(tag); if (text !== undefined) n.textContent = text; if (css) n.className = css; return n; };
 const stamp = value => value ? new Date(value).toLocaleString('de-DE', { dateStyle: 'short', timeStyle: 'short' }) : '–';
 const date = value => value ? new Date(value + 'T00:00:00').toLocaleDateString('de-DE') : '–';
 const SEVERITY = { DISCREPANCY: 'Abweichung', WARNING: 'Hinweis' };
 const money = (amount, currency) => amount == null ? '–' : new Intl.NumberFormat('de-DE', { minimumFractionDigits: 2 }).format(amount) + ' ' + (currency || '');

 async function open(lcId) {
  const id = lcId || activeLc?.id; if (!id) return;
  const editable = can('DOCUMENT_REVIEW');
  let dialog = document.querySelector('#reviewAssistantDialog');
  if (!dialog) { dialog = document.createElement('dialog'); dialog.id = 'reviewAssistantDialog'; dialog.className = 'wide-dialog review-assistant'; document.body.append(dialog); }
  dialog.onclose = null; dialog.replaceChildren(node('p', 'Der Assistent wird geladen …')); if (!dialog.open) dialog.showModal();
  let lc, results, documents, recorded, presentation = null, summary, missing, labelsLoaded = false, requirementFields = new Set(), requirementSuggestions = [];
  const load = async () => {
   [lc, { results }, documents, recorded] = await Promise.all([
    activeLc?.id === id ? Promise.resolve(activeLc) : json(`/api/lcs/${id}`), json(`/api/lcs/${id}/document-checks`), json(`/api/lcs/${id}/documents`).catch(() => []), json(`/api/lcs/${id}/review-steps`).catch(() => [])]);
   presentation = (await json(`/api/lcs/${id}/rule-facts`).catch(() => ({}))).LC_PRESENTATION_DATE || null;
   if (!labelsLoaded) { const defs = await json(`/api/lcs/${id}/rule-facts/definitions`).catch(() => null); if (defs?.lc) { LcDocumentReview.setFieldLabels(Object.fromEntries([...defs.lc, ...(defs.requirements || [])].map(d => [d.field, d.label]))); requirementFields = new Set((defs.requirements || []).map(d => d.field)); labelsLoaded = true; } }
   requirementSuggestions = await json(`/api/lcs/${id}/rule-requirements/suggestions`).catch(() => []);
   summary = S.summarize(results); missing = globalThis.LcDocumentReview?.missingInputs ? LcDocumentReview.missingInputs(results) : [];
   summary.ANGABEN.open = missing.length;
  };
  try { await load(); } catch (error) { dialog.replaceChildren(node('p', 'Der Assistent konnte nicht geladen werden: ' + error.message), closeButton(dialog)); return; }
  let index = S.currentIndex(recorded);
  const reload = async () => { try { await load(); } catch (error) { alert('Daten konnten nicht aktualisiert werden: ' + error.message); } render(); };
  const record = async (key, status, reason) => {
   await json(`/api/lcs/${id}/review-steps/${key}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ status, reason: reason || null }) });
   recorded = await json(`/api/lcs/${id}/review-steps`); if (index < S.STEPS.length - 1) index++; render();
  };
  const sub = list => list.length ? list : [node('p', 'Nichts offen.', 'muted')];

  function findings(key) {
   const items = summary[key]?.items || []; const box = node('div', undefined, 'assistant-findings');
   if (!items.length) { box.append(node('p', key === 'ANGABEN' ? '' : '✓ Hier ist nichts offen.', 'muted')); return box; }
   items.slice(0, 12).forEach(f => { const row = node('p', undefined, 'assistant-finding'); const badge = node('b', SEVERITY[f.severity] || f.severity, 'doc-review-badge ' + String(f.severity).toLowerCase()); row.append(badge, document.createTextNode(' ' + (f.documentName ? f.documentName + ': ' : '') + (f.message || ''))); box.append(row); });
   if (items.length > 12) box.append(node('p', `… und ${items.length - 12} weitere im Prüfmodus.`, 'muted'));
   return box;
  }
  const reviewButton = label => { const b = node('button', label || 'Dokumentweise prüfen öffnen', 'secondary'); b.type = 'button'; b.onclick = () => { LcDocumentReview?.open?.(); }; return b; };
  const watchReviewDialog = () => { const d = document.querySelector('#documentReviewDialog'); if (d) d.addEventListener('close', () => reload(), { once: true }); };

  const bodies = {
   AKTE() {
    const grid = node('div', undefined, 'assistant-cards');
    [['Referenz', lc.reference], ['Applicant', lc.applicant], ['Beneficiary', lc.beneficiary], ['Issuing Bank', lc.issuingBank], ['Betrag', money(lc.amount, lc.currency)], ['Ablauf', date(lc.expiryDate) + (lc.expiryPlace ? ' · ' + lc.expiryPlace : '')], ['Spätester Versand', date(lc.latestShipmentDate)]].forEach(([k, v]) => { const c = node('div', undefined, 'card'); c.append(node('small', k), node('b', v || '–')); grid.append(c); });
    const wrap = node('div'); wrap.append(grid, node('h4', 'Geforderte Dokumente'));
    const list = node('ul'); (lc.requiredDocuments || []).forEach(d => list.append(node('li', d))); wrap.append((lc.requiredDocuments || []).length ? list : node('p', 'Keine Dokumentenanforderungen erkannt.', 'muted'));
    if (editable && can('LC_EDIT') && typeof openEdit === 'function') { const edit = node('button', 'Akte bearbeiten', 'secondary'); edit.type = 'button'; edit.onclick = () => { if (activeLc?.id !== id) return; openEdit(); }; wrap.append(edit); }
    return wrap;
   },
   VOLLSTAENDIGKEIT() {
    const wrap = node('div'); wrap.append(node('h4', 'Vorliegende Dokumente'));
    if (!documents.length) wrap.append(node('p', 'Noch kein Dokument in der Akte.', 'muted'));
    else { const table = node('table', undefined, 'compact-table'); table.append(Object.assign(node('tr'), {})); documents.forEach(d => { const r = node('tr'); [d.typeLabel || d.documentType, d.originalFilename, typeof documentCopyLabel === 'function' ? documentCopyLabel(d.copyNumber) : ''].forEach(t => r.append(node('td', t))); table.append(r); }); wrap.append(table); }
    wrap.append(findings('VOLLSTAENDIGKEIT'));
    return wrap;
   },
   ANGABEN() {
    const wrap = node('div');
    if (!missing.length) { wrap.append(node('p', '✓ Es fehlen keine LC-Angaben.', 'muted')); return wrap; }
    wrap.append(node('p', 'Diese Angaben konnte die App nicht aus dem Akkreditiv lesen. Erfassen Sie sie, oder wählen Sie „Im Akkreditiv nicht verlangt“ – dann gelten die Regeln dazu als nicht anwendbar und fallen aus dieser Liste.', 'muted'));
    missing.forEach(m => {
     const row = node('div', undefined, 'doc-review-missing-row'); row.append(node('span', `${LcDocumentReview.fieldLabel(m.field)} · ${m.rules} Regel${m.rules === 1 ? '' : 'n'}`));
     if (editable && m.field === 'LC_PRESENTATION_DATE') {
      const input = document.createElement('input'); input.type = 'date'; input.value = new Date().toISOString().slice(0, 10); input.max = input.value;
      const save = node('button', 'Vorlagedatum speichern'); save.type = 'button'; const status = node('small'); status.setAttribute('role', 'status');
      save.onclick = async () => { save.disabled = true; try { await json(`/api/lcs/${id}/rule-facts/presentation-date`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ date: input.value }) }); await reload(); } catch (error) { status.textContent = error.message; save.disabled = false; } };
      row.append(input, save, status);
     } else if (editable && requirementFields.has(m.field)) {
      const note = node('small'); note.setAttribute('role', 'status');
      const proposals = requirementSuggestions.filter(x => x.field === m.field);
      proposals.forEach(x => {
       const take = node('button', `${x.documentTypeLabel}: ${x.value === 'true' ? 'ja' : x.value} aus dem Akkreditiv übernehmen`, 'secondary'); take.type = 'button';
       take.onclick = async () => { take.disabled = true; try { await json(`/api/lcs/${id}/rule-requirements/suggestions/apply`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ documentType: x.documentType, field: x.field }) }); await reload(); } catch (error) { note.textContent = error.message; take.disabled = false; } };
       row.append(take);
      });
      if (!proposals.length) row.append(node('small', 'Je Dokumenttyp unter Prüfdaten → Anforderungen erfassen.', 'muted'));
      const none = node('button', 'Im Akkreditiv nicht verlangt', 'secondary'); none.type = 'button';
      none.onclick = async () => { none.disabled = true; try { await json(`/api/lcs/${id}/rule-facts/waived/${encodeURIComponent(m.field)}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ waived: true }) }); await reload(); } catch (error) { note.textContent = error.message; none.disabled = false; } };
      row.append(none, note);
     } else if (editable) {
      const b = node('button', 'In den LC-Prüfdaten erfassen', 'secondary'); b.type = 'button'; b.dataset.ruleFacts = '';
      const none = node('button', 'Im Akkreditiv nicht verlangt', 'secondary'); none.type = 'button'; const note = node('small'); note.setAttribute('role', 'status');
      none.onclick = async () => { none.disabled = true; try { await json(`/api/lcs/${id}/rule-facts/waived/${encodeURIComponent(m.field)}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ waived: true }) }); await reload(); } catch (error) { note.textContent = error.message; none.disabled = false; } };
      row.append(b, none, note);
     }
     wrap.append(row);
    });
    return wrap;
   },
   COMPLIANCE() { const wrap = node('div'); wrap.append(findings('COMPLIANCE')); if (summary.COMPLIANCE.open) { const b = reviewButton('Treffer im Prüfmodus entscheiden'); b.addEventListener('click', watchReviewDialog); wrap.append(b); } return wrap; },
   DOKUMENTE() {
    const wrap = node('div'); const byDoc = new Map();
    documents.forEach(d => byDoc.set(d.originalFilename, { open: 0, blocking: 0, type: d.typeLabel || d.documentType }));
    summary.DOKUMENTE.items.forEach(f => { const e = byDoc.get(f.documentName) || { open: 0, blocking: 0, type: '' }; e.open++; byDoc.set(f.documentName, e); });
    const table = node('table', undefined, 'compact-table'); byDoc.forEach((e, name) => { const r = node('tr'); [e.type, name, e.open ? `${e.open} offen` : 'nichts offen'].forEach(t => r.append(node('td', t))); table.append(r); });
    wrap.append(byDoc.size ? table : node('p', 'Keine Dokumente.', 'muted'));
    const b = reviewButton(); b.addEventListener('click', watchReviewDialog); wrap.append(b);
    return wrap;
   },
   QUERPRUEFUNG() {
    const wrap = node('div'); const grid = node('div', undefined, 'assistant-cards');
    const days = value => { const n = S.daysUntil(value); return n === null ? '–' : n < 0 ? `vor ${-n} Tag${n === -1 ? '' : 'en'} abgelaufen` : n === 0 ? 'heute' : `in ${n} Tag${n === 1 ? '' : 'en'}`; };
    [['Ablauf des Akkreditivs', date(lc.expiryDate) + ' · ' + days(lc.expiryDate)], ['Spätester Versand', date(lc.latestShipmentDate) + ' · ' + days(lc.latestShipmentDate)], ['Vorlagedatum', presentation ? date(presentation) : 'nicht erfasst']].forEach(([k, v]) => { const c = node('div', undefined, 'card'); c.append(node('small', k), node('b', v)); grid.append(c); });
    wrap.append(grid, findings('QUERPRUEFUNG')); return wrap;
   },
   ABSCHLUSS() {
    const wrap = node('div'); const totalOpen = S.STEPS.reduce((n, s) => n + (s.key === 'ABSCHLUSS' ? 0 : summary[s.key].open), 0), totalBlocking = S.STEPS.reduce((n, s) => n + summary[s.key].blocking, 0);
    const decided = S.STEPS.reduce((n, s) => n + summary[s.key].decided, 0), discrepancies = (results || []).filter(f => f.reviewDecision === 'CONFIRMED_DISCREPANCY').length;
    const known = S.byKey(recorded); const skipped = S.STEPS.filter(s => known[s.key]?.status === 'SKIPPED');
    wrap.append(node('p', `${decided} Befunde entschieden (davon ${discrepancies} Abweichung${discrepancies === 1 ? '' : 'en'}), ${totalOpen} offen${totalBlocking ? ', davon ' + totalBlocking + ' blockierend' : ''}.`, totalBlocking ? 'error' : ''));
    if (skipped.length) { wrap.append(node('h4', 'Übersprungene Schritte')); const ul = node('ul'); skipped.forEach(s => ul.append(node('li', `${s.label}: ${known[s.key].reason} (${known[s.key].completedBy})`))); wrap.append(ul); }
    const actions = node('div', undefined, 'doc-review-finish-actions');
    if (discrepancies && globalThis.LcDocumentReview?.openNotice) { const b = node('button', 'Unstimmigkeitsmeldung erstellen'); b.type = 'button'; b.onclick = () => LcDocumentReview.openNotice(id); actions.append(b); }
    if (can('LC_EDIT') && totalOpen === 0) { const b = node('button', 'Prüfung abschließen (Vermerk in der Akte)', discrepancies ? 'secondary' : ''); b.type = 'button'; b.onclick = async () => { b.disabled = true; try { await json(`/api/lcs/${id}/notes`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ content: `Dokumentenprüfung abgeschlossen (Prüfungsassistent): ${decided} Befunde entschieden, ${discrepancies ? discrepancies + ' Abweichung' + (discrepancies === 1 ? '' : 'en') + ' bestätigt' : 'keine Abweichungen'}${skipped.length ? ', ' + skipped.length + ' Schritt' + (skipped.length === 1 ? '' : 'e') + ' übersprungen' : ''}.` }) }); b.textContent = 'Abschluss vermerkt ✓'; } catch (error) { b.disabled = false; alert(error.message); } }; actions.append(b); }
    if (can('AUDIT_VIEW')) { const a = node('a', 'Prüfakte exportieren', 'button-link secondary'); a.href = `/api/lcs/${id}/dossier`; actions.append(a); }
    wrap.append(actions); if (totalOpen) wrap.append(node('p', 'Der Abschlussvermerk ist erst möglich, wenn nichts mehr offen ist.', 'muted'));
    return wrap;
   }
  };

  function render() {
   const known = S.byKey(recorded), p = S.progress(recorded), step = S.STEPS[index], entry = summary[step.key];
   const head = node('div', undefined, 'dialoghead'); head.append(node('h2', `Prüfungsassistent · ${lc.reference}`), closeButton(dialog));
   const bar = node('div', undefined, 'doc-review-progress'); const progress = document.createElement('progress'); progress.max = p.total; progress.value = p.done + p.skipped;
   bar.append(node('span', `${p.done + p.skipped} von ${p.total} Schritten${p.skipped ? ` (${p.skipped} übersprungen)` : ''}`), progress);
   const nav = node('ol', undefined, 'assistant-steps');
   S.STEPS.forEach((s, i) => {
    const r = known[s.key], open = summary[s.key].open;
    const li = node('li'); const b = node('button', undefined, 'assistant-step' + (i === index ? ' active' : '') + (r ? ' ' + r.status.toLowerCase() : '')); b.type = 'button';
    b.append(node('span', r ? (r.status === 'DONE' ? '✓' : '↷') : String(i + 1), 'assistant-mark'), node('span', s.label)); if (open && !r) b.append(node('b', String(open), 'assistant-count'));
    b.onclick = () => { index = i; render(); }; li.append(b); nav.append(li);
   });
   const content = node('section', undefined, 'assistant-content');
   content.append(node('h3', `Schritt ${index + 1} von ${S.STEPS.length}: ${step.label}`), node('p', step.intro));
   const rec = known[step.key];
   if (rec) { const line = node('p', rec.status === 'DONE' ? `✓ Erledigt von ${rec.completedBy} am ${stamp(rec.completedAt)}` : `↷ Übersprungen von ${rec.completedBy} am ${stamp(rec.completedAt)}: ${rec.reason}`, 'assistant-recorded'); if (editable) { const reopen = node('button', 'Wieder öffnen', 'secondary'); reopen.type = 'button'; reopen.onclick = async () => { await json(`/api/lcs/${id}/review-steps/${step.key}`, { method: 'DELETE' }); recorded = await json(`/api/lcs/${id}/review-steps`); render(); }; line.append(' ', reopen); } content.append(line); }
   content.append(bodies[step.key]());
   if (!['AKTE', 'ANGABEN', 'VOLLSTAENDIGKEIT', 'DOKUMENTE', 'QUERPRUEFUNG', 'COMPLIANCE', 'ABSCHLUSS'].includes(step.key)) content.append(findings(step.key));
   const foot = node('div', undefined, 'assistant-footer');
   const back = node('button', '← Zurück', 'secondary'); back.type = 'button'; back.disabled = index === 0; back.onclick = () => { index--; render(); };
   const next = node('button', 'Weiter →', 'secondary'); next.type = 'button'; next.disabled = index === S.STEPS.length - 1; next.onclick = () => { index++; render(); };
   foot.append(back);
   if (editable) {
    const okOk = S.canComplete(entry); const done = node('button', 'Schritt erledigt ✓'); done.type = 'button'; done.disabled = !okOk; if (!okOk) done.title = `${entry.open} offen: erst entscheiden oder den Schritt mit Begründung überspringen.`;
    done.onclick = async () => { done.disabled = true; try { await record(step.key, 'DONE'); } catch (error) { alert(error.message); done.disabled = false; } };
    const skip = node('button', 'Überspringen …', 'secondary'); skip.type = 'button';
    skip.onclick = async () => {
     const reason = globalThis.LcReviewNote ? await LcReviewNote.ask({ title: `Schritt „${step.label}" überspringen`, text: 'Begründung (Pflicht, wird in der Akte vermerkt):', decision: 'ACCEPTED', required: true }) : prompt('Begründung zum Überspringen:');
     if (reason === null || !String(reason).trim()) return;
     try { await record(step.key, 'SKIPPED', String(reason).trim()); } catch (error) { alert(error.message); }
    };
    foot.append(skip, done);
   }
   foot.append(next);
   dialog.replaceChildren(head, bar, Object.assign(node('div', undefined, 'assistant-body'), {}), foot);
   dialog.querySelector('.assistant-body').append(nav, content);
  }
  render();
  dialog.onclose = () => { if (activeLc?.id === id && typeof show === 'function') show(id).catch?.(() => {}); };
 }
 function closeButton(dialog) { const b = node('button', 'Schließen'); b.type = 'button'; b.setAttribute('data-close', ''); b.onclick = () => dialog.close(); return b; }
 globalThis.LcReviewAssistant = { open };
 document.addEventListener('click', event => { if (event.target.closest?.('[data-review-assistant]')) open(); });
})();
