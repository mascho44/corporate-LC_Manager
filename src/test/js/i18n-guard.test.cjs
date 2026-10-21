const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs');
const dir = 'src/main/resources/static/';

/* Safety net for the multilingual rule (docs/mehrsprachigkeit.md): these scripts are fully translated, so they must not gain a plain German text.
   Add new scripts of new features to this list. The check is a heuristic (umlauts and common German words); it does not replace looking at the screen in English. */
const TRANSLATED = ['draft-check', 'amendment-request', 'document-set', 'submission-package', 'kpis', 'akte-copy', 'procurement-plan', 'lc-tools', 'corporate-profile', 'action-menus', 'global-search'];
const GERMAN = /[äöüÄÖÜß]|\b(und|der|die|das|zur|für|mit|nicht|noch|von|bei|eine?|im|aus|bitte|keine|wird|werden|kann|alle|neue|speichern|löschen|öffnen|schließen|prüfen|erstellen|bearbeiten|laden)\b/i;

function plainGermanTexts(source) {
 const withoutFallbacks = source.replace(/\b(?:tx|tr)\('[\w.]+'\s*(?:\+[^,]+)?,\s*'(?:[^'\\]|\\.)*'/g, 'tx_').replace(/\/\/[^\n]*/g, '').replace(/\/\*[\s\S]*?\*\//g, '');
 const found = [];
 for (const match of withoutFallbacks.matchAll(/(['"`])((?:\\.|(?!\1).){5,300})\1/g)) {
  const text = match[2].replace(/\$\{[^}]*\}|<[^>]+>/g, ' ').trim();
  if (GERMAN.test(text) && !/^(\/api|data-|#|\.)/.test(text) && !/^[\w.-]+$/.test(text)) found.push(text.slice(0, 80));
 }
 return found;
}

test('translated scripts contain no plain German text', () => {
 for (const name of TRANSLATED) assert.deepEqual(plainGermanTexts(fs.readFileSync(dir + name + '.js', 'utf8')), [], `${name}.js: deutscher Klartext ohne tx()/tr()`);
});

test('the check itself recognises a plain German text and ignores a translated one', () => {
 assert.equal(plainGermanTexts("el.textContent = 'Bitte eine Datei wählen.';").length, 1);
 assert.deepEqual(plainGermanTexts("el.textContent = tr('x.y', 'Bitte eine Datei wählen.');"), []);
 assert.deepEqual(plainGermanTexts("const url = '/api/lcs'; // Bitte nicht übersetzen"), []);
});

test('both language packs have the same keys and the same placeholders', () => {
 const en = JSON.parse(fs.readFileSync(dir + 'language-en.json', 'utf8')), de = JSON.parse(fs.readFileSync(dir + 'language-de.json', 'utf8'));
 assert.deepEqual(Object.keys(en).sort(), Object.keys(de).sort());
 const placeholders = text => [...String(text).matchAll(/\{(\w+)\}/g)].map(m => m[1]).sort().join(',');
 for (const key of Object.keys(en)) assert.equal(placeholders(en[key]), placeholders(de[key]), `Platzhalter von ${key}`);
});
