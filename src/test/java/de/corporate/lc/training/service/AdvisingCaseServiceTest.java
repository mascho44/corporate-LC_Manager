package de.corporate.lc.training.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.corporate.lc.audit.service.AuditService;
import de.corporate.lc.document.repository.LcDocumentRepository;
import de.corporate.lc.lc.repository.LetterOfCreditRepository;
import de.corporate.lc.training.domain.TrainingSession;
import de.corporate.lc.training.repository.TrainingSessionRepository;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdvisingCaseServiceTest {
 final ObjectMapper mapper=new ObjectMapper();
 final TrainingSessionRepository sessions=mock(TrainingSessionRepository.class);
 final LetterOfCreditRepository lcs=mock(LetterOfCreditRepository.class);
 final LcDocumentRepository documents=mock(LcDocumentRepository.class);
 final AdvisingCaseService service=new AdvisingCaseService(sessions,lcs,documents,mapper,mock(AuditService.class));
 final UUID id=UUID.randomUUID();
 TrainingSession session(List<AdvisingTrainingService.Field> fields)throws Exception{
  var s=new TrainingSession();s.setMessageType("ADVISING_LETTER");s.setUsername("user");s.setStatus("DRAFT");s.setOriginalPdf(new byte[]{1,2});s.setExtractedText("LC number: WRONG\nApplicant: WRONG");s.setReviewsJson(mapper.writeValueAsString(fields));when(sessions.findById(id)).thenReturn(Optional.of(s));return s;
 }
 AdvisingTrainingService.Field field(String code,String value,String review){return new AdvisingTrainingService.Field("Label","BANK|LABEL","WRONG",code,value,review);}
 @Test void previewUsesCorrectedValuesAndDiscardsOriginalAndRejectedFields()throws Exception{
  session(List.of(field("reference","RIGHT","corrected"),field("applicant","Rejected","invalid"),field("amount","EUR 1.234,56","correct"),field("expiryDate","261130 / GERMANY","correct")));
  var result=service.preview(id,"user");assertThat(result.fields()).containsEntry("reference","RIGHT").containsEntry("amount","1234.56").containsEntry("currency","EUR").containsEntry("expiryDate","2026-11-30").doesNotContainKey("applicant");
 }
 @Test void conflictingTargetsAreNotSilentlyChosen()throws Exception{
  session(List.of(field("reference","FIRST","correct"),field("reference","SECOND","correct")));
  var result=service.preview(id,"user");assertThat(result.fields()).doesNotContainKey("reference");assertThat(result.warnings()).anyMatch(w->w.contains("Mehrdeutige"));
 }
 @Test void reviewOwnerAndLinkedCaseCannotBeBypassed()throws Exception{
  var s=session(List.of(field("reference","RIGHT",null)));
  assertThatThrownBy(()->service.preview(id,"user")).hasMessageContaining("alle Angaben");
  assertThatThrownBy(()->service.preview(id,"other")).hasMessageContaining("nicht übernommen");
  s.setStatus("CONFIRMED");s.setLcId(UUID.randomUUID());assertThatThrownBy(()->service.preview(id,"user")).hasMessageContaining("bereits");verifyNoInteractions(lcs,documents);
 }
}
