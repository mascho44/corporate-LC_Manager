/* "Als Vorlage kopieren": a new file with the terms of the open one (parties, banks, goods, required documents, conditions) under a new reference.
   Dates, documents and review results are not copied. The server does the work; this only asks for the reference. */
(() => {
 /** Suggestion for the new reference: the old one with a running suffix, "LC-1" -> "LC-1-K2". */
 function suggestion(reference, taken) {
  const base = String(reference || '').replace(/-K\d+$/, '');
  for (let n = 2; n < 100; n++) if (!taken.has(`${base}-K${n}`)) return `${base}-K${n}`;
  return base + '-K';
 }
 globalThis.LcAkteCopy = { suggestion };
 if (typeof document === 'undefined' || !document.body) return;

 async function copy() {
  const source = activeLc; if (!source) return;
  const taken = new Set((typeof data !== 'undefined' ? data : []).map(lc => lc.reference));
  const reference = prompt(`Neue Akte mit den Bedingungen von ${source.reference}.\nDaten, Dokumente und Prüfergebnisse werden nicht übernommen.\n\nReferenz der neuen Akte:`, suggestion(source.reference, taken));
  if (!reference || !reference.trim()) return;
  try {
   const created = await json(`/api/lcs/${encodeURIComponent(source.id)}/copy`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ reference: reference.trim() }) });
   if (typeof data !== 'undefined') data.push(created);
   if (typeof render === 'function') render();
   await show(created.id);
  } catch (error) { alert('Akte nicht kopiert: ' + error.message); }
 }

 function enhance() {
  if (typeof can !== 'function' || !can('LC_EDIT')) return;
  const upload = document.querySelector('#uploadBtn');
  if (!upload || document.querySelector('#copyLcBtn')) return;
  const button = document.createElement('button'); button.type = 'button'; button.id = 'copyLcBtn'; button.className = 'secondary';
  button.textContent = 'Als Vorlage kopieren'; button.title = 'Neue Akte mit denselben Beteiligten, Banken, Waren und Dokumentenanforderungen anlegen';
  button.onclick = copy; upload.before(button);
 }
 let scheduled = false;
 new MutationObserver(() => { if (!scheduled) { scheduled = true; setTimeout(() => { scheduled = false; enhance(); }, 80); } }).observe(document.body, { childList: true, subtree: true });
 enhance();
})();
