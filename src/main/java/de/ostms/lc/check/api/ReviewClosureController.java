package de.ostms.lc.check.api;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.check.service.ReviewClosureService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.*;

/** Closure of the examination of a file (see ReviewClosureService) and the amount limits that decide how many people are needed. */
@RestController
public class ReviewClosureController {
 public record Start(String summary){}
 public record RuleRequest(@NotBlank @Pattern(regexp="[A-Za-z]{3}",message="Währung muss ein dreistelliger ISO-Code sein.") String currency,
   @NotNull @DecimalMin("0.00") @Digits(integer=17,fraction=2) BigDecimal minimumAmount,@Min(1) @Max(5) int requiredClosers){}
 private final ReviewClosureService service;private final AuditService audit;
 public ReviewClosureController(ReviewClosureService service,AuditService audit){this.service=service;this.audit=audit;}

 @GetMapping("/api/lcs/{lcId}/review-closure")
 public ResponseEntity<ReviewClosureService.State> get(@PathVariable UUID lcId){return service.get(lcId).map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());}

 @PostMapping("/api/lcs/{lcId}/review-closure")
 public ReviewClosureService.State start(@PathVariable UUID lcId,@RequestBody Start request,Authentication auth){
  var change=service.start(lcId,auth.getName(),request.summary());
  audit.record(auth,change.completedNow()?"REVIEW_CLOSED":"REVIEW_CLOSURE_STARTED","LETTER_OF_CREDIT",lcId,"required="+change.state().required());
  return change.state();
 }

 @PostMapping("/api/lcs/{lcId}/review-closure/sign-off")
 public ReviewClosureService.State signOff(@PathVariable UUID lcId,Authentication auth){
  var change=service.signOff(lcId,auth.getName());
  audit.record(auth,change.completedNow()?"REVIEW_CLOSED":"REVIEW_CLOSURE_SIGNED","LETTER_OF_CREDIT",lcId,String.join(", ",change.state().signers()));
  return change.state();
 }

 @DeleteMapping("/api/lcs/{lcId}/review-closure")
 public ResponseEntity<Void> withdraw(@PathVariable UUID lcId,Authentication auth){
  service.withdraw(lcId,auth.getName());
  audit.record(auth,"REVIEW_CLOSURE_WITHDRAWN","LETTER_OF_CREDIT",lcId,"");
  return ResponseEntity.noContent().build();
 }

 @GetMapping("/api/settings/review-close-thresholds")
 public List<ReviewClosureService.Rule> rules(){return service.rules();}

 @PutMapping("/api/settings/review-close-thresholds")
 public List<ReviewClosureService.Rule> replace(@Valid @RequestBody List<@Valid RuleRequest> rules,Authentication auth){
  var before=service.rules();
  var after=service.replaceRules(rules.stream().map(r->new ReviewClosureService.Rule(r.currency(),r.minimumAmount(),r.requiredClosers())).toList());
  audit.recordChange(auth,"REVIEW_CLOSE_POLICY_UPDATED","REVIEW_CLOSE_POLICY","global",after.size()+" Betragsgrenzen",before.toString(),after.toString());
  return after;
 }
}
