/* Optional AI support: tenant switch and non-binding hints across the documents of a file. Suggestions per document live in rule-facts.js. */
(() => {
 const ORDER = { HIGH: 0, MEDIUM: 1, LOW: 2 };
 const LABEL = { HIGH: 'Hoch', MEDIUM: 'Mittel', LOW: 'Niedrig' };
 /** Most serious first; unknown severities count as low. */
 function sortHints(hints) {
  return [...(hints || [])].sort((a, b) => (ORDER[a.severity] ?? 2) - (ORDER[b.severity] ?? 2));
 }
 globalThis.LcAi = { sortHints, LABEL };
 if (typeof document === 'undefined' || !document.body) return;

 const node = (tag, text, className) => { const n = document.createElement(tag); if (text !== undefined) n.textContent = text; if (className) n.className = className; return n; };
 let status = null, checked = 0;

 async function loadStatus() {
  if (typeof json !== 'function') return null;
  if (status && Date.now() - checked < 60000) return status;
  try { status = await json('/api/ai/status'); checked = Date.now(); } catch (ignored) { status = null; }
  return status;
 }
 async function reveal() {
  const buttons = [...document.querySelectorAll('[data-ai-hints][hidden]')];
  if (!buttons.length) return;
  const s = await loadStatus();
  if (s && s.enabled) buttons.forEach(b => { b.hidden = false; });
 }

 function dialog() {
  let d = document.querySelector('#aiHintsDialog');
  if (d) return d;
  d = document.createElement('dialog'); d.id = 'aiHintsDialog';
  const head = node('div', undefined, 'dialoghead');
  head.append(node('h2', 'KI-Hinweise (unverbindlich)'));
  const close = node('button', '×', 'ghost'); close.type = 'button'; close.onclick = () => d.close(); head.append(close);
  d.append(head, node('p', 'Anregungen eines Sprachmodells aus den Dokumenttexten, jeweils mit Zitat. Sie sind keine Prüfergebnisse und fließen nicht in die Regelprüfung ein. Bitte am Original nachprüfen.', 'ai-note'), node('div', undefined, 'ai-list'));
  document.body.append(d);
  return d;
 }
 async function showHints(button) {
  const lcId = typeof activeLc !== 'undefined' && activeLc ? activeLc.id : null;
  if (!lcId || button.disabled) return;
  const d = dialog(), list = d.querySelector('.ai-list');
  button.disabled = true; const label = button.textContent; button.textContent = 'KI liest die Dokumente … (kann eine Minute dauern)';
  try {
   const hints = sortHints(await json('/api/lcs/' + lcId + '/ai-hints', { method: 'POST' }));
   list.replaceChildren();
   if (!hints.length) list.append(node('p', 'Die KI hat keine belegbaren Auffälligkeiten gefunden.'));
   hints.forEach(h => {
    const item = node('div', undefined, 'ai-hint ai-' + String(h.severity || 'LOW').toLowerCase());
    item.append(node('b', (LABEL[h.severity] || 'Niedrig') + ' · ' + h.check), node('small', (h.documents || []).join(', ')), node('p', h.explanation || ''), node('blockquote', '„' + (h.evidence || '') + '“'));
    list.append(item);
   });
   d.showModal();
  } catch (error) { alert('KI-Hinweise nicht verfügbar: ' + error.message); }
  finally { button.disabled = false; button.textContent = label; }
 }
 document.addEventListener('click', event => { const b = event.target.closest('[data-ai-hints]'); if (b) showHints(b); });

 async function buildSettings(host) {
  const box = node('section', undefined, 'scan-profile'); box.id = 'aiSettingsBox';
  const message = node('p', ''); message.setAttribute('role', 'status');
  const checkbox = document.createElement('input'); checkbox.type = 'checkbox';
  const label = node('label', ' KI-Vorschläge für diesen Mandanten aktivieren'); label.prepend(checkbox);
  const info = node('small', '');
  box.append(node('h3', 'KI-Vorschläge'), node('p', 'Optional und standardmäßig aus. Bei Aktivierung gehen Dokumenttexte an den vom Betreiber konfigurierten KI-Dienst. Vorschläge werden nie automatisch gespeichert und ersetzen keine Prüfung.'), label, info, message);
  host.append(box);
  let view = null;
  async function load() {
   view = await json('/api/ai/status'); checkbox.checked = !!view.enabled; checkbox.disabled = !view.available;
   info.textContent = view.available ? `Dienst: ${view.provider} · Modell ${view.model}` : 'In dieser Installation ist kein KI-Dienst konfiguriert.';
   message.textContent = view.changedBy ? `Zuletzt geändert von ${view.changedBy}.` : '';
  }
  checkbox.onchange = async () => {
   checkbox.disabled = true;
   try { view = await json('/api/ai/settings', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ enabled: checkbox.checked }) }); status = null; message.textContent = checkbox.checked ? 'KI-Vorschläge sind aktiv.' : 'KI-Vorschläge sind aus.'; }
   catch (error) { message.textContent = error.message; checkbox.checked = !checkbox.checked; }
   finally { checkbox.disabled = !(view && view.available); }
  };
  try { await load(); } catch (error) { message.textContent = error.message; }
 }
 const wait = setInterval(() => {
  const host = document.querySelector('#tenantSection');
  if (host && typeof can === 'function' && typeof currentUser !== 'undefined' && currentUser) {
   clearInterval(wait);
   if (can('SETTINGS_MANAGE') && !host.querySelector('#aiSettingsBox')) buildSettings(host);
  }
 }, 500);
 setInterval(reveal, 1500);
})();
