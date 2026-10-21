/* Platform administration: tab "Server log" – the latest log events of the running server, with level filter, search, auto refresh and download. */
(() => {
 const LEVELS = ['ERROR', 'WARN', 'INFO', 'DEBUG'];
 const fmtTime = value => { const d = new Date(value); return Number.isNaN(d.getTime()) ? String(value) : d.toLocaleString(globalThis.LcI18n?.language?.() === 'en' ? 'en-GB' : 'de-DE', { hour12: false }); };
 /** One entry as a plain text line, for display and download. */
 function line(entry) { return `${fmtTime(entry.time)}  ${String(entry.level).padEnd(5)}  [${entry.logger}]  ${entry.message}`; }
 /** The text file content for the download. */
 function asText(entries) { return (entries || []).map(line).join('\n') + '\n'; }
 globalThis.LcServerLog = { line, asText, LEVELS };
 if (typeof document === 'undefined') return;

 const node = (tag, text, cls) => { const n = document.createElement(tag); if (text !== undefined) n.textContent = text; if (cls) n.className = cls; return n; };
 function render(pane) {
  if (pane.dataset.serverLog) { pane.querySelector('[data-refresh]')?.click(); return; }
  pane.dataset.serverLog = '1';
  const level = document.createElement('select'); level.setAttribute('aria-label', tx('sl.level', 'Mindeststufe'));
  [['ERROR', tx('sl.errors', 'Nur Fehler')], ['WARN', tx('sl.warnings', 'Warnungen und Fehler')], ['INFO', tx('sl.info', 'Info und höher')], ['DEBUG', tx('sl.all', 'Alles')]].forEach(([v, t]) => { const o = node('option', t); o.value = v; level.append(o); });
  level.value = 'WARN';
  const search = document.createElement('input'); search.type = 'search'; search.placeholder = tx('sl.search', 'Suchen im Text oder Logger'); search.setAttribute('aria-label', search.placeholder);
  const limit = document.createElement('select'); limit.setAttribute('aria-label', tx('sl.limit', 'Anzahl Zeilen'));
  [200, 500, 1000, 2000].forEach(n => { const o = node('option', tx('sl.lines', '{n} Zeilen', { n })); o.value = String(n); limit.append(o); }); limit.value = '500';
  const refresh = node('button', tx('sl.refresh', 'Aktualisieren'), 'secondary'); refresh.type = 'button'; refresh.dataset.refresh = '';
  const auto = document.createElement('input'); auto.type = 'checkbox'; const autoLabel = node('label'); autoLabel.append(auto, document.createTextNode(' ' + tx('sl.auto', 'Alle 5 Sekunden')));
  const download = node('button', tx('sl.download', 'Als Textdatei laden'), 'secondary'); download.type = 'button';
  const status = node('small'); status.setAttribute('role', 'status');
  const bar = node('div', undefined, 'server-log-bar'); bar.append(level, search, limit, refresh, autoLabel, download);
  const output = node('pre', undefined, 'server-log-output'); output.tabIndex = 0;
  const note = node('small', tx('sl.note', 'Zeigt die letzten Ereignisse dieses Serverprozesses (höchstens 2000, seit dem letzten Start). Passwörter und Tokens werden maskiert. Für ältere Einträge das Container-Log auf dem Server verwenden.'));
  pane.append(node('h3', tx('sl.title', 'Server-Log')), note, bar, status, output);
  let entries = [], timer = null;
  const load = async () => {
   status.textContent = tx('sl.loading', 'Lade …');
   try {
    const params = new URLSearchParams({ level: level.value, q: search.value, limit: limit.value });
    const result = await json('/api/platform/server-log?' + params);
    entries = result.entries;
    const stick = output.scrollTop + output.clientHeight >= output.scrollHeight - 30;
    output.replaceChildren(...(entries.length ? entries.map(e => { const row = node('div', line(e), 'server-log-line ' + String(e.level).toLowerCase()); return row; }) : [node('div', tx('sl.empty', 'Keine passenden Einträge.'))]));
    if (stick) output.scrollTop = output.scrollHeight;
    status.textContent = tx('sl.status', '{n} von {size} gespeicherten Einträgen · seit {since}', { n: entries.length, size: result.size, since: fmtTime(result.since) });
   } catch (error) { status.textContent = tx('sl.failed', 'Server-Log nicht verfügbar:') + ' ' + error.message; }
  };
  refresh.onclick = load; level.onchange = load; limit.onchange = load;
  search.onkeydown = event => { if (event.key === 'Enter') load(); };
  auto.onchange = () => { clearInterval(timer); if (auto.checked) timer = setInterval(() => { if (pane.hidden) return; load(); }, 5000); };
  download.onclick = () => { const url = URL.createObjectURL(new Blob([asText(entries)], { type: 'text/plain;charset=utf-8' })); const a = document.createElement('a'); a.href = url; a.download = 'server-log.txt'; a.click(); setTimeout(() => URL.revokeObjectURL(url), 1000); };
  load();
 }
 globalThis.LcServerLog.render = render;
})();
