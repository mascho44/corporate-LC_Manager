/* "CSV exportieren" on the file list: exactly the rows the list currently shows (filters and sort applied), built in the browser from the loaded data.
   Cells that start like a formula are neutralised so a spreadsheet does not execute them. */
(() => {
 const tr = (key, de, values = {}) => { const i = globalThis.LcI18n, s = i?.t ? i.t(key, values) : null; return s && s !== key ? s : String(de).replace(/\{(\w+)\}/g, (m, k) => values[k] ?? m); };

 const light = { RED: ['le.red', 'Abweichungen'], YELLOW: ['le.yellow', 'Hinweise offen'], GREEN: ['le.green', 'Prüfung erfüllt'] };

 /** One cell: text in quotes, quotes doubled, a leading = + - @ or control character defused with an apostrophe. */
 function cell(value) {
  let text = value == null ? '' : String(value);
  if (/^[=+\-@\t\r]/.test(text)) text = "'" + text;
  return '"' + text.replace(/"/g, '""') + '"';
 }

 function amount(value, german) {
  if (value == null || value === '') return '';
  const text = Number(value).toFixed(2);
  return german ? text.replace('.', ',') : text;
 }

 /** The file as CSV text; `german` selects ; as separator and a decimal comma, otherwise , and a decimal point. */
 function csv(rows, german = true, label = value => value) {
  const separator = german ? ';' : ',';
  const head = [tr('le.reference', 'Referenz'), tr('le.kind', 'Art'), tr('le.status', 'Status'), tr('le.light', 'Prüfung'), 'Applicant', 'Beneficiary', tr('le.amount', 'Betrag'), tr('le.currency', 'Währung'),
   tr('le.issued', 'Ausstellung'), tr('le.expiry', 'Ablauf'), tr('le.shipment', 'Spätester Versand'), tr('le.followUp', 'Wiedervorlage'), tr('le.assignee', 'Zuständig'), 'SLA'];
  const lines = [head.map(cell).join(separator)];
  for (const x of rows) {
   const l = light[x.dossierStatus?.status];
   lines.push([x.reference, x.instrumentType === 'GUARANTEE' ? tr('le.guarantee', 'Garantie') : tr('le.lc', 'Akkreditiv'), label(x.status || 'RECEIVED'), l ? tr(l[0], l[1]) : '', x.applicant, x.beneficiary,
    amount(x.amount, german), x.currency, x.issueDate, x.expiryDate, x.latestShipmentDate, x.followUpDate, x.assignedTo, x.sla]
    .map((value, index) => index === 6 ? '"' + (value ?? '') + '"' : cell(value)).join(separator));
  }
  return lines.join('\r\n') + '\r\n';
 }

 globalThis.LcListExport = { csv, cell };

 if (typeof document === 'undefined') return;
 const filters = document.querySelector('#lcSection .lc-filters');
 if (!filters) return;
 const button = document.createElement('button');
 button.type = 'button'; button.id = 'lcExport'; button.className = 'secondary';
 const caption = () => { button.textContent = tr('le.button', 'CSV exportieren'); };
 caption(); document.addEventListener('lc-language-changed', caption);
 filters.append(button);
 button.onclick = () => {
  const rows = globalThis.LcVisibleRows || [];
  const german = (globalThis.LcI18n?.language?.() || 'de') !== 'en';
  const text = csv(rows, german, typeof statusLabel === 'function' ? statusLabel : undefined);
  const url = URL.createObjectURL(new Blob(['﻿' + text], { type: 'text/csv;charset=utf-8' }));
  const link = document.createElement('a');
  link.href = url; link.download = 'Akten-' + new Date().toISOString().slice(0, 10) + '.csv';
  document.body.append(link); link.click(); link.remove(); setTimeout(() => URL.revokeObjectURL(url), 1000);
 };
})();
