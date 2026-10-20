package de.ostms.lc.check.service;

import de.ostms.lc.check.api.CheckResult.Severity;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * English wording of a finding for the English interface. The German message carries the details (values, quotes); the English text names
 * what was checked and the outcome, by finding code. Specific sentences come first, then the area of the code with a sentence per severity.
 * The values stay visible in the condition and evidence fields of the finding.
 */
public final class FindingText {
    private static final Map<Pattern, String> SPECIFIC = new LinkedHashMap<>();
    private static final Map<Pattern, String> AREA = new LinkedHashMap<>();

    private static void specific(String regex, String text) { SPECIFIC.put(Pattern.compile(regex), text); }
    private static void area(String regex, String name) { AREA.put(Pattern.compile(regex), name); }

    static {
        specific("MISSING_DOCUMENT", "A document required by the credit has not been presented.");
        specific("DOCUMENT_PRESENT", "A document required by the credit has been presented.");
        specific("UNCLASSIFIED_REQUIREMENT", "A document condition of the credit must be checked manually.");
        specific("INVOICE_AMOUNT_EXCEEDED", "The invoice amount exceeds the maximum amount allowed under the credit.");
        specific("INVOICE_AMOUNT_OK", "The invoice amount is within the amount of the credit.");
        specific("CURRENCY_MISMATCH", "The currency of a document differs from the currency of the credit.");
        specific("DUPLICATE_DOCUMENT_NUMBER", "A document number occurs more than once.");
        specific("DOCUMENT_AFTER_EXPIRY", "A document is dated after the expiry of the credit.");
        specific("LC_EXPIRED", "The credit has expired.");
        specific("LC_AMENDMENT_PENDING", "An amendment of the credit is pending and not yet accepted; the check uses the version in force.");
        specific("LC_EFFECTIVE_VERSION", "The check uses the credit including accepted amendments.");
        specific("GOODS_DESCRIPTION_MISMATCH", "The description of goods differs from the credit.");
        specific("GOODS_DESCRIPTION_NOT_FOUND", "The description of goods of the credit was not found in the document.");
        specific("GOODS_DESCRIPTION_OK", "The description of goods matches the credit.");
        specific("NO_TEXT|PDF_REQUIRES_OCR|EXTRACTION_FAILED|FORMAT_NOT_EXTRACTED", "The document could not be read; no automatic check was possible.");
        specific("PACKAGES_MISSING", "The number of packages is missing in the packing list.");
        specific("WEIGHTS_MISSING", "Weights are missing in the packing list.");
        specific("PACKING_WEIGHT_IMPLAUSIBLE", "The weights in the packing list are not plausible (net above gross).");
        specific("PROHIBITION_VIOLATED", "A prohibition of the credit (for example partial shipment or transhipment) appears to be violated.");
        specific("INCOTERM_NOT_FOUND", "The Incoterm of the credit was not found in the invoice.");
        specific("SIGNATURE_REQUIREMENT_MISSING_SUSPECTED", "The document should be signed, but no signature was detected.");
        specific("APPLICANT_NOT_FOUND", "The applicant of the credit was not found in the document.");
        specific("BENEFICIARY_NOT_FOUND", "The beneficiary of the credit was not found in the document.");
        specific("CONFIRMED_DISCREPANCY", "A discrepancy confirmed in the review.");
        specific("REVIEW_STALE", "The review decision is out of date because the file has changed.");

        area("ADDITIONAL_COND.*", "Additional conditions (47A)");
        area("AIR_WAYBILL_.*|AWB_.*|AIR_SHIPMENT_DATE_.*", "Air waybill");
        area("CMR_.*", "CMR consignment note");
        area("BILL_OF_LADING_.*|FULL_SET_BILL_OF_LADING|CLEAN_ON_BOARD", "Bill of lading");
        area("BENEFICIARY_CERTIFICATE_.*|BENEFICIARY_STATEMENT_.*", "Beneficiary's certificate");
        area("ORIGIN_CERTIFICATE_.*|CHAMBER_ISSUER_.*|COUNTRY_OF_ORIGIN_.*|ORIGIN_COUNTRY_.*", "Certificate of origin");
        area("QUALITY_.*", "Quality certificate");
        area("INSPECTION_.*", "Inspection certificate");
        area("INSURANCE_.*", "Insurance document");
        area("DRAFT_.*|INVOICE_DRAFT_.*", "Bill of exchange");
        area("CUMULATIVE_INVOICE_.*|INVOICE_.*|CAPTURED_.*", "Invoice amount");
        area("PACKING_.*|PACKAGES_.*", "Packing list");
        area("COURIER_.*", "Courier receipt");
        area("PRESENTATION_.*", "Presentation period");
        area("SHIPMENT_DATE_.*|AFTER_SHIPMENT", "Shipment date");
        area("PARTIAL_SHIPMENT_.*|TRANSSHIPMENT_.*|TRANSPORT_.*|PORT_OF_.*|PLACE_OF_.*|FINAL_DESTINATION", "Transport terms");
        area("SIGNATURE_REQUIREMENT_.*", "Signature");
        area("INCOTERM_.*", "Incoterm");
        area("HS_CODE_.*", "HS code");
        area("SANCTION.*", "Sanctions screening");
        area("LC_REFERENCE_.*|.*_REFERENCE_.*", "Credit number on the document");
        area("APPLICANT_.*|BENEFICIARY_.*", "Parties");
        area("QUANTITY_.*", "Quantity");
        area("DATE_.*", "Dates");
    }

    private FindingText() {}

    /** English text for the finding; null when the code is unknown (the interface then shows the German message). */
    public static String english(String code, Severity severity) {
        if (code == null || severity == null) return null;
        for (var rule : SPECIFIC.entrySet()) if (rule.getKey().matcher(code).matches()) return rule.getValue();
        for (var rule : AREA.entrySet()) if (rule.getKey().matcher(code).matches()) return rule.getValue() + ": " + switch (severity) {
            case OK -> "in order.";
            case WARNING -> "please check against the credit.";
            case DISCREPANCY -> "does not comply with the credit.";
        };
        return null;
    }
}
