#!/usr/bin/env node
/*
 * Moves tenant administration from a platform account to a separate tenant account, using only the platform API.
 *
 *   node scripts/platform-separate-admin.cjs --new-admin markus --tenants "SISW Demo,Firma"            (shows the plan, changes nothing)
 *   node scripts/platform-separate-admin.cjs --new-admin markus --tenants "SISW Demo,Firma" --apply     (executes after a confirmation)
 *
 * Options: --url https://lc.ostms.de  --platform-user admin
 * You enter the password and the two-factor code in the terminal; nothing is stored or printed. Every change is audited by the server like a change made in the platform UI.
 * Steps per tenant: (1) the new administrator gets the administrator role (role change or assignment), (2) only then the platform account's access to that tenant is suspended.
 */
'use strict';
const readline = require('node:readline');

/** Pure planning step (tested): what has to change so that `newAdmin` administers each tenant and `platformUser` no longer has access. */
function plan(memberships, choices, { platformUser, newAdmin, tenants }) {
 const lower = s => String(s || '').trim().toLowerCase();
 const actions = [], problems = [];
 for (const wanted of tenants) {
  const choice = choices.find(c => lower(c.name) === lower(wanted) || lower(c.code) === lower(wanted));
  if (!choice) { problems.push(`Mandant „${wanted}“ nicht gefunden.`); continue; }
  const roles = (choice.roles || []).filter(r => /administrator/.test(lower(r.name)) && (r.permissions || []).includes('USER_MANAGE'));
  if (roles.length !== 1) { problems.push(`Mandant „${choice.name}“: erwarte genau eine Administratorrolle mit Benutzerverwaltung, gefunden ${roles.length}.`); continue; }
  const role = roles[0];
  const rows = memberships.filter(m => m.tenantId === choice.id);
  const target = rows.find(m => lower(m.username) === lower(newAdmin));
  if (!target) actions.push({ type: 'assign', tenantId: choice.id, tenant: choice.name, username: newAdmin, roleId: role.id, role: role.name });
  else if (!target.manageable) problems.push(`Mandant „${choice.name}“: ${newAdmin} ist dort eine Heimat-Identität und über die Plattform nicht änderbar.`);
  else {
   if (target.roleId !== role.id) actions.push({ type: 'role', tenantId: choice.id, tenant: choice.name, userId: target.userId, username: newAdmin, roleId: role.id, role: role.name });
   if (target.suspended || !target.membershipActive) actions.push({ type: 'restore', tenantId: choice.id, tenant: choice.name, userId: target.userId, username: newAdmin });
  }
  const old = rows.find(m => lower(m.username) === lower(platformUser));
  if (old && old.manageable && old.membershipActive && !old.suspended) actions.push({ type: 'suspend', tenantId: choice.id, tenant: choice.name, userId: old.userId, username: platformUser });
  else if (old && !old.manageable) problems.push(`Mandant „${choice.name}“: ${platformUser} ist dort eine Heimat-Identität und über die Plattform nicht änderbar.`);
 }
 return { actions, problems };
}
function describe(a) {
 return { assign: `${a.tenant}: ${a.username} als „${a.role}“ zuweisen`, role: `${a.tenant}: Rolle von ${a.username} auf „${a.role}“ ändern`, restore: `${a.tenant}: Zugang von ${a.username} wiederherstellen`, suspend: `${a.tenant}: Zugang von ${a.username} aussetzen` }[a.type];
}
module.exports = { plan, describe };
if (require.main !== module) return;

