const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm');
const context = vm.createContext({});
context.globalThis = context;
vm.runInContext(fs.readFileSync('src/main/resources/static/mobile-nav.js', 'utf8'), context);
const { setOpen } = context.LcMobileNav;
const body = () => { const classes = new Set(); return { classList: { contains: c => classes.has(c), toggle: (c, on) => { if (on) classes.add(c); else classes.delete(c); return on; } } }; };
test('the drawer toggles, opens and closes explicitly', () => {
 const b = body();
 assert.equal(setOpen(b), true); assert.equal(b.classList.contains('nav-open'), true);
 assert.equal(setOpen(b), false); assert.equal(b.classList.contains('nav-open'), false);
 assert.equal(setOpen(b, true), true); assert.equal(setOpen(b, true), true); assert.equal(setOpen(b, false), false);
});
