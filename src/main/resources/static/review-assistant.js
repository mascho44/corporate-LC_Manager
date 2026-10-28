/* Prüfungsassistent: leads through the examination of a file step by step (see review-steps.js). Progress is stored per file, so a colleague can continue. */
(() => {
 if (typeof document === 'undefined' || !document.body || !globalThis.LcReviewSteps) return;
 const S = globalThis.LcReviewSteps;
 const tr = (key, de, values = {}) => { const i = globalThis.LcI18n, s = i?.t ? i.t(key, values) : null; return s && s !== key ? s : String(de).replace(/\{(\w+)\}/g, (m, k) => values[k] ?? m); };
 const locale = () => globalThis.LcI18n?.language?.() === 'en' ? 'en-GB' : 'de-DE';
 const node = (tag, text, css) => { const n = document.createElement(tag); if (text !== undefined) n.textContent = text; if (css) n.className = css; return n; };
 const stamp = value => value ? new Date(value).toLocaleString(locale(), { dateStyle: 'short', timeStyle: 'short' }) : '–';
 const date = value => value ? new Date(value + 'T00:00:00').toLocaleDateString(locale()) : '–';
 const SEVERITY = () => ({ DISCREPANCY: tr('dr.sev.disc', 'Abweichung'), WARNING: tr('dr.sev.warn', 'Hinweis') });
 const money = (amount, currency) => amount == null ? '–' : new Intl.NumberFormat('de-DE', { minimumFractionDigits: 2 }).format(amount) + ' ' + (currency || '');

 async function open(lcId) {
  const id = lcId || activeLc?.id; if (!id) return;
  const editable = can('DOCUMENT_REVIEW');
  let dialog = document.querySelector('#reviewAssistantDialog');
  if (!dialog) { dialog = document.createElement('dialog'); dialog.id = 'reviewAssistantDialog'; dialog.className = 'wide-dialog review-assistant'; document.body.append(dialog); }
  dialog.onclose = null; dialog.replaceChildren(node('p', tr('ra.loading', 'Der Assistent wird geladen …'))); if (!dialog.open) dialog.showModal();
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
  try { await load(); } catch (error) { dialog.replaceChildren(node('p', tr('ra.loadFailed', 'Der Assistent konnte nicht geladen werden:') + ' ' + error.message), closeButton(dialog)); return; }
  let index = S.currentIndex(recorded);
  const reload = async () => { try { await load(); } catch (error) { alert(tr('ra.refreshFailed', 'Daten konnten nicht aktualisiert werden:') + ' ' + error.message); } render(); };
  const record = async (key, status, reason) => {
   await json(`/api/lcs/${id}/review-steps/${key}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ status, reason: reason || null }) });
   recorded = await json(`/api/lcs/${id}/review-steps`); if (index < S.STEPS.length - 1) index++; render();
  };
  const sub = list => list.length ? list : [node('p', tr('ra.nothingOpen', 'Nichts offen.'), 'muted')];

  function findings(key) {
   const items = summary[key]?.items || []; const box = node('div', undefined, 'assistant-findings');
   if (!items.length) { box.append(node('p', key === 'ANGABEN' ? '' : tr('ra.hereNothing', '✓ Hier ist nichts offen.'), 'muted')); return box; }
   items.slice(0, 12).forEach(f => { const row = node('p', undefined, 'assistant-finding'); const badge = node('b', SEVERITY()[f.severity] || f.severity, 'doc-review-badge ' + String(f.severity).toLowerCase()); row.append(badge, document.createTextNode(' ' + (f.documentName ? f.documentName + ': ' : '') + (f.message || ''))); box.append(row); });
   if (items.length > 12) box.append(node('p', tr('ra.more', '… und {n} weitere im Prüfmodus.', { n: items.length - 12 }), 'muted'));
   return box;
  }
  const reviewButton = label => { const b = node('button', label || tr('ra.openReview', 'Dokumentweise prüfen öffnen'), 'secondary'); b.type = 'button'; b.onclick = () => { LcDocumentReview?.open?.(); }; return b; };
  const watchReviewDialog = () => { const d = document.querySelector('#documentReviewDialog'); if (d) d.addEventListener('close', () => reload(), { once: true }); };

  const bodies = {
   AKTE() {
    const grid = node('div', undefined, 'assistant-cards');
    [[tr('ra.ref', 'Referenz'), lc.reference], ['Applicant', lc.applicant], ['Beneficiary', lc.beneficiary], ['Issuing Bank', lc.issuingBank], [tr('ra.amount', 'Betrag'), money(lc.amount, lc.currency)], [tr('ra.expiry', 'Ablauf'), date(lc.expiryDate) + (lc.expiryPlace ? ' · ' + lc.expiryPlace : '')], [tr('ra.latestShipment', 'Spätester Versand'), date(lc.latestShipmentDate)]].forEach(([k, v]) => { const c = node('div', undefined, 'card'); c.append(node('small', k), node('b', v || '–')); grid.append(c); });
    const wrap = node('div'); wrap.append(grid, node('h4', tr('ra.required', 'Geforderte Dokumente')));
    const list = node('ul'); (lc.requiredDocuments || []).forEach(d => list.append(node('li', d))); wrap.append((lc.requiredDocuments || []).length ? list : node('p', tr('ra.noReq', 'Keine Dokumentenanforderungen erkannt.'), 'muted'));
    if (editable && can('LC_EDIT') && typeof openEdit === 'function') { const edit = node('button', tr('ak.edit', 'Bearbeiten'), 'secondary'); edit.type = 'button'; edit.onclick = () => { if (activeLc?.id !== id) return; openEdit(); }; wrap.append(edit); }
    return wrap;
   },
   VOLLSTAENDIGKEIT() {
    const wrap = node('div'); wrap.append(node('h4', tr('ra.present', 'Vorliegende Dokumente')));
    if (!documents.length) wrap.append(node('p', tr('ra.noDoc', 'Noch kein Dokument in der Akte.'), 'muted'));
    else { const table = node('table', undefined, 'compact-table'); table.append(Object.assign(node('tr'), {})); documents.forEach(d => { const r = node('tr'); [d.typeLabel || d.documentType, d.originalFilename, typeof documentCopyLabel === 'function' ? documentCopyLabel(d.copyNumber) : ''].forEach(t => r.append(node('td', t))); table.append(r); }); wrap.append(table); }
    wrap.append(findings('VOLLSTAENDIGKEIT'));
    return wrap;
   },
   ANGABEN() {
    const wrap = node('div');
    if (!missing.length) { wrap.append(node('p', tr('ra.noMissing', '✓ Es fehlen keine LC-Angaben.'), 'muted')); return wrap; }
    wrap.append(node('p', tr('ra.missingIntro', 'Diese Angaben konnte die App nicht aus dem Akkreditiv lesen. Erfassen Sie sie, oder wählen Sie „Im Akkreditiv nicht verlangt“ – dann gelten die Regeln dazu als nicht anwendbar und fallen aus dieser Liste.'), 'muted'));
    missing.forEach(m => {
     const row = node('div', undefined, 'doc-review-missing-row'); row.append(node('span', LcDocumentReview.fieldLabel(m.field) + ' · ' + (m.rules === 1 ? tr('dr.rowRuleOne', '1 Regel') : tr('dr.rowRuleMany', '{n} Regeln', { n: m.rules }))));
     if (editable && m.field === 'LC_PRESENTATION_DATE') {
      const input = document.createElement('input'); input.type = 'date'; input.value = new Date().toISOString().slice(0, 10); input.max = input.value;
      const save = node('button', tr('dr.savePresentation', 'Vorlagedatum speichern')); save.type = 'button'; const status = node('small'); status.setAttribute('role', 'status');
      save.onclick = async () => { save.disabled = true; try { await json(`/api/lcs/${id}/rule-facts/presentation-date`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ date: input.value }) }); await reload(); } catch (error) { status.textContent = error.message; save.disabled = false; } };
      row.append(input, save, status);
     } else if (editable && requirementFields.has(m.field)) {
      const note = node('small'); note.setAttribute('role', 'status');
      const proposals = requirementSuggestions.filter(x => x.field === m.field);
      proposals.forEach(x => {
       const take = node('button', tr('ra.take', '{type}: {value} aus dem Akkreditiv übernehmen', { type: x.documentTypeLabel, value: x.value === 'true' ? tr('common.yes', 'ja') : x.value }), 'secondary'); take.type = 'button';
       take.onclick = async () => { take.disabled = true; try { await json(`/api/lcs/${id}/rule-requirements/suggestions/apply`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ documentType: x.documentType, field: x.field }) }); await reload(); } catch (error) { note.textContent = error.message; take.disabled = false; } };
       row.append(take);
      });
      if (!proposals.length) row.append(node('small', tr('ra.perType', 'Je Dokumenttyp unter Prüfdaten → Anforderungen erfassen.'), 'muted'));
      const none = node('button', tr('ra.notRequired', 'Im Akkreditiv nicht verlangt'), 'secondary'); none.type = 'button';
      none.onclick = async () => { none.disabled = true; try { await json(`/api/lcs/${id}/rule-facts/waived/${encodeURIComponent(m.field)}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ waived: true }) }); await reload(); } catch (error) { note.textContent = error.message; none.disabled = false; } };
      row.append(none, note);
     } else if (editable) {
      const b = node('button', tr('dr.capture', 'In den LC-Prüfdaten erfassen'), 'secondary'); b.type = 'button'; b.dataset.ruleFacts = '';
      const none = node('button', tr('ra.notRequired', 'Im Akkreditiv nicht verlangt'), 'secondary'); none.type = 'button'; const note = node('small'); note.setAttribute('role', 'status');
      none.onclick = async () => { none.disabled = true; try { await json(`/api/lcs/${id}/rule-facts/waived/${encodeURIComponent(m.field)}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ waived: true }) }); await reload(); } catch (error) { note.textContent = error.message; none.disabled = false; } };
      row.append(b, none, note);
     }
     wrap.append(row);
    });
    return wrap;
   },
   COMPLIANCE() { const wrap = node('div'); wrap.append(findings('COMPLIANCE')); if (summary.COMPLIANCE.open) { const b = reviewButton(tr('ra.decideHits', 'Treffer im Prüfmodus entscheiden')); b.addEventListener('click', watchReviewDialog); wrap.append(b); } return wrap; },
   DOKUMENTE() {
    const wrap = node('div'); const byDoc = new Map();
    documents.forEach(d => byDoc.set(d.originalFilename, { open: 0, blocking: 0, type: d.typeLabel || d.documentType }));
    summary.DOKUMENTE.items.forEach(f => { const e = byDoc.get(f.documentName) || { open: 0, blocking: 0, type: '' }; e.open++; byDoc.set(f.documentName, e); });
    const table = node('table', undefined, 'compact-table'); byDoc.forEach((e, name) => { const r = node('tr'); [e.type, name, e.open ? tr('ra.nOpen', '{n} offen', { n: e.open }) : tr('ra.noneOpen', 'nichts offen')].forEach(t => r.append(node('td', t))); table.append(r); });
    wrap.append(byDoc.size ? table : node('p', tr('ra.noDocs', 'Keine Dokumente.'), 'muted'));
    const b = reviewButton(); b.addEventListener('click', watchReviewDialog); wrap.append(b);
    return wrap;
   },
   QUERPRUEFUNG() {
    const wrap = node('div'); const grid = node('div', undefined, 'assistant-cards');
    const days = value => { const n = S.daysUntil(value); return n === null ? '–' : n < 0 ? tr('ra.daysAgo', 'vor {n} Tag(en) abgelaufen', { n: -n }) : n === 0 ? tr('ra.today', 'heute') : tr('ra.inDays', 'in {n} Tag(en)', { n }); };
    [[tr('ra.credExpiry', 'Ablauf des Akkreditivs'), date(lc.expiryDate) + ' · ' + days(lc.expiryDate)], [tr('ra.latestShipment', 'Spätester Versand'), date(lc.latestShipmentDate) + ' · ' + days(lc.latestShipmentDate)], [tr('dr.presentationDate', 'Vorlagedatum'), presentation ? date(presentation) : tr('ra.notEntered', 'nicht erfasst')]].forEach(([k, v]) => { const c = node('div', undefined, 'card'); c.append(node('small', k), node('b', v)); grid.append(c); });
    wrap.append(grid, findings('QUERPRUEFUNG')); return wrap;
   },
   ABSCHLUSS() {
    const wrap = node('div'); const totalOpen = S.STEPS.reduce((n, s) => n + (s.key === 'ABSCHLUSS' ? 0 : summary[s.key].open), 0), totalBlocking = S.STEPS.reduce((n, s) => n + summary[s.key].blocking, 0);
    const decided = S.STEPS.reduce((n, s) => n + summary[s.key].decided, 0), discrepancies = (results || []).filter(f => f.reviewDecision === 'CONFIRMED_DISCREPANCY').length;
    const known = S.byKey(recorded); const skipped = S.STEPS.filter(s => known[s.key]?.status === 'SKIPPED');
    wrap.append(node('p', tr('ra.summary', '{decided} Befund(e) entschieden (davon {disc} Abweichung(en)), {open} offen{blocking}.', { decided, disc: discrepancies, open: totalOpen, blocking: totalBlocking ? tr('ra.blocking', ', davon {n} blockierend', { n: totalBlocking }) : '' }), totalBlocking ? 'error' : ''));
    if (skipped.length) { wrap.append(node('h4', tr('ra.skippedSteps', 'Übersprungene Schritte'))); const ul = node('ul'); skipped.forEach(s => ul.append(node('li', `${s.label}: ${known[s.key].reason} (${known[s.key].completedBy})`))); wrap.append(ul); }
    const openSteps = S.STEPS.filter(s => s.key !== 'ABSCHLUSS' && summary[s.key].open > 0);
    if (openSteps.length) { const jump = node('p', tr('ra.openIn', 'Noch offen in:') + ' '); openSteps.forEach(s => { const b = node('button', `${s.label} (${summary[s.key].open})`, 'secondary'); b.type = 'button'; b.onclick = () => { index = S.STEPS.indexOf(s); render(); }; jump.append(b, ' '); }); wrap.append(jump); }
    const actions = node('div', undefined, 'doc-review-finish-actions');
    if (discrepancies && globalThis.LcDocumentReview?.openNotice) { const b = node('button', tr('dr.notice.create', 'Unstimmigkeitsmeldung erstellen')); b.type = 'button'; b.onclick = () => LcDocumentReview.openNotice(id); actions.append(b); }
    if (can('LC_EDIT') && totalOpen === 0 && globalThis.LcReviewClosure) actions.append(LcReviewClosure.control(id, () => tr('ra.note', 'Dokumentenprüfung abgeschlossen (Prüfungsassistent): {decided} Befund(e) entschieden, {disc}{skipped}.', { decided, disc: discrepancies ? tr('ra.note.disc', '{n} Abweichung(en) bestätigt', { n: discrepancies }) : tr('ra.note.none', 'keine Abweichungen'), skipped: skipped.length ? tr('ra.note.skipped', ', {n} Schritt(e) übersprungen', { n: skipped.length }) : '' }), discrepancies ? 'secondary' : ''));
    if (can('AUDIT_VIEW')) { const a = node('a', tr('lc.export', 'Prüfakte exportieren'), 'button-link secondary'); a.href = `/api/lcs/${id}/dossier`; actions.append(a); }
    wrap.append(actions); if (totalOpen) wrap.append(node('p', tr('ra.noteBlocked', 'Der Abschlussvermerk ist erst möglich, wenn nichts mehr offen ist.'), 'muted'));
    return wrap;
   }
  };

  function render() {
   const known = S.byKey(recorded), p = S.progress(recorded), step = S.STEPS[index], entry = summary[step.key];
   const head = node('div', undefined, 'dialoghead'); head.append(node('h2', tr('ra.title', 'Prüfungsassistent') + ' · ' + lc.reference), closeButton(dialog));
   const bar = node('div', undefined, 'doc-review-progress'); const progress = document.createElement('progress'); progress.max = p.total; progress.value = p.done + p.skipped;
   bar.append(node('span', tr('ra.progress', '{n} von {total} Schritten', { n: p.done + p.skipped, total: p.total }) + (p.skipped ? ' ' + tr('ra.progressSkipped', '({n} übersprungen)', { n: p.skipped }) : '')), progress);
   const nav = node('ol', undefined, 'assistant-steps');
   S.STEPS.forEach((s, i) => {
    const r = known[s.key], open = summary[s.key].open;
    const li = node('li'); const b = node('button', undefined, 'assistant-step' + (i === index ? ' active' : '') + (r ? ' ' + r.status.toLowerCase() : '')); b.type = 'button';
    b.append(node('span', r ? (r.status === 'DONE' ? '✓' : '↷') : String(i + 1), 'assistant-mark'), node('span', s.label)); if (open && !r) b.append(node('b', String(open), 'assistant-count'));
    b.onclick = () => { index = i; render(); }; li.append(b); nav.append(li);
   });
   const content = node('section', undefined, 'assistant-content');
   content.append(node('h3', tr('ra.stepHead', 'Schritt {i} von {n}: {label}', { i: index + 1, n: S.STEPS.length, label: step.label })), node('p', step.intro));
   const rec = known[step.key];
   const openHere = step.key === 'ABSCHLUSS' ? S.STEPS.reduce((n, x) => n + (x.key === 'ABSCHLUSS' ? 0 : summary[x.key].open), 0) : summary[step.key].open;
   if (rec) { const line = node('p', rec.status === 'DONE' ? (openHere > 0 ? tr('ra.staleDone', '⚠ Erledigt von {who} am {when} – seitdem sind wieder {n} Punkt(e) offen. Bitte den Schritt wieder öffnen.', { who: rec.completedBy, when: stamp(rec.completedAt), n: openHere }) : tr('ra.done', '✓ Erledigt von {who} am {when}', { who: rec.completedBy, when: stamp(rec.completedAt) })) : tr('ra.skipped', '↷ Übersprungen von {who} am {when}: {reason}', { who: rec.completedBy, when: stamp(rec.completedAt), reason: rec.reason }), 'assistant-recorded'); if (editable) { const reopen = node('button', tr('ra.reopen', 'Wieder öffnen'), 'secondary'); reopen.type = 'button'; reopen.onclick = async () => { await json(`/api/lcs/${id}/review-steps/${step.key}`, { method: 'DELETE' }); recorded = await json(`/api/lcs/${id}/review-steps`); render(); }; line.append(' ', reopen); } content.append(line); }
   content.append(bodies[step.key]());
   if (!['AKTE', 'ANGABEN', 'VOLLSTAENDIGKEIT', 'DOKUMENTE', 'QUERPRUEFUNG', 'COMPLIANCE', 'ABSCHLUSS'].includes(step.key)) content.append(findings(step.key));
   const foot = node('div', undefined, 'assistant-footer');
   const back = node('button', tr('ra.back', '← Zurück'), 'secondary'); back.type = 'button'; back.disabled = index === 0; back.onclick = () => { index--; render(); };
   const next = node('button', tr('ra.next', 'Weiter →'), 'secondary'); next.type = 'button'; next.disabled = index === S.STEPS.length - 1; next.onclick = () => { index++; render(); };
   foot.append(back);
   if (editable) {
    const okOk = S.canComplete(entry); const done = node('button', tr('ra.stepDone', 'Schritt erledigt ✓')); done.type = 'button'; done.disabled = !okOk; if (!okOk) done.title = tr('ra.stepBlocked', '{n} offen: erst entscheiden oder den Schritt mit Begründung überspringen.', { n: entry.open });
    done.onclick = async () => { done.disabled = true; try { await record(step.key, 'DONE'); } catch (error) { alert(error.message); done.disabled = false; } };
    const skip = node('button', tr('ra.skip', 'Überspringen …'), 'secondary'); skip.type = 'button';
    skip.onclick = async () => {
     const reason = globalThis.LcReviewNote ? await LcReviewNote.ask({ title: tr('ra.skipTitle', 'Schritt „{label}“ überspringen', { label: step.label }), text: tr('ra.skipText', 'Begründung (Pflicht, wird in der Akte vermerkt):'), decision: 'ACCEPTED', required: true }) : prompt(tr('ra.skipPrompt', 'Begründung zum Überspringen:'));
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
 function closeButton(dialog) { const b = node('button', tr('common.close', 'Schließen')); b.type = 'button'; b.setAttribute('data-close', ''); b.onclick = () => dialog.close(); return b; }
 globalThis.LcReviewAssistant = { open };
 document.addEventListener('click', event => { if (event.target.closest?.('[data-review-assistant]')) open(); });
})();
