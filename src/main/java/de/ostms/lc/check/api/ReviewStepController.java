package de.ostms.lc.check.api;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.check.domain.LcReviewStep;
import de.ostms.lc.check.repository.LcReviewStepRepository;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;

/** Progress of the guided examination (Prüfungsassistent): steps completed or skipped per file. The steps themselves are fixed; the progress is stored so a colleague can continue. */
@RestController @RequestMapping("/api/lcs/{lcId}/review-steps")
public class ReviewStepController {
 public static final List<String> STEPS=List.of("AKTE","VOLLSTAENDIGKEIT","ANGABEN","COMPLIANCE","DOKUMENTE","QUERPRUEFUNG","ABSCHLUSS");
 public record Request(String status,String reason){}
 public record View(String step,String status,String reason,String completedBy,LocalDateTime completedAt){}
 private final LcReviewStepRepository steps;private final LetterOfCreditRepository lcs;private final AuditService audit;
 public ReviewStepController(LcReviewStepRepository steps,LetterOfCreditRepository lcs,AuditService audit){this.steps=steps;this.lcs=lcs;this.audit=audit;}
 private static View view(LcReviewStep s){return new View(s.getStepKey(),s.getStatus(),s.getReason(),s.getCompletedBy(),s.getCompletedAt());}
 private void requireLc(UUID lcId){if(lcs.findById(lcId).isEmpty())throw new NoSuchElementException("Akte nicht gefunden.");}
 private static String key(String step){
  String k=step==null?"":step.toUpperCase(Locale.ROOT);
  if(!STEPS.contains(k))throw new IllegalArgumentException("Unbekannter Prüfschritt.");
  return k;
 }
 @GetMapping @Transactional(readOnly=true)
 public List<View> list(@PathVariable UUID lcId){requireLc(lcId);return steps.forLc(lcId).stream().map(ReviewStepController::view).toList();}
 @PutMapping("/{step}") @Transactional
 public View set(@PathVariable UUID lcId,@PathVariable String step,@RequestBody Request request,Authentication auth){
  requireLc(lcId);String k=key(step);
  String status=request.status()==null?"":request.status().toUpperCase(Locale.ROOT);
  if(!LcReviewStep.DONE.equals(status)&&!LcReviewStep.SKIPPED.equals(status))throw new IllegalArgumentException("Der Status muss DONE oder SKIPPED sein.");
  String reason=request.reason()==null?null:request.reason().strip();
  if(LcReviewStep.SKIPPED.equals(status)&&(reason==null||reason.length()<3))throw new IllegalArgumentException("Zum Überspringen bitte eine Begründung angeben.");
  if(reason!=null&&reason.length()>500)throw new IllegalArgumentException("Die Begründung darf höchstens 500 Zeichen lang sein.");
  final String storedReason=LcReviewStep.DONE.equals(status)?null:reason;
  var entry=steps.byKey(lcId,k).orElseGet(()->new LcReviewStep(lcId,k,status,storedReason,auth.getName()));
  entry.set(status,storedReason,auth.getName());
  var saved=steps.save(entry);
  audit.record(auth,LcReviewStep.DONE.equals(status)?"REVIEW_STEP_DONE":"REVIEW_STEP_SKIPPED","LETTER_OF_CREDIT",lcId,k+(storedReason==null?"":" · "+storedReason));
  return view(saved);
 }
 @DeleteMapping("/{step}") @Transactional
 public ResponseEntity<Void> reopen(@PathVariable UUID lcId,@PathVariable String step,Authentication auth){
  requireLc(lcId);String k=key(step);
  steps.byKey(lcId,k).ifPresent(e->{steps.delete(e);audit.record(auth,"REVIEW_STEP_REOPENED","LETTER_OF_CREDIT",lcId,k);});
  return ResponseEntity.noContent().build();
 }
}
