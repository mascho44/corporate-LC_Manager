package de.ostms.lc.document.api;

import de.ostms.lc.document.domain.DocumentType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record DocumentInboxAttachRequest(@NotNull UUID lcId, @NotNull DocumentType documentType, LocalDate documentDate,
 @jakarta.validation.constraints.Min(-3) @jakarta.validation.constraints.Max(3) Integer copyNumber) {
 public DocumentInboxAttachRequest(UUID lcId,DocumentType type,LocalDate date){this(lcId,type,date,null);}
}
