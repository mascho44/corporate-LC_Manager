package de.ostms.lc.document.service;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.check.service.DocumentCheckService;
import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.document.domain.LcDocument;
import de.ostms.lc.document.repository.LcDocumentRepository;
import de.ostms.lc.lc.domain.LetterOfCredit;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DocumentReRecognitionServiceTest {
 private final LcDocumentRepository documents=mock(LcDocumentRepository.class);private final DocumentExtractionService extraction=mock(DocumentExtractionService.class);
 private final PdfPagePreviewService pages=mock(PdfPagePreviewService.class);private final DocumentCheckService checks=mock(DocumentCheckService.class);private final AuditService audit=mock(AuditService.class);
 private final DocumentReRecognitionService service=new DocumentReRecognitionService(documents,extraction,pages,checks,audit,mock(PlatformTransactionManager.class));
 private final TestingAuthenticationToken auth=new TestingAuthenticationToken("markus","x");
 private final UUID lcId=UUID.randomUUID();private final UUID documentId=UUID.randomUUID();

 private LcDocument stored(){
  var lc=new LetterOfCredit();ReflectionTestUtils.setField(lc,"id",lcId);
  var d=new LcDocument();ReflectionTestUtils.setField(d,"id",documentId);d.setLetterOfCredit(lc);d.setOriginalFilename("scan.pdf");d.setContentType("image/png");d.setContent(new byte[]{1,2});d.setFileSize(2);
  d.setDocumentType(DocumentType.OTHER);d.setExtractionStatus("NO_TEXT");d.setDocumentDate(LocalDate.of(2026,1,1));d.setAmount(new BigDecimal("10"));return d;
 }
 private void recognise(String text,String status){
  doAnswer(call->{LcDocument probe=call.getArgument(0);probe.setExtractedText(text);probe.setExtractionStatus(status);probe.setDocumentDate(DocumentDateDetector.detect(text).date());probe.setExtractedCurrency("EUR");probe.setExtractedAmount(new BigDecimal("66252.52"));probe.setExtractedDocumentNumber("INV-1");return null;}).when(extraction).extract(any());
 }

 @Test void previewShowsMachineChangesFillsOnlyEmptyHumanFieldsAndOnlySuggestsTheType(){
  var doc=stored();when(documents.findById(documentId)).thenReturn(Optional.of(doc));
  recognise("COMMERCIAL INVOICE\nInvoice date: 21.07.2026\nORIGINAL\nTotal EUR 66.252,52","OCR_EXTRACTED");
  var preview=service.preview(lcId,documentId,auth);
  var byKey=preview.changes().stream().collect(java.util.stream.Collectors.toMap(DocumentReRecognitionService.Change::key,c->c));
  assertThat(byKey.get("status").appliesOnConfirm()).isTrue();assertThat(byKey.get("status").after()).isEqualTo("OCR_EXTRACTED");
  assertThat(byKey.get("documentNumber").after()).isEqualTo("INV-1");
  assertThat(byKey.get("documentDate").appliesOnConfirm()).isFalse(); // already maintained by hand
  assertThat(byKey.get("documentDate").note()).contains("bleibt unverändert");
  assertThat(byKey.get("copy").appliesOnConfirm()).isTrue();assertThat(byKey.get("copy").after()).isEqualTo("Original");
  assertThat(byKey.get("currency").appliesOnConfirm()).isTrue();assertThat(byKey.get("amount").appliesOnConfirm()).isFalse();
  assertThat(byKey.get("type").appliesOnConfirm()).isFalse();assertThat(byKey.get("type").after()).isEqualTo("Commercial Invoice");
  assertThat(preview.token()).isNotBlank();
  verify(documents,never()).save(any()); // preview never stores anything
 }
 @Test void applyReplacesMachineFieldsFillsEmptyOnesRecordsAuditAndTheTokenWorksOnce(){
  var doc=stored();doc.setDocumentDate(null);when(documents.findById(documentId)).thenReturn(Optional.of(doc));when(documents.save(any())).thenAnswer(call->call.getArgument(0));
  recognise("COMMERCIAL INVOICE\nInvoice date: 21.07.2026\nCOPY","OCR_EXTRACTED");
  var preview=service.preview(lcId,documentId,auth);
  var view=service.apply(lcId,documentId,preview.token(),auth);
  assertThat(doc.getExtractionStatus()).isEqualTo("OCR_EXTRACTED");assertThat(doc.getExtractedDocumentNumber()).isEqualTo("INV-1");
  assertThat(doc.getDocumentDate()).isEqualTo(LocalDate.of(2026,7,21));assertThat(doc.getCopyNumber()).isEqualTo(1);assertThat(doc.getAmount()).isEqualByComparingTo("10");assertThat(doc.getCurrency()).isEqualTo("EUR");
  assertThat(doc.getDocumentType()).isEqualTo(DocumentType.OTHER);assertThat(view).isNotNull();
  verify(checks).invalidateDecisions(lcId);
  verify(audit).recordChangeInTransaction(eq(auth),eq("DOCUMENT_RE_RECOGNIZED"),eq("LETTER_OF_CREDIT"),eq(lcId),contains("scan.pdf"),anyString(),anyString());
  assertThatThrownBy(()->service.apply(lcId,documentId,preview.token(),auth)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("abgelaufen");
 }
 @Test void aTokenIsBoundToDocumentUserAndLc(){
  var doc=stored();when(documents.findById(documentId)).thenReturn(Optional.of(doc));recognise("text","EXTRACTED");
  var token=service.preview(lcId,documentId,auth).token();
  assertThatThrownBy(()->service.apply(lcId,documentId,token,new TestingAuthenticationToken("other","x"))).isInstanceOf(IllegalArgumentException.class);
  var token2=service.preview(lcId,documentId,auth).token();
  assertThatThrownBy(()->service.apply(UUID.randomUUID(),documentId,token2,auth)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->service.apply(lcId,documentId,"unknown",auth)).isInstanceOf(IllegalArgumentException.class);
  verify(documents,never()).save(any());
 }
 @Test void foreignDocumentsAndVeryLongPdfsAreRejected()throws Exception{
  var doc=stored();when(documents.findById(documentId)).thenReturn(Optional.of(doc));
  assertThatThrownBy(()->service.preview(UUID.randomUUID(),documentId,auth)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("gehört nicht");
  doc.setContentType("application/pdf");when(pages.pageCount(any())).thenReturn(11);
  assertThatThrownBy(()->service.preview(lcId,documentId,auth)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("mehr als 10 Seiten");
  verify(extraction,never()).extract(any());
 }
}
