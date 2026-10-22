package de.ostms.lc.liquidity;

import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.document.domain.LcDocument;
import de.ostms.lc.document.repository.LcDocumentRepository;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.domain.LetterOfCreditStatus;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import de.ostms.lc.lc.service.LcDeadlineService;
import de.ostms.lc.payment.LcPayment;
import de.ostms.lc.payment.LcPaymentRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LiquidityTest {
 static final LocalDate TODAY=LocalDate.of(2026,10,22); // Thursday, week 43
 private static LiquidityCalculator.Item item(String direction,String currency,String amount,LocalDate date){return new LiquidityCalculator.Item(UUID.randomUUID(),"LC-"+amount,"X",direction,currency,new BigDecimal(amount),date,"CREDIT");}
 private static LiquidityCalculator.Period period(LiquidityCalculator.Forecast f,String key){return f.periods().stream().filter(p->p.key().equals(key)).findFirst().orElseThrow();}

 @Test void weeksGroupInflowsAndOutflowsPerCurrencyWithOverdueAndUndatedRows() {
  var items=List.of(item("IN","EUR","1000",LocalDate.of(2026,10,23)),item("IN","EUR","500",LocalDate.of(2026,10,25)),item("OUT","EUR","300",LocalDate.of(2026,10,26)),
   item("IN","USD","200",LocalDate.of(2026,10,28)),item("IN","EUR","70",LocalDate.of(2026,10,21)),item("OUT","EUR","40",null),item("IN","EUR","9",LocalDate.of(2027,6,1)));
  var forecast=LiquidityCalculator.compute(items,TODAY,LiquidityCalculator.Granularity.WEEK,4);
  assertThat(forecast.currencies()).containsExactly("EUR","USD");
  assertThat(forecast.periods()).extracting(LiquidityCalculator.Period::key).containsExactly("OVERDUE","2026-W43","2026-W44","2026-W45","2026-W46","NO_DATE");
  var w43=period(forecast,"2026-W43").byCurrency().get("EUR");assertThat(w43.in()).isEqualByComparingTo("1500");assertThat(w43.out()).isZero();assertThat(w43.net()).isEqualByComparingTo("1500");
  var w44=period(forecast,"2026-W44");assertThat(w44.byCurrency().get("EUR").out()).isEqualByComparingTo("300");assertThat(w44.byCurrency().get("EUR").net()).isEqualByComparingTo("-300");assertThat(w44.byCurrency().get("USD").in()).isEqualByComparingTo("200");
  assertThat(period(forecast,"OVERDUE").byCurrency().get("EUR").in()).isEqualByComparingTo("70");
  assertThat(period(forecast,"NO_DATE").byCurrency().get("EUR").out()).isEqualByComparingTo("40");
  assertThat(forecast.periods().stream().flatMap(p->p.byCurrency().values().stream()).map(LiquidityCalculator.Amounts::in).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("1770"); // the item beyond the horizon is left out
  assertThat(period(forecast,"2026-W43").from()).isEqualTo(LocalDate.of(2026,10,19));assertThat(period(forecast,"2026-W43").to()).isEqualTo(LocalDate.of(2026,10,25));
 }
 @Test void monthsAndTheLimitsOfThePeriodCount() {
  var items=List.of(item("IN","EUR","100",LocalDate.of(2026,10,30)),item("IN","EUR","200",LocalDate.of(2026,11,2)),item("OUT","EUR","50",LocalDate.of(2027,1,31)));
  var forecast=LiquidityCalculator.compute(items,TODAY,LiquidityCalculator.Granularity.MONTH,3);
  assertThat(forecast.periods()).extracting(LiquidityCalculator.Period::key).containsExactly("OVERDUE","2026-10","2026-11","2026-12","NO_DATE");
  assertThat(period(forecast,"2026-10").byCurrency().get("EUR").in()).isEqualByComparingTo("100");assertThat(period(forecast,"2026-11").byCurrency().get("EUR").in()).isEqualByComparingTo("200");
  assertThat(LiquidityCalculator.compute(items,TODAY,LiquidityCalculator.Granularity.MONTH,99).periods()).hasSize(24+2);
  assertThat(LiquidityCalculator.compute(items,TODAY,LiquidityCalculator.Granularity.WEEK,0).periods()).hasSize(1+2);
  assertThat(LiquidityCalculator.compute(List.of(),TODAY,LiquidityCalculator.Granularity.WEEK,4).currencies()).isEmpty();
 }

 private final LetterOfCreditRepository lcs=mock(LetterOfCreditRepository.class);private final LcDeadlineService deadlines=mock(LcDeadlineService.class);
 private final LcPaymentRepository payments=mock(LcPaymentRepository.class);private final LcDocumentRepository documents=mock(LcDocumentRepository.class);
 private final LiquidityService service=new LiquidityService(lcs,deadlines,payments,documents);
 private LetterOfCredit lc(String ref,String role,String amount,LetterOfCreditStatus status,String type){
  var lc=new LetterOfCredit();org.springframework.test.util.ReflectionTestUtils.setField(lc,"id",UUID.randomUUID());lc.setReference(ref);lc.setPartyRole(role);lc.setAmount(new BigDecimal(amount));lc.setCurrency("EUR");lc.setStatus(status);lc.setInstrumentType(type);lc.setApplicant("ACME GMBH\nHAMBURG");lc.setBeneficiary("SUPPLIER LTD\nSHANGHAI");return lc;
 }
 private void maturity(LetterOfCredit lc,LocalDate... dates){
  var list=new java.util.ArrayList<LcDeadlineService.LcDeadlineView>();for(var d:dates)list.add(new LcDeadlineService.LcDeadlineView("MATURITY_ESTIMATE","t",d,"s","n",false));
  list.add(new LcDeadlineService.LcDeadlineView("EXPIRY","Ablauf",LocalDate.of(2020,1,1),"s","n",true));
  when(deadlines.forLc(lc.getId())).thenReturn(list);
 }

 @Test void theServiceBuildsOpenAmountsFromInvoicesAndPaymentsAndSetsTheDirectionByRole() {
  var export=lc("EXP-1","BENEFICIARY","100000",LetterOfCreditStatus.ACTIVE,"LC");maturity(export,LocalDate.of(2026,11,30),LocalDate.of(2026,11,15));
  var invoice=new LcDocument();invoice.setDocumentType(DocumentType.COMMERCIAL_INVOICE);invoice.setAmount(new BigDecimal("80000"));invoice.setCurrency("EUR");
  var tooBig=new LcDocument();tooBig.setDocumentType(DocumentType.COMMERCIAL_INVOICE);tooBig.setAmount(new BigDecimal("30000"));tooBig.setCurrency("EUR");
  when(documents.findByLetterOfCreditIdOrderByUploadedAtDesc(export.getId())).thenReturn(List.of(invoice));
  var imp=lc("IMP-1","APPLICANT","50000",LetterOfCreditStatus.ACTIVE,"LC");maturity(imp,LocalDate.of(2026,12,1));
  var paid=lc("PAID-1","BENEFICIARY","1000",LetterOfCreditStatus.ACTIVE,"LC");maturity(paid,LocalDate.of(2026,11,1));
  when(payments.forLc(paid.getId())).thenReturn(List.of(new LcPayment(paid.getId(),TODAY,new BigDecimal("1000"),"EUR",null,null,"MANUAL","a")));
  var partly=lc("PART-1","BENEFICIARY","1000",LetterOfCreditStatus.ACTIVE,"LC");maturity(partly,LocalDate.of(2026,11,1));
  when(payments.forLc(partly.getId())).thenReturn(List.of(new LcPayment(partly.getId(),TODAY,new BigDecimal("400"),"EUR",null,null,"MANUAL","a"),new LcPayment(partly.getId(),TODAY,new BigDecimal("999"),"USD",null,null,"MANUAL","a")));
  var closed=lc("CLOSED-1","BENEFICIARY","1000",LetterOfCreditStatus.CLOSED,"LC");
  var guarantee=lc("G-1","APPLICANT","1000",LetterOfCreditStatus.ACTIVE,"GUARANTEE");
  var undated=lc("NODATE-1","APPLICANT","700",LetterOfCreditStatus.ACTIVE,"LC");when(deadlines.forLc(undated.getId())).thenReturn(List.of());
  when(lcs.findAll()).thenReturn(List.of(export,imp,paid,partly,closed,guarantee,undated));
  var result=service.preview(LiquidityCalculator.Granularity.WEEK,13,TODAY);
  assertThat(result.items()).extracting(LiquidityCalculator.Item::reference).containsExactly("PART-1","EXP-1","IMP-1","NODATE-1");
  var byRef=new java.util.HashMap<String,LiquidityCalculator.Item>();result.items().forEach(i->byRef.put(i.reference(),i));
  assertThat(byRef.get("EXP-1").direction()).isEqualTo("IN");assertThat(byRef.get("EXP-1").amount()).isEqualByComparingTo("80000");assertThat(byRef.get("EXP-1").basis()).isEqualTo("INVOICE");assertThat(byRef.get("EXP-1").date()).isEqualTo(LocalDate.of(2026,11,15));
  assertThat(byRef.get("EXP-1").counterparty()).isEqualTo("ACME GMBH");
  assertThat(byRef.get("PART-1").amount()).isEqualByComparingTo("600");
  assertThat(byRef.get("IMP-1").direction()).isEqualTo("OUT");assertThat(byRef.get("IMP-1").amount()).isEqualByComparingTo("50000");assertThat(byRef.get("IMP-1").basis()).isEqualTo("CREDIT");assertThat(byRef.get("IMP-1").counterparty()).isEqualTo("SUPPLIER LTD");
  assertThat(byRef.get("NODATE-1").date()).isNull();
  assertThat(result.truncated()).isFalse();
 }
}
