# Corporate LC Manager – Fachliche Dokumentation

Stand: 05.10.2026 · Version 0.5.0-SNAPSHOT

## Zweck und fachlicher Rahmen

Der Corporate LC Manager führt Akkreditivdaten, Originaldokumente,
SWIFT-Nachrichten, Änderungen, Fristen und Prüfentscheidungen in einer LC-Akte
zusammen. Die Benutzer bearbeiten ihren Arbeitsvorrat, erstellen Dokumente und
prüfen Unterlagen gegen die gültige LC-Fassung.

Automatische Prüfungen unterstützen die fachliche Arbeit. Ein positives
Ergebnis bestätigt die implementierten Regeln; es ist keine vollständige
UCP-600-/ISBP-Prüfung und keine Bankfreigabe.

## Begriffe

| Begriff | Bedeutung |
|---|---|
| LC-Akte | Akkreditivdatensatz mit Dokumenten, Änderungen, Aufgaben und Chronik |
| Aktenreferenz | Eindeutige Referenz der Akte in der Anwendung |
| Referenz eigene Bank | Gesonderte Referenz der eigenen Bank |
| Referenz Fremdbank | Gesonderte Referenz der anderen/eröffnenden Bank |
| Amendment | Änderung eines bestehenden Akkreditivs, insbesondere aus MT707 |
| Training | Fachlich geprüfte Extraktion eines Dokuments; unabhängig von der Aktenanlage |
| Bankprofil | Vom Benutzer eingegebener BIC/Bankname zur Trennung gelernter Avisierungszuordnungen |
| OCR-Konfidenz | Messwert für die ursprüngliche OCR-Erkennung, getrennt von Zuordnungssicherheit |

## Navigation und Arbeitsplatz

Das linke Menü öffnet zuerst die jeweilige Seite. Von dort startet der Benutzer
bei Bedarf einen Bearbeitungsdialog.

- **Cockpit / Meine Arbeit:** Fristen, Wiedervorlagen, Zuständigkeiten und
  dringende Aufgaben.
- **LC-Akten:** Akten suchen, filtern, öffnen und bearbeiten; Dokumente und
  Annexe in der Dokumentenakte ansehen.
- **Dokumentenposteingang:** Noch nicht zugeordnete Dateien prüfen und einer
  bestehenden oder neuen Akte zuordnen.
- **Training & Dokumentenerkennung:** Erkennungsprofil wählen und einen
  SWIFT- oder Avisierungstrainingsdialog öffnen.
- **SWIFT-Import / Importhistorie:** Geschäftsnachrichten prüfen, übernehmen
  und erfolgreiche oder abgelehnte Importe nachvollziehen.
- **Administration:** Firmen, Vorlagen, Benutzer/Rollen, Freigabestufen,
  Audit und Betrieb abhängig von den Rechten.
- **Info & Lizenzen:** Copyright, GPL-Lizenz, OSS-Komponenten und Projektlinks.

Das Benutzermenü oben rechts bietet Profil, Bild, Passwort, TOTP und Abmeldung.

## LC-Akte und Änderungen

Eine Akte wird beispielsweise aus MT700 oder aus einem Avisierungsschreiben
im Posteingang angelegt. Sie enthält Antragsteller, Begünstigten, Banken,
Betrag/Währung, Laufzeit, Versandfristen, Dokumentenanforderungen, zusätzliche
Bedingungen, Zuständigkeit und Wiedervorlage.

Die Aktenreferenz ist von den beiden Bankreferenzen getrennt. „Unsere/Ihre
Referenz“ in einem Brief muss anhand der Bankrolle fachlich zugeordnet werden.

MT707-Änderungen werden der bestehenden Akte zugeordnet. Historie und
Vergleichsansichten helfen, alte und gültige Bedingungen zu unterscheiden.
MT760 hat ein eigenes Erkennungs-/Trainingsprofil; daraus entsteht derzeit
keine vollständige Garantienverwaltung.

## Dokumentenposteingang und Aktenablage

