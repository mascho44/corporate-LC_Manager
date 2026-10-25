/* Platform administration: one matrix, persons x tenants; every cell is the role of the person in that tenant (changed in place), with a switch to suspend the access. */
(() => {
 const DEFAULT_TENANT = '00000000-0000-0000-0000-000000000001';
 const administrative = role => (role.permissions || []).some(p => p === 'USER_MANAGE' || p === 'SETTINGS_MANAGE');
 const lower = s => String(s || '').toLowerCase();
 /** The home tenant only holds the accounts and is not a workspace; the server marks it (older answers fall back to the first default). */
 const isHome = c => c.home === true || (c.home === undefined && c.id === DEFAULT_TENANT);

 /** Memberships grouped by user id. */
 function byUser(rows) {
  const map = new Map();
  (rows || []).forEach(r => { if (!map.has(r.userId)) map.set(r.userId, []); map.get(r.userId).push(r); });
  return map;
 }
 /** Human-readable state of one membership. */
 function stateOf(row) {
  if (!row) return '';
  if (!row.accountActive) return tx('pu.0','Konto gesperrt');
  if (row.suspended || !row.membershipActive) return tx('pu.1','Zugang ausgesetzt');
  if (!row.tenantActive) return tx('pu.2','Mandant ausgesetzt');
  return 'aktiv';
 }
 /** Users matching the filter, sorted by name. state: all | active | inactive | pending | platform. */
 function userList(accounts, rows, filter = {}) {
  const grouped = byUser(rows), text = lower(filter.text).trim();
  return (accounts || []).filter(a => {
   const own = grouped.get(a.id) || [];
   if (filter.tenantId && !own.some(r => r.tenantId === filter.tenantId)) return false;
   if (filter.state === 'active' && !(a.active && !a.invitationPending)) return false;
   if (filter.state === 'inactive' && a.active) return false;
   if (filter.state === 'pending' && !a.invitationPending) return false;
   if (filter.state === 'platform' && !a.platformAdministrator) return false;
   if (!text) return true;
   return [a.username, a.displayName, a.email, ...own.map(r => r.tenantName), ...own.map(r => r.roleName)].some(v => lower(v).includes(text));
  }).sort((a, b) => lower(a.username).localeCompare(lower(b.username)));
 }
 /** Roles of a tenant; administrative roles are disabled for platform administrators (they belong to no tenant). */
 function roleOptions(account, choice) {
  return ((choice && choice.roles) || []).map(r => ({ id: r.id, name: r.name, disabled: !!(account && account.platformAdministrator && administrative(r)), reason: account && account.platformAdministrator && administrative(r) ? tx('pu.3','Plattformadministratoren verwalten keine Mandanten') : '' }));
 }
 /** Users × tenants; every cell is the membership row or null. */
 function matrix(choices, accounts, rows) {
  const columns = (choices || []).filter(c => !isHome(c)).sort((a, b) => lower(a.name).localeCompare(lower(b.name)));
  const grouped = byUser(rows);
  return { columns, rows: [...(accounts || [])].sort((a, b) => lower(a.username).localeCompare(lower(b.username))).map(a => ({ account: a, cells: columns.map(c => (grouped.get(a.id) || []).find(r => r.tenantId === c.id) || null) })) };
 }
 globalThis.LcPlatformUsers = { byUser, stateOf, userList, roleOptions, matrix, DEFAULT_TENANT };
 if (typeof document === 'undefined' || !document.body) return;

 const node = (tag, text, className) => { const n = document.createElement(tag); if (text !== undefined) n.textContent = text; if (className) n.className = className; return n; };
 const option = (value, label, disabled) => { const o = node('option', label); o.value = value; if (disabled) o.disabled = true; return o; };

 async function setup(section) {
  if (!section || typeof json !== 'function') return;
  const root = node('section', undefined, 'pu-root'); root.id = 'platformUsersSection';
  const message = node('p', ''); message.setAttribute('role', 'status');
  const body = node('div');
const search = document.createElement('input'); search.type = 'search'; search.placeholder = tx('pu.15','Suchen: Name, E-Mail, Mandant, Rolle'); search.setAttribute('aria-label', tx('pu.15','Suchen: Name, E-Mail, Mandant, Rolle')); search.oninput = () => render();
  /* render is a function declaration below and is hoisted */
  root.append(node('h3', tx('pu.7','Benutzer & Mandanten')), node('p', tx('pu.8','Personen sind globale Konten mit Rollen in mehreren Mandanten. Die Matrix zeigt und ändert, wer in welchem Mandanten welche Rolle hat; ein Plattformadministrator gehört keinem Mandanten.')), search, message, body);
  section.append(root);
  let accounts = [], rows = [], choices = [];

  async function load() {
   message.textContent = tx('pu.9','Lade …');
   [accounts, rows, choices] = await Promise.all([json('/api/platform/users'), json('/api/platform/memberships'), json('/api/platform/invitations/choices').catch(() => [])]);
   message.textContent = '';
   render();
  }
  const run = async (work, done) => { try { await work(); message.textContent = done || tx('pu.10','Gespeichert.'); await load(); } catch (e) { message.textContent = e.message; } };
  const call = (method, path, payload) => json(path, { method, headers: { 'Content-Type': 'application/json' }, body: payload === undefined ? undefined : JSON.stringify(payload) });
  const assign = (tenantId, username, roleId) => call('POST', '/api/platform/memberships', { tenantId, username, roleId });
  const changeRole = (tenantId, userId, roleId) => call('PUT', `/api/platform/memberships/${tenantId}/${userId}/role`, { roleId });
  const setSuspended = (tenantId, userId, suspended) => call('PUT', `/api/platform/memberships/${tenantId}/${userId}/access`, { suspended });
  const roleSelect = (account, choice, current) => { const s = document.createElement('select'); roleOptions(account, choice).forEach(r => { const o = option(r.id, r.name + (r.disabled ? tx('pu.11',' (nicht für Plattformadmins)') : ''), r.disabled); s.append(o); }); if (current) s.value = current; return s; };

  /* ---- the matrix: persons x tenants, every cell the role in that tenant ---- */
  const own = account => typeof currentUser !== 'undefined' && currentUser && String(currentUser.username || '').toLowerCase() === String(account.username || '').toLowerCase();
  function render() {
   const m = matrix(choices, userList(accounts, rows, { text: search.value }), rows);
   if (!m.columns.length) { body.replaceChildren(node('p', tx('pu.42','Es gibt noch keine Mandanten.'))); return; }
   const table = node('table', undefined, 'pu-table pu-matrix'); const head = node('tr'); head.append(node('th', tx('pu.12','Benutzer'))); m.columns.forEach(c => head.append(node('th', c.name))); table.append(head);
   m.rows.forEach(r => {
    const a = r.account, tr = node('tr'), first = node('td');
    first.append(node('b', a.displayName || a.username), node('small', ` ${a.username}${a.platformAdministrator ? ' · ' + tx('pu.pa','Plattformadministrator') : ''}${a.active ? '' : ' · ' + tx('pu.19','Gesperrt')}${a.invitationPending ? ' · ' + tx('pu.pending','Einladung offen') : ''}`));
    if (!own(a)) { const del = node('button', '🗑', 'ghost'); del.type = 'button'; del.title = tx('pu.delete','Konto löschen'); del.setAttribute('aria-label', del.title + ': ' + a.username);
     del.onclick = async () => { if (await confirmAction(tx('pu.deleteConfirm','Das Konto „{name}“ endgültig löschen? Zugänge, Anmeldedaten und persönliche Angaben entfallen; der Benutzername bleibt in der Historie erhalten und kann nicht neu vergeben werden. Das lässt sich nicht rückgängig machen.', { name: a.username }))) run(() => call('DELETE', '/api/platform/users/' + encodeURIComponent(a.id)), tx('pu.deleted','Konto gelöscht.')); };
     first.append(' ', del); }
    tr.append(first);
    m.columns.forEach((c, i) => {
     const cell = r.cells[i], td = node('td');
     const usable = a.active && !a.invitationPending && !a.platformAdministrator;
     if (cell && !cell.manageable) td.append(node('span', cell.roleName + tx('pu.46',' (Heimat-Identität)')));
     else if (!cell && !usable) td.append(node('span', '–', 'pu-cell none'));
     else {
      const select = document.createElement('select'); select.setAttribute('aria-label', `${a.username} – ${c.name}`);
      if (!cell) select.append(option('', '–'));
      roleOptions(a, c).forEach(role => select.append(option(role.id, role.name + (role.disabled ? tx('pu.11',' (nicht für Plattformadmins)') : ''), role.disabled)));
      select.value = cell ? cell.roleId : '';
      select.onchange = () => { if (!select.value) return; run(() => cell ? changeRole(c.id, a.id, select.value) : assign(c.id, a.username, select.value)); };
      td.append(select);
      if (cell) {
       if (cell.suspended || !cell.membershipActive) td.classList.add('pu-cell', 'off');
       const sus = node('button', cell.suspended ? '▶' : '⏸', cell.suspended ? 'secondary' : 'ghost'); sus.type = 'button';
       sus.title = cell.suspended ? tx('pu.37','Zugang wiederherstellen') : tx('pu.38','Zugang aussetzen'); sus.setAttribute('aria-label', sus.title + ': ' + a.username + ' – ' + c.name);
       sus.onclick = async () => { if (await confirmAction(`${sus.title}: ${a.username} in ${c.name}?`)) run(() => setSuspended(c.id, a.id, !cell.suspended)); };
       td.append(' ', sus);
       const state = stateOf(cell); if (state && state !== 'aktiv') td.append(node('small', ' ' + state));
      }
     }
     tr.append(td);
    });
    table.append(tr);
   });
   if (!m.rows.length) { const tr = node('tr'), td = node('td', tx('pu.23','Keine Benutzer gefunden.')); td.colSpan = m.columns.length + 1; tr.append(td); table.append(tr); }
   const wrap = node('div', undefined, 'members-table-wrap'); wrap.append(table);
   body.replaceChildren(node('small', tx('pu.55b','Pro Person und Mandant wählen Sie die Rolle; „–“ bedeutet kein Zugang, die Wahl einer Rolle weist den Zugang zu. ⏸ setzt den Zugang in diesem Mandanten aus, ▶ stellt ihn wieder her. Die Bereiche (Akkreditive, Garantien, Dokumente) stellt der Mandanten-Admin in seiner Zugriffsmatrix ein.')), wrap);
  }
  try { await load(); } catch (e) { message.textContent = e.message; }
 }
 globalThis.LcPlatformUsers.setup = setup;
})();
