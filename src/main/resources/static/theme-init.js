/* Applies the chosen colour theme before the page is drawn (no flash): data-theme="light" or "dark" on <html>. Without a choice the light theme applies; "system" follows the device setting (theme.css). */
(() => {
 try { const choice = localStorage.getItem('lc-theme'); if (choice === 'light' || choice === 'dark' || choice === 'system') document.documentElement.setAttribute('data-theme', choice); } catch (ignored) { /* storage blocked: the system setting applies */ }
})();
