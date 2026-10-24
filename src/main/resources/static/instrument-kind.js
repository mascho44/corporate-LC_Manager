/* File kind marker for corporate tenants: Garantie, Export-LC (we are beneficiary), Import-LC (we are applicant). Badge in list and file, plus a filter above the list. Presentation only; the data is instrumentType and partyRole. */
(() => {
 if (typeof document === 'undefined' || !document.body) return;
 const tr = (key, de, values) => typeof tx === 'function' ? tx(key, de, values) : de;
 const modules = () => globalThis.LcWorkspaceModules || {};
 const corporateOnly = () => modules().corporate === true && modules().bank !== true;
 const kindOf = lc => !lc ? null : lc.instrumentType === 'GUARANTEE' ? 'GUARANTEE' : lc.partyRole === 'APPLICANT' ? 'IMPORT' : lc.partyRole === 'BENEFICIARY' ? 'EXPORT' : null;
 const label = kind => kind === 'GUARANTEE' ? tr('kind.guarantee', 'Garantie') : kind === 'IMPORT' ? tr('kind.import', 'Import-LC') : tr('kind.export', 'Export-LC');
 let filter = '';
 function badgeRows() {
  if (!corporateOnly() || typeof data === 'undefined') return;
  document.querySelectorAll('#list .dossier-row[data-id]').forEach(row => {
   const kind = kindOf(data.find(x => x.id === row.dataset.id));
   row.hidden = Boolean(filter && kind !== filter);
   const title = row.querySelector('strong');
   if (kind && kind !== 'GUARANTEE' && title && !title.querySelector('.kind-badge')) title.insertAdjacentHTML('beforeend', ' <span class="badge kind-badge">' + label(kind) + '</span>');
  });
 }
 function filterSelect() {
  if (!corporateOnly() || document.querySelector('#lcKindFilter')) return;
  const bar = document.querySelector('#lcSection .lc-filters'); if (!bar) return;
  const select = document.createElement('select'); select.id = 'lcKindFilter'; select.setAttribute('aria-label', tr('kind.filter', 'Akten-Typ'));
  select.innerHTML = [['', tr('kind.all', 'Alle Typen')], ['EXPORT', tr('kind.export', 'Export-LC')], ['IMPORT', tr('kind.import', 'Import-LC')], ['GUARANTEE', tr('kind.guarantee', 'Garantie')]].map(([v, t]) => `<option value="${v}">${t}</option>`).join('');
  select.onchange = () => { filter = select.value; badgeRows(); };
  bar.append(select);
 }
 if (typeof render === 'function') {
  const renderBase = render;
  render = function () { const result = renderBase.apply(this, arguments); try { filterSelect(); badgeRows(); } catch (ignored) { } return result; };
 }
 if (typeof show === 'function') {
  const showBase = show;
  show = async function (id) {
   await showBase(id);
   try {
    const heading = document.querySelector('#detail h2'), kind = corporateOnly() ? kindOf(activeLc) : null;
    if (kind && kind !== 'GUARANTEE' && heading && !heading.querySelector('.kind-badge')) heading.insertAdjacentHTML('beforeend', ' <span class="badge kind-badge">' + label(kind) + '</span>');
   } catch (ignored) { }
  };
 }
})();
