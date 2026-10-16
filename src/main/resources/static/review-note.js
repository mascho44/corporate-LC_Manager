/* Prüfvermerk: a small dialog instead of prompt(), with frequent reasons as one-click templates. Own templates are remembered per browser. */
(() => {
 const KEY = 'lc.reviewNoteTemplates';
 const MAX = 12;
 const DEFAULTS = {
  ACCEPTED: ['Original geprüft, Angabe im Dokument vorhanden.', 'Abweichung unwesentlich, keine Unstimmigkeit im Sinne des LC.', 'Schreibweise abweichend, gleiche Partei.', 'Mit Kunde abgestimmt, Bestätigung liegt vor.'],
  CONFIRMED_DISCREPANCY: ['Unstimmigkeit, Dokument muss korrigiert werden.', 'Unstimmigkeit, Kunde informiert.', 'Unstimmigkeit, Verzicht des Auftraggebers einholen.']
 };
 // Reasons with their rule basis, chosen by the kind of finding (code or message); they come before the general ones.
 const CONTEXT = [
  [/ADDRESS|APPLICANT|BENEFICIARY|PARTY|CONSIGNEE|NOTIFY/, { ACCEPTED: ['Adresse abweichend, gleiches Land – zulässig (UCP 600 Art. 14(j)).', 'Schreibfehler ohne Bedeutungsänderung – keine Unstimmigkeit (ISBP 821 A23).'], CONFIRMED_DISCREPANCY: ['Partei weicht vom LC ab – Unstimmigkeit.'] }],
  [/GOODS|DESCRIPTION|HS_CODE/, { ACCEPTED: ['Allgemeine Warenbeschreibung, nicht widersprüchlich – zulässig außerhalb der Rechnung (UCP 600 Art. 14(e)).', 'Warenbeschreibung entspricht dem LC (UCP 600 Art. 18(c)).'], CONFIRMED_DISCREPANCY: ['Warenbeschreibung der Rechnung entspricht nicht dem LC (UCP 600 Art. 18(c)).'] }],
  [/AMOUNT|QUANTITY|TOLERANCE|UNIT_PRICE/, { ACCEPTED: ['Innerhalb der zulässigen Toleranz (UCP 600 Art. 30).'], CONFIRMED_DISCREPANCY: ['Betrag bzw. Menge außerhalb der Toleranz (UCP 600 Art. 30).'] }],
  [/DOCUMENT_DATE|DATED|ISSUE_DATE|PRESENTATION/, { ACCEPTED: ['Dokument datiert vor der Vorlage – zulässig (UCP 600 Art. 14(i)).'], CONFIRMED_DISCREPANCY: ['Dokument datiert nach der Vorlage (UCP 600 Art. 14(i)).'] }],
  [/SHIPMENT|ON_BOARD|LATEST/, { ACCEPTED: ['Verladedatum innerhalb der LC-Frist.'], CONFIRMED_DISCREPANCY: ['Verladung nach dem spätesten Verladedatum – Unstimmigkeit.'] }],
  [/INSURANCE|COVER/, { ACCEPTED: ['Deckung mindestens 110 % CIF/CIP (UCP 600 Art. 28(f)(ii)).'], CONFIRMED_DISCREPANCY: ['Versicherungsdeckung unter der geforderten Mindesthöhe (UCP 600 Art. 28(f)(ii)).'] }],
  [/ORIGINAL|COPY|SIGNATURE|SIGNED/, { ACCEPTED: ['Original erkennbar gekennzeichnet bzw. unterzeichnet (UCP 600 Art. 17).'], CONFIRMED_DISCREPANCY: ['Original oder Unterschrift fehlt (UCP 600 Art. 17).'] }]
 ];
 /** Reasons fitting a finding (by its code and message), first match per kind. */
 function contextTemplates(decision, finding) {
  const text = `${finding?.code || ''} ${finding?.message || ''}`.toUpperCase().replace(/[\s-]+/g, '_');
  return CONTEXT.filter(([pattern]) => pattern.test(text)).flatMap(([, t]) => t[decision] || []);
 }
 /** Own templates first, then those fitting the finding, then the defaults for the decision, without duplicates. */
 function templatesFor(decision, own, finding = null) {
  const seen = new Set(), out = [];
  [...(own || []), ...contextTemplates(decision, finding), ...(DEFAULTS[decision] || DEFAULTS.ACCEPTED)].forEach(t => { const v = String(t || '').trim(); if (v && !seen.has(v.toLowerCase())) { seen.add(v.toLowerCase()); out.push(v); } });
  return out;
 }
 /** Adds a template at the front, keeps at most MAX entries. */
 function remember(own, text) {
  const v = String(text || '').trim(); if (!v) return own || [];
  return [v, ...(own || []).filter(t => t.toLowerCase() !== v.toLowerCase())].slice(0, MAX);
 }
 const load = () => { try { const v = JSON.parse(localStorage.getItem(KEY) || '[]'); return Array.isArray(v) ? v.filter(x => typeof x === 'string') : []; } catch { return []; } };
 const save = list => { try { localStorage.setItem(KEY, JSON.stringify(list)); } catch { /* only a convenience */ } };
 globalThis.LcReviewNote = { templatesFor, contextTemplates, remember, DEFAULTS };
 if (typeof document === 'undefined' || !document.body) return;

 /** Asks for a review note. Resolves with the text (may be empty when not required) or null when cancelled. */
 function ask({ title, text, decision = 'ACCEPTED', required = false, finding = null } = {}) {
  return new Promise(resolve => {
   const dialog = document.createElement('dialog'); dialog.className = 'review-note-dialog';
   const form = document.createElement('form'); form.method = 'dialog';
   const head = document.createElement('h2'); head.textContent = title || 'Prüfvermerk';
   const info = document.createElement('p'); info.textContent = text || '';
   const chips = document.createElement('div'); chips.className = 'review-note-chips';
   const area = document.createElement('textarea'); area.rows = 4; area.maxLength = 1000; area.required = required; area.setAttribute('aria-label', 'Prüfvermerk');
   templatesFor(decision, load(), finding).slice(0, 9).forEach((t, i) => { const b = document.createElement('button'); b.type = 'button'; b.className = 'secondary review-note-chip'; b.textContent = `${i + 1} · ${t}`; b.title = `Taste ${i + 1}`; b.onclick = () => { area.value = t; area.focus(); }; chips.append(b); });
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
   // Number keys pick a template while the text field is empty; Enter then saves.
   dialog.onkeydown = event => { if (event.metaKey || event.ctrlKey || event.altKey || area.value.trim() || !/^[1-9]$/.test(event.key)) return; const chip = chips.children[Number(event.key) - 1]; if (chip) { event.preventDefault(); chip.click(); } };
   area.onkeydown = event => { if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); form.requestSubmit(); } };
   dialog.onclose = () => { dialog.remove(); resolve(result); };
   dialog.showModal(); area.focus();
  });
 }
 globalThis.LcReviewNote.ask = ask;
})();