Einzeldateien oder ZIP-Archive können gesammelt hochgeladen werden. Die
Anwendung erstellt Zuordnungsvorschläge; der Benutzer bestätigt die Zielakte
und den Dokumenttyp. Unbekannte Dokumente können als Anlage geführt werden.

Für ein Avisierungsschreiben gibt es „Neue LC-Akte aus Avisierungsschreiben“.
Erkannte Angaben werden zunächst als Vorschläge dargestellt. Nach der
fachlichen Prüfung werden die neue Akte und das Original gemeinsam gespeichert.

Dokumente können angesehen, heruntergeladen, klassifiziert, mit Versionen
verglichen oder mit entsprechender Berechtigung gelöscht werden. Die
Aktenübersicht trennt Dokumentenarbeit und administrative Informationen.

Grenzen: 10 MB je Einzeldatei; Sammel-/ZIP-Importe insgesamt höchstens 50 MB
und 100 Nutzdateien. Nicht unterstützte oder unsichere Archive werden abgelehnt.

## Training: SWIFT und Avisierungsschreiben

1. Erkennungsprofil wählen und „Training öffnen“ klicken.
2. PDF laden. Für Avisierungen ein konsistentes Bankprofil (BIC/Bankname)
   angeben; identische Schreibweise auch beim späteren Import verwenden.
3. Den Originalausschnitt mit dem erkannten Wert vergleichen. Bei Bedarf
   vergrößern oder das gesamte Original-PDF öffnen.
4. Für jedes Feld eine Entscheidung treffen:
   - **Richtig erkannt:** unveränderten, zugeordneten Wert bestätigen.
   - **Korrektur bestätigen:** korrigierten Wert und gegebenenfalls neue
     Feldzuordnung bestätigen.
   - **Nicht verwendbar:** vollständig falsche oder irrelevante Erkennung
     verwerfen.
5. Nach erneuter Text-/Zuordnungsänderung das Feld erneut bestätigen.
6. „Nur Training abschließen“ wählen oder bestätigte Avisierungsangaben in
   eine neue LC-Akte übernehmen.

Bestätigungen werden sofort gespeichert. Ein bloßes Tippen ohne Bestätigung
liefert keine bestätigte Lernentscheidung. Ein Trainingsabschluss ist auch
ohne neue LC-Akte möglich; die vorhandenen Daten bleiben über die
Trainingshistorie verfügbar. Andere Benutzer können die zentralen Daten lesen;
die Bearbeitung eines Entwurfs ist dem Ersteller vorbehalten.

## Wie das Lernen arbeitet

Das Lernen ist eine regelbasierte Auswertung bestätigter Beispiele. Es ist
kein externes KI-Modelltraining. Gemeinsame Trainingsdaten verbessern die
Feldzuordnung und – profilspezifisch – Extraktionskorrekturen.

Bei Avisierungen werden Bankprofil und Feldbezeichnung ausgewertet. Eine
Zuordnung wird aktiv, wenn mindestens drei entsprechende Bestätigungen
vorliegen und mindestens 75 % der Stimmen übereinstimmen. Übertragen wird
die Feldzuordnung; Beträge oder Referenzen aus alten Avisierungen werden
nicht in neue Dokumente eingesetzt. Lernregeln können verwaltet/deaktiviert
werden. Ausgeblendete Trainingsvorlagen behalten ihre Lerninformationen.

## Bestätigtes Avisierungstraining in eine LC-Akte übernehmen

Nach Prüfung aller Felder erscheint bei passenden Rechten die Aktion
„Bestätigte Angaben in neue LC-Akte übernehmen“. Auch ein bereits
abgeschlossenes, noch nicht übernommenes eigenes Training kann genutzt werden.

Die Anlage-Maske enthält ausschließlich bestätigte und verwendbare Werte.
Widersprüchliche Zuordnungen oder nicht eindeutig normalisierbare Beträge/
Datumsangaben bleiben leer und erhalten einen Hinweis. Pflichtangaben sind
Aktenreferenz, Antragsteller, Begünstigter, positiver Betrag, Währung und
Verfallsdatum. Fehlende Angaben werden dort ergänzt und abschließend geprüft.

