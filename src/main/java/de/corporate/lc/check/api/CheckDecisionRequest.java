package de.corporate.lc.check.api;
import jakarta.validation.constraints.*;
public record CheckDecisionRequest(@NotBlank @Size(max=100) String findingCode,@Size(max=255) String documentName,@NotBlank @Pattern(regexp="ACCEPTED|CONFIRMED_DISCREPANCY") String decision,@Size(max=1000) String comment){}
