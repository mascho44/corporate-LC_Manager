package de.ostms.lc.document.api;
import java.util.UUID;
import java.time.LocalDate;
import de.ostms.lc.document.domain.DocumentType;
public record InboxMetadataReview(UUID lcId,DocumentType documentType,
 @jakarta.validation.constraints.Min(-3) @jakarta.validation.constraints.Max(3) Integer copyNumber,
 LocalDate documentDate,@jakarta.validation.Valid @jakarta.validation.constraints.NotNull DocumentInboxAttachRequest.Metadata metadata,
 @jakarta.validation.constraints.Size(max=100) String profile,boolean confirmed){}
