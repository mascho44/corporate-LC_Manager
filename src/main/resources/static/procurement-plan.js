/* Procurement plan for corporate tenants: what the LC requires, what is already there, what is missing, with a traffic light by due date. */
(() => {
 const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
 const LABEL = { DONE: 'Vorhanden', OVERDUE: 'Überfällig', CRITICAL: 'Kritisch', SOON: 'Bald fällig', OK: 'Im Plan', UNKNOWN: 'Unklar' };
 const ICON = { DONE: '✓', OVERDUE: '!', CRITICAL: '!', SOON: '●', OK: '●', UNKNOWN: '?' };
 const date = value => value ? new Date(value + 'T00:00:00').toLocaleDateString('de-DE') : '–';
 const when = days => days == null ? '' : days < 0 ? `${-days} Tage überfällig` : days === 0 ? 'heute' : `in ${days} Tagen`;
 const badge = risk => `<span class="plan-light plan-${risk.toLowerCase()}" title="${LABEL[risk]}"><span aria-hidden="true">${ICON[risk]}</span> ${LABEL[risk]}</span>`;

 function headline(plan) {
  if (plan.latestShipment == null) return 'Kein spätester Versandtermin (:44C:) erfasst – Fristen können nicht berechnet werden.';
  const base = `Spätester Versand ${date(plan.latestShipment)} (${when(plan.daysToShipment)})`;
  if (plan.shipmentRisk === 'DONE') return `${base} · Transportdokument liegt vor.`;
  return `${base} · Bis dahin muss die Ware verladen und das Transportdokument (B/L, AWB, CMR) vorliegen.`;
 }
 function itemRow(item) {
  return `<tr class="plan-row plan-row-${item.risk.toLowerCase()}"><td>${badge(item.risk)}</td><td><b>${esc(item.documentName || 'Nicht erkannt')}</b><br><small>${esc(item.requirement)}</small></td>` +
   `<td>${item.present ? 'Hochgeladen' : 'Fehlt noch'}</td><td>${item.dueDate ? date(item.dueDate) + '<br><small>' + when(item.daysLeft) + '</small>' : '–'}</td><td><small>${esc(item.hint)}</small></td></tr>`;
 }
 function planHtml(plan) {
  if (!plan || !plan.items) return '';
  const done = plan.items.length - plan.open;
  const counts = `<p class="plan-counts"><span class="plan-light plan-done">✓ ${done} vorhanden</span> <span class="plan-light plan-ok">● ${plan.open - plan.overdue - plan.critical} offen</span>` +
   `${plan.critical ? ` <span class="plan-light plan-critical">! ${plan.critical} kritisch</span>` : ''}${plan.overdue ? ` <span class="plan-light plan-overdue">! ${plan.overdue} überfällig</span>` : ''}</p>`;
  const table = plan.items.length ? `<div class="plan-scroll"><table class="plan-table"><thead><tr><th>Ampel</th><th>Dokument</th><th>Stand</th><th>Zieltermin</th><th>Hinweis</th></tr></thead><tbody>${plan.items.map(itemRow).join('')}</tbody></table></div>`
   : '<p>Keine Dokumentenanforderungen erkannt.</p>';
  return `<section class="procurement-plan"><div class="plan-head"><h3>Beschaffungsplan</h3>${badge(plan.shipmentRisk)}</div><p>${esc(headline(plan))}</p>${counts}${table}` +
   `<p class="plan-note"><small>Zieltermine sind Richtwerte in Arbeitstagen vor dem spätesten Versand (ohne Feiertage). Das Ziel dient der rechtzeitigen Beschaffung und ersetzt keine Prüfung durch die Bank.</small></p></section>`;
 }
 globalThis.LcProcurementPlan = { planHtml, headline };
 if (typeof document === 'undefined' || !document.body || typeof show !== 'function') return;
 const base = show;
 show = async function (id) {
  await base(id);
  try {
   if (!globalThis.LcWorkspaceModules?.corporate) return;
   const overview = document.querySelector('#detail .dossier-section[data-section="overview"]');
   if (!overview) return;
   const plan = await json(`/api/lcs/${encodeURIComponent(id)}/procurement-plan`).catch(() => null);
   if (typeof activeLc === 'undefined' || activeLc?.id !== id) return;
   overview.querySelector('.procurement-plan')?.remove();
   const html = planHtml(plan);
   if (html) overview.insertAdjacentHTML('afterbegin', html);
  } catch (ignored) { }
 };
})();
