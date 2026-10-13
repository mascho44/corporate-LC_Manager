# Avis der Bank einlesen und LC-Akte anlegen

Ein Avis besteht aus dem **Anschreiben der Bank** und dem **Akkreditivtext**. Im Posteingang gibt es bei PDFs den Knopf **Als Avis einlesen …**.

1. Der Text der PDF (Texterkennung ist abgeschlossen) wird auf die gedruckten SWIFT-Felder des Akkreditivs durchsucht: Felder in der Form
   `:20:…`, `:32B:…` oder als Tabelle (`20 Documentary Credit Number …`). Das Anschreiben davor und die Grußformel danach (z. B. „Mit freundlichen Grüßen",
   „Best regards") werden ignoriert.
2. Die Vorschau zeigt Referenz, Auftraggeber, Begünstigten, Betrag, Ablauf und die geforderten Dokumente. Es gelten dieselben Prüfungen wie beim
   SWIFT-Import (Pflichtfelder :31D: und :32B:, Referenz noch nicht vorhanden). Fehler sperren das Anlegen.
3. **LC-Akte aus Avis anlegen** erzeugt die Akte aus allen Feldern des Akkreditivtextes (wie ein MT700-Import, inkl. Dokumentenanforderungen, spätester
   Versand, Warenbeschreibung) und legt den Avis in der Akte als Dokument „Avisierungsschreiben" ab. Beides geschieht in einer Transaktion.

Die Funktion steht Bank- und Corporate-Mandanten zur Verfügung (Recht „LC bearbeiten" und „Dokumente hochladen"). Erkannte Angaben sind Vorschläge; bitte mit
dem Original vergleichen. Enthält ein Avis den Akkreditivtext nicht in dieser Form, meldet die Vorschau das, und die Akte wird manuell angelegt.
