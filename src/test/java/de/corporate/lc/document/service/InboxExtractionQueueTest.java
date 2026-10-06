package de.corporate.lc.document.service;
import de.corporate.lc.document.domain.*;
import de.corporate.lc.document.repository.DocumentInboxRepository;
import de.corporate.lc.audit.service.AuditService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop","spring.datasource.url=jdbc:h2:mem:inboxqueue;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver"},showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class InboxExtractionQueueTest {
 @Autowired DocumentInboxRepository inbox;
 @Autowired PlatformTransactionManager manager;
 DocumentExtractionService extraction;AuditService audit;InboxExtractionQueue queue;
 @BeforeEach void setup(){inbox.deleteAll();extraction=mock(DocumentExtractionService.class);audit=mock(AuditService.class);queue=new InboxExtractionQueue(inbox,extraction,audit,manager);}
 @AfterEach void stop(){queue.shutdown();}
 DocumentInboxItem enqueue(String status){var item=new DocumentInboxItem();item.setOriginalFilename("synthetic.txt");item.setContentType("text/plain");item.setFileSize(1);item.setContent(new byte[]{1});item.setReceivedBy("synthetic-user");item.setExtractionStatus(status);return inbox.saveAndFlush(item);}
 @Test void scheduledWorkerUsesDefaultTenantWithoutLeakingCallerContext()throws Exception{
  var own=enqueue("QUEUED");var caller=UUID.randomUUID();DocumentInboxItem foreign;
  try(var scope=de.corporate.lc.tenant.domain.TenantContext.open(caller)){foreign=enqueue("QUEUED");}
  var completed=new java.util.concurrent.CountDownLatch(1);
  doAnswer(call->{assertThat(de.corporate.lc.tenant.domain.TenantContext.currentId()).isEqualTo(de.corporate.lc.tenant.domain.Tenant.DEFAULT_ID);LcDocument doc=call.getArgument(0);doc.setExtractionStatus("EXTRACTED");return null;}).when(extraction).extractInBackground(any());
  doAnswer(call->{completed.countDown();return null;}).when(audit).record(eq("synthetic-user"),eq("DOCUMENT_INBOX_EXTRACTED"),any(),eq(own.getId()),eq("EXTRACTED"),eq(true),isNull());
  try{
   try(var scope=de.corporate.lc.tenant.domain.TenantContext.open(caller)){queue.dispatch();assertThat(de.corporate.lc.tenant.domain.TenantContext.currentId()).isEqualTo(caller);assertThat(completed.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();assertThat(inbox.findById(foreign.getId()).orElseThrow().getExtractionStatus()).isEqualTo("QUEUED");}
   assertThat(inbox.findById(own.getId()).orElseThrow().getExtractionStatus()).isEqualTo("EXTRACTED");
  }finally{try(var scope=de.corporate.lc.tenant.domain.TenantContext.open(caller)){inbox.deleteById(foreign.getId());}}
 }
 @Test void claimsCommittedFilesAndExtractsOutsideDatabaseTransaction(){
  var item=enqueue("QUEUED");
  doAnswer(call->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();assertThat(inbox.findById(item.getId()).orElseThrow().getExtractionStatus()).isEqualTo("PROCESSING");LcDocument doc=call.getArgument(0);doc.setExtractionStatus("EXTRACTED");doc.setExtractedText("synthetic text");doc.setExtractedReference("SYNTHETIC-REF");return null;}).when(extraction).extractInBackground(any());
  queue.processNext();var saved=inbox.findById(item.getId()).orElseThrow();assertThat(saved.getExtractionStatus()).isEqualTo("EXTRACTED");assertThat(saved.getExtractedText()).isEqualTo("synthetic text");assertThat(saved.getContent()).containsExactly(1);assertThat(saved.getExtractionToken()).isNull();
  queue.processNext();verify(extraction,times(1)).extractInBackground(any());verify(audit).record(eq("synthetic-user"),eq("DOCUMENT_INBOX_EXTRACTED"),any(),eq(item.getId()),eq("EXTRACTED"),eq(true),isNull());
 }
 @Test void deletionDuringExtractionDoesNotResurrectFile(){
  var item=enqueue("QUEUED");doAnswer(call->{inbox.deleteById(item.getId());LcDocument doc=call.getArgument(0);doc.setExtractionStatus("EXTRACTED");return null;}).when(extraction).extractInBackground(any());
  queue.processNext();assertThat(inbox.findById(item.getId())).isEmpty();verifyNoInteractions(audit);
 }
 @Test void workerCannotClaimAnotherTenantsQueuedDocument(){
  var foreignTenant=UUID.randomUUID();DocumentInboxItem foreign;
  try(var scope=de.corporate.lc.tenant.domain.TenantContext.open(foreignTenant)){foreign=enqueue("QUEUED");}
  try{
   queue.processNext();verifyNoInteractions(extraction,audit);
   try(var scope=de.corporate.lc.tenant.domain.TenantContext.open(foreignTenant)){assertThat(inbox.findById(foreign.getId()).orElseThrow().getExtractionStatus()).isEqualTo("QUEUED");}
  }finally{try(var scope=de.corporate.lc.tenant.domain.TenantContext.open(foreignTenant)){inbox.deleteById(foreign.getId());}}
 }
 @Test void workerCarriesClaimedTenantThroughExtractionAndCompletion(){
  var foreignTenant=UUID.randomUUID();DocumentInboxItem foreign;
  try(var scope=de.corporate.lc.tenant.domain.TenantContext.open(foreignTenant)){foreign=enqueue("QUEUED");}
  doAnswer(call->{assertThat(de.corporate.lc.tenant.domain.TenantContext.currentId()).isEqualTo(foreignTenant);LcDocument doc=call.getArgument(0);assertThat(doc.getTenantId()).isEqualTo(foreignTenant);doc.setExtractionStatus("EXTRACTED");return null;}).when(extraction).extractInBackground(any());
  try{
   try(var scope=de.corporate.lc.tenant.domain.TenantContext.open(foreignTenant)){queue.processNext();assertThat(inbox.findById(foreign.getId()).orElseThrow().getExtractionStatus()).isEqualTo("EXTRACTED");}
   assertThat(de.corporate.lc.tenant.domain.TenantContext.currentId()).isEqualTo(de.corporate.lc.tenant.domain.Tenant.DEFAULT_ID);
  }finally{try(var scope=de.corporate.lc.tenant.domain.TenantContext.open(foreignTenant)){inbox.deleteById(foreign.getId());}}
 }
 @Test void preservesDocumentTypeConfirmedDuringSplit(){
  var item=enqueue("QUEUED");item.setClassificationHistoryJson(ClassificationHistory.manual(null,DocumentType.PACKING_LIST,"synthetic-user"));inbox.saveAndFlush(item);
  doAnswer(call->{LcDocument doc=call.getArgument(0);doc.setExtractionStatus("EXTRACTED");doc.setClassificationHistoryJson(ClassificationHistory.automatic("synthetic.txt","COMMERCIAL INVOICE"));return null;}).when(extraction).extractInBackground(any());
  queue.processNext();assertThat(ClassificationHistory.selectedType(inbox.findById(item.getId()).orElseThrow().getClassificationHistoryJson())).isEqualTo(DocumentType.PACKING_LIST);
 }
 @Test void recoversExpiredClaimButDoesNotStealActiveWork(){
  var item=enqueue("PROCESSING");item.setExtractionToken(UUID.randomUUID());item.setExtractionStartedAt(LocalDateTime.now());inbox.saveAndFlush(item);
  queue.processNext();verifyNoInteractions(extraction);
  item.setExtractionStartedAt(LocalDateTime.now().minusMinutes(36));inbox.saveAndFlush(item);
  doAnswer(call->{LcDocument doc=call.getArgument(0);doc.setExtractionStatus("OCR_TIMEOUT");return null;}).when(extraction).extractInBackground(any());queue.processNext();
  assertThat(inbox.findById(item.getId()).orElseThrow().getExtractionStatus()).isEqualTo("OCR_TIMEOUT");
 }
 @Test void reclaimedLeaseCannotBeOverwrittenByOldWorker(){
  var item=enqueue("QUEUED");var replacement=UUID.randomUUID();
  doAnswer(call->{new TransactionTemplate(manager).execute(status->{var current=inbox.findForUpdate(item.getId()).orElseThrow();current.setExtractionToken(replacement);return null;});LcDocument doc=call.getArgument(0);doc.setExtractionStatus("EXTRACTED");return null;}).when(extraction).extractInBackground(any());
  queue.processNext();assertThat(inbox.findById(item.getId()).orElseThrow().getExtractionToken()).isEqualTo(replacement);verifyNoInteractions(audit);
 }
}
