/* For standalone pages without i18n.js/app.js: plain German texts with {name} placeholders. */
if (typeof globalThis.tx !== 'function') globalThis.tx = (key, de, values = {}) => String(de).replace(/\{(\w+)\}/g, (_, n) => String(values[n] ?? '{' + n + '}'));
