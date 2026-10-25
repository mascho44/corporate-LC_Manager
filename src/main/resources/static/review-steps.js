/* Guided examination (Prüfungsassistent): the fixed steps and which findings belong to which step. Pure logic, no DOM. */
(() => {
 const tr = (key, de, values = {}) => { const i = globalThis.LcI18n, s = i?.t ? i.t(key, values) : null; return s && s !== key ? s : String(de).replace(/\{(\w+)\}/g, (m, k) => values[k] ?? m); };
 const STEPS = [
  { key: 'AKTE', get label() { return tr('rs.AKTE.label', 'Akte und Bedingungen'); }, get intro() { return tr('rs.AKTE.intro', 'Parteien, Betrag, Fristen und die geforderten Dokumente des Akkreditivs lesen. Stimmen die Stammdaten mit der SWIFT-Nachricht überein?'); } },
  { key: 'VOLLSTAENDIGKEIT', get label() { return tr('rs.VOLLSTAENDIGKEIT.label', 'Vollständigkeit'); }, get intro() { return tr('rs.VOLLSTAENDIGKEIT.intro', 'Liegen alle geforderten Dokumente vor, in der geforderten Zahl von Originalen und Kopien (bei „Full Set" alle Originale)?'); } },
  { key: 'ANGABEN', get label() { return tr('rs.ANGABEN.label', 'Fehlende Angaben'); }, get intro() { return tr('rs.ANGABEN.intro', 'Angaben, ohne die Regeln nicht prüfbar sind (z. B. das Vorlagedatum), jetzt erfassen, damit nichts „nicht prüfbar" bleibt.'); } },
  { key: 'COMPLIANCE', get label() { return tr('rs.COMPLIANCE.label', 'Compliance'); }, get intro() { return tr('rs.COMPLIANCE.intro', 'Sanktionen, Embargo und Exportkontrolle. Treffer zuerst entscheiden; ein Falsch-positiv braucht die Freigabe einer zweiten Person.'); } },
  { key: 'DOKUMENTE', get label() { return tr('rs.DOKUMENTE.label', 'Dokumente'); }, get intro() { return tr('rs.DOKUMENTE.intro', 'Dokument für Dokument prüfen: Rechnung, Transportdokument, Versicherung, Ursprung, Packliste. Jeder Befund wird bestätigt oder als Abweichung gemeldet.'); } },
  { key: 'QUERPRUEFUNG', get label() { return tr('rs.QUERPRUEFUNG.label', 'Querprüfungen und Fristen'); }, get intro() { return tr('rs.QUERPRUEFUNG.intro', 'Betrag, Ware und Daten über alle Dokumente hinweg; Vorlagefrist, spätester Versand und Ablauf des Akkreditivs.'); } },
  { key: 'ABSCHLUSS', get label() { return tr('rs.ABSCHLUSS.label', 'Abschluss'); }, get intro() { return tr('rs.ABSCHLUSS.intro', 'Ergebnis ansehen, bei Abweichungen die Unstimmigkeitsmeldung erstellen, die Prüfung mit einem Vermerk abschließen.'); } }
 ];
 const COMPLIANCE = /SANCTION|EMBARGO|EXPORT_CONTROL|DUAL_USE|HS_CODE/;
 const COMPLETE = /MISSING_DOCUMENT|DOCUMENT_PRESENT|ORIGINAL_COUNT|COPY_COUNT|DOCUMENT_COPIES|DUPLICATE_DOCUMENT|FULL_SET|REQUIRED_DOCUMENT/;
 const CROSS = /_CONSISTENCY|PRESENTATION_|AMOUNT_EXCEEDED|SHIPMENT_DATE|EXPIRY|INSURANCE_DATE|PARTIAL_SHIPMENT|TRANSSHIPMENT|QUANTITY_NOT_COMPARABLE|ADDITIONAL_CONDITIONS/;
 const BLOCKING = /SANCTION|EMBARGO|EXPORT_CONTROL|AMOUNT|EXPIR|PRESENTATION_PERIOD|PRESENTATION_BEFORE|SHIPMENT_DATE|LATEST_SHIPMENT|MISSING_DOCUMENT|SIGNATURE/;
 const isOpen = f => !f.reviewDecision && f.severity !== 'OK' && f.code !== 'REVIEW_STALE';
 const isMissing = f => f.severity === 'WARNING' && /^Regel nicht prüfbar/.test(f.message || '') && /\bLC_[A-Z_]+ = nicht erfasst/.test(f.lcCondition || ''); // guard-ignore: matches the German message text of the server, not a UI text
 /** The step a finding belongs to; the first matching rule decides: missing input, compliance, completeness, cross-check (or no single document), else the documents. */
 function stepOf(f) {
  const code = String(f.code || '');
  if (isMissing(f)) return 'ANGABEN';
  if (COMPLIANCE.test(code)) return 'COMPLIANCE';
  if (COMPLETE.test(code)) return 'VOLLSTAENDIGKEIT';
  if (!f.documentName || CROSS.test(code)) return 'QUERPRUEFUNG';
  return 'DOKUMENTE';
 }
 /** Per step: open findings, how many of them block, how many were decided; ABSCHLUSS and AKTE carry no findings of their own. */
 function summarize(results) {
  const out = Object.fromEntries(STEPS.map(s => [s.key, { open: 0, blocking: 0, decided: 0, items: [] }]));
  (results || []).forEach(f => {
   const e = out[stepOf(f)];
   if (isOpen(f)) { e.open++; e.items.push(f); if (BLOCKING.test(String(f.code || ''))) e.blocking++; }
   else if (f.reviewDecision && f.code !== 'REVIEW_STALE') e.decided++;
  });
  return out;
 }
 /** A step can be completed when none of its findings is open; otherwise it has to be skipped with a reason. */
 const canComplete = entry => !entry || entry.open === 0;
 /** Recorded steps by key. */
 const byKey = recorded => Object.fromEntries((recorded || []).map(r => [r.step, r]));
 /** Index of the first step without a record (the step to work on), the last step when all are recorded. */
 function currentIndex(recorded) {
  const known = byKey(recorded);
  const i = STEPS.findIndex(s => !known[s.key]);
  return i < 0 ? STEPS.length - 1 : i;
 }
 function progress(recorded) {
  const known = byKey(recorded);
  const done = STEPS.filter(s => known[s.key]?.status === 'DONE').length, skipped = STEPS.filter(s => known[s.key]?.status === 'SKIPPED').length;
  return { done, skipped, total: STEPS.length, finished: done + skipped === STEPS.length };
 }
 /** Days from `from` to the date (negative = past), null without a date. */
 function daysUntil(date, from = new Date()) {
  if (!date) return null;
  const d = new Date(date + 'T00:00:00'), f = new Date(from.getFullYear(), from.getMonth(), from.getDate());
  return Number.isNaN(d.getTime()) ? null : Math.round((d - f) / 86400000);
 }
 globalThis.LcReviewSteps = { STEPS, stepOf, summarize, canComplete, byKey, currentIndex, progress, daysUntil, isOpen };
})();
