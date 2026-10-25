/* Amendments (MT707) take effect only when accepted: status per amendment, accept / reject, and the planned changes of open ones. */
(() => {
 const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
 const STATUS = (globalThis.lcLazy || (f => f()))(() => ({ PENDING: [tx('aml.c17b04','Offen – noch nicht wirksam'), 'pending'], ACCEPTED: [tx('aml.de73d4','Angenommen – wirksam'), 'accepted'], REJECTED: [tx('aml.50413f','Abgelehnt – ohne Wirkung'), 'rejected'] }));
 const date = value => { try { return value ? new Intl.DateTimeFormat('de-DE').format(new Date(value)) : ''; } catch (ignored) { return String(value || ''); } };
 const when = value => String(value || '').replace('T', ' ').slice(0, 16);
 const money = value => { const n = Number(value); return Number.isFinite(n) ? new Intl.NumberFormat('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(n) : String(value); };
 /** The changes an open amendment would make, as readable lines. */
 function plannedChanges(a) {
  const lines = [];
  if (a.newExpiryDate) lines.push(`Ablauf: ${date(a.newExpiryDate)}${a.newExpiryPlace ? ' ' + a.newExpiryPlace : ''}`);
  if (a.newLatestShipmentDate) lines.push(tx('amt.58d9d4','Spätester Versand: {p0}',{p0:date(a.newLatestShipmentDate)}));
  if (a.amountIncrease) lines.push(tx('amt.2d1ad8','Betrag erhöht um {p0}',{p0:money(a.amountIncrease)}));
  if (a.amountDecrease) lines.push(`Betrag vermindert um ${money(a.amountDecrease)}`);
  if (a.changedDocuments) lines.push(tx('aml.2a578e','Dokumentenanforderungen (46B) geändert'));
  if (a.changedGoods) lines.push(tx('aml.e983ec','Warenbeschreibung (45B) geändert'));
  if (a.changedConditions) lines.push(tx('aml.b219a2','Zusatzbedingungen (47B) geändert'));
  try { Object.keys(JSON.parse(a.otherChanges || '{}')).forEach(tag => lines.push(tag === '59' ? tx('amx.59','Begünstigter (59) geändert') : tx('amt.dbe8ab','Feld {p0} geändert',{p0:tag}))); } catch (ignored) { }
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
  const buttons = canDecide ? `<div class="amendment-actions"><button type="button" data-amendment-accept="${escape(a.id)}">${tx('amh.3fb019','Änderung annehmen')}</button> <button type="button" class="secondary" data-amendment-reject="${escape(a.id)}">${tx('amh.d4c1dc','Änderung ablehnen')}</button></div>` : '';
  return `<div class="amendment-pending"><small>${tx('amh.fc624e','Geplante Änderungen')}</small>${list}${buttons}</div>`;
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
    const history = [...document.querySelectorAll('#detail .docs')].find(section => (typeof sectionKey === 'function' ? sectionKey(section) === 'amendments' : section.querySelector('h3')?.textContent === 'Amendment-Historie'));
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
     if (typeof confirmAction === 'function' && !await confirmAction(tx('aml.dc46e3','Änderung annehmen? Sie wird jetzt in die gültige Fassung der Akte übernommen; bisherige Prüfentscheidungen werden zurückgesetzt.'))) return;
     const comment = prompt(tx('aml.33d749','Optionaler Vermerk zur Annahme:')); if (comment === null) return;
     try { await decide(id, b.dataset.amendmentAccept, 'accept', comment); } catch (error) { alert(error.message); }
    });
    history.querySelectorAll('[data-amendment-reject]').forEach(b => b.onclick = async () => {
     const comment = prompt(tx('aml.d51d24','Optionaler Vermerk zur Ablehnung (die Akte bleibt unverändert):')); if (comment === null) return;
     try { await decide(id, b.dataset.amendmentReject, 'reject', comment); } catch (error) { alert(error.message); }
    });
   } catch (ignored) { }
  };
 }
})();
