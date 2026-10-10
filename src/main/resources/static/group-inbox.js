/* Group inbox: tasks handed to teams. Members claim a task, hand it back or complete it; user managers maintain the teams. */
(() => {
 /** Splits inbox items into what can be claimed, what the user is working on and what colleagues work on. */
 function group(items, me) {
  const name = String(me || '').toLowerCase(), result = { open: [], mine: [], others: [] };
  (items || []).forEach(item => {
   const owner = String(item.assignedTo || '').toLowerCase();
   (!owner ? result.open : owner === name ? result.mine : result.others).push(item);
  });
  return result;
 }
 globalThis.LcGroupInbox = { group };
 if (typeof document === 'undefined' || !document.body) return;

 const node = (tag, text, className) => { const n = document.createElement(tag); if (text !== undefined) n.textContent = text; if (className) n.className = className; return n; };
 const fmt = value => { try { return value ? new Intl.DateTimeFormat('de-DE').format(new Date(value)) : ''; } catch (ignored) { return String(value || ''); } };
 let nav = null, section = null, message = null, busy = false, timer = null;

 async function setup() {
  if (typeof can !== 'function' || typeof json !== 'function' || section) return;
  let mine = [];
  try { mine = await json('/api/teams/mine'); } catch (ignored) { return; }
  const manager = can('USER_MANAGE');
  if (!mine.length && !manager) return;
  nav = node('button', 'Gruppeninbox'); nav.type = 'button'; nav.id = 'appNavGroupInbox';
  document.querySelector('#appNav nav')?.append(nav);
  section = document.createElement('section'); section.id = 'groupInboxSection'; section.className = 'panel hidden';
  message = node('p', ''); message.setAttribute('role', 'status');
  section.append(node('h2', 'Gruppeninbox'), message);
  document.querySelector('main')?.append(section);
  nav.onclick = () => { appNavigate('groupInbox', nav); return refresh(); };
  badge(); timer = setInterval(() => { if (!document.hidden) badge(); }, 60000);
 }

 async function badge() {
  try { const items = await json('/api/tasks/inbox'); const open = group(items, currentUser?.username).open.length; nav.textContent = 'Gruppeninbox' + (open ? ` (${open})` : ''); } catch (ignored) { }
 }

 async function act(task, done) {
  if (busy) return; busy = true; message.textContent = '';
  try { await task(); message.textContent = done; } catch (error) { message.textContent = error.message; }
  finally { busy = false; await refresh(true); }
 }

 function row(item, kind) {
  const r = node('article', undefined, 'membership-row team-task-row');
  const title = node('b', item.title);
  const meta = node('span', ` · ${item.lcReference || 'ohne Akte'} · ${item.teamName}${item.dueDate ? ' · fällig ' + fmt(item.dueDate) : ''}${kind === 'others' ? ' · bei ' + item.assignedTo : ''}`);
  r.append(title, meta);
  const button = (label, handler, css) => { const b = node('button', label); b.type = 'button'; b.className = css || 'secondary'; b.onclick = handler; r.append(b); };
  if (kind === 'open') button('Übernehmen', () => act(() => json(`/api/tasks/${item.taskId}/claim`, { method: 'POST' }), 'Auftrag übernommen.'), 'primary');
  if (kind === 'mine') {
   button('Erledigt', () => act(() => json(`/api/lcs/${item.lcId}/tasks/${item.taskId}/complete?completed=true`, { method: 'PUT' }), 'Auftrag erledigt.'), 'primary');
   button('Zurückgeben', () => act(() => json(`/api/tasks/${item.taskId}/release`, { method: 'POST' }), 'Auftrag zurück in die Gruppeninbox gelegt.'));
  }
  return r;
 }

 function list(title, items, kind, empty) {
  const box = node('div'); box.append(node('h3', `${title} (${items.length})`));
  if (!items.length) box.append(node('p', empty)); else items.forEach(item => box.append(row(item, kind)));
  return box;
 }

 function newTaskForm(teams) {
  const form = document.createElement('form'); form.className = 'form-grid';
  const lc = document.createElement('select'); lc.name = 'lc'; lc.required = true;
  (typeof data !== 'undefined' ? data : []).filter(x => !['CLOSED', 'EXPIRED'].includes(x.status)).forEach(x => { const o = node('option', x.reference); o.value = x.id; lc.append(o); });
  const team = document.createElement('select'); team.name = 'team'; team.required = true;
  teams.filter(t => t.active).forEach(t => { const o = node('option', t.name); o.value = t.id; team.append(o); });
  const title = document.createElement('input'); title.name = 'title'; title.required = true; title.maxLength = 500; title.placeholder = 'Was ist zu tun?';
  const due = document.createElement('input'); due.name = 'due'; due.type = 'date';
  const labeled = (text, input) => { const l = node('label', text); l.append(input); return l; };
  const submit = node('button', 'Auftrag an Team anlegen'); submit.type = 'submit';
  form.append(labeled('Akte', lc), labeled('Team', team), labeled('Auftrag', title), labeled('Fällig am', due), submit);
  form.onsubmit = event => { event.preventDefault(); return act(() => json(`/api/lcs/${lc.value}/tasks`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ title: title.value.trim(), dueDate: due.value || null, teamId: team.value }) }), 'Auftrag angelegt.'); };
  return form;
 }

 async function teamAdmin() {
  const box = node('div'); box.append(node('h3', 'Teams verwalten'));
  const [teams, people] = await Promise.all([json('/api/teams'), json('/api/users/assignable').catch(() => [])]);
  const editor = team => {
   const form = document.createElement('form'); form.className = 'form-grid';
   const name = document.createElement('input'); name.name = 'name'; name.required = true; name.maxLength = 100; name.value = team ? team.name : '';
   const description = document.createElement('input'); description.name = 'description'; description.maxLength = 300; description.value = team?.description || '';
   const members = document.createElement('fieldset'); members.append(node('legend', 'Mitglieder'));
   people.forEach(p => { const l = node('label', ` ${p.displayName || p.username}`); const c = document.createElement('input'); c.type = 'checkbox'; c.value = p.username; c.checked = Boolean(team?.members?.some(m => m.toLowerCase() === p.username.toLowerCase())); l.prepend(c); members.append(l); });
   const active = document.createElement('input'); active.type = 'checkbox'; active.checked = team ? team.active : true;
   const activeLabel = node('label', ' Aktiv'); activeLabel.prepend(active);
   const submit = node('button', team ? 'Speichern' : 'Team anlegen'); submit.type = 'submit';
   const wrap = (text, input) => { const l = node('label', text); l.append(input); return l; };
   form.append(wrap('Name', name), wrap('Beschreibung', description), members, activeLabel, submit);
   form.onsubmit = event => { event.preventDefault(); const body = JSON.stringify({ name: name.value.trim(), description: description.value.trim(), members: [...members.querySelectorAll('input:checked')].map(c => c.value), active: active.checked });
    return act(() => json(team ? `/api/teams/${team.id}` : '/api/teams', { method: team ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' }, body }), team ? 'Team gespeichert.' : 'Team angelegt.'); };
   return form;
  };
  teams.forEach(team => { const d = document.createElement('details'); d.append(node('summary', `${team.name}${team.active ? '' : ' (inaktiv)'} · ${team.members.length} Mitglieder`), editor(team)); box.append(d); });
  const create = document.createElement('details'); create.append(node('summary', 'Neues Team'), editor(null)); box.append(create);
  return box;
 }

 async function refresh(keepMessage) {
  if (!section) return;
  const text = message.textContent;
  try {
   const [items, mine] = await Promise.all([json('/api/tasks/inbox'), json('/api/teams/mine')]);
   const parts = group(items, currentUser?.username);
   section.replaceChildren(node('h2', 'Gruppeninbox'), message);
   message.textContent = keepMessage ? text : '';
   if (mine.length) {
    section.append(list('Zur Übernahme', parts.open, 'open', 'Nichts zu übernehmen.'), list('In meiner Bearbeitung', parts.mine, 'mine', 'Keine übernommenen Aufträge.'), list('Bei Kolleginnen und Kollegen', parts.others, 'others', 'Keine.'));
   } else section.append(node('p', 'Sie gehören keinem Team an.'));
   const teams = can('USER_MANAGE') ? await json('/api/teams') : mine;
   if (teams.some(t => t.active) && typeof data !== 'undefined' && data.length) { section.append(node('h3', 'Neuer Teamauftrag'), newTaskForm(teams)); }
   if (can('USER_MANAGE')) section.append(await teamAdmin());
   badge();
  } catch (error) { message.textContent = error.message; }
 }

 const wait = setInterval(() => { if (typeof currentUser !== 'undefined' && currentUser && document.querySelector('#appNav nav') && typeof appNavigate === 'function') { clearInterval(wait); setup(); } }, 500);
})();
