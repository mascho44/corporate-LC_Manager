package de.ostms.lc.guarantee;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.domain.LetterOfCreditStatus;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Guarantees the company has had issued by a bank (customer side). A guarantee is a file of kind GUARANTEE plus its details; MT760 files without details count as active. */
@Service
public class GuaranteeService {
 public static final List<String> TYPES=List.of("ADVANCE_PAYMENT","PERFORMANCE","WARRANTY","BID","PAYMENT","CUSTOMS","RENT","OTHER");
 public static final List<String> STATUSES=List.of("REQUESTED","ACTIVE","RETURNED","CLAIMED","EXPIRED","CANCELLED");
 /** Guarantees that expire within this many days are flagged. */
 static final int SOON_DAYS=90;
 public record Request(String reference,String guaranteeType,String bank,String beneficiary,String currency,BigDecimal amount,LocalDate issueDate,LocalDate validFrom,LocalDate expiryDate,LocalDate claimUntil,
   Boolean autoExtension,Integer extensionNoticeDays,String underlying,String beneficiaryReference,String status){}
 public record StatusRequest(String status,LocalDate date,String note){}
 public record View(UUID id,String reference,String guaranteeType,String bank,String beneficiary,String currency,BigDecimal amount,LocalDate issueDate,LocalDate validFrom,LocalDate expiryDate,LocalDate claimUntil,
   boolean autoExtension,Integer extensionNoticeDays,LocalDate decisionDate,String underlying,String beneficiaryReference,String status,LocalDate statusDate,String statusNote,Long daysToExpiry,boolean expiringSoon,boolean open,boolean detailsKnown,String bankReference){}
 private final LetterOfCreditRepository lcs;private final GuaranteeDetailRepository details;private final AuditService audit;
 public GuaranteeService(LetterOfCreditRepository lcs,GuaranteeDetailRepository details,AuditService audit){this.lcs=lcs;this.details=details;this.audit=audit;}

 static boolean open(String status){return "REQUESTED".equals(status)||"ACTIVE".equals(status);}

 @Transactional(readOnly=true) public List<View> list(LocalDate today){
  var byLc=new HashMap<UUID,GuaranteeDetail>();details.findAll().forEach(d->byLc.put(d.getLcId(),d));
  return lcs.findAll().stream().filter(lc->"GUARANTEE".equals(lc.getInstrumentType())).map(lc->view(lc,byLc.get(lc.getId()),today))
    .sorted(Comparator.comparing((View v)->!v.open()).thenComparing(v->v.expiryDate()==null?LocalDate.MAX:v.expiryDate()).thenComparing(View::reference)).toList();
 }
 @Transactional(readOnly=true) public View one(UUID lcId,LocalDate today){var lc=guarantee(lcId);return view(lc,details.byLc(lcId).orElse(null),today);}

