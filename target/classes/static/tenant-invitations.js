/* Invitations of the current tenant: tenant administrators invite new people by e-mail instead of asking the platform administration. */
(() => {
 const STATUS = { SENT: 'versendet', PENDING_MAIL: 'wird versendet', MAIL_RETRY: 'Versand wird erneut versucht', SMTP_FAILED: 'Versand fehlgeschlagen', MAIL_FAILED: 'Versand fehlgeschlagen', MAIL_CANCELLED: 'Versand abgebrochen', EXPIRED: 'abgelaufen' };
 /** Delivery status in words; unknown values are shown as they are. */
 function statusText(status) { return STATUS[String(status || '').toUpperCase()] || String(status || ''); }
 const PERMISSIONS = { AUDIT_VIEW: 'Audit-Protokoll ansehen', DOCUMENT_APPROVE: 'Dokumente freigeben', DOCUMENT_DELETE: 'Dokumente löschen', DOCUMENT_GENERATE: 'Dokumente erstellen', DOCUMENT_REVIEW: 'Dokumente prüfen',
  DOCUMENT_UPLOAD: 'Dokumente hochladen', EMAIL_SEND: 'E-Mails senden', LC_DELETE: 'LC-Akten löschen', LC_EDIT: 'LC-Akten bearbeiten', SETTINGS_MANAGE: 'Einstellungen verwalten',
  SWIFT_IMPORT: 'SWIFT-Nachrichten importieren', TRAINING_MANAGE: 'Training verwalten', USER_MANAGE: 'Benutzer verwalten' };
 /** Permission codes in words; unknown codes are shown as they are. */
 function permissionText(codes) { const words = (codes || []).map(c => PERMISSIONS[c] || c).sort((a, b) => a.localeCompare(b, 'de')); return words.length ? words.join(' · ') : 'keine'; }

 const EMAIL = /^[^\s@<>;,]+@[^\s@<>;,]+\.[^\s@<>;,]{2,}$/;
 const MAX_BULK = 50;
 /** A readable name from the part before the @ ("max.mustermann" -> "Max Mustermann"). */
 function nameFromEmail(email) { return String(email).split('@')[0].split(/[._\-+]+/).filter(Boolean).map(p => p.charAt(0).toUpperCase() + p.slice(1)).join(' ') || email; }
 /**
  * People to invite from pasted text, one per line (or separated by ";" when written on one line):
  * "Name <mail@x>", "mail@x", "mail@x, Name", "Name; mail@x", "Name, mail@x". Returns the valid entries (duplicates by e-mail skipped) and a message per bad line.
  */
 function parseInviteLines(text) {
  const entries = [], problems = [], seen = new Set();
  const lines = String(text || '').split(/\r?\n/);
  lines.forEach((raw, index) => {
   const line = raw.trim(); if (!line) return;
   let name = '', email = '';
   const angle = line.match(/^(.*?)<\s*([^<>\s]+)\s*>\s*$/);
   if (angle) { name = angle[1].trim().replace(/^["']|["']$/g, ''); email = angle[2]; }
   else {
    const parts = line.split(/[;,\t]/).map(p => p.trim()).filter(Boolean);
    const found = parts.find(p => EMAIL.test(p));
    if (found) { email = found; name = parts.filter(p => p !== found).join(' '); }
    else email = line;
   }
   if (!EMAIL.test(email)) { problems.push(`Zeile ${index + 1}: „${line.slice(0, 40)}" enthält keine gültige E-Mail-Adresse.`); return; }
   const key = email.toLowerCase();
   if (seen.has(key)) { problems.push(`Zeile ${index + 1}: ${email} steht schon in der Liste.`); return; }
   seen.add(key); entries.push({ displayName: (name || nameFromEmail(email)).slice(0, 255), email });
  });
  if (entries.length > MAX_BULK) { problems.push(`Es werden nur die ersten ${MAX_BULK} Personen eingeladen (${entries.length} angegeben).`); entries.length = MAX_BULK; }
  return { entries, problems };
 }
 /** Short statement about a role's rights: "Alle 13 Rechte", or "12 von 13 Rechten – es fehlen: …" (the missing ones when there are at most four). */
 function permissionSummary(codes) {
  const all = Object.keys(PERMISSIONS), have = new Set(codes || []), missing = all.filter(c => !have.has(c));
  if (!have.size) return 'Keine Rechte (nur ansehen)';
  if (!missing.length) return `Alle ${all.length} Rechte`;
  const base = `${all.length - missing.length} von ${all.length} Rechten`;
  return missing.length <= 4 ? `${base} – es fehlen: ${missing.map(c => PERMISSIONS[c]).sort((a, b) => a.localeCompare(b, 'de')).join(', ')}` : base;
 }
 globalThis.LcTenantInvitations = { statusText, permissionText, parseInviteLines, permissionSummary, nameFromEmail };
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
  const bulk = node('details', undefined, 'tenant-invitation-bulk'); bulk.append(node('summary', 'Mehrere Personen auf einmal einladen'));
  const bulkText = document.createElement('textarea'); bulkText.rows = 5; bulkText.maxLength = 8000; bulkText.setAttribute('aria-label', 'Personen, eine pro Zeile'); bulkText.placeholder = 'Eine Person pro Zeile, z. B.\nAnna Beispiel <anna@firma.de>\nben@firma.de\ncem@firma.de; Cem Muster';
  const bulkInfo = node('small', 'Dieselbe Rolle wie oben. Zeilen: „Name <E-Mail>", „E-Mail" oder „E-Mail; Name" (höchstens 50).');
  const bulkSend = node('button', 'Alle einladen'); bulkSend.type = 'button';
  const bulkResult = node('div', undefined, 'tenant-invitation-bulk-result'); bulkResult.setAttribute('role', 'status');
  bulk.append(bulkText, bulkInfo, bulkSend, bulkResult);
  box.append(node('h4', 'Neue Personen einladen'), node('small', 'Die Person erhält eine E-Mail mit einem Link (24 Stunden gültig), wählt dort ihr Passwort und wird mit der gewählten Rolle diesem Mandanten zugeordnet.'), form, bulk, message, node('h4', 'Offene Einladungen'), list);
  body.prepend(box);
  let roles = [];
  const permissions = () => {
   const r = roles.find(x => x.id === role.value); preview.replaceChildren();
   if (!r) return;
   preview.append(document.createTextNode(permissionSummary(r.permissions) + ' · '));
   const link = node('button', 'Rollen vergleichen', 'link-button'); link.type = 'button'; link.onclick = () => globalThis.LcMembersPage?.showTab?.('roles'); preview.append(link);
  };
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
  bulkSend.onclick = async () => {
   if (busy) return;
   const { entries, problems } = parseInviteLines(bulkText.value);
   bulkResult.replaceChildren(...problems.map(p => node('p', p, 'error')));
   if (!entries.length) { if (!problems.length) bulkResult.append(node('p', 'Bitte mindestens eine Person eintragen.')); return; }
   const roleName = roles.find(r => r.id === role.value)?.name || 'der gewählten Rolle';
   if (typeof confirmAction === 'function' && !await confirmAction(`${entries.length} Person${entries.length === 1 ? '' : 'en'} als ${roleName} einladen?`)) return;
   busy = true; bulkSend.disabled = true; let ok = 0;
   for (const [i, entry] of entries.entries()) {
    bulkSend.textContent = `Einladen … ${i + 1} von ${entries.length}`;
    try { const r = await call('POST', '/api/users/invitations', { displayName: entry.displayName, email: entry.email, roleId: role.value }); if (r.invited) ok++; bulkResult.append(node('p', `${r.invited ? '✓' : '✗'} ${entry.email}: ${r.message}`, r.invited ? 'ok' : 'error')); }
    catch (e) { bulkResult.append(node('p', `✗ ${entry.email}: ${e.message}`, 'error')); }
   }
   bulkResult.prepend(node('p', `${ok} von ${entries.length} Einladungen versendet.`, 'tenant-invitation-bulk-summary'));
   bulkSend.textContent = 'Alle einladen'; bulkSend.disabled = false; busy = false; if (ok) bulkText.value = '';
   try { await load(); } catch (e) { message.textContent = e.message; }
  };
  try { await load(); } catch (e) { message.textContent = e.message; }
 }
 globalThis.LcTenantInvitations.render = render;
})();
