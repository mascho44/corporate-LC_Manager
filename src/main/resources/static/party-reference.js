/* Applicant (:50:) or beneficiary (:59:) that only refer to another field ("SEE 47A", "AS PER FIELD 46A"): a correction box in the import preview.
   The found address is prefilled; the referred field is shown to copy from. The original message text stays unchanged. */
(() => {
 const REFERENCE = /\b(?:SEE|REFER(?:\s+TO)?|SIEHE|VIDE|AS\s+PER|GEM(?:AESS|ÄSS|\.))\s+(?:(?:FIELD|FLD|TAG|FELD)\s*:?\s*)?(\d{2}[A-Z]?)\b/i;
 const PARTIES = [['applicant', '50', tx('pal.1dc6a9','Auftraggeber (Applicant, :50:)')], ['beneficiary', '59', tx('pal.18eff1','Begünstigter (Beneficiary, :59:)')]];
 const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

 /** The field a party value refers to ("47A"), or null. "SEE 47" means 47A when only that exists. */
 function referenceOf(value, codes = []) {
  const m = REFERENCE.exec(String(value || ''));
  if (!m) return null;
  const tag = m[1].toUpperCase();
  return /^\d{2}$/.test(tag) && !codes.includes(tag) && codes.includes(tag + 'A') ? tag + 'A' : tag;
 }
 /** Parties of a preview that need a correction, with the suggested address (resolved by the server) and the text of the referred field. */
 function corrections(preview) {
  const fields = preview?.rawFields || [], codes = fields.map(f => f.code);
  const out = [];
  PARTIES.forEach(([key, code, label]) => {
   const field = fields.find(f => f.code === code);
   const source = field && referenceOf(field.value, codes);
   if (!source) return;
   const resolved = preview[key] && preview[key].trim() !== String(field.value).trim() ? preview[key] : '';
   out.push({ key, code, label, original: field.value, source, sourceText: fields.find(f => f.code === source)?.value || '', suggested: resolved });
  });
  return out;
 }
 function html(list) {
  if (!list.length) return '';
  return `<div class="party-correction" data-party-correction><b>${tx('pah.d2b2f7','Korrektur nötig')}</b>` + list.map(c =>
   `<label>${esc(c.label)} ${tx('pax.says','– in der Nachricht steht „{orig}“',{orig:esc(c.original)})}${c.suggested ? tx('pal.8de2d0',' (Adresse aus :') + esc(c.source) + tx('pal.8f52ec',': vorgeschlagen, bitte prüfen)') : ''}` +
   `<textarea data-party="${c.key}" rows="3" maxlength="2000" placeholder="${tx('pah.9ae133','Name und Adresse eintragen')}">${esc(c.suggested)}</textarea></label>` +
   (c.sourceText ? `<details><summary>${tx('pah.ae63ef','Feld :')}${esc(c.source)}${tx('pax.show',': anzeigen')}</summary><pre>${esc(c.sourceText)}</pre></details>` : `<small>${tx('pah.ae63ef','Feld :')}${esc(c.source)}${tx('pax.absent',': kommt in der Nachricht nicht vor.')}</small>`)
  ).join('') + `<small>${tx('pah.9ffc62','Die Korrektur wird in die Akte übernommen; der Originaltext der Nachricht bleibt erhalten.')}</small></div>`;
 }
 /** Values of a correction box; missing lists the labels that are still empty. */
 function read(container) {
  const out = { applicant: null, beneficiary: null, missing: [] };
  container?.querySelectorAll('[data-party]').forEach(area => {
   const value = area.value.trim();
   out[area.dataset.party] = value || null;
   if (!value) out.missing.push(area.dataset.party === 'applicant' ? tx('pal.5144d0','Auftraggeber') : tx('pal.391339','Begünstigter'));
  });
  return out;
 }
 globalThis.LcPartyReference = { referenceOf, corrections, html, read };
})();
