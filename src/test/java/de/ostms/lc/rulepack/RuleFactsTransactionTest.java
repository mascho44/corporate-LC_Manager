package de.ostms.lc.rulepack;
import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.check.service.DocumentCheckService;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import de.ostms.lc.document.repository.LcDocumentRepository;
import de.ostms.lc.document.domain.*;
import de.ostms.lc.lc.domain.LetterOfCredit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.transaction.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static de.ostms.lc.rulepack.PackDefinition.Field;
@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop","spring.datasource.url=jdbc:h2:mem:rulefacttransaction;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver"},showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({RuleFactsController.class,RuleFactsTransactionTest.Beans.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class RuleFactsTransactionTest {
 @TestConfiguration static class Beans{
  @Bean AuditService audit(){return mock(AuditService.class);}
  @Bean DocumentCheckService checks(){return mock(DocumentCheckService.class);}
 }
 @Autowired RuleFactsController controller;@Autowired LetterOfCreditRepository lcs;
 @Autowired LcDocumentRepository docs;@Autowired AuditService audit;
 @Test void factsAreScopedPersistedAndRollbackOnAuditFailure(){
  var lc=new LetterOfCredit();lc.setReference("SYNTHETIC-FACTS");lc=lcs.saveAndFlush(lc);
  var other=new LetterOfCredit();other.setReference("SYNTHETIC-OTHER");other=lcs.saveAndFlush(other);
  var doc=new LcDocument();doc.setLetterOfCredit(lc);doc.setDocumentType(DocumentType.COMMERCIAL_INVOICE);
  doc.setOriginalFilename("synthetic.pdf");doc.setContentType("application/pdf");doc.setContent(new byte[]{1});doc.setFileSize(1);doc=docs.saveAndFlush(doc);
  var auth=new UsernamePasswordAuthenticationToken("synthetic-editor","unused");
  var id=lc.getId();var docId=doc.getId();var otherId=other.getId();
  assertThatThrownBy(()->controller.updateDocument(otherId,docId,Map.of(Field.DOCUMENT_ISSUER,"Demo"),auth)).isInstanceOf(NoSuchElementException.class);
  AuditService target=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(audit);
  doThrow(new IllegalStateException("Synthetic audit failure")).when(target).recordInTransaction(any(),anyString(),anyString(),any(),anyString());
  assertThatThrownBy(()->controller.updateLc(id,Map.of(Field.LC_TRANSFERRED,"false"),auth)).isInstanceOf(IllegalStateException.class);
  assertThat(lcs.findById(id).orElseThrow().getRuleFactsJson()).isNull();
  assertThatThrownBy(()->controller.updateDocument(id,docId,Map.of(Field.DOCUMENT_ISSUER,"Demo"),auth)).isInstanceOf(IllegalStateException.class);
  assertThat(docs.findById(docId).orElseThrow().getRuleFactsJson()).isNull();
  assertThatThrownBy(()->controller.updateRequirements(id,DocumentType.BILL_OF_LADING,Map.of(Field.LC_REQUIRED_ORIGINAL_COUNT,"3"),auth)).isInstanceOf(IllegalStateException.class);
  assertThat(lcs.findById(id).orElseThrow().getRuleRequirementsJson()).isNull();
  reset(target);
  controller.updateLc(id,Map.of(Field.LC_TRANSFERRED,"false"),auth);
  controller.updateDocument(id,docId,Map.of(Field.DOCUMENT_ISSUER,"Demo"),auth);
  controller.updateRequirements(id,DocumentType.BILL_OF_LADING,Map.of(Field.LC_REQUIRED_ORIGINAL_COUNT,"3"),auth);
  controller.updateRequirements(id,DocumentType.INSURANCE_CERTIFICATE,Map.of(Field.LC_SIGNATURE_REQUIRED,"true"),auth);
  assertThat(RuleRequirements.read(lcs.findById(id).orElseThrow().getRuleRequirementsJson()).get(DocumentType.BILL_OF_LADING)).containsEntry(Field.LC_REQUIRED_ORIGINAL_COUNT,"3");
  assertThat(controller.lc(id)).containsEntry(Field.LC_TRANSFERRED,"false");
  assertThat(controller.document(id,docId)).containsEntry(Field.DOCUMENT_ISSUER,"Demo");
 }
}
