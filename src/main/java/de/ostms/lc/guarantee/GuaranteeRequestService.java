package de.ostms.lc.guarantee;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.document.service.DocxTemplateService;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * The request for a guarantee to the bank, the simple way: LCM makes the application as a Word letter (company letterhead, the facts of the guarantee, the wanted wording) and the
 * company sends it itself; LCM follows the status (draft, sent, query, approved, issued, refused, withdrawn) and the date by which the bank should answer. When the bank has issued
 * the guarantee the file becomes active, a refused or withdrawn request closes it as not come about.
 */
@Service
public class GuaranteeRequestService {
 public static final List<String> STATUSES=List.of("DRAFT","SENT","QUERY","APPROVED","ISSUED","REJECTED","WITHDRAWN");
 /** From which status to which; a request can always be withdrawn until it is issued. */
 static final Map<String,Set<String>> NEXT=Map.of(
  "DRAFT",Set.of("SENT","WITHDRAWN"),"SENT",Set.of("QUERY","APPROVED","ISSUED","REJECTED","WITHDRAWN"),"QUERY",Set.of("SENT","APPROVED","ISSUED","REJECTED","WITHDRAWN"),
  "APPROVED",Set.of("ISSUED","REJECTED","WITHDRAWN"),"ISSUED",Set.of(),"REJECTED",Set.of(),"WITHDRAWN",Set.of());
 public record Request(String recipient,String ourReference,String wording,String remarks,LocalDate responseDue){}
 public record StatusRequest(String status,LocalDate date,String note,String bankReference){}
 public record View(UUID lcId,String status,String recipient,String ourReference,String wording,String remarks,LocalDate sentOn,String bankReference,LocalDate responseDue,LocalDate statusDate,String statusNote,boolean exists,boolean open,List<String> nextStatuses){}
 private static final DateTimeFormatter DE=DateTimeFormatter.ofPattern("dd.MM.yyyy"),EN=DateTimeFormatter.ofPattern("dd MMM yyyy",Locale.ENGLISH);
 private final GuaranteeRequestRepository requests;private final LetterOfCreditRepository lcs;private final GuaranteeService guarantees;private final DocxTemplateService templates;private final AuditService audit;
 public GuaranteeRequestService(GuaranteeRequestRepository requests,LetterOfCreditRepository lcs,GuaranteeService guarantees,DocxTemplateService templates,AuditService audit){this.requests=requests;this.lcs=lcs;this.guarantees=guarantees;this.templates=templates;this.audit=audit;}

 static boolean open(String status){return "DRAFT".equals(status)||"SENT".equals(status)||"QUERY".equals(status)||"APPROVED".equals(status);}
 static View view(UUID lcId,GuaranteeRequest r){
  if(r==null)return new View(lcId,"DRAFT",null,null,null,null,null,null,null,null,null,false,true,List.of("SENT","WITHDRAWN"));
  return new View(lcId,r.getStatus(),r.getRecipient(),r.getOurReference(),r.getWording(),r.getRemarks(),r.getSentOn(),r.getBankReference(),r.getResponseDue(),r.getStatusDate(),r.getStatusNote(),true,open(r.getStatus()),NEXT.get(r.getStatus()).stream().sorted().toList());
 }
 private LetterOfCredit guarantee(UUID lcId){
  var lc=lcs.findById(lcId).orElseThrow(()->new NoSuchElementException("Garantie nicht gefunden."));
  if(!"GUARANTEE".equals(lc.getInstrumentType()))throw new NoSuchElementException("Garantie nicht gefunden.");
  return lc;
 }
 @Transactional(readOnly=true) public View get(UUID lcId){guarantee(lcId);return view(lcId,requests.byLc(lcId).orElse(null));}

 /** Saves the text of the application; the status stays as it is (a new request starts as draft). */
 @Transactional public View save(UUID lcId,Request in,Authentication auth){
  var lc=guarantee(lcId);if(in==null)throw new IllegalArgumentException("Bitte die Angaben zum Antrag ausfüllen.");
  var r=requests.byLc(lcId).orElseGet(()->new GuaranteeRequest(lcId));
  if(!open(r.getStatus()))throw new IllegalStateException("Der Antrag ist abgeschlossen und lässt sich nicht mehr ändern.");
  r.setText(clip(in.recipient(),500),clip(in.ourReference(),100),clip(in.wording(),8000),clip(in.remarks(),1000),in.responseDue(),user(auth));
  requests.save(r);
  audit.recordInTransaction(auth,"GUARANTEE_REQUEST_SAVED","LETTER_OF_CREDIT",lcId,lc.getReference()+" · "+r.getStatus());
  return view(lcId,r);
 }

