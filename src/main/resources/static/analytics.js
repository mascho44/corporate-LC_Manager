/* Cockpit charts: open files over time, arrivals and closings, deadline load ahead and, for banks, discrepancies and service levels.
   Plain inline SVG without libraries; the numbers come from GET /api/analytics. Presentation only. */
(() => {
 const tr = (key, de, values) => typeof tx === 'function' ? tx(key, de, values) : String(de).replace(/\{(\w+)\}/g, (_, n) => (values || {})[n]);
 const NS = 'http://www.w3.org/2000/svg';
 const svg = (tag, attrs = {}, ...kids) => { const n = document.createElementNS(NS, tag); Object.entries(attrs).forEach(([k, v]) => n.setAttribute(k, v)); kids.forEach(k => k && n.append(k)); return n; };
 const html = (tag, cls, text) => { const n = document.createElement(tag); if (cls) n.className = cls; if (text !== undefined) n.textContent = text; return n; };
 const COLORS = { blue: 'var(--an-blue,#3156a3)', teal: 'var(--an-teal,#14a38b)', amber: 'var(--an-amber,#d99a1e)', red: 'var(--an-red,#d1495b)', violet: 'var(--an-violet,#7a5cc7)', slate: 'var(--an-slate,#8896ab)' };
 const W = 640, H = 230, PAD = { l: 44, r: 14, t: 14, b: 30 };
 const IW = W - PAD.l - PAD.r, IH = H - PAD.t - PAD.b;

 /** Round a maximum up to 1, 2, 5 or 10 times a power of ten, so the axis ends on a tidy number. */
 function niceMax(v) { if (!(v > 0)) return 1; const p = Math.pow(10, Math.floor(Math.log10(v))), f = v / p; return (f <= 1 ? 1 : f <= 2 ? 2 : f <= 5 ? 5 : 10) * p; }
 /** A tidy axis for values up to v: steps of 1, 2 or 5 times a power of ten, four to six steps in all, so every tick is a whole number when the values are. */
 function niceScale(v) {
  if (!(v > 0)) return { max: 4, ticks: 4 };
  const raw = v / 4, p = Math.pow(10, Math.floor(Math.log10(raw))), f = raw / p, step = (f <= 1 ? 1 : f <= 2 ? 2 : f <= 5 ? 5 : 10) * p;
  const ticks = Math.max(2, Math.ceil(v / step - 1e-9));
  return { max: step * ticks, ticks, step };
 }
 /** Smooth curve through the points that never swings past them (monotone cubic interpolation turned into Beziers): a flat stretch stays flat and the line never dips below a low point. */
 function smooth(pts) {
  const n = pts.length;
  if (n < 2) return n ? `M${pts[0][0]},${pts[0][1]}` : '';
  const dx = [], slope = [];
  for (let i = 0; i < n - 1; i++) { dx.push(pts[i + 1][0] - pts[i][0]); slope.push((pts[i + 1][1] - pts[i][1]) / dx[i]); }
  const m = [slope[0]];
  for (let i = 1; i < n - 1; i++) m.push(slope[i - 1] * slope[i] <= 0 ? 0 : (slope[i - 1] + slope[i]) / 2);
  m.push(slope[n - 2]);
  for (let i = 0; i < n - 1; i++) {
   if (slope[i] === 0) { m[i] = 0; m[i + 1] = 0; continue; }
   const a = m[i] / slope[i], b = m[i + 1] / slope[i], h = Math.hypot(a, b);
   if (h > 3) { m[i] = 3 * a / h * slope[i]; m[i + 1] = 3 * b / h * slope[i]; }
  }
  let d = `M${pts[0][0]},${pts[0][1]}`;
  for (let i = 0; i < n - 1; i++) d += ` C${pts[i][0] + dx[i] / 3},${pts[i][1] + m[i] * dx[i] / 3} ${pts[i + 1][0] - dx[i] / 3},${pts[i + 1][1] - m[i + 1] * dx[i] / 3} ${pts[i + 1][0]},${pts[i + 1][1]}`;
  return d;
 }
 const fmtInt = n => Number(n).toLocaleString(tr('gv.locale', 'de-DE'));
 const day = value => typeof fmtDate === 'function' ? fmtDate(value) : value;
 function isoWeek(dateText) { const d = new Date(dateText + 'T00:00:00Z'); d.setUTCDate(d.getUTCDate() + 4 - (d.getUTCDay() || 7)); const y0 = new Date(Date.UTC(d.getUTCFullYear(), 0, 1)); return Math.ceil(((d - y0) / 86400000 + 1) / 7); }
 const MONTHS = () => tr('an.months', 'Jan,Feb,Mär,Apr,Mai,Jun,Jul,Aug,Sep,Okt,Nov,Dez').split(',');
 function labelOf(bucket, weekly) { return weekly ? tr('an.week', 'KW {n}', { n: isoWeek(bucket.start) }) : MONTHS()[Number(bucket.start.slice(5, 7)) - 1] + ' ' + bucket.start.slice(2, 4); }
 function rangeOf(bucket, weekly) { return weekly ? day(bucket.start) + ' – ' + day(bucket.end) : labelOf(bucket, false); }

 /** Shared frame: axes, grid, hover layer with tooltip. draw(ctx) adds the marks and returns nothing; rows(i) gives the tooltip lines of bucket i. */
 function frame(host, { buckets, weekly, max, ticks = 4, format = fmtInt, rows, label, draw, rightAxis }) {
  host.replaceChildren();
  const root = svg('svg', { viewBox: `0 0 ${W} ${H}`, class: 'an-svg', role: 'img', 'aria-label': label });
  const n = buckets.length, step = n > 1 ? IW / (n - 1) : 0, slot = IW / n;
  const x = i => PAD.l + (draw.mode === 'columns' ? slot * (i + 0.5) : step * i);
  const y = v => PAD.t + IH - (Math.min(v, max) / max) * IH;
  for (let t = 0; t <= ticks; t++) {
   const v = max * t / ticks, yy = y(v);
   root.append(svg('line', { x1: PAD.l, x2: W - PAD.r, y1: yy, y2: yy, class: 'an-grid' }), Object.assign(svg('text', { x: PAD.l - 8, y: yy + 4, class: 'an-axis', 'text-anchor': 'end' }), { textContent: format(v) }));
   if (rightAxis) root.append(Object.assign(svg('text', { x: W - PAD.r + 2, y: yy + 4, class: 'an-axis', 'text-anchor': 'start' }), { textContent: rightAxis(t / ticks) }));
  }
  const every = Math.max(1, Math.ceil(n / 8));
  buckets.forEach((b, i) => { if (i % every === 0 || i === n - 1) root.append(Object.assign(svg('text', { x: x(i), y: H - 8, class: 'an-axis', 'text-anchor': 'middle' }), { textContent: labelOf(b, weekly) })); });
  draw({ root, x, y, n, slot, step });
  const cross = svg('line', { y1: PAD.t, y2: PAD.t + IH, class: 'an-cross', visibility: 'hidden' });
  const over = svg('rect', { x: PAD.l, y: PAD.t, width: IW, height: IH, fill: 'transparent', class: 'an-over' });
  root.append(cross, over); host.append(root);
  const tip = html('div', 'an-tip'); tip.hidden = true; host.append(tip);
  const show = event => {
   const box = root.getBoundingClientRect(), px = (event.clientX - box.left) / box.width * W;
   let i = draw.mode === 'columns' ? Math.floor((px - PAD.l) / slot) : Math.round((px - PAD.l) / (step || 1));
   i = Math.max(0, Math.min(n - 1, i)); const cx = x(i);
   cross.setAttribute('x1', cx); cross.setAttribute('x2', cx); cross.setAttribute('visibility', 'visible');
   tip.replaceChildren(html('b', '', rangeOf(buckets[i], weekly)), ...rows(i).map(r => { const line = html('div', 'an-tip-row'); const dot = html('span', 'an-dot'); dot.style.background = r.color; line.append(dot, html('span', '', r.name + ': ' + r.value)); return line; }));
   tip.hidden = false; const hb = host.getBoundingClientRect(), left = cx / W * box.width + (box.left - hb.left);
   tip.style.left = Math.min(Math.max(left + 12, 0), hb.width - tip.offsetWidth - 4) + 'px'; tip.style.top = '8px';
  };
  over.addEventListener('pointermove', show); over.addEventListener('pointerdown', show);
  over.addEventListener('pointerleave', () => { cross.setAttribute('visibility', 'hidden'); tip.hidden = true; });
  return root;
 }

 let gradientCount = 0;
 /** Area chart of one value per bucket (a smooth line over a soft gradient). */
 function areaChart(host, { buckets, weekly, values, name, color, label, format }) {
  const scale = niceScale(Math.max(...values, 1)), max = scale.max;
  const id = 'an-g' + (++gradientCount);
  const draw = ({ root, x, y, n }) => {
   const pts = values.map((v, i) => [x(i), y(v)]);
   const line = smooth(pts), base = PAD.t + IH;
   root.append(svg('defs', {}, svg('linearGradient', { id, x1: 0, y1: 0, x2: 0, y2: 1 }, svg('stop', { offset: '0%', 'stop-color': color, 'stop-opacity': '.38' }), svg('stop', { offset: '100%', 'stop-color': color, 'stop-opacity': '.02' }))));
   if (n > 1) root.append(svg('path', { d: `${line} L${pts[n - 1][0]},${base} L${pts[0][0]},${base} Z`, fill: `url(#${id})`, class: 'an-area' }));
   root.append(svg('path', { d: line, fill: 'none', stroke: color, 'stroke-width': 2.4, 'stroke-linecap': 'round', class: 'an-line', pathLength: 1 }));
   pts.forEach(p => root.append(svg('circle', { cx: p[0], cy: p[1], r: n > 30 ? 0 : 3, fill: 'var(--an-surface,#fff)', stroke: color, 'stroke-width': 2 })));
  };
  draw.mode = 'line';
  frame(host, { buckets, weekly, max, ticks: scale.ticks, label, format: format || fmtInt, draw, rows: i => [{ name, value: (format || fmtInt)(values[i]), color }] });
 }

 /** Columns per bucket: grouped (side by side) or stacked; series = [{name, values, color}]. */
 function columnChart(host, { buckets, weekly, series, stacked, label, line }) {
  const totals = buckets.map((_, i) => stacked ? series.reduce((s, x) => s + x.values[i], 0) : Math.max(...series.map(x => x.values[i])));
  const scale = line ? { max: 100, ticks: 4 } : niceScale(Math.max(...totals, 1)), max = scale.max;
  const draw = ({ root, x, y, n, slot }) => {
   const group = Math.min(slot * 0.72, 46), bar = stacked ? group : group / series.length;
   buckets.forEach((_, i) => {
    let acc = 0;
    series.forEach((s, k) => {
     const v = s.values[i]; if (!v) return;
     const h = (v / max) * IH, bx = x(i) - group / 2 + (stacked ? 0 : k * bar), by = stacked ? PAD.t + IH - ((acc + v) / max) * IH : PAD.t + IH - h;
     const r = svg('rect', { x: bx + (stacked ? 0 : 1), y: by, width: Math.max(2, bar - (stacked ? 0 : 2)), height: h, rx: 3, fill: s.color, class: 'an-bar', style: `animation-delay:${i * 18}ms` });
     root.append(r); acc += v;
    });
   });
   if (line) {
    const pts = line.values.map((v, i) => v == null ? null : [x(i), PAD.t + IH - (v / 100) * IH]);
    const seg = pts.filter(Boolean);
    if (seg.length > 1) root.append(svg('path', { d: smooth(seg), fill: 'none', stroke: line.color, 'stroke-width': 2.4, 'stroke-linecap': 'round', class: 'an-line', pathLength: 1 }));
    seg.forEach(p => root.append(svg('circle', { cx: p[0], cy: p[1], r: 3.2, fill: 'var(--an-surface,#fff)', stroke: line.color, 'stroke-width': 2 })));
   }
  };
  draw.mode = 'columns';
  frame(host, { buckets, weekly, max, ticks: scale.ticks, label, format: line ? v => v + ' %' : fmtInt, draw,
   rows: i => [...series.map(s => ({ name: s.name, value: fmtInt(s.values[i]), color: s.color })), ...(line ? [{ name: line.name, value: line.values[i] == null ? '–' : line.values[i] + ' %', color: line.color }] : [])] });
 }

 function legend(items) { const box = html('div', 'an-legend'); items.forEach(i => { const e = html('span', 'an-key'); const d = html('span', 'an-dot'); d.style.background = i.color; e.append(d, i.name); box.append(e); }); return box; }
 function stat(label, value, delta) {
  const box = html('div', 'an-stat'); box.append(html('small', '', label), html('b', '', value));
  if (delta != null && delta !== 0) { const d = html('span', 'an-delta ' + (delta > 0 ? 'up' : 'down'), (delta > 0 ? '▲ +' : '▼ ') + fmtInt(delta)); d.title = tr('an.vsPrevious', 'gegenüber dem vorherigen Zeitraum'); box.append(d); }
  return box;
 }
 function card(title, ...parts) { const c = html('section', 'an-card'); c.append(html('h3', '', title), ...parts); return c; }

 const money = (v, c) => fmtInt(Math.round(Number(v))) + ' ' + c;
 function render(host, data) {
  const weekly = data.granularity === 'WEEK', trend = data.trend, buckets = trend.map(p => ({ start: p.start, end: p.end }));
  host.replaceChildren();
  // 1 open files
  const c1 = html('div', 'an-chart'); const delta1 = trend.length > 1 ? data.totals.open - trend[0].open : 0;
  areaChart(c1, { buckets, weekly, values: trend.map(p => p.open), name: tr('an.open', 'Offene Akten'), color: COLORS.blue, label: tr('an.openLabel', 'Verlauf der offenen Akten') });
  // 2 volume (one currency at a time)
  const cur = data.currencies, c2 = html('div', 'an-chart'); let selected = cur[0];
  const pick = document.createElement('select'); pick.setAttribute('aria-label', tr('an.currency', 'Währung')); cur.forEach(c => { const o = html('option', '', c); o.value = c; pick.append(o); });
  const drawVolume = () => areaChart(c2, { buckets, weekly, values: trend.map(p => Number(p.volume[selected] || 0)), name: tr('an.volumeIn', 'Volumen {c}', { c: selected }), color: COLORS.teal, label: tr('an.volumeLabel', 'Verlauf des offenen Volumens'), format: v => v >= 1e6 ? (v / 1e6).toLocaleString(tr('gv.locale', 'de-DE'), { maximumFractionDigits: 1 }) + ' Mio' : v >= 1e3 ? Math.round(v / 1e3) + ' T' : String(Math.round(v)) });
  pick.onchange = () => { selected = pick.value; drawVolume(); };
  if (cur.length) drawVolume(); else c2.append(html('p', 'an-empty', tr('an.noVolume', 'Noch kein offenes Volumen.')));
  // 3 arrivals and closings
  const c3 = html('div', 'an-chart'), s3 = [{ name: tr('an.received', 'Eingänge'), values: trend.map(p => p.received), color: COLORS.blue }, { name: tr('an.closed', 'Abschlüsse'), values: trend.map(p => p.closed), color: COLORS.teal }];
  columnChart(c3, { buckets, weekly, series: s3, label: tr('an.flowLabel', 'Eingänge und Abschlüsse je Zeitraum') });
  // 4 deadline load ahead
  const lb = data.load.map(l => ({ start: l.start, end: l.end })), c4 = html('div', 'an-chart');
  const s4 = [
   { name: tr('an.expiry', 'Ablauf'), values: data.load.map(l => l.expiry), color: COLORS.red },
   { name: tr('an.shipment', 'Verladung'), values: data.load.map(l => l.shipment), color: COLORS.amber },
   { name: tr('an.followUp', 'Wiedervorlage'), values: data.load.map(l => l.followUp), color: COLORS.slate },
   { name: tr('an.guaranteeExpiry', 'Garantie läuft ab'), values: data.load.map(l => l.guaranteeExpiry), color: COLORS.violet },
   { name: tr('an.guaranteeDecision', 'Garantie: entscheiden'), values: data.load.map(l => l.guaranteeDecision), color: COLORS.blue }].filter(s => s.values.some(v => v > 0));
  if (s4.length) columnChart(c4, { buckets: lb, weekly: true, series: s4, stacked: true, label: tr('an.loadLabel', 'Anstehende Fristen je Woche') }); else c4.append(html('p', 'an-empty', tr('an.noLoad', 'Keine anstehenden Fristen in den nächsten 12 Wochen.')));
  const totals = data.totals;
  const vol = Object.entries(totals.volume).map(([c, v]) => money(v, c)).join(' · ') || '–';
  host.append(
   card(tr('an.openTitle', 'Offene Akten'), (() => { const r = html('div', 'an-stats'); r.append(stat(tr('an.now', 'Jetzt offen'), fmtInt(totals.open), delta1), stat(tr('an.overdue', 'Frist überschritten'), fmtInt(totals.overdue))); return r; })(), c1),
   card(tr('an.volumeTitle', 'Offenes Volumen'), (() => { const r = html('div', 'an-stats'); r.append(stat(tr('an.volumeNow', 'Jetzt'), vol)); if (cur.length > 1) r.append(pick); else if (cur.length) r.append(html('small', '', cur[0])); return r; })(), c2),
   card(tr('an.flowTitle', 'Eingänge und Abschlüsse'), (() => { const r = html('div', 'an-stats'); r.append(stat(tr('an.received', 'Eingänge'), fmtInt(totals.received), totals.received - totals.previousReceived), stat(tr('an.closed', 'Abschlüsse'), fmtInt(totals.closed), totals.closed - totals.previousClosed)); return r; })(), c3, legend(s3)),
   card(tr('an.loadTitle', 'Fristen in den nächsten 12 Wochen'), c4, legend(s4)));
  if (data.bank) {
   const bb = data.bank.map(p => ({ start: p.start, end: p.end })), c5 = html('div', 'an-chart');
   const s5 = [{ name: tr('an.discrepancies', 'Bestätigte Abweichungen'), values: data.bank.map(p => p.discrepancies), color: COLORS.red }, { name: tr('an.reports', 'Prüfberichte'), values: data.bank.map(p => p.reports), color: COLORS.blue }];
   const hasSla = data.bank.some(p => p.slaQuota != null);
   columnChart(c5, { buckets: bb, weekly, series: s5, label: tr('an.bankLabel', 'Abweichungen und Prüfberichte je Zeitraum') });
   const c6 = html('div', 'an-chart');
   if (hasSla) columnChart(c6, { buckets: bb, weekly, series: [{ name: tr('an.slaInTime', 'Eingehalten'), values: data.bank.map(p => p.slaInTime), color: COLORS.teal }, { name: tr('an.slaLate', 'Zu spät'), values: data.bank.map(p => p.slaLate), color: COLORS.red }], stacked: true, label: tr('an.slaLabel', 'Beendete SLA-Uhren je Zeitraum'), }); else c6.append(html('p', 'an-empty', tr('an.noSla', 'Noch keine beendeten SLA-Uhren im Zeitraum.')));
   host.append(card(tr('an.bankTitle', 'Prüfungen (Bank)'), c5, legend(s5)), card(tr('an.slaTitle', 'Service-Level'), c6, legend([{ name: tr('an.slaInTime', 'Eingehalten'), color: COLORS.teal }, { name: tr('an.slaLate', 'Zu spät'), color: COLORS.red }])));
  }
 }

 let section = null, days = 90, generation = 0;
 async function load() {
  const body = section.querySelector('.an-body'), mine = ++generation;
  body.setAttribute('aria-busy', 'true');
  try { const data = await json('/api/analytics?days=' + days); if (mine !== generation) return; render(body, data); }
  catch (error) { if (mine === generation) body.replaceChildren(html('p', 'an-empty', tr('an.failed', 'Auswertung konnte nicht geladen werden: ') + error.message)); }
  finally { body.removeAttribute('aria-busy'); }
 }
 function build() {
  section = html('section', 'panel an-section'); section.id = 'analyticsSection';
  const head = html('div', 'panelhead'), title = html('div'); title.append(html('h2', '', tr('an.title', 'Auswertung')), html('p', '', tr('an.intro', 'Verlauf der Akten, Eingänge und Abschlüsse, anstehende Fristen und – für Banken – Prüfungen und Service-Level.')));
  const range = html('div', 'an-range'); range.setAttribute('role', 'group'); range.setAttribute('aria-label', tr('an.range', 'Zeitraum'));
  [[30, tr('an.d30', '30 Tage')], [90, tr('an.d90', '90 Tage')], [365, tr('an.d365', '12 Monate')]].forEach(([d, text]) => { const b = html('button', d === days ? 'active' : '', text); b.type = 'button'; b.setAttribute('aria-pressed', String(d === days)); b.onclick = () => { days = d; range.querySelectorAll('button').forEach(x => { const on = x === b; x.classList.toggle('active', on); x.setAttribute('aria-pressed', String(on)); }); load(); }; range.append(b); });
  head.append(title, range); section.append(head, html('div', 'an-body'));
  const anchor = document.querySelector('#todaySection') || document.querySelector('#cockpitSection');
  if (!anchor) return false; anchor.after(section); return true;
 }
 globalThis.LcAnalytics = { niceMax, niceScale, smooth, isoWeek, labelOf };
 if (typeof document === 'undefined') return;
 const timer = setInterval(() => {
  if (typeof json !== 'function' || typeof currentUser === 'undefined' || !currentUser || !document.querySelector('#cockpitSection')) return;
  clearInterval(timer);
  if (typeof can === 'function' && !can('LC_VIEW') && !can('GUARANTEE_VIEW')) return;
  if (build()) load();
 }, 400);
 document.addEventListener('lc-language-changed', () => { if (section) load(); });
})();
