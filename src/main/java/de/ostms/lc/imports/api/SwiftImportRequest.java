package de.ostms.lc.imports.api;
/** A SWIFT message to preview or import; applicant and beneficiary are optional corrections when :50: or :59: only refer to another field ("SEE 47A"). */
public record SwiftImportRequest(String filename, String rawMessage, String applicant, String beneficiary) {
    public SwiftImportRequest(String filename, String rawMessage) { this(filename, rawMessage, null, null); }
}
