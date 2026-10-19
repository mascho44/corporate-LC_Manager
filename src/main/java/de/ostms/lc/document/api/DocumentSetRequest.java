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
        @NotEmpty Set<DocumentType> types) {}
