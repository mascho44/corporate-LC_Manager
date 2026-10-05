package de.corporate.lc.training.api;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record AdvisingNewCaseRequest(
 @NotBlank @Size(max=255) String reference,
 @Size(max=255) String ownBankReference, @Size(max=255) String foreignBankReference,
 @NotBlank @Size(max=255) String applicant, @NotBlank @Size(max=255) String beneficiary,
 @NotNull @DecimalMin(value="0", inclusive=false) @Digits(integer=17,fraction=2) BigDecimal amount,
 @NotBlank @Pattern(regexp="[A-Z]{3}") String currency, @NotNull LocalDate expiryDate,
 @Size(max=255) String issuingBank, @Size(max=255) String expiryPlace) {}
