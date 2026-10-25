/* "Änderung beantragen": a request to amend the credit, to the issuing bank (importer) or to the buyer (exporter), as PDF letter or as MT707 field text. */
(() => {
 const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
 const tr = (key, de, values = {}) => { const i = globalThis.LcI18n, s = i?.t ? i.t(key, values) : null; return s && s !== key ? s : String(de).replace(/\{(\w+)\}/g, (_, n) => String(values[n] ?? `{${n}}`)); };
 const FIELDS = ['date', 'recipient', 'ourReference', 'newAmount', 'newExpiryDate', 'newExpiryPlace', 'newLatestShipmentDate', 'goods', 'documents', 'conditions', 'other', 'reason'];

 /** Request body: empty fields are left out (null), amounts accept German or English decimals. */
 function requestBody(form) {
  const text = v => (v == null || String(v).trim() === '') ? null : String(v).trim();
  const body = {};
  for (const key of FIELDS) body[key] = text(form[key]);
  if (body.newAmount != null) {
   let a = body.newAmount.replace(/\s/g, '');
   a = /,\d{1,2}$/.test(a) ? a.replace(/\./g, '').replace(',', '.') : a.replace(/,/g, '');
   body.newAmount = Number.isFinite(Number(a)) ? Number(a) : body.newAmount;
  }
  return body;
 }
 /** True when at least one change is entered (the server compares with the credit). */
 const hasChange = body => ['newAmount', 'newExpiryDate', 'newLatestShipmentDate', 'goods', 'documents', 'conditions', 'other'].some(k => body[k] != null);
 globalThis.LcAmendmentRequest = { requestBody, hasChange };
 if (typeof document === 'undefined' || !document.body) return;

 const day = d => d ? new Date(d + 'T00:00:00').toLocaleDateString('de-DE') : '–';
 async function post(url, body) {
  const response = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': typeof csrfToken === 'string' ? csrfToken : '' }, body: JSON.stringify(body) });
  if (!response.ok) { let message = `HTTP ${response.status}`; try { message = (await response.json()).error || message; } catch (ignored) { /* no JSON body */ } throw new Error(message); }
  return response;
 }

 async function open() {
  if (typeof activeLc === 'undefined' || !activeLc) return;
  const lcId = activeLc.id, base = `/api/lcs/${encodeURIComponent(lcId)}/amendment-request`;
  const dialog = document.createElement('dialog'); dialog.className = 'wide-dialog amendment-request-dialog'; document.body.append(dialog); dialog.onclose = () => dialog.remove();
  dialog.innerHTML = `<div class="dialoghead"><h2>${tr('ar.title', 'Änderung beantragen')}</h2><button type="button" data-close>${tr('common.close', 'Schließen')}</button></div><p role="status">${tr('common.preparing', 'Wird vorbereitet …')}</p>`;
  dialog.querySelector('[data-close]').onclick = () => dialog.close(); dialog.showModal();
  let p; try { p = await json(`${base}/proposal`); } catch (e) { dialog.querySelector('[role=status]').textContent = e.message; return; }
  const state = { date: p.date, recipient: p.recipient || '', newAmount: p.newAmount ?? '', newExpiryDate: p.newExpiryDate || '', newLatestShipmentDate: p.newLatestShipmentDate || '' };
  const input = (key, label, type = 'text', extra = '') => `<label>${esc(label)}<input data-field="${key}" type="${type}"${extra}></label>`;
  const area = (key, label, hint) => `<label>${esc(label)}${hint ? ` <small>${esc(hint)}</small>` : ''}<textarea data-field="${key}" rows="2" maxlength="4000"></textarea></label>`;
  dialog.innerHTML = `<div class="dialoghead"><h2>${tr('ar.title', 'Änderung beantragen')}</h2><button type="button" data-close>${tr('common.close', 'Schließen')}</button></div>` +
   `<p>${p.toBank ? tr('ar.toBank', 'Antrag an die eröffnende Bank') : tr('ar.toBuyer', 'Bitte an den Käufer, das Akkreditiv bei seiner Bank ändern zu lassen')}. ${tr('ar.onlyFilled', 'Nur ausgefüllte Felder werden beantragt; alles andere bleibt unverändert.')}</p>` +
   (p.hints?.length ? `<div class="ar-hints"><b>${tr('ar.suggested', 'Aus den offenen Abweichungen vorgeschlagen:')}</b><ul>${p.hints.map(h => `<li>${esc(h)}</li>`).join('')}</ul></div>` : '') +
   `<div class="ar-grid">${input('date', tr('common.date', 'Datum'), 'date')}${input('ourReference', tr('common.ourReference', 'Unsere Referenz'))}</div><label>${tr('ar.recipient', 'Empfänger')}<textarea data-field="recipient" rows="3" maxlength="1000"></textarea></label>` +
   `<h3>${tr('ar.changes', 'Änderungen')}</h3><div class="ar-grid">${input('newAmount', tr('ar.newAmount', 'Neuer Betrag (bisher {v})', { v: `${p.currentAmount ?? '–'} ${p.currency || ''}`.trim() }), 'text', ' inputmode="decimal"')}` +
   `${input('newExpiryDate', tr('ar.newExpiry', 'Neuer Ablauf (bisher {v})', { v: day(p.currentExpiry) }), 'date')}${input('newExpiryPlace', tr('ar.expiryPlace', 'Ort des Ablaufs (optional)'))}${input('newLatestShipmentDate', tr('ar.newShipment', 'Neuer letzter Verladetag (bisher {v})', { v: day(p.currentLatestShipment) }), 'date')}</div>` +
   area('goods', tr('ar.goods', 'Warenbeschreibung (45B)'), tr('ar.newWording', 'neuer Wortlaut')) + area('documents', tr('ar.documents', 'Dokumentenanforderungen (46B)')) + area('conditions', tr('ar.conditions', 'Zusatzbedingungen (47B)')) + area('other', tr('ar.other', 'Sonstiges')) +
   `<label>${tr('ar.reason', 'Begründung (optional, erscheint im Schreiben)')}<textarea data-field="reason" rows="2" maxlength="2000"></textarea></label><p data-error role="alert"></p>` +
   `<div class="dialog-actions"><button type="button" class="secondary" data-text>${tr('ar.text', 'Text für Bankportal (MT707-Felder)')}</button><button type="button" data-pdf>${tr('ar.pdf', 'Schreiben als PDF')}</button></div><div data-out></div>`;
  dialog.querySelector('[data-close]').onclick = () => dialog.close();
  dialog.querySelectorAll('[data-field]').forEach(el => { el.value = state[el.dataset.field] ?? ''; el.oninput = () => { state[el.dataset.field] = el.value; }; });
  const error = dialog.querySelector('[data-error]'), out = dialog.querySelector('[data-out]');
  const body = () => { const b = requestBody(state); if (!b.recipient) throw new Error(tr('ar.needRecipient', 'Bitte den Empfänger angeben.')); if (!hasChange(b)) throw new Error(tr('ar.needChange', 'Bitte mindestens eine Änderung angeben.')); return b; };
  dialog.querySelector('[data-pdf]').onclick = async event => {
   const button = event.currentTarget; error.textContent = ''; button.disabled = true;
   try {
    const blob = await (await post(base, body())).blob();
    const link = document.createElement('a'); link.href = URL.createObjectURL(blob); link.download = `Aenderungsantrag-${activeLc?.reference || 'LC'}.pdf`; document.body.append(link); link.click(); link.remove(); setTimeout(() => URL.revokeObjectURL(link.href), 10000);
    out.innerHTML = `<p class="ar-ok">${tr('ar.downloaded', 'Das Schreiben wurde heruntergeladen.')}</p>`;
   } catch (e) { error.textContent = e.message; } finally { button.disabled = false; }
  };
  dialog.querySelector('[data-text]').onclick = async event => {
   const button = event.currentTarget; error.textContent = ''; button.disabled = true;
   try {
    const { text } = await (await post(`${base}/text`, body())).json();
    out.innerHTML = `<label>${tr('ar.textLabel', 'Vorlage im Feldformat einer MT707 (zum Übertragen ins Bankportal, keine SWIFT-Nachricht)')}<textarea readonly rows="8" class="ar-text"></textarea></label><button type="button" class="secondary" data-copy>${tr('common.copy', 'Kopieren')}</button>`;
    out.querySelector('textarea').value = text;
    out.querySelector('[data-copy]').onclick = async e => { try { await navigator.clipboard.writeText(text); e.currentTarget.textContent = tr('common.copied', 'Kopiert ✓'); } catch { out.querySelector('textarea').select(); } };
   } catch (e) { error.textContent = e.message; } finally { button.disabled = false; }
  };
 }

 function enhance() {
  if (typeof can !== 'function' || !can('DOCUMENT_GENERATE')) return;
  const upload = document.querySelector('#uploadBtn');
  if (!upload || document.querySelector('#amendmentRequestBtn')) return;
  const button = document.createElement('button'); button.type = 'button'; button.id = 'amendmentRequestBtn'; button.className = 'secondary';
  button.textContent = tr('ar.title', 'Änderung beantragen'); button.title = tr('ar.buttonTitle', 'Änderungsantrag an die Bank oder Bitte an den Käufer, mit Vorschlägen aus den offenen Abweichungen');
  button.onclick = open; upload.before(button);
 }
 let scheduled = false;
 new MutationObserver(() => { if (!scheduled) { scheduled = true; setTimeout(() => { scheduled = false; enhance(); }, 80); } }).observe(document.body, { childList: true, subtree: true });
 enhance();
})();
