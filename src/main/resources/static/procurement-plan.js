/* Procurement plan for corporate tenants: what the LC requires, what is already there, what is missing, with a traffic light by due date. */
(() => {
 const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
 const LABEL = { DONE: 'Vorhanden', OVERDUE: 'Überfällig', CRITICAL: 'Kritisch', SOON: 'Bald fällig', OK: 'Im Plan', UNKNOWN: 'Unklar' };
 const ICON = { DONE: '✓', OVERDUE: '!', CRITICAL: '!', SOON: '●', OK: '●', UNKNOWN: '?' };
 const STATUS = { NOT_STARTED: 'Nicht begonnen', IN_PROGRESS: 'In Arbeit', REQUESTED: 'Beantragt / bestellt' };
 const date = value => value ? new Date(value + 'T00:00:00').toLocaleDateString('de-DE') : '–';
 const when = days => days == null ? '' : days < 0 ? `${-days} Tage überfällig` : days === 0 ? 'heute' : `in ${days} Tagen`;
 const badge = risk => `<span class="plan-light plan-${risk.toLowerCase()}" title="${LABEL[risk]}"><span aria-hidden="true">${ICON[risk]}</span> ${LABEL[risk]}</span>`;

 function headline(plan) {
  if (plan.latestShipment == null) return 'Kein spätester Versandtermin (:44C:) erfasst – Fristen können nicht berechnet werden.';
  const base = `Spätester Versand ${date(plan.latestShipment)} (${when(plan.daysToShipment)})`;
  if (plan.shipmentRisk === 'DONE') return `${base} · Transportdokument liegt vor.`;
  return `${base} · Bis dahin muss die Ware verladen und das Transportdokument (B/L, AWB, CMR) vorliegen.`;
 }
 function statusCell(item, editable) {
  if (item.present) return 'Hochgeladen';
  if (!item.documentType) return 'Fehlt noch';
  if (!editable) return `Fehlt noch<br><small>${esc(STATUS[item.status] || '')}${item.note ? ' · ' + esc(item.note) : ''}</small>`;
  const options = Object.entries(STATUS).map(([key, label]) => `<option value="${key}"${key === item.status ? ' selected' : ''}>${label}</option>`).join('');
  return `Fehlt noch<br><select data-plan-status data-requirement="${esc(item.requirement)}" aria-label="Bearbeitungsstand">${options}</select>` +
   `<br><input type="text" data-plan-note maxlength="500" placeholder="Notiz" value="${esc(item.note || '')}" aria-label="Notiz zum Dokument">`;
 }
 function itemRow(item, editable) {
  return `<tr class="plan-row plan-row-${item.risk.toLowerCase()}"><td data-label="Ampel">${badge(item.risk)}</td><td data-label="Dokument"><b>${esc(item.documentName || 'Nicht erkannt')}</b><br><small>${esc(item.requirement)}</small>${item.hint ? `<br><small class="plan-hint">${esc(item.hint)}</small>` : ''}</td>` +
   `<td data-label="Stand">${statusCell(item, editable)}</td><td data-label="Zieltermin">${item.dueDate ? date(item.dueDate) + '<br><small>' + when(item.daysLeft) + '</small>' : '–'}</td></tr>`;
 }
 function planHtml(plan, options = {}) {
  if (!plan || !plan.items) return '';
  const done = plan.items.length - plan.open;
  const counts = `<p class="plan-counts"><span class="plan-light plan-done">✓ ${done} vorhanden</span> <span class="plan-light plan-ok">● ${plan.open - plan.overdue - plan.critical} offen</span>` +
   `${plan.critical ? ` <span class="plan-light plan-critical">! ${plan.critical} kritisch</span>` : ''}${plan.overdue ? ` <span class="plan-light plan-overdue">! ${plan.overdue} überfällig</span>` : ''}</p>`;
  const table = plan.items.length ? `<div class="plan-scroll"><table class="plan-table"><thead><tr><th>Ampel</th><th>Dokument</th><th>Stand</th><th>Zieltermin</th></tr></thead><tbody>${plan.items.map(item => itemRow(item, options.canEdit)).join('')}</tbody></table></div>`
   : '<p>Keine Dokumentenanforderungen erkannt.</p>';
  return `<section class="procurement-plan"><div class="plan-head"><h3>Beschaffungsplan</h3><span>${options.canSettings ? '<button type="button" class="secondary" data-plan-lead-times>Vorlaufzeiten</button> ' : ''}${badge(plan.shipmentRisk)}</span></div><p>${esc(headline(plan))}</p>${counts}${table}` +
   `<p class="plan-note"><small>Zieltermine zählen die eingestellten Vorlaufzeiten in Arbeitstagen rückwärts vom spätesten Versand (ohne Feiertage). Das Ziel dient der rechtzeitigen Beschaffung und ersetzt keine Prüfung durch die Bank.</small></p></section>`;
 }
 /** Compact form for the LC list: shipment light, days to shipment and what is still missing. */
 function chipHtml(summary) {
  if (!summary) return '';
  const left = summary.daysToShipment == null ? 'kein Versandtermin' : summary.shipmentRisk === 'DONE' ? 'versandt' : `Versand ${when(summary.daysToShipment)}`;
  const missing = summary.open ? `${summary.open} Dokument${summary.open === 1 ? '' : 'e'} fehlen` : 'komplett';
  const next = summary.nextDocument && summary.nextDue && summary.open ? ` · als Nächstes ${esc(summary.nextDocument)} bis ${date(summary.nextDue)}` : '';
  return `<span class="plan-chip">${badge(summary.shipmentRisk)} <small>${left} · ${missing}${next}</small></span>`;
 }
 globalThis.LcProcurementPlan = { planHtml, headline, chipHtml };
 if (typeof document === 'undefined' || !document.body || typeof show !== 'function') return;
 const can_ = permission => typeof can === 'function' && can(permission);
 async function renderPlan(id) {
  const overview = document.querySelector('#detail .dossier-section[data-section="overview"]');
  if (!overview) return;
  if (typeof activeLc !== 'undefined' && activeLc?.partyRole === 'APPLICANT') { overview.querySelector('.procurement-plan')?.remove(); return; }
  const plan = await json(`/api/lcs/${encodeURIComponent(id)}/procurement-plan`).catch(() => null);
  if (typeof activeLc === 'undefined' || activeLc?.id !== id) return;
  overview.querySelector('.procurement-plan')?.remove();
  const html = planHtml(plan, { canEdit: can_('LC_EDIT'), canSettings: can_('SETTINGS_MANAGE') });
  if (html) overview.insertAdjacentHTML('afterbegin', html);
 }
 const base = show;
 show = async function (id) {
  await base(id);
  try { if (globalThis.LcWorkspaceModules?.corporate) await renderPlan(id); } catch (ignored) { }
 };
 document.addEventListener('change', async event => {
  const select = event.target.closest?.('[data-plan-status]'); if (!select || typeof activeLc === 'undefined' || !activeLc) return;
  const lcId = activeLc.id, note = select.closest('td').querySelector('[data-plan-note]')?.value || '';
  try { await json(`/api/lcs/${encodeURIComponent(lcId)}/procurement-status`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ requirement: select.dataset.requirement, status: select.value, note }) }); await renderPlan(lcId); }
  catch (error) { alert('Stand konnte nicht gespeichert werden: ' + error.message); }
 });
 document.addEventListener('change', async event => {
  const input = event.target.closest?.('[data-plan-note]'); if (!input || typeof activeLc === 'undefined' || !activeLc) return;
  const select = input.closest('td').querySelector('[data-plan-status]'), lcId = activeLc.id;
  try { await json(`/api/lcs/${encodeURIComponent(lcId)}/procurement-status`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ requirement: select.dataset.requirement, status: select.value, note: input.value }) }); }
  catch (error) { alert('Notiz konnte nicht gespeichert werden: ' + error.message); }
 });
 document.addEventListener('click', async event => {
  if (!event.target.closest?.('[data-plan-lead-times]')) return;
  const dialog = document.createElement('dialog'); dialog.className = 'wide-dialog plan-lead-dialog'; document.body.append(dialog);
  dialog.onclose = () => dialog.remove();
  const draw = list => {
   dialog.innerHTML = `<div class="dialoghead"><h2>Vorlaufzeiten</h2><button type="button" data-close>Schließen</button></div><p>Arbeitstage, die ein Dokument vor dem spätesten Versand vorliegen soll. Gilt für alle LC-Akten dieses Mandanten. Transportdokumente sind immer am Versandtag fällig.</p>` +
    `<table class="plan-table"><thead><tr><th>Dokument</th><th>Arbeitstage</th><th>Standard</th><th></th></tr></thead><tbody>${list.map(l => `<tr><td>${esc(l.documentName)}</td><td><input type="number" min="0" max="60" value="${l.workingDays}" data-lead="${l.documentType}" aria-label="Arbeitstage ${esc(l.documentName)}"></td><td>${l.defaultDays}${l.custom ? ' · angepasst' : ''}</td><td>${l.custom ? `<button type="button" class="secondary" data-lead-reset="${l.documentType}">Standard</button>` : ''}</td></tr>`).join('')}</tbody></table><p data-lead-error role="alert"></p>`;
   dialog.querySelector('[data-close]').onclick = () => dialog.close();
  };
  try { draw(await json('/api/procurement/lead-times')); dialog.showModal(); } catch (error) { dialog.remove(); alert(error.message); return; }
  dialog.addEventListener('change', async e => {
   const input = e.target.closest('[data-lead]'); if (!input) return;
   try { draw(await json(`/api/procurement/lead-times/${input.dataset.lead}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ workingDays: Number(input.value) }) })); }
   catch (error) { dialog.querySelector('[data-lead-error]').textContent = error.message; }
  });
  dialog.addEventListener('click', async e => {
   const reset = e.target.closest('[data-lead-reset]'); if (!reset) return;
   try { draw(await json(`/api/procurement/lead-times/${reset.dataset.leadReset}`, { method: 'DELETE' })); } catch (error) { dialog.querySelector('[data-lead-error]').textContent = error.message; }
  });
  dialog.addEventListener('close', () => { if (typeof activeLc !== 'undefined' && activeLc) renderPlan(activeLc.id); });
 });
 // LC list: one request for all running LCs after each render of the list.
 if (typeof render === 'function') {
  const renderBase = render;
  let loading = false;
  render = function () {
   renderBase();
   if (!globalThis.LcWorkspaceModules?.corporate || loading) return;
   loading = true;
   json('/api/procurement-overview').then(list => {
    const byId = new Map((list || []).map(item => [item.lcId, item]));
    document.querySelectorAll('#list .dossier-row').forEach(row => {
     row.querySelector('.plan-chip')?.remove();
     const html = chipHtml(byId.get(row.dataset.id));
     if (html) row.querySelector('div')?.insertAdjacentHTML('beforeend', html);
    });
   }).catch(() => { }).finally(() => { loading = false; });
  };
 }
})();
