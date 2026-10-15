/* Prüfvermerk: a small dialog instead of prompt(), with frequent reasons as one-click templates. Own templates are remembered per browser. */
(() => {
 const KEY = 'lc.reviewNoteTemplates';
 const MAX = 12;
 const DEFAULTS = {
  ACCEPTED: ['Original geprüft, Angabe im Dokument vorhanden.', 'Abweichung unwesentlich, keine Unstimmigkeit im Sinne des LC.', 'Schreibweise abweichend, gleiche Partei.', 'Mit Kunde abgestimmt, Bestätigung liegt vor.'],
  CONFIRMED_DISCREPANCY: ['Unstimmigkeit, Dokument muss korrigiert werden.', 'Unstimmigkeit, Kunde informiert.', 'Unstimmigkeit, Verzicht des Auftraggebers einholen.']
 };
 /** Own templates first, then the defaults for the decision, without duplicates. */
 function templatesFor(decision, own) {
  const seen = new Set(), out = [];
  [...(own || []), ...(DEFAULTS[decision] || DEFAULTS.ACCEPTED)].forEach(t => { const v = String(t || '').trim(); if (v && !seen.has(v.toLowerCase())) { seen.add(v.toLowerCase()); out.push(v); } });
  return out;
 }
 /** Adds a template at the front, keeps at most MAX entries. */
 function remember(own, text) {
  const v = String(text || '').trim(); if (!v) return own || [];
  return [v, ...(own || []).filter(t => t.toLowerCase() !== v.toLowerCase())].slice(0, MAX);
 }
 const load = () => { try { const v = JSON.parse(localStorage.getItem(KEY) || '[]'); return Array.isArray(v) ? v.filter(x => typeof x === 'string') : []; } catch { return []; } };
 const save = list => { try { localStorage.setItem(KEY, JSON.stringify(list)); } catch { /* only a convenience */ } };
 globalThis.LcReviewNote = { templatesFor, remember, DEFAULTS };
 if (typeof document === 'undefined' || !document.body) return;

 /** Asks for a review note. Resolves with the text (may be empty when not required) or null when cancelled. */
 function ask({ title, text, decision = 'ACCEPTED', required = false } = {}) {
  return new Promise(resolve => {
   const dialog = document.createElement('dialog'); dialog.className = 'review-note-dialog';
   const form = document.createElement('form'); form.method = 'dialog';
   const head = document.createElement('h2'); head.textContent = title || 'Prüfvermerk';
   const info = document.createElement('p'); info.textContent = text || '';
   const chips = document.createElement('div'); chips.className = 'review-note-chips';
   const area = document.createElement('textarea'); area.rows = 4; area.maxLength = 1000; area.required = required; area.setAttribute('aria-label', 'Prüfvermerk');
   templatesFor(decision, load()).forEach(t => { const b = document.createElement('button'); b.type = 'button'; b.className = 'secondary review-note-chip'; b.textContent = t; b.onclick = () => { area.value = t; area.focus(); }; chips.append(b); });
   const keep = document.createElement('label'); keep.className = 'review-note-keep'; const box = document.createElement('input'); box.type = 'checkbox'; keep.append(box, document.createTextNode(' Als Vorlage merken'));
   const error = document.createElement('small'); error.className = 'error'; error.setAttribute('role', 'alert');
   const actions = document.createElement('div'); actions.className = 'review-note-actions';
   const cancel = document.createElement('button'); cancel.type = 'button'; cancel.className = 'secondary'; cancel.textContent = 'Abbrechen';
   const ok = document.createElement('button'); ok.type = 'submit'; ok.textContent = 'Speichern';
   actions.append(cancel, ok);
   form.append(head, info, chips, area, keep, error, actions); dialog.append(form); document.body.append(dialog);
   let result = null;
   cancel.onclick = () => dialog.close();
   form.onsubmit = event => {
    event.preventDefault();
    const value = area.value.trim();
    if (required && !value) { error.textContent = 'Bitte eine Begründung eingeben oder eine Vorlage wählen.'; area.focus(); return; }
    if (box.checked && value) save(remember(load(), value));
    result = value; dialog.close();
   };
   dialog.onclose = () => { dialog.remove(); resolve(result); };
   dialog.showModal(); area.focus();
  });
 }
 globalThis.LcReviewNote.ask = ask;
})();
