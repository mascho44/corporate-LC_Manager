package de.corporate.lc.document.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record GeneratedDocumentItemRequest(
        @Size(max = 30) String position,
        @Size(max = 1000) String description,
        @DecimalMin("0.0") BigDecimal quantity,
        @Size(max = 30) String unit,
        @DecimalMin("0.0") BigDecimal unitPrice,
        @DecimalMin("0.0") BigDecimal amount,
        @Min(0) Integer packages,
        @DecimalMin("0.0") BigDecimal netWeight,
        @DecimalMin("0.0") BigDecimal grossWeight) {}