 @Transactional public View setStatus(UUID lcId,StatusRequest in,Authentication auth){
  var lc=guarantee(lcId);
  if(in==null||in.status()==null||!STATUSES.contains(in.status()))throw new IllegalArgumentException("Unbekannter Status des Antrags.");
  var r=requests.byLc(lcId).orElseGet(()->new GuaranteeRequest(lcId));
  if(!NEXT.get(r.getStatus()).contains(in.status()))throw new IllegalArgumentException("Dieser Statuswechsel ist nicht möglich.");
  LocalDate date=in.date()==null?LocalDate.now():in.date();
  if(date.isAfter(LocalDate.now()))throw new IllegalArgumentException("Das Datum darf nicht in der Zukunft liegen.");
  r.setStatus(in.status(),date,clip(in.note(),500),clip(in.bankReference(),100),user(auth));requests.save(r);
  // the file follows the request: issued -> the guarantee is active, refused or withdrawn -> it did not come about
  switch(in.status()){
   case "ISSUED"->guarantees.setStatus(lcId,new GuaranteeService.StatusRequest("ACTIVE",date,clip(in.note(),500)),auth);
   case "REJECTED","WITHDRAWN"->guarantees.setStatus(lcId,new GuaranteeService.StatusRequest("CANCELLED",date,("REJECTED".equals(in.status())?"Antrag von der Bank abgelehnt":"Antrag zurückgezogen")+(clip(in.note(),400)==null?"":": "+clip(in.note(),400))),auth);
   default->{}
  }
  audit.recordInTransaction(auth,"GUARANTEE_REQUEST_STATUS_CHANGED","LETTER_OF_CREDIT",lcId,lc.getReference()+" · "+in.status()+(r.getBankReference()==null?"":" · "+r.getBankReference()));
  return view(lcId,r);
 }

