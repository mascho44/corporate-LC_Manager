# Änderungsprotokoll

## 0.5.1 – in Arbeit

* Datenbank: Die App meldet PostgreSQL pro Verbindung den aktuellen Mandanten (`app.tenant_id`) – Vorbereitung für Row-Level-Security, noch ohne Richtlinien.
* Lokale Datenbanksicherungen lassen sich optional verschlüsseln (`BACKUP_PASSPHRASE_FILE`) und auf die neuesten N begrenzen (`BACKUP_KEEP`); der Wiederherstellungstest versteht verschlüsselte Sicherungen.
* Optionale eingeschränkte Datenbankrolle für die Laufzeit (`DB_APP_USER`): Migrationen laufen weiter mit der Besitzerrolle, die App selbst darf nur Daten lesen und schreiben. Einrichtung mit `scripts/db-app-role.sh`, siehe `docs/datenbank-rollen.md`. Ohne die Variablen ändert sich nichts.
* Befunde mit Datums- oder Zahlenabweichung erklären die Differenz in Worten (z. B. „3 Tage nach der zulässigen Grenze“, Überschuss über dem Höchstbetrag).

* IMAP-Kanal gegen einen IMAP-Testserver (GreenMail) geprüft; der Test läuft in der CI.
* Prüfungsassistent auch aus der Aktenliste startbar (Knopf „Assistent"); der Posteingang zeigt die Herkunft von E-Mail- und Ordner-Eingängen (Absender, Betreff, Kanal).

## 0.5.0 – 17.10.2026 (erstes Release)

Corporate LC Manager unterstützt die Bearbeitung von Akkreditiven (Documentary Credits) für Banken und Corporates: SWIFT-Import, Dokumentenerkennung, regelbasierte Prüfung mit Nachweis, geführte Prüfung, Posteingang mit mehreren Eingangskanälen, mandantengetrennte Verwaltung. Die ausführliche Beschreibung steht in der [Projektdokumentation](docs/README.md).

### Prüfung
* **Prüfungsassistent:** geführte Prüfung in sieben Schritten mit gespeichertem Fortschritt je Akte; Überspringen nur mit Begründung ([Doku](docs/pruefassistent.md)).
* **Prüfmodus:** dokumentweise Prüfung mit Fortschritt, gelber Fundstelle im PDF (auch für geprüfte Werte wie Betrag und Datum), rotem Haken bei entschiedenen Befunden, Kasten „Fehlende LC-Angaben" mit Vorlagedatum, Begründungsbausteinen passend zum Befund, Abschluss mit Unstimmigkeitsmeldung (MT734/Brief als Entwurf) und Vermerk.
* **Regeln:** interne Rule Packs (UCP 600/ISBP), Sanktionsprüfung mit Vier-Augen-Freigabe, HS-Code-Erkennung, Original-/Kopie-Prüfung und Full Set beim Konnossement mit Zahl der Originale aus Frachtführer-Tabellen.
* **Entscheidungen bleiben erhalten:** Prüfentscheidungen sind an den Inhalt gebunden, nicht an die ganze Datei, und überstehen unverändertes Neuladen.

### Posteingang und Erkennung
* **Eingangskanäle:** gemeinsamer Eingang für Scan-Station (REST), Ordner („Scan to Folder") und E-Mail-Postfach (IMAP, nur verschlüsselt, mit Absenderliste und verschlüsselt gespeichertem Kennwort); Doppelte werden erkannt, die Herkunft bleibt am Dokument ([Doku](docs/eingangskanaele.md)).
* **Erkennung:** Dokumenttyp, Beträge, Nummern, Referenzen und Daten auch bei Scans (Zuordnung von Beschriftung und Wert nach Spaltenlage, verlorene Dezimalkommas), Warenbeschreibung unter den üblichen Beschriftungen, gelernte Beschriftungen aus von Hand eingetragenen Werten.
* **Aufteilung von Sammel-PDFs:** Vorschlag mit Seitennummern, Rückseiten, Anlagen und schwach gelesenen Rückseiten; Sammeldateien werden als solche gekennzeichnet; automatische Aufteilung nach mittlerer Wortsicherheit.
* **OCR:** genauere Tesseract-Modelle im Image, OCR-Toleranz je Mandant ([Doku](docs/ocr-toleranz-und-modelle.md)).
* **SWIFT-Import:** MT700, MT720, MT707 und weitere; bedruckte Bankformate mit Beschriftung und Wert getrennt; Korrekturdialog bei Verweisen wie „SEE 47A".

### Mandanten, Sicherheit und Betrieb
* Mandantentrennung mit Plattformadministration, Mitgliedschaften und Rechten; Kontorechte; Zwei-Faktor für Administratoren.
* Audit-Protokoll mit Verkettung, Betriebsüberwachung (`ops/monitor.sh`), Sicherung und Wiederherstellung, EBICS-Anbindung.

### Bekannte Grenzen
* Die automatische Aufteilung verlangt, dass jede Seite sicher zugeordnet ist; Seiten ohne Überschrift und ohne Fortsetzungshinweis bleiben eigene Teile.
* Die OCR ist bei stark verrauschten Kopierer-Scans begrenzt; Felder echter Formulare werden dort nicht immer erkannt.
* Stempel werden nur als Text erkannt, nicht als Bild; Original/Copy kennt keine Nummerierung („1/3").
* Mail-Anmeldung nur mit Benutzername und Kennwort (kein OAuth); der IMAP-Zugriff ist nicht gegen einen echten Mailserver getestet.
* Die Prüfung ersetzt keine fachliche Endprüfung; Erkennung und Vorschläge sind Hilfen, keine Entscheidungen.

Die vollständige Liste der Änderungen steht in den [Pull Requests](https://github.com/mascho44/corporate-LC_Manager/pulls?q=is%3Apr+is%3Amerged).
