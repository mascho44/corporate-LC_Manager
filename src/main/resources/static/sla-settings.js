/* SLAs (bank profile): a settings page with the rules (activity, start and stop events, target time in banking days) and the SLA status of a file in the cockpit. */
globalThis.LcSla = {
 /** Colour class and word key of a state; running states are green/yellow/red, finished ones neutral. */
 tone(state) { return { ON_TRACK: 'ok', WARNING: 'warn', BREACHED: 'bad', DONE_IN_TIME: 'done', DONE_LATE: 'late', PAUSED: 'paused', NONE: 'none' }[state] || 'none'; },
 label(state) {
  const map = { ON_TRACK: tx('sla.s.onTrack', 'im Plan'), WARNING: tx('sla.s.warning', 'knapp'), BREACHED: tx('sla.s.breached', 'verletzt'), DONE_IN_TIME: tx('sla.s.doneInTime', 'eingehalten'), DONE_LATE: tx('sla.s.doneLate', 'zu spät erledigt'), PAUSED: tx('sla.s.paused', 'pausiert'), NONE: tx('sla.s.none', 'nichts offen') };
  return map[state] || state;
 },
 /** Short text of one clock for the file view. */
 describe(item, formatDate = value => String(value).slice(0, 10)) {
  const c = item.clock;
  if (c.paused) return tx('sla.clockPaused', '{name}: pausiert · {elapsed} von {target} Bankarbeitstagen', { name: item.ruleName, elapsed: c.elapsedDays, target: c.targetDays });
  return c.stoppedAt
   ? tx('sla.clockDone', '{name}: {state} · {elapsed} von {target} Bankarbeitstagen', { name: item.ruleName, state: LcSla.label(c.state), elapsed: c.elapsedDays, target: c.targetDays })
   : tx('sla.clockRunning', '{name}: {state} · fällig {due} · {elapsed} von {target} Bankarbeitstagen', { name: item.ruleName, state: LcSla.label(c.state), due: formatDate(c.dueDate), elapsed: c.elapsedDays, target: c.targetDays });
 },
 /** Adds the SLA block to the cockpit of a file when the tenant has SLA rules (bank profile). */
 async render(container, lcId) {
  let status;
  try { status = await json(`/api/lcs/${encodeURIComponent(lcId)}/sla`); } catch { return; }
  if (!status || !status.instances.length || !container.isConnected) return;
  container.querySelector('.sla-block')?.remove();
  const block = document.createElement('section'); block.className = 'sla-block';
  const head = document.createElement('h3'); head.textContent = tx('sla.title', 'SLA'); const badge = document.createElement('span'); badge.className = 'sla-badge ' + LcSla.tone(status.state); badge.textContent = LcSla.label(status.state); head.append(' ', badge);
  const list = document.createElement('ul'); list.className = 'sla-list';
  status.instances.forEach(item => { const li = document.createElement('li'); li.className = LcSla.tone(item.clock.state); li.textContent = LcSla.describe(item, value => typeof fmtDate === 'function' ? fmtDate(value) : value); list.append(li); });
  block.append(head, list); container.querySelector('.grid')?.before(block) || container.append(block);
 }
};

