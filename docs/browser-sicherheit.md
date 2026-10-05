# Browser-Sicherheit

Die Anwendung liefert eine Content Security Policy zunächst ausschließlich als
`Content-Security-Policy-Report-Only` aus. Sie blockiert noch keine Ressourcen.
Abweichungen erscheinen in der Browser-Konsole; es gibt derzeit keine zentrale
Sammlung und keine Übermittlung an externe Reporting-Dienste.

Die Zielrichtlinie erlaubt Skripte, Netzwerkanfragen und Schriftarten nur von
der eigenen Anwendung. Bilder dürfen zusätzlich Data-/Blob-URLs verwenden
(Authenticator-QR-Code, Avatar und PDF-Ausschnitt). Interne PDF-Frames und
Blob-Vorschauen bleiben vorgesehen. Plugins sind nicht vorgesehen.
Inline-Stile sind wegen vorhandener dynamischer Layouts vorläufig erlaubt;
Inline-Skripte und `eval` sind nicht erlaubt.

Zusätzlich werden folgende Header bereits durchgesetzt:

- `Referrer-Policy: no-referrer`: auch externe Links erhalten keine Seitenadresse
  der Anwendung als Referrer.
- `Permissions-Policy`: Kamera, Mikrofon, Standort, Zahlungs- und USB-Zugriff
  werden gesperrt; diese Funktionen benötigt die Anwendung nicht.
- Der bestehende Schutz gegen Framing (`X-Frame-Options: DENY`) und
  MIME-Sniffing (`X-Content-Type-Options: nosniff`) bleibt unverändert.

## Prüfung vor CSP-Durchsetzung

Nach Deployment in den Browser-Entwicklerwerkzeugen die Konsole prüfen:

1. Login, TOTP-Einrichtung und Passwort-Reset.
2. LC-Akte, Dokumentvorschau, Original-PDF und Downloads.
3. SWIFT-/Avisierungstraining, PDF-Ausschnitte, Lupe und Originalansicht.
4. Profilbild-Upload und Dokumentenerstellung.

Erwartete Warnungen fachlich einordnen, notwendige Ausnahmen möglichst eng
definieren und erst anschließend auf `Content-Security-Policy` umstellen.
Bis dahin stellt die CSP **keinen durchgesetzten Schutz** gegen Script-Injection
dar und ersetzt keine sichere Ausgabe-/Eingabeverarbeitung.

Für Produktion sind HTTPS, eine HTTP-zu-HTTPS-Weiterleitung und HSTS am
vertrauenswürdigen Proxy zu prüfen. HSTS-Preload wird nicht automatisch aktiviert;
`includeSubDomains` darf nur verwendet werden, wenn alle betroffenen
Subdomains ebenfalls dauerhaft HTTPS unterstützen.

Grundlagen: [OWASP CSP](https://cheatsheetseries.owasp.org/cheatsheets/Content_Security_Policy_Cheat_Sheet.html)
und [Spring Security Header](https://docs.spring.io/spring-security/reference/servlet/exploits/headers.html).
