package de.ostms.lc.document.service;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.audit.service.AuditSnapshots;
import de.ostms.lc.check.service.DocumentCheckService;
import de.ostms.lc.document.api.DocumentView;
import de.ostms.lc.document.domain.DocumentCopy;
import de.ostms.lc.document.domain.LcDocument;
import de.ostms.lc.document.repository.LcDocumentRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs the current recognition (OCR, date, stamp, number, type rules) again on a stored document and shows what would change.
 * Machine-derived fields are replaced on confirmation; human-maintained fields (date, mark, amount, currency) are only filled
 * when empty, the document type is only suggested. Nothing changes before the user confirms.
 */
@Service
public class DocumentReRecognitionService {
 static final int MAX_PAGES=10;
 private static final long PENDING_MINUTES=15;
 public record Change(String key,String label,String before,String after,boolean appliesOnConfirm,String note){}
 public record Preview(UUID documentId,String filename,String token,List<Change> changes,boolean anyApplied){}
 private record Pending(UUID documentId,UUID lcId,String username,Instant createdAt,LcDocument probe){}
 private final LcDocumentRepository documents;private final DocumentExtractionService extraction;private final PdfPagePreviewService pages;
 private final DocumentCheckService checks;private final AuditService audit;private final TransactionTemplate transaction;
 private final Map<String,Pending> pending=new ConcurrentHashMap<>();private final SecureRandom random=new SecureRandom();
 public DocumentReRecognitionService(LcDocumentRepository documents,DocumentExtractionService extraction,PdfPagePreviewService pages,DocumentCheckService checks,AuditService audit,PlatformTransactionManager manager){
  this.documents=documents;this.extraction=extraction;this.pages=pages;this.checks=checks;this.audit=audit;this.transaction=new TransactionTemplate(manager);
 }

 /** Loads the document inside a short transaction, then recognises outside any transaction (OCR can take a while). */
 public Preview preview(UUID lcId,UUID documentId,Authentication auth){
  record Loaded(LcDocument before,LcDocument probe){}
  var loaded=transaction.execute(status->{
   var doc=documents.findById(documentId).orElseThrow(()->new NoSuchElementException("Dokument nicht gefunden."));
   requireInLc(doc,lcId);
   var before=copyOf(doc);var probe=new LcDocument();
   probe.setOriginalFilename(doc.getOriginalFilename());probe.setContentType(doc.getContentType());probe.setContent(doc.getContent());probe.setFileSize(doc.getFileSize());
   return new Loaded(before,probe);
  });
  try{
   if("application/pdf".equalsIgnoreCase(loaded.before().getContentType())&&pages.pageCount(loaded.before().getContent())>MAX_PAGES)
    throw new IllegalArgumentException("Das Dokument hat mehr als "+MAX_PAGES+" Seiten. Bitte über den Posteingang neu erkennen.");
  }catch(IllegalArgumentException e){throw e;}catch(Exception e){throw new IllegalStateException("PDF konnte nicht gelesen werden.",e);}
  extraction.extract(loaded.probe());
  var changes=diff(loaded.before(),loaded.probe());
  purgeExpired();
  String token=newToken();
  pending.put(token,new Pending(documentId,lcId,auth.getName(),Instant.now(),loaded.probe()));
  return new Preview(documentId,loaded.before().getOriginalFilename(),token,changes,changes.stream().anyMatch(Change::appliesOnConfirm));
 }

 @Transactional
 public DocumentView apply(UUID lcId,UUID documentId,String token,Authentication auth){
  var entry=token==null?null:pending.remove(token);
  if(entry==null||!entry.documentId().equals(documentId)||!entry.lcId().equals(lcId)||!entry.username().equals(auth.getName())||entry.createdAt().isBefore(Instant.now().minusSeconds(PENDING_MINUTES*60)))
   throw new IllegalArgumentException("Die Vorschau ist abgelaufen. Bitte erneut neu erkennen.");
  var doc=documents.findById(documentId).orElseThrow(()->new NoSuchElementException("Dokument nicht gefunden."));
  requireInLc(doc,lcId);
  String before=AuditSnapshots.document(doc);
  var probe=entry.probe();
  doc.setExtractionStatus(probe.getExtractionStatus());doc.setExtractedText(probe.getExtractedText());doc.setOcrEvidenceJson(probe.getOcrEvidenceJson());
  doc.setExtractedReference(probe.getExtractedReference());doc.setExtractedDocumentNumber(probe.getExtractedDocumentNumber());
  doc.setExtractedAmount(probe.getExtractedAmount());doc.setExtractedCurrency(probe.getExtractedCurrency());
  if(doc.getDocumentDate()==null)doc.setDocumentDate(probe.getDocumentDate());
  var hint=DocumentCopyDetector.detect(probe.getExtractedText());
  if(doc.getCopyNumber()==null&&hint.copyNumber()!=null)doc.setCopyNumber(hint.copyNumber());
  if(doc.getAmount()==null)doc.setAmount(probe.getExtractedAmount());
  if(doc.getCurrency()==null)doc.setCurrency(probe.getExtractedCurrency());
  documents.save(doc);
  long reset=checks.invalidateDecisions(lcId);
  audit.recordChangeInTransaction(auth,"DOCUMENT_RE_RECOGNIZED","LETTER_OF_CREDIT",lcId,doc.getOriginalFilename()+" · erneut erkannt"+(reset>0?" · "+reset+" Prüfentscheidungen zurückgesetzt":""),before,AuditSnapshots.document(doc));
  return DocumentView.from(doc);
 }

