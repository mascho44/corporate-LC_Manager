/* Platform administration: users and tenants managed with lists (user list, tenant member list, matrix) instead of typing user names. */
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
 /** Tenants the user can still be added to (the identity home is not a workspace). */
 function addableTenants(account, choices, rows) {
  const has = new Set((byUser(rows).get(account.id) || []).map(r => r.tenantId));
  return (choices || []).filter(c => !isHome(c) && !has.has(c.id));
 }
 /** Roles of a tenant; administrative roles are disabled for platform administrators (they belong to no tenant). */
 function roleOptions(account, choice) {
  return ((choice && choice.roles) || []).map(r => ({ id: r.id, name: r.name, disabled: !!(account && account.platformAdministrator && administrative(r)), reason: account && account.platformAdministrator && administrative(r) ? tx('pu.3','Plattformadministratoren verwalten keine Mandanten') : '' }));
 }
 /** What a bulk assignment would do for each selected user. */
 function bulkPlan(selected, choice, role, rows) {
  const out = { assign: [], change: [], skipped: [] };
  const grouped = byUser(rows);
  for (const a of selected || []) {
   const own = (grouped.get(a.id) || []).find(r => r.tenantId === choice.id);
   if (a.platformAdministrator && administrative(role)) { out.skipped.push({ username: a.username, reason: tx('pu.3','Plattformadministratoren verwalten keine Mandanten') }); continue; }
   if (!a.active || a.invitationPending) { out.skipped.push({ username: a.username, reason: tx('pu.4','Konto nicht aktiv') }); continue; }
   if (!own) out.assign.push(a);
   else if (!own.manageable) out.skipped.push({ username: a.username, reason: tx('pu.5','Heimat-Identität, nur im eigenen Mandanten änderbar') });
   else if (own.roleId !== role.id) out.change.push({ account: a, row: own });
   else out.skipped.push({ username: a.username, reason: tx('pu.6','hat diese Rolle bereits') });
  }
  return out;
 }
 /** Users × tenants; every cell is the membership row or null. */
 function matrix(choices, accounts, rows) {
  const columns = (choices || []).filter(c => !isHome(c)).sort((a, b) => lower(a.name).localeCompare(lower(b.name)));
  const grouped = byUser(rows);
  return { columns, rows: [...(accounts || [])].sort((a, b) => lower(a.username).localeCompare(lower(b.username))).map(a => ({ account: a, cells: columns.map(c => (grouped.get(a.id) || []).find(r => r.tenantId === c.id) || null) })) };
 }
 globalThis.LcPlatformUsers = { byUser, stateOf, userList, addableTenants, roleOptions, bulkPlan, matrix, DEFAULT_TENANT };
 if (typeof document === 'undefined' || !document.body) return;

 const node = (tag, text, className) => { const n = document.createElement(tag); if (text !== undefined) n.textContent = text; if (className) n.className = className; return n; };
 const option = (value, label, disabled) => { const o = node('option', label); o.value = value; if (disabled) o.disabled = true; return o; };

 async function setup(section) {
  if (!section || typeof json !== 'function') return;
  const root = node('section', undefined, 'pu-root'); root.id = 'platformUsersSection';
  const message = node('p', ''); message.setAttribute('role', 'status');
  const tabs = node('div', undefined, 'pu-tabs'), body = node('div');
  root.append(node('h3', tx('pu.7','Benutzer & Mandanten')), node('p', tx('pu.8','Benutzer sind globale Konten mit Rollen in mehreren Mandanten. Hier weist du sie per Liste zu; ein Plattformadministrator gehört keinem Mandanten.')), tabs, message, body);
  section.append(root);
  let accounts = [], rows = [], choices = [], tab = 'users', selectedUser = null, tenantView = null;
  const selected = new Set();

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
  const tenantSelect = list => { const s = document.createElement('select'); list.forEach(c => s.append(option(c.id, c.name + ' · ' + c.code))); return s; };

  function renderTabs() {
   tabs.replaceChildren();
   [['users', tx('pu.12','Benutzer')], ['tenants', tx('pu.13','Mandanten')], ['matrix', tx('pu.14','Matrix')]].forEach(([id, label]) => {
    const b = node('button', label, id === tab ? 'pu-tab active' : 'pu-tab'); b.type = 'button'; b.onclick = () => { tab = id; render(); }; tabs.append(b);
   });
  }

  /* ---- users: list with bulk bar and detail ---- */
  function renderUsers() {
   const filterBar = node('div', undefined, 'pu-filter');
   const text = document.createElement('input'); text.type = 'search'; text.placeholder = tx('pu.15','Suchen: Name, E-Mail, Mandant, Rolle'); text.value = renderUsers.text || '';
   const tenant = document.createElement('select'); tenant.append(option('', tx('pu.16','Alle Mandanten'))); choices.filter(c => !isHome(c)).forEach(c => tenant.append(option(c.id, c.name))); tenant.value = renderUsers.tenant || '';
   const state = document.createElement('select'); [['all', tx('pu.17','Alle Konten')], ['active', tx('pu.18','Aktiv')], ['inactive', tx('pu.19','Gesperrt')], ['pending', tx('pu.20','Einladung offen')], ['platform', tx('pu.21','Plattformadministratoren')]].forEach(([v, l]) => state.append(option(v, l))); state.value = renderUsers.state || 'all';
   filterBar.append(text, tenant, state);
   const list = node('div', undefined, 'pu-list'), detail = node('div', undefined, 'pu-detail');
   const bulk = node('div', undefined, 'pu-bulk');
   const draw = () => {
    renderUsers.text = text.value; renderUsers.tenant = tenant.value; renderUsers.state = state.value;
    const shown = userList(accounts, rows, { text: text.value, tenantId: tenant.value, state: state.value });
    list.replaceChildren();
    const grouped = byUser(rows);
    shown.forEach(a => {
     const item = node('div', undefined, 'pu-row' + (selectedUser === a.id ? ' current' : ''));
     const check = document.createElement('input'); check.type = 'checkbox'; check.checked = selected.has(a.id); check.setAttribute('aria-label', a.username + tx('pu.22',' auswählen'));
     check.onchange = () => { if (check.checked) selected.add(a.id); else selected.delete(a.id); drawBulk(); };
     const open = node('button', undefined, 'pu-open'); open.type = 'button';
     const own = grouped.get(a.id) || [];
     open.append(node('b', a.displayName || a.username), node('small', `${a.username} · ${a.platformAdministrator ? 'Plattformadministrator' : own.length + ' Mandant(en)'}${a.active ? '' : ' · gesperrt'}${a.invitationPending ? ' · Einladung offen' : ''}`));
     open.onclick = () => { selectedUser = a.id; draw(); drawDetail(); };
     item.append(check, open); list.append(item);
    });
    if (!shown.length) list.append(node('p', tx('pu.23','Keine Benutzer gefunden.')));
    drawBulk();
   };
   const drawBulk = () => {
    bulk.replaceChildren();
    if (!selected.size) { bulk.append(node('small', tx('pu.24','Mehrere Benutzer ankreuzen, um sie gemeinsam einem Mandanten zuzuweisen.'))); return; }
    const picked = accounts.filter(a => selected.has(a.id));
    const targets = choices.filter(c => !isHome(c));
    if (!targets.length) { bulk.append(node('small', tx('pu.25','Keine Mandanten verfügbar.'))); return; }
    const t = tenantSelect(targets); let r = roleSelect(picked[0], targets[0]);
    const swap = () => { const choice = targets.find(c => c.id === t.value); const next = document.createElement('select'); (choice.roles || []).forEach(role => next.append(option(role.id, role.name))); r.replaceWith(next); r = next; };
    t.onchange = swap; swap();
    const go = node('button', `${picked.length} Benutzer zuweisen`); go.type = 'button';
    const off = node('button', tx('pu.26','Zugang im Mandanten aussetzen'), 'secondary'); off.type = 'button';
    go.onclick = () => run(async () => {
     const choice = targets.find(c => c.id === t.value), role = (choice.roles || []).find(x => x.id === r.value);
     const plan = bulkPlan(picked, choice, role, rows);
     if (!await confirmAction(tx('put.fd0368','{p0} neu zuweisen, {p1} Rolle ändern, {p2} überspringen ({p3}, {p4})?',{p0:plan.assign.length,p1:plan.change.length,p2:plan.skipped.length,p3:choice.name,p4:role.name}) + (plan.skipped.length ? tx('pu.27','\n\nÜbersprungen: ') + plan.skipped.map(s => s.username + ' (' + s.reason + ')').join('; ') : ''))) return;
     const failures = [];
     for (const a of plan.assign) try { await assign(choice.id, a.username, role.id); } catch (e) { failures.push(a.username + ': ' + e.message); }
     for (const c of plan.change) try { await changeRole(choice.id, c.account.id, role.id); } catch (e) { failures.push(c.account.username + ': ' + e.message); }
     selected.clear();
     if (failures.length) throw new Error(tx('pu.28','Teilweise fehlgeschlagen: ') + failures.join('; '));
    }, tx('pu.29','Zuweisung abgeschlossen.'));
    off.onclick = () => run(async () => {
     const choice = targets.find(c => c.id === t.value);
     const todo = picked.map(a => ({ a, row: (byUser(rows).get(a.id) || []).find(x => x.tenantId === choice.id) })).filter(x => x.row && x.row.manageable && !x.row.suspended);
     if (!todo.length) throw new Error(tx('pu.30','Keiner der Ausgewählten hat in diesem Mandanten einen aktiven, änderbaren Zugang.'));
     if (!await confirmAction(tx('put.4c5960','Zugang von {p0} Benutzer(n) in {p1} aussetzen?',{p0:todo.length,p1:choice.name}))) return;
     const failures = [];
     for (const x of todo) try { await setSuspended(choice.id, x.a.id, true); } catch (e) { failures.push(x.a.username + ': ' + e.message); }
     selected.clear(); if (failures.length) throw new Error(tx('pu.28','Teilweise fehlgeschlagen: ') + failures.join('; '));
    }, tx('pu.31','Zugänge ausgesetzt.'));
    const clear = node('button', tx('pu.32','Auswahl aufheben'), 'secondary'); clear.type = 'button'; clear.onclick = () => { selected.clear(); draw(); };
    bulk.append(node('b', tx('put.b4503f','{p0} ausgewählt: ',{p0:picked.length})), t, r, go, off, clear);
   };
   const drawDetail = () => {
    detail.replaceChildren();
    const a = accounts.find(x => x.id === selectedUser);
    if (!a) { detail.append(node('p', tx('pu.33','Benutzer links auswählen.'))); return; }
    const own = (byUser(rows).get(a.id) || []).sort((x, y) => lower(x.tenantName).localeCompare(lower(y.tenantName)));
    detail.append(node('h4', a.displayName || a.username), node('small', `${a.username} · ${a.email || 'keine E-Mail'}${a.platformAdministrator ? ' · Plattformadministrator (gehört keinem Mandanten)' : ''}`));
    const table = node('table', undefined, 'pu-table'); const head = node('tr'); ['Mandant', tx('pu.34','Rolle'), tx('pu.35','Zugang'), ''].forEach(h => head.append(node('th', h))); table.append(head);
    own.forEach(r => {
     const choice = choices.find(c => c.id === r.tenantId), tr = node('tr');
     tr.append(node('td', r.tenantName));
     const roleCell = node('td'), actionCell = node('td');
     if (r.manageable && choice) {
      const s = roleSelect(a, choice, r.roleId); const save = node('button', tx('pu.36','Rolle speichern'), 'secondary'); save.type = 'button'; save.onclick = () => run(() => changeRole(r.tenantId, r.userId, s.value));
      roleCell.append(s, ' ', save);
      const sus = node('button', r.suspended ? tx('pu.37','Zugang wiederherstellen') : tx('pu.38','Zugang aussetzen'), r.suspended ? 'secondary' : 'danger'); sus.type = 'button';
      sus.onclick = async () => { if (await confirmAction(`${r.suspended ? tx('pu.37','Zugang wiederherstellen') : tx('pu.38','Zugang aussetzen')}: ${a.username} in ${r.tenantName}?`)) run(() => setSuspended(r.tenantId, r.userId, !r.suspended)); };
      actionCell.append(sus);
     } else roleCell.append(r.roleName + (r.manageable ? '' : tx('pu.46',' (Heimat-Identität)')));
     tr.append(roleCell, node('td', stateOf(r)), actionCell); table.append(tr);
    });
    if (!own.length) { const tr = node('tr'); const td = node('td', a.platformAdministrator ? 'Keine Mandanten – so soll es für Plattformadministratoren sein.' : 'In keinem Mandanten.'); td.colSpan = 4; tr.append(td); table.append(tr); }
    detail.append(table);
    const free = addableTenants(a, choices, rows);
    if (free.length && a.active && !a.invitationPending) {
     const t = tenantSelect(free); let r = roleSelect(a, free[0]);
     const swap = () => { const next = roleSelect(a, free.find(c => c.id === t.value)); r.replaceWith(next); r = next; }; t.onchange = swap;
     const add = node('button', tx('pu.39','Zu Mandant hinzufügen')); add.type = 'button'; add.onclick = () => run(() => assign(t.value, a.username, r.value), tx('pu.40','Zugewiesen.'));
     const row = node('p', undefined, 'pu-add'); row.append(node('b', tx('pu.41','Mandant hinzufügen: ')), t, r, add); detail.append(row);
    }
   };
   [text, tenant, state].forEach(el => { el.oninput = draw; el.onchange = draw; });
   const left = node('div', undefined, 'pu-left'); left.append(filterBar, list);
   const layout = node('div', undefined, 'pu-layout'); layout.append(left, detail);
   body.replaceChildren(bulk, layout); draw(); drawDetail();
  }

  /* ---- tenants: member list and multi-select to add ---- */
  function renderTenants() {
   const targets = choices.filter(c => !isHome(c));
   if (!targets.length) { body.replaceChildren(node('p', tx('pu.42','Es gibt noch keine Mandanten.'))); return; }
   if (!tenantView || !targets.some(c => c.id === tenantView)) tenantView = targets[0].id;
   const pick = tenantSelect(targets); pick.value = tenantView; pick.onchange = () => { tenantView = pick.value; renderTenants(); };
   const choice = targets.find(c => c.id === tenantView);
   const members = rows.filter(r => r.tenantId === tenantView).sort((a, b) => lower(a.username).localeCompare(lower(b.username)));
   const table = node('table', undefined, 'pu-table'); const head = node('tr'); [tx('pu.12','Benutzer'), tx('pu.34','Rolle'), tx('pu.35','Zugang'), ''].forEach(h => head.append(node('th', h))); table.append(head);
   const admins = members.filter(r => r.manageable !== false && /administrator/i.test(r.roleName) && !r.suspended && r.membershipActive && r.accountActive).length;
   members.forEach(r => {
    const a = accounts.find(x => x.id === r.userId) || { id: r.userId, username: r.username };
    const tr = node('tr'); tr.append(node('td', `${r.displayName || r.username} (${r.username})`));
    const roleCell = node('td'), actionCell = node('td');
    if (r.manageable) {
     const s = roleSelect(a, choice, r.roleId); const save = node('button', tx('pu.43','Speichern'), 'secondary'); save.type = 'button'; save.onclick = () => run(() => changeRole(r.tenantId, r.userId, s.value)); roleCell.append(s, ' ', save);
     const sus = node('button', r.suspended ? tx('pu.44','Wiederherstellen') : tx('pu.45','Aussetzen'), r.suspended ? 'secondary' : 'danger'); sus.type = 'button';
     sus.onclick = async () => { if (await confirmAction(`${r.suspended ? tx('pu.37','Zugang wiederherstellen') : tx('pu.38','Zugang aussetzen')}: ${r.username}?`)) run(() => setSuspended(r.tenantId, r.userId, !r.suspended)); }; actionCell.append(sus);
    } else roleCell.append(r.roleName + tx('pu.46',' (Heimat-Identität)'));
    tr.append(roleCell, node('td', stateOf(r)), actionCell); table.append(tr);
   });
   if (!members.length) { const tr = node('tr'); const td = node('td', tx('pu.47','Dieser Mandant hat noch keine Mitglieder.')); td.colSpan = 4; tr.append(td); table.append(tr); }
   const warn = admins === 0 ? node('p', tx('pu.48','Dieser Mandant hat keinen aktiven Mandantenadmin – bitte einen zuweisen oder einladen.'), 'placement-warning') : node('small', `${admins} aktive(r) Mandantenadmin(s)`);
   // add: checklist of users not yet in the tenant
   const have = new Set(members.map(r => r.userId));
   const candidates = accounts.filter(a => !have.has(a.id) && a.active && !a.invitationPending).sort((a, b) => lower(a.username).localeCompare(lower(b.username)));
   const add = node('div', undefined, 'pu-adder'); add.append(node('h4', tx('pu.49','Benutzer hinzufügen')));
   const search = document.createElement('input'); search.type = 'search'; search.placeholder = tx('pu.50','Benutzer suchen'); const list = node('div', undefined, 'pu-checklist');
   const roleSel = document.createElement('select'); (choice.roles || []).forEach(r => roleSel.append(option(r.id, r.name)));
   const checked = new Set();
   const drawList = () => { list.replaceChildren(); candidates.filter(a => !search.value || lower(a.username + ' ' + a.displayName + ' ' + a.email).includes(lower(search.value))).forEach(a => {
     const l = node('label', ' ' + (a.displayName || a.username) + ' (' + a.username + ')' + (a.platformAdministrator ? ' – Plattformadministrator' : '')); const c = document.createElement('input'); c.type = 'checkbox'; c.checked = checked.has(a.id); c.onchange = () => { c.checked ? checked.add(a.id) : checked.delete(a.id); }; l.prepend(c); list.append(l); });
    if (!list.children.length) list.append(node('small', tx('pu.51','Keine weiteren Benutzer verfügbar.'))); };
   search.oninput = drawList; drawList();
   const go = node('button', tx('pu.52','Ausgewählte zuweisen')); go.type = 'button';
   go.onclick = () => run(async () => {
    const role = (choice.roles || []).find(r => r.id === roleSel.value), picked = candidates.filter(a => checked.has(a.id));
    if (!picked.length) throw new Error(tx('pu.53','Bitte mindestens einen Benutzer auswählen.'));
    const plan = bulkPlan(picked, choice, role, rows);
    if (!plan.assign.length) throw new Error(tx('pu.54','Nichts zuzuweisen. ') + plan.skipped.map(s => s.username + ': ' + s.reason).join('; '));
    if (!await confirmAction(`${plan.assign.length} Benutzer als „${role.name}“ in ${choice.name} zuweisen?` + (plan.skipped.length ? tx('pu.27','\n\nÜbersprungen: ') + plan.skipped.map(s => s.username + ' (' + s.reason + ')').join('; ') : ''))) return;
    const failures = []; for (const a of plan.assign) try { await assign(choice.id, a.username, role.id); } catch (e) { failures.push(a.username + ': ' + e.message); }
    if (failures.length) throw new Error(tx('pu.28','Teilweise fehlgeschlagen: ') + failures.join('; '));
   }, tx('pu.40','Zugewiesen.'));
   add.append(search, list, node('p'), roleSel, ' ', go);
   body.replaceChildren(pick, warn, table, add);
  }

  /* ---- matrix ---- */
  function renderMatrix() {
   const m = matrix(choices, accounts, rows);
   if (!m.columns.length) { body.replaceChildren(node('p', tx('pu.42','Es gibt noch keine Mandanten.'))); return; }
   const table = node('table', undefined, 'pu-table pu-matrix'); const head = node('tr'); head.append(node('th', tx('pu.12','Benutzer'))); m.columns.forEach(c => head.append(node('th', c.name))); table.append(head);
   m.rows.forEach(r => {
    const tr = node('tr'); const first = node('td'); const open = node('button', r.account.username, 'pu-open'); open.type = 'button'; open.onclick = () => { selectedUser = r.account.id; tab = 'users'; render(); }; first.append(open); tr.append(first);
    r.cells.forEach(cell => { const td = node('td', cell ? cell.roleName : '–', cell ? (cell.suspended || !cell.membershipActive ? 'pu-cell off' : 'pu-cell') : 'pu-cell none'); if (cell && (cell.suspended || !cell.membershipActive)) td.title = tx('pu.1','Zugang ausgesetzt'); tr.append(td); });
    table.append(tr);
   });
   body.replaceChildren(node('small', tx('pu.55','Ein Klick auf einen Benutzernamen öffnet seine Zuweisungen. Ausgegraute Zellen: Zugang ausgesetzt; „–“: kein Zugang.')), table);
  }

  function render() { renderTabs(); if (tab === 'users') renderUsers(); else if (tab === 'tenants') renderTenants(); else renderMatrix(); }
  try { await load(); } catch (e) { message.textContent = e.message; }
 }
 globalThis.LcPlatformUsers.setup = setup;
})();
