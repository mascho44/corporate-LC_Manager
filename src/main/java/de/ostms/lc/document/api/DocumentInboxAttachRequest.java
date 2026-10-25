package de.ostms.lc.document.api;

import de.ostms.lc.document.domain.DocumentType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record DocumentInboxAttachRequest(@NotNull UUID lcId, @NotNull DocumentType documentType, LocalDate documentDate,
 @jakarta.validation.constraints.Min(-3) @jakarta.validation.constraints.Max(3) Integer copyNumber,
 @jakarta.validation.Valid Metadata metadata) {
 public record Metadata(@jakarta.validation.constraints.Size(max=255) String reference,
  @jakarta.validation.constraints.Size(max=255) String documentNumber,
  @jakarta.validation.constraints.DecimalMin("0") java.math.BigDecimal amount,
  @jakarta.validation.constraints.Pattern(regexp="[A-Z]{3}") String currency){}
 public DocumentInboxAttachRequest(UUID lcId,DocumentType type,LocalDate date,Integer copy){this(lcId,type,date,copy,null);}
 public DocumentInboxAttachRequest(UUID lcId,DocumentType type,LocalDate date){this(lcId,type,date,null);}
}
