/* Payment status of a file: summary, list of payments received, entry by hand (right to edit files). Other systems can report payments through the API. */
globalThis.LcPayments = {
 tone(status, overdue) { return overdue ? 'bad' : { NONE: 'none', PARTIAL: 'warn', PAID: 'ok', OVER: 'warn' }[status] || 'none'; },
 label(status) { return { NONE: tx('pay.s.none', 'noch keine Zahlung'), PARTIAL: tx('pay.s.partial', 'teilweise bezahlt'), PAID: tx('pay.s.paid', 'bezahlt'), OVER: tx('pay.s.over', 'mehr als der Akkreditivbetrag') }[status] || status; },
 async render(container, lcId) {
  let data; try { data = await json(`/api/lcs/${encodeURIComponent(lcId)}/payments`); } catch { return; }
  if (!container.isConnected) return;
  const money = (value, currency) => (typeof fmtMoney === 'function' ? fmtMoney(value, currency) : currency + ' ' + Number(value).toLocaleString(globalThis.LcI18n?.language?.() === 'en' ? 'en-GB' : 'de-DE', { minimumFractionDigits: 2 }));
  const day = value => typeof fmtDate === 'function' ? fmtDate(value) : value;
  const node = (tag, text, cls) => { const n = document.createElement(tag); if (text !== undefined) n.textContent = text; if (cls) n.className = cls; return n; };
  container.querySelector('.payment-block')?.remove();
  const block = node('section', undefined, 'payment-block sla-block');
  const s = data.summary;
  const head = node('h3', tx('pay.title', 'Zahlung')); const badge = node('span', LcPayments.label(s.status), 'sla-badge ' + LcPayments.tone(s.status, s.overdue)); head.append(' ', badge);
  block.append(head);
  const lines = [];
  if (s.lcAmount != null) lines.push(tx('pay.received', 'Eingegangen {received} von {amount}', { received: money(s.received, s.currency), amount: money(s.lcAmount, s.currency) }) + (s.status === 'PARTIAL' ? ' · ' + tx('pay.open', 'offen {open}', { open: money(s.open, s.currency) }) : ''));
  if (s.expectedDate && s.status !== 'PAID' && s.status !== 'OVER') lines.push(tx('pay.expected', 'Erwartet bis {date} (Schätzung aus den Fristen)', { date: day(s.expectedDate) }) + (s.overdue ? ' · ' + tx('pay.overdue', 'überfällig') : ''));
  if (s.otherCurrency) lines.push(tx('pay.otherCurrency', 'Zahlungen in anderer Währung sind erfasst, zählen aber nicht zum Betrag.'));
  lines.forEach(l => block.append(node('p', l)));
  if (data.payments.length) {
   const ul = node('ul', undefined, 'sla-list');
   data.payments.forEach(p => {
    const li = node('li', day(p.receivedOn) + ' · ' + money(p.amount, p.currency) + (p.reference ? ' · ' + p.reference : '') + (p.note ? ' · ' + p.note : '') + ' · ' + tx('pay.by', 'erfasst von {who}', { who: p.recordedBy || '–' }), 'ok');
    if (typeof can === 'function' && can('LC_EDIT')) {
     const del = node('button', tx('pay.delete', 'Löschen'), 'secondary'); del.type = 'button';
     del.onclick = async () => { if (!await confirmAction(tx('pay.confirmDelete', 'Zahlung vom {date} löschen?', { date: day(p.receivedOn) }))) return; try { await json(`/api/lcs/${encodeURIComponent(lcId)}/payments/${p.id}`, { method: 'DELETE' }); LcPayments.render(container, lcId); } catch (error) { alert(error.message); } };
     li.append(' ', del);
    }
    ul.append(li);
   });
   block.append(ul);
  }
  if (typeof can === 'function' && can('LC_EDIT')) {
   const add = node('button', tx('pay.add', 'Zahlung erfassen'), 'secondary'); add.type = 'button';
   add.onclick = () => {
    add.remove();
    const f = document.createElement('form'); f.className = 'form-grid payment-form';
    const field = (label, control) => { const l = node('label', label); l.append(control); f.append(l); return control; };
    const date = field(tx('pay.date', 'Eingegangen am'), document.createElement('input')); date.type = 'date'; date.required = true; date.value = new Date().toISOString().slice(0, 10); date.max = date.value;
    const amount = field(tx('pay.amount', 'Betrag'), document.createElement('input')); amount.type = 'number'; amount.step = '0.01'; amount.min = '0.01'; amount.required = true; if (s.open != null && s.open > 0) amount.value = s.open;
    const currency = field(tx('pay.currency', 'Währung'), document.createElement('input')); currency.maxLength = 3; currency.value = s.currency || '';
    const reference = field(tx('pay.reference', 'Referenz (optional)'), document.createElement('input')); reference.maxLength = 100;
    const note = field(tx('pay.note', 'Hinweis (optional)'), document.createElement('input')); note.maxLength = 500;
    const save = node('button', tx('pay.save', 'Speichern')); save.type = 'submit'; const cancel = node('button', tx('pay.cancel', 'Abbrechen'), 'secondary'); cancel.type = 'button'; cancel.onclick = () => LcPayments.render(container, lcId);
    const message = node('p', ''); message.setAttribute('role', 'status');
    const bar = node('div', undefined, 'payment-actions'); bar.append(save, cancel); f.append(bar, message);
    f.onsubmit = async event => {
     event.preventDefault();
     try { await json(`/api/lcs/${encodeURIComponent(lcId)}/payments`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ receivedOn: date.value, amount: amount.value, currency: currency.value, reference: reference.value, note: note.value }) }); LcPayments.render(container, lcId); }
     catch (error) { message.textContent = error.message; }
    };
    block.append(f);
   };
   block.append(add);
  }
  container.querySelector('.grid')?.before(block) || container.append(block);
 }
};