 private static void requireInLc(LcDocument doc,UUID lcId){
  if(doc.getLetterOfCredit()==null||!lcId.equals(doc.getLetterOfCredit().getId()))throw new IllegalArgumentException("Dokument gehört nicht zu diesem Akkreditiv.");
 }
 private static LcDocument copyOf(LcDocument d){
  var c=new LcDocument();c.setOriginalFilename(d.getOriginalFilename());c.setContentType(d.getContentType());c.setContent(d.getContent());c.setFileSize(d.getFileSize());
  c.setExtractionStatus(d.getExtractionStatus());c.setExtractedText(d.getExtractedText());c.setExtractedReference(d.getExtractedReference());c.setExtractedDocumentNumber(d.getExtractedDocumentNumber());
  c.setExtractedAmount(d.getExtractedAmount());c.setExtractedCurrency(d.getExtractedCurrency());c.setDocumentDate(d.getDocumentDate());c.setDocumentType(d.getDocumentType());c.setCopyNumber(d.getCopyNumber());c.setAmount(d.getAmount());c.setCurrency(d.getCurrency());
  return c;
 }

 static List<Change> diff(LcDocument before,LcDocument probe){
  var changes=new ArrayList<Change>();
  machine(changes,"status","Erkennungsstatus",before.getExtractionStatus(),probe.getExtractionStatus());
  int oldLength=before.getExtractedText()==null?0:before.getExtractedText().length(),newLength=probe.getExtractedText()==null?0:probe.getExtractedText().length();
  if(!Objects.equals(before.getExtractedText(),probe.getExtractedText()))changes.add(new Change("text","Erkannter Text",oldLength+" Zeichen",newLength+" Zeichen",true,null));
  machine(changes,"documentNumber","Dokumentnummer",before.getExtractedDocumentNumber(),probe.getExtractedDocumentNumber());
  machine(changes,"reference","Referenz im Dokument",before.getExtractedReference(),probe.getExtractedReference());
  machine(changes,"extractedAmount","Erkannter Betrag",text(before.getExtractedAmount()),text(probe.getExtractedAmount()));
  machine(changes,"extractedCurrency","Erkannte Währung",before.getExtractedCurrency(),probe.getExtractedCurrency());
  human(changes,"documentDate","Dokumentdatum",text(before.getDocumentDate()),text(probe.getDocumentDate()));
  var hint=DocumentCopyDetector.detect(probe.getExtractedText());
  human(changes,"copy","Kennzeichnung",before.getCopyNumber()==null?null:DocumentCopy.label(before.getCopyNumber()),hint.copyNumber()==null?null:DocumentCopy.label(hint.copyNumber()));
  human(changes,"amount","Betrag",text(before.getAmount()),text(probe.getExtractedAmount()));
  human(changes,"currency","Währung",before.getCurrency(),probe.getExtractedCurrency());
  var suggestion=DocumentClassifier.classify(before.getOriginalFilename(),probe.getExtractedText());
  if(suggestion.suggestedType()!=null&&suggestion.suggestedType()!=before.getDocumentType()&&suggestion.score()>=.8)
   changes.add(new Change("type","Dokumenttyp",before.getDocumentType()==null?null:before.getDocumentType().getDisplayName(),suggestion.suggestedType().getDisplayName(),false,"Nur ein Vorschlag, der Typ bleibt unverändert."));
  return List.copyOf(changes);
 }
 private static void machine(List<Change> out,String key,String label,String before,String after){if(!Objects.equals(blank(before),blank(after)))out.add(new Change(key,label,before,after,true,null));}
 /** Human-maintained value: filled only when empty; a differing recognised value is shown but not applied. */
 private static void human(List<Change> out,String key,String label,String before,String after){
  if(blank(after)==null||Objects.equals(blank(before),blank(after)))return;
  if(blank(before)==null)out.add(new Change(key,label,null,after,true,"wird ergänzt"));
  else out.add(new Change(key,label,before,after,false,"manuell gepflegt, bleibt unverändert"));
 }
 private static String blank(String v){return v==null||v.isBlank()?null:v;}
 private static String text(Object v){return v==null?null:v instanceof BigDecimal b?b.stripTrailingZeros().toPlainString():v instanceof LocalDate d?d.toString():v.toString();}
 private String newToken(){byte[] bytes=new byte[18];random.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
 private void purgeExpired(){var cutoff=Instant.now().minusSeconds(PENDING_MINUTES*60);pending.values().removeIf(p->p.createdAt().isBefore(cutoff));while(pending.size()>100){pending.keySet().stream().findFirst().ifPresent(pending::remove);}}
}