 @Transactional public View create(Request r,Authentication auth){
  var c=validate(r,true);
  if(lcs.existsByReference(c.reference()))throw new IllegalArgumentException("Eine Akte mit dieser Referenz existiert bereits: "+c.reference());
  var lc=new LetterOfCredit();lc.setInstrumentType("GUARANTEE");lc.setPartyRole("APPLICANT");apply(lc,c);
  lcs.save(lc);
  var detail=new GuaranteeDetail(lc.getId());detail.set(c.guaranteeType(),c.validFrom(),c.claimUntil(),c.autoExtension()!=null&&c.autoExtension(),c.extensionNoticeDays(),c.underlying(),c.beneficiaryReference(),user(auth));
  detail.setStatus(c.status(),null,null,user(auth));details.save(detail);syncStatus(lc,c.status());lcs.save(lc);
  audit.recordInTransaction(auth,"GUARANTEE_CREATED","LETTER_OF_CREDIT",lc.getId(),lc.getReference()+" · "+c.guaranteeType()+" · "+lc.getCurrency()+" "+lc.getAmount().toPlainString());
  return view(lc,detail,LocalDate.now());
 }
 /** The bank's reference of the issued guarantee; empty clears it. */
 @Transactional public View setBankReference(UUID lcId,String value,Authentication auth){
  var lc=guarantee(lcId);var ref=value==null||value.isBlank()?null:value.strip();
  if(ref!=null&&ref.length()>100)throw new IllegalArgumentException("Die Referenz der Bank ist zu lang (höchstens 100 Zeichen).");
  var detail=details.byLc(lcId).orElseGet(()->new GuaranteeDetail(lcId));detail.setBankReference(ref,user(auth));details.save(detail);
  audit.recordInTransaction(auth,"GUARANTEE_UPDATED","LETTER_OF_CREDIT",lcId,lc.getReference()+" · Referenz der Bank "+(ref==null?"entfernt":ref));
  return view(lc,detail,LocalDate.now());
 }
 @Transactional public View update(UUID lcId,Request r,Authentication auth){
  var lc=guarantee(lcId);var c=validate(r,false);
  if(!lc.getReference().equals(c.reference())&&lcs.existsByReferenceAndIdNot(c.reference(),lcId))throw new IllegalArgumentException("Eine Akte mit dieser Referenz existiert bereits: "+c.reference());
  apply(lc,c);
  var detail=details.byLc(lcId).orElseGet(()->new GuaranteeDetail(lcId));
  detail.set(c.guaranteeType(),c.validFrom(),c.claimUntil(),c.autoExtension()!=null&&c.autoExtension(),c.extensionNoticeDays(),c.underlying(),c.beneficiaryReference(),user(auth));
  details.save(detail);lcs.save(lc);
  audit.recordInTransaction(auth,"GUARANTEE_UPDATED","LETTER_OF_CREDIT",lcId,lc.getReference()+" · "+c.guaranteeType());
  return view(lc,detail,LocalDate.now());
 }
 /** Returned (given back by the beneficiary), claimed, expired, active again or requested: closes the file for the reminders as soon as the guarantee is no longer open. */
 @Transactional public View setStatus(UUID lcId,StatusRequest r,Authentication auth){
  var lc=guarantee(lcId);
  if(r==null||r.status()==null||!STATUSES.contains(r.status()))throw new IllegalArgumentException("Unbekannter Status der Garantie.");
  LocalDate date=r.date()==null?LocalDate.now():r.date();
  if(date.isAfter(LocalDate.now()))throw new IllegalArgumentException("Das Datum darf nicht in der Zukunft liegen.");
  String note=r.note()==null||r.note().isBlank()?null:(r.note().trim().length()>500?r.note().trim().substring(0,500):r.note().trim());
  var detail=details.byLc(lcId).orElseGet(()->new GuaranteeDetail(lcId));
  detail.setStatus(r.status(),open(r.status())?null:date,note,user(auth));details.save(detail);syncStatus(lc,r.status());lcs.save(lc);
  audit.recordInTransaction(auth,"GUARANTEE_STATUS_CHANGED","LETTER_OF_CREDIT",lcId,lc.getReference()+" · "+r.status()+(note==null?"":" · "+note));
  return view(lc,detail,LocalDate.now());
 }

