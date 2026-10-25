const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm');
const context = vm.createContext({tx:(key,de,values={})=>String(de).replace(/\{(\w+)\}/g,(m,k)=>values[k]??m),});
context.globalThis = context;
vm.runInContext(fs.readFileSync('src/main/resources/static/members-page.js', 'utf8'), context);
const api = context.LcMembersPage;
const members = [{ username: 'anna', roleId: 'r1', roleName: 'Administrator', suspended: false }, { username: 'ben', roleId: 'r2', roleName: 'Leser', suspended: false }, { username: 'cem', roleId: 'r2', roleName: 'Leser', suspended: true }];
test('members are filtered by search text, role and status; German status words are searchable', () => {
 const names = list => Array.from(list, m => m.username);
 assert.deepEqual(names(api.filterMembers(members, {})), ['anna', 'ben', 'cem']);
 assert.deepEqual(names(api.filterMembers(members, { text: ' LES ' })), ['ben', 'cem']);
 assert.deepEqual(names(api.filterMembers(members, { status: 'suspended' })), ['cem']);
 assert.deepEqual(names(api.filterMembers(members, { text: 'gesperrt' })), ['cem']);
 assert.deepEqual(names(api.filterMembers(members, { status: 'active' })), ['anna', 'ben']);
 assert.equal(api.filterMembers(null, {}).length, 0);
});

const levels = (lc, guarantee, documents, admin = false) => ({ lc, guarantee, documents, admin });
test('the cells of the access matrix and the permissions behind them agree in both directions', () => {
 for (const l of [levels('NONE', 'NONE', 'VIEW'), levels('VIEW', 'EDIT', 'VIEW'), levels('EDIT', 'NONE', 'EDIT'), levels('EDIT', 'EDIT', 'EDIT'), levels('VIEW', 'VIEW', 'EDIT')]) {
  const back = api.levelsOf(Array.from(api.permissionsFor(l)));
  assert.deepEqual([back.lc, back.guarantee, back.documents, back.admin, back.special.length], [l.lc, l.guarantee, l.documents, false, 0]);
 }
 const admin = api.levelsOf(Array.from(api.permissionsFor(levels('NONE', 'NONE', 'VIEW', true))));
 assert.equal(admin.admin, true); assert.equal(admin.lc, 'EDIT'); assert.equal(admin.guarantee, 'EDIT'); assert.equal(admin.special.length, 0);
});
test('rights the matrix cannot express are reported as special rights, and editing a guarantee needs the viewing right as well', () => {
 const special = api.levelsOf(['LC_VIEW', 'LC_EDIT', 'SWIFT_IMPORT', 'AUDIT_VIEW', 'LC_DELETE']);
 assert.deepEqual(Array.from(special.special), ['AUDIT_VIEW', 'LC_DELETE']);
 assert.deepEqual(Array.from(api.permissionsFor(levels('NONE', 'EDIT', 'VIEW'))), ['GUARANTEE_EDIT', 'GUARANTEE_VIEW']);
});
test('the base role follows the cells and an existing role with the same rights is reused', () => {
 assert.equal(api.baseRoleFor(levels('VIEW', 'VIEW', 'VIEW')), 'VIEWER'); assert.equal(api.baseRoleFor(levels('VIEW', 'EDIT', 'VIEW')), 'EDITOR'); assert.equal(api.baseRoleFor(levels('NONE', 'NONE', 'VIEW', true)), 'ADMIN');
 const wanted = levels('EDIT', 'VIEW', 'VIEW');
 const roles = [{ id: 'x', baseRole: 'EDITOR', permissions: ['LC_VIEW'] }, { id: 'y', baseRole: 'EDITOR', permissions: Array.from(api.permissionsFor(wanted)).reverse() }, { id: 'z', baseRole: 'VIEWER', permissions: Array.from(api.permissionsFor(wanted)) }];
 assert.equal(api.findRole(roles, wanted).id, 'y'); assert.equal(api.findRole(roles, levels('NONE', 'EDIT', 'VIEW')), null);
 assert.equal(api.roleNameFor(wanted), 'Zugriff: Akkreditive bearbeiten · Garantien ansehen · Dokumente ansehen');
});
