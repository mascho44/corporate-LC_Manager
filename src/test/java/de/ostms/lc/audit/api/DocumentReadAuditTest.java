package de.ostms.lc.audit.api;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.document.api.DocumentController;
import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.document.domain.LcDocument;
import de.ostms.lc.document.service.DocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DocumentReadAuditTest {
 @Test void viewingAndDownloadingADocumentAreRecordedSeparately(){
  var service=mock(DocumentService.class);var audit=mock(AuditService.class);var id=UUID.randomUUID();
  var document=new LcDocument();document.setOriginalFilename("rechnung.pdf");document.setContentType("application/pdf");document.setDocumentType(DocumentType.COMMERCIAL_INVOICE);document.setContent(new byte[]{1});document.setFileSize(1);
  when(service.one(id)).thenReturn(document);
  var controller=new DocumentController(service,audit,null,null);var auth=new TestingAuthenticationToken("markus","x");
  controller.preview(id,auth);controller.download(id,auth);
  verify(audit).record(eq(auth),eq("DOCUMENT_VIEWED"),eq("LC_DOCUMENT"),eq(id),contains("rechnung.pdf"));
  verify(audit).record(eq(auth),eq("DOCUMENT_DOWNLOADED"),eq("LC_DOCUMENT"),eq(id),contains("rechnung.pdf"));
 }
}
