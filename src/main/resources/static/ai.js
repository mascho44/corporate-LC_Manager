/* Optional AI support: tenant switch and non-binding hints across the documents of a file. Suggestions per document live in rule-facts.js. */
(() => {
 const ORDER = { HIGH: 0, MEDIUM: 1, LOW: 2 };
 const LABEL = { HIGH: 'Hoch', MEDIUM: 'Mittel', LOW: 'Niedrig' };
 /** Most serious first; unknown severities count as low. */
 function sortHints(hints) {
  return [...(hints || [])].sort((a, b) => (ORDER[a.severity] ?? 2) - (ORDER[b.severity] ?? 2));
 }
/** One line telling whose configuration is in effect. */
 function describeSource(view) {
  if (!view) return '';
  if (view.source === 'mandant') return `Aktiv konfiguriert: eigener Dienst (${view.provider}, Modell ${view.model}).`;
  if (view.source === 'betreiber') return `Es gilt der Standard des Betreibers (${view.provider}, Modell ${view.model}); hier kann der Mandant einen eigenen Dienst hinterlegen.`;
  return view.available ? 'Noch kein KI-Dienst konfiguriert.' : 'In dieser Installation sind noch keine KI-Dienste freigegeben.';
 }
 globalThis.LcAi = { sortHints, LABEL, describeSource };
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
  const field = (label, input) => { const l = node('label', label + ' '); l.append(input); return l; };
  const provider = document.createElement('select');
  const baseUrl = document.createElement('input'); baseUrl.type = 'url'; baseUrl.maxLength = 300; baseUrl.autocomplete = 'off';
  const model = document.createElement('input'); model.type = 'text'; model.maxLength = 100; model.autocomplete = 'off';
  const key = document.createElement('input'); key.type = 'password'; key.maxLength = 500; key.autocomplete = 'new-password';
  const timeout = document.createElement('input'); timeout.type = 'number'; timeout.min = 10; timeout.max = 600; timeout.placeholder = '120';
  const save = node('button', 'Speichern'); save.type = 'button';
  const test = node('button', 'Verbindung testen'); test.type = 'button'; test.className = 'secondary';
  const clear = node('button', 'Schlüssel entfernen'); clear.type = 'button'; clear.className = 'secondary';
  const checkbox = document.createElement('input'); checkbox.type = 'checkbox';
  const enable = node('label', ' KI-Vorschläge für diesen Mandanten aktivieren'); enable.prepend(checkbox);
  const info = node('small', ''), hosts = node('small', '');
  const actions = node('p'); actions.append(save, ' ', test, ' ', clear);
  box.append(node('h3', 'KI-Vorschläge'),
   node('p', 'Optional und standardmäßig aus. Jeder Mandant hinterlegt hier seinen eigenen KI-Dienst und Schlüssel; Dokumenttexte gehen nur an diesen Dienst. Vorschläge werden nie automatisch gespeichert und ersetzen keine Prüfung.'),
   info, field('Anbieter', provider), field('Adresse', baseUrl), field('Modell', model), field('API-Schlüssel', key), field('Zeitlimit (Sekunden)', timeout), hosts, actions, enable, message);
  host.append(box);
  let view = null;
  const preset = () => (view?.presets || []).find(p => p.id === provider.value) || {};
  const sync = () => {
   const p = preset();
   baseUrl.placeholder = p.baseUrl || 'https://…';
   baseUrl.readOnly = p.id === 'mistral' || p.id === 'openai';
   if (baseUrl.readOnly) baseUrl.value = p.baseUrl;
   key.disabled = !!p.local;
  };
  const show = v => {
   view = v;
   provider.replaceChildren();
   (v.presets || []).forEach(p => { const o = node('option', p.label); o.value = p.id; if (p.local && !v.allowLocal) o.disabled = true; provider.append(o); });
   const own = v.source === 'mandant';
   provider.value = own && v.provider ? v.provider : 'mistral';
   baseUrl.value = own && v.baseUrl ? v.baseUrl : ''; model.value = own && v.model ? v.model : ''; timeout.value = own && v.timeoutSeconds ? v.timeoutSeconds : '';
   key.value = ''; key.placeholder = v.keySet ? 'hinterlegt – leer lassen, um ihn zu behalten' : 'noch kein Schlüssel';
   sync();
   checkbox.checked = !!v.enabled; checkbox.disabled = !v.available && !v.enabled;
   info.textContent = describeSource(v);
   hosts.textContent = v.allowedHosts && v.allowedHosts.length ? 'Freigegebene Hosts (Betreiber): ' + v.allowedHosts.join(', ') : 'Der Betreiber hat noch keine KI-Hosts freigegeben (LC_AI_ALLOWED_HOSTS).';
   clear.disabled = !v.keySet;
   if (!v.encryptionConfigured) message.textContent = 'Hinweis: Auf dem Server fehlt der Schlüssel zum Verschlüsseln (LC_AI_ENCRYPTION_KEY oder EBICS_ENCRYPTION_KEY); API-Schlüssel können noch nicht gespeichert werden.';
   else message.textContent = v.changedBy ? `Zuletzt geändert von ${v.changedBy}.` : '';
  };
  const run = async (button, work) => {
   if (button.disabled) return; const buttons = [save, test, clear, checkbox]; buttons.forEach(b => { b.dataset.was = b.disabled ? '1' : ''; b.disabled = true; });
   try { await work(); } catch (error) { message.textContent = error.message; }
   finally { buttons.forEach(b => { b.disabled = b.dataset.was === '1'; }); if (view) clear.disabled = !view.keySet; }
  };
  provider.onchange = sync;
  save.onclick = () => run(save, async () => {
   show(await json('/api/ai/config', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ provider: provider.value, baseUrl: baseUrl.value.trim(), model: model.value.trim(), timeoutSeconds: timeout.value ? Number(timeout.value) : null, apiKey: key.value }) }));
   status = null; message.textContent = 'Gespeichert.';
  });
  test.onclick = () => run(test, async () => {
   message.textContent = 'Teste die Verbindung …';
   const r = await json('/api/ai/test', { method: 'POST' });
   message.textContent = (r.ok ? '✓ ' : '✗ ') + r.message + (r.millis != null ? ` (${r.millis} ms)` : '');
  });
  clear.onclick = () => run(clear, async () => {
   if (typeof confirmAction === 'function' && !await confirmAction('Den gespeicherten API-Schlüssel entfernen? Die KI-Vorschläge werden dabei ausgeschaltet.')) return;
   show(await json('/api/ai/config/key', { method: 'DELETE' })); status = null; message.textContent = 'Schlüssel entfernt, KI ausgeschaltet.';
  });
  checkbox.onchange = () => run(checkbox, async () => {
   try { show(await json('/api/ai/settings', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ enabled: checkbox.checked }) })); status = null; message.textContent = checkbox.checked ? 'KI-Vorschläge sind aktiv.' : 'KI-Vorschläge sind aus.'; }
   catch (error) { checkbox.checked = !checkbox.checked; throw error; }
  });
  try { show(await json('/api/ai/status')); } catch (error) { message.textContent = error.message; }
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
