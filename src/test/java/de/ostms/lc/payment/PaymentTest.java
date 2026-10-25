package de.ostms.lc.payment;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import de.ostms.lc.lc.service.LcDeadlineService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PaymentTest {
 private final LcPaymentRepository payments=mock(LcPaymentRepository.class);private final LetterOfCreditRepository lcs=mock(LetterOfCreditRepository.class);
 private final LcDeadlineService deadlines=mock(LcDeadlineService.class);private final AuditService audit=mock(AuditService.class);
 private final PaymentService service=new PaymentService(payments,lcs,deadlines,audit);
 private final UUID id=UUID.randomUUID();
 private LetterOfCredit lc(){var lc=mock(LetterOfCredit.class);when(lc.getAmount()).thenReturn(new BigDecimal("100000.00"));when(lc.getCurrency()).thenReturn("EUR");return lc;}
 private static LcPayment pay(String amount,String currency){return new LcPayment(UUID.randomUUID(),LocalDate.of(2026,10,1),new BigDecimal(amount),currency,null,null,"MANUAL","anna");}

 @Test void statusFollowsTheSumOfThePaymentsInTheCurrencyOfTheCredit() {
  var amount=new BigDecimal("100000.00");var today=LocalDate.of(2026,10,22);
  assertThat(PaymentService.summarize(amount,"EUR",List.of(),null,today).status()).isEqualTo("NONE");
  var partial=PaymentService.summarize(amount,"EUR",List.of(pay("40000","EUR")),null,today);
  assertThat(partial.status()).isEqualTo("PARTIAL");assertThat(partial.open()).isEqualByComparingTo("60000");
  assertThat(PaymentService.summarize(amount,"EUR",List.of(pay("40000","EUR"),pay("60000","EUR")),null,today).status()).isEqualTo("PAID");
  var over=PaymentService.summarize(amount,"EUR",List.of(pay("100000.01","EUR")),null,today);assertThat(over.status()).isEqualTo("OVER");assertThat(over.open()).isZero();
  var foreign=PaymentService.summarize(amount,"EUR",List.of(pay("100000","USD")),null,today);
  assertThat(foreign.status()).isEqualTo("NONE");assertThat(foreign.otherCurrency()).isTrue();
 }
 @Test void aPaymentIsOverdueOnlyWhenTheExpectedDateHasPassedAndNotEverythingIsPaid() {
  var amount=new BigDecimal("100");var expected=LocalDate.of(2026,10,20);
  assertThat(PaymentService.summarize(amount,"EUR",List.of(),expected,LocalDate.of(2026,10,20)).overdue()).isFalse();
  assertThat(PaymentService.summarize(amount,"EUR",List.of(),expected,LocalDate.of(2026,10,21)).overdue()).isTrue();
  assertThat(PaymentService.summarize(amount,"EUR",List.of(pay("50","EUR")),expected,LocalDate.of(2026,10,21)).overdue()).isTrue();
  assertThat(PaymentService.summarize(amount,"EUR",List.of(pay("100","EUR")),expected,LocalDate.of(2026,10,21)).overdue()).isFalse();
  assertThat(PaymentService.summarize(amount,"EUR",List.of(),null,LocalDate.of(2026,10,21)).overdue()).isFalse();
 }
 @Test void recordingValidatesStoresAndAudits() {
  var file=lc();when(lcs.findById(id)).thenReturn(Optional.of(file));when(payments.forLc(id)).thenReturn(List.of());when(payments.save(any())).thenAnswer(i->i.getArgument(0));
  var view=service.record(id,new PaymentService.Request(LocalDate.of(2026,10,20),new BigDecimal("1234.5"),null," SWIFT-123 ","  "),"MANUAL",null);
  assertThat(view.amount()).isEqualByComparingTo("1234.50");assertThat(view.currency()).isEqualTo("EUR");assertThat(view.reference()).isEqualTo("SWIFT-123");assertThat(view.note()).isNull();assertThat(view.source()).isEqualTo("MANUAL");
  verify(audit).recordInTransaction(any(),eq("LC_PAYMENT_RECORDED"),eq("LETTER_OF_CREDIT"),eq(id),anyString());
  assertThatThrownBy(()->service.record(id,new PaymentService.Request(null,BigDecimal.ZERO,null,null,null),"MANUAL",null)).hasMessageContaining("Betrag");
  assertThatThrownBy(()->service.record(id,new PaymentService.Request(null,null,null,null,null),"MANUAL",null)).hasMessageContaining("Betrag");
  assertThatThrownBy(()->service.record(id,new PaymentService.Request(null,BigDecimal.ONE,"EURO",null,null),"MANUAL",null)).hasMessageContaining("Währung");
  assertThatThrownBy(()->service.record(id,new PaymentService.Request(LocalDate.now().plusDays(1),BigDecimal.ONE,null,null,null),"MANUAL",null)).hasMessageContaining("Zukunft");
  assertThat(service.record(id,new PaymentService.Request(null,BigDecimal.ONE,"usd",null,null),"API",null).currency()).isEqualTo("USD");
 }
 @Test void unknownFilePaymentAndTooManyPaymentsAreRefused() {
  when(lcs.findById(any())).thenReturn(Optional.empty());
  assertThatThrownBy(()->service.record(UUID.randomUUID(),new PaymentService.Request(null,BigDecimal.ONE,null,null,null),"MANUAL",null)).isInstanceOf(NoSuchElementException.class);
  var file=lc();when(lcs.findById(id)).thenReturn(Optional.of(file));when(payments.forLc(id)).thenReturn(java.util.Collections.nCopies(200,pay("1","EUR")));
  assertThatThrownBy(()->service.record(id,new PaymentService.Request(null,BigDecimal.ONE,null,null,null),"MANUAL",null)).hasMessageContaining("200");
  var other=pay("5","EUR");when(payments.findById(other.getId())).thenReturn(Optional.of(other));
  assertThatThrownBy(()->service.delete(id,other.getId(),null)).isInstanceOf(NoSuchElementException.class); // belongs to another file
 }
 @Test void deletingRemovesAndAudits() {
  var p=new LcPayment(id,LocalDate.of(2026,10,1),new BigDecimal("5"),"EUR",null,null,"MANUAL","anna");when(payments.findById(p.getId())).thenReturn(Optional.of(p));
  service.delete(id,p.getId(),null);verify(payments).delete(p);verify(audit).recordInTransaction(any(),eq("LC_PAYMENT_DELETED"),eq("LETTER_OF_CREDIT"),eq(id),anyString());
 }
 @Test void theEarliestMaturityEstimateIsTheExpectedDate() {
  var file=lc();when(lcs.findById(id)).thenReturn(Optional.of(file));when(payments.forLc(id)).thenReturn(List.of());
  when(deadlines.forLc(id)).thenReturn(List.of(
   new LcDeadlineService.LcDeadlineView("EXPIRY","Ablauf",LocalDate.of(2026,9,1),"s","n",true),
   new LcDeadlineService.LcDeadlineView("MATURITY_ESTIMATE","b",LocalDate.of(2026,11,30),"s","n",false),
   new LcDeadlineService.LcDeadlineView("MATURITY_ESTIMATE","a",LocalDate.of(2026,11,15),"s","n",false)));
  var result=service.forLc(id,LocalDate.of(2026,11,16));
  assertThat(result.summary().expectedDate()).isEqualTo(LocalDate.of(2026,11,15));assertThat(result.summary().overdue()).isTrue();
 }
}
