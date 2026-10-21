/* Language for the pages without login (sign-in, platform sign-in, password reset, invitation, mailroom): German or English, chosen with the switch on the page
   and remembered in this browser. The texts are kept here (no network request before sign-in). Static texts carry data-i18n keys; scripts use tx('key', 'German'). */
(() => {
 const D = {
  'brand.language': ['Sprache', 'Language'],
  'login.title': ['Anmelden · Corporate LC Manager', 'Sign in · Corporate LC Manager'],
  'login.eyebrow': ['GESCHÜTZTER BEREICH', 'PROTECTED AREA'],
  'login.h1': ['Willkommen zurück.', 'Welcome back.'],
  'login.lead': ['Melden Sie sich an, um Akkreditive und Dokumente zu bearbeiten.', 'Sign in to process letters of credit and documents.'],
  'login.username': ['Benutzername', 'User name'],
  'login.password': ['Passwort', 'Password'],
  'login.submit': ['Anmelden', 'Sign in'],
  'login.forgot': ['Passwort vergessen?', 'Forgot your password?'],
  'login.info': ['Info, Copyright & Open-Source-Lizenzen', 'Info, copyright & open-source licences'],
  'login.failed': ['Anmeldung fehlgeschlagen.', 'Sign-in failed.'],
  'login.needLogin': ['Anmeldung erforderlich.', 'Sign-in required.'],
  'login.tenantsFailed': ['Mandanten konnten nicht geladen werden.', 'Tenants could not be loaded.'],
  'login.noTenant': ['Kein berechtigter Mandant verfügbar.', 'No authorised tenant available.'],
  'login.chooseTenant': ['Mandant auswählen', 'Choose tenant'],
  'login.openTenant': ['Mandant öffnen', 'Open tenant'],
  'login.openPlatform': ['Plattformverwaltung öffnen', 'Open platform administration'],
  'login.tenantDenied': ['Der Zugriff auf diesen Mandanten wurde verweigert. Mögliche Ursache: Administratoren brauchen die Zwei-Faktor-Anmeldung. Bitte neu anmelden und sie einrichten.', 'Access to this tenant was denied. Possible cause: administrators need two-factor authentication. Please sign in again and set it up.'],
  'login.tenantFailed': ['Mandant konnte nicht geöffnet werden.', 'The tenant could not be opened.'],
  'login.totpSetup': ['Zwei-Faktor-Anmeldung einrichten', 'Set up two-factor authentication'],
  'login.totpSetupHint': ['Für Administratoren ist 2FA verpflichtend. Scannen Sie den QR-Code mit Ihrer Authenticator-App und bestätigen Sie den aktuellen Code.', '2FA is mandatory for administrators. Scan the QR code with your authenticator app and confirm the current code.'],
  'login.qrAlt': ['QR-Code für die Authenticator-App', 'QR code for the authenticator app'],
  'login.manualKey': ['Manueller Einrichtungsschlüssel: ', 'Manual setup key: '],
  'login.code': ['Sicherheitscode', 'Security code'],
  'login.codePlaceholder': ['Code aus der Authenticator-App', 'Code from the authenticator app'],
  'login.recoveryHint': ['Bei bereits aktivierter 2FA können Sie auch einen Notfallcode verwenden.', 'If 2FA is already enabled you can also use an emergency code.'],
  'login.checkCode': ['Code prüfen', 'Check code'],
  'login.recoveryTitle': ['Notfallcodes sicher aufbewahren', 'Keep the emergency codes safe'],
  'login.recoveryText': ['Jeder Code kann einmal verwendet werden. Speichern Sie diese Codes jetzt an einem sicheren Ort.', 'Each code can be used once. Save these codes now in a safe place.'],
  'login.codesSaved': ['Codes gesichert – weiter', 'Codes saved – continue'],
  'pl.title': ['Plattform-Anmeldung · Corporate LC Manager', 'Platform sign-in · Corporate LC Manager'],
  'pl.brand': ['Plattformverwaltung', 'Platform administration'],
  'pl.brandSub': ['Globale Administration · außerhalb der Mandanten', 'Global administration · outside the tenants'],
  'pl.note': ['Nur für Plattform-Administratoren mit Zwei-Faktor-Anmeldung. Mandantendaten und Geschäftsabläufe sind hier nicht erreichbar.', 'For platform administrators with two-factor authentication only. Tenant data and business processes are not reachable here.'],
  'pl.toTenantLogin': ['Zur Mandantenanmeldung', 'To the tenant sign-in'],
  'pl.noAccess': ['Dieses Konto hat keinen Plattformzugang oder keine aktive Zwei-Faktor-Anmeldung.', 'This account has no platform access or no active two-factor authentication.'],
  'pl.setupFirst': ['Bitte zuerst die Zwei-Faktor-Anmeldung über die normale Anmeldung einrichten.', 'Please set up two-factor authentication first via the normal sign-in.'],
  'pr.title': ['Passwort zurücksetzen · Corporate LC Manager', 'Reset password · Corporate LC Manager'],
  'pr.h1': ['Passwort vergessen?', 'Forgot your password?'],
  'pr.h1New': ['Neues Passwort setzen', 'Set a new password'],
  'pr.requestText': ['Geben Sie Ihren Benutzernamen und die im Profil hinterlegte E-Mail-Adresse ein.', 'Enter your user name and the e-mail address stored in your profile.'],
  'pr.email': ['E-Mail', 'E-mail'],
  'pr.requestSubmit': ['Reset-Link anfordern', 'Request reset link'],
  'pr.completeText': ['Der Link ist 30 Minuten gültig. Ihre Zwei-Faktor-Anmeldung bleibt aktiv.', 'The link is valid for 30 minutes. Your two-factor authentication stays active.'],
  'pr.newPassword': ['Neues Passwort', 'New password'],
  'pr.repeat': ['Passwort wiederholen', 'Repeat password'],
  'pr.rules': ['Mindestens 10 Zeichen mit Großbuchstaben, Kleinbuchstaben und einer Zahl.', 'At least 10 characters with upper case, lower case and a number.'],
  'pr.completeSubmit': ['Passwort ändern', 'Change password'],
  'pr.toLogin': ['Zur Anmeldung', 'To the sign-in'],
  'pr.failed': ['Die Anfrage konnte nicht verarbeitet werden.', 'The request could not be processed.'],
  'pr.mismatch': ['Die Passwörter stimmen nicht überein.', 'The passwords do not match.'],
  'inv.title': ['Einladung annehmen · Corporate LC Manager', 'Accept invitation · Corporate LC Manager'],
  'inv.h1': ['Einladung annehmen', 'Accept your invitation'],
  'inv.lead': ['Wählen Sie Ihr eigenes Passwort. Die Einladung ist 24 Stunden gültig und kann einmal verwendet werden.', 'Choose your own password. Your invitation is valid for 24 hours and can be used once.'],
  'inv.confirm': ['Passwort bestätigen', 'Confirm password'],
  'inv.rules': ['Mindestens 10 Zeichen, Groß-/Kleinbuchstaben und eine Zahl; höchstens 72 UTF-8-Bytes.', 'At least 10 characters, upper/lower case and a number; at most 72 UTF-8 bytes.'],
  'inv.submit': ['Einladung annehmen', 'Accept invitation'],
  'inv.signIn': ['Anmelden', 'Sign in'],
  'inv.invalid': ['Die Einladung ist ungültig oder fehlt. Bitte bei der Administration eine neue anfordern.', 'Invitation is invalid or missing. Request a new invitation from your administrator.'],
  'inv.mismatch': ['Die Passwörter stimmen nicht überein.', 'Passwords do not match.'],
  'inv.failed': ['Die Einladung konnte nicht angenommen werden.', 'Invitation could not be accepted.'],
  'ps.title': ['Poststelle · Corporate LC Manager', 'Mailroom · Corporate LC Manager'],
  'ps.brandSub': ['Poststelle · Dokumente anliefern', 'Mailroom · deliver documents'],
  'ps.access': ['Zugang', 'Access'],
  'ps.accessText': ['Der Erfassungszugang wird von der Mandanten-Administration angelegt (Einstellungen → Erfassungszugänge). Er erlaubt nur, Dokumente anzuliefern.', 'The capture access is created by the tenant administration (settings → capture accesses). It only allows delivering documents.'],
  'ps.token': ['Erfassungszugang', 'Capture access'],
  'ps.scanner': ['Gerät / Station', 'Device / station'],
  'ps.scannerExample': ['z. B. Scanner 2', 'e.g. scanner 2'],
  'ps.remember': ['Auf diesem Gerät merken', 'Remember on this device'],
  'ps.delivery': ['Lieferung', 'Delivery'],
  'ps.batch': ['Stapel', 'Batch'],
  'ps.batchExample': ['z. B. 17', 'e.g. 17'],
  'ps.dropLabel': ['Dateien auswählen oder hierher ziehen', 'Choose files or drag them here'],
  'ps.dropTitle': ['Dateien hierher ziehen', 'Drag files here'],
  'ps.dropSub': ['oder zum Auswählen klicken (PDF, Bilder)', 'or click to choose (PDF, images)'],
  'ps.e401': ['Der Zugang ist ungültig oder wurde widerrufen. Bitte bei der Administration einen neuen anfordern.', 'The access is invalid or has been revoked. Please request a new one from the administration.'],
  'ps.e403': ['Dieser Zugang darf das nicht.', 'This access is not allowed to do that.'],
  'ps.e413': ['Die Datei ist zu groß.', 'The file is too large.'],
  'ps.duplicate': ['Schon früher angeliefert – nicht ein zweites Mal angelegt.', 'Delivered before – not created a second time.'],
  'ps.pages': ['Angeliefert, aber die Seitenzahl weicht ab (erkannt {n}). Bitte den Stapel prüfen.', 'Delivered, but the page count differs (recognised {n}). Please check the batch.'],
  'ps.delivered': ['Angeliefert.', 'Delivered.'],
  'ps.error': ['Fehler {status}. Bitte noch einmal versuchen.', 'Error {status}. Please try again.'],
  'ps.sending': ['wird gesendet', 'sending'],
  'ps.noConnection': ['Keine Verbindung zum Server. Bitte noch einmal versuchen.', 'No connection to the server. Please try again.'],
  'ps.needToken': ['Bitte zuerst den Erfassungszugang eintragen (beginnt mit lcc_).', 'Please enter the capture access first (starts with lcc_).'],
  'ps.summary': ['{ok} angeliefert{warn}{fail}.', '{ok} delivered{warn}{fail}.'],
  'ps.summaryWarn': [', {n} mit Hinweis', ', {n} with a note'],
  'ps.summaryFail': [', {n} fehlgeschlagen', ', {n} failed']
 };
 const STORE = 'lc.preloginLanguage';
 const read = () => { try { const v = localStorage.getItem(STORE); return v === 'de' || v === 'en' ? v : null; } catch { return null; } };
 let language = read() || (document.documentElement.lang === 'en' ? 'en' : 'de');
 const text = (key, de, values = {}) => {
  const entry = D[key], base = entry ? entry[language === 'en' ? 1 : 0] : de;
  return String(base ?? de).replace(/\{(\w+)\}/g, (m, name) => String(values[name] ?? m));
 };
 globalThis.tx = text;
 globalThis.LcPrelogin = { D, text, language: () => language };
 if (typeof document === 'undefined' || !document.body) return;

 function apply() {
  document.documentElement.lang = language;
  document.querySelectorAll('[data-i18n]').forEach(node => {
   const value = text(node.dataset.i18n, node.textContent);
   const first = [...node.childNodes].find(child => child.nodeType === 3 && child.textContent.trim());
   if (node.children.length && first) first.textContent = value; else if (!node.children.length) node.textContent = value;
  });
  document.querySelectorAll('[data-i18n-placeholder]').forEach(node => { node.placeholder = text(node.dataset.i18nPlaceholder, node.placeholder); });
  document.querySelectorAll('[data-i18n-aria-label]').forEach(node => { node.setAttribute('aria-label', text(node.dataset.i18nAriaLabel, node.getAttribute('aria-label'))); });
  document.querySelectorAll('.prelogin-lang button').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.lang === language)));
 }
 function build() {
  const bar = document.createElement('div'); bar.className = 'prelogin-lang'; bar.setAttribute('role', 'group'); bar.setAttribute('aria-label', 'Sprache / Language');
  [['de', 'Deutsch'], ['en', 'English']].forEach(([code, label]) => {
   const button = document.createElement('button'); button.type = 'button'; button.dataset.lang = code; button.textContent = label;
   button.onclick = () => { language = code; try { localStorage.setItem(STORE, code); } catch { /* the choice is only a convenience */ } apply(); document.dispatchEvent(new Event('prelogin-language-changed')); };
   bar.append(button);
  });
  document.body.append(bar);
 }
 build(); apply();
})();
