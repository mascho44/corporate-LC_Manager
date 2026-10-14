/* Guarantees (MT760) and free-format bank messages (MT199/MT799): badge, message list per dossier, assignment of loose messages. */
(() => {
 const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
 const isGuarantee = lc => Boolean(lc) && lc.instrumentType === 'GUARANTEE';
 const badge = () => '<span class="badge instrument-badge" title="Garantie / Standby (MT760)">Garantie</span>';
 const when = value => String(value || '').replace('T', ' ').slice(0, 16);
 function messageHtml(m) {
  const related = m.relatedReference ? ` · Bezug ${escape(m.relatedReference)}` : '';
  return `<details class="swift-message"><summary><b>${escape(m.messageType)}</b> · ${escape(m.reference)}${related} · ${escape(when(m.importedAt))}</summary><pre>${escape(m.narrative)}</pre></details>`;
 }
 function panelHtml(messages) {
  if (!messages || !messages.length) return '';
  return `<section class="swift-messages"><h3>Bankmitteilungen (${messages.length})</h3>${messages.map(messageHtml).join('')}</section>`;
 }
 globalThis.LcGuaranteeMessages = { escape, isGuarantee, badge, messageHtml, panelHtml };
 if (typeof document === 'undefined' || !document.body) return;

 const badgeRows = () => {
  if (typeof data === 'undefined') return;
  document.querySelectorAll('#list .dossier-row[data-id]').forEach(row => {
   const lc = data.find(x => x.id === row.dataset.id);
   const title = row.querySelector('strong');
   if (isGuarantee(lc) && title && !title.querySelector('.instrument-badge')) title.insertAdjacentHTML('beforeend', ' ' + badge());
  });
 };
 if (typeof render === 'function') {
  const renderBase = render;
  render = function () { const result = renderBase.apply(this, arguments); try { badgeRows(); } catch (ignored) { } return result; };
 }
 if (typeof show === 'function') {
  const showBase = show;
  show = async function (id) {
   await showBase(id);
   try {
    const lc = typeof data !== 'undefined' ? data.find(x => x.id === id) : null;
    const detail = document.querySelector('#detail');
    if (!detail) return;
    const heading = detail.querySelector('h2');
    if (isGuarantee(lc) && heading && !heading.querySelector('.instrument-badge')) heading.insertAdjacentHTML('beforeend', ' ' + badge());
    detail.querySelector('.swift-messages')?.remove();
    const messages = await json(`/api/lcs/${encodeURIComponent(id)}/swift-messages`).catch(() => []);
    const overview = detail.querySelector('.dossier-section[data-section="overview"]');
    if (overview && typeof activeLc !== 'undefined' && activeLc?.id === id) overview.insertAdjacentHTML('beforeend', panelHtml(messages));
   } catch (ignored) { }
  };
 }

 // Loose messages (no matching dossier) can be assigned from the import history page.
 async function renderLoose() {
  const host = document.querySelector('#importsSection');
  if (!host || typeof can !== 'function' || !can('SWIFT_IMPORT')) return;
  host.querySelector('.swift-loose')?.remove();
  const loose = await json('/api/swift-messages/unassigned').catch(() => []);
  if (!loose.length) return;
  const options = (typeof data !== 'undefined' ? data : []).map(lc => `<option value="${escape(lc.id)}">${escape(lc.reference)}</option>`).join('');
  host.insertAdjacentHTML('beforeend', `<div class="swift-loose"><h3>Mitteilungen ohne Akte (${loose.length})</h3>${loose.map(m => `<div class="swift-loose-row" data-message="${escape(m.id)}">${messageHtml(m)}<select aria-label="Akte wählen">${options}</select><button type="button" class="secondary" data-assign>Zuordnen</button></div>`).join('')}</div>`);
  host.querySelectorAll('.swift-loose-row [data-assign]').forEach(button => button.onclick = async () => {
   const row = button.closest('.swift-loose-row');
   try {
    await json(`/api/swift-messages/${encodeURIComponent(row.dataset.message)}/assign`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ lcId: row.querySelector('select').value }) });
    await renderLoose();
   } catch (error) { alert(error.message); }
  });
 }
 document.addEventListener('click', event => { if (event.target.closest?.('#appNavImports,[data-app-section="imports"]')) setTimeout(renderLoose, 300); });
})();
