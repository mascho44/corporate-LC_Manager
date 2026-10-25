/* Presentation date as part of "Dokumente eingereicht": the edit dialog asks for it (today proposed) when that status is chosen,
   so rules comparing with the presentation date do not end up "nicht prüfbar". */
(() => {
 const today = () => new Date().toISOString().slice(0, 10);
 /** Date to save: only for the presented status and a valid, not future date; null otherwise. */
 function dateToSave(status, value, now = today()) {
  if (status !== 'DOCUMENTS_PRESENTED' || !/^\d{4}-\d{2}-\d{2}$/.test(value || '')) return null;
  return value <= now ? value : null;
 }
 globalThis.LcPresentationDate = { dateToSave };
 if (typeof document === 'undefined' || !document.body) return;
 const form = document.querySelector('#editForm'), dialog = document.querySelector('#editDialog');
 if (!form || !dialog) return;
 const label = document.createElement('label'); label.hidden = true;
 label.innerHTML = 'Vorlagedatum (Dokumente eingereicht am)<input type="date" data-presentation-date>';
 const input = label.querySelector('input'), status = form.elements.status;
 status.closest('label').after(label);
 let stored = null;
 const sync = () => { const presented = status.value === 'DOCUMENTS_PRESENTED'; label.hidden = !presented; input.max = today(); if (presented && !input.value) input.value = stored || today(); };
 status.addEventListener('change', sync);
 new MutationObserver(async () => {
  if (!dialog.open || !activeLc) return;
  input.value = ''; stored = null; sync();
  try { stored = (await json(`/api/lcs/${activeLc.id}/rule-facts`)).LC_PRESENTATION_DATE || null; if (stored) input.value = stored; sync(); } catch (ignored) { /* the field stays on today */ }
 }).observe(dialog, { attributes: true, attributeFilter: ['open'] });
 // Runs next to the save of the LC; the status change itself only fills an empty date, so the chosen one wins in either order.
 form.addEventListener('submit', () => {
  const date = dateToSave(status.value, input.value), lcId = activeLc?.id;
  if (!date || !lcId || date === stored || !can('LC_EDIT')) return;
  json(`/api/lcs/${lcId}/rule-facts/presentation-date`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ date }) })
   .catch(error => alert('Vorlagedatum nicht gespeichert: ' + error.message));
 });
})();
