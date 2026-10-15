/* Evidence of rule pack findings carries the whole fact set of the document ("Prüfdaten {A=null, B=…}"), mostly empty fields of other document types.
   For display only the recorded values are shown; the stored text (part of the review fingerprint) stays unchanged. */
(() => {
 function readableEvidence(text) {
  const value = String(text ?? '');
  const start = value.indexOf('Prüfdaten {');
  if (start < 0) return value;
  let depth = 0, end = -1;
  for (let i = start + 'Prüfdaten '.length; i < value.length; i++) { if (value[i] === '{') depth++; else if (value[i] === '}' && --depth === 0) { end = i; break; } }
  if (end < 0) return value;
  const inner = value.slice(start + 'Prüfdaten {'.length, end);
  const entries = inner.split(/, (?=[A-Z][A-Z0-9_]+=)/).map(entry => entry.trim()).filter(entry => entry && !/=null$/.test(entry));
  const replacement = entries.length ? 'Erfasste Prüfdaten: ' + entries.join('; ') : 'keine weiteren Prüfdaten erfasst';
  return value.slice(0, start) + replacement + value.slice(end + 1);
 }
 globalThis.readableEvidence = readableEvidence;
})();
