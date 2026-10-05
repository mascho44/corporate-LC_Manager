package de.corporate.lc.training.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.corporate.lc.audit.service.AuditService;
import de.corporate.lc.audit.repository.AuditEventRepository;
import de.corporate.lc.document.repository.LcDocumentRepository;
import de.corporate.lc.lc.repository.LetterOfCreditRepository;
import de.corporate.lc.messaging.service.*;
import de.corporate.lc.messaging.repository.OutboxMessageRepository;
import de.corporate.lc.training.api.AdvisingNewCaseRequest;
import de.corporate.lc.training.domain.TrainingSession;
import de.corporate.lc.training.repository.TrainingSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop","spring.datasource.url=jdbc:h2:mem:advisingcase;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver"},showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({AdvisingCaseService.class,AuditService.class,OutboxService.class,AdvisingCaseTransactionTest.Beans.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class AdvisingCaseTransactionTest {
 @TestConfiguration static class Beans {
  @Bean ObjectMapper objectMapper(){return new ObjectMapper();}
  @Bean MessagePublisher publisher(){return mock(MessagePublisher.class);}
 }
 @Autowired AdvisingCaseService service;
 @Autowired TrainingSessionRepository sessions;
 @Autowired LetterOfCreditRepository lcs;
 @Autowired LcDocumentRepository documents;
 @Autowired AuditEventRepository events;
 @Autowired OutboxMessageRepository outbox;
 @Autowired ObjectMapper mapper;
 @MockitoSpyBean AuditService audit;
 final UsernamePasswordAuthenticationToken auth=new UsernamePasswordAuthenticationToken("user","unused",List.of());
 TrainingSession draft()throws Exception{
  var s=new TrainingSession();s.setMessageType("ADVISING_LETTER");s.setStatus("DRAFT");s.setUsername("user");s.setFilename("advice.pdf");s.setContentType("application/pdf");s.setOriginalPdf(new byte[]{1,2,3});s.setExtractedText("original");s.setExtractionStatus("EXTRACTED");s.setReviewsJson(mapper.writeValueAsString(List.of(new AdvisingTrainingService.Field("LC number","BANK|LC NUMBER","original","reference","confirmed","corrected"))));return sessions.saveAndFlush(s);
 }
 AdvisingNewCaseRequest request(String reference){return new AdvisingNewCaseRequest(reference,"OWN","FOREIGN","Applicant","Beneficiary",new BigDecimal("123.45"),"EUR",LocalDate.of(2027,1,1),"Bank","Germany");}
 @Test void caseOriginalAuditAndOutboxCommitTogetherAndCannotBeRepeated()throws Exception{
  var draft=draft();long auditBefore=events.count(),outboxBefore=outbox.count();String reference="LC-"+UUID.randomUUID();
  var result=service.create(draft.getId(),request(reference),auth);
  assertThat(lcs.findById(result.lcId()).orElseThrow().getReference()).isEqualTo(reference);
  assertThat(documents.findById(result.documentId()).orElseThrow().getContent()).containsExactly(1,2,3);
  var saved=sessions.findById(draft.getId()).orElseThrow();assertThat(saved.getLcId()).isEqualTo(result.lcId());assertThat(saved.getStatus()).isEqualTo("CONFIRMED");
  assertThat(events.count()).isEqualTo(auditBefore+3);assertThat(outbox.count()).isEqualTo(outboxBefore+3);
  assertThatThrownBy(()->service.create(draft.getId(),request("ANOTHER"),auth)).hasMessageContaining("bereits");
  var other=draft();assertThatThrownBy(()->service.create(other.getId(),request(reference),auth)).hasMessageContaining("Referenz besteht bereits");assertThat(sessions.findById(other.getId()).orElseThrow().getLcId()).isNull();
 }
 @Test void failureAfterDocumentAndAuditWritesRollsBackAllBusinessChanges()throws Exception{
  var draft=draft();long lcBefore=lcs.count(),docBefore=documents.count(),auditBefore=events.count(),outboxBefore=outbox.count();
  AuditService target=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(audit);
  doThrow(new IllegalStateException("audit failure")).when(target).recordInTransaction(any(),eq("TRAINING_IMPORTED"),anyString(),any(),anyString());
  assertThatThrownBy(()->service.create(draft.getId(),request("FAIL-"+UUID.randomUUID()),auth)).hasMessageContaining("audit failure");
  assertThat(lcs.count()).isEqualTo(lcBefore);assertThat(documents.count()).isEqualTo(docBefore);assertThat(events.count()).isEqualTo(auditBefore);assertThat(outbox.count()).isEqualTo(outboxBefore);
  var saved=sessions.findById(draft.getId()).orElseThrow();assertThat(saved.getStatus()).isEqualTo("DRAFT");assertThat(saved.getLcId()).isNull();assertThat(saved.getOriginalPdf()).containsExactly(1,2,3);
 }
}
