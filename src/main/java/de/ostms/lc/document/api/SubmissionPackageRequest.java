package de.ostms.lc.document.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** What goes to the bank: the date and recipient of the cover letter and, per document, how many originals and copies are presented. */
public record SubmissionPackageRequest(
        @NotNull LocalDate date,
        @NotBlank @Size(max = 1000) String recipient,
        @Size(max = 100) String ourReference,
        @Size(max = 2000) String remarks,
        @NotEmpty @Size(max = 100) List<@Valid Entry> documents) {
    public record Entry(@NotNull UUID documentId, @Min(0) @Max(99) int originals, @Min(0) @Max(99) int copies) {}
}
