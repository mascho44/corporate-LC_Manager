package de.corporate.lc.document.api;
import de.corporate.lc.document.domain.DocumentType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
public record InboxNewCaseRequest(@NotBlank @Size(max=255) String reference,
 @Size(max=255) String ownBankReference,@Size(max=255) String foreignBankReference,
 @Size(max=255) String applicant,@Size(max=255) String beneficiary,
 @DecimalMin("0.00") BigDecimal amount,@Pattern(regexp="[A-Z]{3}") String currency,
 LocalDate expiryDate,@NotNull DocumentType documentType,LocalDate documentDate,
 @Size(max=255) String issuingBank,@Size(max=255) String expiryPlace){
 public InboxNewCaseRequest(String reference,String ownBankReference,String foreignBankReference,String applicant,String beneficiary,BigDecimal amount,String currency,LocalDate expiryDate,DocumentType documentType,LocalDate documentDate){this(reference,ownBankReference,foreignBankReference,applicant,beneficiary,amount,currency,expiryDate,documentType,documentDate,null,null);}
}
