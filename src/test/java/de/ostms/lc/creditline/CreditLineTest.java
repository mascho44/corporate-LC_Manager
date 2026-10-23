package de.ostms.lc.creditline;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.guarantee.GuaranteeService;
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

class CreditLineTest {
 static final LocalDate TODAY=LocalDate.of(2026,10,23);
 private final CreditLineRepository repo=mock(CreditLineRepository.class);private final GuaranteeService guarantees=mock(GuaranteeService.class);private final AuditService audit=mock(AuditService.class);
 private final CreditLineService service=new CreditLineService(repo,guarantees,audit);

 private static CreditLine line(String name,String bank,String bic,String currency,String amount,String type,boolean active){
  var l=new CreditLine();l.set(name,bank,bic,currency,new BigDecimal(amount),type,null,80,active,null,"t");return l;
 }
 private static GuaranteeService.View guarantee(String bank,String currency,String amount,String type,String status){
  boolean open=status.equals("ACTIVE")||status.equals("REQUESTED");
  return new GuaranteeService.View(UUID.randomUUID(),"G-"+amount,type,bank,"Kunde",currency,new BigDecimal(amount),null,null,null,null,false,null,null,null,null,status,null,null,null,false,open,true);
 }

 @Test void theBankIsMatchedByItsFirstLineOrByTheBicAndIgnoresCaseAndPunctuation() {
  var l=line("A","Musterbank AG",null,"EUR","1000",null,true);
  assertThat(CreditLineService.matchesBank(l,"MUSTERBANK AG\nFrankfurt")).isTrue();
  assertThat(CreditLineService.matchesBank(l,"  Muster-Bank AG.  ")).isTrue();
  assertThat(CreditLineService.matchesBank(l,"Andere Bank AG")).isFalse();
  assertThat(CreditLineService.matchesBank(l,"Musterbank AG International")).isFalse();
  var withBic=line("B","Kreditinstitut",  "MUSTDEFF","EUR","1000",null,true);
  assertThat(CreditLineService.matchesBank(withBic,"Name der Bank\nBIC MUST DEFF")).isTrue();
  assertThat(CreditLineService.matchesBank(withBic,"")).isFalse();
 }

 @Test void aGuaranteeCountsTowardsTheLineOfItsTypeFirstThenTheLineForAllTypesAndInactiveOrOtherCurrenciesDoNotCount() {
  var general=line("Allgemein","Musterbank AG",null,"EUR","100000",null,true);var performance=line("Erfüllung","Musterbank AG",null,"EUR","30000","PERFORMANCE",true);
  var inactive=line("Alt","Musterbank AG",null,"EUR","1",null,false);var usd=line("USD","Musterbank AG",null,"USD","50000",null,true);
  var all=List.of(general,performance,inactive,usd);
  assertThat(CreditLineService.lineFor(all,"Musterbank AG","EUR","PERFORMANCE")).contains(performance);
  assertThat(CreditLineService.lineFor(all,"Musterbank AG","EUR","WARRANTY")).contains(general);
  assertThat(CreditLineService.lineFor(all,"Musterbank AG","usd","BID")).contains(usd);
  assertThat(CreditLineService.lineFor(all,"Musterbank AG","CHF","BID")).isEmpty();
  assertThat(CreditLineService.lineFor(all,"Andere Bank","EUR","BID")).isEmpty();
 }

