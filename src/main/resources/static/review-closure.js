/* Closing the examination of a file: up to a limit set in the settings one person closes alone, above it several different people sign off.
   Shows the matching control (close, sign off, withdraw, done) and talks to /api/lcs/{id}/review-closure. */
(() => {
 const tr = (key, de, values = {}) => { const i = globalThis.LcI18n, s = i?.t ? i.t(key, values) : null; return s && s !== key ? s : String(de).replace(/\{(\w+)\}/g, (m, k) => values[k] ?? m); };
 const JSON_HEADERS = { 'Content-Type': 'application/json' };

 /** What the control shows for a closure state (null = not started) and the current user: `kind` is close | wait | done, plus the actions that apply. */
 function describe(state, user) {
  if (!state) return { kind: 'close' };
  const signers = state.signers || [], mine = signers.some(name => String(name).toLowerCase() === String(user).toLowerCase());
  if (state.completed) return { kind: 'done', signers };
  return { kind: 'wait', signers, missing: Math.max(0, state.required - signers.length), canSign: !mine, canWithdraw: String(state.startedBy).toLowerCase() === String(user).toLowerCase() };
 }
 globalThis.LcReviewClosure = { describe };
 if (typeof document === 'undefined' || !document.body) return;

 /** The control for one file; `note()` returns the note text for a new closure, `onChange` runs after every change. */
 function control(lcId, note, className = '', onChange = () => {}) {
  const host = document.createElement('div'); host.className = 'review-closure';
  const url = `/api/lcs/${lcId}/review-closure`;
  const button = (label, onClick, secondary) => { const b = document.createElement('button'); b.type = 'button'; b.textContent = label; if (secondary) b.className = 'secondary'; b.onclick = async () => { b.disabled = true; try { await onClick(); await load(); onChange(); } catch (error) { b.disabled = false; alert(error.message); } }; return b; };
  const text = value => { const p = document.createElement('small'); p.textContent = value; return p; };
  async function load() {
   let state = null;
   try { const response = await fetch(url, { credentials: 'same-origin' }); if (response.ok && response.status !== 204) state = await response.json(); } catch { /* shown as not started */ }
   const view = describe(state, typeof currentUser === 'undefined' ? '' : currentUser?.username);
   host.replaceChildren();
   if (view.kind === 'close') host.append(button(tr('rc.close', 'Prüfung abschließen'), () => json(url, { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify({ summary: note() }) }), className === 'secondary'));
   else if (view.kind === 'done') host.append(text(tr('rc.done', 'Prüfung abgeschlossen ✓ ({names})', { names: view.signers.join(', ') })));
   else {
    host.append(text(tr('rc.pending', 'Abschluss vorgemerkt von {names}. Noch {missing} weitere Person(en) nötig.', { names: view.signers.join(', '), missing: view.missing })));
    if (view.canSign) host.append(button(tr('rc.signOff', 'Abschluss mitzeichnen'), () => json(url + '/sign-off', { method: 'POST' })));
    if (view.canWithdraw) host.append(button(tr('rc.withdraw', 'Zurückziehen'), () => json(url, { method: 'DELETE' }), true));
   }
  }
  load();
  return host;
 }
 globalThis.LcReviewClosure.control = control;
})();
