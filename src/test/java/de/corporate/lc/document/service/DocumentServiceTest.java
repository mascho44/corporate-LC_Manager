package de.corporate.lc.document.service;

import de.corporate.lc.document.domain.DocumentType;
import de.corporate.lc.document.domain.LcDocument;
import de.corporate.lc.document.repository.LcDocumentRepository;
import de.corporate.lc.lc.domain.LetterOfCredit;
import de.corporate.lc.lc.repository.LetterOfCreditRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DocumentServiceTest {
    @Test void importsZipEntriesAndClassifiesUnknownFilesAsAnnex()throws Exception{
        UUID lcId=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setReference("LC-ZIP-1");
        LcDocumentRepository documents=mock(LcDocumentRepository.class);when(documents.save(any())).thenAnswer(call->call.getArgument(0));
        LetterOfCreditRepository lcs=mock(LetterOfCreditRepository.class);when(lcs.findById(lcId)).thenReturn(Optional.of(lc));
        DocumentExtractionService extraction=mock(DocumentExtractionService.class);
        DocumentService service=new DocumentService(documents,lcs,extraction);
        var result=service.uploadArchive(lcId,new MockMultipartFile("file","akte.zip","application/zip",zip()));
        assertThat(result).extracting("documentType").containsExactly(DocumentType.COMMERCIAL_INVOICE,DocumentType.ANNEX);
        verify(documents,times(2)).save(any());
    }
    private byte[] zip()throws Exception{try(ByteArrayOutputStream out=new ByteArrayOutputStream();ZipOutputStream zip=new ZipOutputStream(out)){zip.putNextEntry(new ZipEntry("Dokumente/invoice-17.txt"));zip.write("Invoice".getBytes(StandardCharsets.UTF_8));zip.closeEntry();zip.putNextEntry(new ZipEntry("Anlagen/notes.txt"));zip.write("Notes".getBytes(StandardCharsets.UTF_8));zip.closeEntry();zip.finish();return out.toByteArray();}}
}
