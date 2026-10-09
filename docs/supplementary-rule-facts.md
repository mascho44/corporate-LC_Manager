# Supplementary rule facts

Schema 4 accepts additional explicitly reviewed document and LC context. These fields contain facts, not publication text or bundled external rule packs. Source references in a rule do not establish compliance or licensing rights.

Use **Document rule facts**, **LC rule context**, and **LC requirements for this document type** in the LC file. Values are persisted in the existing tenant-scoped JSON facts; existing transactional audit and review-invalidation handling applies. Empty means unknown, never false. No database migration is required.

New facts cover presentation date, transshipment and partial-shipment indicators, shipment count, on-board presence/date and intended-vessel indication, consignee/notify party and address countries, prepaid freight and freight terms, Incoterm and its source, required issuer per document type, invoice reference, franchise and percentage wording, insured party and endorsement, draft drawee/tenor/basis, package count and shipping marks, and signer name.

Presentation date can be the left operand as either DOCUMENT_PRESENTATION_DATE or LC_PRESENTATION_DATE, compared to LC_EXPIRY_DATE. Other left operands remain document facts. DOCUMENT_SHIPMENT_DATE and DOCUMENT_ON_BOARD_DATE can also compare to DOCUMENT_PRESENTATION_DATE on the right, including WITHIN_DAYS (start on the left, presentation on the right). No other document/document pair is enabled. Existing DOCUMENT_SIGNED, DOCUMENT_SIGNER_ROLE, DOCUMENT_SIGNED_FOR and DOCUMENT_TRANSPORT_NOTATION remain available.

PEER_DOCUMENT_NUMBER uses the comparison document's captured document number. PEER_PACKAGE_COUNT and PEER_SHIPPING_MARKS use reviewed facts. Peer selection still requires a unique document of the specified type, optionally within the same presentation group; ambiguity produces no invented match.

LC_REQUIRED_ISSUER is saved per document type, not as a global issuer. Incoterm sources are provenance context, not comparison operands. Booleans only accept true/false; dates are ISO dates, and counts/tenor days are nonnegative integers.

This adds input and engine capabilities, not OCR auto-extraction, legal interpretations or complete rule packs. In particular, a shipment count alone does not prove partial shipment, and an indicated transshipment alone does not establish an impermissible transshipment. Pack authors must model conditions and request human review where context matters.

## Field identifiers

The identifiers below are accepted by schema 4. Existing identifiers remain unchanged. The UI supplies type-specific controls from the backend definitions.

