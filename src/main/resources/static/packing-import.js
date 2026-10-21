/* CSV import for the positions of a packing list or invoice. No fixed format: delimiter, encoding, decimal notation and column names are
   detected or chosen by the user; the column mapping is remembered per set of headers. The file never leaves the browser until the document is created. */
(() => {
 const FIELDS = (globalThis.lcLazy || (f => f()))(() => ([
  ['position', tx('pil.52f5e0','Position'), ['pos', 'position', 'item no', 'item', 'line', 'lfd', 'nr', 'no']],
  ['description', tx('pil.35bedb','Beschreibung'), ['description', 'beschreibung', 'goods', 'ware', 'warenbezeichnung', 'bezeichnung', 'artikel', 'article', 'product', 'produkt', 'material', 'text']],
  ['quantity', tx('pil.7c856a','Menge'), ['quantity', 'qty', 'menge', 'anzahl', 'stück', 'stueck', 'pieces', 'pcs']],
  ['unit', tx('pil.574cd0','Einheit'), ['unit', 'einheit', 'uom', 'me']],
  ['unitPrice', tx('pil.86762b','Einzelpreis'), ['unit price', 'einzelpreis', 'preis', 'price', 'ep']],
  ['amount', tx('pil.927cee','Gesamt'), ['amount', 'total', 'gesamt', 'betrag', 'value', 'wert', 'line total', 'summe']],
  ['packages', tx('pil.7d98cc','Packstücke'), ['packages', 'packstücke', 'packstuecke', 'colli', 'cartons', 'ctns', 'kartons', 'packs', 'pallets', 'paletten', 'no of packages']],
  ['netWeight', tx('pil.5544c6','Netto kg'), ['net weight', 'nettogewicht', 'netto', 'net', 'n.w.', 'nw', 'net kg']],
  ['grossWeight', tx('pil.a567c8','Brutto kg'), ['gross weight', 'bruttogewicht', 'brutto', 'gross', 'g.w.', 'gw', 'gross kg']]
 ]));
 const NUMERIC = new Set(['quantity', 'unitPrice', 'amount', 'packages', 'netWeight', 'grossWeight']);

 /** UTF-8 if the bytes are valid UTF-8, otherwise Windows-1252 (typical for German Excel exports). */
 function decode(buffer) {
  const bytes = new Uint8Array(buffer);
  try { return new TextDecoder('utf-8', { fatal: true }).decode(bytes).replace(/^﻿/, ''); }
  catch (error) { return new TextDecoder('windows-1252').decode(bytes); }
 }
 function detectDelimiter(text) {
  const lines = text.split(/\r?\n/).filter(line => line.trim()).slice(0, 10);
  let best = ';', bestScore = -1;
  for (const delimiter of [';', ',', '\t', '|']) {
   const counts = lines.map(line => parseLine(line, delimiter).length - 1);
   const first = counts[0] || 0;
   if (first === 0) continue;
   const consistent = counts.filter(count => count === first).length;
   const score = consistent * 100 + first;
   if (score > bestScore) { best = delimiter; bestScore = score; }
  }
  return best;
 }
 function parseLine(line, delimiter) {
  const cells = []; let cell = '', quoted = false;
  for (let i = 0; i < line.length; i++) {
   const c = line[i];
   if (quoted) { if (c === '"') { if (line[i + 1] === '"') { cell += '"'; i++; } else quoted = false; } else cell += c; }
   else if (c === '"') quoted = true;
   else if (c === delimiter) { cells.push(cell); cell = ''; }
   else cell += c;
  }
  cells.push(cell);
  return cells;
 }
 /** Rows of cells; quoted cells may contain line breaks. */
 function parseCsv(text, delimiter) {
  const rows = []; let cell = '', row = [], quoted = false;
  for (let i = 0; i < text.length; i++) {
   const c = text[i];
   if (quoted) { if (c === '"') { if (text[i + 1] === '"') { cell += '"'; i++; } else quoted = false; } else cell += c; }
   else if (c === '"') quoted = true;
   else if (c === delimiter) { row.push(cell); cell = ''; }
   else if (c === '\n' || c === '\r') { if (c === '\r' && text[i + 1] === '\n') i++; row.push(cell); cell = ''; if (row.some(value => value.trim() !== '')) rows.push(row); row = []; }
   else cell += c;
  }
  row.push(cell);
  if (row.some(value => value.trim() !== '')) rows.push(row);
  return rows;
 }
 /** "1.234,56", "1,234.56", "12,5", "12 kg" → number; anything else → null. `decimal` ('de' comma / 'en' dot) settles ambiguous values like "1.250". */
 function parseNumber(value, decimal) {
  let text = String(value ?? '').trim().replace(/\s+/g, '').replace(/[a-zA-Z€$£%]+$/, '').replace(/^[€$£]/, '');
  if (!text || !/^[-+]?[\d.,]+$/.test(text)) return null;
  const comma = text.lastIndexOf(','), dot = text.lastIndexOf('.');
  if (decimal === 'de') text = text.replace(/\./g, '').replace(',', '.');
  else if (decimal === 'en') text = text.replace(/,/g, '');
  else if (comma >= 0 && dot >= 0) text = comma > dot ? text.replace(/\./g, '').replace(',', '.') : text.replace(/,/g, '');
  else if (comma >= 0) text = /^[-+]?\d{1,3}(,\d{3})+$/.test(text) ? text.replace(/,/g, '') : text.replace(',', '.');
  else if (dot >= 0 && /^[-+]?\d{1,3}(\.\d{3}){2,}$/.test(text)) text = text.replace(/\./g, '');
  const number = Number(text);
  return Number.isFinite(number) ? number : null;
 }
 /** Decimal notation of a file: a clear "12,5" or "1.234,5" means comma, a clear "12.5" or "1,234.5" means dot; undecided → comma (German default). */
 function detectDecimal(rows, mapping) {
  let de = 0, en = 0;
  rows.forEach(row => NUMERIC.forEach(key => {
   const index = mapping[key]; if (index === undefined || index === null || index === '') return;
   const text = String(row[index] ?? '').trim().replace(/\s+/g, '').replace(/[a-zA-Z€$£%]+$/, '');
   if (/^[-+]?\d{1,3}(\.\d{3})+,\d+$|^[-+]?\d+,\d{1,2}$|^[-+]?\d+,\d{4,}$/.test(text)) de++;
   else if (/^[-+]?\d{1,3}(,\d{3})+\.\d+$|^[-+]?\d+\.\d{1,2}$|^[-+]?\d+\.\d{4,}$/.test(text)) en++;
  }));
  return en > de ? 'en' : 'de';
 }
 const normalize = header => String(header || '').toLowerCase().replace(/[()\[\]]/g, ' ').replace(/\./g, '').replace(/\s+/g, ' ').trim();
 /** Best column per field by header name (exact match first, then contained word); each column is used once. */
 function guessMapping(headers) {
  const names = headers.map(normalize), mapping = {}, used = new Set();
  const pass = exact => FIELDS.forEach(([key, , synonyms]) => {
   if (mapping[key] !== undefined) return;
   const index = names.findIndex((name, i) => !used.has(i) && name && synonyms.some(raw => { const s = raw.replace(/\./g, ''); return exact ? name === s : name.split(/[^a-zäöüß]+/).includes(s) || (s.includes(' ') && name.includes(s)); }));
   if (index >= 0) { mapping[key] = index; used.add(index); }
  });
  pass(true); pass(false);
  return mapping;
 }
 /** Items from the data rows; `warnings` lists rows that were skipped or had unreadable numbers. */
 function buildItems(rows, mapping) {
  const items = [], warnings = [], decimal = detectDecimal(rows, mapping);
  rows.forEach((row, index) => {
   const cell = key => mapping[key] === undefined || mapping[key] === null || mapping[key] === '' ? '' : String(row[mapping[key]] ?? '').trim();
   if (!row.some(value => String(value).trim() !== '')) return;
   const item = { position: cell('position'), description: cell('description'), unit: cell('unit') };
   NUMERIC.forEach(key => {
    const raw = cell(key); const number = raw === '' ? null : parseNumber(raw, decimal);
    if (raw !== '' && number === null) warnings.push(tx('pit.1562df','Zeile {p0}: „{p1}" ist keine Zahl ({p2}).',{p0:index + 1,p1:raw,p2:FIELDS.find(f => f[0] === key)[1]}));
    item[key] = number;
   });
   if (!item.description) { warnings.push(tx('pit.41095f','Zeile {p0} ohne Beschreibung übersprungen.',{p0:index + 1})); return; }
   items.push(item);
  });
  items.forEach((item, i) => { if (!item.position) item.position = String(i + 1); });
  return { items, warnings };
 }
 const sum = (items, key) => { const values = items.map(i => i[key]).filter(v => v != null); return values.length ? Math.round(values.reduce((a, b) => a + b, 0) * 1000) / 1000 : null; };
 const totals = items => ({ packages: sum(items, 'packages'), netWeight: sum(items, 'netWeight'), grossWeight: sum(items, 'grossWeight') });

 globalThis.LcPackingImport = { FIELDS, decode, detectDelimiter, parseCsv, parseNumber, detectDecimal, guessMapping, buildItems, totals };
 if (typeof document === 'undefined' || !document.body || typeof ensureDocumentCreationUi !== 'function') return;

 const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
 const storageKey = headers => 'lcPackingMapping:' + headers.map(normalize).join('|');
 const remembered = headers => { try { return JSON.parse(localStorage.getItem(storageKey(headers)) || 'null'); } catch (error) { return null; } };
 const remember = (headers, mapping) => { try { localStorage.setItem(storageKey(headers), JSON.stringify(mapping)); } catch (error) { } };

 function openImport(file) {
  const dialog = document.createElement('dialog'); dialog.className = 'wide-dialog packing-import-dialog'; document.body.append(dialog); dialog.onclose = () => dialog.remove();
  const state = { text: '', delimiter: ';', hasHeader: true, rows: [], mapping: {}, mode: 'replace' };
  const view = () => {
   const headers = state.hasHeader ? (state.rows[0] || []) : (state.rows[0] || []).map((_, i) => tx('pil.89986a','Spalte ') + (i + 1));
   const data = state.hasHeader ? state.rows.slice(1) : state.rows;
   const { items, warnings } = buildItems(data, state.mapping);
   const column = key => `<select data-map="${key}"><option value="">${tx('pih.3d6e2c','– nicht übernehmen –')}</option>${headers.map((h, i) => `<option value="${i}"${state.mapping[key] === i ? ' selected' : ''}>${esc(h || tx('pil.89986a','Spalte ') + (i + 1))}</option>`).join('')}</select>`;
   const preview = items.slice(0, 5).map(i => `<tr>${FIELDS.map(([key]) => `<td>${esc(i[key] ?? '')}</td>`).join('')}</tr>`).join('');
   dialog.innerHTML = `<div class="dialoghead"><h2>${tx('pih.e65949','Positionen aus CSV importieren')}</h2><button type="button" data-close>${tx('pih.8311b9','Schließen')}</button></div>` +
    tx('pit.aad800','<p>{p0} · {p1} Zeilen. Trennzeichen und Spalten sind erkannt; bitte kurz prüfen.</p>',{p0:esc(file.name),p1:data.length}) +
    `<div class="packing-import-options"><label>${tx('pih.123acd','Trennzeichen')} <select data-delimiter>${[[';', tx('pil.2ecec7','Semikolon ;')], [',', tx('pil.316497','Komma ,')], ['\t', tx('pil.6c2e6c','Tabulator')], ['|', tx('pil.3c7364','Senkrechter Strich |')]].map(([v, l]) => `<option value="${v}"${state.delimiter === v ? ' selected' : ''}>${l}</option>`).join('')}</select></label>` +
    `<label><input type="checkbox" data-header${state.hasHeader ? ' checked' : ''}> ${tx('pih.06d9ec','Erste Zeile enthält Spaltennamen')}</label></div>` +
    `<table class="plan-table"><thead><tr><th>${tx('pih.144d62','Feld')}</th><th>${tx('pih.ed6f20','Spalte in der Datei')}</th></tr></thead><tbody>${FIELDS.map(([key, label]) => `<tr><td>${label}</td><td>${column(key)}</td></tr>`).join('')}</tbody></table>` +
    `<h3>${tx('pih.13edf2','Vorschau (')}${items.length} Positionen)</h3><div class="plan-scroll"><table class="plan-table"><thead><tr>${FIELDS.map(([, label]) => `<th>${label}</th>`).join('')}</tr></thead><tbody>${preview || '<tr><td colspan="9">'+tx('pih.681904','Keine Positionen erkannt – bitte Beschreibung zuordnen.')+'</td></tr>'}</tbody></table></div>` +
    (warnings.length ? `<p class="packing-import-warnings" role="status">${warnings.slice(0, 5).map(esc).join('<br>')}${warnings.length > 5 ? `<br>${tx('pih.6c904f','… und')} ${warnings.length - 5} weitere Hinweise` : ''}</p>` : '') +
    `<div class="packing-import-options"><label><input type="radio" name="mode" value="replace"${state.mode === 'replace' ? ' checked' : ''}> ${tx('pih.5818ba','Vorhandene Positionen ersetzen')}</label><label><input type="radio" name="mode" value="append"${state.mode === 'append' ? ' checked' : ''}> ${tx('pih.ecf891','An vorhandene anhängen')}</label></div>` +
    `<div class="dialog-actions"><button type="button" data-apply${items.length ? '' : ' disabled'}>${tx('pix.apply','{n} Positionen übernehmen',{n:items.length})}</button></div>`;
   dialog.querySelector('[data-close]').onclick = () => dialog.close();
   dialog.querySelector('[data-delimiter]').onchange = e => { state.delimiter = e.target.value; reparse(); };
   dialog.querySelector('[data-header]').onchange = e => { state.hasHeader = e.target.checked; remap(); view(); };
   dialog.querySelectorAll('[data-map]').forEach(select => select.onchange = () => { state.mapping[select.dataset.map] = select.value === '' ? undefined : Number(select.value); view(); });
   dialog.querySelectorAll('[name=mode]').forEach(radio => radio.onchange = () => { state.mode = radio.value; });
   dialog.querySelector('[data-apply]').onclick = () => { apply(items); if (state.hasHeader) remember(headers, state.mapping); dialog.close(); };
  };
  const remap = () => { const headers = state.hasHeader ? (state.rows[0] || []) : []; state.mapping = (state.hasHeader && remembered(headers)) || guessMapping(headers); };
  const reparse = () => { state.rows = parseCsv(state.text, state.delimiter); remap(); view(); };
  const apply = items => {
   const list = document.querySelector('#documentItemList'); if (!list) return;
   if (state.mode === 'replace') list.innerHTML = '';
   items.forEach(item => {
    addDocumentItem();
    const row = list.lastElementChild, set = (name, value) => { const input = row.querySelector(`[name="${name}"]`); if (input && value != null && value !== '') input.value = value; };
    set('itemDescription', item.description); set('itemQuantity', item.quantity); set('itemUnit', item.unit); set('itemUnitPrice', item.unitPrice); set('itemAmount', item.amount);
    set('itemPackages', item.packages); set('itemNetWeight', item.netWeight); set('itemGrossWeight', item.grossWeight);
   });
   [...list.children].forEach((row, i) => { row.querySelector('[name="itemPosition"]').value = i + 1; });
   const all = [...list.querySelectorAll('.document-item-row')].map(row => { const n = name => parseNumber(row.querySelector(`[name="${name}"]`).value); return { packages: n('itemPackages'), netWeight: n('itemNetWeight'), grossWeight: n('itemGrossWeight') }; });
   const total = totals(all), form = document.querySelector('#generateDocumentForm');
   ['packages', 'netWeight', 'grossWeight'].forEach(key => { if (form?.elements[key] && form.elements[key].value === '' && total[key] != null) form.elements[key].value = total[key]; });
  };
  file.arrayBuffer().then(buffer => { state.text = decode(buffer); state.delimiter = detectDelimiter(state.text); state.rows = parseCsv(state.text, state.delimiter); remap(); view(); dialog.showModal(); })
   .catch(() => { dialog.remove(); alert(tx('pil.da8f0a','Die Datei konnte nicht gelesen werden.')); });
 }

 const base = ensureDocumentCreationUi;
 ensureDocumentCreationUi = function () {
  base();
  const head = document.querySelector('#documentItems .sectionhead');
  if (!head || head.querySelector('#importDocumentItems')) return;
  const button = document.createElement('button'); button.type = 'button'; button.id = 'importDocumentItems'; button.className = 'secondary'; button.textContent = tx('pil.5cdefa','CSV importieren');
  const input = document.createElement('input'); input.type = 'file'; input.accept = '.csv,.txt,text/csv'; input.hidden = true;
  button.onclick = () => input.click();
  input.onchange = () => { const file = input.files[0]; input.value = ''; if (file) openImport(file); };
  head.append(button, input);
 };
})();
