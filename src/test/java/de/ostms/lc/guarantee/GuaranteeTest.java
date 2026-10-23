package de.ostms.lc.guarantee;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.document.repository.LcDocumentRepository;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.domain.LetterOfCreditStatus;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import de.ostms.lc.lc.service.LcDeadlineService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
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

class GuaranteeTest {
 private final LetterOfCreditRepository lcs=mock(LetterOfCreditRepository.class);private final GuaranteeDetailRepository details=mock(GuaranteeDetailRepository.class);private final AuditService audit=mock(AuditService.class);
 private final GuaranteeService service=new GuaranteeService(lcs,details,audit);
 static final LocalDate TODAY=LocalDate.of(2026,10,23);
 private static GuaranteeService.Request request(String reference){
  return new GuaranteeService.Request(reference,"performance","Musterbank AG\nFrankfurt","Kunde GmbH\nBerlin","eur",new BigDecimal("50000"),LocalDate.of(2026,10,1),LocalDate.of(2026,10,1),LocalDate.of(2027,3,31),LocalDate.of(2027,6,30),true,60,"Liefervertrag 4711","K-2026-17",null);
 }
 private LetterOfCredit lc(String reference,String type,LocalDate expiry){var lc=new LetterOfCredit();ReflectionTestUtils.setField(lc,"id",UUID.randomUUID());lc.setReference(reference);lc.setInstrumentType(type);lc.setExpiryDate(expiry);lc.setAmount(new BigDecimal("1000"));lc.setCurrency("EUR");return lc;}

