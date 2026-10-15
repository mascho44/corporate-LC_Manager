package de.ostms.lc.document.service;

/** Test access to package-private helpers of the extraction from other test packages. */
public final class DocumentExtractionServiceAccess {
    private DocumentExtractionServiceAccess() {}
    public static boolean unreadable(String text) { return DocumentExtractionService.unreadable(text); }
}
