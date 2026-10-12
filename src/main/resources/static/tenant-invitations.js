/* Invitations of the current tenant: tenant administrators invite new people by e-mail instead of asking the platform administration. */
(() => {
 const STATUS = { SENT: 'versendet', PENDING_MAIL: 'wird versendet', MAIL_RETRY: 'Versand wird erneut versucht', SMTP_FAILED: 'Versand fehlgeschlagen', EXPIRED: 'abgelaufen' };
 /** Delivery status in words; unknown values are shown as they are. */
 function statusText(status) { return STATUS[String(status || '').toUpperCase()] || String(status || ''); }
 globalThis.LcTenantInvitations = { statusText };
 if (typeof document === 'undefined' || !document.body) return;

 const node = (tag, text, className) => { const n = document.createElement(tag); if (text !== undefined) n.textContent = text; if (className) n.className = className; return n; };
 const fmt = value => { try { return value ? new Intl.DateTimeFormat('de-DE', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value)) : ''; } catch (ignored) { return String(value || ''); } };

 async function render(body) {
  if (!body || typeof json !== 'function' || body.querySelector('#tenantInvitations')) return;
  const box = node('section', undefined, 'tenant-invitations'); box.id = 'tenantInvitations';
  const message = node('p', ''); message.setAttribute('role', 'status');
  const form = document.createElement('form'); form.className = 'membership-role-controls';
  const name = document.createElement('input'); name.required = true; name.maxLength = 255; name.placeholder = 'Vollständiger Name'; name.setAttribute('aria-label', name.placeholder);
  const email = document.createElement('input'); email.type = 'email'; email.required = true; email.maxLength = 255; email.placeholder = 'E-Mail-Adresse'; email.setAttribute('aria-label', email.placeholder);
  const role = document.createElement('select'); role.required = true; role.setAttribute('aria-label', 'Rolle');
  const preview = node('small', ''); const send = node('button', 'Einladen'); send.type = 'submit';
  form.append(name, email, role, preview, send);
  const list = node('div', undefined, 'tenant-invitation-list');
  box.append(node('h4', 'Neue Personen einladen'), node('small', 'Die Person erhält eine E-Mail mit einem Link (24 Stunden gültig), wählt dort ihr Passwort und wird mit der gewählten Rolle diesem Mandanten zugeordnet.'), form, message, node('h4', 'Offene Einladungen'), list);
  body.prepend(box);
  let roles = [];
  const permissions = () => { const r = roles.find(x => x.id === role.value); preview.textContent = r ? 'Berechtigungen: ' + ((r.permissions || []).slice().sort().join(', ') || 'keine') : ''; };
  role.onchange = permissions;
  const call = (method, path, payload) => json(path, { method, headers: { 'Content-Type': 'application/json' }, body: payload === undefined ? undefined : JSON.stringify(payload) });
  async function load() {
   const overview = await json('/api/users/invitations');
   roles = overview.roles || [];
   const keep = role.value; role.replaceChildren(); roles.forEach(r => { const o = node('option', r.name); o.value = r.id; role.append(o); }); if (keep) role.value = keep; permissions();
   list.replaceChildren();
   const rows = overview.invitations || [];
   if (!rows.length) list.append(node('small', 'Keine offenen Einladungen.'));
   rows.forEach(i => {
    const row = node('div', undefined, 'tenant-invitation-row');
    row.append(node('b', i.email), node('span', ` · ${i.roleName} · ${statusText(i.status)} · gültig bis ${fmt(i.expiresAt)}`));
    const again = node('button', 'Erneut senden', 'secondary'); again.type = 'button';
    again.onclick = async () => { try { await call('POST', `/api/users/invitations/${i.userId}/resend`); message.textContent = 'Einladung erneut versendet.'; await load(); } catch (e) { message.textContent = e.message; } };
    const revoke = node('button', 'Widerrufen', 'danger'); revoke.type = 'button';
    revoke.onclick = async () => { if (typeof confirmAction === 'function' && !await confirmAction(`Einladung an ${i.email} widerrufen?`)) return; try { await call('DELETE', `/api/users/invitations/${i.userId}`); message.textContent = 'Einladung widerrufen.'; await load(); } catch (e) { message.textContent = e.message; } };
    row.append(' ', again, ' ', revoke); list.append(row);
   });
  }
  let busy = false;
  form.onsubmit = async event => {
   event.preventDefault(); if (busy) return; busy = true; send.disabled = true; message.textContent = '';
   try {
    const r = await call('POST', '/api/users/invitations', { displayName: name.value.trim(), email: email.value.trim(), roleId: role.value });
    message.textContent = r.message; if (r.invited) { name.value = ''; email.value = ''; } await load();
   } catch (e) { message.textContent = e.message; } finally { busy = false; send.disabled = false; }
  };
  try { await load(); } catch (e) { message.textContent = e.message; }
 }
 globalThis.LcTenantInvitations.render = render;
})();
