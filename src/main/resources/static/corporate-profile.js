/* Corporate profile: role of the tenant per LC file (exporter or importer), a short "ready to submit?" check on top of the review, and no bank-only tools.
   Everything here is presentation; the server checks permissions and profile on every request. */
(() => {
 const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
 const MAX_LISTED = 8;

 /** Verdict from the review result: open discrepancies and missing documents block, warnings are only mentioned. */
 function submissionCheck(review) {
  const results = review?.results || [];
  const open = result => !result.reviewDecision;
  const blocking = results.filter(r => r.severity === 'DISCREPANCY' && open(r));
  const warnings = results.filter(r => r.severity === 'WARNING' && open(r));
  const missing = blocking.filter(r => r.code === 'MISSING_DOCUMENT').length;
  return { ready: blocking.length === 0, blocking: blocking.map(r => r.message), missing, warnings: warnings.length, total: results.length };
 }
 function checkHtml(check, showAll) {
  const head = check.ready
   ? '<b class="submission-ok">✓ Einreichungsbereit</b><p>Die einfache Prüfung hat keine offene Abweichung gefunden.</p>'
   : `<b class="submission-no">✗ Noch nicht einreichungsbereit</b><p>${check.blocking.length} Punkt${check.blocking.length === 1 ? ' muss' : 'e müssen'} vorher geklärt werden${check.missing ? ` (davon ${check.missing} fehlende${check.missing === 1 ? 's' : ''} Dokument${check.missing === 1 ? '' : 'e'})` : ''}:</p>` +
     `<ul>${check.blocking.slice(0, MAX_LISTED).map(m => `<li>${esc(m)}</li>`).join('')}${check.blocking.length > MAX_LISTED ? `<li>… und ${check.blocking.length - MAX_LISTED} weitere (unter „Alle Befunde")</li>` : ''}</ul>`;
  const warn = check.warnings ? `<p>${check.warnings} Hinweis${check.warnings === 1 ? '' : 'e'} zur Kenntnis, kein Hindernis.</p>` : '';
  return `${head}${warn}<p class="submission-note"><small>Oberflächliche Vorprüfung auf offene Abweichungen. Die endgültige Prüfung liegt bei der Bank.</small></p>` +
   `<button type="button" class="secondary" data-submission-all aria-pressed="${showAll}">${showAll ? 'Befundliste ausblenden' : `Alle Befunde anzeigen (${check.total})`}</button>`;
 }
 globalThis.LcCorporateProfile = { submissionCheck, checkHtml };
 if (typeof document === 'undefined' || !document.body) return;

 const modules = () => globalThis.LcWorkspaceModules || {};
 const corporateOnly = () => modules().corporate === true && modules().bank !== true;
 const can_ = permission => typeof can === 'function' && can(permission);
 let showAll = false;

 async function roleBar(id) {
  const overview = document.querySelector('#detail .dossier-section[data-section="overview"]');
  if (!overview || !modules().corporate) return;
  overview.querySelector('.party-role')?.remove();
  const current = activeLc?.partyRole === 'APPLICANT' ? 'APPLICANT' : 'BENEFICIARY';
  const bar = document.createElement('div'); bar.className = 'party-role';
  bar.innerHTML = `<label>Unsere Rolle in dieser Akte <select data-party-role${can_('LC_EDIT') ? '' : ' disabled'}><option value="BENEFICIARY"${current === 'BENEFICIARY' ? ' selected' : ''}>Begünstigter (Exporteur)</option><option value="APPLICANT"${current === 'APPLICANT' ? ' selected' : ''}>Antragsteller (Importeur)</option></select></label>` +
   `<small>${current === 'BENEFICIARY' ? 'Wir liefern die Dokumente: Beschaffungsplan, Fristen und Einreichungs-Check gelten.' : 'Wir eröffnen das Akkreditiv: Es gibt keinen Beschaffungsplan; eingehende Dokumente werden vollständig geprüft.'}</small>`;
  const customerLabel = document.createElement('label'); customerLabel.className = 'lc-customer';
  customerLabel.append('Kunde dieser Akte ');
  const customerSelect = document.createElement('select'); customerSelect.dataset.lcCustomer = ''; customerSelect.disabled = !can_('LC_EDIT');
  customerSelect.innerHTML = '<option value="">– nicht verknüpft –</option>'; customerLabel.append(customerSelect);
  bar.append(customerLabel);
  overview.prepend(bar);
  json('/api/customers').then(list => {
   customerSelect.innerHTML = '<option value="">– nicht verknüpft –</option>' + (list || []).map(c => `<option value="${esc(c.id)}"${activeLc?.customerId === c.id ? ' selected' : ''}>${esc(c.name)}${c.customerNumber ? ' · ' + esc(c.customerNumber) : ''}</option>`).join('');
  }).catch(() => { customerLabel.hidden = true; });
  customerSelect.onchange = async event => {
   const previous = activeLc?.customerId || '';
   try {
    await json(`/api/lcs/${encodeURIComponent(id)}/customer`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ customerId: event.target.value || null }) });
    if (activeLc) activeLc.customerId = event.target.value || null;
   } catch (error) { alert('Kunde konnte nicht gespeichert werden: ' + error.message); event.target.value = previous; }
  };
  bar.querySelector('select').onchange = async event => {
   try {
    await json(`/api/lcs/${encodeURIComponent(id)}/party-role`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ role: event.target.value }) });
    if (activeLc) activeLc.partyRole = event.target.value;
    await (typeof showKeepingView === 'function' ? showKeepingView(id) : show(id));
   } catch (error) { alert('Rolle konnte nicht gespeichert werden: ' + error.message); event.target.value = current; }
  };
 }

 /** The long finding list is folded away until asked for; the filter bar is built a moment after the card, so this runs on every change too. */
 function foldFindings() {
  if (!document.querySelector('#detail .submission-check')) return;
  document.querySelectorAll('#detail .finding-keyboard-help,#detail .finding-tools,#detail .finding-filters,#detail .review-filter-bar,#detail .check-list')
   .forEach(node => node.classList.toggle('submission-folded', !showAll));
 }

 /** Ready-to-submit card above the review for corporate-only tenants on exporter files; the long finding list is folded away until asked for. */
 async function submissionCard() {
  const summary = document.querySelector('#detail .review-summary');
  if (!summary || !corporateOnly() || activeLc?.partyRole === 'APPLICANT') return;
  const lcId = activeLc.id;
  const review = await json(`/api/lcs/${encodeURIComponent(lcId)}/document-checks`).catch(() => null);
  if (!review || activeLc?.id !== lcId || !summary.isConnected) return;
  document.querySelectorAll('#detail .submission-check').forEach(node => node.remove());
  const card = document.createElement('section'); card.className = 'submission-check'; card.innerHTML = checkHtml(submissionCheck(review), showAll);
  summary.before(card);
  const apply = () => foldFindings();
  card.querySelector('[data-submission-all]').onclick = event => { showAll = !showAll; event.currentTarget.textContent = showAll ? 'Befundliste ausblenden' : `Alle Befunde anzeigen (${review.results.length})`; event.currentTarget.setAttribute('aria-pressed', String(showAll)); apply(); };
  apply();
 }

 /** Bank-only review tools are not offered to corporate-only tenants. */
 function hideBankTools() {
  if (!corporateOnly()) return;
  document.querySelectorAll('[data-rule-source-open],[data-simulation-open]').forEach(button => { button.hidden = true; });
 }

 let lastBlock = null, scheduled = false;
 const run = () => {
  scheduled = false;
  hideBankTools();
  foldFindings();
  const block = document.querySelector('#detail .review-summary');
  if (block && block !== lastBlock) { lastBlock = block; submissionCard().catch(() => { }); }
  else if (!block) lastBlock = null;
  else if (!document.querySelector('#detail .submission-check') && corporateOnly()) submissionCard().catch(() => { });
 };
 new MutationObserver(() => { if (!scheduled) { scheduled = true; setTimeout(run, 80); } }).observe(document.body, { childList: true, subtree: true });

 if (typeof show === 'function') {
  const base = show;
  show = async function (id) { await base(id); try { await roleBar(id); } catch (ignored) { } };
 }
 run();
})();
