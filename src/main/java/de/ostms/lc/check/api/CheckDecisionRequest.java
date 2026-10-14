package de.ostms.lc.check.api;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
public record CheckDecisionRequest(@NotBlank @Size(max=100) String findingCode,@Size(max=255) String documentName,@NotBlank @Pattern(regexp="ACCEPTED|CONFIRMED_DISCREPANCY") String decision,@Size(max=1000) String comment,@NotBlank @Pattern(regexp="[0-9a-f]{64}") String reviewFingerprint,@Valid EvidenceMark mark){
    /** A rectangle marked on a page of a document of the LC file (fractions of the page: x, y = upper left corner, w, h = size). */
    public record EvidenceMark(@NotNull UUID documentId,@NotNull @Min(1) @Max(500) Integer page,@NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double x,@NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double y,
                               @NotNull @DecimalMin("0.005") @DecimalMax("1.0") Double w,@NotNull @DecimalMin("0.005") @DecimalMax("1.0") Double h){
        @AssertTrue(message="Die Markierung liegt nicht auf der Seite.") public boolean isInsidePage(){return x==null||y==null||w==null||h==null||(x+w<=1.0001&&y+h<=1.0001);}
    }
    public CheckDecisionRequest(String findingCode,String documentName,String decision,String comment,String reviewFingerprint){this(findingCode,documentName,decision,comment,reviewFingerprint,null);}
    public CheckDecisionRequest(String findingCode,String documentName,String decision,String comment){this(findingCode,documentName,decision,comment,null,null);}
}
