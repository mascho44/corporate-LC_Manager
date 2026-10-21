/* Inbox: how a document arrived through a scan station (source, scanner, batch, pages) and a warning when the declared page count differs from the real one. */
(() => {
 const fmt = value => { try { return value ? new Intl.DateTimeFormat('de-DE', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value)) : ''; } catch (ignored) { return ''; } };
 /** One line about the delivery; `warning` is set when the declared page count does not match. */
 function describe(info) {
  if (!info) return null;
  const source = String(info.source || '').replace(/^(?:capture|mail|folder):/, '');
  if (info.channel === 'EMAIL') {
   const mail = [tx('cil.cf40e0','Eingang: E-Mail') + (info.sender ? tx('cil.c8a06e',' von ') + info.sender : '')];
   if (info.subject) mail.push(tx('cil.37e2b4','Betreff „') + info.subject + '“');
   if (info.scannedAt) mail.push('gesendet ' + fmt(info.scannedAt));
   if (source) mail.push(tx('cil.9cf877','Postfach ') + source);
   return { text: mail.join(' · '), warning: null };
  }
  if (info.channel === 'FOLDER') {
   const file = info.reference ? String(info.reference).replace(/^[^/]*\//, '') : '';
   const folder = [tx('cil.be4514','Eingang: Ordner ') + (info.scanner || source || '')];
   if (file) folder.push(tx('cil.54b99a','Datei ') + file);
   if (info.scannedAt) folder.push('abgelegt ' + fmt(info.scannedAt));
   return { text: folder.join(' · '), warning: null };
  }
  const parts = [tx('cil.cb8750','Eingang: ') + (source || tx('cil.55fd28','Erfassung'))];
  if (info.scanner) parts.push(tx('cil.e74a6b','Gerät ') + info.scanner);
  if (info.batchId) parts.push(tx('cil.797f57','Stapel ') + info.batchId);
  if (info.declaredPages != null) parts.push(info.declaredPages + (info.declaredPages === 1 ? tx('cil.669cfa',' Seite') : tx('cil.2b2bbd',' Seiten')) + tx('cil.085a1c',' gemeldet'));
  if (info.scannedAt) parts.push('gescannt ' + fmt(info.scannedAt));
  const warning = info.pageCountMatches === false ? tx('cit.cfeab1','Seitenzahl weicht ab: gemeldet {p0}, erkannt {p1}. Bitte den Stapel prüfen.',{p0:info.declaredPages,p1:info.actualPages}) : null;
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
