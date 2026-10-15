/* Plattformadministration: Tabs statt einer langen Seite.
   Übersicht (Kennzahlen, Hinweise) · Mandanten · Konten & Einladungen · Protokoll · Mein Konto (über den Kopf erreichbar). */
(() => {
 const TABS = [['overview', 'Übersicht'], ['tenants', 'Mandanten'], ['accounts', 'Konten & Einladungen'], ['log', 'Protokoll']];

 /** Kennzahlen und Hinweise aus den vorhandenen Listen; reine Funktion. */
 function summarize(tenants = [], accounts = [], invitations = []) {
  const live = tenants.filter(t => !t.archived);
  const hints = [];
  const noAdmin = live.filter(t => t.administrators === 0);
  if (noAdmin.length) hints.push({ tab: 'tenants', text: `${noAdmin.length} Mandant(en) ohne aktiven Administrator: ${noAdmin.map(t => t.code).join(', ')}` });
  const suspended = live.filter(t => !t.active);
  if (suspended.length) hints.push({ tab: 'tenants', text: `${suspended.length} Mandant(en) gesperrt: ${suspended.map(t => t.code).join(', ')}` });
  const failed = invitations.filter(i => ['MAIL_FAILED', 'MAIL_CANCELLED', 'EXPIRED'].includes(i.status));
  if (failed.length) hints.push({ tab: 'accounts', text: `${failed.length} Einladung(en) nicht zugestellt oder abgelaufen: ${failed.map(i => i.username).join(', ')}` });
  const noTotp = accounts.filter(a => a.active && !a.invitationPending && a.totpEnabled === false);
  if (noTotp.length) hints.push({ tab: 'accounts', text: `${noTotp.length} aktive(s) Konto/Konten ohne Zwei-Faktor-Anmeldung: ${noTotp.map(a => a.username).join(', ')}` });
  return {
   tiles: [
    { label: 'Mandanten aktiv', value: live.filter(t => t.active).length, detail: `${tenants.length - live.length} archiviert · ${suspended.length} gesperrt`, tab: 'tenants' },
    { label: 'Konten aktiv', value: accounts.filter(a => a.active && !a.invitationPending).length, detail: `${accounts.filter(a => !a.active).length} gesperrt`, tab: 'accounts' },
    { label: 'Offene Einladungen', value: invitations.length, detail: failed.length ? `${failed.length} mit Problem` : 'alle im Versand', tab: 'accounts' },
    { label: 'Plattformadministratoren', value: accounts.filter(a => a.platformAdministrator).length, detail: 'Zugriff auf alle Mandanten', tab: 'accounts' }
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
  head.append(node('p', 'Eine globale Kontosperre verhindert den Zugang in allen Mandanten. Die Aktivierung hebt lokale Sperren in einzelnen Mandanten nicht auf.'));
  if (reload) { reload.textContent = 'Alles neu laden'; head.append(reload); }
  const tabs = node('div', undefined, 'platform-tabs'); tabs.setAttribute('role', 'tablist');
  let active = 'overview';
  const paint = () => {
   [...tabs.children].forEach(b => { const on = b.dataset.tab === active; b.classList.toggle('active', on); b.setAttribute('aria-selected', String(on)); });
   Object.entries(panes).forEach(([key, pane]) => { pane.hidden = key !== active; });
  };
  const show = key => { if (!panes[key]) return; active = key; paint(); if (key === 'overview') renderOverview(); };
  TABS.forEach(([key, label]) => { const b = node('button', label, 'platform-tab'); b.type = 'button'; b.dataset.tab = key; b.setAttribute('role', 'tab'); b.onclick = () => show(key); tabs.append(b); });
  panes.account.prepend(node('h3', 'Mein Konto'));
  section.append(head, tabs, ...Object.values(panes));

  const myAccount = document.getElementById('platformMyAccount') || node('button', 'Mein Konto');
  myAccount.type = 'button'; myAccount.id = 'platformMyAccount'; myAccount.onclick = () => { active = 'account'; paint(); window.scrollTo(0, 0); };
  if (!myAccount.isConnected) document.getElementById('platformLogout')?.before(myAccount);

  const overview = panes.overview;
  let overviewGeneration = 0;
  async function renderOverview() {
   const generation = ++overviewGeneration;
   overview.replaceChildren(node('p', 'Lade …'));
   try {
    const [tenants, accounts, invitations] = await Promise.all([json('/api/platform/tenants'), json('/api/platform/users'), json('/api/platform/invitations')]);
    if (generation !== overviewGeneration) return;
    const { tiles, hints } = summarize(tenants, accounts, invitations);
    const grid = node('div', undefined, 'platform-tiles');
    tiles.forEach(t => { const tile = node('button', undefined, 'platform-tile'); tile.type = 'button'; tile.append(node('b', String(t.value)), node('span', t.label), node('small', t.detail)); tile.onclick = () => show(t.tab); grid.append(tile); });
    const list = node('div', undefined, 'platform-hints');
    if (!hints.length) list.append(node('p', 'Keine Auffälligkeiten.', 'platform-ok'));
    hints.forEach(h => { const row = node('article', undefined, 'membership-row'); row.append(node('div', h.text)); const go = node('button', 'Öffnen', 'secondary'); go.type = 'button'; go.onclick = () => show(h.tab); row.append(go); list.append(row); });
    const sanctions = node('div', undefined, 'platform-sanctions');
    overview.replaceChildren(node('h3', 'Überblick'), grid, node('h3', 'Hinweise'), list, node('h3', 'Sanktionslisten'), sanctions);
    renderSanctions(sanctions, list);
   } catch (error) { if (generation === overviewGeneration) overview.replaceChildren(node('p', error.message)); }
  }
  /** Status of the public sanctions lists (EU, UN, OFAC) with a manual refresh; a missing or stale list also shows up as a hint. */
  async function renderSanctions(box, hints) {
   let status;
   try { status = await json('/api/platform/sanctions'); } catch (error) { box.replaceChildren(node('p', 'Status nicht verfügbar: ' + error.message)); return; }
   const table = node('table', undefined, 'pu-table'); const head = node('tr'); ['Liste', 'Stand', 'Einträge', 'Hinweis'].forEach(h => head.append(node('th', h))); table.append(head);
   const stale = [];
   status.sources.forEach(s => {
    const tr = node('tr'); const age = s.fetchedAt ? (Date.now() - new Date(s.fetchedAt).getTime()) / 36e5 : Infinity;
    if (s.fetchedAt && age > 48) stale.push(s.source);
    [s.source, s.fetchedAt ? new Date(s.fetchedAt).toLocaleString('de-DE') : 'noch nicht geladen', s.entries ? s.entries.toLocaleString('de-DE') : '–', s.error || (!s.fetchedAt ? 'wird nach dem Start automatisch geladen' : age > 48 ? 'älter als 48 Stunden' : 'aktuell')].forEach(v => tr.append(node('td', v)));
    table.append(tr);
   });
   const refresh = node('button', status.refreshing ? 'Aktualisierung läuft …' : 'Jetzt aktualisieren', 'secondary'); refresh.type = 'button'; refresh.disabled = status.refreshing;
   const note = node('small', 'Öffentliche Listen von EU, UN und OFAC; automatischer Abruf täglich. Die Prüfung unterstützt die Sachbearbeitung und ersetzt kein zertifiziertes Screening-System.');
   refresh.onclick = async () => { refresh.disabled = true; refresh.textContent = 'Aktualisierung läuft …'; try { await json('/api/platform/sanctions/refresh', { method: 'POST' }); setTimeout(() => renderSanctions(box, hints), 15000); } catch (error) { refresh.textContent = 'Fehler: ' + error.message; } };
   box.replaceChildren(table, refresh, note);
   if (stale.length && hints) { hints.querySelector('.platform-ok')?.remove(); const row = node('article', undefined, 'membership-row'); row.append(node('div', `Sanktionslisten nicht aktuell: ${stale.join(', ')}`)); hints.append(row); }
  }
  paint(); renderOverview();
  globalThis.LcPlatformTabs.show = show; globalThis.LcPlatformTabs.refresh = renderOverview;
  return { show, panes };
 }

 globalThis.LcPlatformTabs = { summarize, paneOf, build };
})();
