package de.corporate.lc.document.api;

import de.corporate.lc.document.domain.DocumentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record GeneratedDocumentRequest(
        @NotNull DocumentType type,
        @NotBlank @Size(max=100) String documentNumber,
        @NotNull LocalDate documentDate,
        @Size(max=4000) String description,
        @Size(max=100) String quantity,
        @Min(0) Integer packages,
        @DecimalMin("0.0") BigDecimal netWeight,
        @DecimalMin("0.0") BigDecimal grossWeight,
        @Size(max=4000) String notes,
        @Valid @Size(max=100) List<GeneratedDocumentItemRequest> items) {}
