package de.ostms.lc.document.service;
import de.ostms.lc.document.domain.*;
import de.ostms.lc.document.repository.DocumentInboxRepository;
import de.ostms.lc.audit.service.AuditService;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class InboxAutomaticSplitterTest {
 final InboxAutomaticSplitter splitter=new InboxAutomaticSplitter(mock(DocumentInboxRepository.class),new DocumentExtractionService(),mock(AuditService.class));
 LcDocument document(String... pages)throws Exception{var d=new LcDocument();d.setContentType("application/pdf");d.setContent(PdfDocumentSplitterTest.pdf(pages));d.setExtractionStatus("EXTRACTED");return d;}
 @Test void preparesDifferentTypesWithoutChangingOrHumanConfirmingOriginal()throws Exception{var doc=document("COMMERCIAL INVOICE","COMMERCIAL INVOICE","PACKING LIST");var result=splitter.prepare(doc);assertThat(result.outputs()).hasSize(2);assertThat(result.outputs().get(0).part().toPage()).isEqualTo(2);assertThat(result.outputs().get(1).part().documentType()).isEqualTo(DocumentType.PACKING_LIST);assertThat(ClassificationHistory.selectedType(ClassificationHistory.automaticSplit("part.pdf",result.outputs().get(1).text()))).isNull();}
 @Test void unknownContinuationAndSingleTypeRequireManualReview()throws Exception{assertThat(splitter.prepare(document("COMMERCIAL INVOICE","Unknown continuation","PACKING LIST")).outputs()).isEmpty();assertThat(splitter.prepare(document("COMMERCIAL INVOICE","COMMERCIAL INVOICE")).outputs()).isEmpty();}
 @Test void confidentScansReusePageEvidenceAndUncertainWordsPreventAutomaticSplit()throws Exception{var doc=document("","");doc.setExtractionStatus("OCR_EXTRACTED");var words=List.of(new OcrEvidence.Word("COMMERCIAL INVOICE",.95,1,0,0,200,20),new OcrEvidence.Word("PACKING LIST",.95,2,0,0,200,20));var mapper=new com.fasterxml.jackson.databind.ObjectMapper();doc.setOcrEvidenceJson(mapper.writeValueAsString(new OcrEvidence("test","OCR",200,.8,words)));var result=splitter.prepare(doc);assertThat(result.outputs()).hasSize(2);assertThat(result.outputs().get(1).evidence().words().get(0).page()).isEqualTo(1);doc.setOcrEvidenceJson(mapper.writeValueAsString(new OcrEvidence("test","OCR",200,.8,List.of(words.get(0),new OcrEvidence.Word("PACKING LIST",.3,2,0,0,200,20)))));assertThat(splitter.prepare(doc).outputs()).isEmpty();}
 @Test void failedAndNonPdfDocumentsNeverSplit()throws Exception{var doc=document("COMMERCIAL INVOICE","PACKING LIST");doc.setExtractionStatus("FAILED");assertThat(splitter.prepare(doc).outputs()).isEmpty();doc.setExtractionStatus("EXTRACTED");doc.setContentType("text/plain");assertThat(splitter.prepare(doc).outputs()).isEmpty();}
 @Test void foreignOwnershipIsRejectedBeforeReadingPdf()throws Exception{var doc=document("COMMERCIAL INVOICE","PACKING LIST");try(var scope=de.ostms.lc.tenant.domain.TenantContext.open(UUID.randomUUID())){assertThatThrownBy(()->splitter.prepare(doc)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);}}
}
