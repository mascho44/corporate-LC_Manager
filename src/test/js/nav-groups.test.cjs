const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm'), path = require('node:path');
const dir = path.join(__dirname, '../../main/resources/static/');
const source = fs.readFileSync(dir + 'nav-groups.js', 'utf8');
const context = vm.createContext({ document: undefined });
vm.runInContext(source, context);
const LcNav = context.LcNav;
const entry = (key, hidden = false) => ({ key, hidden });
const keys = steps => JSON.parse(JSON.stringify(steps)).map(s => s.heading ? '#' + s.heading + (s.visible ? '' : '(hidden)') : s.key);

test('working entries come first, administration follows in three groups, whatever the order the pages added them in', () => {
 const added = ['sec:cockpit', 'appNavMyWork', 'sec:lcs', 'appNavInbox', 'appNavTraining', 'appNavImport', 'sec:imports', 'appNavMonitoring', 'appNavCompany', 'appNavTemplates', 'appNavUsers', 'appNavAudit',
  'appNavRulePacks', 'appNavApprovalPolicy', 'appNavEbics', 'appNavSla', 'appNavCorrespondent', 'appNavWebhooks', 'appNavApiKeys', 'appNavLiquidity', 'appNavGuarantees', 'appNavCreditLines', 'appNavGroupInbox', 'appNavCalendar', 'appNavTenants', 'appNavPlatform'].map(k => entry(k));
 const order = keys(LcNav.plan(added));
 assert.deepEqual(order, ['#work', 'sec:cockpit', 'appNavMyWork', 'appNavGroupInbox', 'sec:lcs', 'appNavGuarantees', 'appNavCreditLines', 'appNavCalendar', 'appNavLiquidity', 'appNavInbox', 'appNavImport', 'sec:imports', 'appNavTraining',
  '#admin', '#master', 'appNavCompany', 'appNavTemplates', 'appNavRulePacks', 'appNavCorrespondent', 'appNavApprovalPolicy', 'appNavSla',
  '#access', 'appNavUsers', 'appNavApiKeys', 'appNavWebhooks', 'appNavAudit', '#ops', 'appNavEbics', 'appNavMonitoring', 'appNavTenants', 'appNavPlatform']);
 // guarantees, lines, calendar, liquidity and group inbox are work, not administration
 const adminStart = order.indexOf('#admin');
 ['appNavGuarantees', 'appNavCreditLines', 'appNavCalendar', 'appNavLiquidity', 'appNavGroupInbox'].forEach(k => assert.ok(order.indexOf(k) < adminStart, k));
 ['appNavSla', 'appNavApiKeys', 'appNavWebhooks', 'appNavEbics', 'appNavUsers'].forEach(k => assert.ok(order.indexOf(k) > adminStart, k));
});

test('a group heading is shown only when one of its entries is visible, and the administration heading only when any group is', () => {
 const steps = JSON.parse(JSON.stringify(LcNav.plan([entry('sec:cockpit'), entry('appNavCompany', true), entry('appNavUsers', true), entry('appNavEbics', true), entry('appNavSla', false)])));
 const heading = id => steps.find(s => s.heading === id).visible;
 assert.equal(heading('master'), true); assert.equal(heading('access'), false); assert.equal(heading('ops'), false); assert.equal(heading('admin'), true);
 const none = LcNav.plan([entry('sec:cockpit'), entry('appNavCompany', true), entry('appNavMonitoring', true)]);
 assert.equal(none.find(s => s.heading === 'admin').visible, false);
 assert.equal(LcNav.plan([entry('sec:cockpit')]).find(s => s.heading === 'master').visible, false);
});

test('an entry the menu does not know yet stays with the working entries and nothing is dropped', () => {
 const order = keys(LcNav.plan([entry('sec:cockpit'), entry('appNavSomethingNew'), entry('appNavCompany')]));
 assert.ok(order.indexOf('appNavSomethingNew') > order.indexOf('sec:cockpit') && order.indexOf('appNavSomethingNew') < order.indexOf('#admin'));
 assert.ok(order.includes('appNavCompany'));
 assert.equal(new Set(order).size, order.length, 'no entry twice');
});

test('the menu script is loaded after the pages that add entries and the group texts exist in both language packs', () => {
 const html = fs.readFileSync(dir + 'index.html', 'utf8'), sources = [...html.matchAll(/<script src="\/([^"?]+)/g)].map(m => m[1]);
 ['guarantees.js', 'liquidity.js', 'webhook-settings.js', 'api-key-settings.js', 'sla-settings.js', 'correspondent-settings.js', 'ebics.js', 'tenants.js'].forEach(f => assert.ok(sources.indexOf(f) >= 0 && sources.indexOf(f) < sources.indexOf('nav-groups.js'), f));
 const de = JSON.parse(fs.readFileSync(dir + 'language-de.json', 'utf8')), en = JSON.parse(fs.readFileSync(dir + 'language-en.json', 'utf8'));
 ['master', 'access', 'ops'].forEach(g => { assert.ok(de['nav.group.' + g]); assert.ok(en['nav.group.' + g]); });
 // every group entry exists as an id or section somewhere in the sources
 const all = fs.readdirSync(dir).filter(f => /\.(js|html)$/.test(f)).map(f => fs.readFileSync(dir + f, 'utf8')).join('\n');
 [...LcNav.WORK, ...LcNav.ADMIN.flatMap(g => g.items)].filter(k => !k.startsWith('sec:')).forEach(k => assert.ok(all.includes(k), 'no entry with id ' + k));
});
