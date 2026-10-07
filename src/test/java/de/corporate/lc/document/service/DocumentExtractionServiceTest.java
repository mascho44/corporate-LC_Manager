package de.corporate.lc.document.service;

import de.corporate.lc.document.domain.LcDocument;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DocumentExtractionServiceTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"synchronous","background","recognized"})
    void rejectsForeignDocumentBeforeContentAccessOrMetadataMutation(String entryPoint){
        LcDocument foreign;try(var scope=de.corporate.lc.tenant.domain.TenantContext.open(java.util.UUID.randomUUID())){foreign=spy(new LcDocument());}
        foreign.setExtractionStatus("QUEUED");foreign.setExtractedText("Synthetic existing text");foreign.setOcrEvidenceJson("Synthetic existing evidence");foreign.setClassificationHistoryJson("Synthetic existing classification");clearInvocations(foreign);
        var service=new DocumentExtractionService();
        assertThatThrownBy(()->{switch(entryPoint){case "synchronous"->service.extract(foreign);case "background"->service.extractInBackground(foreign);default->service.applyRecognizedText(foreign,"Synthetic replacement","EXTRACTED");}}).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verify(foreign,never()).getContent();verify(foreign,never()).setExtractionStatus(any());verify(foreign,never()).setExtractedText(any());verify(foreign,never()).setOcrEvidenceJson(any());verify(foreign,never()).setClassificationHistoryJson(any());
        assertThat(foreign.getExtractionStatus()).isEqualTo("QUEUED");assertThat(foreign.getExtractedText()).isEqualTo("Synthetic existing text");assertThat(foreign.getOcrEvidenceJson()).isEqualTo("Synthetic existing evidence");
    }
    @Test void selectedTenantCanExtractOwnTextAndApplyPageRecognition(){
        var tenant=java.util.UUID.randomUUID();try(var scope=de.corporate.lc.tenant.domain.TenantContext.open(tenant)){
            var document=new LcDocument();document.setOriginalFilename("synthetic.txt");document.setContentType("text/plain");document.setContent("COMMERCIAL INVOICE\nInvoice No: SYNTHETIC-1".getBytes(StandardCharsets.UTF_8));
            var service=new DocumentExtractionService();service.extractInBackground(document);assertThat(document.getExtractionStatus()).isEqualTo("EXTRACTED");assertThat(document.getTenantId()).isEqualTo(tenant);
            service.applyRecognizedText(document,"PACKING LIST\nInvoice No: SYNTHETIC-2","EXTRACTED");assertThat(document.getExtractedDocumentNumber()).isEqualTo("SYNTHETIC-2");
        }
    }
    @Test void extractsInvoiceMetadataFromTextDocument() {
        String text = """
                COMMERCIAL INVOICE
                Invoice No: INV-2026-104
                LC Reference: LC2026004711
                Grand Total: EUR 125,000.00
                """;
        LcDocument document = new LcDocument();
        document.setOriginalFilename("invoice.txt");
        document.setContentType("text/plain");
        document.setContent(text.getBytes(StandardCharsets.UTF_8));

        new DocumentExtractionService().extract(document);

        assertThat(document.getExtractionStatus()).isEqualTo("EXTRACTED");
        assertThat(document.getExtractedDocumentNumber()).isEqualTo("INV-2026-104");
        assertThat(document.getExtractedReference()).isEqualTo("LC2026004711");
        assertThat(document.getExtractedCurrency()).isEqualTo("EUR");
        assertThat(document.getExtractedAmount()).isEqualByComparingTo(new BigDecimal("125000.00"));
    }
}
