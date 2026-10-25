const test = require('node:test'), assert = require('node:assert');
const vm = require('node:vm'), fs = require('node:fs'), path = require('node:path');
const ctx = {tx:(key,de,values={})=>String(de).replace(/\{(\w+)\}/g,(m,k)=>values[k]??m)}; ctx.globalThis = ctx; vm.createContext(ctx);
vm.runInContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static/intake-channels.js'), 'utf8'), ctx);
const c = ctx.LcIntakeChannels;
test('a channel line names type, state and the last run', () => {
 assert.match(c.rowText({ type: 'FOLDER', name: 'poststelle', enabled: true, intervalMinutes: 2, lastRunAt: null }), /Ordner \(Scan to Folder\) · poststelle · an, alle 2 Min\. · noch nie gelaufen/);
 const text = c.rowText({ type: 'FOLDER', name: 'scanner-1', enabled: false, intervalMinutes: 5, lastRunAt: '2026-10-17T10:00:00', lastResult: '2 neu, 0 bereits vorhanden, 0 fehlgeschlagen' });
 assert.match(text, /scanner-1 · aus · zuletzt .*: 2 neu/);
 assert.strictEqual(c.failed({ lastResult: 'Fehler: Ordner nicht lesbar' }), true);
 assert.strictEqual(c.failed({ lastResult: '1 neu' }), false);
 assert.strictEqual(c.failed({}), false);
});
test('a mailbox line shows server, folder, senders and never the password; the request leaves an empty password out', () => {
 const channel = { type: 'EMAIL', name: 'post', enabled: false, intervalMinutes: 2, mail: { host: 'imap.kunde.example', port: 993, security: 'SSL', username: 'poststelle', folder: 'INBOX', allowedSenders: ['@kunde.example', 'a@b.example'], passwordSet: true } };
 assert.match(c.rowText(channel), /E-Mail-Postfach \(IMAP\) · post · aus/);
 const line = c.mailText(channel);
 assert.match(line, /poststelle@imap\.kunde\.example:993 \(SSL\) · Ordner INBOX · Absender: @kunde\.example, a@b\.example · Kennwort hinterlegt/);
 assert.match(c.mailText({ ...channel, mail: { ...channel.mail, allowedSenders: [], passwordSet: false } }), /keine freigegeben · Kennwort fehlt/);
 assert.strictEqual(c.mailText({ type: 'FOLDER' }), null);
 const withPassword = c.mailRequest({ host: ' h.example ', port: '', security: 'STARTTLS', username: ' u ', password: 'geheim', folder: '', processedFolder: '', rejectedFolder: 'Abgelehnt', senders: 'a@x.example, @y.example;\n\n b@z.example' });
 assert.deepStrictEqual(JSON.parse(JSON.stringify(withPassword)), { host: 'h.example', security: 'STARTTLS', username: 'u', allowedSenders: ['a@x.example', '@y.example', 'b@z.example'], folder: null, processedFolder: null, rejectedFolder: 'Abgelehnt', password: 'geheim' });
 assert.strictEqual('password' in c.mailRequest({ host: 'h', port: '143', security: 'SSL', username: 'u', password: '', folder: 'INBOX', processedFolder: 'v', rejectedFolder: 'a', senders: '' }), false);
 assert.strictEqual(c.mailRequest({ host: 'h', port: '143', security: 'SSL', username: 'u', password: '', folder: '', processedFolder: '', rejectedFolder: '', senders: '' }).port, 143);
});
