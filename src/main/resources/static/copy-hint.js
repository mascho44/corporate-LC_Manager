/* Original/copy designation of an inbox document: what the person has entered or the split proposal carries wins; otherwise the stamp text found in the document
   (ORIGINAL, COPY, KOPIE, ...) is offered as the preselection. It is only a suggestion; the person confirms it with "Anhängen" and nothing is stored before. */
(() => {
 /** 0 = Original, 1 = Copy, null = nothing to suggest. */
 function defaultFor(item) {
  if (!item) return null;
  if (item.copyNumber !== null && item.copyNumber !== undefined && item.copyNumber !== '') return item.copyNumber;
  const kind = item.copyHint && item.copyHint.kind;
  return kind === 'ORIGINAL' ? 0 : kind === 'COPY' ? 1 : null;
 }
 /** Short note under the field when the preselection comes from the stamp text. */
 function note(item) {
  if (!item || (item.copyNumber !== null && item.copyNumber !== undefined && item.copyNumber !== '')) return '';
  const kind = item.copyHint && item.copyHint.kind;
  return kind === 'ORIGINAL' || kind === 'COPY' ? 'Vorbelegt aus dem Stempeltext (' + (kind === 'ORIGINAL' ? 'Original' : 'Copy') + ') – bitte bestätigen.' : '';
 }
 globalThis.LcCopyHint = { defaultFor, note };
})();
