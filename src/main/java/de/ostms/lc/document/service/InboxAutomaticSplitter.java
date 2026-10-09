package de.ostms.lc.document.service;

import de.ostms.lc.document.domain.*;
import de.ostms.lc.document.repository.DocumentInboxRepository;
import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.tenant.domain.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.util.*;

/** Conservative splitting: original retained; automatic classification is never human confirmation. */
@Service
public class InboxAutomaticSplitter {
 public record Plan(List<PdfDocumentSplitter.Output> outputs) { public static Plan none(){return new Plan(List.of());} }
 private final DocumentInboxRepository inbox;private final DocumentExtractionService extraction;private final AuditService audit;
 public InboxAutomaticSplitter(DocumentInboxRepository inbox,DocumentExtractionService extraction,AuditService audit){this.inbox=inbox;this.extraction=extraction;this.audit=audit;}
 public Plan prepare(LcDocument document)throws Exception{
  TenantContext.require(document.getTenantId());
  if(!"application/pdf".equals(document.getContentType())||!List.of("EXTRACTED","OCR_EXTRACTED").contains(document.getExtractionStatus()))return Plan.none();
  var proposal=PdfDocumentSplitter.propose(document.getContent(),document.getOcrEvidenceJson());
  if(!eligible(proposal))return Plan.none();
  if("OCR_EXTRACTED".equals(document.getExtractionStatus())){
   var evidence=DocumentExtractionService.readEvidence(document.getOcrEvidenceJson());if(evidence==null)return Plan.none();
   for(var page:proposal.pages()){var words=evidence.words().stream().filter(w->w.page()==page.number()).toList();if(words.isEmpty()||words.stream().anyMatch(w->w.confidence()==null||!Double.isFinite(w.confidence())||w.confidence()<.8))return Plan.none();}
  }
  return new Plan(PdfDocumentSplitter.split(document.getContent(),document.getOcrEvidenceJson(),proposal.parts()));
 }
 static boolean eligible(PdfDocumentSplitter.Proposal proposal){return proposal.parts().size()>=2&&proposal.parts().size()<=100&&proposal.parts().stream().map(PdfDocumentSplitter.Part::documentType).distinct().count()>=2&&proposal.pages().stream().allMatch(InboxAutomaticSplitter::reliablePage);}
 static boolean reliablePage(PdfDocumentSplitter.Page p){var c=p.classification();if(c.suggestedType()==null)return false;if(c.score()>=.9&&"SUGGESTED".equals(c.status()))return true;return c.score()>=.8&&("PAGE_SEQUENCE_V1".equals(c.method())||"DOCUMENT_REFERENCE_V1".equals(c.method()));}
 @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
 public List<DocumentInboxItem> persist(DocumentInboxItem original,Plan plan)throws Exception{
  return persist(original,plan,original.getReceivedBy());
 }
 @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
 public List<DocumentInboxItem> persist(DocumentInboxItem original,Plan plan,String requestedBy)throws Exception{
  TenantContext.require(original.getTenantId());
  if(!"OPEN".equals(original.getStatus())||original.getSourceInboxId()!=null||ClassificationHistory.selectedType(original.getClassificationHistoryJson())!=null||plan.outputs().isEmpty())return List.of();
  var result=new ArrayList<DocumentInboxItem>();var json=new com.fasterxml.jackson.databind.ObjectMapper();
  for(var output:plan.outputs()){
   var item=new DocumentInboxItem();var part=output.part();String base=original.getOriginalFilename().replaceFirst("(?i)\\.pdf$","");if(base.length()>180)base=base.substring(0,180);
   item.setOriginalFilename(base+"-pages-"+part.fromPage()+"-"+part.toPage()+".pdf");item.setContentType("application/pdf");item.setContent(output.content());item.setFileSize(output.content().length);item.setReceivedBy(original.getReceivedBy());
   item.setSourceInboxId(original.getId());item.setSourceFromPage(part.fromPage());item.setSourceToPage(part.toPage());
   var document=new LcDocument();document.setOriginalFilename(item.getOriginalFilename());extraction.applyRecognizedText(document,output.text(),output.evidence()==null?"EXTRACTED":"OCR_EXTRACTED");
   item.setExtractionStatus(document.getExtractionStatus());item.setExtractedText(document.getExtractedText());item.setExtractedReference(document.getExtractedReference());item.setExtractedDocumentNumber(document.getExtractedDocumentNumber());item.setExtractedAmount(document.getExtractedAmount());item.setExtractedCurrency(document.getExtractedCurrency());
   if(output.evidence()!=null)item.setOcrEvidenceJson(json.writeValueAsString(output.evidence()));
   item.setClassificationHistoryJson(ClassificationHistory.automaticSplit(item.getOriginalFilename(),output.text()));result.add(inbox.saveAndFlush(item));
  }
  original.setStatus("SPLIT");inbox.saveAndFlush(original);
  var actor=UsernamePasswordAuthenticationToken.unauthenticated(requestedBy,null);
  audit.recordInTransaction(actor,"DOCUMENT_INBOX_AUTO_SPLIT","DOCUMENT_INBOX",original.getId(),"Automatic split; original retained; parts="+result.size());
  for(var item:result)audit.recordInTransaction(actor,"DOCUMENT_INBOX_AUTO_SPLIT_PART","DOCUMENT_INBOX",item.getId(),"Source="+original.getId()+"; pages="+item.getSourceFromPage()+"-"+item.getSourceToPage()+"; automatic classification requires review");
  return List.copyOf(result);
 }
}
