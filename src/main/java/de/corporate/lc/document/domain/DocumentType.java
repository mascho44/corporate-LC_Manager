package de.corporate.lc.document.domain;

public enum DocumentType {
    COMMERCIAL_INVOICE("Commercial Invoice"),
    PACKING_LIST("Packing List"),
    BILL_OF_LADING("Bill of Lading"),
    AIR_WAYBILL("Air Waybill"),
    CERTIFICATE_OF_ORIGIN("Certificate of Origin"),
    INSURANCE_CERTIFICATE("Insurance Certificate"),
    INSPECTION_CERTIFICATE("Inspection Certificate"),
    OTHER("Other");

    private final String displayName;

    DocumentType(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}