 @Test void theOverviewSumsOpenGuaranteesPerLineSetsTheStateAndListsGuaranteesWithoutLine() {
  var general=line("Allgemein","Musterbank AG",null,"EUR","100000",null,true);var performance=line("Erfüllung","Musterbank AG",null,"EUR","30000","PERFORMANCE",true);
  when(repo.findAll()).thenReturn(List.of(general,performance));
  when(guarantees.list(TODAY)).thenReturn(List.of(
   guarantee("Musterbank AG","EUR","20000","PERFORMANCE","ACTIVE"),guarantee("Musterbank AG","EUR","15000","PERFORMANCE","REQUESTED"), // 35000 on a 30000 line
   guarantee("Musterbank AG","EUR","79000","WARRANTY","ACTIVE"),guarantee("Musterbank AG","EUR","5000","WARRANTY","RETURNED"),guarantee("Musterbank AG","EUR","7000","WARRANTY","CLAIMED"),
   guarantee("Andere Bank","EUR","1000","BID","ACTIVE")));
  var overview=service.overview(TODAY);
  var byName=new java.util.HashMap<String,CreditLineService.View>();overview.lines().forEach(v->byName.put(v.name(),v));
  var perf=byName.get("Erfüllung");assertThat(perf.used()).isEqualByComparingTo("35000");assertThat(perf.free()).isEqualByComparingTo("-5000");assertThat(perf.state()).isEqualTo("EXCEEDED");assertThat(perf.guaranteeCount()).isEqualTo(2);
  var gen=byName.get("Allgemein");assertThat(gen.used()).isEqualByComparingTo("79000");assertThat(gen.percent()).isEqualTo(79);assertThat(gen.state()).isEqualTo("OK");
  assertThat(overview.withoutLine()).hasSize(1).first().satisfies(g->assertThat(g.bank()).isEqualTo("Andere Bank"));
 }

 @Test void warningStartsAtTheWarningShareAndAnExpiredLineIsFlagged() {
  var l=line("L","Bank","","EUR","1000",null,true);
  assertThat(CreditLineService.view(l,new BigDecimal("799"),1,TODAY).state()).isEqualTo("OK");
  assertThat(CreditLineService.view(l,new BigDecimal("800"),1,TODAY).state()).isEqualTo("WARNING");
  assertThat(CreditLineService.view(l,new BigDecimal("1000"),1,TODAY).state()).isEqualTo("WARNING");
  assertThat(CreditLineService.view(l,new BigDecimal("1000.01"),1,TODAY).state()).isEqualTo("EXCEEDED");
  l.set("L","Bank",null,"EUR",new BigDecimal("1000"),null,TODAY.minusDays(1),80,true,null,"t");assertThat(CreditLineService.view(l,BigDecimal.ZERO,0,TODAY).expired()).isTrue();
  l.set("L","Bank",null,"EUR",new BigDecimal("1000"),null,TODAY,80,true,null,"t");assertThat(CreditLineService.view(l,BigDecimal.ZERO,0,TODAY).expired()).isFalse();
 }

 @Test void theCheckSaysWhetherANewGuaranteeFitsAndLeavesOutTheOneThatIsBeingChanged() {
  var general=line("Allgemein","Musterbank AG",null,"EUR","100000",null,true);when(repo.findAll()).thenReturn(List.of(general));
  var existing=guarantee("Musterbank AG","EUR","60000","PERFORMANCE","ACTIVE");when(guarantees.list(TODAY)).thenReturn(List.of(existing));
  var fits=service.check("MUSTERBANK AG","eur","bid",new BigDecimal("30000"),null,TODAY);
  assertThat(fits.found()).isTrue();assertThat(fits.fits()).isTrue();assertThat(fits.free()).isEqualByComparingTo("40000");assertThat(fits.freeAfter()).isEqualByComparingTo("10000");assertThat(fits.state()).isEqualTo("WARNING"); // 90 % of the line
  var tooBig=service.check("Musterbank AG","EUR",null,new BigDecimal("45000"),null,TODAY);
  assertThat(tooBig.fits()).isFalse();assertThat(tooBig.freeAfter()).isEqualByComparingTo("-5000");assertThat(tooBig.state()).isEqualTo("EXCEEDED");
  var editing=service.check("Musterbank AG","EUR",null,new BigDecimal("90000"),existing.id(),TODAY);
  assertThat(editing.fits()).isTrue();assertThat(editing.free()).isEqualByComparingTo("100000");
  var none=service.check("Andere Bank","EUR",null,new BigDecimal("1"),null,TODAY);
  assertThat(none.found()).isFalse();assertThat(none.fits()).isTrue();
 }

