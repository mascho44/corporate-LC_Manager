package de.ostms.lc.document.api;

import de.ostms.lc.document.domain.DocumentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** One input for the whole set of documents of a shipment: the same date, numbers, goods lines and weights go into every document. */
public record DocumentSetRequest(
        @NotNull LocalDate documentDate,
        @NotBlank @Size(max = 60) String invoiceNumber,
        @NotEmpty @Size(max = 100) List<@Valid GeneratedDocumentItemRequest> items,
        @Size(max = 100) String quantity,
        @Min(0) Integer packages,
        @DecimalMin("0.0") BigDecimal netWeight,
        @DecimalMin("0.0") BigDecimal grossWeight,
        @Size(max = 4000) String notes,
        @NotEmpty Set<DocumentType> types,
        @Pattern(regexp = "(?i)docx|pdf") String format,
        java.util.UUID replaceSetId) {
    public DocumentSetRequest(LocalDate documentDate, String invoiceNumber, List<GeneratedDocumentItemRequest> items, String quantity, Integer packages,
                              BigDecimal netWeight, BigDecimal grossWeight, String notes, Set<DocumentType> types, String format) {
        this(documentDate, invoiceNumber, items, quantity, packages, netWeight, grossWeight, notes, types, format, null);
    }
    public DocumentSetRequest(LocalDate documentDate, String invoiceNumber, List<GeneratedDocumentItemRequest> items, String quantity, Integer packages,
                              BigDecimal netWeight, BigDecimal grossWeight, String notes, Set<DocumentType> types) {
        this(documentDate, invoiceNumber, items, quantity, packages, netWeight, grossWeight, notes, types, null, null);
    }

    /** Word unless PDF was asked for. */
    public boolean pdf() { return "pdf".equalsIgnoreCase(format); }
}
