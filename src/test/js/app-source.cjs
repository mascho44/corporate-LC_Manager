// The former app.js is split into parts (same global scope, loaded in this order); tests that read its source use the joined text.
const fs = require('node:fs'), path = require('node:path');
const PARTS = ['app-inbox', 'app-core', 'app-review-tools', 'app-audit-training', 'app-documents', 'app-admin', 'app-workspace'];
module.exports = () => PARTS.map(name => fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/' + name + '.js'), 'utf8')).join('\n');
module.exports.PARTS = PARTS;
