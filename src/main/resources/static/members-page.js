/* "Benutzer & Rechte": one page for tenant administrators instead of a menu page plus a long dialog.
   Tabs: Mitglieder (table with role, status, search and filters), Einladungen (invite, assign an existing account), Rollen & Rechte (matrix of roles and permissions). */
(() => {
 const LABELS = { LC_EDIT: 'LC-Akten bearbeiten', LC_DELETE: 'LC-Akten löschen', SWIFT_IMPORT: 'SWIFT-Nachrichten importieren', DOCUMENT_UPLOAD: 'Dokumente hochladen', DOCUMENT_DELETE: 'Dokumente löschen', DOCUMENT_GENERATE: 'Dokumente erstellen (Maker)',
  DOCUMENT_REVIEW: 'Dokumente prüfen (Checker)', DOCUMENT_APPROVE: 'Dokumente freigeben (Approver)', EMAIL_SEND: 'E-Mails aus Akten senden', TRAINING_MANAGE: 'Training verwalten', AUDIT_VIEW: 'Audit-Protokoll ansehen', SETTINGS_MANAGE: 'Firmen, Vorlagen und Einstellungen verwalten', USER_MANAGE: 'Benutzer verwalten' };
 const GROUPS = [['Akkreditive', ['LC_EDIT', 'LC_DELETE', 'SWIFT_IMPORT']], ['Dokumente', ['DOCUMENT_UPLOAD', 'DOCUMENT_DELETE', 'DOCUMENT_GENERATE', 'DOCUMENT_REVIEW', 'DOCUMENT_APPROVE', 'EMAIL_SEND']], ['Training und Nachweis', ['TRAINING_MANAGE', 'AUDIT_VIEW']], ['Verwaltung', ['SETTINGS_MANAGE', 'USER_MANAGE']]];

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
  const known = new Set(GROUPS.flatMap(([, codes]) => codes));
  const extra = [...new Set((roles || []).flatMap(r => r.permissions || []).filter(code => !known.has(code)))].sort();
  const groups = GROUPS.concat(extra.length ? [['Weitere', extra]] : []);
  return groups.map(([group, codes]) => ({ group, rows: codes.map(code => ({ code, label: LABELS[code] || code, roles: (roles || []).map(role => has(role, code)) })) }));
 }
 /** How many members hold each role: Map roleId -> count. */
 function memberCounts(members) { const counts = new Map(); (members || []).forEach(m => counts.set(m.roleId, (counts.get(m.roleId) || 0) + 1)); return counts; }
 globalThis.LcMembersPage = { filterMembers, permissionMatrix, memberCounts, LABELS };
 if (typeof document === 'undefined' || !document.body) return;

 const node = (tag, text, css) => { const n = document.createElement(tag); if (text !== undefined) n.textContent = text; if (css) n.className = css; return n; };
 let section = null, overview = null, active = 'members';

 function build() {
  section = node('section', undefined, 'panel hidden members-page'); section.id = 'membersSection';
  const head = node('div', undefined, 'panelhead'), title = node('div');
  title.append(node('h2', 'Benutzer & Rechte'), node('p', '', 'members-facts'));
  const refresh = node('button', 'Aktualisieren', 'secondary'); refresh.type = 'button'; refresh.onclick = () => load();
  head.append(title, refresh);
  const tabs = node('div', undefined, 'members-tabs'); tabs.setAttribute('role', 'tablist');
  [['members', 'Mitglieder'], ['invitations', 'Einladungen'], ['roles', 'Rollen & Rechte']].forEach(([key, text]) => {
   const tab = node('button', text, 'members-tab'); tab.type = 'button'; tab.dataset.tab = key; tab.setAttribute('role', 'tab');
   tab.onclick = () => { active = key; paint(); }; tabs.append(tab);
  });
  const body = node('div', undefined, 'members-body'); body.setAttribute('role', 'tabpanel');
  section.append(head, tabs, body);
  (document.querySelector('main') || document.body).append(section);
 }

 async function load() {
  const body = section.querySelector('.members-body'); body.textContent = 'Mitglieder werden geladen …';
  try { overview = await json('/api/users/memberships'); if (!Array.isArray(overview.memberships)) throw new Error('Ungültige Antwort'); paint(); }
  catch (error) { body.textContent = 'Mitglieder konnten nicht geladen werden: ' + error.message; }
 }

 function paint() {
  if (!overview) return;
  const s = overview.settings;
  section.querySelector('.members-facts').textContent = [overview.tenantName, s?.code && 'Kennung ' + s.code, s && (s.bankEnabled ? 'Bank' : '') + (s.bankEnabled && s.corporateEnabled ? ' + ' : '') + (s.corporateEnabled ? 'Corporate' : '')].filter(Boolean).join(' · ');
  section.querySelectorAll('.members-tab').forEach(tab => { const on = tab.dataset.tab === active; tab.classList.toggle('active', on); tab.setAttribute('aria-selected', String(on)); });
  const body = section.querySelector('.members-body'); body.replaceChildren();
  ({ members: paintMembers, invitations: paintInvitations, roles: paintRoles })[active](body);
 }

 function paintMembers(body) {
  const roles = overview.roleChoices || [], members = overview.memberships;
  const route = overview.tenantId && overview.tenantId !== '00000000-0000-0000-0000-000000000001' ? '/api/users/memberships/shared/' : '/api/users/memberships/';
  const bar = node('div', undefined, 'members-toolbar');
  const search = node('input'); search.type = 'search'; search.placeholder = 'Name, Rolle oder Status suchen'; search.setAttribute('aria-label', 'Mitglieder suchen'); search.maxLength = 100;
  const roleFilter = node('select'); roleFilter.setAttribute('aria-label', 'Nach Rolle filtern'); roleFilter.append(Object.assign(node('option', 'Alle Rollen'), { value: '' }));
  roles.forEach(r => roleFilter.append(Object.assign(node('option', r.name), { value: r.id })));
  const statusFilter = node('select'); statusFilter.setAttribute('aria-label', 'Nach Status filtern');
  [['', 'Alle Status'], ['active', 'Aktiv'], ['suspended', 'Gesperrt']].forEach(([v, t]) => statusFilter.append(Object.assign(node('option', t), { value: v })));
  const count = node('small', '', 'members-count'); count.setAttribute('role', 'status');
  bar.append(search, roleFilter, statusFilter, count);
  const wrap = node('div', undefined, 'members-table-wrap'), table = node('table', undefined, 'members-table');
  const head = node('thead'), hr = node('tr'); ['Benutzer', 'Rolle', 'Status', ''].forEach(t => hr.append(node('th', t))); head.append(hr);
  const tbody = node('tbody'); table.append(head, tbody); wrap.append(table); body.append(bar, wrap);
  const canEditRole = overview.roleEditingEnabled && roles.length && can('USER_MANAGE'), canAccess = overview.accessEditingEnabled && can('USER_MANAGE');
  const me = String(typeof currentUser !== 'undefined' ? currentUser?.username || '' : '').toLowerCase();
  const draw = () => {
   const list = filterMembers(members, { text: search.value, roleId: roleFilter.value, status: statusFilter.value });
   count.textContent = `${list.length} von ${members.length} Mitgliedern`; tbody.replaceChildren();
   if (!list.length) { const r = node('tr'), c = node('td', 'Keine Mitglieder gefunden.'); c.colSpan = 4; r.append(c); tbody.append(r); return; }
   list.forEach(m => {
    const own = String(m.username).toLowerCase() === me, row = node('tr');
    const who = node('td'); who.append(node('b', m.username)); if (own) who.append(node('small', ' (Sie)'));
    const roleCell = node('td'), message = node('small', '', 'members-saved'); message.setAttribute('role', 'status');
    if (canEditRole) {
     const select = node('select'); select.setAttribute('aria-label', 'Rolle von ' + m.username); roles.forEach(r => select.append(Object.assign(node('option', r.name), { value: r.id }))); select.value = m.roleId;
     select.disabled = own; if (own) select.title = 'Die eigene Rolle kann hier nicht geändert werden.';
     select.onchange = async () => {
      const previous = m.roleId; select.disabled = true; message.textContent = 'Wird gespeichert …'; message.className = 'members-saved';
      try { await json(route + encodeURIComponent(m.userId) + '/role', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ roleId: select.value }) }); m.roleId = select.value; m.roleName = roles.find(r => r.id === select.value)?.name || m.roleName; message.textContent = '✓ gespeichert'; message.className = 'members-saved ok'; }
      catch (error) { select.value = previous; message.textContent = error.message || 'Rolle konnte nicht gespeichert werden.'; message.className = 'members-saved error'; }
      finally { select.disabled = own; }
     };
     roleCell.append(select, message);
    } else roleCell.append(node('span', m.roleName));
    const state = node('td'); state.append(node('span', m.suspended ? 'Gesperrt' : m.active ? 'Aktiv' : 'Inaktiv', 'import-status ' + (m.active && !m.suspended ? 'success' : 'rejected')));
    const actions = node('td', undefined, 'members-actions');
    if (canAccess) {
     const disabled = (!m.suspended && own) || m.identityActive === false;
     const toggle = node('button', m.suspended ? 'Aktivieren' : 'Sperren', m.suspended ? 'secondary' : 'danger'); toggle.type = 'button'; toggle.disabled = disabled;
     toggle.title = m.identityActive === false ? 'Das globale Konto ist inaktiv.' : own && !m.suspended ? 'Den eigenen Zugang kann man nicht sperren.' : 'Betrifft nur den Zugang zu diesem Mandanten.';
     toggle.onclick = async () => {
      if (!m.suspended && !await confirmAction(`Zugang zu diesem Mandanten für ${m.username} sperren?`)) return;
      toggle.disabled = true;
      try { await json(route + encodeURIComponent(m.userId) + '/access', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ suspended: !m.suspended }) }); await load(); }
      catch (error) { message.textContent = error.message || 'Zugang konnte nicht geändert werden.'; message.className = 'members-saved error'; toggle.disabled = false; }
     };
     actions.append(toggle);
    }
    row.append(who, roleCell, state, actions); tbody.append(row);
   });
  };
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
  top.append(node('p', 'Welche Rolle darf was: ✓ bedeutet, die Rolle hat das Recht. Mitglieder erben alle Rechte ihrer Rolle.', 'members-note'));
  if (can('USER_MANAGE') && document.querySelector('#manageRoles')) { const edit = node('button', 'Rollen bearbeiten', 'secondary'); edit.type = 'button'; edit.onclick = () => document.querySelector('#manageRoles')?.click(); top.append(edit); }
  const wrap = node('div', undefined, 'members-table-wrap'), table = node('table', undefined, 'members-matrix');
  const head = node('thead'), hr = node('tr'); hr.append(node('th', 'Recht'));
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
