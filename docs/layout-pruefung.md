# Layout prüfen

Ziel: Fehler wie verrutschte Spalten, überlappende Knöpfe, abgeschnittener Text oder überbreite Seiten **vor** der Auslieferung finden, nicht erst im Betrieb.

## Regeln (`scripts/layout-rules.js`)
`window.lcLayoutCheck()` prüft die gerade angezeigte Seite und liefert eine Liste von Funden (leer = keine Regel verletzt):

| Regel | Findet |
| --- | --- |
| `pageOverflow` | die Seite ist breiter als das Fenster (seitliches Scrollen) |
| `covered` | ein Bedienelement, über dem ein anderes Element liegt (z. B. Knopf von Text verdeckt) |
| `overlap` | zwei Bedienelemente liegen übereinander |
| `cutOff` | ein Bedienelement ragt aus einem beschneidenden Elternelement heraus |
| `clippedText` | Text wird ohne Auslassungspunkte abgeschnitten |
| `controlSize` | Knopf/Feld außerhalb 28–44 px Höhe (Standard aus `ui-system.css`: 36 px, klein 30 px) |

Einsatz: Skript in die Browser-Konsole kopieren und `lcLayoutCheck()` aufrufen, oder mit Playwright per `page.evaluate(src)` einspielen. Getestet an Testseiten: das frühere Raster der Dokumentzeile (Vorschau als zusätzliches Rasterelement) wird als `overlap` gemeldet, die korrigierte Fassung ist ohne Befund.

## Testinstallation mit Beispieldaten (`scripts/layout-stack.sh`)
`scripts/layout-stack.sh up` startet eine **wegwerfbare, rein lokale** Installation (Docker, Port 18086, synthetische Zugangsdaten aus `docker-compose.tenant-test.yml`) und legt Beispieldaten an:
zwei Mandanten (`layout-corp` nur Corporate, `layout-both` Bank und Corporate) mit LC-Akten (eine als Importeur), Dokumenten, Posteingang (Einreichungsschreiben, lange Dateinamen), Kundenstamm, Erfassungszugang und Vorlage.
Danach die Seiten im Browser öffnen, anmelden (Benutzer `admin`, Code per `scripts/layout-stack.sh code`) und `lcLayoutScan()` ausführen (scrollt die Seite durch und sammelt die Funde; `lcLayoutCheck()` prüft nur den sichtbaren Ausschnitt). `scripts/layout-stack.sh down` entfernt alles.
Nie gegen die Produktivinstallation verwenden.

## Was der erste Durchlauf an der echten Anwendung gefunden hat
Behoben: LC-Liste (Betrag und „Akte öffnen" liefen aus dem Rahmen), Abschnittsleisten mit vielen Knöpfen, uneinheitliche Reiter einer Akte (späte Reiter als blaue Knöpfe, letzter Reiter hinter Scrollbalken),
Aufgabenformular (Felder lagen übereinander), Auswahlkästchen im Posteingang (330 px breit, schwebten über dem Dateinamen), Beschaffungstabelle (Hinweisspalte abgeschnitten),
Speichern-Knopf der Gruppeninbox. Außerdem: Knöpfe und Eingabefelder wurden in Arial 13 px gezeichnet statt in der Seitenschrift; das Menü wirkte dadurch unscharf.
Inhaltlicher Fund: Anforderungen im Plural („bills of lading", „certificates of origin") wurden nicht als Dokumenttyp erkannt.

## Noch offen
Automatischer Durchlauf aller Schlüsselansichten in drei Breiten als Browser-Test in der CI, danach Bildvergleich gegen freigegebene Referenzbilder.
