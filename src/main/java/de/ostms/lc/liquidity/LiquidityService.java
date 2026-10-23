package de.ostms.lc.liquidity;

import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.document.repository.LcDocumentRepository;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.domain.LetterOfCreditStatus;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import de.ostms.lc.lc.service.LcDeadlineService;
import de.ostms.lc.payment.LcPayment;
import de.ostms.lc.payment.LcPaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Liquidity preview of a corporate tenant: the open amount of every credit with a known or estimated maturity. Export credits (the company is beneficiary)
 * are inflows, import credits (the company is applicant) are outflows. Everything is an estimate from the payment clause; amounts are the commercial
 * invoices of the file when there are any (at most the credit amount), otherwise the credit amount, less the payments already recorded.
 */
@Service
public class LiquidityService {
 static final int MAX_FILES=500;
 public record Response(LiquidityCalculator.Forecast forecast,List<LiquidityCalculator.Item> items,boolean truncated){}
 private final LetterOfCreditRepository lcs;private final LcDeadlineService deadlines;private final LcPaymentRepository payments;private final LcDocumentRepository documents;
 public LiquidityService(LetterOfCreditRepository lcs,LcDeadlineService deadlines,LcPaymentRepository payments,LcDocumentRepository documents){this.lcs=lcs;this.deadlines=deadlines;this.payments=payments;this.documents=documents;}

 @Transactional(readOnly=true) public Response preview(LiquidityCalculator.Granularity granularity,int periods,LocalDate today){
  var files=lcs.findAll().stream().filter(lc->"LC".equals(lc.getInstrumentType())&&lc.getStatus()!=LetterOfCreditStatus.CLOSED&&lc.getStatus()!=LetterOfCreditStatus.EXPIRED&&lc.getAmount()!=null&&lc.getCurrency()!=null).toList();
  boolean truncated=files.size()>MAX_FILES;
  var items=new ArrayList<LiquidityCalculator.Item>();
  for(var lc:files.stream().limit(MAX_FILES).toList()){item(lc).ifPresent(items::add);}
  items.sort(Comparator.comparing((LiquidityCalculator.Item i)->i.date()==null?LocalDate.MAX:i.date()).thenComparing(LiquidityCalculator.Item::reference,Comparator.nullsLast(Comparator.naturalOrder())));
  return new Response(LiquidityCalculator.compute(items,today,granularity,periods),items,truncated);
 }

 private Optional<LiquidityCalculator.Item> item(LetterOfCredit lc){
  UUID id=lc.getId();
  boolean inflow=lc.isExporterSide();
  LocalDate date=deadlines.forLc(id).stream().filter(d->"MATURITY_ESTIMATE".equals(d.type())).map(LcDeadlineService.LcDeadlineView::date).min(Comparator.naturalOrder()).orElse(null);
  BigDecimal invoices=BigDecimal.ZERO;boolean hasInvoice=false;
  for(var doc:documents.findByLetterOfCreditIdOrderByUploadedAtDesc(id)){
   if(doc.getDocumentType()==DocumentType.COMMERCIAL_INVOICE&&doc.getAmount()!=null&&lc.getCurrency().equalsIgnoreCase(doc.getCurrency())){invoices=invoices.add(doc.getAmount());hasInvoice=true;}
  }
  BigDecimal expected=hasInvoice?invoices.min(lc.getAmount()):lc.getAmount();
  BigDecimal received=BigDecimal.ZERO;
  for(LcPayment p:payments.forLc(id))if(lc.getCurrency().equalsIgnoreCase(p.getCurrency()))received=received.add(p.getAmount());
  BigDecimal open=expected.subtract(received);
  if(open.signum()<=0)return Optional.empty();
  String other=inflow?lc.getApplicant():lc.getBeneficiary();
  return Optional.of(new LiquidityCalculator.Item(id,lc.getReference(),other==null?null:other.lines().findFirst().orElse(null),inflow?"IN":"OUT",lc.getCurrency().toUpperCase(Locale.ROOT),open,date,hasInvoice?"INVOICE":"CREDIT"));
 }
}
