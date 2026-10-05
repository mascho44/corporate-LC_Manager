package de.corporate.lc.check.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Null LC overrides retain the original; supplied document rows replace their metadata. */
public record SimulationRequest(@DecimalMin("0") @Digits(integer=17,fraction=2) BigDecimal amount,
        LocalDate expiryDate, LocalDate latestShipmentDate,
        @Size(max=100) List<@Valid DocumentOverride> documents) {
    public record DocumentOverride(@NotNull UUID id, LocalDate documentDate,
            @DecimalMin("0") @Digits(integer=17,fraction=2) BigDecimal amount,
            @Pattern(regexp="[A-Z]{3}") String currency) {}
}
