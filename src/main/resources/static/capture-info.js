/* Inbox: how a document arrived through a scan station (source, scanner, batch, pages) and a warning when the declared page count differs from the real one. */
(() => {
 const fmt = value => { try { return value ? new Intl.DateTimeFormat('de-DE', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value)) : ''; } catch (ignored) { return ''; } };
 /** One line about the delivery; `warning` is set when the declared page count does not match. */
 function describe(info) {
  if (!info) return null;
  const source = String(info.source || '').replace(/^capture:/, '');
  const parts = ['Eingang: ' + (source || 'Erfassung')];
  if (info.scanner) parts.push('Gerät ' + info.scanner);
  if (info.batchId) parts.push('Stapel ' + info.batchId);
  if (info.declaredPages != null) parts.push(info.declaredPages + (info.declaredPages === 1 ? ' Seite' : ' Seiten') + ' gemeldet');
  if (info.scannedAt) parts.push('gescannt ' + fmt(info.scannedAt));
  const warning = info.pageCountMatches === false ? `Seitenzahl weicht ab: gemeldet ${info.declaredPages}, erkannt ${info.actualPages}. Bitte den Stapel prüfen.` : null;
  return { text: parts.join(' · '), warning };
 }
 globalThis.LcCaptureInfo = { describe };
 if (typeof document === 'undefined' || !document.body) return;

 let cache = null, loading = false, known = '';
 async function load() {
  if (loading) return; loading = true;
  try { cache = await json('/api/inbox-capture'); } catch (error) { cache = null; } finally { loading = false; }
  apply();
 }
 function apply() {
  if (!cache) return;
  document.querySelectorAll('.inbox-item').forEach(article => {
   const d = describe(cache[article.dataset.inboxId]);
   article.querySelector('.capture-info')?.remove();
   if (!d) return;
   const box = document.createElement('div'); box.className = 'capture-info';
   const line = document.createElement('small'); line.textContent = d.text; box.append(line);
   if (d.warning) { const warn = document.createElement('small'); warn.className = 'capture-warning'; warn.setAttribute('role', 'status'); warn.textContent = '⚠ ' + d.warning; box.append(warn); }
   (article.querySelector('.inbox-item-head > div:not(.doc-thumb)') || article.querySelector('.inbox-item-head') || article).append(box);
  });
 }
 setInterval(() => {
  const section = document.querySelector('#inboxSection');
  if (!section || section.classList.contains('hidden')) return;
  const ids = [...document.querySelectorAll('.inbox-item')].map(a => a.dataset.inboxId).join(',');
  if (ids !== known) { known = ids; load(); }
  else if (cache && [...document.querySelectorAll('.inbox-item')].some(a => cache[a.dataset.inboxId] && !a.querySelector('.capture-info'))) apply();
 }, 1500);
})();
