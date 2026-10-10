/* Amendments (MT707) take effect only when accepted: status per amendment, accept / reject, and the planned changes of open ones. */
(() => {
 const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
 const STATUS = { PENDING: ['Offen – noch nicht wirksam', 'pending'], ACCEPTED: ['Angenommen – wirksam', 'accepted'], REJECTED: ['Abgelehnt – ohne Wirkung', 'rejected'] };
 const date = value => { try { return value ? new Intl.DateTimeFormat('de-DE').format(new Date(value)) : ''; } catch (ignored) { return String(value || ''); } };
 const when = value => String(value || '').replace('T', ' ').slice(0, 16);
 const money = value => { const n = Number(value); return Number.isFinite(n) ? new Intl.NumberFormat('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(n) : String(value); };
 /** The changes an open amendment would make, as readable lines. */
 function plannedChanges(a) {
  const lines = [];
  if (a.newExpiryDate) lines.push(`Ablauf: ${date(a.newExpiryDate)}${a.newExpiryPlace ? ' ' + a.newExpiryPlace : ''}`);
  if (a.newLatestShipmentDate) lines.push(`Spätester Versand: ${date(a.newLatestShipmentDate)}`);
  if (a.amountIncrease) lines.push(`Betrag erhöht um ${money(a.amountIncrease)}`);
  if (a.amountDecrease) lines.push(`Betrag vermindert um ${money(a.amountDecrease)}`);
  if (a.changedDocuments) lines.push('Dokumentenanforderungen (46B) geändert');
  if (a.changedGoods) lines.push('Warenbeschreibung (45B) geändert');
  if (a.changedConditions) lines.push('Zusatzbedingungen (47B) geändert');
  try { Object.keys(JSON.parse(a.otherChanges || '{}')).forEach(tag => lines.push(tag === '59' ? 'Begünstigter (59) geändert' : `Feld ${tag} geändert`)); } catch (ignored) { }
  return lines;
 }
 function badgeHtml(a) {
  const [text, css] = STATUS[a.status] || [a.status, 'pending'];
  const by = a.decidedBy && a.status !== 'PENDING' ? ` · ${escape(a.decidedBy)}${a.decidedAt ? ', ' + escape(when(a.decidedAt)) : ''}` : '';
  const comment = a.decisionComment ? ` · „${escape(a.decisionComment)}“` : '';
  return `<p class="amendment-status ${css}"><b>${escape(text)}</b><small>${by}${comment}</small></p>`;
 }
 function pendingHtml(a, canDecide) {
  if (a.status !== 'PENDING') return '';
  const lines = plannedChanges(a);
  const list = lines.length ? `<ul class="amendment-planned">${lines.map(l => `<li>${escape(l)}</li>`).join('')}</ul>` : '';
  const buttons = canDecide ? `<div class="amendment-actions"><button type="button" data-amendment-accept="${escape(a.id)}">Änderung annehmen</button> <button type="button" class="secondary" data-amendment-reject="${escape(a.id)}">Änderung ablehnen</button></div>` : '';
  return `<div class="amendment-pending"><small>Geplante Änderungen</small>${list}${buttons}</div>`;
 }
 globalThis.LcAmendmentStatus = { escape, plannedChanges, badgeHtml, pendingHtml };
 if (typeof document === 'undefined' || !document.body) return;

 const decide = async (lcId, id, action, comment) => {
  await json(`/api/lcs/${encodeURIComponent(lcId)}/amendments/${encodeURIComponent(id)}/${action}`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ comment }) });
  if (activeLc?.id === lcId) { await show(lcId); if (typeof activateDossierSection === 'function') activateDossierSection('overview'); }
 };
 if (typeof show === 'function') {
  const showBase = show;
  show = async function (id) {
   await showBase(id);
   try {
    const history = [...document.querySelectorAll('#detail .docs')].find(section => section.querySelector('h3')?.textContent === 'Amendment-Historie');
    if (!history) return;
    const amendments = await json(`/api/lcs/${encodeURIComponent(id)}/amendments`).catch(() => []);
    const cards = history.querySelectorAll('.card');
    const canDecide = typeof can === 'function' && can('LC_EDIT');
    amendments.forEach((a, index) => {
     const card = cards[index]; if (!card || card.querySelector('.amendment-status')) return;
     card.insertAdjacentHTML('afterbegin', badgeHtml(a));
     if (a.status === 'PENDING') {
      [...card.querySelectorAll('p')].filter(p => /Altbestand/.test(p.textContent)).forEach(p => p.remove());
      card.insertAdjacentHTML('beforeend', pendingHtml(a, canDecide));
     }
    });
    history.querySelectorAll('[data-amendment-accept]').forEach(b => b.onclick = async () => {
     if (typeof confirmAction === 'function' && !await confirmAction('Änderung annehmen? Sie wird jetzt in die gültige Fassung der Akte übernommen; bisherige Prüfentscheidungen werden zurückgesetzt.')) return;
     const comment = prompt('Optionaler Vermerk zur Annahme:'); if (comment === null) return;
     try { await decide(id, b.dataset.amendmentAccept, 'accept', comment); } catch (error) { alert(error.message); }
    });
    history.querySelectorAll('[data-amendment-reject]').forEach(b => b.onclick = async () => {
     const comment = prompt('Optionaler Vermerk zur Ablehnung (die Akte bleibt unverändert):'); if (comment === null) return;
     try { await decide(id, b.dataset.amendmentReject, 'reject', comment); } catch (error) { alert(error.message); }
    });
   } catch (ignored) { }
  };
 }
})();
