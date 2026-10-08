package de.ostms.lc.training.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.ostms.lc.training.domain.TrainingSession;
import de.ostms.lc.training.repository.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class AdvisingTrainingServiceTest {
 final ObjectMapper mapper=new ObjectMapper();final TrainingSessionRepository sessions=mock(TrainingSessionRepository.class);final TrainingLearningControlRepository controls=mock(TrainingLearningControlRepository.class);
 final TrainingLearningService learning=new TrainingLearningService(sessions,controls,mapper);final AdvisingTrainingService training=new AdvisingTrainingService(sessions,mapper,learning);
 @Test void bankScopedMappingLearnsFromThreeDraftsNotValues()throws Exception{
  List<TrainingSession> examples=new ArrayList<>();for(int i=0;i<3;i++){var session=new TrainingSession();session.setMessageType("ADVISING_LETTER");session.setStatus("DRAFT");session.setReviewsJson(mapper.writeValueAsString(List.of(new AdvisingTrainingService.Field("Our reference","BANK-A|OUR REFERENCE","OLD-"+i,"ownBankReference","OLD-"+i,"correct"))));examples.add(session);}
  when(sessions.findAll()).thenReturn(examples.subList(0,2));assertThat(learning.adviceTarget("BANK-A","Our reference")).isNull();
  when(sessions.findAll()).thenReturn(examples);assertThat(learning.adviceTarget("BANK-A","Our reference")).isEqualTo("ownBankReference");assertThat(learning.adviceTarget("BANK-B","Our reference")).isNull();
  assertThat(training.preview("BANK-A","Our reference: NEW-999").fields()).containsEntry("ownBankReference","NEW-999");assertThat(training.preview("BANK-B","Our reference: NEW-999").fields()).doesNotContainKey("ownBankReference");
 }
 @Test void savesImmediatelyPreservingOriginalAndRestrictsOwner()throws Exception{
  var session=new TrainingSession();session.setMessageType("ADVISING_LETTER");session.setStatus("DRAFT");session.setUsername("user");var field=new AdvisingTrainingService.Field("Our reference","BANK|OUR REFERENCE","OLD","","OLD",null);session.setReviewsJson(mapper.writeValueAsString(List.of(field)));UUID id=UUID.randomUUID();when(sessions.findForUpdate(id)).thenReturn(Optional.of(session));when(sessions.save(any())).thenAnswer(call->call.getArgument(0));
  var submitted=new AdvisingTrainingService.Field("forged","forged","forged","ownBankReference","NEW","corrected");training.save(id,List.of(submitted),"user",false);var saved=mapper.readValue(session.getReviewsJson(),AdvisingTrainingService.Field[].class)[0];assertThat(saved.originalCode()).isEqualTo("BANK|OUR REFERENCE");assertThat(saved.originalValue()).isEqualTo("OLD");assertThat(saved.value()).isEqualTo("NEW");assertThat(session.getStatus()).isEqualTo("DRAFT");
  assertThatThrownBy(()->training.save(id,List.of(submitted),"other",false)).isInstanceOf(IllegalArgumentException.class);
  training.save(id,List.of(submitted),"user",true);assertThat(session.getStatus()).isEqualTo("CONFIRMED");assertThatThrownBy(()->training.save(id,List.of(submitted),"user",false)).isInstanceOf(IllegalArgumentException.class);
 }
}