function args() {
 const out = { url: 'https://lc.ostms.de', platformUser: 'admin', newAdmin: null, tenants: [], apply: false };
 const a = process.argv.slice(2);
 for (let i = 0; i < a.length; i++) {
  if (a[i] === '--url') out.url = a[++i];
  else if (a[i] === '--platform-user') out.platformUser = a[++i];
  else if (a[i] === '--new-admin') out.newAdmin = a[++i];
  else if (a[i] === '--tenants') out.tenants = String(a[++i]).split(',').map(s => s.trim()).filter(Boolean);
  else if (a[i] === '--apply') out.apply = true;
  else { console.error('Unbekannte Option: ' + a[i]); process.exit(2); }
 }
 if (!out.newAdmin || !out.tenants.length) { console.error('Benötigt: --new-admin <Benutzer> --tenants "<Mandant>,<Mandant>"'); process.exit(2); }
 if (!/^https:\/\//.test(out.url) && !/^http:\/\/(localhost|127\.0\.0\.1)/.test(out.url)) { console.error('Nur HTTPS (oder localhost).'); process.exit(2); }
 return out;
}
function ask(question, hidden) {
 return new Promise(resolve => {
  const rl = readline.createInterface({ input: process.stdin, output: process.stdout, terminal: true });
  if (hidden) rl._writeToOutput = s => { if (s.includes(question)) rl.output.write(s); };
  rl.question(question, answer => { rl.close(); if (hidden) process.stdout.write('\n'); resolve(answer); });
 });
}
const jar = new Map();
let base = '', csrf = '';
async function call(path, method = 'GET', body) {
 const headers = { Accept: 'application/json' };
 if (body !== undefined) headers['Content-Type'] = 'application/json';
 if (method !== 'GET' && csrf) headers['X-CSRF-TOKEN'] = csrf;
 if (jar.size) headers.Cookie = [...jar].map(([k, v]) => `${k}=${v}`).join('; ');
 const response = await fetch(base + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body), redirect: 'manual' });
 for (const c of response.headers.getSetCookie?.() || []) { const [pair] = c.split(';'); const i = pair.indexOf('='); jar.set(pair.slice(0, i), pair.slice(i + 1)); }
 const text = await response.text();
 let data = {}; try { data = text ? JSON.parse(text) : {}; } catch { data = { raw: text.slice(0, 200) }; }
 if (!response.ok) throw new Error(`${method} ${path}: HTTP ${response.status} ${data.error || data.message || ''}`.trim());
 return data;
}
(async () => {
 const o = args(); base = o.url.replace(/\/+$/, '');
 console.log(`Server: ${base} · Plattformkonto: ${o.platformUser}`);
 const password = await ask('Passwort: ', true);
 let login = await call('/api/auth/login', 'POST', { username: o.platformUser, password });
 if (login.requiresTotpSetup) throw new Error('Zwei-Faktor-Anmeldung ist für dieses Konto noch nicht eingerichtet.');
 if (login.requiresTotp) login = await call('/api/auth/login/totp', 'POST', { code: (await ask('Code aus der Authenticator-App: ')).trim() });
 const session = await call('/api/platform/session'); csrf = session.csrfToken;
 const access = await call('/api/platform/access'); if (access.enabled !== true) throw new Error('Kein Plattformzugang.');
 const [memberships, choices] = await Promise.all([call('/api/platform/memberships'), call('/api/platform/invitations/choices')]);
 const { actions, problems } = plan(memberships, choices, o);
 problems.forEach(p => console.log('! ' + p));
 if (!actions.length) { console.log('Nichts zu tun.'); await call('/api/platform/logout', 'POST').catch(() => {}); return; }
 console.log('\nGeplante Änderungen:'); actions.forEach((a, i) => console.log(` ${i + 1}. ${describe(a)}`));
 if (problems.length) { console.log('\nBitte zuerst die Hinweise (!) klären; es wird nichts geändert.'); await call('/api/platform/logout', 'POST').catch(() => {}); process.exit(1); }
 if (!o.apply) { console.log('\nNur Vorschau. Mit --apply ausführen.'); await call('/api/platform/logout', 'POST').catch(() => {}); return; }
 if ((await ask('\nDiese Änderungen jetzt ausführen? (ja/nein): ')).trim().toLowerCase() !== 'ja') { console.log('Abgebrochen.'); await call('/api/platform/logout', 'POST').catch(() => {}); return; }
 for (const a of actions) {
  if (a.type === 'assign') await call('/api/platform/memberships', 'POST', { tenantId: a.tenantId, username: a.username, roleId: a.roleId });
  else if (a.type === 'role') await call(`/api/platform/memberships/${a.tenantId}/${a.userId}/role`, 'PUT', { roleId: a.roleId });
  else await call(`/api/platform/memberships/${a.tenantId}/${a.userId}/access`, 'PUT', { suspended: a.type === 'suspend' });
  console.log('✓ ' + describe(a));
 }
 const after = plan(await call('/api/platform/memberships'), choices, o);
 console.log(after.actions.length ? '\nNoch offen:\n' + after.actions.map(a => ' - ' + describe(a)).join('\n') : '\nFertig: die Verwaltung liegt bei ' + o.newAdmin + ', ' + o.platformUser + ' hat in diesen Mandanten keinen Zugang mehr.');
 await call('/api/platform/logout', 'POST').catch(() => {});
})().catch(e => { console.error('Fehler: ' + e.message); process.exit(1); });
