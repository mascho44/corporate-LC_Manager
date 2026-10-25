/* Liquidity preview (corporate profile): expected payments of the open credits by week or month and currency; inflows from export credits, outflows from import credits. */
async function setupLiquidity() {
 let data; try { data = await json('/api/liquidity'); } catch { return; } // not a corporate tenant: no page
 let granularity = 'WEEK';
 const node = (tag, value, cls) => { const n = document.createElement(tag); if (value !== undefined) n.textContent = value; if (cls) n.className = cls; return n; };
 const english = () => globalThis.LcI18n?.language?.() === 'en';
 const money = (value, currency) => Number(value).toLocaleString(english() ? 'en-GB' : 'de-DE', { style: 'currency', currency, maximumFractionDigits: 0 });
 const day = value => typeof fmtDate === 'function' ? fmtDate(value) : value;
 const nav = node('button', tx('liq.nav', 'Liquiditätsvorschau')); nav.type = 'button'; nav.id = 'appNavLiquidity'; nav.dataset.i18n = 'liq.nav';
 document.querySelector('#appNav nav').append(nav);
 const section = document.createElement('section'); section.id = 'liquiditySection'; section.className = 'panel hidden';
 const bar = node('div', undefined, 'liq-bar'); const tableBox = document.createElement('div'); const listBox = document.createElement('div');
 section.append(node('h2', tx('liq.title', 'Liquiditätsvorschau')), node('p', tx('liq.intro', 'Erwartete Zahlungen aus den offenen Akkreditiven: Eingänge aus Export-Akkreditiven, Ausgänge aus Import-Akkreditiven. Alles ist eine Schätzung aus der Zahlungsklausel (siehe Fristen); Beträge sind die Rechnungen der Akte (höchstens der Akkreditivbetrag), sonst der Akkreditivbetrag, abzüglich erfasster Zahlungen.')), bar, tableBox, listBox);
 document.querySelector('main').append(section);

 const periodLabel = p => p.key === 'OVERDUE' ? tx('liq.overdue', 'Überfällig') : p.key === 'NO_DATE' ? tx('liq.noDate', 'Ohne Termin') : granularity === 'WEEK' ? tx('liq.week', 'KW {key} ({from})', { key: p.key.split('-W')[1], from: day(p.from) }) : p.key;
 const csvRows = () => {
  const head = [tx('liq.col.period', 'Zeitraum'), tx('liq.col.currency', 'Währung'), tx('liq.col.in', 'Eingänge'), tx('liq.col.out', 'Ausgänge'), tx('liq.col.net', 'Saldo')];
  const rows = [];
  data.forecast.periods.forEach(p => Object.entries(p.byCurrency).forEach(([cur, a]) => rows.push([periodLabel(p), cur, a.in, a.out, a.net])));
  return [head, ...rows];
 };
 function draw() {
  bar.replaceChildren(); tableBox.replaceChildren(); listBox.replaceChildren();
  const mode = document.createElement('select');
  [['WEEK', tx('liq.byWeek', 'Nach Kalenderwochen')], ['MONTH', tx('liq.byMonth', 'Nach Monaten')]].forEach(([value, text]) => { const o = node('option', text); o.value = value; mode.append(o); });
  mode.value = granularity; mode.onchange = async () => { granularity = mode.value; try { data = await json('/api/liquidity?granularity=' + granularity); } catch (error) { return; } draw(); };
  const csv = node('button', tx('liq.csv', 'Als CSV speichern'), 'secondary'); csv.type = 'button';
  csv.onclick = () => { const cell = v => '"' + String(v).replace(/"/g, '""') + '"'; const blob = new Blob(['﻿' + csvRows().map(r => r.map(cell).join(';')).join('\r\n')], { type: 'text/csv;charset=utf-8' }); const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = 'liquiditaetsvorschau-' + data.forecast.today + '.csv'; a.click(); setTimeout(() => URL.revokeObjectURL(a.href), 1000); };
  bar.append(mode, csv);
  const currencies = data.forecast.currencies;
  if (!currencies.length) { tableBox.append(node('p', tx('liq.none', 'Keine offenen Akkreditive mit erwarteter Zahlung.'))); return; }
  currencies.forEach(cur => {
   tableBox.append(node('h3', cur));
   const table = node('table', undefined, 'sla-report liq-table'); const thead = document.createElement('thead'); const hr = document.createElement('tr');
   [tx('liq.col.period', 'Zeitraum'), tx('liq.col.in', 'Eingänge'), tx('liq.col.out', 'Ausgänge'), tx('liq.col.net', 'Saldo')].forEach(h => hr.append(node('th', h))); thead.append(hr); table.append(thead);
   const body = document.createElement('tbody'); let running = 0;
   data.forecast.periods.forEach(p => {
    const a = p.byCurrency[cur]; if (!a && p.key !== 'OVERDUE' && p.key !== 'NO_DATE' && !data.forecast.periods.some(x => x.byCurrency[cur])) return;
    if (!a && (p.key === 'OVERDUE' || p.key === 'NO_DATE')) return;
    const tr = document.createElement('tr'); if (p.key === 'OVERDUE') tr.className = 'liq-overdue';
    tr.append(node('th', periodLabel(p)), node('td', a ? money(a.in, cur) : '–'), node('td', a ? money(a.out, cur) : '–'), node('td', a ? money(a.net, cur) : '–', a && a.net < 0 ? 'liq-negative' : ''));
    body.append(tr); if (a && p.key !== 'NO_DATE') running += Number(a.net);
   });
   table.append(body); const wrap = node('div', undefined, 'sla-report-wrap'); wrap.append(table); tableBox.append(wrap);
  });
  listBox.append(node('h3', tx('liq.items', 'Posten')));
  if (data.truncated) listBox.append(node('p', tx('liq.truncated', 'Es sind sehr viele Akten; die Vorschau berücksichtigt die ersten 500.')));
  const ul = node('ul', undefined, 'sla-list');
  data.items.forEach(i => {
   const when = i.date ? day(i.date) : tx('liq.noDate', 'Ohne Termin');
   const basis = i.basis === 'INVOICE' ? tx('liq.basis.invoice', 'Rechnung') : tx('liq.basis.credit', 'Akkreditivbetrag');
   const li = node('li', `${when} · ${i.reference || '–'}${i.counterparty ? ' · ' + i.counterparty : ''} · ${i.direction === 'IN' ? tx('liq.in', 'Eingang') : tx('liq.out', 'Ausgang')} ${money(i.amount, i.currency)} (${basis})`, !i.date ? 'warn' : new Date(i.date) < new Date(data.forecast.today) ? 'bad' : i.direction === 'IN' ? 'ok' : 'warn');
   ul.append(li);
  });
  listBox.append(ul);
 }
 nav.onclick = async () => { try { data = await json('/api/liquidity?granularity=' + granularity); } catch { /* keep the last data */ } appNavigate('liquidity', nav); draw(); };
 draw();
}
