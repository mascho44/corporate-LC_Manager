/* Layout rules for a rendered page. Paste into the browser console, run through a browser tool, or inject with Playwright: `await page.evaluate(src)`
   then `window.lcLayoutCheck()`. Returns a list of findings; an empty list means none of these rules was violated.
   Rules: rowMisaligned (fields of one row not level), pageOverflow, covered (a control that another element lies over), overlap (two controls on top of each other), cutOff (a control reaching
   outside a clipping parent), clippedText (text cut off without an ellipsis), controlSize (outside the 28–56 px range (36/30 px standard, 44 px header, up to two text lines) of ui-system.css). */
(() => {
 const CONTROLS = 'button,a[href],input:not([type=hidden]),select,textarea,summary,[role=button]';
 const SKIP_SIZE = '.today-tile,input[type=checkbox],input[type=radio],input[type=range],input[type=file],.ghost,.doc-thumb,.split-thumbnail,.snippet-zoom,.finding-pick,.app-nav button,.dossier-nav button,.tab,[data-no-layout-size]';
 const label = el => {
  const id = el.id ? '#' + el.id : '', cls = typeof el.className === 'string' && el.className.trim() ? '.' + el.className.trim().split(/\s+/).slice(0, 2).join('.') : '';
  const text = (el.getAttribute('aria-label') || el.textContent || el.value || '').trim().replace(/\s+/g, ' ').slice(0, 40);
  return el.tagName.toLowerCase() + id + cls + (text ? ` „${text}"` : '');
 };
 // Content of a closed <details> is not shown although the browser still reports boxes for it.
 const inClosedDetails = el => { for (let p = el.parentElement; p; p = p.parentElement) if (p.tagName === 'DETAILS' && !p.open) { const s = el.closest('summary'); return !(s && s.parentElement === p); } return false; };
 const visible = el => {
  if (!el.getClientRects().length || inClosedDetails(el)) return false;
  const style = getComputedStyle(el);
  return style.visibility !== 'hidden' && style.display !== 'none' && Number(style.opacity) > 0.05;
 };
 const area = r => Math.max(0, r.width) * Math.max(0, r.height);
 const intersection = (a, b) => Math.max(0, Math.min(a.right, b.right) - Math.max(a.left, b.left)) * Math.max(0, Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top));
 const floating = el => { for (let p = el; p && p !== document.documentElement; p = p.parentElement) { const position = getComputedStyle(p).position; if (position === 'fixed' || position === 'sticky') return true; } return false; };
 const clipping = el => { for (let p = el.parentElement; p && p !== document.body; p = p.parentElement) { const o = getComputedStyle(p); if (/(hidden|clip)/.test(o.overflowX + o.overflowY) && p.tagName !== 'HTML') return p; } return null; };

 window.lcLayoutCheck = function (options = {}) {
  const root = options.root || document, findings = [];
  const add = (rule, el, detail) => findings.push({ rule, element: label(el), detail });
  const doc = document.documentElement;
  if (doc.scrollWidth > doc.clientWidth + 1) add('pageOverflow', document.body, `Seite ist ${doc.scrollWidth} px breit, sichtbar sind ${doc.clientWidth} px`);

  const controls = [...root.querySelectorAll(CONTROLS)].filter(el => visible(el) && !el.closest('[hidden],[inert]'));
  controls.forEach(el => {
   const r = el.getBoundingClientRect();
   if (!el.matches(SKIP_SIZE) && el.tagName !== 'A' && el.tagName !== 'SUMMARY' && el.tagName !== 'TEXTAREA' && (r.height < 28 || r.height > 56 || r.width < 24)) add('controlSize', el, `${Math.round(r.width)} × ${Math.round(r.height)} px`);
   // Covered: the element that really sits at the centre of the control is neither the control nor part of it.
   if (r.width > 0 && r.height > 0 && r.top >= 0 && r.left >= 0 && r.bottom <= innerHeight && r.right <= innerWidth) {
    const top = document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2);
    if (top && top !== el && !(floating(top) && !floating(el)) && !el.contains(top) && !top.contains(el) && !(el.tagName === 'INPUT' && el.labels && [...el.labels].some(l => l.contains(top)))) add('covered', el, `verdeckt von ${label(top)}`);
   }
   const clip = clipping(el);
   if (clip) { const c = clip.getBoundingClientRect(); if (r.right > c.right + 2 || r.bottom > c.bottom + 2 || r.left < c.left - 2) add('cutOff', el, `ragt aus ${label(clip)} heraus`); }
  });
  for (let i = 0; i < controls.length; i++) for (let j = i + 1; j < controls.length; j++) {
   const a = controls[i], b = controls[j];
   if (a.contains(b) || b.contains(a) || floating(a) !== floating(b)) continue;
   const ra = a.getBoundingClientRect(), rb = b.getBoundingClientRect(), shared = intersection(ra, rb);
   if (shared > 0 && shared / Math.min(area(ra), area(rb)) > 0.2) add('overlap', a, `überlappt ${label(b)}`);
  }
  // Fields side by side that look like one row but do not start level (typically a hint line under one of them lifts its neighbour).
  const fields = controls.filter(el => /^(INPUT|SELECT)$/.test(el.tagName) && !/^(checkbox|radio|range|file)$/.test(el.type));
  for (let i = 0; i < fields.length; i++) for (let j = i + 1; j < fields.length; j++) {
   const a = fields[i], b = fields[j], ra = a.getBoundingClientRect(), rb = b.getBoundingClientRect();
   const apart = ra.right <= rb.left + 1 || rb.right <= ra.left + 1;
   const shared = Math.min(ra.bottom, rb.bottom) - Math.max(ra.top, rb.top);
   const differ = Math.abs(ra.top - rb.top);
   if (apart && Math.abs(ra.height - rb.height) <= 4 && shared > 0.3 * Math.min(ra.height, rb.height) && differ > 3 && differ <= 30 && a.closest('form,dialog,section') === b.closest('form,dialog,section')) add('rowMisaligned', a, `${Math.round(differ)} px höher/tiefer als ${label(b)}`);
  }
  root.querySelectorAll('h1,h2,h3,h4,b,strong,small,label,p,span,td,th,li,legend,dt,dd').forEach(el => {
   if (!visible(el) || el.children.length || !el.textContent.trim() || el.getBoundingClientRect().width <= 2) return;
   const style = getComputedStyle(el);
   if (/(hidden|clip)/.test(style.overflowX) && style.textOverflow !== 'ellipsis' && el.scrollWidth > el.clientWidth + 2) add('clippedText', el, `Text ${el.scrollWidth} px in ${el.clientWidth} px Breite abgeschnitten`);
  });
  const seen = new Set();
  return findings.filter(f => { const key = f.rule + '|' + f.element + '|' + f.detail; if (seen.has(key)) return false; seen.add(key); return true; });
 };

 /** Scrolls through the page in steps and merges the findings (covered and overlap only see what is inside the viewport). */
 window.lcLayoutScan = async function (options = {}) {
  const all = new Map(), step = Math.max(200, Math.floor(innerHeight * 0.8)), max = document.documentElement.scrollHeight;
  for (let y = 0; y < max; y += step) { window.scrollTo(0, y); await new Promise(r => setTimeout(r, 60)); window.lcLayoutCheck(options).forEach(f => all.set(f.rule + '|' + f.element + '|' + f.detail, f)); }
  window.scrollTo(0, 0);
  return [...all.values()];
 };
})();
