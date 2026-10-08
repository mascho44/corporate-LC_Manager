package de.ostms.lc.document.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.ostms.lc.document.api.SplitPretrainingController;
import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.audit.service.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class SplitPretrainingControllerTest {
 @Test void asyncUploadUsesBackgroundExtractionAndReturnsAcceptedJob()throws Exception{
  var extraction=mock(DocumentExtractionService.class);var training=mock(SplitTrainingService.class);var json=new ObjectMapper();var controller=new SplitPretrainingController(extraction,training,json,mock(AuditService.class),new SplitTrainingReceipt(json));var jobs=new SplitPretrainingJobs();
  org.springframework.test.util.ReflectionTestUtils.setField(controller,"jobs",jobs);
  byte[] pdf=PdfDocumentSplitterTest.pdf("Synthetic document customer details goods quantities and declared values for testing only","Synthetic continuation document package details weights quantities and marks for testing only");
  doAnswer(call->{var doc=(de.ostms.lc.document.domain.LcDocument)call.getArgument(0);doc.setExtractionStatus("EXTRACTED");return null;}).when(extraction).extractInBackground(any());
  when(training.suggest(any(),any(),any())).thenAnswer(call->call.getArgument(2));var actor=UsernamePasswordAuthenticationToken.unauthenticated("reviewer",null);
  try{
   var accepted=controller.start(new MockMultipartFile("file","synthetic.pdf","application/pdf",pdf),actor);assertThat(accepted.getStatusCode().value()).isEqualTo(202);
   var id=accepted.getBody().id();long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(3);SplitPretrainingJobs.Status status;
   do{status=controller.status(id,actor).getBody();if(status.state().equals("COMPLETED")||status.state().equals("FAILED"))break;Thread.sleep(10);}while(System.nanoTime()<deadline);
   assertThat(status.state()).isEqualTo("COMPLETED");assertThat(status.result().receipt()).isNotBlank();verify(extraction).extractInBackground(any());verify(extraction,never()).extractFile(any(),any(),any());verify(training,never()).confirmMeasured(any(),any(),any(),any(),any());
  }finally{jobs.shutdown();}
 }
 @Test void previewDoesNotTrainAndConfirmationUsesSignedPatternWithoutSecondExtraction()throws Exception{
  var extraction=spy(new DocumentExtractionService());var training=mock(SplitTrainingService.class);var audit=mock(AuditService.class);var json=new ObjectMapper();var receipts=new SplitTrainingReceipt(json);var controller=new SplitPretrainingController(extraction,training,json,audit,receipts);
  byte[] pdf=PdfDocumentSplitterTest.pdf("Synthetic document customer details goods quantities and declared values for testing only","Synthetic continuation document package details weights quantities and marks for testing only");
  when(training.suggest(any(),any(),any())).thenAnswer(call->call.getArgument(2));var actor=UsernamePasswordAuthenticationToken.unauthenticated("reviewer",null);
  var preview=controller.proposal(new MockMultipartFile("file","synthetic.pdf","application/pdf",pdf),actor);
  verify(training,never()).confirmMeasured(anyString(),any(),anyString(),any(),any());verifyNoInteractions(audit);
  var parts=List.of(new PdfDocumentSplitter.Part(1,1,DocumentType.COMMERCIAL_INVOICE),new PdfDocumentSplitter.Part(2,2,DocumentType.PACKING_LIST));
  assertThat(controller.confirm(new SplitPretrainingController.Confirmation(preview.receipt(),parts),actor).get("status")).isEqualTo("CONFIRMED");
  verify(extraction,times(1)).extractFile(any(),anyString(),anyString());verify(training).confirmMeasured(matches("[a-f0-9]{64}"),eq(parts),eq("reviewer"),any(),eq("RULE_BASED"));verify(audit).recordInTransaction(eq(actor),eq("DOCUMENT_SPLIT_PRETRAINED"),eq("TRAINING"),isNull(),anyString());
 }
 @Test void invalidReceiptAndIncompleteCoverageCannotTrain()throws Exception{
  var training=mock(SplitTrainingService.class);var json=new ObjectMapper();var receipts=new SplitTrainingReceipt(json);var controller=new SplitPretrainingController(new DocumentExtractionService(),training,json,mock(AuditService.class),receipts);var actor=UsernamePasswordAuthenticationToken.unauthenticated("reviewer",null);
  var parts=List.of(new PdfDocumentSplitter.Part(1,1,DocumentType.OTHER),new PdfDocumentSplitter.Part(3,3,DocumentType.OTHER));
  assertThatThrownBy(()->controller.confirm(new SplitPretrainingController.Confirmation("invalid",parts),actor)).isInstanceOf(IllegalArgumentException.class);
  String token=receipts.issue("a".repeat(64),3,"reviewer");assertThatThrownBy(()->controller.confirm(new SplitPretrainingController.Confirmation(token,parts),actor)).isInstanceOf(IllegalArgumentException.class);verifyNoInteractions(training);
 }
}
