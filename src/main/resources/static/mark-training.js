/* Page "Stempel-Training": the decisions people made about stamps and signatures, per tenant, and (next stage) the learning built on them. Own menu entry, apart from the SWIFT training. */
(() => {
 const GOAL = 30; // decisions per kind that make a first model worthwhile
 const KINDS = ['STAMP', 'SIGNATURE', 'SIGNED_STAMP', 'NONE'];
 const kindLabel = () => ({ STAMP: tx('pm.kind.STAMP', 'Stempel'), SIGNATURE: tx('pm.kind.SIGNATURE', 'Unterschrift'), SIGNED_STAMP: tx('pm.kind.SIGNED_STAMP', 'Unterschriebener Stempel'), NONE: tx('pm.kind.NONE', 'Weder noch') });
 const node = (tag, text, css) => { const n = document.createElement(tag); if (text !== undefined) n.textContent = text; if (css) n.className = css; return n; };
 let section = null, body = null;

 function draw(summary) {
  body.replaceChildren();
  const labels = kindLabel();
  const counts = node('div', undefined, 'mt-counts');
  KINDS.forEach(k => {
   const n = (summary.perKind && summary.perKind[k]) || 0;
   const card = node('div', undefined, 'mt-count');
   card.append(node('b', String(n)), node('span', labels[k]));
   const bar = node('progress'); bar.max = GOAL; bar.value = Math.min(n, GOAL);
   bar.title = tx('mt.goal', 'Richtwert für ein erstes Modell: {n} Entscheidungen je Art', { n: GOAL });
   card.append(bar); counts.append(card);
  });
  body.append(counts);
  body.append(node('p', tx('mt.status', '{total} Entscheidungen in {docs} Dokumenten. Das Lernen aus diesen Entscheidungen folgt im nächsten Schritt; bis dahin werden sie gesammelt.', { total: summary.total, docs: summary.documents }), 'mt-status'));
  const list = node('div', undefined, 'mt-docs');
  if (!summary.recent || !summary.recent.length) list.append(node('p', tx('mt.empty', 'Noch keine Markierungen. Öffnen Sie in einer LC-Akte bei einem PDF „Stempel & Unterschriften“ und markieren Sie die Stellen.')));
  (summary.recent || []).forEach(d => {
   const row = node('div', undefined, 'mt-doc');
   row.append(node('span', d.filename), node('small', tx('mt.marks', '{n} Markierungen', { n: d.marks })));
   const open = node('button', tx('mt.open', 'Öffnen'), 'secondary'); open.type = 'button';
   open.onclick = () => globalThis.LcPageMarks && globalThis.LcPageMarks.open(d.documentId, d.filename);
   row.append(open); list.append(row);
  });
  body.append(list);
 }

 async function refresh() {
  try { draw(await json('/api/mark-training/summary')); }
  catch (error) { body.replaceChildren(node('p', error.message)); }
 }

 function setup() {
  if (typeof json !== 'function' || typeof appNavigate !== 'function' || section) return;
  const nav = node('button', tx('nav.markTraining', 'Stempel-Training')); nav.type = 'button'; nav.id = 'appNavMarkTraining';
  document.querySelector('#appNav nav')?.append(nav);
  section = document.createElement('section'); section.id = 'markTrainingSection'; section.className = 'panel hidden';
  section.append(node('h2', tx('mt.title', 'Stempel- und Unterschriften-Training')), node('p', tx('mt.intro', 'Hier sehen Sie, was das System über Stempel und Unterschriften Ihres Mandanten gelernt hat. Gelernt wird nur aus Ihren eigenen Entscheidungen; es verlassen keine Daten das System.')));
  body = node('div'); section.append(body);
  document.querySelector('main')?.append(section);
  nav.onclick = () => { appNavigate('markTraining', nav); return refresh(); };
  if (typeof globalThis.LcNav?.arrange === 'function') globalThis.LcNav.arrange();
 }
 const timer = setInterval(() => { if (typeof appNavigate === 'function' && typeof json === 'function') { clearInterval(timer); setup(); } }, 300);
})();
