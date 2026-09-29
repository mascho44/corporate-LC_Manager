package de.corporate.lc.lc.api;

import de.corporate.lc.lc.domain.LetterOfCreditStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record LetterOfCreditUpdateRequest(
        @Size(max = 255) String templateCompany,
        @NotBlank @Size(max = 255) String reference,
        @Size(max = 255) String applicant,
        @Size(max = 255) String beneficiary,
        @Size(max = 255) String issuingBank,
        @Size(max = 255) String advisingBank,
        @DecimalMin("0.00") BigDecimal amount,
        @Size(min = 3, max = 3) String currency,
        LocalDate issueDate,
        LocalDate expiryDate,
        @Size(max = 255) String expiryPlace,
        LocalDate latestShipmentDate,
        @Size(max = 100) String assignedTo,
        LocalDate followUpDate,
        @NotNull LetterOfCreditStatus status,
        @Size(max = 100) List<@Size(max = 2000) String> requiredDocuments,
        @Size(max = 50) Map<@Size(max = 255) String, @Size(max = 4000) String> additionalFields) {
}
