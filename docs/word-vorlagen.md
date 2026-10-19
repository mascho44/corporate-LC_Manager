# Word-Vorlagen für erzeugte Dokumente

Für Handelsrechnung, Packliste, Ursprungszeugnis, Begünstigtenbescheinigung und Qualitätszertifikat gibt es Mustervorlagen (DOCX). Sie lassen sich in der App herunterladen, in Word gestalten und als Firmenvorlage wieder hochladen.

## Aufbau der Mustervorlage

- **Briefkopf:** Firmenname, Anschrift, Telefon, E-Mail und Ansprechpartner links, Logo rechts, darunter eine Linie.
- **Titelleiste** mit Dokumentart und Nummer.
- **Kopfblock** in zwei Spalten: Verkäufer/Begünstigter und Käufer/Auftraggeber links; Dokumentnummer, Datum und Akkreditivreferenz sowie Verladehafen, Zielhafen, Incoterm und Zahlungsbedingung rechts (Incoterm und Zahlungsbedingung nur bei der Rechnung).
- **Positionstabelle** mit Kopfzeile, abwechselnd schattierten Zeilen und rechtsbündigen Zahlen; bei der Rechnung mit Summenzeile, bei der Packliste mit Kolli und Gewichten.
- **Summen:** bei der Rechnung Gesamtbetrag, Betrag in Worten („SAY: EUR … ONLY“) und Bankverbindung; bei der Packliste Kolli, Netto- und Bruttogewicht.
- **Bemerkungen**, ein **Erklärungstext** je Dokumentart und der **Unterschriftsblock** mit Ort, Datum und Firma.
- **Fußzeile** mit Firmenname, Anschrift, USt-ID, Handelsregister, Bank, IBAN, BIC und Seitenzahl.

## Platzhalter

Die Platzhalter stehen in doppelten geschweiften Klammern und werden beim Erzeugen aus dem Akkreditiv, der Firma und den Eingaben gefüllt.

| Bereich | Platzhalter |
|---|---|
| Dokument | `{{title}}`, `{{documentNumber}}`, `{{documentDate}}` (Format „05 Oct 2026“), `{{notes}}`, `{{itemsTable}}` (Positionstabelle, als `{{#itemsTable}}`) |
| Akkreditiv | `{{lcReference}}`, `{{beneficiary}}`, `{{applicant}}`, `{{amount}}` (Akkreditivbetrag), `{{portOfLoading}}`, `{{portOfDischarge}}`, `{{incoterm}}`, `{{paymentTerms}}` |
| Beträge | `{{totalAmount}}` (Summe der Positionen), `{{amountInWords}}`, `{{quantity}}`, `{{packages}}`, `{{netWeight}}`, `{{grossWeight}}`, `{{description}}` |
| Firma | `{{companyName}}`, `{{companyAddress}}`, `{{companyPostalCode}}`, `{{companyCity}}`, `{{companyCountry}}`, `{{companyEmail}}`, `{{companyPhone}}`, `{{companyTaxId}}`, `{{companyRegistrationNumber}}`, `{{companyBankName}}`, `{{companyIban}}`, `{{companyBic}}`, `{{companyContactPerson}}`, `{{@companyLogo}}` (Bild) |

Zahlen erscheinen mit Tausendertrennzeichen und zwei Nachkommastellen („125,000.00“). Fehlende Angaben werden als „-“ ausgegeben. Platzhalter funktionieren im Text, in Tabellen und in der Fußzeile.