„LC-Akte anlegen & Original speichern“ erzeugt eine Akte mit dem
Avisierungsschreiben. Ein Link öffnet anschließend die neue Akte. Bei einer
bereits vergebenen Referenz bleibt die Maske samt Eingaben erhalten.
Ein Training kann nur einmal in eine neue Akte übernommen werden.

Ergänzungen in der Anlage-Maske ändern die Akte, nicht automatisch die
bestätigten Trainingsfelder. Fachliche Lernkorrekturen erfolgen in der
Feldprüfung vor dem Abschluss.

## Datumsformate und Qualität

SWIFT verwendet `YYMMDD`, beispielsweise `261130` für den 30.11.2026.
Die Anlage-Maske zeigt ein normales Datumsfeld; intern wird ISO `YYYY-MM-DD`
gespeichert. Angaben wie `1 / 4` oder unvollständige Datumswerte sind keine
gültigen Verfallsdaten.

Digitale PDFs nutzen die Textebene; Scans werden per OCR gelesen. Gemessene
OCR-Konfidenz und heuristische Zuordnungssicherheit sind unterschiedliche
Werte. Wiederholte/mehrdeutige Textstellen können keinen sicheren Ausschnitt
oder Messwert liefern. Auch automatisch sichere Werte müssen fachlich
bestätigt werden. Details: [OCR-Konfidenz](ocr-confidence.md).

## Dokumentenprüfung und Erstellung

Implementierte Vorprüfungen vergleichen Dokumentenanforderungen, Betrag,
Währung, Fristen, Referenzen, Parteien und ausgewählte Transport-/Warenangaben.
Befunde zeigen Regel, LC-Bedingung und Fundstelle, soweit diese eindeutig
ermittelbar ist. Unbekannte Bedingungen benötigen eine manuelle Entscheidung.

Handelsrechnungen, Packlisten und unterstützte Zertifikate können als Entwürfe
erfasst und als PDF/Word erzeugt werden. Firmenstammdaten und
firmenbezogene Word-Muster fließen in die Erstellung ein. Versionierte
Entwürfe werden geprüft, zur Freigabe vorgelegt und finalisiert. Ersteller
und Freigeber sind getrennt; konfigurierte Betragsstufen können mehrere
Freigeber verlangen.

## Rollen, Sicherheit und Protokoll

Benutzer erben die Rechte ihrer zugewiesenen Rolle. Rollen sind
administrierbar; einzelne Rechte steuern LC-Bearbeitung, Dokumente, Training,
Prüfung, Erstellung, Versand, Einstellungen, Benutzer und Audit.
Die Avisierungsübernahme benötigt Training verwalten, LC bearbeiten und
Dokumente hochladen sowie die Eigentümerschaft des Trainings.

Passwortänderung und TOTP sind vorhanden. Ein selbstbedienter
Passwort-vergessen-/E-Mail-Reset ist derzeit nicht implementiert.

Beim Anlegen und Speichern eines Benutzerkontos ist eine gültige E-Mail-Adresse
Pflicht. Sie ist unabhängig vom Benutzernamen und kann im eigenen Profil geändert
werden. Bestehende Konten ohne Adresse bleiben anmeldbar; das Profil und die
Benutzerliste weisen auf die notwendige Ergänzung hin. Es werden keine Adressen
aus Benutzernamen geraten und keine Bestätigungs- oder Reset-Mails automatisch
versendet. Die Prüfung validiert das Format, nicht die tatsächliche Zustellbarkeit.

Das Audit protokolliert relevante Aktionen und teilweise Vorher-/Nachher-Werte.
Die neue Avisierungsübernahme schreibt Aktenanlage, Dokumentablage und
Trainingsverknüpfung gemeinsam mit den Daten. Ein Audit ist kein
unveränderliches externes Archiv; Zugriff und Sicherung müssen betrieblich
geregelt werden.

## Geplant

Löschkonzept, Sanktionslisten-Screening und Compliance-Zugang sind
Roadmap-Punkte. Ebenso EBICS, Enterprise-SSO und umfassende Mandantentrennung.
Aktuelle Planung: [Roadmap](../ROADMAP.md).
