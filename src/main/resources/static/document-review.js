/* Document-wise review: one dialog per document of the LC file. The PDF stays on the right, all findings of that document are listed on the left and can be
   confirmed one after the other (with a marked evidence instead of a comment); after the last open finding the dialog moves on to the next document. */
(() => {
 const english = () => globalThis.LcI18n?.language?.() === 'en';
 const tr = (key, de, values = {}) => { const i = globalThis.LcI18n, s = i?.t ? i.t(key, values) : null; return s && s !== key ? s : String(de).replace(/\{(\w+)\}/g, (m, k) => values[k] ?? m); };
 const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
 // REVIEW_STALE only says that an earlier decision no longer applies; it cannot be decided itself (the original finding next to it is the one to confirm again).
 const isOpen = finding => !finding.reviewDecision && finding.severity !== 'OK' && finding.code !== 'REVIEW_STALE';
 const SEVERITY = () => ({ DISCREPANCY: tr('dr.sev.disc', 'Abweichung'), WARNING: tr('dr.sev.warn', 'Hinweis'), OK: tr('dr.sev.ok', 'in Ordnung') });
 // Amount, expiry and presentation period, shipment date, missing document and signature stop a presentation; they are reviewed first.
 const BLOCKING = /SANCTION|EMBARGO|EXPORT_CONTROL|AMOUNT|EXPIR|PRESENTATION_PERIOD|PRESENTATION_BEFORE|SHIPMENT_DATE|LATEST_SHIPMENT|MISSING_DOCUMENT|SIGNATURE/;
 const SANCTIONS = /^SANCTIONS_(?!SCREENED|NOT_SCREENED|CLEARED)/;
 /** A sanctions hit noted as false positive by one person and waiting for the approval of a second one. */
 const isPendingApproval = finding => isOpen(finding) && SANCTIONS.test(finding.code || '') && !!finding.reviewedBy;
 const TIERS = () => [['blocking', tr('dr.tier.blocking', 'Blockierend')], ['check', tr('dr.tier.check', 'Prüfen')], ['hint', tr('dr.tier.hint', 'Hinweise')], ['done', tr('dr.tier.done', 'Erledigt & in Ordnung')]];
 /** Tier of a finding: blocking, check (other discrepancies), hint (warnings) or done (decided or OK). */
 function tierOf(finding) {
  if (!isOpen(finding)) return 'done';
  if (BLOCKING.test(String(finding.code || ''))) return 'blocking';
  return finding.severity === 'DISCREPANCY' ? 'check' : 'hint';
 }
 /** LC field a rule could not be checked for because the LC data is missing ("LC_PRESENTATION_DATE = nicht erfasst"), or null. Such findings are collected once per field instead of per document. */
 function missingField(finding) {
  if (!isOpen(finding) || finding.severity !== 'WARNING' || !/^Regel nicht prüfbar/.test(finding.message || '')) return null;
  return /\b(LC_[A-Z_]+) = nicht erfasst/.exec(finding.lcCondition || '')?.[1] || null;
 }
 /** Missing LC data with the number of rules and documents waiting for it, most rules first. */
 function missingInputs(results) {
  const byField = new Map();
  (results || []).forEach(f => { const field = missingField(f); if (!field) return; const e = byField.get(field) || { field, rules: 0, documents: new Set() }; e.rules++; if (f.documentName) e.documents.add(f.documentName); byField.set(field, e); });
  return [...byField.values()].sort((a, b) => b.rules - a.rules);
 }
 const FIELD_LABEL = { LC_PRESENTATION_DATE: 'Vorlagedatum', LC_PRESENTATION_PERIOD_DAYS: 'Vorlagefrist (Tage)', LC_INSURANCE_MIN_PERCENT: 'Mindestdeckung Versicherung (%)', LC_TOLERANCE_PERCENT: 'Toleranz (%)' };
 let LOADED_LABELS = {};
 const setFieldLabels = map => { LOADED_LABELS = { ...LOADED_LABELS, ...(map || {}) }; };
 /** Readable fallback from the field code ("LC_REQUIRED_ORIGINAL_COUNT" -> "required original count"); the server labels are German only, so English shows this. */
 const humanField = field => field.replace(/^LC_/, '').toLowerCase().replace(/_/g, ' ');
 const fieldLabel = field => (globalThis.LcI18n?.language?.() === 'en' ? null : LOADED_LABELS[field] || FIELD_LABEL[field]) || humanField(field);
 const TIER_RANK = { blocking: 0, check: 1, hint: 2, done: 3 };
 /** Open and decidable findings over all documents, for the progress bar. */
 function progress(groups) {
  let open = 0, total = 0;
  (groups || []).forEach(g => g.items.forEach(f => { if (f.code === 'REVIEW_STALE' || f.severity === 'OK' && !f.reviewDecision) return; total++; if (isOpen(f)) open++; }));
  return { open, done: total - open, total };
 }

 /** State of the whole presentation: documents finished, decisions taken, and whether the review is complete (nothing open, no LC data missing). */
 function summary(groups, missing = []) {
  let accepted = 0, discrepancies = 0;
  (groups || []).forEach(g => g.items.forEach(f => { if (f.reviewDecision === 'ACCEPTED') accepted++; else if (f.reviewDecision === 'CONFIRMED_DISCREPANCY') discrepancies++; }));
  const docs = (groups || []).filter(g => g.name).length, docsDone = (groups || []).filter(g => g.name && g.openCount === 0).length, open = progress(groups).open;
  return { docs, docsDone, accepted, discrepancies, open, complete: open === 0 && !(missing || []).length };
 }
 /** Findings grouped by document in the order of the document list (findings without a single document last); open ones first, discrepancies before warnings. Documents with nothing to look at are left out. */
 function groupFindings(results, documents) {
  const docs = documents || [], order = new Map(docs.map((d, i) => [d.originalFilename, i]));
  const rank = f => TIER_RANK[tierOf(f)];
  const groups = new Map();
  (results || []).filter(f => !missingField(f)).forEach(f => { const key = f.documentName || ''; if (!groups.has(key)) groups.set(key, []); groups.get(key).push(f); });
  return [...groups.entries()]
   .filter(([, items]) => items.some(f => f.severity !== 'OK' || f.reviewDecision))
   .map(([name, items]) => { const doc = docs.find(d => d.originalFilename === name); return { name, documentId: doc?.id || null, contentType: doc?.contentType || null, items: [...items].sort((a, b) => rank(a) - rank(b)), openCount: items.filter(isOpen).length, blockingCount: items.filter(f => tierOf(f) === 'blocking').length }; })
   .sort((a, b) => (a.name === '' ? 1 : 0) - (b.name === '' ? 1 : 0) || (order.has(a.name) ? order.get(a.name) : 1e6) - (order.has(b.name) ? order.get(b.name) : 1e6));
 }
 /** Index of the next open finding after `from` (no wrap), -1 when none is left. */
 function nextOpen(items, from = -1) { for (let i = from + 1; i < items.length; i++) if (isOpen(items[i])) return i; return -1; }
 /** Index of the next document group with open findings after `from` (no wrap), -1 when none is left. */
 function nextGroupWithOpen(groups, from = -1) { for (let i = from + 1; i < groups.length; i++) if (groups[i].openCount > 0) return i; return -1; }
 /** Open findings with the same rule in other documents: one cause, several places. A finding without a single document is never bundled. */
 function siblingsOf(groups, finding) {
  if (!finding?.documentName) return [];
  const out = [];
  (groups || []).forEach(g => g.items.forEach(f => { if (f !== finding && f.code === finding.code && f.documentName && f.documentName !== finding.documentName && isOpen(f)) out.push(f); }));
  return out;
 }
 globalThis.LcDocumentReview = { fieldLabel, setFieldLabels, summary, missingField, missingInputs, isPendingApproval, groupFindings, nextOpen, nextGroupWithOpen, isOpen, tierOf, progress, siblingsOf };
 if (typeof document === 'undefined' || !document.body) return;

 /** Draft notice of discrepancies (MT734 and letter) from the confirmed discrepancies; editable and copyable, nothing is sent. */
 async function openNotice(lcId) {
  const box = document.createElement('dialog'); box.className = 'wide-dialog notice-dialog'; document.body.append(box); box.onclose = () => box.remove();
  box.innerHTML = '<div class="dialoghead"><h2>' + tr('dr.notice.title', 'Unstimmigkeitsmeldung (Entwurf)') + '</h2><button type="button" data-close>' + tr('common.close', 'Schließen') + '</button></div><p role="status">' + tr('dr.notice.creating', 'Wird erstellt …') + '</p>';
  box.querySelector('[data-close]').onclick = () => box.close(); box.showModal();
  try {
   const draft = await json(`/api/lcs/${lcId}/document-checks/discrepancy-notice`);
   if (!draft.discrepancies) { box.querySelector('[role=status]').textContent = tr('dr.notice.none', 'Noch keine bestätigten Abweichungen. Im Prüfmodus „Abweichung bestätigen“ wählen; nur bestätigte Abweichungen kommen in die Meldung.'); return; }
   box.querySelector('[role=status]').textContent = draft.discrepancies === 1 ? tr('dr.notice.readyOne', '1 bestätigte Abweichung. Entwurf prüfen, ergänzen (z. B. :21: Referenz der Einreicherbank, Betrag) und selbst versenden.') : tr('dr.notice.readyMany', '{n} bestätigte Abweichungen. Entwurf prüfen, ergänzen (z. B. :21: Referenz der Einreicherbank, Betrag) und selbst versenden.', { n: draft.discrepancies });
   for (const [key, title] of [['mt734', 'SWIFT MT734 (Advice of Refusal)'], ['letter', tr('dr.notice.letter', 'Schreiben an die Bank')]]) {
    const section = document.createElement('section'); section.className = 'notice-part';
    const head = document.createElement('h3'); head.textContent = title;
    const area = document.createElement('textarea'); area.rows = key === 'mt734' ? 12 : 14; area.value = draft[key]; area.spellcheck = false;
    const copy = document.createElement('button'); copy.type = 'button'; copy.className = 'secondary'; copy.textContent = tr('dr.copy', 'Kopieren');
    copy.onclick = async () => { try { await navigator.clipboard.writeText(area.value); copy.textContent = tr('dr.copied', 'Kopiert ✓'); } catch { area.select(); copy.textContent = tr('dr.copyManual', 'Markiert – mit Strg/Cmd+C kopieren'); } };
    section.append(head, area, copy); box.append(section);
   }
  } catch (error) { box.querySelector('[role=status]').textContent = tr('dr.notice.unavailable', 'Entwurf nicht verfügbar:') + ' ' + error.message; }
 }
 async function open() {
  const lcId = activeLc?.id; if (!lcId) return;
  let dialog = document.querySelector('#documentReviewDialog');
  if (!dialog) { dialog = document.createElement('dialog'); dialog.id = 'documentReviewDialog'; dialog.className = 'wide-dialog'; document.body.append(dialog); }
  dialog.onclose?.(); dialog.onclose = null;
  dialog.innerHTML = '<p>' + tr('dr.loading', 'Prüfung wird geladen …') + '</p><button type="button" data-close>' + tr('common.close', 'Schließen') + '</button>';
  dialog.querySelector('[data-close]').onclick = () => dialog.close(); dialog.showModal();
  let groups, missing = [], gi = 0, active = -1, busy = false;
  const load = async () => {
   if (!Object.keys(LOADED_LABELS).length) { const defs = await json(`/api/lcs/${lcId}/rule-facts/definitions`).catch(() => null); if (defs?.lc) setFieldLabels(Object.fromEntries([...defs.lc, ...(defs.requirements || [])].map(d => [d.field, d.label]))); }
   const [review, documents] = await Promise.all([json(`/api/lcs/${lcId}/document-checks`), json(`/api/lcs/${lcId}/documents`).catch(() => [])]);
   groups = groupFindings(review.results, documents); missing = missingInputs(review.results);
  };
  try { await load(); } catch (error) { dialog.querySelector('p').textContent = 'Prüfung konnte nicht geladen werden: ' + error.message; return; }
  if (!groups.length && !missing.length) { dialog.querySelector('p').textContent = 'Keine Befunde zu Dokumenten vorhanden.'; return; }
  gi = Math.max(0, nextGroupWithOpen(groups)); 
  dialog.innerHTML = '<div class="dialoghead"><h2>' + tr('dr.title', 'Dokumentweise Prüfung') + '</h2><div class="doc-review-nav"><button type="button" class="secondary" data-doc-prev aria-label="' + tr('dr.prev', 'Vorheriges Dokument') + '">←</button><b data-doc-label></b><button type="button" class="secondary" data-doc-next aria-label="' + tr('dr.next', 'Nächstes Dokument') + '">→</button></div><button type="button" data-close>' + tr('common.close', 'Schließen') + '</button></div>' +
   '<div class="doc-review-progress"><span data-doc-progress></span><progress data-doc-progress-bar max="1" value="0"></progress><button type="button" class="secondary" data-notice>Unstimmigkeitsmeldung (Entwurf)</button></div><section class="doc-review-finish" data-doc-finish hidden></section><section class="doc-review-missing" data-doc-missing hidden></section><nav class="doc-review-strip" data-doc-strip aria-label="Dokumente"></nav>' +
   '<div class="evidence-workspace"><section class="evidence-details">' + (can('DOCUMENT_UPLOAD') ? '<p class="doc-review-bar"><button type="button" class="secondary" data-doc-apply>' + tr('dr.apply', 'Erkannte Prüfdaten übernehmen') + '</button> <small data-doc-apply-status role="status"></small></p>' : '') + '<div data-doc-findings></div></section><section class="evidence-document" data-pdf-pages></section></div>' +
   '<p class="doc-review-keys"><kbd>J</kbd> ' + tr('dr.key.ok', 'erfüllt') + ' · <kbd>N</kbd> ' + tr('dr.key.disc', 'Abweichung') + ' · <kbd>↑</kbd><kbd>↓</kbd> ' + tr('dr.key.finding', 'Befund') + ' · <kbd>←</kbd><kbd>→</kbd> ' + tr('dr.key.doc', 'Dokument') + '</p>';
  dialog.querySelector('[data-close]').onclick = () => dialog.close();
  const list = dialog.querySelector('[data-doc-findings]'), viewer = dialog.querySelector('[data-pdf-pages]'), label = dialog.querySelector('[data-doc-label]');
  const prev = dialog.querySelector('[data-doc-prev]'), next = dialog.querySelector('[data-doc-next]');
  const strip = dialog.querySelector('[data-doc-strip]'), progressText = dialog.querySelector('[data-doc-progress]'), progressBar = dialog.querySelector('[data-doc-progress-bar]');
  const openTiers = new Set(['blocking', 'check']);
  const missingBox = dialog.querySelector('[data-doc-missing]');
  /** Reloads after LC data was entered: rules waiting for it are evaluated again. */
  async function refreshAfterFacts() {
   const name = groups[gi]?.name; await load(); renderMissing();
   if (!groups.length) { list.textContent = missing.length ? tr('dr.noMoreMissing', 'Keine weiteren Befunde – zuerst die fehlenden Angaben oben erfassen.') : tr('dr.allDone', '✓ Alle Befunde sind erledigt.'); return; }
   const at = groups.findIndex(g => g.name === name); await showGroup(at >= 0 ? at : Math.max(0, nextGroupWithOpen(groups)));
  }
  const finishBox = dialog.querySelector('[data-doc-finish]');
  let finished = false;
  /** When nothing is open any more: the result of the presentation and the next step (notice of discrepancies or a closing note in the file). */
  function renderFinish(sum) {
   finishBox.hidden = !sum.complete; if (!sum.complete || finishBox.dataset.key === `${sum.accepted}/${sum.discrepancies}/${finished}`) return;
   finishBox.dataset.key = `${sum.accepted}/${sum.discrepancies}/${finished}`; finishBox.replaceChildren();
   const head = document.createElement('p');
   head.textContent = sum.discrepancies ? tr('dr.fin.disc', '✓ Alle Befunde entschieden: {a} erfüllt, {d} Unstimmigkeit(en) bestätigt. Nächster Schritt: Unstimmigkeitsmeldung.', { a: sum.accepted, d: sum.discrepancies }) : tr('dr.fin.clean', '✓ Alle Befunde entschieden: {a} erfüllt, keine Unstimmigkeiten. Die Dokumente können als konform behandelt werden.', { a: sum.accepted });
   finishBox.classList.toggle('with-discrepancies', sum.discrepancies > 0); finishBox.append(head);
   const actions = document.createElement('div'); actions.className = 'doc-review-finish-actions';
   if (sum.discrepancies) { const notice = document.createElement('button'); notice.type = 'button'; notice.textContent = tr('dr.notice.create', 'Unstimmigkeitsmeldung erstellen'); notice.onclick = () => openNotice(lcId); actions.append(notice); }
   if (can('LC_EDIT')) {
    const close = document.createElement('button'); close.type = 'button'; close.className = sum.discrepancies ? 'secondary' : ''; close.disabled = finished;
    close.textContent = finished ? tr('dr.closed', 'Abschluss vermerkt ✓') : tr('dr.close', 'Prüfung abschließen (Vermerk in der Akte)');
    close.onclick = async () => {
     const text = tr('dr.note', 'Dokumentenprüfung abgeschlossen: {docs} Dokument(e), {a} Befund(e) als erfüllt bestätigt, {disc}.', { docs: sum.docs, a: sum.accepted, disc: sum.discrepancies ? tr('dr.note.disc', '{d} Unstimmigkeit(en) bestätigt', { d: sum.discrepancies }) : tr('dr.note.none', 'keine Unstimmigkeiten') });
     close.disabled = true;
     try { await json(`/api/lcs/${lcId}/notes`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ content: text }) }); finished = true; renderFinish(summary(groups, missing)); }
     catch (error) { close.disabled = false; alert(tr('dr.noteFailed', 'Vermerk konnte nicht gespeichert werden:') + ' ' + error.message); }
    };
    actions.append(close);
   }
   if (can('AUDIT_VIEW')) { const dossier = document.createElement('a'); dossier.className = 'button-link secondary'; dossier.href = `/api/lcs/${lcId}/dossier`; dossier.textContent = 'Prüfakte exportieren'; actions.append(dossier); }
   finishBox.append(actions);
  }
  function renderMissing() {
   missingBox.hidden = !missing.length; missingBox.replaceChildren(); if (!missing.length) return;
   const rules = missing.reduce((n, m) => n + m.rules, 0);
   const head = document.createElement('p'); head.innerHTML = '<b>' + tr('dr.missing.title', 'Fehlende LC-Angaben') + '</b> – ' + (rules === 1 ? tr('dr.missing.textOne', '1 Regel kann erst geprüft werden, wenn diese Angaben erfasst sind. Sie stehen deshalb nicht einzeln bei den Dokumenten.') : tr('dr.missing.textMany', '{n} Regeln können erst geprüft werden, wenn diese Angaben erfasst sind. Sie stehen deshalb nicht einzeln bei den Dokumenten.', { n: rules })); missingBox.append(head);
   const editable = can('LC_EDIT');
   const others = missing.filter(m => !(editable && m.field === 'LC_PRESENTATION_DATE'));
   if (editable && others.length) { const capture = document.createElement('button'); capture.type = 'button'; capture.className = 'secondary'; capture.dataset.ruleFacts = ''; capture.textContent = tr('dr.capture', 'In den LC-Prüfdaten erfassen'); head.append(' ', capture); }
   const list = document.createElement('details'); list.className = 'doc-review-missing-list';
   const summaryLine = document.createElement('summary'); summaryLine.textContent = others.length === 1 ? tr('dr.showOne', '1 fehlende Angabe anzeigen') : tr('dr.showMany', '{n} fehlende Angaben anzeigen', { n: others.length });
   if (others.length) { list.append(summaryLine); if (others.length <= 3) list.open = true; }
   missing.forEach(m => {
    const row = document.createElement('div'); row.className = 'doc-review-missing-row';
    const text = document.createElement('span'); text.textContent = fieldLabel(m.field) + ' · ' + (m.rules === 1 ? tr('dr.rowRuleOne', '1 Regel') : tr('dr.rowRuleMany', '{n} Regeln', { n: m.rules })) + (m.documents.size ? ', ' + (m.documents.size === 1 ? tr('dr.rowDocOne', '1 Dokument') : tr('dr.rowDocMany', '{n} Dokumente', { n: m.documents.size })) : ''); row.append(text);
    if (editable && m.field === 'LC_PRESENTATION_DATE') {
     const input = document.createElement('input'); input.type = 'date'; input.value = new Date().toISOString().slice(0, 10); input.max = input.value; input.setAttribute('aria-label', tr('dr.presentationDate', 'Vorlagedatum'));
     const save = document.createElement('button'); save.type = 'button'; save.textContent = tr('dr.savePresentation', 'Vorlagedatum speichern');
     const status = document.createElement('small'); status.setAttribute('role', 'status');
     save.onclick = async () => {
      if (!input.value) { status.textContent = tr('dr.pickDate', 'Bitte ein Datum wählen.'); return; }
      save.disabled = true; status.textContent = tr('dr.saving', 'Wird gespeichert …');
      try { await json(`/api/lcs/${lcId}/rule-facts/presentation-date`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ date: input.value }) }); await refreshAfterFacts(); }
      catch (error) { status.textContent = tr('dr.notSaved', 'Nicht gespeichert:') + ' ' + error.message; save.disabled = false; }
     };
     row.append(input, save, status);
    }
    (editable && m.field === 'LC_PRESENTATION_DATE' ? missingBox : list).append(row);
   });
   if (others.length) missingBox.append(list);
  }
  const onFactsSaved = () => { if (dialog.open) refreshAfterFacts().catch(() => {}); };
  document.addEventListener('lc-rule-facts-saved', onFactsSaved); dialog.onclose = () => document.removeEventListener('lc-rule-facts-saved', onFactsSaved);
  renderMissing();
  function renderOverview() {
   const p = progress(groups);
   const sum = summary(groups, missing);
   progressText.textContent = `${p.done} / ${p.total} Befunde erledigt · ${sum.docsDone} / ${sum.docs} Dokumente fertig`;
   renderFinish(sum); progressBar.max = Math.max(1, p.total); progressBar.value = p.done;
   strip.replaceChildren(...groups.map((g, i) => {
    const b = document.createElement('button'); b.type = 'button'; b.className = 'doc-review-chip' + (i === gi ? ' active' : '') + (g.openCount === 0 ? ' done' : '') + (g.blockingCount ? ' blocking' : '');
    const name = document.createElement('span'); name.textContent = g.name || 'Dokumentensatz'; const count = document.createElement('b'); count.textContent = g.openCount === 0 ? '✓' : g.openCount + (g.blockingCount ? ' !' : ''); b.append(name, count);
    b.title = g.openCount ? `${g.openCount} offen, davon ${g.blockingCount} blockierend` : 'erledigt';
    b.disabled = busy; b.onclick = () => i !== gi && showGroup(i); return b;
   }));
  }

  async function showEvidencePage(finding) {
   if (!groups[gi]?.documentId) return;
   try {
    const params = new URLSearchParams({ code: finding.code }); if (finding.documentName) params.set('documentName', finding.documentName); if (finding.reviewFingerprint) params.set('reviewFingerprint', finding.reviewFingerprint);
    const view = await json(`/api/lcs/${lcId}/document-checks/evidence?${params}`);
    const page = (view.location?.status === 'MATCH' || view.location?.status === 'AMBIGUOUS') ? view.location.pages?.[0] : null;
    dialog.showEvidenceLines?.(page ? view.location.boxes : []);
    if (page && dialog.open) dialog.showPdfPage?.(page);
   } catch (ignored) { /* the page jump is a convenience only */ }
  }
  // Red ticks on the PDF for every decided finding of the shown document: at the line the evidence was found on, else at the marked area.
  const checkCache = new Map();
  async function loadCheckmarks(group) {
   if (!group?.documentId || !dialog.setCheckmarks) return;
   const decided = group.items.filter(f => f.reviewDecision && f.code !== 'REVIEW_STALE' && f.documentName).slice(0, 40), marks = [];
   await Promise.all(decided.map(async f => {
    const key = `${f.code}|${f.documentName}|${f.reviewFingerprint || ''}`;
    let boxes = checkCache.get(key);
    if (!boxes) {
     boxes = [];
     try {
      const params = new URLSearchParams({ code: f.code, documentName: f.documentName }); if (f.reviewFingerprint) params.set('reviewFingerprint', f.reviewFingerprint);
      const view = await json(`/api/lcs/${lcId}/document-checks/evidence?${params}`);
      const located = (view.location?.status === 'MATCH' || view.location?.status === 'AMBIGUOUS') ? view.location.boxes || [] : [];
      const drawn = typeof parseMark === 'function' ? parseMark(view.evidenceMark) : null;
      boxes = located.length ? located : drawn ? [drawn] : [];
     } catch (ignored) { /* the tick is a convenience only */ }
     checkCache.set(key, boxes);
    }
    boxes.forEach(b => marks.push({ page: b.page, x: b.x, y: b.y, w: b.w, h: b.h, kind: f.reviewDecision === 'CONFIRMED_DISCREPANCY' ? 'discrepancy' : 'ok', title: `${f.reviewDecision === 'CONFIRMED_DISCREPANCY' ? tr('dr.markDisc', 'Abweichung bestätigt') : tr('dr.markOk', 'Geprüft')}${f.reviewedBy ? ' · ' + f.reviewedBy : ''}` }));
   }));
   if (groups[gi] === group) dialog.setCheckmarks?.(marks);
  }
  function renderList() {
   const group = groups[gi];
   renderOverview();
   label.textContent = tr('dr.docLabel', 'Dokument {i} von {n}: {name} · {open} offen', { i: gi + 1, n: groups.length, name: group.name || tr('dr.docSet', 'Dokumentensatz (ohne einzelnes Dokument)'), open: group.openCount });
   prev.disabled = gi === 0 || busy; next.disabled = gi >= groups.length - 1 || busy;
   list.replaceChildren();
   if (group.openCount === 0) { const done = document.createElement('p'); done.className = 'doc-review-done'; done.textContent = '✓ Alle Befunde zu diesem Dokument sind erledigt.'; list.append(done); }
   const sections = {};
   TIERS().forEach(([key, title]) => {
    const count = group.items.filter(f => tierOf(f) === key).length; if (!count) return;
    const box = document.createElement('details'); box.className = 'doc-review-tier tier-' + key; box.dataset.tier = key;
    box.open = openTiers.has(key) || (active >= 0 && tierOf(group.items[active]) === key);
    box.ontoggle = () => { if (box.open) openTiers.add(key); else openTiers.delete(key); };
    const summary = document.createElement('summary'); summary.textContent = `${title} (${count})`; box.append(summary);
    sections[key] = box; list.append(box);
   });
   group.items.forEach((finding, index) => {
    const card = document.createElement('article'); card.className = 'doc-review-finding' + (index === active ? ' active' : '') + (isOpen(finding) ? '' : ' decided'); card.dataset.index = index;
    const head = document.createElement('p'); head.className = 'doc-review-head';
    const badge = document.createElement('b'); badge.className = 'doc-review-badge ' + String(finding.severity || '').toLowerCase(); badge.textContent = SEVERITY()[finding.severity] || finding.severity;
    head.append(badge, document.createTextNode(' ' + ((english() && finding.messageEn) || finding.message || ''))); card.append(head);
    if (finding.lcCondition) { const c = document.createElement('small'); c.textContent = 'LC-Bedingung: ' + finding.lcCondition; card.append(c); }
    if (finding.advice) { const a = document.createElement('p'); a.className = 'doc-review-advice'; a.textContent = english() ? 'How to fix: ' + (finding.adviceEn || finding.advice) : 'So beheben: ' + finding.advice; card.append(a); }
    if (finding.explanation) { const e = document.createElement('p'); e.className = 'doc-review-explanation'; e.textContent = 'Rechnung: ' + finding.explanation; card.append(e); }
    if (finding.documentEvidence) { const details = document.createElement('details'); details.className = 'doc-review-details'; const summary = document.createElement('summary'); summary.textContent = tr('dr.details', 'Regeldetails anzeigen'); const q = document.createElement('blockquote'); q.textContent = globalThis.readableEvidence ? readableEvidence(finding.documentEvidence) : finding.documentEvidence; details.append(summary, q); card.append(details); }
    if (isOpen(finding)) {
     const actions = document.createElement('div'); actions.className = 'doc-review-actions';
     const pending = isPendingApproval(finding);
     if (pending) { const note = document.createElement('small'); note.className = 'doc-review-pending'; note.textContent = `${finding.reviewComment} (${tr('dr.notedBy', 'vorgemerkt von {name}', { name: finding.reviewedBy })})`; card.append(note); }
     [['ACCEPTED', pending ? tr('dr.btn.release', 'Falsch-positiv freigeben (Vier-Augen)') : SANCTIONS.test(finding.code || '') ? tr('dr.btn.noteFp', 'Als Falsch-positiv vormerken') : tr('dr.btn.accept', 'Als erfüllt bestätigen')], ['CONFIRMED_DISCREPANCY', SANCTIONS.test(finding.code || '') ? tr('dr.btn.hit', 'Treffer bestätigen') : tr('dr.btn.disc', 'Abweichung bestätigen')]].forEach(([decision, text]) => {
      const button = document.createElement('button'); button.type = 'button'; button.className = decision === 'ACCEPTED' ? '' : 'secondary'; button.textContent = text; button.disabled = busy;
      button.onclick = event => { event.stopPropagation(); decide(index, decision); }; actions.append(button);
     });
     const missingLc = /\b(LC_[A-Z_]+) = nicht erfasst/.exec(finding.lcCondition || '');
     if (missingLc) { const facts = document.createElement('button'); facts.type = 'button'; facts.className = 'secondary'; facts.dataset.ruleFacts = ''; facts.textContent = missingLc[1] === 'LC_PRESENTATION_DATE' ? tr('dr.enterPresentation', 'Vorlagedatum erfassen') : tr('dr.enterMissing', 'Fehlende LC-Prüfdaten erfassen'); actions.append(facts); }
     const hint = document.createElement('small'); hint.textContent = tr('dr.markHint', 'Fundstelle rechts im PDF markieren statt Kommentar schreiben.'); card.append(actions, hint);
     const siblings = siblingsOf(groups, finding);
     if (siblings.length) {
      const also = document.createElement('div'); also.className = 'doc-review-also';
      const names = [...new Set(siblings.map(f => f.documentName))];
      const text = document.createElement('small'); text.textContent = `Gleicher Befund auch in: ${names.join(', ')}`;
      const all = document.createElement('button'); all.type = 'button'; all.className = 'secondary'; all.disabled = busy; all.textContent = tr('dr.allAccept', 'Für alle {n} als erfüllt bestätigen', { n: siblings.length + 1 });
      all.onclick = event => { event.stopPropagation(); decideAll(index, 'ACCEPTED'); };
      const allDiscrepancy = document.createElement('button'); allDiscrepancy.type = 'button'; allDiscrepancy.className = 'secondary'; allDiscrepancy.disabled = busy; allDiscrepancy.textContent = tr('dr.allDisc', 'Für alle {n} Abweichung bestätigen', { n: siblings.length + 1 });
      allDiscrepancy.onclick = event => { event.stopPropagation(); decideAll(index, 'CONFIRMED_DISCREPANCY'); };
      also.append(text, all, allDiscrepancy); card.append(also);
     }
    } else if (finding.code === 'REVIEW_STALE') {
     const note = document.createElement('small'); note.textContent = tr('dr.stale', 'Eine frühere Entscheidung gilt nicht mehr (Regel oder Vergleichsgrundlage haben sich geändert). Bitte den ursprünglichen Befund dieses Dokuments erneut bestätigen; dieser Hinweis verschwindet dann von selbst.'); card.append(note);
    } else {
     const done = document.createElement('small'); done.className = 'doc-review-doneline';
     done.textContent = `✓ ${finding.reviewDecision === 'ACCEPTED' ? 'als erfüllt bestätigt' : finding.reviewDecision === 'CONFIRMED_DISCREPANCY' ? 'Abweichung bestätigt' : 'in Ordnung'}${finding.reviewedBy ? ' · ' + finding.reviewedBy : ''}`; card.append(done);
    }
    card.onclick = () => select(index); sections[tierOf(finding)].append(card);
   });
   loadCheckmarks(group);
  }
  function select(index) { active = index; dialog.clearEvidenceMark?.(); renderList(); list.querySelector('.doc-review-finding.active')?.scrollIntoView?.({ block: 'nearest' }); const f = groups[gi].items[index]; if (f) showEvidencePage(f); }
  async function showGroup(index, keepSelection = false) {
   gi = index; if (!keepSelection) active = nextOpen(groups[gi].items);
   dialog.disposePdfViewer?.(); viewer.replaceChildren();
   renderList();
   const group = groups[gi];
   if (group.documentId && group.contentType === 'application/pdf') {
    const first = active >= 0 ? group.items[active] : null;
    await renderFindingPdf(dialog, viewer, group.documentId, 1, [], null);
    loadCheckmarks(group);
    if (first) showEvidencePage(first);
   } else { const none = document.createElement('p'); none.textContent = group.documentId ? tr('dr.notPdf', 'Dieses Dokument ist kein PDF und kann hier nicht angezeigt werden.') : tr('dr.setFindings', 'Diese Befunde betreffen den Dokumentensatz, nicht ein einzelnes Dokument.'); viewer.append(none); }
  }
  async function decide(index, decision) {
   if (busy) return; busy = true; const finding = groups[gi].items[index], name = groups[gi].name, mark = dialog.getEvidenceMark?.() || null; renderList();
   try {
    await decideCheck(finding.code, finding.documentName || '', decision, finding.reviewFingerprint, mark);
    await load();
    dialog.clearEvidenceMark?.();
    const at = groups.findIndex(g => g.name === name);
    if (at < 0) { const n = nextGroupWithOpen(groups); if (n >= 0) await showGroup(n); else { list.textContent = tr('dr.allDone', '✓ Alle Befunde sind erledigt.'); } return; }
    gi = at;
    const nextIndex = nextOpen(groups[gi].items);
    if (nextIndex >= 0) { active = nextIndex; renderList(); showEvidencePage(groups[gi].items[nextIndex]); }
    else { const n = nextGroupWithOpen(groups, gi); if (n >= 0) await showGroup(n); else { active = -1; renderList(); } }
   } catch (error) { alert(tr('dr.decisionFailed', 'Entscheidung konnte nicht gespeichert werden:') + ' ' + error.message); }
   finally { busy = false; if (dialog.open) renderList(); }
  }
  /** One decision for the finding and the same open finding in the other documents. The marked evidence belongs to the current document only, so the others need a written reason. */
  async function decideAll(index, decision) {
   if (busy) return; const finding = groups[gi].items[index], siblings = siblingsOf(groups, finding); if (!siblings.length) return decide(index, decision);
   const comment = await globalThis.LcReviewNote.ask({ decision, finding, required: decision === 'ACCEPTED', title: tr('dr.allDocs', 'Für alle {n} Dokumente', { n: siblings.length + 1 }), text: decision === 'ACCEPTED' ? tr('dr.allReasonReq', 'Begründung für alle Dokumente (Pflicht; eine markierte Fundstelle gilt nur für dieses Dokument).') : tr('dr.allReasonOpt', 'Optionaler Prüfvermerk für alle Dokumente.') });
   if (comment === null) return;
   if (decision === 'ACCEPTED' && !comment.trim()) { alert(tr('dr.needReason', 'Bitte eine Begründung eingeben. Die Entscheidung wurde nicht gespeichert.')); return; }
   if (comment.length > 1000) { alert(tr('dr.reasonLong', 'Die Begründung darf maximal 1000 Zeichen enthalten.')); return; }
   busy = true; const name = groups[gi].name, mark = dialog.getEvidenceMark?.() || null; renderList();
   const post = (f, withMark) => json(`/api/lcs/${lcId}/document-checks/decisions`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ findingCode: f.code, documentName: f.documentName || '', decision, comment: comment.trim() || null, reviewFingerprint: f.reviewFingerprint, mark: withMark ? mark : null }) });
   let failed = 0;
   try {
    await post(finding, true);
    for (const f of siblings) { try { await post(f, false); } catch (ignored) { failed++; } }
    await load(); dialog.clearEvidenceMark?.();
    if (activeLc?.id === lcId && typeof refreshReviewInline === 'function') refreshReviewInline(lcId);
    if (failed) alert(tr('dr.someFailed', '{f} von {n} Entscheidungen konnten nicht gespeichert werden; bitte diese Befunde einzeln prüfen.', { f: failed, n: siblings.length + 1 }));
    const at = groups.findIndex(g => g.name === name);
    const nextIndex = at >= 0 ? nextOpen(groups[at].items) : -1;
    if (at >= 0 && nextIndex >= 0) { gi = at; active = nextIndex; renderList(); showEvidencePage(groups[gi].items[nextIndex]); }
    else { const n = nextGroupWithOpen(groups, Math.max(at, -1)); const first = n >= 0 ? n : nextGroupWithOpen(groups); if (first >= 0) await showGroup(first); else { active = -1; renderList(); } }
   } catch (error) { alert(tr('dr.decisionFailed', 'Entscheidung konnte nicht gespeichert werden:') + ' ' + error.message); }
   finally { busy = false; if (dialog.open) renderList(); }
  }
  const applyButton = dialog.querySelector('[data-doc-apply]');
  if (applyButton) applyButton.onclick = async () => {
   const status = dialog.querySelector('[data-doc-apply-status]'); applyButton.disabled = true; status.textContent = tr('dr.applying', 'Wird übernommen …');
   try {
    const result = await json(`/api/lcs/${lcId}/rule-facts/suggestions/apply`, { method: 'POST' });
    const count = (result.documentFields || 0) + (result.lcFields || 0);
    const name = groups[gi]?.name; await load();
    const at = groups.findIndex(g => g.name === name);
    status.textContent = count ? (count === 1 ? tr('dr.appliedOne', '1 erkannte Angabe übernommen (nur leere Felder); die Befunde wurden neu berechnet.') : tr('dr.appliedMany', '{n} erkannte Angaben übernommen (nur leere Felder); die Befunde wurden neu berechnet.', { n: count })) : tr('dr.appliedNone', 'Es gab keine neuen erkannten Angaben.');
    if (groups.length) await showGroup(at >= 0 ? at : 0);
   } catch (error) { status.textContent = tr('dr.notApplied', 'Nicht übernommen:') + ' ' + error.message; }
   finally { applyButton.disabled = false; }
  };
  dialog.querySelector('[data-notice]').onclick = () => openNotice(lcId);
  dialog.onkeydown = event => {
   if (event.target.closest?.('input,textarea,select') || event.metaKey || event.ctrlKey || event.altKey || busy || !groups?.length) return;
   const items = groups[gi].items, key = event.key;
   const step = dir => { for (let i = active + dir; i >= 0 && i < items.length; i += dir) if (tierOf(items[i]) !== 'done' || dir < 0) return select(i); };
   if ((key === 'j' || key === 'J') && active >= 0 && isOpen(items[active])) decide(active, 'ACCEPTED');
   else if ((key === 'n' || key === 'N') && active >= 0 && isOpen(items[active])) decide(active, 'CONFIRMED_DISCREPANCY');
   else if (key === 'ArrowDown') step(1);
   else if (key === 'ArrowUp') step(-1);
   else if (key === 'ArrowRight' && gi < groups.length - 1) showGroup(gi + 1);
   else if (key === 'ArrowLeft' && gi > 0) showGroup(gi - 1);
   else return;
   event.preventDefault();
  };
  prev.onclick = () => gi > 0 && showGroup(gi - 1); next.onclick = () => gi < groups.length - 1 && showGroup(gi + 1);
  if (groups.length) await showGroup(gi); else list.textContent = tr('dr.noMoreMissing', 'Keine weiteren Befunde – zuerst die fehlenden Angaben oben erfassen.');
 }

 Object.assign(globalThis.LcDocumentReview, { open, openNotice });
 document.addEventListener('click', event => { if (event.target.closest?.('[data-document-review]')) open(); });
})();