 @Test void createsTheFileAndTheDetailsWithNormalisedValuesAndAudits() {
  when(lcs.existsByReference("G-1")).thenReturn(false);when(lcs.save(any())).thenAnswer(i->{var l=(LetterOfCredit)i.getArgument(0);if(l.getId()==null)ReflectionTestUtils.setField(l,"id",UUID.randomUUID());return l;});when(details.save(any())).thenAnswer(i->i.getArgument(0));
  var view=service.create(request("G-1"),null);
  assertThat(view.guaranteeType()).isEqualTo("PERFORMANCE");assertThat(view.currency()).isEqualTo("EUR");assertThat(view.amount()).isEqualByComparingTo("50000.00");assertThat(view.status()).isEqualTo("ACTIVE");assertThat(view.autoExtension()).isTrue();
  assertThat(view.decisionDate()).isEqualTo(LocalDate.of(2027,1,30)); // expiry 31.03.2027 minus 60 days
  var saved=org.mockito.ArgumentCaptor.forClass(LetterOfCredit.class);verify(lcs,atLeastOnce()).save(saved.capture());
  var file=saved.getAllValues().get(0);assertThat(file.getInstrumentType()).isEqualTo("GUARANTEE");assertThat(file.getPartyRole()).isEqualTo("APPLICANT");assertThat(file.getStatus()).isEqualTo(LetterOfCreditStatus.ACTIVE);
  verify(audit).recordInTransaction(any(),eq("GUARANTEE_CREATED"),eq("LETTER_OF_CREDIT"),any(),anyString());
 }
 @Test void rejectsInvalidInput() {
  when(lcs.existsByReference("DUP")).thenReturn(true);
  assertThatThrownBy(()->service.create(request("DUP"),null)).hasMessageContaining("existiert bereits");
  java.util.function.Function<java.util.function.UnaryOperator<GuaranteeService.Request>,Throwable> bad=f->org.assertj.core.api.Assertions.catchThrowable(()->GuaranteeService.validate(f.apply(request("X")),true));
  assertThat(bad.apply(r->new GuaranteeService.Request(" ",r.guaranteeType(),r.bank(),r.beneficiary(),r.currency(),r.amount(),r.issueDate(),r.validFrom(),r.expiryDate(),r.claimUntil(),r.autoExtension(),r.extensionNoticeDays(),r.underlying(),r.beneficiaryReference(),r.status()))).hasMessageContaining("Referenz");
  assertThat(bad.apply(r->new GuaranteeService.Request(r.reference(),"MAGIC",r.bank(),r.beneficiary(),r.currency(),r.amount(),r.issueDate(),r.validFrom(),r.expiryDate(),r.claimUntil(),r.autoExtension(),r.extensionNoticeDays(),r.underlying(),r.beneficiaryReference(),r.status()))).hasMessageContaining("Garantieart");
  assertThat(bad.apply(r->new GuaranteeService.Request(r.reference(),r.guaranteeType(),r.bank(),r.beneficiary(),"EURO",r.amount(),r.issueDate(),r.validFrom(),r.expiryDate(),r.claimUntil(),r.autoExtension(),r.extensionNoticeDays(),r.underlying(),r.beneficiaryReference(),r.status()))).hasMessageContaining("Währung");
  assertThat(bad.apply(r->new GuaranteeService.Request(r.reference(),r.guaranteeType(),r.bank(),r.beneficiary(),r.currency(),BigDecimal.ZERO,r.issueDate(),r.validFrom(),r.expiryDate(),r.claimUntil(),r.autoExtension(),r.extensionNoticeDays(),r.underlying(),r.beneficiaryReference(),r.status()))).hasMessageContaining("Betrag");
  assertThat(bad.apply(r->new GuaranteeService.Request(r.reference(),r.guaranteeType(),r.bank(),r.beneficiary(),r.currency(),r.amount(),r.issueDate(),r.validFrom(),null,r.claimUntil(),false,r.extensionNoticeDays(),r.underlying(),r.beneficiaryReference(),r.status()))).hasMessageContaining("Ablaufdatum");
  assertThat(bad.apply(r->new GuaranteeService.Request(r.reference(),r.guaranteeType(),r.bank(),r.beneficiary(),r.currency(),r.amount(),r.issueDate(),LocalDate.of(2027,5,1),r.expiryDate(),r.claimUntil(),r.autoExtension(),r.extensionNoticeDays(),r.underlying(),r.beneficiaryReference(),r.status()))).hasMessageContaining("vor dem Beginn");
  assertThat(bad.apply(r->new GuaranteeService.Request(r.reference(),r.guaranteeType(),r.bank(),r.beneficiary(),r.currency(),r.amount(),r.issueDate(),r.validFrom(),r.expiryDate(),LocalDate.of(2027,1,1),r.autoExtension(),r.extensionNoticeDays(),r.underlying(),r.beneficiaryReference(),r.status()))).hasMessageContaining("Inanspruchnahme");
  assertThat(bad.apply(r->new GuaranteeService.Request(r.reference(),r.guaranteeType(),r.bank(),r.beneficiary(),r.currency(),r.amount(),r.issueDate(),r.validFrom(),r.expiryDate(),r.claimUntil(),r.autoExtension(),9999,r.underlying(),r.beneficiaryReference(),r.status()))).hasMessageContaining("Entscheidung");
  assertThat(bad.apply(r->new GuaranteeService.Request(r.reference(),r.guaranteeType(),r.bank(),r.beneficiary(),r.currency(),r.amount(),r.issueDate(),r.validFrom(),r.expiryDate(),r.claimUntil(),r.autoExtension(),r.extensionNoticeDays(),r.underlying(),r.beneficiaryReference(),"WEIRD"))).hasMessageContaining("Status");
  // automatic extension without an end date is allowed
  GuaranteeService.validate(new GuaranteeService.Request("OPEN","OTHER","Bank","Begünstigter","EUR",BigDecimal.ONE,null,null,null,null,true,null,null,null,null),true);
 }
 @Test void aGuaranteeThatIsNoLongerOpenClosesTheFileForTheReminders() {
  var file=lc("G-2","GUARANTEE",LocalDate.of(2027,1,1));when(lcs.findById(file.getId())).thenReturn(Optional.of(file));when(details.byLc(file.getId())).thenReturn(Optional.empty());when(details.save(any())).thenAnswer(i->i.getArgument(0));when(lcs.save(any())).thenAnswer(i->i.getArgument(0));
  var returned=service.setStatus(file.getId(),new GuaranteeService.StatusRequest("RETURNED",LocalDate.of(2026,10,20),"Original zurück"),null);
  assertThat(returned.status()).isEqualTo("RETURNED");assertThat(returned.open()).isFalse();assertThat(returned.statusDate()).isEqualTo(LocalDate.of(2026,10,20));assertThat(file.getStatus()).isEqualTo(LetterOfCreditStatus.CLOSED);
  service.setStatus(file.getId(),new GuaranteeService.StatusRequest("ACTIVE",null,null),null);assertThat(file.getStatus()).isEqualTo(LetterOfCreditStatus.ACTIVE);
  service.setStatus(file.getId(),new GuaranteeService.StatusRequest("EXPIRED",null,null),null);assertThat(file.getStatus()).isEqualTo(LetterOfCreditStatus.EXPIRED);
  assertThatThrownBy(()->service.setStatus(file.getId(),new GuaranteeService.StatusRequest("GONE",null,null),null)).hasMessageContaining("Status");
  assertThatThrownBy(()->service.setStatus(file.getId(),new GuaranteeService.StatusRequest("RETURNED",LocalDate.now().plusDays(1),null),null)).hasMessageContaining("Zukunft");
 }
 @Test void otherFilesAreNotGuarantees() {
  var credit=lc("LC-1","LC",null);when(lcs.findById(credit.getId())).thenReturn(Optional.of(credit));
  assertThatThrownBy(()->service.one(credit.getId(),TODAY)).isInstanceOf(NoSuchElementException.class);
  assertThatThrownBy(()->service.setStatus(credit.getId(),new GuaranteeService.StatusRequest("RETURNED",null,null),null)).isInstanceOf(NoSuchElementException.class);
 }
 @Test void theListHoldsOnlyGuaranteesOpenOnesFirstSortedByExpiryAndFlagsThoseExpiringSoon() {
  var soon=lc("SOON","GUARANTEE",TODAY.plusDays(30));var later=lc("LATER","GUARANTEE",TODAY.plusDays(200));var done=lc("DONE","GUARANTEE",TODAY.plusDays(10));var credit=lc("CREDIT","LC",TODAY.plusDays(5));var imported=lc("IMPORTED","GUARANTEE",TODAY.plusDays(100)); // imported MT760: no details
  var doneDetail=new GuaranteeDetail(done.getId());doneDetail.setStatus("RETURNED",TODAY,null,"a");
  when(lcs.findAll()).thenReturn(List.of(later,done,credit,imported,soon));when(details.findAll()).thenReturn(List.of(doneDetail));
  var list=service.list(TODAY);
  assertThat(list).extracting(GuaranteeService.View::reference).containsExactly("SOON","IMPORTED","LATER","DONE");
  assertThat(list.get(0).expiringSoon()).isTrue();assertThat(list.get(0).daysToExpiry()).isEqualTo(30);assertThat(list.get(1).expiringSoon()).isFalse();assertThat(list.get(1).detailsKnown()).isFalse();assertThat(list.get(1).status()).isEqualTo("ACTIVE");
  assertThat(list.get(3).expiringSoon()).isFalse();assertThat(list.get(3).open()).isFalse();
 }

