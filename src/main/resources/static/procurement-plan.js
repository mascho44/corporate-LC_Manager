/* Procurement plan for corporate tenants: what the LC requires, what is already there, what is missing, with a traffic light by due date. */
(() => {
 const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
 const tr = (key, de, values = {}) => { const i = globalThis.LcI18n, s = i?.t ? i.t(key, values) : null; return s && s !== key ? s : String(de).replace(/\{(\w+)\}/g, (_, n) => String(values[n] ?? `{${n}}`)); };
 const label = risk => ({ DONE: tr('pl.done','Vorhanden'), OVERDUE: tr('pl.overdue','Überfällig'), CRITICAL: tr('pl.critical','Kritisch'), SOON: tr('pl.soon','Bald fällig'), OK: tr('pl.ok','Im Plan'), UNKNOWN: tr('pl.unknown','Unklar') })[risk];
 const ICON = { DONE: '✓', OVERDUE: '!', CRITICAL: '!', SOON: '●', OK: '●', UNKNOWN: '?' };
 const statusText = key => ({ NOT_STARTED: tr('pl.notStarted','Nicht begonnen'), IN_PROGRESS: tr('pl.inProgress','In Arbeit'), REQUESTED: tr('pl.requested','Beantragt / bestellt') })[key];
 const STATUS_KEYS = ['NOT_STARTED', 'IN_PROGRESS', 'REQUESTED'];
 const date = value => value ? new Date(value + 'T00:00:00').toLocaleDateString(globalThis.LcI18n?.language?.() === 'en' ? 'en-GB' : 'de-DE') : '–';
 const when = days => days == null ? '' : days < 0 ? tr('pl.daysOverdue','{n} Tage überfällig',{n:-days}) : days === 0 ? tr('pl.today','heute') : tr('pl.inDays','in {n} Tagen',{n:days});
 const badge = risk => `<span class="plan-light plan-${risk.toLowerCase()}" title="${label(risk)}"><span aria-hidden="true">${ICON[risk]}</span> ${label(risk)}</span>`;

 function headline(plan) {
  if (plan.latestShipment == null) return tr('pl.noShipment','Kein spätester Versandtermin (:44C:) erfasst – Fristen können nicht berechnet werden.');
  const base = tr('pl.latest','Spätester Versand {date} ({when})',{date:date(plan.latestShipment),when:when(plan.daysToShipment)});
  if (plan.shipmentRisk === 'DONE') return `${base} · ${tr('pl.transportThere','Transportdokument liegt vor.')}`;
  return `${base} · ${tr('pl.untilThen','Bis dahin muss die Ware verladen und das Transportdokument (B/L, AWB, CMR) vorliegen.')}`;
 }
 function statusCell(item, editable) {
  if (item.present) return tr('pl.uploaded','Hochgeladen');
  if (!item.documentType) return tr('pl.missing','Fehlt noch');
  if (!editable) return `${tr('pl.missing','Fehlt noch')}<br><small>${esc(statusText(item.status) || '')}${item.note ? ' · ' + esc(item.note) : ''}</small>`;
  const options = STATUS_KEYS.map(key => `<option value="${key}"${key === item.status ? ' selected' : ''}>${statusText(key)}</option>`).join('');
  return `${tr('pl.missing','Fehlt noch')}<br><select data-plan-status data-requirement="${esc(item.requirement)}" aria-label="${tr('pl.statusLabel','Bearbeitungsstand')}">${options}</select>` +
   `<br><input type="text" data-plan-note maxlength="500" placeholder="${tr('pl.note','Notiz')}" value="${esc(item.note || '')}" aria-label="${tr('pl.noteLabel','Notiz zum Dokument')}">`;
 }
 function itemRow(item, editable) {
  return `<tr class="plan-row plan-row-${item.risk.toLowerCase()}"><td data-label="${tr('pl.colLight','Ampel')}">${badge(item.risk)}</td><td data-label="${tr('pl.colDocument','Dokument')}"><b>${esc(item.documentName || tr('pl.notRecognised','Nicht erkannt'))}</b><br><small>${esc(item.requirement)}</small>${item.hint ? `<br><small class="plan-hint">${esc(globalThis.LcI18n?.language?.() === 'en' && item.hintEn ? item.hintEn : item.hint)}</small>` : ''}</td>` +
   `<td data-label="${tr('pl.colStatus','Stand')}">${statusCell(item, editable)}</td><td data-label="${tr('pl.colDue','Zieltermin')}">${item.dueDate ? date(item.dueDate) + '<br><small>' + when(item.daysLeft) + '</small>' : '–'}</td></tr>`;
 }
 function planHtml(plan, options = {}) {
  if (!plan || !plan.items) return '';
  const done = plan.items.length - plan.open;
  const counts = `<p class="plan-counts"><span class="plan-light plan-done">✓ ${tr('pl.nAvailable','{n} vorhanden',{n:done})}</span> <span class="plan-light plan-ok">● ${tr('pl.nOpen','{n} offen',{n:plan.open - plan.overdue - plan.critical})}</span>` +
   `${plan.critical ? ` <span class="plan-light plan-critical">! ${tr('pl.nCritical','{n} kritisch',{n:plan.critical})}</span>` : ''}${plan.overdue ? ` <span class="plan-light plan-overdue">! ${tr('pl.nOverdue','{n} überfällig',{n:plan.overdue})}</span>` : ''}</p>`;
  const table = plan.items.length ? `<div class="plan-scroll"><table class="plan-table"><thead><tr><th>${tr('pl.colLight','Ampel')}</th><th>${tr('pl.colDocument','Dokument')}</th><th>${tr('pl.colStatus','Stand')}</th><th>${tr('pl.colDue','Zieltermin')}</th></tr></thead><tbody>${plan.items.map(item => itemRow(item, options.canEdit)).join('')}</tbody></table></div>`
   : `<p>${tr('lc.noRequirements','Keine Dokumentenanforderungen erkannt.')}</p>`;
  return `<section class="procurement-plan"><div class="plan-head"><h3>${tr('pl.title','Beschaffungsplan')}</h3><span>${options.canSettings ? `<button type="button" class="secondary" data-plan-lead-times>${tr('pl.leadTimes','Vorlaufzeiten')}</button> ` : ''}${badge(plan.shipmentRisk)}</span></div><p>${esc(headline(plan))}</p>${counts}${table}` +
   `<p class="plan-note"><small>${tr('pl.footnote','Zieltermine zählen die eingestellten Vorlaufzeiten in Arbeitstagen rückwärts vom spätesten Versand (ohne Feiertage). Das Ziel dient der rechtzeitigen Beschaffung und ersetzt keine Prüfung durch die Bank.')}</small></p></section>`;
 }
 /** Compact form for the LC list: shipment light, days to shipment and what is still missing. */
 function chipHtml(summary) {
  if (!summary) return '';
  const left = summary.daysToShipment == null ? tr('pl.noDate','kein Versandtermin') : summary.shipmentRisk === 'DONE' ? tr('pl.shipped','versandt') : tr('pl.shipWhen','Versand {when}',{when:when(summary.daysToShipment)});
  const missing = summary.open ? (summary.open === 1 ? tr('pl.missingOne','1 Dokument fehlt') : tr('pl.missingMany','{n} Dokumente fehlen',{n:summary.open})) : tr('pl.complete','komplett');
  const next = summary.nextDocument && summary.nextDue && summary.open ? ' · ' + tr('pl.next','als Nächstes {doc} bis {date}',{doc:esc(summary.nextDocument),date:date(summary.nextDue)}) : '';
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
  catch (error) { alert(tr('pl.statusFailed','Stand konnte nicht gespeichert werden') + ': ' + error.message); }
 });
 document.addEventListener('change', async event => {
  const input = event.target.closest?.('[data-plan-note]'); if (!input || typeof activeLc === 'undefined' || !activeLc) return;
  const select = input.closest('td').querySelector('[data-plan-status]'), lcId = activeLc.id;
  try { await json(`/api/lcs/${encodeURIComponent(lcId)}/procurement-status`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ requirement: select.dataset.requirement, status: select.value, note: input.value }) }); }
  catch (error) { alert(tr('pl.noteFailed','Notiz konnte nicht gespeichert werden') + ': ' + error.message); }
 });
 document.addEventListener('click', async event => {
  if (!event.target.closest?.('[data-plan-lead-times]')) return;
  const dialog = document.createElement('dialog'); dialog.className = 'wide-dialog plan-lead-dialog'; document.body.append(dialog);
  dialog.onclose = () => dialog.remove();
  const draw = list => {
   dialog.innerHTML = `<div class="dialoghead"><h2>${tr('pl.leadTimes','Vorlaufzeiten')}</h2><button type="button" data-close>${tr('common.close','Schließen')}</button></div><p>${tr('pl.leadIntro','Arbeitstage, die ein Dokument vor dem spätesten Versand vorliegen soll. Gilt für alle LC-Akten dieses Mandanten. Transportdokumente sind immer am Versandtag fällig.')}</p>` +
    `<table class="plan-table"><thead><tr><th>${tr('pl.colDocument','Dokument')}</th><th>${tr('pl.workingDays','Arbeitstage')}</th><th>${tr('pl.default','Standard')}</th><th></th></tr></thead><tbody>${list.map(l => `<tr><td>${esc(l.documentName)}</td><td><input type="number" min="0" max="60" value="${l.workingDays}" data-lead="${l.documentType}" aria-label="${tr('pl.workingDays','Arbeitstage')} ${esc(l.documentName)}"></td><td>${l.defaultDays}${l.custom ? ' · ' + tr('pl.custom','angepasst') : ''}</td><td>${l.custom ? `<button type="button" class="secondary" data-lead-reset="${l.documentType}">${tr('pl.default','Standard')}</button>` : ''}</td></tr>`).join('')}</tbody></table><p data-lead-error role="alert"></p>`;
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
