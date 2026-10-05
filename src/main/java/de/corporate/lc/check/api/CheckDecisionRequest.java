package de.corporate.lc.check.api;
import jakarta.validation.constraints.*;
public record CheckDecisionRequest(@NotBlank @Size(max=100) String findingCode,@Size(max=255) String documentName,@NotBlank @Pattern(regexp="ACCEPTED|CONFIRMED_DISCREPANCY") String decision,@Size(max=1000) String comment,@NotBlank @Pattern(regexp="[0-9a-f]{64}") String reviewFingerprint){
    public CheckDecisionRequest(String findingCode,String documentName,String decision,String comment){this(findingCode,documentName,decision,comment,null);}
}