 @Test void theDeadlinesOfAnOpenGuaranteeAreExpiryClaimPeriodAndTheDecisionDate() {
  var file=lc("G-3","GUARANTEE",LocalDate.of(2027,3,31));var detail=new GuaranteeDetail(file.getId());detail.set("PERFORMANCE",null,LocalDate.of(2027,6,30),true,60,null,null,"a");
  var lcRepo=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);when(lcRepo.findById(file.getId())).thenReturn(Optional.of(file));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(file.getId())).thenReturn(List.of());
  var repo=mock(GuaranteeDetailRepository.class);when(repo.byLc(file.getId())).thenReturn(Optional.of(detail));
  var deadlines=new LcDeadlineService(lcRepo,docs);ReflectionTestUtils.setField(deadlines,"guarantees",repo);
  var result=deadlines.forLc(file.getId());
  assertThat(result).extracting(LcDeadlineService.LcDeadlineView::type).containsExactly("EXTENSION_DECISION","EXPIRY","CLAIM_UNTIL");
  assertThat(result.get(0).date()).isEqualTo(LocalDate.of(2027,1,30));assertThat(result.get(0).title()).contains("Verlängerung verhindern");assertThat(result.get(1).title()).isEqualTo("Ablauf der Garantie");
  detail.setStatus("RETURNED",LocalDate.of(2026,10,1),null,"a");
  assertThat(deadlines.forLc(file.getId())).isEmpty(); // a returned guarantee has no deadlines
  var credit=lc("LC-2","LC",LocalDate.of(2027,3,31));when(lcRepo.findById(credit.getId())).thenReturn(Optional.of(credit));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(credit.getId())).thenReturn(List.of());
  assertThat(deadlines.forLc(credit.getId())).extracting(LcDeadlineService.LcDeadlineView::title).containsExactly("Ablauf des Akkreditivs");
 }
}
