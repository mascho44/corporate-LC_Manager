package de.ostms.lc.document.api;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/** The changes asked for in a request to amend a credit; empty fields stay as they are. */
public record AmendmentRequestInput(
        @NotNull LocalDate date,
        @NotBlank @Size(max = 1000) String recipient,
        @Size(max = 100) String ourReference,
        @DecimalMin("0.01") BigDecimal newAmount,
        LocalDate newExpiryDate,
        @Size(max = 200) String newExpiryPlace,
        LocalDate newLatestShipmentDate,
        @Size(max = 4000) String goods,
        @Size(max = 4000) String documents,
        @Size(max = 4000) String conditions,
        @Size(max = 4000) String other,
        @Size(max = 2000) String reason) {}