async function setupSla() {
 if (typeof can !== 'function' || !can('SETTINGS_MANAGE') || currentUser?.role !== 'ADMIN') return; // administrators only
 let rules, catalog, teams = [];
 try { [rules, catalog] = await Promise.all([json('/api/sla/rules'), json('/api/sla/catalog')]); } catch { return; }
 teams = await json('/api/teams').catch(() => []); // not a bank tenant: no SLA page
 const node = (tag, value, cls) => { const n = document.createElement(tag); if (value !== undefined) n.textContent = value; if (cls) n.className = cls; return n; };
 const action = code => tx('audit.act.' + code, code);
 const nav = node('button', tx('sla.nav', 'SLAs')); nav.type = 'button'; nav.id = 'appNavSla'; nav.dataset.i18n = 'sla.nav';
 document.querySelector('#appNav nav').append(nav);
 const section = document.createElement('section'); section.id = 'slaSection'; section.className = 'panel hidden';
 const message = node('p', ''); message.setAttribute('role', 'status');
 const table = document.createElement('div'); const inboxBox = document.createElement('div'); const reportBox = document.createElement('div'); const formBox = document.createElement('div');
 section.append(node('h2', tx('sla.title', 'SLA')), node('p', tx('sla.intro', 'Zielzeiten für Tätigkeiten in Bankarbeitstagen (Montag bis Freitag ohne bundesweite Feiertage). Die Uhr startet mit einem Start-Ereignis an der Akte und stoppt mit einem Stopp-Ereignis.')), message, table, inboxBox, reportBox, formBox);
 document.querySelector('main').append(section);

 async function drawInbox() {
  inboxBox.replaceChildren();
  if (!rules.some(r => r.active && r.activityKey === 'INBOX_ASSIGNMENT')) return;
  let clocks; try { clocks = (await json('/api/sla/inbox')).filter(c => c.state === 'WARNING' || c.state === 'BREACHED'); } catch { return; }
  inboxBox.append(node('h3', tx('sla.inboxTitle', 'Offene Posteingangsdokumente')));
  if (!clocks.length) { inboxBox.append(node('p', tx('sla.inboxNone', 'Keines ist knapp oder überfällig.'))); return; }
  const list = node('ul', undefined, 'sla-list');
  clocks.forEach(c => { const li = node('li', tx('sla.inboxLine', '{name}: {state} · eingegangen {received} · fällig {due}', { name: c.filename, state: LcSla.label(c.state), received: String(c.receivedAt).slice(0, 10), due: c.dueDate }), LcSla.tone(c.state)); list.append(li); });
  inboxBox.append(list);
 }

 let reportDays = 30;
 async function drawReport() {
  reportBox.replaceChildren();
  if (!rules.some(r => r.active && r.activityKey !== 'INBOX_ASSIGNMENT')) return;
  let report; try { report = await json('/api/sla/report?days=' + reportDays); } catch { return; }
  reportBox.append(node('h3', tx('sla.reportTitle', 'Einhaltung der SLAs')));
  const period = document.createElement('select');
  [30, 90, 365].forEach(d => { const o = node('option', tx('sla.reportDays', 'Letzte {n} Tage', { n: d })); o.value = d; period.append(o); });
  period.value = String(reportDays); period.onchange = () => { reportDays = Number(period.value); drawReport(); };
  const csv = node('button', tx('sla.reportCsv', 'Als CSV speichern'), 'secondary'); csv.type = 'button';
  const head = [tx('sla.colRule', 'Regel'), tx('sla.colInTime', 'Eingehalten'), tx('sla.colLate', 'Zu spät'), tx('sla.colRunning', 'Laufend'), tx('sla.colWarning', 'Knapp'), tx('sla.colBreached', 'Verletzt'), tx('sla.colPaused', 'Pausiert'), tx('sla.colQuota', 'Quote (%)')];
  const rows = report.rules.map(r => [r.name, r.doneInTime, r.doneLate, r.running, r.warning, r.breached, r.paused, r.quotaPercent == null ? '' : r.quotaPercent]);
  csv.onclick = () => {
   const cell = v => '"' + String(v).replace(/"/g, '""') + '"';
   const blob = new Blob(['\ufeff' + [head, ...rows].map(row => row.map(cell).join(';')).join('\r\n')], { type: 'text/csv;charset=utf-8' });
   const link = document.createElement('a'); link.href = URL.createObjectURL(blob); link.download = 'sla-einhaltung-' + report.to + '.csv'; link.click(); setTimeout(() => URL.revokeObjectURL(link.href), 1000);
  };
  const bar = node('div', undefined, 'sla-report-bar'); bar.append(period, csv); reportBox.append(bar);
  if (!rows.length) { reportBox.append(node('p', tx('sla.reportNone', 'Noch keine Auswertung möglich.'))); return; }
  const table = node('table', undefined, 'sla-report'); const thead = document.createElement('thead'); const hr = document.createElement('tr');
  head.forEach(h => hr.append(node('th', h))); thead.append(hr); table.append(thead);
  const body = document.createElement('tbody');
  rows.forEach(row => { const tr = document.createElement('tr'); row.forEach((v, i) => tr.append(node(i ? 'td' : 'th', i === 7 && v !== '' ? v + ' %' : String(v)))); body.append(tr); });
  table.append(body); const wrap = node('div', undefined, 'sla-report-wrap'); wrap.append(table); reportBox.append(wrap);
 }
 function draw() {
  drawInbox();
  drawReport();
  table.replaceChildren();
  if (!rules.length) table.append(node('p', tx('sla.none', 'Noch keine SLA-Regeln.')));
  rules.forEach(rule => {
   const row = node('article', undefined, 'membership-row sla-rule' + (rule.active ? '' : ' inactive'));
   const text = node('div'); text.append(node('b', rule.name), node('small', tx('sla.ruleLine', '{days} Bankarbeitstage · Warnung ab {warn} % · von {start} bis {stop}', { days: rule.targetDays, warn: rule.warnPercent, start: rule.startActions.map(action).join(' / '), stop: rule.stopActions.map(action).join(' / ') }) + (rule.escalationTeamId ? ' · ' + tx('sla.escalatesTo', 'Eskalation an {team}', { team: (teams.find(x => x.id === rule.escalationTeamId) || {}).name || '?' }) : '') + (rule.active ? '' : ' · ' + tx('sla.inactive', 'inaktiv'))));
   const edit = node('button', tx('sla.edit', 'Bearbeiten'), 'secondary'); edit.type = 'button'; edit.onclick = () => form(rule);
   const del = node('button', tx('sla.delete', 'Löschen'), 'secondary'); del.type = 'button';
   del.onclick = async () => { if (!await confirmAction(tx('sla.confirmDelete', 'SLA-Regel „{name}“ löschen?', { name: rule.name }))) return; try { await json('/api/sla/rules/' + rule.id, { method: 'DELETE' }); rules = rules.filter(r => r.id !== rule.id); message.textContent = tx('sla.deleted', 'Regel gelöscht.'); draw(); } catch (error) { message.textContent = error.message; } };
   row.append(text, edit, del); table.append(row);
  });
  const add = node('button', tx('sla.add', 'Regel hinzufügen')); add.type = 'button'; add.className = 'sla-add'; add.onclick = () => form(null); table.append(add);
 }
 function pick(select, values) { [...select.options].forEach(o => { o.selected = values.includes(o.value); }); }
 function form(rule) {
  formBox.replaceChildren(); message.textContent = '';
  const f = document.createElement('form'); f.className = 'form-grid sla-form';
  const field = (label, control) => { const l = node('label', label); l.append(control); f.append(l); return control; };
  const template = document.createElement('select'); const blank = node('option', tx('sla.custom', 'Eigene Tätigkeit')); blank.value = ''; template.append(blank);
  catalog.activities.forEach(a => { const o = node('option', globalThis.LcI18n?.language?.() === 'en' ? a.nameEn : a.name); o.value = a.key; template.append(o); });
  if (!rule) field(tx('sla.template', 'Vorlage'), template);
  const name = field(tx('sla.name', 'Name'), document.createElement('input')); name.required = true; name.maxLength = 120;
  const mk = () => { const s = document.createElement('select'); s.multiple = true; s.size = 6; s.dataset.noLayoutSize = ''; catalog.actions.forEach(code => { const o = node('option', action(code) + ' (' + code + ')'); o.value = code; s.append(o); }); return s; };
  const start = field(tx('sla.start', 'Start-Ereignisse'), mk()), stop = field(tx('sla.stop', 'Stopp-Ereignisse'), mk());
  const pause = field(tx('sla.pause', 'Pause-Ereignisse (optional, z. B. Rückfrage an den Kunden)'), mk()), resume = field(tx('sla.resume', 'Fortsetzen-Ereignisse (z. B. Antwort eingegangen)'), mk());
  const days = field(tx('sla.days', 'Zielzeit (Bankarbeitstage)'), document.createElement('input')); days.type = 'number'; days.min = 1; days.max = 60; days.value = 3;
  const warn = field(tx('sla.warn', 'Warnung ab (%)'), document.createElement('input')); warn.type = 'number'; warn.min = 10; warn.max = 99; warn.value = 80;
  const escalation = document.createElement('select'); const none = node('option', tx('sla.noEscalation', 'Keine Eskalation')); none.value = ''; escalation.append(none);
  teams.filter(x => x.active !== false).forEach(x => { const o = node('option', x.name); o.value = x.id; escalation.append(o); });
  if (teams.length) field(tx('sla.escalation', 'Bei Verletzung Auftrag an Team'), escalation);
  const active = document.createElement('input'); active.type = 'checkbox'; active.checked = true; const activeLabel = node('label', tx('sla.active', 'Aktiv')); activeLabel.prepend(active); f.append(activeLabel);
  let key = 'CUSTOM';
  const fill = r => { name.value = r.name; pick(start, r.startActions); pick(stop, r.stopActions); pick(pause, r.pauseActions || []); pick(resume, r.resumeActions || []); days.value = r.targetDays; warn.value = r.warnPercent; active.checked = r.active; escalation.value = r.escalationTeamId || ''; key = r.activityKey || 'CUSTOM'; };
  if (rule) fill(rule);
  template.onchange = () => { const a = catalog.activities.find(x => x.key === template.value); if (a) { fill({ name: globalThis.LcI18n?.language?.() === 'en' ? a.nameEn : a.name, startActions: a.startActions, stopActions: a.stopActions, targetDays: a.defaultDays, warnPercent: 80, active: true, activityKey: a.key }); } else key = 'CUSTOM'; };
  const save = node('button', tx('sla.save', 'Speichern')); save.type = 'submit'; const cancel = node('button', tx('sla.cancel', 'Abbrechen'), 'secondary'); cancel.type = 'button'; cancel.onclick = () => formBox.replaceChildren();
  const bar = document.createElement('div'); bar.className = 'sla-actions'; bar.append(save, cancel); f.append(bar);
  f.onsubmit = async event => {
   event.preventDefault();
   const body = { activityKey: key, name: name.value.trim(), startActions: [...start.selectedOptions].map(o => o.value), stopActions: [...stop.selectedOptions].map(o => o.value), pauseActions: [...pause.selectedOptions].map(o => o.value), resumeActions: [...resume.selectedOptions].map(o => o.value), targetDays: Number(days.value), warnPercent: Number(warn.value), active: active.checked, escalationTeamId: escalation.value || null };
   try {
    const saved = await json(rule ? '/api/sla/rules/' + rule.id : '/api/sla/rules', { method: rule ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
    rules = rule ? rules.map(r => r.id === saved.id ? saved : r) : [...rules, saved].sort((a, b) => a.name.localeCompare(b.name));
    formBox.replaceChildren(); message.textContent = tx('sla.saved', 'Regel gespeichert.'); draw();
   } catch (error) { message.textContent = error.message; }
  };
  formBox.append(f);
 }
 nav.onclick = () => { appNavigate('sla', nav); draw(); };
 draw();
}