 private LetterOfCredit guarantee(UUID id){
  var lc=lcs.findById(id).orElseThrow(()->new NoSuchElementException("Garantie nicht gefunden."));
  if(!"GUARANTEE".equals(lc.getInstrumentType()))throw new NoSuchElementException("Garantie nicht gefunden.");
  return lc;
 }
 /** The file status follows the guarantee: closed or expired guarantees stop the deadline reminders. */
 private static void syncStatus(LetterOfCredit lc,String status){
  lc.setStatus(switch(status){case "RETURNED","CLAIMED","CANCELLED"->LetterOfCreditStatus.CLOSED;case "EXPIRED"->LetterOfCreditStatus.EXPIRED;case "ACTIVE"->LetterOfCreditStatus.ACTIVE;default->LetterOfCreditStatus.RECEIVED;});
 }
 private static void apply(LetterOfCredit lc,Request c){
  lc.setReference(c.reference());lc.setBeneficiary(c.beneficiary());lc.setIssuingBank(c.bank());lc.setCurrency(c.currency());lc.setAmount(c.amount());lc.setIssueDate(c.issueDate());lc.setExpiryDate(c.expiryDate());
 }
 static View view(LetterOfCredit lc,GuaranteeDetail d,LocalDate today){
  String status=d==null?("EXPIRED".equals(String.valueOf(lc.getStatus()))?"EXPIRED":"CLOSED".equals(String.valueOf(lc.getStatus()))?"RETURNED":"ACTIVE"):d.getStatus();
  Long days=lc.getExpiryDate()==null?null:ChronoUnit.DAYS.between(today,lc.getExpiryDate());
  boolean open=open(status);
  return new View(lc.getId(),lc.getReference(),d==null?"OTHER":d.getGuaranteeType(),lc.getIssuingBank(),lc.getBeneficiary(),lc.getCurrency(),lc.getAmount(),lc.getIssueDate(),d==null?null:d.getValidFrom(),lc.getExpiryDate(),d==null?null:d.getClaimUntil(),
   d!=null&&d.isAutoExtension(),d==null?null:d.getExtensionNoticeDays(),d==null?null:d.decisionDate(lc.getExpiryDate()),d==null?null:d.getUnderlying(),d==null?null:d.getBeneficiaryReference(),status,d==null?null:d.getStatusDate(),d==null?null:d.getStatusNote(),
   days,open&&days!=null&&days>=0&&days<=SOON_DAYS,open,d!=null,d==null?null:d.getBankReference());
 }
 static Request validate(Request r,boolean creating){
  if(r==null)throw new IllegalArgumentException("Bitte die Angaben zur Garantie ausfüllen.");
  String reference=clip(r.reference(),100);if(reference==null)throw new IllegalArgumentException("Bitte eine Referenz angeben.");
  String type=r.guaranteeType()==null||r.guaranteeType().isBlank()?"OTHER":r.guaranteeType().trim().toUpperCase(Locale.ROOT);
  if(!TYPES.contains(type))throw new IllegalArgumentException("Unbekannte Garantieart.");
  String beneficiary=clip(r.beneficiary(),500);if(beneficiary==null)throw new IllegalArgumentException("Bitte den Begünstigten angeben.");
  String bank=clip(r.bank(),500);if(bank==null)throw new IllegalArgumentException("Bitte die ausstellende Bank angeben.");
  String currency=r.currency()==null?"":r.currency().trim().toUpperCase(Locale.ROOT);if(!currency.matches("[A-Z]{3}"))throw new IllegalArgumentException("Bitte eine Währung mit drei Buchstaben angeben.");
  if(r.amount()==null||r.amount().signum()<=0)throw new IllegalArgumentException("Bitte einen Betrag größer als 0 angeben.");
  BigDecimal amount=r.amount().setScale(2,RoundingMode.HALF_UP);
  if(r.expiryDate()==null&&!Boolean.TRUE.equals(r.autoExtension()))throw new IllegalArgumentException("Bitte das Ablaufdatum angeben (oder die automatische Verlängerung ohne festes Ende wählen).");
  if(r.validFrom()!=null&&r.expiryDate()!=null&&r.expiryDate().isBefore(r.validFrom()))throw new IllegalArgumentException("Das Ablaufdatum liegt vor dem Beginn der Gültigkeit.");
  if(r.claimUntil()!=null&&r.expiryDate()!=null&&r.claimUntil().isBefore(r.expiryDate()))throw new IllegalArgumentException("Die Frist für die Inanspruchnahme darf nicht vor dem Ablauf liegen.");
  if(r.extensionNoticeDays()!=null&&(r.extensionNoticeDays()<0||r.extensionNoticeDays()>730))throw new IllegalArgumentException("Die Frist für die Entscheidung muss zwischen 0 und 730 Tagen liegen.");
  String status=r.status()==null||r.status().isBlank()?"ACTIVE":r.status().trim().toUpperCase(Locale.ROOT);
  if(!STATUSES.contains(status))throw new IllegalArgumentException("Unbekannter Status der Garantie.");
  return new Request(reference,type,bank,beneficiary,currency,amount,r.issueDate(),r.validFrom(),r.expiryDate(),r.claimUntil(),r.autoExtension(),r.extensionNoticeDays(),clip(r.underlying(),500),clip(r.beneficiaryReference(),100),status);
 }
 private static String clip(String v,int max){if(v==null||v.isBlank())return null;String t=v.trim();return t.length()<=max?t:t.substring(0,max);}
 private static String user(Authentication a){return a==null?null:a.getName();}
}
