package de.ostms.lc.check.api;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.check.domain.LcReviewStep;
import de.ostms.lc.check.repository.LcReviewStepRepository;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.TestingAuthenticationToken;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReviewStepControllerTest {
 final LcReviewStepRepository steps=Mockito.mock(LcReviewStepRepository.class);final LetterOfCreditRepository lcs=Mockito.mock(LetterOfCreditRepository.class);final AuditService audit=Mockito.mock(AuditService.class);
 final org.springframework.security.core.Authentication user=new TestingAuthenticationToken("anna","x");final UUID lc=UUID.randomUUID();
 ReviewStepController controller(){when(lcs.findById(lc)).thenReturn(Optional.of(new LetterOfCredit()));when(steps.save(any())).thenAnswer(c->c.getArgument(0));return new ReviewStepController(steps,lcs,audit);}

 @Test void aStepIsCompletedWithoutReasonAndSkippedOnlyWithOne(){
  var c=controller();when(steps.byKey(lc,"VOLLSTAENDIGKEIT")).thenReturn(Optional.empty());
  var done=c.set(lc,"vollstaendigkeit",new ReviewStepController.Request("done","ignoriert"),user);
  assert done.status().equals("DONE");assertThat(done.reason()).isNull();assertThat(done.completedBy()).isEqualTo("anna");
  verify(audit).record(eq(user),eq("REVIEW_STEP_DONE"),eq("LETTER_OF_CREDIT"),eq(lc),eq("VOLLSTAENDIGKEIT"));
  assertThatThrownBy(()->c.set(lc,"COMPLIANCE",new ReviewStepController.Request("SKIPPED"," "),user)).hasMessageContaining("Begründung");
  assertThatThrownBy(()->c.set(lc,"COMPLIANCE",new ReviewStepController.Request("SKIPPED","ab"),user)).hasMessageContaining("Begründung");
  when(steps.byKey(lc,"COMPLIANCE")).thenReturn(Optional.empty());
  var skipped=c.set(lc,"COMPLIANCE",new ReviewStepController.Request("SKIPPED","Sanktionsprüfung erfolgt im Vorfeld durch Compliance"),user);
  assertThat(skipped.status()).isEqualTo("SKIPPED");assertThat(skipped.reason()).contains("Compliance");
  verify(audit).record(eq(user),eq("REVIEW_STEP_SKIPPED"),eq("LETTER_OF_CREDIT"),eq(lc),contains("COMPLIANCE · Sanktionsprüfung"));
 }
 @Test void unknownStepsStatusesAndMissingFilesAreRefusedAndASetStepCanBeChangedOrReopened(){
  var c=controller();
  assertThatThrownBy(()->c.set(lc,"FREI","x".equals("y")?null:new ReviewStepController.Request("DONE",null),user)).hasMessageContaining("Prüfschritt");
  assertThatThrownBy(()->c.set(lc,"AKTE",new ReviewStepController.Request("MAYBE",null),user)).hasMessageContaining("DONE oder SKIPPED");
  assertThatThrownBy(()->c.set(lc,"AKTE",new ReviewStepController.Request("SKIPPED","x".repeat(501)),user)).hasMessageContaining("500");
  var other=UUID.randomUUID();when(lcs.findById(other)).thenReturn(Optional.empty());
  assertThatThrownBy(()->c.list(other)).isInstanceOf(NoSuchElementException.class);
  var existing=new LcReviewStep(lc,"AKTE","SKIPPED","erst später","bernd");when(steps.byKey(lc,"AKTE")).thenReturn(Optional.of(existing));
  var changed=c.set(lc,"AKTE",new ReviewStepController.Request("DONE",null),user);
  assertThat(changed.status()).isEqualTo("DONE");assertThat(changed.completedBy()).isEqualTo("anna");assertThat(existing.getReason()).isNull();
  c.reopen(lc,"AKTE",user);verify(steps).delete(existing);verify(audit).record(eq(user),eq("REVIEW_STEP_REOPENED"),eq("LETTER_OF_CREDIT"),eq(lc),eq("AKTE"));
  assertThat(ReviewStepController.STEPS).containsExactly("AKTE","VOLLSTAENDIGKEIT","ANGABEN","COMPLIANCE","DOKUMENTE","QUERPRUEFUNG","ABSCHLUSS");
 }
}
