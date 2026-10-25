/* Hover magnifier for page thumbnails: shows the area under the pointer enlarged, without opening the page. */
(() => {
 const ZOOM = 2.6, SIZE = 240;
 /** Background offset so that the point under the pointer is centred in the lens; the result is clamped to the image. */
 function lensGeometry(rect, clientX, clientY, zoom = ZOOM, size = SIZE) {
  const x = Math.min(Math.max(clientX - rect.left, 0), rect.width);
  const y = Math.min(Math.max(clientY - rect.top, 0), rect.height);
  return {
   backgroundSize: `${rect.width * zoom}px ${rect.height * zoom}px`,
   backgroundPosition: `${size / 2 - x * zoom}px ${size / 2 - y * zoom}px`
  };
 }
 globalThis.LcThumbLoupe = { lensGeometry };
 if (typeof document === 'undefined' || !document.body) return;
 let lens = null;
 const hide = () => { if (lens) lens.hidden = true; };
 document.addEventListener('mousemove', event => {
  const image = event.target instanceof Element ? event.target.closest('.split-thumbnail img') : null;
  if (!image || !image.getAttribute('src') || !image.complete) { hide(); return; }
  if (!lens) {
   lens = document.createElement('div');
   lens.className = 'thumb-loupe';
   lens.setAttribute('aria-hidden', 'true');
   document.body.append(lens);
  }
  const rect = image.getBoundingClientRect(), geometry = lensGeometry(rect, event.clientX, event.clientY);
  lens.style.backgroundImage = `url("${image.src}")`;
  lens.style.backgroundSize = geometry.backgroundSize;
  lens.style.backgroundPosition = geometry.backgroundPosition;
  lens.style.width = lens.style.height = SIZE + 'px';
  const left = event.clientX + 24 + SIZE > innerWidth ? event.clientX - 24 - SIZE : event.clientX + 24;
  const top = Math.min(Math.max(event.clientY - SIZE / 2, 8), innerHeight - SIZE - 8);
  lens.style.left = left + 'px';
  lens.style.top = top + 'px';
  lens.hidden = false;
 });
 document.addEventListener('mouseleave', hide);
 document.addEventListener('scroll', hide, true);
})();
