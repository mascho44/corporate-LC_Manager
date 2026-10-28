package de.ostms.lc.check.service;

import de.ostms.lc.check.domain.LcReviewClosure;
import de.ostms.lc.check.domain.ReviewCloseThreshold;
import de.ostms.lc.check.repository.LcReviewClosureRepository;
import de.ostms.lc.check.repository.ReviewCloseThresholdRepository;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.domain.LetterOfCreditStatus;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import de.ostms.lc.lc.service.LcTimelineService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReviewClosureServiceTest {
 final LcReviewClosureRepository closures=Mockito.mock(LcReviewClosureRepository.class);final ReviewCloseThresholdRepository thresholds=Mockito.mock(ReviewCloseThresholdRepository.class);
 final LetterOfCreditRepository lcs=Mockito.mock(LetterOfCreditRepository.class);final LcTimelineService timeline=Mockito.mock(LcTimelineService.class);
 final UUID id=UUID.randomUUID();final LetterOfCredit lc=new LetterOfCredit();
 final List<LcReviewClosure> stored=new ArrayList<>();

 ReviewClosureService service(String amount){
  lc.setAmount(new BigDecimal(amount));lc.setCurrency("EUR");
  when(lcs.findById(id)).thenReturn(Optional.of(lc));when(lcs.save(any())).thenAnswer(c->c.getArgument(0));
  when(thresholds.ordered()).thenReturn(List.of(new ReviewCloseThreshold("EUR",new BigDecimal("100000.00"),2),new ReviewCloseThreshold("EUR",new BigDecimal("1000000.00"),3)));
  when(closures.save(any())).thenAnswer(c->{var e=(LcReviewClosure)c.getArgument(0);stored.remove(e);stored.add(e);return e;});
  when(closures.forLc(id)).thenAnswer(c->stored.stream().findFirst());
  return new ReviewClosureService(closures,thresholds,lcs,timeline);
 }

 @Test void belowTheLimitOnePersonClosesAloneAndTheFileIsMarkedCompleted(){
  var s=service("99999.99");
  var change=s.start(id,"anna","Prüfung abgeschlossen.");
  assertThat(change.completedNow()).isTrue();assertThat(change.state().completed()).isTrue();
  assertThat(lc.getStatus()).isEqualTo(LetterOfCreditStatus.REVIEW_COMPLETED);
  verify(timeline).addNote(eq(id),eq("anna"),contains("Abschluss durch: anna"));
 }
 @Test void atOrAboveTheLimitASecondDifferentPersonMustSignOff(){
  var s=service("100000.00");
  var change=s.start(id,"anna","Prüfung abgeschlossen.");
  assertThat(change.completedNow()).isFalse();assertThat(change.state().required()).isEqualTo(2);
  assertThat(lc.getStatus()).isNotEqualTo(LetterOfCreditStatus.REVIEW_COMPLETED);verifyNoInteractions(timeline);
  assertThatThrownBy(()->s.signOff(id,"Anna")).hasMessageContaining("bereits mitgezeichnet");
  var done=s.signOff(id,"bernd");
  assertThat(done.completedNow()).isTrue();assertThat(done.state().signers()).containsExactly("anna","bernd");
  assertThat(lc.getStatus()).isEqualTo(LetterOfCreditStatus.REVIEW_COMPLETED);
  verify(timeline).addNote(eq(id),eq("bernd"),contains("anna, bernd"));
 }
 @Test void theHighestMatchingLimitDecidesAndNoRuleMeansOnePerson(){
  var s=service("5000000.00");
  assertThat(s.requiredFor(new BigDecimal("5000000"),"EUR")).isEqualTo(3);
  assertThat(s.requiredFor(new BigDecimal("500000"),"EUR")).isEqualTo(2);
  assertThat(s.requiredFor(new BigDecimal("5000000"),"USD")).isEqualTo(1);
  assertThat(s.requiredFor(null,"EUR")).isEqualTo(1);
 }
 @Test void onlyTheStarterWithdrawsAndAnOpenClosureBlocksANewOne(){
  var s=service("200000.00");s.start(id,"anna","Vermerk");
  assertThatThrownBy(()->s.start(id,"bernd","Vermerk")).hasMessageContaining("bereits einen offenen");
  assertThatThrownBy(()->s.withdraw(id,"bernd")).hasMessageContaining("Nur die Person");
  s.withdraw(id,"anna");verify(closures).delete(any(LcReviewClosure.class));
 }
 @Test void aNoteIsRequiredAndTheRulesAreChecked(){
  var s=service("10.00");
  assertThatThrownBy(()->s.start(id,"anna"," ")).hasMessageContaining("Vermerk");
  assertThatThrownBy(()->s.replaceRules(List.of(new ReviewClosureService.Rule("EUR",BigDecimal.ONE,6)))).hasMessageContaining("zwischen 1 und 5");
  assertThatThrownBy(()->s.replaceRules(List.of(new ReviewClosureService.Rule("EUR",BigDecimal.ONE,2),new ReviewClosureService.Rule("eur",new BigDecimal("1.0"),3)))).hasMessageContaining("Doppelte");
 }
}
