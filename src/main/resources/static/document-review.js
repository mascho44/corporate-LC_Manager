/* Document-wise review: one dialog per document of the LC file. The PDF stays on the right, all findings of that document are listed on the left and can be
   confirmed one after the other (with a marked evidence instead of a comment); after the last open finding the dialog moves on to the next document. */
(() => {
 const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
 const isOpen = finding => !finding.reviewDecision && finding.severity !== 'OK';
 const SEVERITY = { DISCREPANCY: 'Abweichung', WARNING: 'Hinweis', OK: 'in Ordnung' };

 /** Findings grouped by document in the order of the document list (findings without a single document last); open ones first, discrepancies before warnings. Documents with nothing to look at are left out. */
 function groupFindings(results, documents) {
  const docs = documents || [], order = new Map(docs.map((d, i) => [d.originalFilename, i]));
  const rank = f => isOpen(f) ? (f.severity === 'DISCREPANCY' ? 0 : 1) : 2;
  const groups = new Map();
  (results || []).forEach(f => { const key = f.documentName || ''; if (!groups.has(key)) groups.set(key, []); groups.get(key).push(f); });
  return [...groups.entries()]
   .filter(([, items]) => items.some(f => f.severity !== 'OK' || f.reviewDecision))
   .map(([name, items]) => { const doc = docs.find(d => d.originalFilename === name); return { name, documentId: doc?.id || null, contentType: doc?.contentType || null, items: [...items].sort((a, b) => rank(a) - rank(b)), openCount: items.filter(isOpen).length }; })
   .sort((a, b) => (a.name === '' ? 1 : 0) - (b.name === '' ? 1 : 0) || (order.has(a.name) ? order.get(a.name) : 1e6) - (order.has(b.name) ? order.get(b.name) : 1e6));
 }
 /** Index of the next open finding after `from` (no wrap), -1 when none is left. */
 function nextOpen(items, from = -1) { for (let i = from + 1; i < items.length; i++) if (isOpen(items[i])) return i; return -1; }
 /** Index of the next document group with open findings after `from` (no wrap), -1 when none is left. */
 function nextGroupWithOpen(groups, from = -1) { for (let i = from + 1; i < groups.length; i++) if (groups[i].openCount > 0) return i; return -1; }
 globalThis.LcDocumentReview = { groupFindings, nextOpen, nextGroupWithOpen, isOpen };
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
   '<div class="evidence-workspace"><section class="evidence-details"><div data-doc-findings></div></section><section class="evidence-document" data-pdf-pages></section></div>';
  dialog.querySelector('[data-close]').onclick = () => dialog.close();
  const list = dialog.querySelector('[data-doc-findings]'), viewer = dialog.querySelector('[data-pdf-pages]'), label = dialog.querySelector('[data-doc-label]');
  const prev = dialog.querySelector('[data-doc-prev]'), next = dialog.querySelector('[data-doc-next]');

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
   label.textContent = `Dokument ${gi + 1} von ${groups.length}: ${group.name || 'Dokumentensatz (ohne einzelnes Dokument)'} · ${group.openCount} offen`;
   prev.disabled = gi === 0 || busy; next.disabled = gi >= groups.length - 1 || busy;
   list.replaceChildren();
   if (group.openCount === 0) { const done = document.createElement('p'); done.className = 'doc-review-done'; done.textContent = '✓ Alle Befunde zu diesem Dokument sind erledigt.'; list.append(done); }
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
    } else {
     const done = document.createElement('small'); done.className = 'doc-review-doneline';
     done.textContent = `✓ ${finding.reviewDecision === 'ACCEPTED' ? 'als erfüllt bestätigt' : finding.reviewDecision === 'CONFIRMED_DISCREPANCY' ? 'Abweichung bestätigt' : 'in Ordnung'}${finding.reviewedBy ? ' · ' + finding.reviewedBy : ''}`; card.append(done);
    }
    card.onclick = () => select(index); list.append(card);
   });
  }
  function select(index) { active = index; dialog.clearEvidenceMark?.(); renderList(); const f = groups[gi].items[index]; if (f) showEvidencePage(f); }
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
  prev.onclick = () => gi > 0 && showGroup(gi - 1); next.onclick = () => gi < groups.length - 1 && showGroup(gi + 1);
  await showGroup(gi);
 }

 document.addEventListener('click', event => { if (event.target.closest?.('[data-document-review]')) open(); });
})();
