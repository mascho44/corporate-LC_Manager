# Anmeldesicherheit

## Bedienung

Administratoren benötigen Zwei-Faktor-Anmeldung mit einer Authenticator-App.
Nach erfolgreicher Passwortprüfung wird bei noch nicht eingerichteter 2FA der
QR-Code samt manuellem Schlüssel angezeigt. Erst ein gültiger Code aktiviert
2FA und gibt die Anwendung frei. Die acht einmal verwendbaren Notfallcodes
werden einmal angezeigt und müssen sicher außerhalb der Anwendung aufbewahrt
werden. Administratoren können 2FA nicht deaktivieren.

Andere Benutzer können 2FA weiterhin freiwillig in ihrem Profil aktivieren.
Bereits eingerichtete Authenticator-Apps und Notfallcodes bleiben gültig.

## Grenzen und Sitzungen

- Passwort- und Codeprüfung: jeweils höchstens fünf Versuche pro Konto sowie
  30 pro IP-Adresse innerhalb von fünf Minuten.
- Konto-Limits gelten unabhängig von Groß-/Kleinschreibung und über mehrere
  IP-Adressen hinweg. Erfolgreiche vollständige Anmeldung löscht das
  Konto-Limit, nicht das IP-Limit.
- Bei Überschreitung: HTTP 429 mit `Retry-After: 300`.
- Ausstehende 2FA-Anmeldung/Einrichtung läuft nach fünf Minuten ab.
- Sitzungen laufen nach 30 Minuten Inaktivität und spätestens acht Stunden
  nach vollständiger Anmeldung ab.
- Passwortwechsel, deaktivierte oder gelöschte Konten entziehen bestehende
  Sitzungen beim nächsten Zugriff. Admin-Sitzungen ohne eingerichtete 2FA
  sind nicht zulässig.
- Beim Login und nach vollständiger Anmeldung wird die Sitzungs-ID erneuert.
- Produktionscookies sind Secure, HttpOnly und SameSite=Lax.

## Betrieb

Für die 2FA-Einrichtung muss `TOTP_ENCRYPTION_KEY` konfiguriert sein;
die Produktionskonfiguration verlangt diesen bereits. Schlüssel sicher
aufbewahren: Ein Verlust verhindert die Entschlüsselung bestehender TOTP-Daten.
Der erste Login nach Einführung verlangt auch bei bestehenden Benutzern eine
neue Anmeldung.

Die Versuchsbegrenzung liegt bewusst im begrenzten Arbeitsspeicher einer
Anwendungsinstanz (maximal 10.000 Schlüssel, danach Ablehnung neuer Einträge).
Ein Neustart setzt die Limits zurück. Bei mehreren Instanzen ist ein gemeinsamer
Limiter, z. B. Redis, erforderlich. Die Limits sind keine dauerhafte Kontosperre.
Der vorgeschaltete Proxy muss fremde Forwarded-/X-Forwarded-For-Header
entfernen bzw. vertrauenswürdig neu setzen, damit IP-Limits zuverlässig sind.
Bei vielen Benutzern hinter einer gemeinsamen IP ist das IP-Limit zu prüfen.

Einrichtungsschlüssel, Passwörter und Notfallcodes gehören nicht in Protokolle.
Erfolgreiche Anmeldung, fehlgeschlagene Passwort-/Codeprüfung und
2FA-Einrichtung werden im bestehenden Audit-Protokoll erfasst.
