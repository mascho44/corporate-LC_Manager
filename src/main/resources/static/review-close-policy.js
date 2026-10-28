/* Settings page "Prüfabschluss": from which LC amount (per currency) on the examination must be closed by several different people.
   Without a matching rule one person closes alone. Shown next to "Freigabestufen" for users with the settings right. */
(() => {
 const tr = (key, de, values = {}) => { const i = globalThis.LcI18n, s = i?.t ? i.t(key, values) : null; return s && s !== key ? s : String(de).replace(/\{(\w+)\}/g, (m, k) => values[k] ?? m); };

 /** Cleans the rows of the form: upper-case currency, numbers; throws a message for incomplete rows. */
 function normalize(rows) {
  return rows.map(row => {
   const currency = String(row.currency || '').trim().toUpperCase(), amount = Number(row.minimumAmount), closers = Number(row.requiredClosers);
   if (!/^[A-Z]{3}$/.test(currency) || row.minimumAmount === '' || !Number.isFinite(amount) || amount < 0) throw new Error(tr('rp.incomplete', 'Bitte Währung (3 Buchstaben) und Mindestbetrag für jede Grenze angeben.'));
   if (!Number.isInteger(closers) || closers < 1 || closers > 5) throw new Error(tr('rp.count', 'Die Zahl der Personen muss zwischen 1 und 5 liegen.'));
   return { currency, minimumAmount: amount, requiredClosers: closers };
  });
 }
 globalThis.LcReviewClosePolicy = { normalize };
 if (typeof document === 'undefined' || !document.body) return;

 const el = (tag, props = {}, ...children) => { const node = Object.assign(document.createElement(tag), props); node.append(...children); return node; };

 function build() {
  const anchor = document.querySelector('#appNavApprovalPolicy');
  if (!anchor || document.querySelector('#appNavReviewClosePolicy')) return;
  const nav = el('button', { id: 'appNavReviewClosePolicy', type: 'button', textContent: tr('rp.nav', 'Prüfabschluss') }); anchor.after(nav);
  const dialog = el('dialog', { id: 'reviewClosePolicyDialog', className: 'wide-dialog' });
  const list = el('div', { className: 'approval-threshold-list' }), error = el('div', { className: 'error', role: 'alert' });
  const close = el('button', { type: 'button', className: 'ghost', textContent: '×' }); close.onclick = () => dialog.close();
  dialog.append(el('div', { className: 'dialoghead' }, el('div', {}, el('h2', { textContent: tr('rp.title', 'Prüfabschluss') }), el('p', { textContent: tr('rp.intro', 'Je Währung und Mindestbetrag lässt sich festlegen, wie viele verschiedene Personen die Prüfung abschließen müssen. Ohne passende Regel genügt eine Person.') })), close), list, error);
  const render = rules => {
   list.replaceChildren();
   for (const rule of rules) {
    const currency = el('input', { value: rule.currency || '', maxLength: 3, size: 4 }), amount = el('input', { value: rule.minimumAmount ?? '', inputMode: 'decimal' }), closers = el('input', { type: 'number', min: 1, max: 5, value: rule.requiredClosers || 2 });
    const remove = el('button', { type: 'button', className: 'secondary', textContent: '×' }); remove.title = tr('rp.remove', 'Grenze entfernen');
    const row = el('div', { className: 'form-grid' }, el('label', {}, tr('rp.currency', 'Währung'), currency), el('label', {}, tr('rp.amount', 'ab Betrag'), amount), el('label', {}, tr('rp.closers', 'Personen'), closers), remove);
    row.dataset.row = '1'; row.read = () => ({ currency: currency.value, minimumAmount: amount.value, requiredClosers: closers.value }); remove.onclick = () => { row.remove(); };
    list.append(row);
   }
  };
  const read = () => [...list.querySelectorAll('[data-row]')].map(row => row.read());
  const add = el('button', { type: 'button', className: 'secondary', textContent: tr('rp.add', 'Grenze hinzufügen') }), save = el('button', { type: 'button', textContent: tr('rp.save', 'Speichern') });
  add.onclick = () => render([...read(), { currency: 'EUR', minimumAmount: '', requiredClosers: 2 }]);
  save.onclick = async () => { try { const saved = await json('/api/settings/review-close-thresholds', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(normalize(read())) }); render(saved); error.textContent = tr('rp.saved', 'Prüfabschluss gespeichert.'); } catch (e) { error.textContent = e.message; } };
  dialog.append(el('div', { className: 'actions' }, add, save)); document.body.append(dialog);
  nav.onclick = async () => { error.textContent = ''; dialog.showModal(); try { render(await json('/api/settings/review-close-thresholds')); } catch (e) { error.textContent = e.message; } };
 }
 let tries = 0;
 const wait = setInterval(() => { tries++; if (document.querySelector('#appNavApprovalPolicy')) { clearInterval(wait); build(); } else if (tries > 60) clearInterval(wait); }, 1000);
})();
