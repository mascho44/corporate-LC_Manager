/* Workflows in the dossier: progress of running and finished workflows, cancel, and a start form. */
(() => {
 const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
 const symbol = { DONE: '✓', ACTIVE: '●', PENDING: '○', CANCELLED: '×' };
 const label = { DONE: 'erledigt', ACTIVE: 'aktuell', PENDING: 'wartet', CANCELLED: 'abgebrochen' };
 const statusText = { RUNNING: 'läuft', DONE: 'abgeschlossen', CANCELLED: 'abgebrochen' };
 function stepHtml(step) {
  const who = step.state === 'DONE' && step.completedBy ? ` · ${escape(step.completedBy)}` : step.state === 'ACTIVE' ? (step.assignedTo ? ` · ${escape(step.assignedTo)}` : ' · in der Gruppeninbox') : '';
  const eyes = step.fourEyes ? ' <small>Vier-Augen</small>' : '';
  return `<li class="wf-step ${escape(String(step.state).toLowerCase())}"><span aria-hidden="true">${symbol[step.state] || '○'}</span> <span class="sr-only">${label[step.state] || ''}: </span>${escape(step.title)}${eyes}${who}</li>`;
 }
 function workflowHtml(w, canCancel) {
  const cancel = canCancel && w.status === 'RUNNING' ? `<button type="button" class="secondary" data-workflow-cancel="${escape(w.id)}">Abbrechen</button>` : '';
  return `<article class="wf"><header><b>${escape(w.templateTitle)}</b> · ${escape(statusText[w.status] || w.status)} · gestartet von ${escape(w.startedBy)}${cancel}</header><ol class="wf-steps">${(w.steps || []).map(stepHtml).join('')}</ol></article>`;
 }
 function panelHtml(list, canCancel) {
  if (!list || !list.length) return '';
  return `<section class="workflows"><h3>Workflows</h3>${list.map(w => workflowHtml(w, canCancel)).join('')}</section>`;
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
     if (typeof confirmAction === 'function' && !await confirmAction('Workflow abbrechen? Der offene Schritt wird entfernt, erledigte Schritte bleiben als Verlauf.')) return;
     try { await json(`/api/workflows/${encodeURIComponent(button.dataset.workflowCancel)}/cancel`, { method: 'POST' }); await show(id); }
     catch (error) { alert(error.message); }
    });
   } catch (ignored) { }
  };
 }
})();
