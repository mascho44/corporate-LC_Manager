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
 assert.deepEqual(names(api.filterMembers(members, { roleId: 'r2', status: 'suspended' })), ['cem']);
 assert.deepEqual(names(api.filterMembers(members, { text: 'gesperrt' })), ['cem']);
 assert.deepEqual(names(api.filterMembers(members, { status: 'active' })), ['anna', 'ben']);
 assert.equal(api.filterMembers(null, {}).length, 0);
});
test('the permission matrix lists every permission once per area with a flag per role, unknown permissions under "Weitere"', () => {
 const roles = [{ id: 'a', name: 'Admin', permissions: ['LC_EDIT', 'USER_MANAGE', 'NEW_RIGHT'] }, { id: 'b', name: 'Leser', permissions: [] }];
 const matrix = api.permissionMatrix(roles);
 assert.deepEqual(Array.from(matrix, g => g.group), ['Akkreditive', 'Dokumente', 'Training und Nachweis', 'Verwaltung', 'Weitere']);
 const edit = matrix[0].rows.find(r => r.code === 'LC_EDIT');
 assert.deepEqual(Array.from(edit.roles), [true, false]); assert.equal(edit.label, 'LC-Akten bearbeiten');
 assert.deepEqual(Array.from(matrix[4].rows, r => r.code), ['NEW_RIGHT']); assert.equal(matrix[4].rows[0].label, 'NEW_RIGHT');
 assert.equal(matrix.flatMap(g => g.rows).length, 14);
});
test('members are counted per role', () => {
 const counts = api.memberCounts(members); assert.equal(counts.get('r2'), 2); assert.equal(counts.get('r1'), 1); assert.equal(counts.get('x') || 0, 0);
});
test('bulk actions skip the own user and members that need no change, and say why', () => {
 const group = [{ username: 'Anna', roleId: 'r1', suspended: false }, { username: 'ben', roleId: 'r2', suspended: false }, { username: 'cem', roleId: 'r2', suspended: true }, { username: 'dan', roleId: 'r2', suspended: false, identityActive: false }];
 const names = list => Array.from(list, m => m.username);
 const role = api.bulkPlan(group, 'role', { roleId: 'r2', me: 'anna' });
 assert.equal(role.apply.length, 0, 'everyone already has r2 or is the own user');
 assert.deepEqual(Array.from(role.skipped, s => s.reason), ['eigene Rolle', 'hat die Rolle schon', 'hat die Rolle schon', 'hat die Rolle schon']);
 const r1 = api.bulkPlan(group, 'role', { roleId: 'r1', me: 'anna' });
 assert.deepEqual(names(r1.apply), ['ben', 'cem', 'dan']);
 const suspend = api.bulkPlan(group, 'suspend', { me: 'ANNA' });
 assert.deepEqual(names(suspend.apply), ['ben']); assert.deepEqual(Array.from(suspend.skipped, s => s.reason), ['eigener Zugang', 'schon gesperrt', 'Konto inaktiv']);
 const activate = api.bulkPlan(group, 'activate', { me: 'anna' });
 assert.deepEqual(names(activate.apply), ['cem']); assert.equal(activate.skipped.length, 3);
});
