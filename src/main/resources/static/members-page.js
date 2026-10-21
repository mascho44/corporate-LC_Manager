/* "Benutzer & Rechte": one page for tenant administrators instead of a menu page plus a long dialog.
   Tabs: Mitglieder (table with role, status, search and filters), Einladungen (invite, assign an existing account), Rollen & Rechte (matrix of roles and permissions). */
(() => {
 const LABELS = () => ({ LC_EDIT: tx('mp.0','LC-Akten bearbeiten'), LC_DELETE: tx('mp.1','LC-Akten löschen'), SWIFT_IMPORT: tx('mp.2','SWIFT-Nachrichten importieren'), DOCUMENT_UPLOAD: tx('mp.3','Dokumente hochladen'), DOCUMENT_DELETE: tx('mp.4','Dokumente löschen'), DOCUMENT_GENERATE: tx('mp.5','Dokumente erstellen (Maker)'),
  DOCUMENT_REVIEW: tx('mp.6','Dokumente prüfen (Checker)'), DOCUMENT_APPROVE: tx('mp.7','Dokumente freigeben (Approver)'), EMAIL_SEND: tx('mp.8','E-Mails aus Akten senden'), TRAINING_MANAGE: tx('mp.9','Training verwalten'), AUDIT_VIEW: tx('mp.10','Audit-Protokoll ansehen'), SETTINGS_MANAGE: tx('mp.11','Firmen, Vorlagen und Einstellungen verwalten'), USER_MANAGE: tx('mp.12','Benutzer verwalten') });
 const GROUPS = () => [[tx('mp.13','Akkreditive'), ['LC_EDIT', 'LC_DELETE', 'SWIFT_IMPORT']], [tx('mp.14','Dokumente'), ['DOCUMENT_UPLOAD', 'DOCUMENT_DELETE', 'DOCUMENT_GENERATE', 'DOCUMENT_REVIEW', 'DOCUMENT_APPROVE', 'EMAIL_SEND']], [tx('mp.15','Training und Nachweis'), ['TRAINING_MANAGE', 'AUDIT_VIEW']], [tx('mp.16','Verwaltung'), ['SETTINGS_MANAGE', 'USER_MANAGE']]];

 /** Members matching the search text (name, role, status words) and the role and status filters; status is 'active' | 'suspended' | ''. */
 function filterMembers(members, { text = '', roleId = '', status = '' } = {}) {
  const needle = String(text).trim().toLocaleLowerCase('de');
  return (members || []).filter(m => {
   const state = m.suspended ? 'suspended' : 'active';
   if (roleId && m.roleId !== roleId) return false;
   if (status && state !== status) return false;
   return !needle || [m.username, m.roleName, m.suspended ? 'gesperrt' : 'aktiv'].join(' ').toLocaleLowerCase('de').includes(needle);
  });
 }
 /** Permission matrix rows grouped by area: [{ group, rows: [{ code, label, roles: [bool per role] }] }]; permissions the roles have but the groups do not know go to "Weitere". */
 function permissionMatrix(roles) {
  const has = (role, code) => Array.isArray(role.permissions) && role.permissions.includes(code);
  const known = new Set(GROUPS().flatMap(([, codes]) => codes));
  const extra = [...new Set((roles || []).flatMap(r => r.permissions || []).filter(code => !known.has(code)))].sort();
  const groups = GROUPS().concat(extra.length ? [[tx('mp.17','Weitere'), extra]] : []);
  return groups.map(([group, codes]) => ({ group, rows: codes.map(code => ({ code, label: LABELS()[code] || code, roles: (roles || []).map(role => has(role, code)) })) }));
 }
 /** How many members hold each role: Map roleId -> count. */
 function memberCounts(members) { const counts = new Map(); (members || []).forEach(m => counts.set(m.roleId, (counts.get(m.roleId) || 0) + 1)); return counts; }

 /** What a bulk action does to the selected members and what it leaves alone (and why): action 'role' (needs roleId), 'suspend' or 'activate'; `me` is the own user name. */
 function bulkPlan(selected, action, { roleId = '', me = '' } = {}) {
  const apply = [], skipped = [], mine = String(me).toLowerCase();
  (selected || []).forEach(m => {
   const own = String(m.username).toLowerCase() === mine;
   let reason = null;
   if (action === 'role') reason = own ? tx('mp.18','eigene Rolle') : m.roleId === roleId ? tx('mp.19','hat die Rolle schon') : null;
   else if (action === 'suspend') reason = own ? tx('mp.20','eigener Zugang') : m.suspended ? tx('mp.21','schon gesperrt') : m.identityActive === false ? tx('mp.22','Konto inaktiv') : null;
   else if (action === 'activate') reason = !m.suspended ? tx('mp.23','nicht gesperrt') : m.identityActive === false ? tx('mp.22','Konto inaktiv') : null;
   (reason ? skipped.push({ username: m.username, reason }) : apply.push(m));
  });
  return { apply, skipped };
 }
 globalThis.LcMembersPage = { filterMembers, permissionMatrix, memberCounts, bulkPlan, LABELS };
 if (typeof document === 'undefined' || !document.body) return;

 const node = (tag, text, css) => { const n = document.createElement(tag); if (text !== undefined) n.textContent = text; if (css) n.className = css; return n; };
 let section = null, overview = null, active = 'members';

 function build() {
  section = node('section', undefined, 'panel hidden members-page'); section.id = 'membersSection';
  const head = node('div', undefined, 'panelhead'), title = node('div');
  title.append(node('h2', tx('mp.24','Benutzer & Rechte')), node('p', '', 'members-facts'));
  const refresh = node('button', tx('mp.25','Aktualisieren'), 'secondary'); refresh.type = 'button'; refresh.onclick = () => load();
  head.append(title, refresh);
  const flash = node('p', '', 'members-flash'); flash.setAttribute('role', 'status');
  const tabs = node('div', undefined, 'members-tabs'); tabs.setAttribute('role', 'tablist');
  [['members', tx('mp.26','Mitglieder')], ['invitations', tx('mp.27','Einladungen')], ['roles', tx('mp.28','Rollen & Rechte')]].forEach(([key, text]) => {
   const tab = node('button', text, 'members-tab'); tab.type = 'button'; tab.dataset.tab = key; tab.setAttribute('role', 'tab');
   tab.onclick = () => { active = key; setFlash(''); paint(); }; tabs.append(tab);
  });
  const body = node('div', undefined, 'members-body'); body.setAttribute('role', 'tabpanel');
  section.append(head, flash, tabs, body);
  (document.querySelector('main') || document.body).append(section);
 }

 let generation = 0;
 async function load() {
  const mine = ++generation, body = section.querySelector('.members-body'); body.textContent = tx('mp.29','Mitglieder werden geladen …');
  try { const fresh = await json('/api/users/memberships'); if (mine !== generation) return; if (!Array.isArray(fresh.memberships)) throw new Error(tx('mp.30','Ungültige Antwort')); overview = fresh; paint(); }
  catch (error) { if (mine === generation) body.textContent = tx('mp.31','Mitglieder konnten nicht geladen werden: ') + error.message; }
 }
 globalThis.LcMembersPage.reload = () => (section ? load() : undefined);

 globalThis.LcMembersPage.showTab = key => { active = key; if (section && overview) paint(); };
 const setFlash = text => { const f = section?.querySelector('.members-flash'); if (f) f.textContent = text || ''; };
 function paint() {
  if (!overview) return;
  const s = overview.settings;
  section.querySelector('.members-facts').textContent = [overview.tenantName, s?.code && tx('mp.32','Kennung ') + s.code, s && (s.bankEnabled ? tx('mp.33','Bank') : '') + (s.bankEnabled && s.corporateEnabled ? ' + ' : '') + (s.corporateEnabled ? 'Corporate' : '')].filter(Boolean).join(' · ');
  section.querySelectorAll('.members-tab').forEach(tab => { const on = tab.dataset.tab === active; tab.classList.toggle('active', on); tab.setAttribute('aria-selected', String(on)); });
  const body = section.querySelector('.members-body'); body.replaceChildren();
  ({ members: paintMembers, invitations: paintInvitations, roles: paintRoles })[active](body);
 }

 function paintMembers(body) {
  const roles = overview.roleChoices || [], members = overview.memberships;
  const route = overview.tenantId && overview.tenantId !== '00000000-0000-0000-0000-000000000001' ? '/api/users/memberships/shared/' : '/api/users/memberships/';
  const bar = node('div', undefined, 'members-toolbar');
  const search = node('input'); search.type = 'search'; search.placeholder = tx('mp.34','Name, Rolle oder Status suchen'); search.setAttribute('aria-label', tx('mp.35','Mitglieder suchen')); search.maxLength = 100;
  const roleFilter = node('select'); roleFilter.setAttribute('aria-label', tx('mp.36','Nach Rolle filtern')); roleFilter.append(Object.assign(node('option', tx('mp.37','Alle Rollen')), { value: '' }));
  roles.forEach(r => roleFilter.append(Object.assign(node('option', r.name), { value: r.id })));
  const statusFilter = node('select'); statusFilter.setAttribute('aria-label', tx('mp.38','Nach Status filtern'));
  [['', tx('mp.39','Alle Status')], ['active', tx('mp.40','Aktiv')], ['suspended', tx('mp.41','Gesperrt')]].forEach(([v, t]) => statusFilter.append(Object.assign(node('option', t), { value: v })));
  const count = node('small', '', 'members-count'); count.setAttribute('role', 'status');
  bar.append(search, roleFilter, statusFilter, count);
  const wrap = node('div', undefined, 'members-table-wrap'), table = node('table', undefined, 'members-table');
  const selected = new Set(); // user ids
  const head = node('thead'), hr = node('tr');
  const allBox = node('input'); allBox.type = 'checkbox'; allBox.setAttribute('aria-label', tx('mp.42','Alle angezeigten Mitglieder auswählen'));
  const boxTh = node('th'); boxTh.className = 'members-select'; boxTh.append(allBox); hr.append(boxTh);
  [tx('mp.43','Benutzer'), tx('mp.44','Rolle'), tx('mp.45','Status'), ''].forEach(t => hr.append(node('th', t))); head.append(hr);
  const tbody = node('tbody'); table.append(head, tbody); wrap.append(table);
  const bulkBar = node('div', undefined, 'members-bulk'); bulkBar.hidden = true;
  body.append(bar, bulkBar, wrap);
  const canEditRole = overview.roleEditingEnabled && roles.length && can('USER_MANAGE'), canAccess = overview.accessEditingEnabled && can('USER_MANAGE');
  const me = String(typeof currentUser !== 'undefined' ? currentUser?.username || '' : '').toLowerCase();
  const draw = () => {
   const list = filterMembers(members, { text: search.value, roleId: roleFilter.value, status: statusFilter.value });
   count.textContent = tx('mpt.8ca8ee','{p0} von {p1} Mitgliedern',{p0:list.length,p1:members.length}); tbody.replaceChildren();
   allBox.checked = list.length > 0 && list.every(m => selected.has(m.userId)); refreshBulk();
   if (!list.length) { const r = node('tr'), c = node('td', tx('mp.46','Keine Mitglieder gefunden.')); c.colSpan = 5; r.append(c); tbody.append(r); return; }
   list.forEach(m => {
    const own = String(m.username).toLowerCase() === me, row = node('tr');
    const pick = node('td'); pick.className = 'members-select'; const box = node('input'); box.type = 'checkbox'; box.checked = selected.has(m.userId); box.setAttribute('aria-label', m.username + ' auswählen');
    box.onchange = () => { box.checked ? selected.add(m.userId) : selected.delete(m.userId); allBox.checked = list.every(x => selected.has(x.userId)); refreshBulk(); }; pick.append(box);
    const who = node('td'); who.append(node('b', m.username)); if (own) who.append(node('small', tx('mp.47',' (Sie)')));
    const roleCell = node('td'), message = node('small', '', 'members-saved'); message.setAttribute('role', 'status');
    if (canEditRole) {
     const select = node('select'); select.setAttribute('aria-label', tx('mp.48','Rolle von ') + m.username); roles.forEach(r => select.append(Object.assign(node('option', r.name), { value: r.id }))); select.value = m.roleId;
     select.disabled = own; if (own) select.title = tx('mp.49','Die eigene Rolle kann hier nicht geändert werden.');
     select.onchange = async () => {
      const previous = m.roleId; select.disabled = true; message.textContent = tx('mp.50','Wird gespeichert …'); message.className = 'members-saved';
      try { await json(route + encodeURIComponent(m.userId) + '/role', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ roleId: select.value }) }); m.roleId = select.value; m.roleName = roles.find(r => r.id === select.value)?.name || m.roleName; message.textContent = tx('mp.51','✓ gespeichert'); message.className = 'members-saved ok'; }
      catch (error) { select.value = previous; message.textContent = error.message || tx('mp.52','Rolle konnte nicht gespeichert werden.'); message.className = 'members-saved error'; }
      finally { select.disabled = own; }
     };
     roleCell.append(select, message);
    } else roleCell.append(node('span', m.roleName));
    const state = node('td'); state.append(node('span', m.suspended ? tx('mp.41','Gesperrt') : m.active ? tx('mp.40','Aktiv') : 'Inaktiv', 'import-status ' + (m.active && !m.suspended ? 'success' : 'rejected')));
    const actions = node('td', undefined, 'members-actions');
    if (canAccess) {
     const disabled = (!m.suspended && own) || m.identityActive === false;
     const toggle = node('button', m.suspended ? tx('mp.53','Aktivieren') : tx('mp.54','Sperren'), m.suspended ? 'secondary' : 'danger'); toggle.type = 'button'; toggle.disabled = disabled;
     toggle.title = m.identityActive === false ? tx('mp.55','Das globale Konto ist inaktiv.') : own && !m.suspended ? tx('mp.56','Den eigenen Zugang kann man nicht sperren.') : tx('mp.57','Betrifft nur den Zugang zu diesem Mandanten.');
     toggle.onclick = async () => {
      if (!m.suspended && !await confirmAction(tx('mpt.ca0a2a','Zugang zu diesem Mandanten für {p0} sperren?',{p0:m.username}))) return;
      toggle.disabled = true;
      try { await json(route + encodeURIComponent(m.userId) + '/access', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ suspended: !m.suspended }) }); await load(); }
      catch (error) { message.textContent = error.message || tx('mp.58','Zugang konnte nicht geändert werden.'); message.className = 'members-saved error'; toggle.disabled = false; }
     };
     actions.append(toggle);
    }
    row.append(pick, who, roleCell, state, actions); tbody.append(row);
   });
  };
  allBox.onchange = () => { filterMembers(members, { text: search.value, roleId: roleFilter.value, status: statusFilter.value }).forEach(m => allBox.checked ? selected.add(m.userId) : selected.delete(m.userId)); draw(); };
  function refreshBulk() {
   bulkBar.replaceChildren(); bulkBar.hidden = !selected.size; if (!selected.size) return;
   const chosen = members.filter(m => selected.has(m.userId));
   bulkBar.append(node('b', tx('mpt.18e4e5','{p0} ausgewählt',{p0:chosen.length})));
   const run = async (action, roleId) => {
    const plan = bulkPlan(chosen, action, { roleId, me });
    if (!plan.apply.length) { setFlash(tx('mp.59','Nichts zu tun: ') + plan.skipped.map(s => `${s.username} (${s.reason})`).join(', ') + '.'); return; }
    const what = action === 'role' ? tx('mp.whatRole','die Rolle „{role}“ vergeben',{role:roles.find(r => r.id === roleId)?.name}) : action === 'suspend' ? tx('mp.60','den Zugang sperren') : tx('mp.61','den Zugang wieder aktivieren');
    if (!await confirmAction(tx('mp.confirmBulk','{n} Mitglied(er) {what}?',{n:plan.apply.length,what})+(plan.skipped.length?' '+tx('mp.skippedList','Übersprungen: {list}.',{list:plan.skipped.map(s => `${s.username} (${s.reason})`).join(', ')}):''))) return;
    bulkBar.querySelectorAll('button,select').forEach(c => c.disabled = true); let done = 0; const failed = [];
    for (const [i, m] of plan.apply.entries()) {
     setFlash(tx('mpt.fc4082','{p0} von {p1} …',{p0:i + 1,p1:plan.apply.length}));
     try { await json(route + encodeURIComponent(m.userId) + (action === 'role' ? '/role' : '/access'), { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(action === 'role' ? { roleId } : { suspended: action === 'suspend' }) }); done++; }
     catch (error) { failed.push(`${m.username}: ${error.message}`); }
    }
    await load();
    setFlash(tx('mp.doneOf','{done} von {total} geändert',{done,total:plan.apply.length})+(plan.skipped.length?tx('mp.doneSkipped',', {n} übersprungen ({list})',{n:plan.skipped.length,list:plan.skipped.map(s => `${s.username}: ${s.reason}`).join(', ')}):'')+(failed.length?tx('mp.doneFailed','. Fehler: {list}',{list:failed.join('; ')}):'')+'.');
   };
   if (canEditRole) {
    const choose = node('select'); choose.setAttribute('aria-label', tx('mp.62','Rolle für die Auswahl')); roles.forEach(r => choose.append(Object.assign(node('option', r.name), { value: r.id })));
    const setRole = node('button', tx('mp.63','Rolle setzen'), 'secondary'); setRole.type = 'button'; setRole.onclick = () => run('role', choose.value);
    bulkBar.append(choose, setRole);
   }
   if (canAccess) {
    const suspend = node('button', tx('mp.54','Sperren'), 'danger'); suspend.type = 'button'; suspend.onclick = () => run('suspend');
    const activate = node('button', tx('mp.53','Aktivieren'), 'secondary'); activate.type = 'button'; activate.onclick = () => run('activate');
    bulkBar.append(suspend, activate);
   }
   const clear = node('button', tx('mp.64','Auswahl aufheben'), 'ghost'); clear.type = 'button'; clear.onclick = () => { selected.clear(); draw(); }; bulkBar.append(clear);
  }
  [search, roleFilter, statusFilter].forEach(c => c.addEventListener(c === search ? 'input' : 'change', draw)); draw();
 }

 function paintInvitations(body) {
  const holder = node('div', undefined, 'members-invitations'); body.append(holder);
  globalThis.LcTenantInvitations?.render?.(holder);
  const shared = overview.tenantId && overview.tenantId !== '00000000-0000-0000-0000-000000000001';
  if (shared && can('USER_MANAGE') && typeof renderMembershipAssignment === 'function') renderMembershipAssignment(holder, overview.roleChoices || []);
 }

 function paintRoles(body) {
  const roles = overview.roleChoices || [], counts = memberCounts(overview.memberships);
  const top = node('div', undefined, 'members-toolbar');
  top.append(node('p', tx('mp.65','Welche Rolle darf was: ✓ bedeutet, die Rolle hat das Recht. Mitglieder erben alle Rechte ihrer Rolle.'), 'members-note'));
  if (can('USER_MANAGE') && typeof openRolesEditor === 'function') { const edit = node('button', tx('mp.66','Rollen bearbeiten'), 'secondary'); edit.type = 'button'; edit.onclick = async () => { await openRolesEditor(); }; top.append(edit); }
  const wrap = node('div', undefined, 'members-table-wrap'), table = node('table', undefined, 'members-matrix');
  const head = node('thead'), hr = node('tr'); hr.append(node('th', tx('mp.67','Recht')));
  roles.forEach(r => { const th = node('th'); th.append(node('span', r.name), node('small', `${counts.get(r.id) || 0} Mitglied${(counts.get(r.id) || 0) === 1 ? '' : 'er'}`)); hr.append(th); });
  head.append(hr); table.append(head);
  permissionMatrix(roles).forEach(({ group, rows }) => {
   const body2 = node('tbody'), gr = node('tr', undefined, 'members-group'), gc = node('th', group); gc.colSpan = roles.length + 1; gr.append(gc); body2.append(gr);
   rows.forEach(r => { const tr = node('tr'); tr.append(node('th', r.label)); r.roles.forEach((on, i) => { const td = node('td', on ? '✓' : '–', on ? 'yes' : 'no'); td.setAttribute('aria-label', `${roles[i].name}: ${on ? 'ja' : 'nein'}`); tr.append(td); }); body2.append(tr); });
   table.append(body2);
  });
  wrap.append(table); body.append(top, wrap);
 }

 function ready() {
  const nav = document.querySelector('#appNavUsers');
  if (!nav || nav.dataset.membersPage || typeof appNavigate !== 'function' || typeof can !== 'function' || typeof json !== 'function') return false;
  if (!section) build();
  nav.dataset.membersPage = '1';
  return true;
 }
 // The menu entry is wired by the overview pages (and re-wired later); a click caught in the capture phase takes the page instead, whatever handler is attached to the button.
 document.addEventListener('click', event => {
  const nav = event.target.closest?.('#appNavUsers');
  if (!nav || !section || typeof appNavigate !== 'function') return;
  event.stopImmediatePropagation(); event.preventDefault();
  appNavigate('members', nav); load();
 }, true);
 const timer = setInterval(() => { try { if (ready()) clearInterval(timer); } catch (ignored) { /* retried until the app has finished starting */ } }, 400);
})();
