package de.ostms.lc.document.service;

import de.ostms.lc.check.service.DocumentCheckService;
import de.ostms.lc.document.api.DocumentInboxAttachRequest;
import de.ostms.lc.document.domain.*;
import de.ostms.lc.document.repository.*;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DocumentInboxServiceTest {
    @Test void metadataReviewIsSavedWithoutTrainingAndRejectsUnavailableItems(){
        var id=UUID.randomUUID();var original=item();when(inbox.findForUpdate(id)).thenReturn(Optional.of(original));
        service.saveMetadataReview(id,"{\"confirmed\":false}");
        assertThat(original.getMetadataReviewJson()).isEqualTo("{\"confirmed\":false}");verify(inbox).save(original);verifyNoInteractions(documents,checks);
        original.setExtractionStatus("PROCESSING");assertThatThrownBy(()->service.saveMetadataReview(id,"{}")).isInstanceOf(IllegalStateException.class);
        original.setExtractionStatus("EXTRACTED");assertThatThrownBy(()->service.saveMetadataReview(id,"x".repeat(4097))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void existingProcessedPdfCanBeAutomaticallySplitWithoutAnotherUpload()throws Exception{var id=UUID.randomUUID();var source=item();ReflectionTestUtils.setField(source,"id",id);source.setContentType("application/pdf");source.setOriginalFilename("synthetic-old.pdf");source.setExtractionStatus("EXTRACTED");source.setReceivedBy("original-uploader");source.setContent(PdfDocumentSplitterTest.pdf("COMMERCIAL INVOICE","PACKING LIST"));when(inbox.findForUpdate(id)).thenReturn(Optional.of(source));when(lcs.findAssignmentTargets()).thenReturn(List.of());when(inbox.saveAndFlush(any())).thenAnswer(call->{DocumentInboxItem saved=call.getArgument(0);if(saved.getId()==null)ReflectionTestUtils.setField(saved,"id",UUID.randomUUID());return saved;});var audit=mock(de.ostms.lc.audit.service.AuditService.class);ReflectionTestUtils.setField(service,"automaticSplitter",new InboxAutomaticSplitter(inbox,new DocumentExtractionService(),audit));var parts=service.automaticSplit(id,"requesting-user");assertThat(parts).hasSize(2).allMatch(part->part.automaticallySplit()&&part.classification().status().equals("SUGGESTED"));assertThat(source.getStatus()).isEqualTo("SPLIT");assertThat(source.getContent()).isNotEmpty();verify(audit).recordInTransaction(argThat(a->a.getName().equals("requesting-user")),eq("DOCUMENT_INBOX_AUTO_SPLIT"),eq("DOCUMENT_INBOX"),eq(id),anyString());verifyNoInteractions(documents,checks);}
    @Test void automaticSplitRejectsForeignPayloadBeforeReadingIt()throws Exception{var foreign=mock(DocumentInboxItem.class);when(foreign.getTenantId()).thenReturn(UUID.randomUUID());var id=UUID.randomUUID();when(inbox.findForUpdate(id)).thenReturn(Optional.of(foreign));assertThatThrownBy(()->service.automaticSplit(id,"tester")).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);verify(foreign,never()).getContent();}
    @Test void splitKeepsOriginalAndStoresSelectedTypesAndPageOrigins() throws Exception {
        UUID id=UUID.randomUUID();var original=item();original.setContent(PdfDocumentSplitterTest.pdf("COMMERCIAL INVOICE","PACKING LIST"));original.setContentType("application/pdf");original.setOriginalFilename("bundle.pdf");
        when(inbox.findForUpdate(id)).thenReturn(Optional.of(original));when(lcs.findAssignmentTargets()).thenReturn(List.of());
        when(inbox.save(any())).thenAnswer(call->{DocumentInboxItem saved=call.getArgument(0);if(saved.getId()==null)ReflectionTestUtils.setField(saved,"id",UUID.randomUUID());return saved;});
        var actual=new DocumentInboxService(inbox,lcs,documents,new DocumentExtractionService(),checks);
        var training=mock(SplitTrainingService.class);ReflectionTestUtils.setField(actual,"splitTraining",training);
        var result=actual.split(id,List.of(new PdfDocumentSplitter.Part(1,1,DocumentType.COMMERCIAL_INVOICE),new PdfDocumentSplitter.Part(2,2,DocumentType.PACKING_LIST)),"tester");
        assertThat(result).hasSize(2);assertThat(original.getStatus()).isEqualTo("SPLIT");assertThat(original.getContent()).isNotEmpty();
        assertThat(result.get(1).sourceInboxId()).isEqualTo(id);assertThat(result.get(1).sourceFromPage()).isEqualTo(2);assertThat(result.get(1).classification().suggestedType()).isEqualTo(DocumentType.PACKING_LIST);
        assertThat(result.get(0).extractionStatus()).isEqualTo("EXTRACTED");verifyNoInteractions(documents,checks);
        verify(training).confirm(eq(original.getContent()),isNull(),eq(List.of(new PdfDocumentSplitter.Part(1,1,DocumentType.COMMERCIAL_INVOICE),new PdfDocumentSplitter.Part(2,2,DocumentType.PACKING_LIST))),eq("tester"));
        assertThatThrownBy(()->actual.split(id,List.of(new PdfDocumentSplitter.Part(1,1,DocumentType.OTHER),new PdfDocumentSplitter.Part(2,2,DocumentType.OTHER)),"tester")).isInstanceOf(IllegalStateException.class);
    }
    @Test void invalidSplitDoesNotChangeOriginalOrSaveParts() throws Exception {
        UUID id=UUID.randomUUID();var original=item();original.setContent(PdfDocumentSplitterTest.pdf("A","B"));original.setContentType("application/pdf");when(inbox.findForUpdate(id)).thenReturn(Optional.of(original));
        assertThatThrownBy(()->service.split(id,List.of(new PdfDocumentSplitter.Part(1,1,DocumentType.OTHER),new PdfDocumentSplitter.Part(1,2,DocumentType.OTHER)),"tester")).isInstanceOf(IllegalArgumentException.class);
        assertThat(original.getStatus()).isEqualTo("OPEN");verify(inbox,never()).save(any());
    }
    @Test void newCaseCreatesAndAttachesOnlyAfterExplicitRequest(){
        UUID id=UUID.randomUUID(),lcId=UUID.randomUUID();var item=item();when(inbox.findForUpdate(id)).thenReturn(Optional.of(item));
        when(lcs.saveAndFlush(any())).thenAnswer(call->{LetterOfCredit lc=call.getArgument(0);ReflectionTestUtils.setField(lc,"id",lcId);when(lcs.findById(lcId)).thenReturn(Optional.of(lc));assertThat(lc.getReference()).isEqualTo("NEW123");assertThat(lc.getAmount()).isNull();assertThat(lc.getOwnBankReference()).isEqualTo("OWN-123");assertThat(lc.getForeignBankReference()).isEqualTo("FOREIGN-123");return lc;});
        when(documents.save(any())).thenAnswer(call->{LcDocument doc=call.getArgument(0);ReflectionTestUtils.setField(doc,"id",UUID.randomUUID());return doc;});
        var request=new de.ostms.lc.document.api.InboxNewCaseRequest(" NEW123 "," OWN-123 "," FOREIGN-123 ","Applicant","Beneficiary",null,null,null,DocumentType.ANNEX,null);
        var result=service.createCase(id,request);assertThat(result.lcId()).isEqualTo(lcId);assertThat(item.getAttachedLcId()).isEqualTo(lcId);assertThat(item.getContent()).isNull();assertThat(item.getStatus()).isEqualTo("ATTACHED");
    }
    @Test void duplicateReferenceKeepsInboxUntouched(){
        UUID id=UUID.randomUUID();var item=item();when(inbox.findForUpdate(id)).thenReturn(Optional.of(item));when(lcs.existsByReference("EXISTS")).thenReturn(true);
        var request=new de.ostms.lc.document.api.InboxNewCaseRequest("EXISTS",null,null,null,null,null,null,null,DocumentType.ANNEX,null);
        assertThatThrownBy(()->service.createCase(id,request)).isInstanceOf(IllegalArgumentException.class);assertThat(item.getStatus()).isEqualTo("OPEN");assertThat(item.getContent()).isNotNull();verify(lcs,never()).saveAndFlush(any());verifyNoInteractions(documents);
    }
    final DocumentInboxRepository inbox=mock(DocumentInboxRepository.class);
    final LetterOfCreditRepository lcs=mock(LetterOfCreditRepository.class);
    final LcDocumentRepository documents=mock(LcDocumentRepository.class);
    final DocumentExtractionService extraction=mock(DocumentExtractionService.class);
    final DocumentCheckService checks=mock(DocumentCheckService.class);
    final DocumentInboxService service=new DocumentInboxService(inbox,lcs,documents,extraction,checks);

    @Test void referenceSuggestionDoesNotCreateAnLcDocument() throws Exception {
        LetterOfCredit lc=new LetterOfCredit();lc.setReference("LC1234");
        UUID lcId=UUID.randomUUID();ReflectionTestUtils.setField(lc,"id",lcId);
        var target=mock(LetterOfCreditRepository.AssignmentTarget.class);
        when(target.getId()).thenReturn(lcId);when(target.getReference()).thenReturn("LC1234");
        when(lcs.findAssignmentTargets()).thenReturn(List.of(target));
        doAnswer(call->{LcDocument doc=call.getArgument(0);doc.setExtractedReference("LC1234");doc.setExtractionStatus("EXTRACTED");return null;}).when(extraction).extract(any());
        when(inbox.save(any())).thenAnswer(call->call.getArgument(0));
        var result=service.receive(List.of(new MockMultipartFile("file","invoice.txt","text/plain",new byte[]{1})),"user");
        assertThat(result.get(0).suggestedLcId()).isNull();
        assertThat(result.get(0).extractionStatus()).isEqualTo("QUEUED");
        assertThat(result.get(0).status()).isEqualTo("OPEN");
        verifyNoInteractions(documents,checks,extraction);
    }

    @Test void correctedMetadataIsTransferredWithoutChangingLcMasterData(){
        UUID id=UUID.randomUUID(),lcId=UUID.randomUUID();var item=item();item.setExtractedText("Invoice date: 25.08.2026");when(inbox.findForUpdate(id)).thenReturn(Optional.of(item));
        var lc=new LetterOfCredit();lc.setReference("LC-UNCHANGED");ReflectionTestUtils.setField(lc,"id",lcId);when(lcs.findById(lcId)).thenReturn(Optional.of(lc));
        when(documents.save(any())).thenAnswer(call->{LcDocument doc=call.getArgument(0);ReflectionTestUtils.setField(doc,"id",UUID.randomUUID());assertThat(doc.getExtractedReference()).isEqualTo("LC-CORRECTED");assertThat(doc.getExtractedDocumentNumber()).isEqualTo("INV-42");assertThat(doc.getAmount()).isEqualByComparingTo("42.50");assertThat(doc.getCurrency()).isEqualTo("EUR");assertThat(doc.getDocumentDate()).isNull();return doc;});
        service.attach(id,new DocumentInboxAttachRequest(lcId,DocumentType.COMMERCIAL_INVOICE,null,null,new DocumentInboxAttachRequest.Metadata("LC-CORRECTED","INV-42",new java.math.BigDecimal("42.50"),"EUR")));
        assertThat(lc.getReference()).isEqualTo("LC-UNCHANGED");
    }
    @Test void confirmedAssignmentMovesContentAndInvalidatesOldChecks() {
        UUID id=UUID.randomUUID(),lcId=UUID.randomUUID(),documentId=UUID.randomUUID();
        DocumentInboxItem item=item();when(inbox.findForUpdate(id)).thenReturn(Optional.of(item));
        LetterOfCredit lc=new LetterOfCredit();ReflectionTestUtils.setField(lc,"id",lcId);
        when(lcs.findById(lcId)).thenReturn(Optional.of(lc));
        when(documents.save(any())).thenAnswer(call->{LcDocument doc=call.getArgument(0);ReflectionTestUtils.setField(doc,"id",documentId);assertThat(doc.getContent()).containsExactly(1,2);assertThat(doc.getExtractedText()).isEqualTo("original text");assertThat(doc.getLetterOfCredit()).isSameAs(lc);return doc;});
        var result=service.attach(id,new DocumentInboxAttachRequest(lcId,DocumentType.PACKING_LIST,LocalDate.of(2026,10,4)));
        assertThat(result.document().documentType()).isEqualTo(DocumentType.PACKING_LIST);
        assertThat(item.getStatus()).isEqualTo("ATTACHED");assertThat(item.getContent()).isNull();
        assertThat(item.getAttachedDocumentId()).isEqualTo(documentId);verify(checks).invalidateDecisions(lcId);
        assertThatThrownBy(()->service.attach(id,new DocumentInboxAttachRequest(lcId,DocumentType.ANNEX,null))).isInstanceOf(IllegalStateException.class);
        verify(documents,times(1)).save(any());
    }

    @Test void oversizedOrUnsafeBatchIsRejectedBeforeSaving() {
        assertThatThrownBy(()->service.receive(List.of(new MockMultipartFile("file","../invoice.txt","text/plain",new byte[]{1})),"user")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.receive(Collections.nCopies(101,new MockMultipartFile("file","x.txt","text/plain",new byte[]{1})),"user")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.receive(List.of(new MockMultipartFile("file","large.pdf","application/pdf",new byte[10*1024*1024+1])),"user")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(inbox,documents,extraction);
    }

    @Test void zipDocumentsAreStoredSeparatelyWithoutAutomaticAssignment() throws Exception {
        byte[] archive;
        try(var output=new java.io.ByteArrayOutputStream();var zip=new java.util.zip.ZipOutputStream(output)){
            for(String name:List.of("folder/invoice.txt","folder/packing.txt")){
                zip.putNextEntry(new java.util.zip.ZipEntry(name));zip.write(new byte[]{1,2});zip.closeEntry();
            }
            zip.finish();archive=output.toByteArray();
        }
        when(inbox.save(any())).thenAnswer(call->call.getArgument(0));
        var result=service.receive(List.of(new MockMultipartFile("file","batch.zip","application/zip",archive)),"user");
        assertThat(result).extracting("originalFilename").containsExactly("folder/invoice.txt","folder/packing.txt");
        assertThat(result).extracting("status").containsOnly("OPEN");
        verify(inbox,times(2)).save(any());verifyNoInteractions(documents,checks);
    }

    @Test void invalidLaterArchivePreventsSavingEarlierOrdinaryFile() {
        var ordinary=new MockMultipartFile("file","letter.txt","text/plain",new byte[]{1});
        var invalid=new MockMultipartFile("file","bad.zip","application/zip",new byte[]{1,2});
        assertThatThrownBy(()->service.receive(List.of(ordinary,invalid),"user")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(inbox,documents,extraction);
    }

    @Test void attachedFilesCannotBePreviewedOrDeletedFromInbox() {
        UUID id=UUID.randomUUID();DocumentInboxItem item=item();item.setStatus("ATTACHED");
        when(inbox.findById(id)).thenReturn(Optional.of(item));when(inbox.findForUpdate(id)).thenReturn(Optional.of(item));
        assertThatThrownBy(()->service.openItem(id)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->service.delete(id)).isInstanceOf(IllegalStateException.class);
        verify(inbox,never()).delete(any());
    }

    private DocumentInboxItem item(){DocumentInboxItem item=new DocumentInboxItem();item.setOriginalFilename("packing.txt");item.setContentType("text/plain");item.setFileSize(2);item.setContent(new byte[]{1,2});item.setExtractedText("original text");return item;}

    @Test void pendingRecognitionBlocksAttachmentButNotDeletion(){
        var id=UUID.randomUUID();var item=item();item.setExtractionStatus("PROCESSING");when(inbox.findForUpdate(id)).thenReturn(Optional.of(item));
        assertThatThrownBy(()->service.attach(id,new DocumentInboxAttachRequest(UUID.randomUUID(),DocumentType.ANNEX,null))).isInstanceOf(IllegalStateException.class).hasMessageContaining("läuft noch");
        service.delete(id);verify(inbox).delete(item);verifyNoInteractions(documents);
    }
    @Test void failedRecognitionCanRetryWithoutUploadingAgain(){
        var id=UUID.randomUUID();var item=item();item.setExtractionStatus("OCR_TIMEOUT");when(inbox.findForUpdate(id)).thenReturn(Optional.of(item));when(inbox.save(any())).thenAnswer(call->call.getArgument(0));
        assertThat(service.retryExtraction(id).extractionStatus()).isEqualTo("QUEUED");assertThat(item.getContent()).containsExactly(1,2);verifyNoInteractions(extraction);
        assertThatThrownBy(()->service.retryExtraction(id)).isInstanceOf(IllegalStateException.class);
    }
}
