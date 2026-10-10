# Fakten-Matrix: Quelle und Erkennungsstatus

Stand: automatisch aus dem Code erzeugt. Status: **erkannt** = Vorschlag aus MT700 bzw. Scan; **manuell** = bewusst Bearbeiter-Eingabe; **abgeleitet** = wird automatisch berechnet; **aus Akte** = kommt aus bereits erfassten Stammdaten; **offen** = noch keine Erkennung.

Zusammenfassung: abgeleitet 13, aus Akte 12, erkannt 101, manuell 8, offen 55 (insgesamt 189).

| Fakt | Status | Quelle |
|---|---|---|
| `DOCUMENT_ACCEPTED_FOR_CARRIAGE_INDICATED` | erkannt | Textsuche Dokument (DocumentIndicators) |
| `DOCUMENT_ADDITIONAL_COSTS_INDICATED` | erkannt | Textsuche Dokument (DocumentIndicators) |
| `DOCUMENT_AGENT_PRINCIPAL_INDICATED` | erkannt | Textsuche Dokument (DocumentIndicators) |
| `DOCUMENT_AMOUNT` | aus Akte | Stammdaten der Akte bzw. Dokument-Metadaten (bereits erfasst) |
| `DOCUMENT_AMOUNT_WORDS_MATCH_INDICATED` | offen | noch keine Erkennung |
| `DOCUMENT_APPLICANT_ADDRESS` | offen | noch keine Erkennung |
| `DOCUMENT_APPLICANT_ADDRESS_COUNTRY` | erkannt | Dokumenttext/OCR (DocumentFactSuggester, DocumentTextFacts, Packliste, Parteien) |
| `DOCUMENT_BENEFICIARY_ADDRESS_COUNTRY` | erkannt | Dokumenttext/OCR (DocumentFactSuggester, DocumentTextFacts, Packliste, Parteien) |
| `DOCUMENT_CARRIER` | erkannt | Dokumenttext/OCR (DocumentFactSuggester, DocumentTextFacts, Packliste, Parteien) |
| `DOCUMENT_CARRIER_INDICATED` | offen | noch keine Erkennung |
| `DOCUMENT_CERTIFIED_STATEMENT` | offen | noch keine Erkennung |
| `DOCUMENT_CHARTER_PARTY_INDICATED` | erkannt | Textsuche Dokument (DocumentIndicators) |
| `DOCUMENT_CLAIM_EXPIRY_INDICATED` | erkannt | Textsuche Dokument (DocumentIndicators) |
| `DOCUMENT_CLAUSE_INDICATED` | offen | noch keine Erkennung |
| `DOCUMENT_CONSIGNEE` | erkannt | Dokumenttext/OCR (DocumentFactSuggester, DocumentTextFacts, Packliste, Parteien) |
| `DOCUMENT_CONSIGNEE_ADDRESS_COUNTRY` | offen | noch keine Erkennung |
| `DOCUMENT_CONTAINERISED_SHIPMENT_INDICATED` | erkannt | Textsuche Dokument (DocumentIndicators) |
| `DOCUMENT_CORRECTIONS_PRESENT` | offen | noch keine Erkennung |
| `DOCUMENT_CORRECTION_AUTHENTICATED_INDICATED` | offen | noch keine Erkennung |
| `DOCUMENT_COUNTERSIGNATURE_PRESENT` | erkannt | Textsuche Dokument (DocumentIndicators) |
| `DOCUMENT_COUNTERSIGNATURE_REQUIRED` | offen | noch keine Erkennung |
| `DOCUMENT_COVERAGE_FROM` | erkannt | Dokumenttext/OCR (DocumentFactSuggester, DocumentTextFacts, Packliste, Parteien) |
| `DOCUMENT_COVERAGE_TO` | erkannt | Dokumenttext/OCR (DocumentFactSuggester, DocumentTextFacts, Packliste, Parteien) |
| `DOCUMENT_COVER_DATE_INDICATED` | erkannt | Textsuche Dokument (DocumentIndicators) |
| `DOCUMENT_CURRENCY` | aus Akte | Stammdaten der Akte bzw. Dokument-Metadaten (bereits erfasst) |
| `DOCUMENT_DATE` | aus Akte | Stammdaten der Akte bzw. Dokument-Metadaten (bereits erfasst) |
| `DOCUMENT_DEPARTURE_AIRPORT` | erkannt | Dokumenttext/OCR (DocumentFactSuggester, DocumentTextFacts, Packliste, Parteien) |
| `DOCUMENT_DESTINATION_AIRPORT` | erkannt | Dokumenttext/OCR (DocumentFactSuggester, DocumentTextFacts, Packliste, Parteien) |
| `DOCUMENT_DISCHARGE_PORT` | erkannt | Dokumenttext/OCR (DocumentFactSuggester, DocumentTextFacts, Packliste, Parteien) |
| `DOCUMENT_DISCOUNT_INDICATED` | erkannt | Textsuche Dokument (DocumentIndicators) |
| `DOCUMENT_DRAFT_TENOR_BASIS` | erkannt | Dokumenttext/OCR (DocumentFactSuggester, DocumentTextFacts, Packliste, Parteien) |
| `DOCUMENT_DRAFT_TENOR_DAYS` | erkannt | Dokumenttext/OCR (DocumentFactSuggester, DocumentTextFacts, Packliste, Parteien) |
| `DOCUMENT_DRAWEE` | erkannt | Dokumenttext/OCR (DocumentFactSuggester, DocumentTextFacts, Packliste, Parteien) |
| `DOCUMENT_ENDORSEMENT_PRESENT` | erkannt | Textsuche Dokument (DocumentIndicators) |
| `DOCUMENT_ENDORSEMENT_TO` | offen | noch keine Erkennung |
| `DOCUMENT_ENTIRE_CARRIAGE_SINGLE_DOC_INDICATED` | offen | noch keine Erkennung |
| `DOCUMENT_EXAMINATION_START_DATE` | manuell | Bearbeiter-Eingabe (Prüfungs-/Vorlagedaten) |
| `DOCUMENT_FORM_TYPE` | offen | noch keine Erkennung |
| `DOCUMENT_FRANCHISE_PRESENT` | erkannt | Textsuche Dokument (DocumentIndicators) |
| `DOCUMENT_FREIGHT_PREPAID` | erkannt | Dokumenttext/OCR (DocumentFactSuggester, DocumentTextFacts, Packliste, Parteien) |
| `DOCUMENT_FREIGHT_TERMS` | erkannt | Dokumenttext/OCR (DocumentFactSuggester, DocumentTextFacts, Packliste, Parteien) |
| `DOCUMENT_FULL_QUANTITY_INDICATED` | offen | noch keine Erkennung |
| `DOCUMENT_GOODS_DESCRIPTION` | aus Akte | Stammdaten der Akte bzw. Dokument-Metadaten (bereits erfasst) |
| `DOCUMENT_GROSS_WEIGHT` | erkannt | Dokumenttext/OCR (DocumentFactSuggester, DocumentTextFacts, Packliste, Parteien) |
| `DOCUMENT_INCOTERM` | erkannt | Dokumenttext/OCR (DocumentFactSuggester, DocumentTextFacts, Packliste, Parteien) |
| `DOCUMENT_INCOTERM_SOURCE` | erkannt | Dokumenttext/OCR (DocumentFactSuggester, DocumentTextFacts, Packliste, Parteien) |
| `DOCUMENT_INSURANCE_CURRENCY` | erkannt | Dokumenttext/OCR (DocumentFactSuggester, DocumentTextFacts, Packliste, Parteien) |
| `DOCUMENT_INSURANCE_EFFECTIVE_DATE` | erkannt | Dokumenttext/OCR (DocumentFactSuggester, DocumentTextFacts, Packliste, Parteien) |
| `DOCUMENT_INSURANCE_RISKS` | erkannt | Dokumenttext/OCR (DocumentFactSuggester, DocumentTextFacts, Packliste, Parteien) |
| `DOCUMENT_INSURANCE_TYPE` | erkannt | Dokumenttext/OCR (DocumentFactSuggester, DocumentTextFacts, Packliste, Parteien) |
| `DOCUMENT_INSURED_AMOUNT` | erkannt | Dokumenttext/OCR (DocumentFactSuggester, DocumentTextFacts, Packliste, Parteien) |
| `DOCUMENT_INSURED_PARTY` | erkannt | Dokumenttext/OCR (DocumentFactSuggester, DocumentTextFacts, Packliste, Parteien) |
| `DOCUMENT_INTENDED_PORT_INDICATED` | erkannt | Textsuche Dokument (DocumentIndicators) |
| `DOCUMENT_INTENDED_VESSEL_INDICATED` | erkannt | Textsuche Dokument (DocumentIndicators) |
| `DOCUMENT_INVOICE_REFERENCE` | erkannt | Dokumenttext/OCR (DocumentFactSuggester, DocumentTextFacts, Packliste, Parteien) |
| `DOCUMENT_IRRESPECTIVE_OF_PERCENTAGE` | erkannt | Textsuche Dokument (DocumentIndicators) |
| `DOCUMENT_ISSUED_ORIGINAL_COUNT` | offen | noch keine Erkennung |
| `DOCUMENT_ISSUER` | aus Akte | Stammdaten der Akte bzw. Dokument-Metadaten (bereits erfasst) |
| `DOCUMENT_LOADING_PORT` | erkannt | Dokumenttext/OCR (DocumentFactSuggester, DocumentTextFacts, Packliste, Parteien) |
| `DOCUMENT_NET_WEIGHT` | erkannt | Dokumenttext/OCR (DocumentFactSuggester, DocumentTextFacts, Packliste, Parteien) |
| `DOCUMENT_NOTIFY_ADDRESS_COUNTRY` | offen | noch keine Erkennung |
| `DOCUMENT_NOTIFY_PARTY` | erkannt | Dokumenttext/OCR (DocumentFactSuggester, DocumentTextFacts, Packliste, Parteien) |
| `DOCUMENT_NUMBER` | erkannt | Dokumenttext/OCR (DocumentFactSuggester, DocumentTextFacts, Packliste, Parteien) |
| `DOCUMENT_ON_BOARD_DATE` | erkannt | Dokumenttext/OCR (DocumentFactSuggester, DocumentTextFacts, Packliste, Parteien) |
| `DOCUMENT_ON_BOARD_NOTATION_PRESENT` | erkannt | Dokumenttext/OCR (DocumentFactSuggester, DocumentTextFacts, Packliste, Parteien) |
| `DOCUMENT_ON_DECK_INDICATED` | erkannt | Textsuche Dokument (DocumentIndicators) |
| `DOCUMENT_ORIGINAL_COUNT` | erkannt | Dokumenttext/OCR (DocumentFactSuggester, DocumentTextFacts, Packliste, Parteien) |
| `DOCUMENT_ORIGINAL_FOR_CONSIGNOR_INDICATED` | offen | noch keine Erkennung |
| `DOCUMENT_ORIGIN_COUNTRY` | erkannt | Dokumenttext/OCR (DocumentFactSuggester, DocumentTextFacts, Packliste, Parteien) |
| `DOCUMENT_PACKAGE_COUNT` | erkannt | Dokumenttext/OCR (DocumentFactSuggester, DocumentTextFacts, Packliste, Parteien) |
| `DOCUMENT_PACKING_DESCRIPTION` | offen | noch keine Erkennung |
| `DOCUMENT_PARTIAL_SHIPMENT_INDICATED` | erkannt | Textsuche Dokument (DocumentIndicators) |
| `DOCUMENT_PLACE_OF_DESTINATION` | offen | noch keine Erkennung |
| `DOCUMENT_PLACE_OF_FINAL_DESTINATION` | erkannt | Dokumenttext/OCR (DocumentFactSuggester, DocumentTextFacts, Packliste, Parteien) |
| `DOCUMENT_PLACE_OF_RECEIPT` | erkannt | Dokumenttext/OCR (DocumentFactSuggester, DocumentTextFacts, Packliste, Parteien) |
| `DOCUMENT_PLACE_OF_SHIPMENT` | offen | noch keine Erkennung |
| `DOCUMENT_PREMIUM_PAID_INDICATED` | erkannt | Textsuche Dokument (DocumentIndicators) |
| `DOCUMENT_PRESENTATION_DATE` | manuell | Bearbeiter-Eingabe (Prüfungs-/Vorlagedaten) |
| `DOCUMENT_PRESENTATION_GROUP` | manuell | Bearbeiter-Eingabe (Prüfungs-/Vorlagedaten) |
| `DOCUMENT_PRESHIPMENT_INDICATED` | erkannt | Textsuche Dokument (DocumentIndicators) |
| `DOCUMENT_PROVISIONAL_INDICATED` | erkannt | Textsuche Dokument (DocumentIndicators) |
| `DOCUMENT_QUANTITY` | erkannt | Dokumenttext/OCR (DocumentFactSuggester, DocumentTextFacts, Packliste, Parteien) |
| `DOCUMENT_QUANTITY_UNIT` | erkannt | Dokumenttext/OCR (DocumentFactSuggester, DocumentTextFacts, Packliste, Parteien) |
| `DOCUMENT_RECIPIENT` | aus Akte | Stammdaten der Akte bzw. Dokument-Metadaten (bereits erfasst) |
| `DOCUMENT_REFERS_TO_INVOICED_GOODS_INDICATED` | offen | noch keine Erkennung |
| `DOCUMENT_RELEASE_CONDITION_INDICATED` | offen | noch keine Erkennung |
| `DOCUMENT_SHIPMENT_COUNT` | offen | noch keine Erkennung |
| `DOCUMENT_SHIPMENT_DATE` | erkannt | Dokumenttext/OCR (DocumentFactSuggester, DocumentTextFacts, Packliste, Parteien) |
| `DOCUMENT_SHIPPED_ON_BOARD_INDICATED` | erkannt | Textsuche Dokument (DocumentIndicators) |
| `DOCUMENT_SHIPPING_MARKS` | offen | noch keine Erkennung |
| `DOCUMENT_SIGNED` | erkannt | Dokumenttext/OCR (DocumentFactSuggester, DocumentTextFacts, Packliste, Parteien) |
| `DOCUMENT_SIGNED_BY_AGENT_INDICATED` | erkannt | Textsuche Dokument (DocumentIndicators) |
| `DOCUMENT_SIGNED_FOR` | offen | noch keine Erkennung |
| `DOCUMENT_SIGNED_OR_STAMPED_PRESENT` | offen | noch keine Erkennung |
| `DOCUMENT_SIGNER_NAME` | offen | noch keine Erkennung |
| `DOCUMENT_SIGNER_ROLE` | offen | noch keine Erkennung |
| `DOCUMENT_TO_ORDER_INDICATED` | erkannt | Textsuche Dokument (DocumentIndicators) |
| `DOCUMENT_TRANSPORT_NOTATION` | offen | noch keine Erkennung |
| `DOCUMENT_TRANSSHIPMENT_INDICATED` | erkannt | Textsuche Dokument (DocumentIndicators) |
| `DOCUMENT_UNIT_PRICE_AMOUNT` | erkannt | Dokumenttext/OCR (DocumentFactSuggester, DocumentTextFacts, Packliste, Parteien) |
| `DOCUMENT_VALUE_INDICATED` | offen | noch keine Erkennung |
| `DOCUMENT_VESSEL` | erkannt | Dokumenttext/OCR (DocumentFactSuggester, DocumentTextFacts, Packliste, Parteien) |
| `DOCUMENT_WEIGHT_UNIT` | erkannt | Dokumenttext/OCR (DocumentFactSuggester, DocumentTextFacts, Packliste, Parteien) |
| `LC_ADDITIONAL_COSTS_ALLOWED` | offen | noch keine Erkennung |
| `LC_AMOUNT` | aus Akte | Stammdaten der Akte bzw. Dokument-Metadaten (bereits erfasst) |
| `LC_AMOUNT_TOLERANCE_ALLOWED` | erkannt | MT700-Feld (Mt700Facts) / Parteifelder :50:/:59: |
| `LC_APPLICANT` | aus Akte | Stammdaten der Akte bzw. Dokument-Metadaten (bereits erfasst) |
| `LC_APPLICANT_ADDRESS` | erkannt | MT700-Feld (Mt700Facts) / Parteifelder :50:/:59: |
| `LC_APPLICANT_ADDRESS_COUNTRY` | erkannt | MT700-Feld (Mt700Facts) / Parteifelder :50:/:59: |
| `LC_BENEFICIARY` | aus Akte | Stammdaten der Akte bzw. Dokument-Metadaten (bereits erfasst) |
| `LC_BENEFICIARY_ADDRESS_COUNTRY` | erkannt | MT700-Feld (Mt700Facts) / Parteifelder :50:/:59: |
| `LC_CIF_CIP_VALUE_AMOUNT` | offen | noch keine Erkennung |
| `LC_CLAIMED_AMOUNT` | offen | noch keine Erkennung |
| `LC_CONSIGNEE` | erkannt | MT700-Feld (Mt700Facts) / Parteifelder :50:/:59: |
| `LC_CONSIGNEE_ADDRESS_COUNTRY` | offen | noch keine Erkennung |
| `LC_CONSIGNMENT_TYPE` | offen | noch keine Erkennung |
| `LC_COVERAGE_FROM` | offen | noch keine Erkennung |
| `LC_COVERAGE_TO` | offen | noch keine Erkennung |
| `LC_CURRENCY` | aus Akte | Stammdaten der Akte bzw. Dokument-Metadaten (bereits erfasst) |
| `LC_DEPARTURE_AIRPORT` | erkannt | MT700-Feld (Mt700Facts) / Parteifelder :50:/:59: |
| `LC_DESTINATION_AIRPORT` | erkannt | MT700-Feld (Mt700Facts) / Parteifelder :50:/:59: |
| `LC_DISCHARGE_PORT` | erkannt | MT700-Feld (Mt700Facts) / Parteifelder :50:/:59: |
| `LC_DISCOUNT_REQUIRED` | offen | noch keine Erkennung |
| `LC_DOCUMENT_ISSUED_ORIGINAL_COUNT` | offen | noch keine Erkennung |
| `LC_DRAFT_TENOR_BASIS` | erkannt | MT700-Feld (Mt700Facts) / Parteifelder :50:/:59: |
| `LC_DRAFT_TENOR_DAYS` | erkannt | MT700-Feld (Mt700Facts) / Parteifelder :50:/:59: |
| `LC_DRAWEE` | erkannt | MT700-Feld (Mt700Facts) / Parteifelder :50:/:59: |
| `LC_ENDORSEMENT_REQUIRED` | erkannt | MT700-Feld (Mt700Facts) / Parteifelder :50:/:59: |
| `LC_ENDORSEMENT_TO` | offen | noch keine Erkennung |
| `LC_EXAMINATION_DECISION_DATE` | manuell | Bearbeiter-Eingabe (Prüfungs-/Vorlagedaten) |
| `LC_EXPIRY_DATE` | aus Akte | Stammdaten der Akte bzw. Dokument-Metadaten (bereits erfasst) |
| `LC_EXTENDED_EXPIRY_DATE` | offen | noch keine Erkennung |
| `LC_FRANCHISE_ALLOWED` | offen | noch keine Erkennung |
| `LC_FREIGHT_PREPAID_REQUIRED` | erkannt | MT700-Feld (Mt700Facts) / Parteifelder :50:/:59: |
| `LC_FREIGHT_TERMS` | erkannt | MT700-Feld (Mt700Facts) / Parteifelder :50:/:59: |
| `LC_GOODS_DESCRIPTION` | erkannt | MT700-Feld (Mt700Facts) / Parteifelder :50:/:59: |
| `LC_GROSS_GOODS_AMOUNT` | offen | noch keine Erkennung |
| `LC_INCOTERM` | erkannt | MT700-Feld (Mt700Facts) / Parteifelder :50:/:59: |
| `LC_INCOTERM_SOURCE` | erkannt | MT700-Feld (Mt700Facts) / Parteifelder :50:/:59: |
| `LC_INSTALMENT_PERIOD_END_DATE` | offen | noch keine Erkennung |
| `LC_INSTALMENT_SHIPMENT_REQUIRED` | offen | noch keine Erkennung |
| `LC_INSURANCE_BASE_AMOUNT` | offen | noch keine Erkennung |
| `LC_INSURANCE_MIN_PERCENT` | erkannt | MT700-Feld (Mt700Facts) / Parteifelder :50:/:59: |
| `LC_INSURANCE_RISKS` | erkannt | MT700-Feld (Mt700Facts) / Parteifelder :50:/:59: |
| `LC_INSURANCE_TYPE` | erkannt | MT700-Feld (Mt700Facts) / Parteifelder :50:/:59: |
| `LC_INSURED_PARTY` | offen | noch keine Erkennung |
| `LC_IRRESPECTIVE_OF_PERCENTAGE_REQUIRED` | erkannt | MT700-Feld (Mt700Facts) / Parteifelder :50:/:59: |
| `LC_LATEST_SHIPMENT_DATE` | aus Akte | Stammdaten der Akte bzw. Dokument-Metadaten (bereits erfasst) |
| `LC_LOADING_PORT` | erkannt | MT700-Feld (Mt700Facts) / Parteifelder :50:/:59: |
| `LC_NOTIFY_ADDRESS_COUNTRY` | offen | noch keine Erkennung |
| `LC_NOTIFY_PARTY` | erkannt | MT700-Feld (Mt700Facts) / Parteifelder :50:/:59: |
| `LC_ON_BOARD_NOTATION_REQUIRED` | erkannt | MT700-Feld (Mt700Facts) / Parteifelder :50:/:59: |
| `LC_ORIGIN_COUNTRY` | offen | noch keine Erkennung |
| `LC_PACKING_REQUIREMENT` | offen | noch keine Erkennung |
| `LC_PARTIAL_SHIPMENT_ALLOWED` | erkannt | MT700-Feld (Mt700Facts) / Parteifelder :50:/:59: |
| `LC_PLACE_OF_DESTINATION` | offen | noch keine Erkennung |
| `LC_PLACE_OF_FINAL_DESTINATION` | erkannt | MT700-Feld (Mt700Facts) / Parteifelder :50:/:59: |
| `LC_PLACE_OF_RECEIPT` | erkannt | MT700-Feld (Mt700Facts) / Parteifelder :50:/:59: |
| `LC_PLACE_OF_SHIPMENT` | offen | noch keine Erkennung |
| `LC_PREMIUM_PAID_REQUIRED` | erkannt | MT700-Feld (Mt700Facts) / Parteifelder :50:/:59: |
| `LC_PRESENTATION_DATE` | manuell | Bearbeiter-Eingabe (Prüfungs-/Vorlagedaten) |
| `LC_PRESENTATION_PERIOD_DAYS` | erkannt | MT700-Feld (Mt700Facts) / Parteifelder :50:/:59: |
| `LC_PRESHIPMENT_INSPECTION_REQUIRED` | erkannt | MT700-Feld (Mt700Facts) / Parteifelder :50:/:59: |
| `LC_QUANTITY` | erkannt | MT700-Feld (Mt700Facts) / Parteifelder :50:/:59: |
| `LC_QUANTITY_UNIT` | erkannt | MT700-Feld (Mt700Facts) / Parteifelder :50:/:59: |
| `LC_REQUIRED_FORM_TYPE` | erkannt | MT700-Feld (Mt700Facts) / Parteifelder :50:/:59: |
| `LC_REQUIRED_ISSUER` | manuell | Dokumentanforderung je Regel (nicht Teil der Akte) |
| `LC_REQUIRED_ORIGINAL_COUNT` | manuell | Dokumentanforderung je Regel (nicht Teil der Akte) |
| `LC_REQUIRED_STATEMENT` | offen | noch keine Erkennung |
| `LC_RULE_STANDARD` | erkannt | MT700-Feld (Mt700Facts) / Parteifelder :50:/:59: |
| `LC_SECOND_BENEFICIARY` | offen | noch keine Erkennung |
| `LC_SIGNATURE_REQUIRED` | manuell | Dokumentanforderung je Regel (nicht Teil der Akte) |
| `LC_TOLERANCE_PERCENT` | erkannt | MT700-Feld (Mt700Facts) / Parteifelder :50:/:59: |
| `LC_TRANSFERRED` | offen | noch keine Erkennung |
| `LC_TRANSSHIPMENT_ALLOWED` | erkannt | MT700-Feld (Mt700Facts) / Parteifelder :50:/:59: |
| `LC_UNIT_PRICE_AMOUNT` | offen | noch keine Erkennung |
| `PEER_AMOUNT` | abgeleitet | Vergleichsdokument (automatisch aus der Akte) |
| `PEER_CONSIGNEE` | abgeleitet | Vergleichsdokument (automatisch aus der Akte) |
| `PEER_CURRENCY` | abgeleitet | Vergleichsdokument (automatisch aus der Akte) |
| `PEER_DOCUMENT_NUMBER` | abgeleitet | Vergleichsdokument (automatisch aus der Akte) |
| `PEER_GOODS_DESCRIPTION` | abgeleitet | Vergleichsdokument (automatisch aus der Akte) |
| `PEER_GROSS_WEIGHT` | abgeleitet | Vergleichsdokument (automatisch aus der Akte) |
| `PEER_NET_WEIGHT` | abgeleitet | Vergleichsdokument (automatisch aus der Akte) |
| `PEER_PACKAGE_COUNT` | abgeleitet | Vergleichsdokument (automatisch aus der Akte) |
| `PEER_QUANTITY` | abgeleitet | Vergleichsdokument (automatisch aus der Akte) |
| `PEER_QUANTITY_UNIT` | abgeleitet | Vergleichsdokument (automatisch aus der Akte) |
| `PEER_SHIPMENT_DATE` | abgeleitet | Vergleichsdokument (automatisch aus der Akte) |
| `PEER_SHIPPING_MARKS` | abgeleitet | Vergleichsdokument (automatisch aus der Akte) |
| `PEER_WEIGHT_UNIT` | abgeleitet | Vergleichsdokument (automatisch aus der Akte) |