 @Test void validationNormalisesAndRejectsBadInput() {
  var ok=CreditLineService.validate(new CreditLineService.Request(" Avallinie ","Musterbank AG"," mustdeff ","eur",new BigDecimal("5000.555"),"performance",null,null,null," "));
  assertThat(ok.currency()).isEqualTo("EUR");assertThat(ok.bic()).isEqualTo("MUSTDEFF");assertThat(ok.amount()).isEqualByComparingTo("5000.56");assertThat(ok.guaranteeType()).isEqualTo("PERFORMANCE");assertThat(ok.warnPercent()).isEqualTo(80);assertThat(ok.note()).isNull();
  java.util.function.UnaryOperator<CreditLineService.Request> base=r->r;
  assertThatThrownBy(()->CreditLineService.validate(new CreditLineService.Request(" ","B",null,"EUR",BigDecimal.ONE,null,null,null,null,null))).hasMessageContaining("Namen");
  assertThatThrownBy(()->CreditLineService.validate(new CreditLineService.Request("N"," ",null,"EUR",BigDecimal.ONE,null,null,null,null,null))).hasMessageContaining("Bank");
  assertThatThrownBy(()->CreditLineService.validate(new CreditLineService.Request("N","B","KURZ","EUR",BigDecimal.ONE,null,null,null,null,null))).hasMessageContaining("BIC");
  assertThatThrownBy(()->CreditLineService.validate(new CreditLineService.Request("N","B",null,"EURO",BigDecimal.ONE,null,null,null,null,null))).hasMessageContaining("Währung");
  assertThatThrownBy(()->CreditLineService.validate(new CreditLineService.Request("N","B",null,"EUR",BigDecimal.ZERO,null,null,null,null,null))).hasMessageContaining("Betrag");
  assertThatThrownBy(()->CreditLineService.validate(new CreditLineService.Request("N","B",null,"EUR",BigDecimal.ONE,"MAGIC",null,null,null,null))).hasMessageContaining("Garantieart");
  assertThatThrownBy(()->CreditLineService.validate(new CreditLineService.Request("N","B",null,"EUR",BigDecimal.ONE,null,null,5,null,null))).hasMessageContaining("Warnschwelle");
 }

 @Test void createUpdateAndDeleteAuditAndRefuseDuplicateNames() {
  when(repo.byName("Avallinie")).thenReturn(Optional.empty());when(repo.save(any())).thenAnswer(i->i.getArgument(0));when(guarantees.list(any())).thenReturn(List.of());
  var req=new CreditLineService.Request("Avallinie","Musterbank AG",null,"EUR",new BigDecimal("1000"),null,null,null,null,null);
  var created=service.create(req,null);assertThat(created.name()).isEqualTo("Avallinie");verify(audit).recordInTransaction(any(),eq("CREDIT_LINE_CREATED"),eq("CREDIT_LINE"),any(),anyString());
  var existing=line("Avallinie","Musterbank AG",null,"EUR","1000",null,true);when(repo.byName("Avallinie")).thenReturn(Optional.of(existing));
  assertThatThrownBy(()->service.create(req,null)).hasMessageContaining("schon eine Linie");
  when(repo.findById(existing.getId())).thenReturn(Optional.of(existing));when(repo.findAll()).thenReturn(List.of(existing));
  assertThat(service.update(existing.getId(),new CreditLineService.Request("Avallinie","Musterbank AG",null,"EUR",new BigDecimal("2000"),null,null,null,null,null),null).amount()).isEqualByComparingTo("2000");
  service.delete(existing.getId(),null);verify(repo).delete(existing);verify(audit).recordInTransaction(any(),eq("CREDIT_LINE_DELETED"),eq("CREDIT_LINE"),any(),anyString());
  assertThatThrownBy(()->service.delete(UUID.randomUUID(),null)).isInstanceOf(NoSuchElementException.class);
 }
}
