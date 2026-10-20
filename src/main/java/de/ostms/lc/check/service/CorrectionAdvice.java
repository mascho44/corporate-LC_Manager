package de.ostms.lc.check.service;

import de.ostms.lc.check.api.CheckResult.Severity;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * What to do about a finding: a short, concrete step for the document issuer, or the request for an amendment when the document cannot be
 * changed because the credit itself is the problem. First matching rule wins, so specific codes come before the general patterns.
 */
public final class CorrectionAdvice {
    private static final String CHANGE = " Ist das Dokument richtig und die Bedingung des Akkreditivs nicht erfüllbar, beim Käufer eine Änderung des Akkreditivs beantragen.";
    private static final String CHANGE_EN = " If the document is right and the condition of the credit cannot be met, ask the buyer for an amendment of the credit.";
    private record Advice(String german, String english) {}
    private static final Map<Pattern, Advice> RULES = new LinkedHashMap<>();

    private static void rule(String regex, String advice, String english) { RULES.put(Pattern.compile(regex), new Advice(advice, english)); }

    static {
        rule("MISSING_DOCUMENT", "Dokument beschaffen oder erstellen (Dokumentensatz erzeugen) und in der Akte hochladen.", "Obtain or create the document (create document set) and upload it to the file.");
        rule("INVOICE_AMOUNT_EXCEEDED|CUMULATIVE_INVOICE_AMOUNT_.*|DRAFT_AMOUNT_EXCEEDED|INVOICE_DRAFT_AMOUNT_.*",
            "Rechnung neu ausstellen und auf den noch verfügbaren Betrag des Akkreditivs begrenzen (Toleranz beachten)." + CHANGE, "Reissue the invoice and limit it to the amount still available under the credit (mind the tolerance)." + CHANGE_EN);
        rule(".*CURRENCY_MISMATCH|CAPTURED_CURRENCY_MISMATCH", "Währung im Dokument auf die Währung des Akkreditivs ändern und das Dokument neu ausstellen.", "Change the currency in the document to the currency of the credit and reissue the document.");
        rule("CAPTURED_AMOUNT_MISMATCH", "Erfassten Betrag mit dem Dokument vergleichen; stimmt das Dokument, den Betrag in der Akte korrigieren, sonst das Dokument neu ausstellen.", "Compare the captured amount with the document; if the document is right, correct the amount in the file, otherwise reissue the document.");
        rule(".*REFERENCE_(MISMATCH|NOT_FOUND)|LC_REFERENCE_.*", "Auf dem Dokument die Akkreditivnummer genau wie im Akkreditiv angeben, das Dokument neu ausstellen und hochladen.", "Show the credit number on the document exactly as in the credit, reissue the document and upload it.");
        rule("GOODS_DESCRIPTION_(MISMATCH|NOT_FOUND)|.*GOODS_REVIEW|ORIGIN_CERTIFICATE_GOODS_.*|QUALITY_CERTIFICATE_GOODS_.*|INSPECTION_GOODS_.*",
            "Warenbeschreibung auf dem Dokument wörtlich wie im Akkreditiv übernehmen (keine Abkürzungen, keine Umstellung) und neu ausstellen.", "Copy the description of goods on the document word for word from the credit (no abbreviations, no reordering) and reissue it.");
        rule("APPLICANT_NOT_FOUND|BENEFICIARY_NOT_FOUND|.*_BENEFICIARY|.*_APPLICANT|.*CONSIGNEE.*|.*NOTIFY.*|.*SENDER.*",
            "Name und Anschrift der Beteiligten auf dem Dokument exakt wie im Akkreditiv angeben und das Dokument korrigieren lassen.", "Show name and address of the parties on the document exactly as in the credit and have the document corrected.");
        rule("DUPLICATE_DOCUMENT_NUMBER", "Dokumentnummer eindeutig vergeben; doppelte Dokumente entfernen oder das Dokument mit neuer Nummer ausstellen.", "Use unique document numbers; remove duplicate documents or issue the document with a new number.");
        rule("DOCUMENT_AFTER_EXPIRY|LC_EXPIRED|PRESENTATION_.*|SHIPMENT_DATE_.*|AIR_SHIPMENT_DATE_.*|CMR_SHIPMENT_DATE_.*|AFTER_SHIPMENT",
            "Daten prüfen: Dokument- und Verladedatum, Vorlagefrist und Ablauf des Akkreditivs. Bei Überschreitung beim Käufer eine Verlängerung oder Friständerung beantragen, sonst Dokumentdatum berichtigen lassen.", "Check the dates: document and shipment date, presentation period and expiry of the credit. If exceeded, ask the buyer for an extension or a change of the period, otherwise have the document date corrected.");
        rule("INCOTERM_.*", "Incoterm samt Ort so angeben, wie ihn das Akkreditiv in der Warenbeschreibung nennt, und die Rechnung neu ausstellen.", "State the Incoterm with its place as the credit names it in the description of goods, and reissue the invoice.");
        rule("PACKAGES_MISSING|WEIGHTS_MISSING|PACKING_WEIGHT_IMPLAUSIBLE", "Packstücke und Gewichte in der Packliste vollständig und plausibel (netto kleiner gleich brutto) angeben und neu ausstellen.", "State packages and weights in the packing list completely and plausibly (net not above gross) and reissue it.");
        rule("INVOICE_PACKING_.*", "Rechnung und Packliste abstimmen: Beschreibung, Menge und Bezugsnummer müssen gleich lauten; das fehlerhafte Dokument neu ausstellen.", "Align invoice and packing list: description, quantity and reference must read the same; reissue the incorrect document.");
        rule("SIGNATURE_REQUIREMENT_.*", "Dokument unterschreiben (bei verlangtem Original handschriftlich oder wie im Akkreditiv gefordert) und die unterschriebene Fassung hochladen.", "Sign the document (by hand where an original is required, or as the credit demands) and upload the signed version.");
        rule("FULL_SET_BILL_OF_LADING|BILL_OF_LADING_.*", "Konnossement beim Spediteur oder der Reederei berichtigen lassen (Vollsatz, Notify-Adresse, Frachtvermerk, Indossament wie im Akkreditiv verlangt).", "Have the bill of lading corrected by the forwarder or shipping line (full set, notify address, freight clause, endorsement as required by the credit).");
        rule("CLEAN_ON_BOARD|TRANSPORT_.*|TRANSSHIPMENT_.*|PARTIAL_SHIPMENT_.*|PROHIBITION_VIOLATED|PORT_OF_.*|PLACE_OF_TAKING|FINAL_DESTINATION",
            "Transportdokument gegen die Transportbedingungen des Akkreditivs prüfen (Häfen, Umladung, Teilverladung, Aufnahmevermerk) und beim Frachtführer berichtigen lassen." + CHANGE, "Check the transport document against the transport terms of the credit (ports, transhipment, partial shipment, on-board notation) and have the carrier correct it." + CHANGE_EN);
        rule("AWB_.*|CMR_.*|AIR_WAYBILL_.*|COURIER_.*", "Frachtdokument beim Frachtführer berichtigen lassen (Absender, Empfänger, Frachtvermerk, Orte wie im Akkreditiv).", "Have the transport document corrected by the carrier (shipper, consignee, freight clause, places as in the credit).");
        rule("INSURANCE_.*", "Versicherungsdokument beim Versicherer berichtigen lassen (Deckung, Betrag und Währung, Beginn spätestens am Verladetag, Zahlstelle wie im Akkreditiv).", "Have the insurance document corrected by the insurer (cover, amount and currency, effective no later than the shipment date, place of claims as in the credit).");
        rule("ORIGIN_CERTIFICATE_.*|CHAMBER_ISSUER_.*|COUNTRY_OF_ORIGIN_.*|ORIGIN_COUNTRY_.*", "Ursprungszeugnis oder Ursprungsangabe berichtigen lassen (Ursprungsland und Aussteller wie im Akkreditiv verlangt).", "Have the certificate of origin or origin statement corrected (country of origin and issuer as required by the credit).");
        rule("INSPECTION_.*|QUALITY_.*", "Prüf- oder Qualitätszertifikat beim Aussteller berichtigen lassen (Bezug, Datum, Aussteller und Angaben wie im Akkreditiv verlangt).", "Have the inspection or quality certificate corrected by its issuer (reference, date, issuer and details as required by the credit).");
        rule("BENEFICIARY_CERTIFICATE_.*|BENEFICIARY_STATEMENT_.*", "Begünstigtenerklärung im Wortlaut des Akkreditivs neu ausstellen (Dokumentensatz erzeugen) und unterschreiben.", "Reissue the beneficiary's certificate in the wording of the credit (create document set) and sign it.");
        rule("DRAFT_.*", "Tratte nach den Vorgaben des Akkreditivs neu ausfertigen (Betrag, Laufzeit, Bezogener, Bezug).", "Draw the bill of exchange again as the credit requires (amount, tenor, drawee, reference).");
        rule("ADDITIONAL_COND_.*|ADDITIONAL_CONDITIONS_.*", "Zusatzbedingung (Feld 47A) in den betroffenen Dokumenten umsetzen, zum Beispiel Akkreditivnummer oder englische Sprache, und neu ausstellen.", "Apply the additional condition (field 47A) to the documents concerned, for example the credit number or English language, and reissue them.");
        rule("LC_AMENDMENT_PENDING", "Offene Änderung des Akkreditivs zuerst annehmen oder ablehnen; erst danach die Dokumente prüfen und einreichen.", "Accept or reject the pending amendment of the credit first; only then check and present the documents.");
        rule("NO_TEXT|PDF_REQUIRES_OCR|EXTRACTION_FAILED|FORMAT_NOT_EXTRACTED|UNSUPPORTED", "Dokument lesbar bereitstellen (Text-PDF statt Foto oder Scan) und erneut hochladen, damit die Prüfung es lesen kann.", "Provide a readable document (text PDF instead of a photo or scan) and upload it again so that the check can read it.");
        rule("UNCLASSIFIED_REQUIREMENT|REVIEW_STALE|.*_REVIEW|DATE_NOT_CAPTURED", "Bedingung selbst gegen das Dokument halten und das Ergebnis in der Akte vermerken; fehlende Daten in den Prüfdaten ergänzen.", "Compare the condition with the document yourself and note the result in the file; add missing data in the review data.");
    }

    private CorrectionAdvice() {}

    /** The advice for an open finding; null for findings that are fine or have no standard step. */
    public static String forFinding(String code, Severity severity) { return find(code, severity, false); }

    /** The same advice in English, for the English interface. */
    public static String forFindingEnglish(String code, Severity severity) { return find(code, severity, true); }

    private static String find(String code, Severity severity, boolean english) {
        if (code == null || severity == null || severity == Severity.OK) return null;
        for (var rule : RULES.entrySet()) if (rule.getKey().matcher(code).matches()) return english ? rule.getValue().english() : rule.getValue().german();
        return null;
    }
}
