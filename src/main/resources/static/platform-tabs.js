/* Plattformadministration: Tabs statt einer langen Seite.
   Übersicht (Kennzahlen, Hinweise) · Mandanten · Konten & Einladungen · Protokoll · Mein Konto (über den Kopf erreichbar). */
(() => {
 const TABS = (globalThis.lcLazy || (f => f()))(() => ([['overview', tx('ptl.5d1261','Übersicht')], ['tenants', tx('ptl.2f1127','Mandanten')], ['accounts', tx('ptl.a17085','Konten & Einladungen')], ['log', tx('ptl.d1fd5d','Protokoll')], ['serverlog', tx('sl.tab', 'Server-Log')]]));

 /** Kennzahlen und Hinweise aus den vorhandenen Listen; reine Funktion. */
 function summarize(tenants = [], accounts = [], invitations = []) {
  const live = tenants.filter(t => !t.archived);
  const hints = [];
  const noAdmin = live.filter(t => t.administrators === 0);
  if (noAdmin.length) hints.push({ tab: 'tenants', text: `${noAdmin.length} Mandant(en) ohne aktiven Administrator: ${noAdmin.map(t => t.code).join(', ')}` });
  const suspended = live.filter(t => !t.active);
  if (suspended.length) hints.push({ tab: 'tenants', text: `${suspended.length} Mandant(en) gesperrt: ${suspended.map(t => t.code).join(', ')}` });
  const failed = invitations.filter(i => ['MAIL_FAILED', 'MAIL_CANCELLED', 'EXPIRED'].includes(i.status));
  if (failed.length) hints.push({ tab: 'accounts', text: tx('ptt.cbb4b4','{p0} Einladung(en) nicht zugestellt oder abgelaufen: {p1}',{p0:failed.length,p1:failed.map(i => i.username).join(', ')}) });
  const noTotp = accounts.filter(a => a.active && !a.invitationPending && a.totpEnabled === false);
  if (noTotp.length) hints.push({ tab: 'accounts', text: `${noTotp.length} aktive(s) Konto/Konten ohne Zwei-Faktor-Anmeldung: ${noTotp.map(a => a.username).join(', ')}` });
  return {
   tiles: [
    { label: tx('ptl.cf9161','Mandanten aktiv'), value: live.filter(t => t.active).length, detail: `${tenants.length - live.length} archiviert · ${suspended.length} gesperrt`, tab: 'tenants' },
    { label: tx('ptl.78744a','Konten aktiv'), value: accounts.filter(a => a.active && !a.invitationPending).length, detail: `${accounts.filter(a => !a.active).length} gesperrt`, tab: 'accounts' },
    { label: tx('ptl.20b9dc','Offene Einladungen'), value: invitations.length, detail: failed.length ? tx('ptt.d90b7d','{p0} mit Problem',{p0:failed.length}) : tx('ptf.0','alle im Versand'), tab: 'accounts' },
    { label: tx('ptl.c7e29e','Plattformadministratoren'), value: accounts.filter(a => a.platformAdministrator).length, detail: tx('ptl.c9de19','Zugriff auf alle Mandanten'), tab: 'accounts' }
   ],
   hints
  };
 }

 /** Ordnet die Blöcke der Konsole den Tabs zu. Die Reihenfolge der Blöcke bestimmt, wo ein Wechsel stattfindet. */
 function paneOf(child, current) {
  const heading = (child.querySelector?.('h3') || (child.tagName === 'H3' ? child : null))?.textContent || '';
  if (child.id === 'platformPasswordForm') return 'account';
  if (child.id === 'platformDenials') return 'log';
  if (child.id === 'platformUsersSection') return 'accounts';
  if (child.tagName === 'FORM' && child.classList?.contains('form-grid') && /tenant|mandant/i.test(heading)) return 'tenants';
  if (child.tagName === 'SECTION' && /audit/i.test(heading)) return 'log';
  return current;
 }

 function build(section) {
  const node = (tag, text, cls) => { const n = document.createElement(tag); if (text !== undefined) n.textContent = text; if (cls) n.className = cls; return n; };
  const panes = {};
  [...TABS.map(t => t[0]), 'account'].forEach(key => { panes[key] = node('div', undefined, 'platform-pane'); panes[key].dataset.pane = key; });
  const reload = [...section.children].find(c => c.tagName === 'BUTTON');
  let current = 'accounts';
  [...section.children].forEach((child, index) => {
   if (child.tagName === 'H2' || (index === 1 && child.tagName === 'P') || child === reload) return;
   current = paneOf(child, current);
   panes[current].append(child);
  });
  section.replaceChildren();
  const head = node('div', undefined, 'platform-head');
  head.append(node('p', tx('ptl.732257','Eine globale Kontosperre verhindert den Zugang in allen Mandanten. Die Aktivierung hebt lokale Sperren in einzelnen Mandanten nicht auf.')));
  if (reload) { reload.textContent = tx('ptl.2df971','Alles neu laden'); head.append(reload); }
  const tabs = node('div', undefined, 'platform-tabs'); tabs.setAttribute('role', 'tablist');
  let active = 'overview';
  const paint = () => {
   [...tabs.children].forEach(b => { const on = b.dataset.tab === active; b.classList.toggle('active', on); b.setAttribute('aria-selected', String(on)); });
   Object.entries(panes).forEach(([key, pane]) => { pane.hidden = key !== active; });
  };
  const show = key => { if (!panes[key]) return; active = key; paint(); if (key === 'overview') renderOverview(); if (key === 'serverlog') globalThis.LcServerLog?.render?.(panes.serverlog); };
  TABS.forEach(([key, label]) => { const b = node('button', label, 'platform-tab'); b.type = 'button'; b.dataset.tab = key; b.setAttribute('role', 'tab'); b.onclick = () => show(key); tabs.append(b); });
  panes.account.prepend(node('h3', tx('ptl.c0192b','Mein Konto')));
  section.append(head, tabs, ...Object.values(panes));

  const myAccount = document.getElementById('platformMyAccount') || node('button', tx('ptl.c0192b','Mein Konto'));
  myAccount.type = 'button'; myAccount.id = 'platformMyAccount'; myAccount.onclick = () => { active = 'account'; paint(); window.scrollTo(0, 0); };
  if (!myAccount.isConnected) document.getElementById('platformLogout')?.before(myAccount);

  const overview = panes.overview;
  let overviewGeneration = 0;
  async function renderOverview() {
   const generation = ++overviewGeneration;
   overview.replaceChildren(node('p', tx('ptl.b1d2e3','Lade …')));
   try {
    const [tenants, accounts, invitations] = await Promise.all([json('/api/platform/tenants'), json('/api/platform/users'), json('/api/platform/invitations')]);
    if (generation !== overviewGeneration) return;
    const { tiles, hints } = summarize(tenants, accounts, invitations);
    const grid = node('div', undefined, 'platform-tiles');
    tiles.forEach(t => { const tile = node('button', undefined, 'platform-tile'); tile.type = 'button'; tile.append(node('b', String(t.value)), node('span', t.label), node('small', t.detail)); tile.onclick = () => show(t.tab); grid.append(tile); });
    const list = node('div', undefined, 'platform-hints');
    if (!hints.length) list.append(node('p', tx('ptl.c67425','Keine Auffälligkeiten.'), 'platform-ok'));
    hints.forEach(h => { const row = node('article', undefined, 'membership-row'); row.append(node('div', h.text)); const go = node('button', tx('ptl.9889e0','Öffnen'), 'secondary'); go.type = 'button'; go.onclick = () => show(h.tab); row.append(go); list.append(row); });
    const sanctions = node('div', undefined, 'platform-sanctions');
    overview.replaceChildren(node('h3', tx('ptl.fd3576','Überblick')), grid, node('h3', tx('ptl.9fdbbc','Hinweise')), list, node('h3', tx('ptl.a5bfee','Sanktionslisten')), sanctions);
    renderSanctions(sanctions, list);
   } catch (error) { if (generation === overviewGeneration) overview.replaceChildren(node('p', error.message)); }
  }
  /** Status of the public sanctions lists (EU, UN, OFAC) with a manual refresh; a missing or stale list also shows up as a hint. */
  async function renderSanctions(box, hints) {
   let status;
   try { status = await json('/api/platform/sanctions'); } catch (error) { box.replaceChildren(node('p', tx('ptl.261d99','Status nicht verfügbar: ') + error.message)); return; }
   const table = node('table', undefined, 'pu-table'); const head = node('tr'); ['Liste', tx('ptl.f5e531','Stand'), tx('ptl.7a6305','Einträge'), tx('ptl.987451','Hinweis')].forEach(h => head.append(node('th', h))); table.append(head);
   const stale = [];
   status.sources.forEach(s => {
    const tr = node('tr'); const age = s.fetchedAt ? (Date.now() - new Date(s.fetchedAt).getTime()) / 36e5 : Infinity;
    if (s.fetchedAt && age > 48) stale.push(s.source);
    [s.source, s.fetchedAt ? new Date(s.fetchedAt).toLocaleString('de-DE') : tx('ptf.1','noch nicht geladen'), s.entries ? s.entries.toLocaleString('de-DE') : '–', s.error || (!s.fetchedAt ? 'wird nach dem Start automatisch geladen' : age > 48 ? 'älter als 48 Stunden' : 'aktuell')].forEach(v => tr.append(node('td', v)));
    table.append(tr);
   });
   const refresh = node('button', status.refreshing ? tx('ptl.4245cc','Aktualisierung läuft …') : tx('ptl.c2aff3','Jetzt aktualisieren'), 'secondary'); refresh.type = 'button'; refresh.disabled = status.refreshing;
   const note = node('small', tx('ptl.0ba221','Öffentliche Listen von EU, UN und OFAC; automatischer Abruf täglich. Die Prüfung unterstützt die Sachbearbeitung und ersetzt kein zertifiziertes Screening-System.'));
   refresh.onclick = async () => { refresh.disabled = true; refresh.textContent = tx('ptl.4245cc','Aktualisierung läuft …'); try { await json('/api/platform/sanctions/refresh', { method: 'POST' }); setTimeout(() => renderSanctions(box, hints), 15000); } catch (error) { refresh.textContent = tx('ptl.19e8b1','Fehler: ') + error.message; } };
   box.replaceChildren(table, refresh, note);
   if (stale.length && hints) { hints.querySelector('.platform-ok')?.remove(); const row = node('article', undefined, 'membership-row'); row.append(node('div', tx('ptg.b91bee','Sanktionslisten nicht aktuell: {p0}',{p0:stale.join(', ')}))); hints.append(row); }
  }
  paint(); renderOverview();
  globalThis.LcPlatformTabs.show = show; globalThis.LcPlatformTabs.refresh = renderOverview;
  return { show, panes };
 }

 globalThis.LcPlatformTabs = { summarize, paneOf, build };
})();
