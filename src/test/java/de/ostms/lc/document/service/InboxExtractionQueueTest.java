package de.ostms.lc.document.service;
import de.ostms.lc.document.domain.*;
import de.ostms.lc.document.repository.DocumentInboxRepository;
import de.ostms.lc.audit.service.AuditService;
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
 @Test void checkpointsProgressAndReusesEvidenceWithoutSplittingPartialRecognition()throws Exception{
  var item=enqueue("QUEUED");
  String evidence=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(new OcrEvidence("synthetic","test",200,.8,List.of(),List.of(new OcrEvidence.PageResult(1,"OCR_EXTRACTED",1),new OcrEvidence.PageResult(2,"OCR_TIMEOUT",1))));
  item.setOcrEvidenceJson(evidence);inbox.saveAndFlush(item);
  doAnswer(call->{
   LcDocument document=call.getArgument(0);assertThat(document.getOcrEvidenceJson()).isEqualTo(evidence);
   document.setExtractedText("Synthetic partial text");document.setOcrEvidenceJson(evidence);
   java.util.function.Consumer<LcDocument> checkpoint=call.getArgument(1);checkpoint.accept(document);
   var saved=inbox.findById(item.getId()).orElseThrow();assertThat(saved.getExtractionStatus()).isEqualTo("PROCESSING");assertThat(saved.getExtractedText()).isEqualTo("Synthetic partial text");
   document.setExtractionStatus("OCR_PARTIAL");return null;
  }).when(extraction).extractInBackground(any(),any());
  queue.processNext();var saved=inbox.findById(item.getId()).orElseThrow();assertThat(saved.getExtractionStatus()).isEqualTo("OCR_PARTIAL");assertThat(saved.getStatus()).isEqualTo("OPEN");assertThat(saved.getOcrEvidenceJson()).isEqualTo(evidence);
 }
 @Test void automaticallySplitsAtomicallyAndRecoversAfterAuditFailure()throws Exception{
  var item=enqueue("QUEUED");item.setOriginalFilename("synthetic-bundle.pdf");item.setContentType("application/pdf");item.setContent(PdfDocumentSplitterTest.pdf("COMMERCIAL INVOICE","PACKING LIST"));inbox.saveAndFlush(item);
  org.springframework.test.util.ReflectionTestUtils.setField(queue,"automaticSplitter",new InboxAutomaticSplitter(inbox,new DocumentExtractionService(),audit));
  doAnswer(call->{LcDocument doc=call.getArgument(0);new DocumentExtractionService().applyRecognizedText(doc,"COMMERCIAL INVOICE\nPACKING LIST","EXTRACTED");return null;}).when(extraction).extractInBackground(any(),any());
  doThrow(new IllegalStateException("Synthetic audit failure")).when(audit).recordInTransaction(any(),eq("DOCUMENT_INBOX_AUTO_SPLIT"),any(),any(),any());
  assertThatThrownBy(()->queue.processNext()).isInstanceOf(IllegalStateException.class);assertThat(inbox.count()).isEqualTo(1);var claimed=inbox.findById(item.getId()).orElseThrow();assertThat(claimed.getStatus()).isEqualTo("OPEN");assertThat(claimed.getExtractionStatus()).isEqualTo("PROCESSING");claimed.setExtractionStartedAt(LocalDateTime.now().minusMinutes(36));inbox.saveAndFlush(claimed);reset(audit);
  queue.processNext();assertThat(inbox.count()).isEqualTo(3);var source=inbox.findById(item.getId()).orElseThrow();assertThat(source.getStatus()).isEqualTo("SPLIT");assertThat(source.getContent()).isEqualTo(item.getContent());var parts=inbox.findAll().stream().filter(p->p.getSourceInboxId()!=null).toList();assertThat(parts).hasSize(2);assertThat(parts).allMatch(p->p.getTenantId().equals(item.getTenantId())&&ClassificationHistory.wasAutomaticallySplit(p.getClassificationHistoryJson())&&ClassificationHistory.selectedType(p.getClassificationHistoryJson())==null);queue.processNext();assertThat(inbox.count()).isEqualTo(3);
 }
 @Autowired DocumentInboxRepository inbox;
 @Autowired PlatformTransactionManager manager;
 DocumentExtractionService extraction;AuditService audit;InboxExtractionQueue queue;
 @BeforeEach void setup(){inbox.deleteAll();extraction=mock(DocumentExtractionService.class);audit=mock(AuditService.class);queue=new InboxExtractionQueue(inbox,extraction,audit,manager);}
 @AfterEach void stop(){queue.shutdown();}
 DocumentInboxItem enqueue(String status){var item=new DocumentInboxItem();item.setOriginalFilename("synthetic.txt");item.setContentType("text/plain");item.setFileSize(1);item.setContent(new byte[]{1});item.setReceivedBy("synthetic-user");item.setExtractionStatus(status);return inbox.saveAndFlush(item);}
 @Test void scheduledWorkerUsesDefaultTenantWithoutLeakingCallerContext()throws Exception{
  var own=enqueue("QUEUED");var caller=UUID.randomUUID();DocumentInboxItem foreign;
  try(var scope=de.ostms.lc.tenant.domain.TenantContext.open(caller)){foreign=enqueue("QUEUED");}
  var completed=new java.util.concurrent.CountDownLatch(1);
  doAnswer(call->{assertThat(de.ostms.lc.tenant.domain.TenantContext.currentId()).isEqualTo(de.ostms.lc.tenant.domain.Tenant.DEFAULT_ID);LcDocument doc=call.getArgument(0);doc.setExtractionStatus("EXTRACTED");return null;}).when(extraction).extractInBackground(any(),any());
  doAnswer(call->{completed.countDown();return null;}).when(audit).record(eq("synthetic-user"),eq("DOCUMENT_INBOX_EXTRACTED"),any(),eq(own.getId()),eq("EXTRACTED"),eq(true),isNull());
  try{
   try(var scope=de.ostms.lc.tenant.domain.TenantContext.open(caller)){queue.dispatch();assertThat(de.ostms.lc.tenant.domain.TenantContext.currentId()).isEqualTo(caller);assertThat(completed.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();assertThat(inbox.findById(foreign.getId()).orElseThrow().getExtractionStatus()).isEqualTo("QUEUED");}
   assertThat(inbox.findById(own.getId()).orElseThrow().getExtractionStatus()).isEqualTo("EXTRACTED");
  }finally{try(var scope=de.ostms.lc.tenant.domain.TenantContext.open(caller)){inbox.deleteById(foreign.getId());}}
 }
 @Test void claimsCommittedFilesAndExtractsOutsideDatabaseTransaction(){
  var item=enqueue("QUEUED");
  doAnswer(call->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();assertThat(inbox.findById(item.getId()).orElseThrow().getExtractionStatus()).isEqualTo("PROCESSING");LcDocument doc=call.getArgument(0);doc.setExtractionStatus("EXTRACTED");doc.setExtractedText("synthetic text");doc.setExtractedReference("SYNTHETIC-REF");return null;}).when(extraction).extractInBackground(any(),any());
  queue.processNext();var saved=inbox.findById(item.getId()).orElseThrow();assertThat(saved.getExtractionStatus()).isEqualTo("EXTRACTED");assertThat(saved.getExtractedText()).isEqualTo("synthetic text");assertThat(saved.getContent()).containsExactly(1);assertThat(saved.getExtractionToken()).isNull();
  queue.processNext();verify(extraction,times(1)).extractInBackground(any(),any());verify(audit).record(eq("synthetic-user"),eq("DOCUMENT_INBOX_EXTRACTED"),any(),eq(item.getId()),eq("EXTRACTED"),eq(true),isNull());
 }
 @Test void deletionDuringExtractionDoesNotResurrectFile(){
  var item=enqueue("QUEUED");doAnswer(call->{inbox.deleteById(item.getId());LcDocument doc=call.getArgument(0);doc.setExtractionStatus("EXTRACTED");return null;}).when(extraction).extractInBackground(any(),any());
  queue.processNext();assertThat(inbox.findById(item.getId())).isEmpty();verifyNoInteractions(audit);
 }
 @Test void workerCannotClaimAnotherTenantsQueuedDocument(){
  var foreignTenant=UUID.randomUUID();DocumentInboxItem foreign;
  try(var scope=de.ostms.lc.tenant.domain.TenantContext.open(foreignTenant)){foreign=enqueue("QUEUED");}
  try{
   queue.processNext();verifyNoInteractions(extraction,audit);
   try(var scope=de.ostms.lc.tenant.domain.TenantContext.open(foreignTenant)){assertThat(inbox.findById(foreign.getId()).orElseThrow().getExtractionStatus()).isEqualTo("QUEUED");}
  }finally{try(var scope=de.ostms.lc.tenant.domain.TenantContext.open(foreignTenant)){inbox.deleteById(foreign.getId());}}
 }
 @Test void workerCarriesClaimedTenantThroughExtractionAndCompletion(){
  var foreignTenant=UUID.randomUUID();DocumentInboxItem foreign;
  try(var scope=de.ostms.lc.tenant.domain.TenantContext.open(foreignTenant)){foreign=enqueue("QUEUED");}
  doAnswer(call->{assertThat(de.ostms.lc.tenant.domain.TenantContext.currentId()).isEqualTo(foreignTenant);LcDocument doc=call.getArgument(0);assertThat(doc.getTenantId()).isEqualTo(foreignTenant);doc.setExtractionStatus("EXTRACTED");return null;}).when(extraction).extractInBackground(any(),any());
  try{
   try(var scope=de.ostms.lc.tenant.domain.TenantContext.open(foreignTenant)){queue.processNext();assertThat(inbox.findById(foreign.getId()).orElseThrow().getExtractionStatus()).isEqualTo("EXTRACTED");}
   assertThat(de.ostms.lc.tenant.domain.TenantContext.currentId()).isEqualTo(de.ostms.lc.tenant.domain.Tenant.DEFAULT_ID);
  }finally{try(var scope=de.ostms.lc.tenant.domain.TenantContext.open(foreignTenant)){inbox.deleteById(foreign.getId());}}
 }
 @Test void preservesDocumentTypeConfirmedDuringSplit(){
  var item=enqueue("QUEUED");item.setClassificationHistoryJson(ClassificationHistory.manual(null,DocumentType.PACKING_LIST,"synthetic-user"));inbox.saveAndFlush(item);
  doAnswer(call->{LcDocument doc=call.getArgument(0);doc.setExtractionStatus("EXTRACTED");doc.setClassificationHistoryJson(ClassificationHistory.automatic("synthetic.txt","COMMERCIAL INVOICE"));return null;}).when(extraction).extractInBackground(any(),any());
  queue.processNext();assertThat(ClassificationHistory.selectedType(inbox.findById(item.getId()).orElseThrow().getClassificationHistoryJson())).isEqualTo(DocumentType.PACKING_LIST);
 }
 @Test void failedForeignExtractionRestoresContextAndDoesNotChangeNextTenantsQueue(){
  var own=enqueue("QUEUED");var foreignTenant=UUID.randomUUID();DocumentInboxItem foreign;
  try(var scope=de.ostms.lc.tenant.domain.TenantContext.open(foreignTenant)){foreign=enqueue("QUEUED");}
  doAnswer(call->{assertThat(de.ostms.lc.tenant.domain.TenantContext.currentId()).isEqualTo(foreignTenant);throw new IllegalStateException("Synthetic OCR failure");}).when(extraction).extractInBackground(any(),any());
  try{
   try(var scope=de.ostms.lc.tenant.domain.TenantContext.open(foreignTenant)){queue.processNext();assertThat(inbox.findById(foreign.getId()).orElseThrow().getExtractionStatus()).isEqualTo("FAILED");}
   assertThat(de.ostms.lc.tenant.domain.TenantContext.currentId()).isEqualTo(de.ostms.lc.tenant.domain.Tenant.DEFAULT_ID);
   assertThat(inbox.findById(own.getId()).orElseThrow().getExtractionStatus()).isEqualTo("QUEUED");
   doAnswer(call->{assertThat(de.ostms.lc.tenant.domain.TenantContext.currentId()).isEqualTo(de.ostms.lc.tenant.domain.Tenant.DEFAULT_ID);LcDocument doc=call.getArgument(0);assertThat(doc.getTenantId()).isEqualTo(de.ostms.lc.tenant.domain.Tenant.DEFAULT_ID);doc.setExtractionStatus("EXTRACTED");return null;}).when(extraction).extractInBackground(any(),any());
   queue.processNext();assertThat(inbox.findById(own.getId()).orElseThrow().getExtractionStatus()).isEqualTo("EXTRACTED");
   try(var scope=de.ostms.lc.tenant.domain.TenantContext.open(foreignTenant)){assertThat(inbox.findById(foreign.getId()).orElseThrow().getExtractionStatus()).isEqualTo("FAILED");}
  }finally{try(var scope=de.ostms.lc.tenant.domain.TenantContext.open(foreignTenant)){inbox.deleteById(foreign.getId());}}
 }
 @Test void claimRejectsForeignItemBeforeReadingContentEvenIfRepositoryReturnsIt(){
  var brokenRepository=mock(DocumentInboxRepository.class);var candidate=UUID.randomUUID();DocumentInboxItem foreign;
  try(var scope=de.ostms.lc.tenant.domain.TenantContext.open(UUID.randomUUID())){foreign=spy(new DocumentInboxItem());foreign.setExtractionStatus("QUEUED");}
  when(brokenRepository.findExtractionCandidates(any(),any())).thenReturn(List.of(candidate));when(brokenRepository.findForUpdate(candidate)).thenReturn(Optional.of(foreign));
  var isolated=new InboxExtractionQueue(brokenRepository,extraction,audit,manager);
  try{assertThatThrownBy(isolated::processNext).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);verify(foreign,never()).getContent();verify(brokenRepository,never()).save(any());verifyNoInteractions(extraction,audit);}
  finally{isolated.shutdown();}
 }
 @Test void recoversExpiredClaimButDoesNotStealActiveWork(){
  var item=enqueue("PROCESSING");item.setExtractionToken(UUID.randomUUID());item.setExtractionStartedAt(LocalDateTime.now());inbox.saveAndFlush(item);
  queue.processNext();verifyNoInteractions(extraction);
  item.setExtractionStartedAt(LocalDateTime.now().minusMinutes(36));inbox.saveAndFlush(item);
  doAnswer(call->{LcDocument doc=call.getArgument(0);doc.setExtractionStatus("OCR_TIMEOUT");return null;}).when(extraction).extractInBackground(any(),any());queue.processNext();
  assertThat(inbox.findById(item.getId()).orElseThrow().getExtractionStatus()).isEqualTo("OCR_TIMEOUT");
 }
 @Test void reclaimedLeaseCannotBeOverwrittenByOldWorker(){
  var item=enqueue("QUEUED");var replacement=UUID.randomUUID();
  doAnswer(call->{new TransactionTemplate(manager).execute(status->{var current=inbox.findForUpdate(item.getId()).orElseThrow();current.setExtractionToken(replacement);return null;});LcDocument doc=call.getArgument(0);doc.setExtractionStatus("EXTRACTED");return null;}).when(extraction).extractInBackground(any(),any());
  queue.processNext();assertThat(inbox.findById(item.getId()).orElseThrow().getExtractionToken()).isEqualTo(replacement);verifyNoInteractions(audit);
 }
}
