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
    private static final Map<Pattern, String> RULES = new LinkedHashMap<>();

    private static void rule(String regex, String advice) { RULES.put(Pattern.compile(regex), advice); }

    static {
        rule("MISSING_DOCUMENT", "Dokument beschaffen oder erstellen (Dokumentensatz erzeugen) und in der Akte hochladen.");
        rule("INVOICE_AMOUNT_EXCEEDED|CUMULATIVE_INVOICE_AMOUNT_.*|DRAFT_AMOUNT_EXCEEDED|INVOICE_DRAFT_AMOUNT_.*",
            "Rechnung neu ausstellen und auf den noch verfügbaren Betrag des Akkreditivs begrenzen (Toleranz beachten)." + CHANGE);
        rule(".*CURRENCY_MISMATCH|CAPTURED_CURRENCY_MISMATCH", "Währung im Dokument auf die Währung des Akkreditivs ändern und das Dokument neu ausstellen.");
        rule("CAPTURED_AMOUNT_MISMATCH", "Erfassten Betrag mit dem Dokument vergleichen; stimmt das Dokument, den Betrag in der Akte korrigieren, sonst das Dokument neu ausstellen.");
        rule(".*REFERENCE_(MISMATCH|NOT_FOUND)|LC_REFERENCE_.*", "Auf dem Dokument die Akkreditivnummer genau wie im Akkreditiv angeben, das Dokument neu ausstellen und hochladen.");
        rule("GOODS_DESCRIPTION_(MISMATCH|NOT_FOUND)|.*GOODS_REVIEW|ORIGIN_CERTIFICATE_GOODS_.*|QUALITY_CERTIFICATE_GOODS_.*|INSPECTION_GOODS_.*",
            "Warenbeschreibung auf dem Dokument wörtlich wie im Akkreditiv übernehmen (keine Abkürzungen, keine Umstellung) und neu ausstellen.");
        rule("APPLICANT_NOT_FOUND|BENEFICIARY_NOT_FOUND|.*_BENEFICIARY|.*_APPLICANT|.*CONSIGNEE.*|.*NOTIFY.*|.*SENDER.*",
            "Name und Anschrift der Beteiligten auf dem Dokument exakt wie im Akkreditiv angeben und das Dokument korrigieren lassen.");
        rule("DUPLICATE_DOCUMENT_NUMBER", "Dokumentnummer eindeutig vergeben; doppelte Dokumente entfernen oder das Dokument mit neuer Nummer ausstellen.");
        rule("DOCUMENT_AFTER_EXPIRY|LC_EXPIRED|PRESENTATION_.*|SHIPMENT_DATE_.*|AIR_SHIPMENT_DATE_.*|CMR_SHIPMENT_DATE_.*|AFTER_SHIPMENT",
            "Daten prüfen: Dokument- und Verladedatum, Vorlagefrist und Ablauf des Akkreditivs. Bei Überschreitung beim Käufer eine Verlängerung oder Friständerung beantragen, sonst Dokumentdatum berichtigen lassen.");
        rule("INCOTERM_.*", "Incoterm samt Ort so angeben, wie ihn das Akkreditiv in der Warenbeschreibung nennt, und die Rechnung neu ausstellen.");
        rule("PACKAGES_MISSING|WEIGHTS_MISSING|PACKING_WEIGHT_IMPLAUSIBLE", "Packstücke und Gewichte in der Packliste vollständig und plausibel (netto kleiner gleich brutto) angeben und neu ausstellen.");
        rule("INVOICE_PACKING_.*", "Rechnung und Packliste abstimmen: Beschreibung, Menge und Bezugsnummer müssen gleich lauten; das fehlerhafte Dokument neu ausstellen.");
        rule("SIGNATURE_REQUIREMENT_.*", "Dokument unterschreiben (bei verlangtem Original handschriftlich oder wie im Akkreditiv gefordert) und die unterschriebene Fassung hochladen.");
        rule("FULL_SET_BILL_OF_LADING|BILL_OF_LADING_.*", "Konnossement beim Spediteur oder der Reederei berichtigen lassen (Vollsatz, Notify-Adresse, Frachtvermerk, Indossament wie im Akkreditiv verlangt).");
        rule("CLEAN_ON_BOARD|TRANSPORT_.*|TRANSSHIPMENT_.*|PARTIAL_SHIPMENT_.*|PROHIBITION_VIOLATED|PORT_OF_.*|PLACE_OF_TAKING|FINAL_DESTINATION",
            "Transportdokument gegen die Transportbedingungen des Akkreditivs prüfen (Häfen, Umladung, Teilverladung, Aufnahmevermerk) und beim Frachtführer berichtigen lassen." + CHANGE);
        rule("AWB_.*|CMR_.*|AIR_WAYBILL_.*|COURIER_.*", "Frachtdokument beim Frachtführer berichtigen lassen (Absender, Empfänger, Frachtvermerk, Orte wie im Akkreditiv).");
        rule("INSURANCE_.*", "Versicherungsdokument beim Versicherer berichtigen lassen (Deckung, Betrag und Währung, Beginn spätestens am Verladetag, Zahlstelle wie im Akkreditiv).");
        rule("ORIGIN_CERTIFICATE_.*|CHAMBER_ISSUER_.*|COUNTRY_OF_ORIGIN_.*|ORIGIN_COUNTRY_.*", "Ursprungszeugnis oder Ursprungsangabe berichtigen lassen (Ursprungsland und Aussteller wie im Akkreditiv verlangt).");
        rule("INSPECTION_.*|QUALITY_.*", "Prüf- oder Qualitätszertifikat beim Aussteller berichtigen lassen (Bezug, Datum, Aussteller und Angaben wie im Akkreditiv verlangt).");
        rule("BENEFICIARY_CERTIFICATE_.*|BENEFICIARY_STATEMENT_.*", "Begünstigtenerklärung im Wortlaut des Akkreditivs neu ausstellen (Dokumentensatz erzeugen) und unterschreiben.");
        rule("DRAFT_.*", "Tratte nach den Vorgaben des Akkreditivs neu ausfertigen (Betrag, Laufzeit, Bezogener, Bezug).");
        rule("ADDITIONAL_COND_.*|ADDITIONAL_CONDITIONS_.*", "Zusatzbedingung (Feld 47A) in den betroffenen Dokumenten umsetzen, zum Beispiel Akkreditivnummer oder englische Sprache, und neu ausstellen.");
        rule("LC_AMENDMENT_PENDING", "Offene Änderung des Akkreditivs zuerst annehmen oder ablehnen; erst danach die Dokumente prüfen und einreichen.");
        rule("NO_TEXT|PDF_REQUIRES_OCR|EXTRACTION_FAILED|FORMAT_NOT_EXTRACTED|UNSUPPORTED", "Dokument lesbar bereitstellen (Text-PDF statt Foto oder Scan) und erneut hochladen, damit die Prüfung es lesen kann.");
        rule("UNCLASSIFIED_REQUIREMENT|REVIEW_STALE|.*_REVIEW|DATE_NOT_CAPTURED", "Bedingung selbst gegen das Dokument halten und das Ergebnis in der Akte vermerken; fehlende Daten in den Prüfdaten ergänzen.");
    }

    private CorrectionAdvice() {}

    /** The advice for an open finding; null for findings that are fine or have no standard step. */
    public static String forFinding(String code, Severity severity) {
        if (code == null || severity == null || severity == Severity.OK) return null;
        for (var rule : RULES.entrySet()) if (rule.getKey().matcher(code).matches()) return rule.getValue();
        return null;
    }
}
