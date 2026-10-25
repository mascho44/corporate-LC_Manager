package de.ostms.lc.payment;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import de.ostms.lc.lc.service.LcDeadlineService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/** Payments received per file and the payment status derived from them: none, partial, paid, or more than the amount of the credit. */
@Service
public class PaymentService {
 static final int MAX_PER_LC=200;
 public record Request(LocalDate receivedOn,BigDecimal amount,String currency,String reference,String note){}
 public record View(UUID id,LocalDate receivedOn,BigDecimal amount,String currency,String reference,String note,String source,String recordedBy){}
 /** status NONE | PARTIAL | PAID | OVER; received counts only payments in the currency of the credit. */
 public record Summary(String status,BigDecimal lcAmount,String currency,BigDecimal received,BigDecimal open,LocalDate expectedDate,boolean overdue,boolean otherCurrency){}
 public record Payments(Summary summary,List<View> payments){}
 private final LcPaymentRepository payments;private final LetterOfCreditRepository lcs;private final LcDeadlineService deadlines;private final AuditService audit;
 public PaymentService(LcPaymentRepository payments,LetterOfCreditRepository lcs,LcDeadlineService deadlines,AuditService audit){this.payments=payments;this.lcs=lcs;this.deadlines=deadlines;this.audit=audit;}

 static View view(LcPayment p){return new View(p.getId(),p.getReceivedOn(),p.getAmount(),p.getCurrency(),p.getReference(),p.getNote(),p.getSource(),p.getRecordedBy());}

 @Transactional(readOnly=true) public Payments forLc(UUID lcId,LocalDate today){
  var lc=lcs.findById(lcId).orElseThrow(()->new NoSuchElementException("Akkreditiv nicht gefunden"));
  var list=payments.forLc(lcId);
  return new Payments(summarize(lc.getAmount(),lc.getCurrency(),list,expectedDate(lcId),today),list.stream().map(PaymentService::view).toList());
 }
 @Transactional(readOnly=true) public Summary summary(UUID lcId,LocalDate today){return forLc(lcId,today).summary();}

 @Transactional public View record(UUID lcId,Request r,String source,Authentication auth){
  var lc=lcs.findById(lcId).orElseThrow(()->new NoSuchElementException("Akkreditiv nicht gefunden"));
  if(r==null||r.amount()==null||r.amount().signum()<=0)throw new IllegalArgumentException("Bitte einen Betrag größer als 0 angeben.");
  BigDecimal amount=r.amount().setScale(2,RoundingMode.HALF_UP);
  if(amount.precision()>19)throw new IllegalArgumentException("Der Betrag ist zu groß.");
  String currency=r.currency()==null||r.currency().isBlank()?lc.getCurrency():r.currency().trim().toUpperCase(Locale.ROOT);
  if(currency==null||!currency.matches("[A-Z]{3}"))throw new IllegalArgumentException("Bitte eine Währung mit drei Buchstaben angeben.");
  LocalDate day=r.receivedOn()==null?LocalDate.now():r.receivedOn();
  if(day.isAfter(LocalDate.now()))throw new IllegalArgumentException("Das Eingangsdatum darf nicht in der Zukunft liegen.");
  if(payments.forLc(lcId).size()>=MAX_PER_LC)throw new IllegalArgumentException("Es sind höchstens "+MAX_PER_LC+" Zahlungen je Akte möglich.");
  String reference=clip(r.reference(),100),note=clip(r.note(),500);
  var saved=payments.save(new LcPayment(lcId,day,amount,currency,reference,note,source,auth==null?null:auth.getName()));
  audit.recordInTransaction(auth,"LC_PAYMENT_RECORDED","LETTER_OF_CREDIT",lcId,day+" · "+currency+" "+amount.toPlainString()+(reference==null?"":" · "+reference)+" · "+source);
  return view(saved);
 }
 @Transactional public void delete(UUID lcId,UUID paymentId,Authentication auth){
  var payment=payments.findById(paymentId).filter(p->p.getLcId().equals(lcId)).orElseThrow(()->new NoSuchElementException("Zahlung nicht gefunden."));
  payments.delete(payment);
  audit.recordInTransaction(auth,"LC_PAYMENT_DELETED","LETTER_OF_CREDIT",lcId,payment.getReceivedOn()+" · "+payment.getCurrency()+" "+payment.getAmount().toPlainString());
 }

 /** The earliest estimated maturity of the file (from the payment clause), or null. */
 private LocalDate expectedDate(UUID lcId){
  return deadlines.forLc(lcId).stream().filter(d->"MATURITY_ESTIMATE".equals(d.type())).map(LcDeadlineService.LcDeadlineView::date).min(Comparator.naturalOrder()).orElse(null);
 }
 static Summary summarize(BigDecimal lcAmount,String currency,List<LcPayment> list,LocalDate expected,LocalDate today){
  BigDecimal received=BigDecimal.ZERO;boolean other=false;
  for(var p:list){if(currency!=null&&currency.equalsIgnoreCase(p.getCurrency()))received=received.add(p.getAmount());else other=true;}
  String status;
  if(received.signum()==0)status="NONE";
  else if(lcAmount==null)status="PARTIAL";
  else if(received.compareTo(lcAmount)<0)status="PARTIAL";
  else if(received.compareTo(lcAmount)==0)status="PAID";
  else status="OVER";
  BigDecimal open=lcAmount==null?null:lcAmount.subtract(received).max(BigDecimal.ZERO);
  boolean overdue=expected!=null&&today.isAfter(expected)&&(status.equals("NONE")||status.equals("PARTIAL"));
  return new Summary(status,lcAmount,currency,received,open,expected,overdue,other);
 }
 private static String clip(String v,int max){if(v==null||v.isBlank())return null;String t=v.trim();return t.length()<=max?t:t.substring(0,max);}
}
