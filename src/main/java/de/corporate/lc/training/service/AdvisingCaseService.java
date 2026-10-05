package de.corporate.lc.training.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.corporate.lc.audit.service.AuditService;
import de.corporate.lc.document.domain.*;
import de.corporate.lc.document.repository.LcDocumentRepository;
import de.corporate.lc.document.service.AdvisingLetterExtractor;
import de.corporate.lc.lc.domain.LetterOfCredit;
import de.corporate.lc.lc.repository.LetterOfCreditRepository;
import de.corporate.lc.training.api.AdvisingNewCaseRequest;
import de.corporate.lc.training.domain.TrainingSession;
import de.corporate.lc.training.repository.TrainingSessionRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class AdvisingCaseService {
 private final TrainingSessionRepository sessions;
 private final LetterOfCreditRepository lcs;
 private final LcDocumentRepository documents;
 private final ObjectMapper mapper;
 private final AuditService audit;
 public AdvisingCaseService(TrainingSessionRepository sessions,LetterOfCreditRepository lcs,LcDocumentRepository documents,ObjectMapper mapper,AuditService audit){this.sessions=sessions;this.lcs=lcs;this.documents=documents;this.mapper=mapper;this.audit=audit;}

 @Transactional(readOnly=true)
 public AdvisingLetterExtractor.Proposal preview(UUID id,String username){return proposal(eligible(sessions.findById(id).orElseThrow(),username));}

 private TrainingSession eligible(TrainingSession session,String username){
  if(!"ADVISING_LETTER".equals(session.getMessageType())||!username.equals(session.getUsername())||!Set.of("DRAFT","CONFIRMED").contains(session.getStatus()))throw new IllegalArgumentException("Dieses Avisierungstraining kann nicht übernommen werden.");
  if(session.getLcId()!=null)throw new IllegalArgumentException("Dieses Training wurde bereits in eine LC-Akte übernommen.");
  if(session.getOriginalPdf()==null||session.getOriginalPdf().length==0)throw new IllegalArgumentException("Das Original-PDF fehlt.");
  return session;
 }

 private AdvisingLetterExtractor.Proposal proposal(TrainingSession session){
  try{
   var fields=mapper.readValue(session.getReviewsJson(),AdvisingTrainingService.Field[].class);
   if(fields.length==0)throw new IllegalArgumentException("Keine bestätigten Angaben vorhanden.");
   StringBuilder text=new StringBuilder();
   for(var field:fields){
    if(field.review()==null)throw new IllegalArgumentException("Bitte alle Angaben bestätigen oder als nicht verwendbar markieren.");
    if("invalid".equals(field.review()))continue;
    if(!Set.of("correct","corrected","reassigned").contains(field.review())||!AdvisingTrainingService.LABELS.containsKey(field.code())||field.value()==null||field.value().isBlank())throw new IllegalArgumentException("Ungültige bestätigte Angabe.");
    // Never re-extract discarded or uncorrected original text. Preserve duplicate targets as ambiguity.
    text.append(AdvisingTrainingService.LABELS.get(field.code())).append(": ").append(field.value().replaceAll("[\\r\\n]+"," ")).append('\n');
   }
   return AdvisingLetterExtractor.extract(text.toString());
  }catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalArgumentException("Trainingsdaten können nicht gelesen werden.",e);}
 }

 public record Result(UUID lcId,UUID documentId){}
 @Transactional
 public Result create(UUID id,AdvisingNewCaseRequest request,Authentication auth){
  var session=eligible(sessions.findForUpdate(id).orElseThrow(),auth.getName());
  proposal(session); // Validate persisted reviews; submitted values alone must never bypass training review.
  String reference=request.reference().trim();
  if(lcs.existsByReference(reference))throw new IllegalArgumentException("Eine LC-Akte mit dieser Referenz besteht bereits. Bitte eine andere Aktenreferenz verwenden oder die bestehende Akte öffnen.");
  var lc=new LetterOfCredit();lc.setReference(reference);lc.setOwnBankReference(clean(request.ownBankReference()));lc.setForeignBankReference(clean(request.foreignBankReference()));
  lc.setApplicant(request.applicant().trim());lc.setBeneficiary(request.beneficiary().trim());lc.setAmount(request.amount());lc.setCurrency(request.currency());lc.setExpiryDate(request.expiryDate());lc.setIssuingBank(clean(request.issuingBank()));lc.setExpiryPlace(clean(request.expiryPlace()));
  lc=lcs.saveAndFlush(lc);
  var document=new LcDocument();document.setLetterOfCredit(lc);document.setDocumentType(DocumentType.ADVISING_LETTER);document.setOriginalFilename(session.getFilename());document.setContentType("application/pdf");document.setContent(session.getOriginalPdf());document.setFileSize(session.getOriginalPdf().length);document.setExtractedText(session.getExtractedText());document.setExtractionStatus(session.getExtractionStatus());
  document=documents.saveAndFlush(document);
  session.setLcId(lc.getId());session.setStatus("CONFIRMED");if(session.getConfirmedAt()==null)session.setConfirmedAt(LocalDateTime.now());sessions.save(session);
  audit.recordInTransaction(auth,"LC_CREATED","LETTER_OF_CREDIT",lc.getId(),"Aus bestätigtem Avisierungstraining · "+id+" · "+reference);
  audit.recordInTransaction(auth,"DOCUMENT_UPLOADED","LETTER_OF_CREDIT",lc.getId(),"Avisierungsschreiben · Dokument "+document.getId()+" · Original aus Training "+id);
  audit.recordInTransaction(auth,"TRAINING_IMPORTED","TRAINING_SESSION",id,"LC-Akte "+lc.getId()+" · Dokument "+document.getId());
  return new Result(lc.getId(),document.getId());
 }
 private static String clean(String value){return value==null||value.isBlank()?null:value.trim();}
}
