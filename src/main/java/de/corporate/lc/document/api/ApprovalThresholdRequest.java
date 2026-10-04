package de.corporate.lc.document.api;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record ApprovalThresholdRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}", message = "Währung muss ein dreistelliger ISO-Code sein.") String currency,
        @NotNull @DecimalMin(value = "0.00") @Digits(integer = 17, fraction = 2) BigDecimal minimumAmount,
        @Min(1) @Max(5) int requiredApprovals) {}
