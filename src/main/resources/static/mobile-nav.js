/* Phone layout: the side menu becomes a drawer that opens with a menu button in the header and closes after a choice, on Escape or on a tap outside. */
(() => {
 const OPEN = 'nav-open';
 /** Opens, closes or toggles the drawer on the given body element; returns whether it is open afterwards. */
 function setOpen(body, open) {
  const next = open === undefined ? !body.classList.contains(OPEN) : Boolean(open);
  body.classList.toggle(OPEN, next);
  return next;
 }
 globalThis.LcMobileNav = { setOpen };
 if (typeof document === 'undefined' || !document.body) return;

 const header = document.querySelector('body>header'), nav = document.querySelector('#appNav');
 if (!header || !nav) return;
 const button = document.createElement('button');
 button.type = 'button'; button.id = 'mobileNavToggle'; button.className = 'secondary'; button.textContent = tx('mnl.845685','☰ Menü');
 button.setAttribute('aria-controls', 'appNav'); button.setAttribute('aria-expanded', 'false'); button.setAttribute('aria-label', tx('mnl.3bfadb','Menü öffnen oder schließen'));
 header.prepend(button);
 const sync = () => button.setAttribute('aria-expanded', String(document.body.classList.contains(OPEN)));
 button.addEventListener('click', event => { event.stopPropagation(); setOpen(document.body); sync(); });
 nav.addEventListener('click', event => { if (event.target.closest('button,a')) { setOpen(document.body, false); sync(); } });
 document.addEventListener('click', event => { if (document.body.classList.contains(OPEN) && !nav.contains(event.target)) { setOpen(document.body, false); sync(); } });
 document.addEventListener('keydown', event => { if (event.key === 'Escape' && document.body.classList.contains(OPEN)) { setOpen(document.body, false); sync(); button.focus(); } });
 window.addEventListener('resize', () => { if (innerWidth > 900 && document.body.classList.contains(OPEN)) { setOpen(document.body, false); sync(); } });
})();
