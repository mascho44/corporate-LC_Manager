/* Document-wise review: one dialog per document of the LC file. The PDF stays on the right, all findings of that document are listed on the left and can be
   confirmed one after the other (with a marked evidence instead of a comment); after the last open finding the dialog moves on to the next document. */
(() => {
 const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
 // REVIEW_STALE only says that an earlier decision no longer applies; it cannot be decided itself (the original finding next to it is the one to confirm again).
 const isOpen = finding => !finding.reviewDecision && finding.severity !== 'OK' && finding.code !== 'REVIEW_STALE';
 const SEVERITY = { DISCREPANCY: 'Abweichung', WARNING: 'Hinweis', OK: 'in Ordnung' };
 // Amount, expiry and presentation period, shipment date, missing document and signature stop a presentation; they are reviewed first.
 const BLOCKING = /AMOUNT|EXPIR|PRESENTATION_PERIOD|PRESENTATION_BEFORE|SHIPMENT_DATE|LATEST_SHIPMENT|MISSING_DOCUMENT|SIGNATURE/;
 const TIERS = [['blocking', 'Blockierend'], ['check', 'Prüfen'], ['hint', 'Hinweise'], ['done', 'Erledigt & in Ordnung']];
 /** Tier of a finding: blocking, check (other discrepancies), hint (warnings) or done (decided or OK). */
 function tierOf(finding) {
  if (!isOpen(finding)) return 'done';
  if (BLOCKING.test(String(finding.code || ''))) return 'blocking';
  return finding.severity === 'DISCREPANCY' ? 'check' : 'hint';
 }
 const TIER_RANK = { blocking: 0, check: 1, hint: 2, done: 3 };
 /** Open and decidable findings over all documents, for the progress bar. */
 function progress(groups) {
  let open = 0, total = 0;
  (groups || []).forEach(g => g.items.forEach(f => { if (f.code === 'REVIEW_STALE' || f.severity === 'OK' && !f.reviewDecision) return; total++; if (isOpen(f)) open++; }));
  return { open, done: total - open, total };
 }

 /** Findings grouped by document in the order of the document list (findings without a single document last); open ones first, discrepancies before warnings. Documents with nothing to look at are left out. */
 function groupFindings(results, documents) {
  const docs = documents || [], order = new Map(docs.map((d, i) => [d.originalFilename, i]));
  const rank = f => TIER_RANK[tierOf(f)];
  const groups = new Map();
  (results || []).forEach(f => { const key = f.documentName || ''; if (!groups.has(key)) groups.set(key, []); groups.get(key).push(f); });
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
 globalThis.LcDocumentReview = { groupFindings, nextOpen, nextGroupWithOpen, isOpen, tierOf, progress, siblingsOf };
 if (typeof document === 'undefined' || !document.body) return;

 async function open() {
  const lcId = activeLc?.id; if (!lcId) return;
  let dialog = document.querySelector('#documentReviewDialog');
  if (!dialog) { dialog = document.createElement('dialog'); dialog.id = 'documentReviewDialog'; dialog.className = 'wide-dialog'; document.body.append(dialog); }
  dialog.onclose?.(); dialog.onclose = null;
  dialog.innerHTML = '<p>Prüfung wird geladen …</p><button type="button" data-close>Schließen</button>';
  dialog.querySelector('[data-close]').onclick = () => dialog.close(); dialog.showModal();
  let groups, gi = 0, active = -1, busy = false;
  const load = async () => {
   const [review, documents] = await Promise.all([json(`/api/lcs/${lcId}/document-checks`), json(`/api/lcs/${lcId}/documents`).catch(() => [])]);
   groups = groupFindings(review.results, documents);
  };
  try { await load(); } catch (error) { dialog.querySelector('p').textContent = 'Prüfung konnte nicht geladen werden: ' + error.message; return; }
  if (!groups.length) { dialog.querySelector('p').textContent = 'Keine Befunde zu Dokumenten vorhanden.'; return; }
  gi = Math.max(0, nextGroupWithOpen(groups)); 
  dialog.innerHTML = '<div class="dialoghead"><h2>Dokumentweise Prüfung</h2><div class="doc-review-nav"><button type="button" class="secondary" data-doc-prev aria-label="Vorheriges Dokument">←</button><b data-doc-label></b><button type="button" class="secondary" data-doc-next aria-label="Nächstes Dokument">→</button></div><button type="button" data-close>Schließen</button></div>' +
   '<div class="doc-review-progress"><span data-doc-progress></span><progress data-doc-progress-bar max="1" value="0"></progress></div><nav class="doc-review-strip" data-doc-strip aria-label="Dokumente"></nav>' +
   '<div class="evidence-workspace"><section class="evidence-details">' + (can('DOCUMENT_UPLOAD') ? '<p class="doc-review-bar"><button type="button" class="secondary" data-doc-apply>Erkannte Prüfdaten übernehmen</button> <small data-doc-apply-status role="status"></small></p>' : '') + '<div data-doc-findings></div></section><section class="evidence-document" data-pdf-pages></section></div>' +
   '<p class="doc-review-keys"><kbd>J</kbd> erfüllt · <kbd>N</kbd> Abweichung · <kbd>↑</kbd><kbd>↓</kbd> Befund · <kbd>←</kbd><kbd>→</kbd> Dokument</p>';
  dialog.querySelector('[data-close]').onclick = () => dialog.close();
  const list = dialog.querySelector('[data-doc-findings]'), viewer = dialog.querySelector('[data-pdf-pages]'), label = dialog.querySelector('[data-doc-label]');
  const prev = dialog.querySelector('[data-doc-prev]'), next = dialog.querySelector('[data-doc-next]');
  const strip = dialog.querySelector('[data-doc-strip]'), progressText = dialog.querySelector('[data-doc-progress]'), progressBar = dialog.querySelector('[data-doc-progress-bar]');
  const openTiers = new Set(['blocking', 'check']);
  function renderOverview() {
   const p = progress(groups);
   progressText.textContent = `${p.done} / ${p.total} erledigt`; progressBar.max = Math.max(1, p.total); progressBar.value = p.done;
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
    if (page && dialog.open) dialog.showPdfPage?.(page);
   } catch (ignored) { /* the page jump is a convenience only */ }
  }
  function renderList() {
   const group = groups[gi];
   renderOverview();
   label.textContent = `Dokument ${gi + 1} von ${groups.length}: ${group.name || 'Dokumentensatz (ohne einzelnes Dokument)'} · ${group.openCount} offen`;
   prev.disabled = gi === 0 || busy; next.disabled = gi >= groups.length - 1 || busy;
   list.replaceChildren();
   if (group.openCount === 0) { const done = document.createElement('p'); done.className = 'doc-review-done'; done.textContent = '✓ Alle Befunde zu diesem Dokument sind erledigt.'; list.append(done); }
   const sections = {};
   TIERS.forEach(([key, title]) => {
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
    const badge = document.createElement('b'); badge.className = 'doc-review-badge ' + String(finding.severity || '').toLowerCase(); badge.textContent = SEVERITY[finding.severity] || finding.severity;
    head.append(badge, document.createTextNode(' ' + (finding.message || ''))); card.append(head);
    if (finding.lcCondition) { const c = document.createElement('small'); c.textContent = 'LC-Bedingung: ' + finding.lcCondition; card.append(c); }
    if (finding.documentEvidence) { const q = document.createElement('blockquote'); q.textContent = finding.documentEvidence; card.append(q); }
    if (isOpen(finding)) {
     const actions = document.createElement('div'); actions.className = 'doc-review-actions';
     [['ACCEPTED', 'Als erfüllt bestätigen'], ['CONFIRMED_DISCREPANCY', 'Abweichung bestätigen']].forEach(([decision, text]) => {
      const button = document.createElement('button'); button.type = 'button'; button.className = decision === 'ACCEPTED' ? '' : 'secondary'; button.textContent = text; button.disabled = busy;
      button.onclick = event => { event.stopPropagation(); decide(index, decision); }; actions.append(button);
     });
     const hint = document.createElement('small'); hint.textContent = 'Fundstelle rechts im PDF markieren statt Kommentar schreiben.'; card.append(actions, hint);
     const siblings = siblingsOf(groups, finding);
     if (siblings.length) {
      const also = document.createElement('div'); also.className = 'doc-review-also';
      const names = [...new Set(siblings.map(f => f.documentName))];
      const text = document.createElement('small'); text.textContent = `Gleicher Befund auch in: ${names.join(', ')}`;
      const all = document.createElement('button'); all.type = 'button'; all.className = 'secondary'; all.disabled = busy; all.textContent = `Für alle ${siblings.length + 1} als erfüllt bestätigen`;
      all.onclick = event => { event.stopPropagation(); decideAll(index, 'ACCEPTED'); };
      const allDiscrepancy = document.createElement('button'); allDiscrepancy.type = 'button'; allDiscrepancy.className = 'secondary'; allDiscrepancy.disabled = busy; allDiscrepancy.textContent = `Für alle ${siblings.length + 1} Abweichung bestätigen`;
      allDiscrepancy.onclick = event => { event.stopPropagation(); decideAll(index, 'CONFIRMED_DISCREPANCY'); };
      also.append(text, all, allDiscrepancy); card.append(also);
     }
    } else if (finding.code === 'REVIEW_STALE') {
     const note = document.createElement('small'); note.textContent = 'Eine frühere Entscheidung gilt nicht mehr (Regel oder Vergleichsgrundlage haben sich geändert). Bitte den ursprünglichen Befund dieses Dokuments erneut bestätigen; dieser Hinweis verschwindet dann von selbst.'; card.append(note);
    } else {
     const done = document.createElement('small'); done.className = 'doc-review-doneline';
     done.textContent = `✓ ${finding.reviewDecision === 'ACCEPTED' ? 'als erfüllt bestätigt' : finding.reviewDecision === 'CONFIRMED_DISCREPANCY' ? 'Abweichung bestätigt' : 'in Ordnung'}${finding.reviewedBy ? ' · ' + finding.reviewedBy : ''}`; card.append(done);
    }
    card.onclick = () => select(index); sections[tierOf(finding)].append(card);
   });
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
    if (first) showEvidencePage(first);
   } else { const none = document.createElement('p'); none.textContent = group.documentId ? 'Dieses Dokument ist kein PDF und kann hier nicht angezeigt werden.' : 'Diese Befunde betreffen den Dokumentensatz, nicht ein einzelnes Dokument.'; viewer.append(none); }
  }
  async function decide(index, decision) {
   if (busy) return; busy = true; const finding = groups[gi].items[index], name = groups[gi].name, mark = dialog.getEvidenceMark?.() || null; renderList();
   try {
    await decideCheck(finding.code, finding.documentName || '', decision, finding.reviewFingerprint, mark);
    await load();
    dialog.clearEvidenceMark?.();
    const at = groups.findIndex(g => g.name === name);
    if (at < 0) { const n = nextGroupWithOpen(groups); if (n >= 0) await showGroup(n); else { list.textContent = '✓ Alle Befunde sind erledigt.'; } return; }
    gi = at;
    const nextIndex = nextOpen(groups[gi].items);
    if (nextIndex >= 0) { active = nextIndex; renderList(); showEvidencePage(groups[gi].items[nextIndex]); }
    else { const n = nextGroupWithOpen(groups, gi); if (n >= 0) await showGroup(n); else { active = -1; renderList(); } }
   } catch (error) { alert('Entscheidung konnte nicht gespeichert werden: ' + error.message); }
   finally { busy = false; if (dialog.open) renderList(); }
  }
  /** One decision for the finding and the same open finding in the other documents. The marked evidence belongs to the current document only, so the others need a written reason. */
  async function decideAll(index, decision) {
   if (busy) return; const finding = groups[gi].items[index], siblings = siblingsOf(groups, finding); if (!siblings.length) return decide(index, decision);
   const comment = await globalThis.LcReviewNote.ask({ decision, required: decision === 'ACCEPTED', title: `Für alle ${siblings.length + 1} Dokumente`, text: decision === 'ACCEPTED' ? 'Begründung für alle Dokumente (Pflicht; eine markierte Fundstelle gilt nur für dieses Dokument).' : 'Optionaler Prüfvermerk für alle Dokumente.' });
   if (comment === null) return;
   if (decision === 'ACCEPTED' && !comment.trim()) { alert('Bitte eine Begründung eingeben. Die Entscheidung wurde nicht gespeichert.'); return; }
   if (comment.length > 1000) { alert('Die Begründung darf maximal 1000 Zeichen enthalten.'); return; }
   busy = true; const name = groups[gi].name, mark = dialog.getEvidenceMark?.() || null; renderList();
   const post = (f, withMark) => json(`/api/lcs/${lcId}/document-checks/decisions`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ findingCode: f.code, documentName: f.documentName || '', decision, comment: comment.trim() || null, reviewFingerprint: f.reviewFingerprint, mark: withMark ? mark : null }) });
   let failed = 0;
   try {
    await post(finding, true);
    for (const f of siblings) { try { await post(f, false); } catch (ignored) { failed++; } }
    await load(); dialog.clearEvidenceMark?.();
    if (activeLc?.id === lcId && typeof refreshReviewInline === 'function') refreshReviewInline(lcId);
    if (failed) alert(`${failed} von ${siblings.length + 1} Entscheidungen konnten nicht gespeichert werden; bitte diese Befunde einzeln prüfen.`);
    const at = groups.findIndex(g => g.name === name);
    const nextIndex = at >= 0 ? nextOpen(groups[at].items) : -1;
    if (at >= 0 && nextIndex >= 0) { gi = at; active = nextIndex; renderList(); showEvidencePage(groups[gi].items[nextIndex]); }
    else { const n = nextGroupWithOpen(groups, Math.max(at, -1)); const first = n >= 0 ? n : nextGroupWithOpen(groups); if (first >= 0) await showGroup(first); else { active = -1; renderList(); } }
   } catch (error) { alert('Entscheidung konnte nicht gespeichert werden: ' + error.message); }
   finally { busy = false; if (dialog.open) renderList(); }
  }
  const applyButton = dialog.querySelector('[data-doc-apply]');
  if (applyButton) applyButton.onclick = async () => {
   const status = dialog.querySelector('[data-doc-apply-status]'); applyButton.disabled = true; status.textContent = 'Wird übernommen …';
   try {
    const result = await json(`/api/lcs/${lcId}/rule-facts/suggestions/apply`, { method: 'POST' });
    const count = (result.documentFields || 0) + (result.lcFields || 0);
    const name = groups[gi]?.name; await load();
    const at = groups.findIndex(g => g.name === name);
    status.textContent = count ? `${count} erkannte Angabe${count === 1 ? '' : 'n'} übernommen (nur leere Felder); die Befunde wurden neu berechnet.` : 'Es gab keine neuen erkannten Angaben.';
    if (groups.length) await showGroup(at >= 0 ? at : 0);
   } catch (error) { status.textContent = 'Nicht übernommen: ' + error.message; }
   finally { applyButton.disabled = false; }
  };
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
  await showGroup(gi);
 }

 document.addEventListener('click', event => { if (event.target.closest?.('[data-document-review]')) open(); });
})();