 /** The application as a Word letter; language "de" (default) or "en". */
 @Transactional(readOnly=true) public byte[] document(UUID lcId,String language,LocalDate today)throws IOException{
  var lc=guarantee(lcId);var r=requests.byLc(lcId).orElse(null);var d=guarantees.one(lcId,today);
  boolean en="en".equalsIgnoreCase(language);var company=templates.companyData(lc);
  return word(company,d,r,en,today);
 }
 static byte[] word(Map<String,Object> company,GuaranteeService.View g,GuaranteeRequest r,boolean en,LocalDate today)throws IOException{
  var fmt=en?EN:DE;
  try(var doc=new XWPFDocument();var out=new ByteArrayOutputStream()){
   para(doc,text(company,"companyName"),true,13,ParagraphAlignment.LEFT);
   line(doc,text(company,"companyAddressLine"),ParagraphAlignment.LEFT);line(doc,text(company,"companyContactLine"),ParagraphAlignment.LEFT);
   para(doc,"",false,10,ParagraphAlignment.LEFT);
   if(r!=null&&r.getRecipient()!=null)for(String l:r.getRecipient().split("\\R"))line(doc,l,ParagraphAlignment.LEFT);else line(doc,g.bank()==null?"":g.bank().split("\\R")[0],ParagraphAlignment.LEFT);
   line(doc,joined(", ",text(company,"companyCityOnly"),today.format(fmt)),ParagraphAlignment.RIGHT);
   para(doc,"",false,10,ParagraphAlignment.LEFT);
   if(r!=null&&r.getOurReference()!=null)line(doc,(en?"Our reference: ":"Unser Zeichen: ")+r.getOurReference(),ParagraphAlignment.LEFT);
   para(doc,(en?"Application for the issue of a guarantee":"Antrag auf Ausstellung einer Garantie")+" – "+typeName(g.guaranteeType(),en),true,13,ParagraphAlignment.LEFT);
   line(doc,en?"Dear Sir or Madam,":"Sehr geehrte Damen und Herren,",ParagraphAlignment.LEFT);
   line(doc,en?"we kindly ask you to issue the following guarantee in favour of the beneficiary named below:":"wir bitten Sie, zugunsten des unten genannten Begünstigten die folgende Garantie auszustellen:",ParagraphAlignment.LEFT);
   var rows=new ArrayList<String[]>();
   rows.add(new String[]{en?"Type of guarantee":"Art der Garantie",typeName(g.guaranteeType(),en)});
   rows.add(new String[]{en?"Beneficiary":"Begünstigter",oneLines(g.beneficiary())});
   rows.add(new String[]{en?"Amount":"Betrag",g.currency()+" "+(g.amount()==null?"-":g.amount().setScale(2,java.math.RoundingMode.HALF_UP).toPlainString().replace('.',en?'.':','))});
   if(g.validFrom()!=null)rows.add(new String[]{en?"Valid from":"Gültig ab",g.validFrom().format(fmt)});
   rows.add(new String[]{en?"Valid until":"Gültig bis",g.expiryDate()==null?(en?"open-ended":"unbefristet"):g.expiryDate().format(fmt)});
   if(g.claimUntil()!=null)rows.add(new String[]{en?"Claims possible until":"Inanspruchnahme möglich bis",g.claimUntil().format(fmt)});
   if(g.autoExtension())rows.add(new String[]{en?"Extension":"Verlängerung",en?"extends automatically unless terminated":"verlängert sich automatisch, sofern nicht gekündigt"});
   if(g.underlying()!=null)rows.add(new String[]{en?"Underlying transaction":"Grundgeschäft",g.underlying()});
   if(g.beneficiaryReference()!=null)rows.add(new String[]{en?"Reference of the beneficiary":"Aktenzeichen des Begünstigten",g.beneficiaryReference()});
   rows.add(new String[]{en?"Our file":"Unsere Akte",g.reference()});
   var table=doc.createTable(rows.size(),2);
   for(int i=0;i<rows.size();i++){cell(table.getRow(i).getCell(0),rows.get(i)[0],true);cell(table.getRow(i).getCell(1),rows.get(i)[1],false);}
   if(r!=null&&r.getWording()!=null){para(doc,"",false,10,ParagraphAlignment.LEFT);para(doc,en?"Requested wording":"Gewünschter Wortlaut",true,11,ParagraphAlignment.LEFT);for(String l:r.getWording().split("\\R"))line(doc,l,ParagraphAlignment.LEFT);}
   if(r!=null&&r.getRemarks()!=null){para(doc,"",false,10,ParagraphAlignment.LEFT);for(String l:r.getRemarks().split("\\R"))line(doc,l,ParagraphAlignment.LEFT);}
   if(r!=null&&r.getResponseDue()!=null){para(doc,"",false,10,ParagraphAlignment.LEFT);line(doc,(en?"We would appreciate your answer by ":"Wir bitten um Ihre Rückmeldung bis zum ")+r.getResponseDue().format(fmt)+".",ParagraphAlignment.LEFT);}
   para(doc,"",false,10,ParagraphAlignment.LEFT);
   line(doc,en?"The charges are for our account. Please confirm receipt of this application.":"Die Kosten gehen zu unseren Lasten. Bitte bestätigen Sie den Eingang dieses Antrags.",ParagraphAlignment.LEFT);
   line(doc,en?"Kind regards":"Mit freundlichen Grüßen",ParagraphAlignment.LEFT);
   para(doc,"",false,10,ParagraphAlignment.LEFT);para(doc,"",false,10,ParagraphAlignment.LEFT);
   line(doc,"______________________________          ______________________________",ParagraphAlignment.LEFT);
   line(doc,text(company,"companyName"),ParagraphAlignment.LEFT);
   para(doc,"",false,8,ParagraphAlignment.LEFT);
   var legal=joined("  ·  ",text(company,"companyLegalLine"),text(company,"companyBankLine"));if(!legal.isEmpty())para(doc,legal,false,8,ParagraphAlignment.LEFT);
   doc.write(out);return out.toByteArray();
  }
 }
 private static String text(Map<String,Object> m,String key){Object v=m.get(key);return v==null||"-".equals(v)?"":String.valueOf(v);}
 private static String oneLines(String v){return v==null?"-":String.join(", ",Arrays.stream(v.split("\\R")).map(String::strip).filter(s->!s.isEmpty()).toList());}
 static String joined(String sep,String... parts){return String.join(sep,Arrays.stream(parts).filter(p->p!=null&&!p.isBlank()).toList());}
 static String typeName(String type,boolean en){
  return switch(type==null?"OTHER":type){
   case "ADVANCE_PAYMENT"->en?"advance payment guarantee":"Anzahlungsgarantie";case "PERFORMANCE"->en?"performance guarantee":"Vertragserfüllungsgarantie";case "WARRANTY"->en?"warranty guarantee":"Gewährleistungsgarantie";
   case "BID"->en?"bid guarantee":"Bietungsgarantie";case "PAYMENT"->en?"payment guarantee":"Zahlungsgarantie";case "CUSTOMS"->en?"customs guarantee":"Zollbürgschaft";case "RENT"->en?"rent guarantee":"Mietbürgschaft";default->en?"guarantee":"Garantie";
  };
 }
 private static void para(XWPFDocument doc,String text,boolean bold,int size,ParagraphAlignment align){var p=doc.createParagraph();p.setAlignment(align);p.setSpacingAfter(60);var run=p.createRun();run.setText(text);run.setBold(bold);run.setFontSize(size);run.setFontFamily("Calibri");}
 private static void line(XWPFDocument doc,String text,ParagraphAlignment align){para(doc,text,false,10,align);}
 private static void cell(XWPFTableCell cell,String text,boolean bold){var p=cell.getParagraphs().get(0);var run=p.createRun();run.setText(text);run.setBold(bold);run.setFontSize(10);run.setFontFamily("Calibri");}
 private static String clip(String v,int max){if(v==null||v.isBlank())return null;String t=v.trim();return t.length()<=max?t:t.substring(0,max);}
 private static String user(Authentication a){return a==null?null:a.getName();}
}
