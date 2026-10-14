/* Settings box "Gelernte Dokumentarten": the confirmed examples the recognition has learned from (split dialog and assignment), reviewable and removable one by one. */
(() => {
 const node = (tag, text, css) => { const n = document.createElement(tag); if (text !== undefined) n.textContent = text; if (css) n.className = css; return n; };
 const stamp = value => value ? new Date(value).toLocaleDateString('de-DE') : '';
 /** One line per learned example: where it started and who confirmed it when. */
 function rowText(example) {
  const start = (example.sample || '').trim();
  return `${start ? '„' + start + '“' : '(ohne Textauszug)'} · bestätigt von ${example.confirmedBy || '?'}${example.confirmedAt ? ' am ' + stamp(example.confirmedAt) : ''}`;
 }
 /** Examples grouped by document type, in the order the server sent them. */
 function groupByType(examples) {
  const groups = new Map();
  (examples || []).forEach(e => { if (!groups.has(e.type)) groups.set(e.type, { type: e.type, name: e.typeName || e.type, items: [] }); groups.get(e.type).items.push(e); });
  return [...groups.values()];
 }
 globalThis.LcDocTypeTraining = { rowText, groupByType };
 if (typeof document === 'undefined' || !document.body) return;

 async function build(host) {
  const box = node('section', undefined, 'settings-box'); box.id = 'docTypeTrainingBox';
  const message = node('p', '', 'settings-status'); message.setAttribute('role', 'status');
  const list = node('div', undefined, 'settings-list');
  box.append(node('h3', 'Gelernte Dokumentarten'),
   node('p', 'Wenn beim Aufteilen oder Zuordnen eine Dokumentart bestätigt wird, merkt sich das System die typischen Wörter des Dokuments. Ähnliche Dokumente, deren Art sonst nicht erkannt würde (z. B. ein Wechsel oder ein anders aussehendes Dokument), werden danach als Vorschlag „aus Training" angeboten und müssen weiter geprüft werden. Falsch gelernte Beispiele können hier entfernt werden.'),
   message, list);
  host.append(box);
  const render = examples => {
   list.replaceChildren();
   const groups = groupByType(examples);
   if (!groups.length) { list.append(node('p', 'Noch nichts gelernt. Es wird beim Bestätigen von Aufteilungen und Zuordnungen von selbst gesammelt.')); return; }
   groups.forEach(group => {
    list.append(node('h4', `${group.name} (${group.items.length})`));
    group.items.forEach(example => {
     const row = node('div', undefined, 'settings-row'); row.append(node('span', rowText(example)));
     const remove = node('button', 'Entfernen', 'danger'); remove.type = 'button';
     remove.onclick = async () => {
      if (typeof confirmAction === 'function' && !await confirmAction('Dieses gelernte Beispiel entfernen? Die Erkennung nutzt es danach nicht mehr.')) return;
      try { await json(`/api/document-type-training/${encodeURIComponent(example.id)}`, { method: 'DELETE' }); render(await json('/api/document-type-training')); message.textContent = 'Beispiel entfernt.'; }
      catch (error) { message.textContent = error.message; }
     };
     row.append(remove); list.append(row);
    });
   });
  };
  try { render(await json('/api/document-type-training')); } catch (error) { message.textContent = error.message; }
 }
 const wait = setInterval(() => {
  const host = document.querySelector('#tenantSection');
  if (host && typeof can === 'function' && typeof currentUser !== 'undefined' && currentUser) {
   clearInterval(wait);
   if (can('SETTINGS_MANAGE') && !host.querySelector('#docTypeTrainingBox')) build(host);
  }
 }, 500);
})();
