package de.ostms.lc.document.domain;

public enum DocumentType {
    COMMERCIAL_INVOICE("Commercial Invoice"),
    PACKING_LIST("Packing List"),
    BILL_OF_LADING("Bill of Lading"),
    AIR_WAYBILL("Air Waybill"),
    ROAD_CONSIGNMENT_NOTE("CMR / Road Consignment Note"),
    CERTIFICATE_OF_ORIGIN("Certificate of Origin"),
    INSURANCE_CERTIFICATE("Insurance Certificate"),
    INSPECTION_CERTIFICATE("Inspection Certificate"),
    BILL_OF_EXCHANGE("Bill of Exchange / Draft"),
    BENEFICIARY_CERTIFICATE("Beneficiary's Certificate"),
    QUALITY_CERTIFICATE("Quality / Analysis Certificate"),
    COURIER_RECEIPT("Courier Receipt"),
    ADVISING_LETTER("Avisierungsschreiben"),
    SWIFT_MT700("SWIFT MT700 / Akkreditiveröffnung"),
    ANNEX("Annex / Anlage"),
    OTHER("Other");

    private final String displayName;

    DocumentType(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}