| Identifier | Meaning |
| --- | --- |
| `DOCUMENT_PRESENTATION_DATE` | Geprüftes Vorlagedatum |
| `DOCUMENT_TRANSSHIPMENT_INDICATED` | Umladung angezeigt? |
| `LC_TRANSSHIPMENT_ALLOWED` | Umladung erlaubt? |
| `LC_PARTIAL_SHIPMENT_ALLOWED` | Teilverladung erlaubt? |
| `DOCUMENT_SHIPMENT_COUNT` | Geprüfte Anzahl Sendungen |
| `DOCUMENT_ON_BOARD_NOTATION_PRESENT` | Bordvermerk vorhanden? |
| `DOCUMENT_ON_BOARD_DATE` | Geprüftes Borddatum |
| `DOCUMENT_INTENDED_VESSEL_INDICATED` | Intended vessel angezeigt? |
| `LC_ON_BOARD_NOTATION_REQUIRED` | Bordvermerk gefordert? |
| `DOCUMENT_CONSIGNEE` | Empfänger / Consignee |
| `DOCUMENT_NOTIFY_PARTY` | Meldeadresse / Notify party |
| `DOCUMENT_APPLICANT_ADDRESS_COUNTRY` | Land der Auftraggeberadresse |
| `DOCUMENT_BENEFICIARY_ADDRESS_COUNTRY` | Land der Begünstigtenadresse |
| `LC_APPLICANT_ADDRESS_COUNTRY` | LC: Land der Auftraggeberadresse |
| `LC_BENEFICIARY_ADDRESS_COUNTRY` | LC: Land der Begünstigtenadresse |
| `LC_CONSIGNEE` | Geforderter Empfänger |
| `LC_NOTIFY_PARTY` | Geforderte Meldeadresse |
| `DOCUMENT_FREIGHT_PREPAID` | Fracht vorausbezahlt? |
| `DOCUMENT_FREIGHT_TERMS` | Geprüfte Frachtbedingung |
| `LC_FREIGHT_PREPAID_REQUIRED` | Frachtvorauszahlung gefordert? |
| `LC_FREIGHT_TERMS` | Geforderte Frachtbedingung |
| `DOCUMENT_INCOTERM` | Handelsklausel im Dokument |
| `DOCUMENT_INCOTERM_SOURCE` | Fundstelle der Handelsklausel |
| `LC_INCOTERM` | Vereinbarte Handelsklausel |
| `LC_INCOTERM_SOURCE` | Quelle der vereinbarten Handelsklausel |
| `LC_REQUIRED_ISSUER` | Geforderter Aussteller für diesen Dokumenttyp |
| `DOCUMENT_INVOICE_REFERENCE` | Rechnungsnummer-Bezug |
| `PEER_DOCUMENT_NUMBER` | Dokumentnummer des Vergleichsdokuments |
| `DOCUMENT_FRANCHISE_PRESENT` | Franchise / Selbstbehalt vorhanden? |
| `DOCUMENT_IRRESPECTIVE_OF_PERCENTAGE` | Irrespective of percentage angegeben? |
| `LC_FRANCHISE_ALLOWED` | Franchise / Selbstbehalt erlaubt? |
| `LC_IRRESPECTIVE_OF_PERCENTAGE_REQUIRED` | Irrespective of percentage gefordert? |
| `DOCUMENT_INSURED_PARTY` | Versicherter |
| `LC_INSURED_PARTY` | Geforderter Versicherter |
| `DOCUMENT_ENDORSEMENT_PRESENT` | Indossament vorhanden? |
| `DOCUMENT_ENDORSEMENT_TO` | Indossiert an |
| `LC_ENDORSEMENT_REQUIRED` | Indossament gefordert? |
| `LC_ENDORSEMENT_TO` | Geforderter Indossamentempfänger |
| `DOCUMENT_DRAWEE` | Bezogener der Tratte |
| `LC_DRAWEE` | Vereinbarter Bezogener |
| `DOCUMENT_DRAFT_TENOR_DAYS` | Trattenlaufzeit in Tagen |
| `LC_DRAFT_TENOR_DAYS` | Vereinbarte Trattenlaufzeit in Tagen |
| `DOCUMENT_DRAFT_TENOR_BASIS` | Bezugsereignis der Trattenlaufzeit |
| `LC_DRAFT_TENOR_BASIS` | Vereinbartes Bezugsereignis der Laufzeit |
| `DOCUMENT_PACKAGE_COUNT` | Packstückzahl |
| `PEER_PACKAGE_COUNT` | Packstückzahl des Vergleichsdokuments |
| `DOCUMENT_SHIPPING_MARKS` | Markierungen / Shipping marks |
| `PEER_SHIPPING_MARKS` | Markierungen des Vergleichsdokuments |
| `DOCUMENT_SIGNER_NAME` | Name des Unterzeichners |
| `DOCUMENT_PARTIAL_SHIPMENT_INDICATED` | Teilverladung fachlich festgestellt |
| `DOCUMENT_CONSIGNEE_ADDRESS_COUNTRY` | Land der Empfängeradresse |
| `DOCUMENT_NOTIFY_ADDRESS_COUNTRY` | Land der Meldeadresse |
| `LC_CONSIGNEE_ADDRESS_COUNTRY` | LC: Land der Empfängeradresse |
| `LC_NOTIFY_ADDRESS_COUNTRY` | LC: Land der Meldeadresse |
| `DOCUMENT_NUMBER` | Erfasste Dokumentnummer (bestehende Metadaten, nicht zusätzlich als Prüffakt speicherbar) |
