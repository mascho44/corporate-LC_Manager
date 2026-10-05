# Fachliche LC-Bedingungen und Gebührenschätzungen

In einer geöffneten LC-Akte stehen die Aktionen „LC-Bedingungen“ und
„Gebühren & Provisionen“ bereit. Sie öffnen erst nach ausdrücklichem Klick
eine Bearbeitungsansicht; das Hauptmenü öffnet keine neuen Dialoge.

## Formatneutrale Bedingungen

Fachliche Werte werden unabhängig von SWIFT-Feldnummern gespeichert.
Vorhandene importierte Werte bleiben über einen Adapter lesbar.
Explizit gespeicherte Bedingungen haben Vorrang; ein leer gespeicherter Wert
unterdrückt den alten Importwert. Nur tatsächlich geänderte Felder werden
gespeichert. Änderungen benötigen LC_EDIT und werden auditiert.
MT707-Waren- und Zusatzbedingungsänderungen aktualisieren beide Darstellungen.

## Gebühren-MVP

Administratoren mit SETTINGS_MANAGE legen unveränderliche Tarifprofile an;
eine neue Tarifversion wird als neues Profil angelegt. Es gibt keine
vorgegebenen oder erfundenen Banktarife. Sachbearbeiter mit LC_EDIT wählen
ein Profil in LC-Währung und geben die Anzahl der Tarif-Einheiten an.

Pro Einheit wird LC-Betrag × Prozent / 100 + Fixbetrag berechnet, begrenzt
durch Minimum und optional Maximum. Danach wird mit der Anzahl multipliziert
und jede Position auf die ISO-Währungsstellen mit HALF_UP gerundet.
Die Gesamtsumme ist die Summe dieser gerundeten Positionen.
Profilstand, Rechenergebnis und Bearbeiter werden gespeichert und auditiert.

Dies sind Schätzungen, keine Bankabrechnungen. Steuern, Fremdwährungsumrechnung,
automatische Periodenermittlung und Zahlungs-/Abrechnungsstatus sind nicht
Teil dieses MVP. Originalunterlagen für ICC-Regeln werden nicht mit ausgeliefert.
