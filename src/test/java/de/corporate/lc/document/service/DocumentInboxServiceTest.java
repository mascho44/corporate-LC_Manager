package de.corporate.lc.document.service;

import de.corporate.lc.check.service.DocumentCheckService;
import de.corporate.lc.document.api.DocumentInboxAttachRequest;
import de.corporate.lc.document.domain.*;
import de.corporate.lc.document.repository.*;
import de.corporate.lc.lc.domain.LetterOfCredit;
import de.corporate.lc.lc.repository.LetterOfCreditRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DocumentInboxServiceTest {
    final DocumentInboxRepository inbox=mock(DocumentInboxRepository.class);
    final LetterOfCreditRepository lcs=mock(LetterOfCreditRepository.class);
    final LcDocumentRepository documents=mock(LcDocumentRepository.class);
    final DocumentExtractionService extraction=mock(DocumentExtractionService.class);
    final DocumentCheckService checks=mock(DocumentCheckService.class);
    final DocumentInboxService service=new DocumentInboxService(inbox,lcs,documents,extraction,checks);

    @Test void referenceSuggestionDoesNotCreateAnLcDocument() throws Exception {
        LetterOfCredit lc=new LetterOfCredit();lc.setReference("LC1234");
        UUID lcId=UUID.randomUUID();ReflectionTestUtils.setField(lc,"id",lcId);
        when(lcs.findByReference("LC1234")).thenReturn(Optional.of(lc));
        doAnswer(call->{LcDocument doc=call.getArgument(0);doc.setExtractedReference("LC1234");doc.setExtractionStatus("EXTRACTED");return null;}).when(extraction).extract(any());
        when(inbox.save(any())).thenAnswer(call->call.getArgument(0));
        var result=service.receive(List.of(new MockMultipartFile("file","invoice.txt","text/plain",new byte[]{1})),"user");
        assertThat(result.get(0).suggestedLcId()).isEqualTo(lcId);
        assertThat(result.get(0).status()).isEqualTo("OPEN");
        verifyNoInteractions(documents,checks);
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
}
