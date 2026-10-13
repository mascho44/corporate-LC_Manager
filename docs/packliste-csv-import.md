# Packliste: CSV-Import der Positionen

Beim Erstellen einer **Packliste** oder **Handelsrechnung** gibt es im Bereich *Positionen* den Knopf **CSV importieren**.
Es gibt kein festes Format: Die Datei kann aus jedem System kommen (ERP, Excel, Speditionssoftware).

## Ablauf

1. CSV-Datei wählen. Sie wird nur im Browser gelesen; erst das erstellte Dokument geht an den Server.
2. Der Dialog erkennt Zeichensatz (UTF-8 oder Windows-1252, wie ihn deutsches Excel erzeugt), Trennzeichen (`;` `,` Tabulator `|`) und die Spalten
   anhand der Spaltennamen (deutsch und englisch, z. B. *Warenbezeichnung*, *Description*, *Menge*, *Qty*, *Nettogewicht*, *Gross weight*, *Colli*).
3. Zuordnung bei Bedarf ändern, Trennzeichen oder „erste Zeile enthält Spaltennamen" umstellen. Die Vorschau zeigt die ersten Positionen und Hinweise.
4. Positionen **ersetzen** oder **anhängen**. Danach sind es normale Positionszeilen und lassen sich weiter bearbeiten.

Die Zuordnung wird im Browser gemerkt (je Satz von Spaltennamen); dieselbe Exportdatei braucht beim nächsten Mal keine Eingabe mehr.

## Regeln

* Felder: Position, Beschreibung, Menge, Einheit, Einzelpreis, Gesamt, Packstücke, Netto kg, Brutto kg. Nur die Beschreibung ist Pflicht;
  Zeilen ohne Beschreibung werden übersprungen und gemeldet.
* Zahlen: deutsche (`1.234,56`) und englische (`1,234.56`) Schreibweise, Einheiten wie `12 kg` oder `€ 99,90`. Eindeutige Werte der Datei legen die Schreibweise fest;
  bei unklaren wie `1.250` gilt Deutsch (Tausenderpunkt). Nicht lesbare Zahlen bleiben leer und werden gemeldet.
* Sind die Summenfelder (Packstücke, Netto, Brutto) im Dokument leer, werden sie aus den Positionen addiert.

## Beispiel

```
Pos;Warenbezeichnung;Menge;ME;Packstücke;Nettogewicht;Bruttogewicht
1;Schrauben M8;1.000;Stk;2;10,5;12,0
2;Muttern M8;2.500;Stk;1;4,25;5,0
```

Eine direkte Schnittstelle für Systeme (REST mit Schlüssel) ist ein möglicher nächster Schritt.
