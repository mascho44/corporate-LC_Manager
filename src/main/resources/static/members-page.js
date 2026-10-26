/* "Benutzer & Rechte": one page for tenant administrators. Tab "Zugriff" is a simple matrix, persons x areas (Akkreditive, Garantien, Dokumente, Administration), each cell a level (no access / view / edit);
   behind it, the matching role is looked up (or created once) and assigned, so nobody has to deal with roles. Tab "Einladungen" invites persons or assigns existing accounts. */
(() => {
 const LABELS = () => ({ LC_VIEW: tx('permission.LC_VIEW','LC-Akten ansehen'), GUARANTEE_VIEW: tx('permission.GUARANTEE_VIEW','Garantien ansehen'), GUARANTEE_EDIT: tx('permission.GUARANTEE_EDIT','Garantien bearbeiten'), LC_EDIT: tx('mp.0','LC-Akten bearbeiten'), LC_DELETE: tx('mp.1','LC-Akten löschen'), SWIFT_IMPORT: tx('mp.2','SWIFT-Nachrichten importieren'), DOCUMENT_UPLOAD: tx('mp.3','Dokumente hochladen'), DOCUMENT_DELETE: tx('mp.4','Dokumente löschen'), DOCUMENT_GENERATE: tx('mp.5','Dokumente erstellen (Maker)'),
  DOCUMENT_REVIEW: tx('mp.6','Dokumente prüfen (Checker)'), DOCUMENT_APPROVE: tx('mp.7','Dokumente freigeben (Approver)'), EMAIL_SEND: tx('mp.8','E-Mails aus Akten senden'), TRAINING_MANAGE: tx('mp.9','Training verwalten'), AUDIT_VIEW: tx('mp.10','Audit-Protokoll ansehen'), SETTINGS_MANAGE: tx('mp.11','Firmen, Vorlagen und Einstellungen verwalten'), USER_MANAGE: tx('mp.12','Benutzer verwalten') });
 /** Levels of the matrix: LC and guarantees NONE | VIEW | EDIT, documents VIEW | EDIT (viewing follows the file), admin true | false. */
 const LEVEL_PERMISSIONS = {
  lc: { VIEW: ['LC_VIEW'], EDIT: ['LC_VIEW', 'LC_EDIT', 'SWIFT_IMPORT'] },
  guarantee: { VIEW: ['GUARANTEE_VIEW'], EDIT: ['GUARANTEE_VIEW', 'GUARANTEE_EDIT'] },
  documents: { EDIT: ['DOCUMENT_UPLOAD', 'DOCUMENT_DELETE', 'DOCUMENT_GENERATE', 'DOCUMENT_REVIEW', 'EMAIL_SEND', 'TRAINING_MANAGE'] }
 };
 const ALL_PERMISSIONS = ['LC_VIEW', 'GUARANTEE_VIEW', 'LC_EDIT', 'GUARANTEE_EDIT', 'LC_DELETE', 'SWIFT_IMPORT', 'DOCUMENT_UPLOAD', 'DOCUMENT_DELETE', 'DOCUMENT_GENERATE', 'DOCUMENT_REVIEW', 'DOCUMENT_APPROVE', 'EMAIL_SEND', 'TRAINING_MANAGE', 'AUDIT_VIEW', 'SETTINGS_MANAGE', 'USER_MANAGE', 'SANCTIONS_REVIEW'];
 /** The cells of the matrix for a set of permissions; `special` lists permissions the matrix cannot express (the person has a special role). */
 function levelsOf(permissions) {
  const has = new Set(permissions || []), admin = has.has('USER_MANAGE');
  const level = (edit, view) => has.has(edit) ? 'EDIT' : has.has(view) ? 'VIEW' : 'NONE';
  const levels = { lc: level('LC_EDIT', 'LC_VIEW'), guarantee: level('GUARANTEE_EDIT', 'GUARANTEE_VIEW'), documents: has.has('DOCUMENT_UPLOAD') ? 'EDIT' : 'VIEW', admin };
  const wanted = new Set(permissionsFor(levels));
  levels.special = admin ? [] : [...has].filter(p => !wanted.has(p)).sort();
  return levels;
 }
 /** The permissions a row of the matrix stands for. */
 function permissionsFor(levels) {
  if (levels.admin) return ALL_PERMISSIONS.slice().sort();
  const out = new Set([...(LEVEL_PERMISSIONS.lc[levels.lc] || []), ...(LEVEL_PERMISSIONS.guarantee[levels.guarantee] || []), ...(LEVEL_PERMISSIONS.documents[levels.documents] || [])]);
  return [...out].sort();
 }
 /** Base role for the permissions: administrators, editors (anything to edit) or viewers. */
 function baseRoleFor(levels) { return levels.admin ? 'ADMIN' : (levels.lc === 'EDIT' || levels.guarantee === 'EDIT' || levels.documents === 'EDIT') ? 'EDITOR' : 'VIEWER'; }
 /** A readable name for the role that belongs to a row, e.g. "Zugriff: Akkreditive bearbeiten · Garantien ansehen · Dokumente ansehen". */
 function roleNameFor(levels) {
  if (levels.admin) return 'Zugriff: Administration';
  const word = { NONE: 'kein Zugriff', VIEW: 'ansehen', EDIT: 'bearbeiten' };
  return `Zugriff: Akkreditive ${word[levels.lc]} · Garantien ${word[levels.guarantee]} · Dokumente ${word[levels.documents]}`;
 }
 /** The role for a row among the existing roles (same permissions and base role), or null when it has to be created. */
 function findRole(roles, levels) {
  const wanted = permissionsFor(levels).join(','), base = baseRoleFor(levels);
  return (roles || []).find(r => (r.baseRole || base) === base && [...(r.permissions || [])].sort().join(',') === wanted) || null;
 }
 /** Members matching the search text (name, role, status words) and the status filter; status is 'active' | 'suspended' | ''. */
 function filterMembers(members, { text = '', status = '' } = {}) {
  const needle = String(text).trim().toLocaleLowerCase('de');
  return (members || []).filter(m => {
   const state = m.suspended ? 'suspended' : 'active';
   if (status && state !== status) return false;
   return !needle || [m.username, m.roleName, m.suspended ? 'gesperrt' : 'aktiv'].join(' ').toLocaleLowerCase('de').includes(needle);
  });
 }
 globalThis.LcMembersPage = { levelsOf, permissionsFor, baseRoleFor, roleNameFor, findRole, filterMembers, LABELS };
 /** Readable summary of a row, e.g. "Akkreditive bearbeiten · Garantien ansehen · Dokumente ansehen". */
 globalThis.LcMembersPage.summaryOf = levels => roleNameFor(levels).replace(/^Zugriff: /, '');
 if (typeof document === 'undefined' || !document.body) return;

 const node = (tag, text, css) => { const n = document.createElement(tag); if (text !== undefined) n.textContent = text; if (css) n.className = css; return n; };
 let section = null, overview = null, active = 'access';

 function build() {
  section = node('section', undefined, 'panel hidden members-page'); section.id = 'membersSection';
  const head = node('div', undefined, 'panelhead'), title = node('div');
  title.append(node('h2', tx('mp.24','Benutzer & Rechte')), node('p', '', 'members-facts'));
  const refresh = node('button', tx('mp.25','Aktualisieren'), 'secondary'); refresh.type = 'button'; refresh.onclick = () => load();
  head.append(title, refresh);
  const flash = node('p', '', 'members-flash'); flash.setAttribute('role', 'status');
  const tabs = node('div', undefined, 'members-tabs'); tabs.setAttribute('role', 'tablist');
  [['access', tx('mz.tab','Zugriff')], ['invitations', tx('mp.27','Einladungen')]].forEach(([key, text]) => {
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
  ({ access: paintAccess, invitations: paintInvitations })[active](body);
 }


 /** The id of the role that stands for a row of the matrix: an existing role with the same rights, or a new one. */
 async function resolveRoleId(levels) {
  const roles = ((await json('/api/roles')) || []).map(r => ({ id: r.id, baseRole: r.baseRole, permissions: r.permissions || [] }));
  const found = findRole(roles, levels); if (found) return found.id;
  const created = await json('/api/roles', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name: roleNameFor(levels), baseRole: baseRoleFor(levels), permissions: permissionsFor(levels) }) });
  return created.id;
 }
 /** A small form part with the cells of the matrix (areas and levels) for invitations and assignments; resolveRoleId() gives the role to send. */
 function accessPicker(guarantees) {
  const box = node('span', undefined, 'access-picker'), text = LEVEL_TEXT(), state = { lc: 'VIEW', guarantee: 'VIEW', documents: 'VIEW', admin: false };
  const add = (key, label, options) => { const l = node('label', label + ' '), s = node('select'); options.forEach(([v, t]) => s.append(Object.assign(node('option', t), { value: String(v) }))); s.value = String(state[key]); s.setAttribute('aria-label', label); s.onchange = () => { state[key] = key === 'admin' ? s.value === 'true' : s.value; }; l.append(s); box.append(l); return s; };
  add('lc', tx('mz.lc','Akkreditive'), [['NONE', text.NONE], ['VIEW', text.VIEW], ['EDIT', text.EDIT]]);
  if (guarantees !== false) add('guarantee', tx('mz.guarantees','Garantien'), [['NONE', text.NONE], ['VIEW', text.VIEW], ['EDIT', text.EDIT]]); else state.guarantee = 'NONE';
  add('documents', tx('mz.documents','Dokumente'), [['VIEW', text.VIEW], ['EDIT', text.EDIT]]);
  add('admin', tx('mz.admin','Administration'), [[false, tx('mz.no','Nein')], [true, tx('mz.yes','Ja')]]);
  return { element: box, levels: () => ({ ...state }), summary: () => globalThis.LcMembersPage.summaryOf(state), resolveRoleId: () => resolveRoleId({ ...state }) };
 }
 globalThis.LcMembersPage.accessPicker = accessPicker;
 const LEVEL_TEXT = () => ({ NONE: tx('mz.none','Kein Zugriff'), VIEW: tx('mz.view','Ansehen'), EDIT: tx('mz.edit','Bearbeiten') });
 /** Matrix: one row per person, one cell per area; a change finds or creates the matching role and assigns it. */
 async function paintAccess(body) {
  const members = overview.memberships;
  const route = overview.tenantId && overview.tenantId !== '00000000-0000-0000-0000-000000000001' ? '/api/users/memberships/shared/' : '/api/users/memberships/';
  const canEdit = overview.roleEditingEnabled && can('USER_MANAGE'), canAccess = overview.accessEditingEnabled && can('USER_MANAGE');
  let roles = overview.roleChoices || [];
  try { const full = await json('/api/roles'); if (Array.isArray(full)) roles = full.map(r => ({ id: r.id, name: r.name, baseRole: r.baseRole, permissions: r.permissions || [] })); } catch (ignored) { /* the role choices of the overview are enough to show the matrix */ }
  const byId = new Map(roles.map(r => [r.id, r]));
  const showGuarantees = !overview.settings || overview.settings.corporateEnabled !== false;
  body.append(node('p', tx('mz.note','Wer darf was: Wählen Sie je Person und Bereich, wie weit der Zugriff geht. Die passenden Rechte werden im Hintergrund vergeben. „Administration“ umfasst Benutzer, Einstellungen, Schnittstellen und alle Rechte.'), 'members-note'));
  if (!showGuarantees) body.append(node('p', tx('mz.noteBank','In Bank-Mandanten gilt „Dokumente erstellen (Maker)“ nicht: Dokumente werden dort hochgeladen und geprüft, Handelsrechnungen und Packlisten erzeugen nur Corporate-Mandanten.'), 'members-note'));
  const bar = node('div', undefined, 'members-toolbar');
  const search = node('input'); search.type = 'search'; search.placeholder = tx('mz.search','Person suchen'); search.setAttribute('aria-label', tx('mp.35','Mitglieder suchen')); search.maxLength = 100;
  const count = node('small', '', 'members-count'); count.setAttribute('role', 'status'); bar.append(search, count);
  const wrap = node('div', undefined, 'members-table-wrap'), table = node('table', undefined, 'members-table access-matrix');
  const head = node('thead'), hr = node('tr');
  [tx('mp.43','Benutzer'), tx('mz.lc','Akkreditive'), showGuarantees ? tx('mz.guarantees','Garantien') : null, tx('mz.documents','Dokumente'), tx('mz.admin','Administration'), tx('mp.45','Status')].filter(Boolean).forEach(t => hr.append(node('th', t))); head.append(hr);
  const tbody = node('tbody'); table.append(head, tbody); wrap.append(table); body.append(bar, wrap);
  const me = String(typeof currentUser !== 'undefined' ? currentUser?.username || '' : '').toLowerCase();
  async function apply(member, levels, message) {
   const target = findRole(roles, levels);
   let roleId = target?.id;
   if (!roleId) {
    const created = await json('/api/roles', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name: roleNameFor(levels), baseRole: baseRoleFor(levels), permissions: permissionsFor(levels) }) });
    roleId = created.id; roles.push({ id: created.id, name: created.name, baseRole: created.baseRole || baseRoleFor(levels), permissions: created.permissions || permissionsFor(levels) });
   }
   await json(route + encodeURIComponent(member.userId) + '/role', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ roleId }) });
   member.roleId = roleId; message.textContent = tx('mp.51','✓ gespeichert'); message.className = 'members-saved';
  }
  const draw = () => {
   const list = filterMembers(members, { text: search.value });
   count.textContent = tx('mpt.8ca8ee','{p0} von {p1} Mitgliedern', { p0: list.length, p1: members.length }); tbody.replaceChildren();
   if (!list.length) { const r = node('tr'), c = node('td', tx('mp.46','Keine Mitglieder gefunden.')); c.colSpan = 6; r.append(c); tbody.append(r); return; }
   list.forEach(m => {
    const own = String(m.username).toLowerCase() === me, row = node('tr'), role = byId.get(m.roleId);
    const levels = levelsOf(role ? role.permissions : []), message = node('small', '', 'members-saved'); message.setAttribute('role', 'status');
    const who = node('td'); who.append(node('b', m.username)); if (own) who.append(node('small', tx('mp.47',' (Sie)')));
    if (levels.special.length) { const badge = node('small', tx('mz.special',' · Sonderrechte'), 'members-note'); badge.title = levels.special.map(c => LABELS()[c] || c).join(', '); who.append(badge); }
    const shared = route.endsWith('/shared/');
    if (shared && canAccess && !own) {
     const remove = node('button', '🗑', 'ghost'); remove.type = 'button'; remove.title = tx('mz.remove','Aus diesem Mandanten entfernen'); remove.setAttribute('aria-label', remove.title + ': ' + m.username);
     remove.onclick = async () => {
      if (!await confirmAction(tx('mz.removeConfirm','{name} aus diesem Mandanten entfernen? Das Konto bleibt bestehen, der Zugang zu diesem Mandanten entfällt.', { name: m.username }))) return;
      remove.disabled = true;
      try { await json(route + encodeURIComponent(m.userId), { method: 'DELETE' }); await load(); }
      catch (error) { message.textContent = error.message || tx('mz.removeFailed','Konnte nicht entfernt werden.'); message.className = 'members-saved error'; remove.disabled = false; }
     };
     who.append(' ', remove);
    }
    who.append(message); row.append(who);
    const current = { lc: levels.lc, guarantee: levels.guarantee, documents: levels.documents, admin: levels.admin };
    const change = async (key, value, control) => {
     const next = { ...current, [key]: value };
     if (levels.special.length && !await confirmAction(tx('mz.confirmSpecial','{name} hat Sonderrechte ({list}). Sie entfallen, wenn Sie den Zugriff hier ändern. Fortfahren?', { name: m.username, list: levels.special.map(c => LABELS()[c] || c).join(', ') }))) { control.value = String(current[key]); return; }
     if (key === 'admin' && value && !await confirmAction(tx('mz.confirmAdmin','{name} zum Administrator machen? Das umfasst alle Rechte, auch Benutzer- und Schnittstellenverwaltung.', { name: m.username }))) { control.value = 'false'; return; }
     document.querySelectorAll('.access-matrix select').forEach(c => c.disabled = true); message.textContent = tx('mp.50','Wird gespeichert …'); message.className = 'members-saved';
     try { await apply(m, next, message); await load(); }
     catch (error) { control.value = String(current[key]); message.textContent = error.message || tx('mp.52','Rolle konnte nicht gespeichert werden.'); message.className = 'members-saved error'; document.querySelectorAll('.access-matrix select').forEach(c => { c.disabled = false; }); }
    };
    const cell = (key, options, label) => {
     const td = node('td'), select = node('select'); select.setAttribute('aria-label', label + ' – ' + m.username);
     options.forEach(([v, t]) => select.append(Object.assign(node('option', t), { value: String(v) }))); select.value = String(current[key]);
     select.disabled = !canEdit || own || (levels.admin && key !== 'admin'); if (own) select.title = tx('mp.49','Die eigene Rolle kann hier nicht geändert werden.');
     select.onchange = () => change(key, key === 'admin' ? select.value === 'true' : select.value, select); td.append(select); row.append(td);
    };
    const text = LEVEL_TEXT(), three = [['NONE', text.NONE], ['VIEW', text.VIEW], ['EDIT', text.EDIT]], docs = [['VIEW', text.VIEW], ['EDIT', text.EDIT]];
    cell('lc', three, tx('mz.lc','Akkreditive')); if (showGuarantees) cell('guarantee', three, tx('mz.guarantees','Garantien')); cell('documents', docs, tx('mz.documents','Dokumente'));
    cell('admin', [[false, tx('mz.no','Nein')], [true, tx('mz.yes','Ja')]], tx('mz.admin','Administration'));
    const state = node('td', undefined, 'members-actions'); state.append(node('span', m.suspended ? tx('mp.41','Gesperrt') : m.active ? tx('mp.40','Aktiv') : 'Inaktiv', 'import-status ' + (m.active && !m.suspended ? 'success' : 'rejected')));
    if (canAccess && !own && m.identityActive !== false) {
     const toggle = node('button', m.suspended ? tx('mp.53','Aktivieren') : tx('mp.54','Sperren'), m.suspended ? 'secondary' : 'danger'); toggle.type = 'button'; toggle.title = tx('mp.57','Betrifft nur den Zugang zu diesem Mandanten.');
     toggle.onclick = async () => {
      if (!m.suspended && !await confirmAction(tx('mpt.ca0a2a','Zugang zu diesem Mandanten für {p0} sperren?', { p0: m.username }))) return;
      toggle.disabled = true;
      try { await json(route + encodeURIComponent(m.userId) + '/access', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ suspended: !m.suspended }) }); await load(); }
      catch (error) { message.textContent = error.message || tx('mp.58','Zugang konnte nicht geändert werden.'); message.className = 'members-saved error'; toggle.disabled = false; }
     };
     state.append(' ', toggle);
    }
    row.append(state); tbody.append(row);
   });
  };
  search.addEventListener('input', draw); draw();
 }

 function paintInvitations(body) {
  const holder = node('div', undefined, 'members-invitations'); body.append(holder);
  globalThis.LcTenantInvitations?.render?.(holder);
  const shared = overview.tenantId && overview.tenantId !== '00000000-0000-0000-0000-000000000001';
  if (shared && can('USER_MANAGE') && typeof renderMembershipAssignment === 'function') renderMembershipAssignment(holder, overview.roleChoices || []);
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
