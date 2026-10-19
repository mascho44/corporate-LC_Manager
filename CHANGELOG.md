# Änderungsprotokoll

## 0.5.1 – in Arbeit

* Einreichungspaket für die Bank: Begleitschreiben (Word, mit Liste der Dokumente, Originalen und Kopien) und die Dokumente in der üblichen Reihenfolge als ZIP, Vorschlag der Zahlen aus dem Akkreditiv, Warnung bei offenen Abweichungen der Vorprüfung, Vorlagedatum wahlweise speichern (`docs/einreichungspaket.md`). Briefköpfe und Fußzeilen lassen fehlende Firmenangaben weg; die Originalanzahl „FULL SET 3/3“ wird erkannt.
* Anmeldung: Der Heimatmandant (zum Beispiel `platform`) erscheint nicht mehr in der Mandantenauswahl, wenn es andere Mandanten gibt. Der Server markiert ihn mit `home`; der Filter nach dem festen Code `default` war mit dem Wechsel des Heimatmandanten entfallen.
* Dokumente erzeugen ohne Firmenprofil: Statt „Firma nicht gefunden“ zeigt der Briefkopf den Begünstigten aus dem Akkreditiv; der Dialog „Dokumentensatz erzeugen“ weist darauf hin, dass Anschrift, Logo und Bankverbindung aus dem Firmenprofil kommen (Mandanteneinstellungen → Firma).
* Dokumentensatz erzeugen: Rechnung, Packliste und Zertifikate aus einer Eingabe mit gleichen Nummern, Daten, Positionen und Summen, Vorschlag aus dem Akkreditiv, Warnung über dem Höchstbetrag und Ergebnis der automatischen Prüfung (`docs/dokumentensatz.md`).
* Erzeugte Rechnungen speichern die Summe der Positionen statt des Akkreditivbetrags (die Betragsprüfung schlug dort nie an); der Prüftext erzeugter Dokumente enthält Incoterm, Häfen, Zahlungsbedingung und Erklärung.
* Avis: Schreiben und Anlage als zwei Dateien einlesen (Auswahl der Anlage im Dialog, gleiche E-Mail und Akkreditivnummer werden vorgeschlagen; beide Dokumente landen in der neuen Akte).
* **Fehler behoben:** „LC-Akte aus Avis anlegen“ scheiterte seit 15.10.2026 mit Serverfehler 500 (unveränderliche Listen aus dem Parser in der Akte). Betrifft den Avis-Weg im Posteingang, nicht den SWIFT-Import-Dialog.
* Avis einlesen: Akkreditivtexte ohne SWIFT-Feldnummern (Bankportal-Ausdrucke, Handelsfinanzsysteme) werden anhand der Feldnamen auf Englisch und Deutsch gelesen; Daten und Beträge werden in die MT700-Form umgewandelt; die Anlage hinter dem Begleitschreiben wird nicht mehr durch die Grußformel abgeschnitten (`docs/avis-einlesen.md`).
* Word-Mustervorlagen professioneller: Briefkopf mit Logo und Firmendaten, Titelleiste, Kopfblock (Verkäufer/Käufer, Nummer/Datum/Akkreditiv, Häfen, Incoterm, Zahlungsbedingung), formatierte Positionstabelle mit Summe und Betrag in Worten, Erklärungstext, Unterschriftsblock und Fußzeile mit Bank-, Register- und Seitenangaben (`docs/word-vorlagen.md`).
* Plattform-Verwaltung „Benutzer & Mandanten“: Die Mandantenauswahl blendet den Heimatmandanten statt des festen Mandanten `default` aus (der Server liefert die Markierung `home`); die Benutzerzeile hat eine feste Breite, damit der Text in allen Browsern sichtbar bleibt.
* Mandanten: Der frühere Standardmandant `default` ist ein normaler Mandant, sobald ein anderer Heimatmandant festgelegt ist (er lässt sich sperren, archivieren und löschen; geschützt ist stets der aktuelle Heimatmandant). Beim Löschen eines Mandanten wird sein Code frei (der Rest heißt `<code>~deleted~<id>`).
* Mandant anlegen mit bestehendem Konto: Auch ein Plattformadministrator lässt sich als erster Mandantenadmin angeben (die Ablehnung entfällt; das Audit vermerkt es).
* Heimatmandant wechseln: Fehlt eine Rolle im Zielmandanten dem Namen nach, ziehen die Konten auf die Standardrolle gleicher Art um (zum Beispiel „Sachbearbeiter“ auf „Editor“).
* Mandant anlegen: Als erster Mandantenadmin lässt sich ein bestehendes Konto angeben (Benutzername genügt). Der Navigationspunkt „Training & Erkennung“ ist vorerst ausgeblendet.
* Plattform-Verwaltung: Knopf „Als Heimatmandant festlegen“ je aktivem Mandanten (Code eintippen, Umzug aller Konten bestätigen).
* Zusatzbedingungen (:47A:): „Alle Dokumente müssen die Akkreditivnummer zeigen“ und „in englischer Sprache“ werden je Dokument automatisch geprüft; der Sammelhinweis bleibt nur für die übrigen Positionen.
* Sanktionsprüfung: Einträge wie „APPLICANT“ oder „TO ORDER“ bei Consignee und Notify Party werden nicht mehr als Name geprüft (falscher Treffer „APPLICANT“ ~ „LLC APPLICATA“); der Auftraggeber wird ohnehin unter seinem echten Namen geprüft.
* Heimatmandant wechseln (`PUT /api/platform/home-tenant`): Konten und Plattform-Anmeldung lassen sich in einen anderen Mandanten verlegen (Vorbereitung, den alten Standardmandanten zu entfernen). Transaktional, auditiert, umkehrbar.
* Prüfassistent, Schritt „Fehlende Angaben“: Klartext statt technischer Feldnamen; „Im Akkreditiv nicht verlangt“ je Angabe (Migration V131, Regeln werden nicht anwendbar); Ursprungsland und Einzelpreis werden aus dem MT700 gelesen, Unterschrift, Originalanzahl und Aussteller je Dokumenttyp aus Feld 46A als Vorschlag zum Übernehmen.
* Sanktionslisten-Warnung: Zu häufige Mails behoben. Eine unveränderte Liste gilt nach erfolgreichem Abruf als aktuell (bisher zählte nur das Datum der letzten Änderung), und in der ersten Stunde nach einem Start wird nicht gewarnt.
* Heimatmandant konfigurierbar (Migration V130, `platform_home_tenant`): Konten und Plattform-Anmeldung hängen nicht mehr fest am Mandanten `default`. Verhalten unverändert, solange die Einstellung auf `default` steht.
* Mandantenlöschung: Die Inventur meldet nicht mehr „nicht implementiert“, sondern ob die Löschung in der Installation freigeschaltet ist; `docker-compose.prod.yml` reicht `LC_TENANTS_TEST_PURGE_ENABLED` durch (Standard aus), siehe `docs/tenant-test-purge.md`.
* Datenbank: Row-Level-Security für Erfassungszugänge (`capture_token`, Migration V129) mit ausdrücklichem Plattform-Scope für die Token-Suche.
* Datenbank: Row-Level-Security für EBICS, SWIFT, Outbox, E-Mail-Versand und interne Regelpakete (Migration V128).
* Datenbank: Row-Level-Security für Workflows, Teams, Automatisierung und Mandanten-Einstellungen (Migration V127). Der Mandantenwechsel innerhalb einer Transaktion wirkt jetzt auf die Datenbanksitzung; `scripts/db-app-role.sh` vergibt das Ausführungsrecht für die Mitgliedschaftsfunktionen; die Mandanten-Abnahme der CI läuft mit der eingeschränkten Rolle.
* Datenbank: Row-Level-Security für Posteingang, Entwürfe und Trainingsdaten (Migration V126).
* Datenbank: Row-Level-Security für Akten, Dokumente, Änderungen, Aufgaben und deren Kindtabellen (Migration V125).
* Datenbank: Row-Level-Security für Kunden, Beschaffung, Gebühren, Vorlagen und Firmenprofil (Migration V124); die Löschfunktion für Mandantendaten läuft jetzt kontextunabhängig.
* Datenbank: Row-Level-Security für Prüfdaten (Entscheidungen, Notizen, Anforderungszuordnung, Vergleiche, Sanktionsfreigaben; Migration V123).
* Datenbank: Row-Level-Security auch für `intake_channel` und `inbox_capture_info` (Migration V122).
* Datenbank: Row-Level-Security für `learned_field_label` und `lc_review_step` (Pilot, Migration V121); die übrigen Mandanten-Tabellen folgen.
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
