/* Workflows in the dossier: progress of running and finished workflows, cancel, and a start form. */
(() => {
 const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
 const symbol = { DONE: '✓', ACTIVE: '●', PENDING: '○', CANCELLED: '×' };
 const label = { DONE: 'erledigt', ACTIVE: 'aktuell', PENDING: 'wartet', CANCELLED: 'abgebrochen' };
 const statusText = () => ({ RUNNING: tx('wfx.running','läuft'), DONE: tx('wfx.done','abgeschlossen'), CANCELLED: tx('wfx.cancelled','abgebrochen') });
 function stepHtml(step) {
  const who = step.state === 'DONE' && step.completedBy ? ` · ${escape(step.completedBy)}` : step.state === 'ACTIVE' ? (step.assignedTo ? ` · ${escape(step.assignedTo)}` : tx('wfl.a1b8cb',' · in der Gruppeninbox')) : '';
  const eyes = step.fourEyes ? ' <small>'+tx('wfh.0010eb','Vier-Augen')+'</small>' : '';
  return `<li class="wf-step ${escape(String(step.state).toLowerCase())}"><span aria-hidden="true">${symbol[step.state] || '○'}</span> <span class="sr-only">${label[step.state] || ''}: </span>${escape(step.title)}${eyes}${who}</li>`;
 }
 function workflowHtml(w, canCancel) {
  const cancel = canCancel && w.status === 'RUNNING' ? `<button type="button" class="secondary" data-workflow-cancel="${escape(w.id)}">${tx('wfh.4b9727','Abbrechen')}</button>` : '';
  return tx('wft.8d1f85','<article class="wf"><header><b>{p0}</b> · {p1} · gestartet von {p2}{p3}</header><ol class="wf-steps">{p4}</ol></article>',{p0:escape(w.templateTitle),p1:escape(statusText()[w.status] || w.status),p2:escape(w.startedBy),p3:cancel,p4:(w.steps || []).map(stepHtml).join('')});
 }
 function panelHtml(list, canCancel) {
  if (!list || !list.length) return '';
  return `<section class="workflows"><h3>${tx('wfh.e6b088','Workflows')}</h3>${list.map(w => workflowHtml(w, canCancel)).join('')}</section>`;
 }
 globalThis.LcWorkflows = { escape, stepHtml, workflowHtml, panelHtml };
 if (typeof document === 'undefined' || !document.body) return;

 if (typeof show === 'function') {
  const showBase = show;
  show = async function (id) {
   await showBase(id);
   try {
    const detail = document.querySelector('#detail');
    const overview = detail?.querySelector('.dossier-section[data-section="overview"]');
    if (!overview) return;
    const list = await json(`/api/lcs/${encodeURIComponent(id)}/workflows`).catch(() => []);
    if (typeof activeLc === 'undefined' || activeLc?.id !== id) return;
    overview.querySelector('.workflows')?.remove();
    const html = panelHtml(list, typeof can === 'function' && can('LC_EDIT'));
    if (!html) return;
    overview.insertAdjacentHTML('beforeend', html);
    overview.querySelectorAll('[data-workflow-cancel]').forEach(button => button.onclick = async () => {
     if (typeof confirmAction === 'function' && !await confirmAction(tx('wfl.d73ab1','Workflow abbrechen? Der offene Schritt wird entfernt, erledigte Schritte bleiben als Verlauf.'))) return;
     try { await json(`/api/workflows/${encodeURIComponent(button.dataset.workflowCancel)}/cancel`, { method: 'POST' }); await show(id); }
     catch (error) { alert(error.message); }
    });
   } catch (ignored) { }
  };
 }
})();
