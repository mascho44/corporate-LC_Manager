package de.ostms.lc.document.service;

import de.ostms.lc.document.domain.LcDocument;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DocumentExtractionServiceTest {
    @Test void reportsConfiguredScanPageLimitExplicitlyBeforeStartingTools()throws Exception{
        var service=new DocumentExtractionService();org.springframework.test.util.ReflectionTestUtils.setField(service,"maxOcrPages",1);
        assertThat(service.extractFile(PdfDocumentSplitterTest.pdf("",""),"synthetic.pdf","application/pdf").status()).isEqualTo("OCR_PAGE_LIMIT");
    }
    @Test void groupsOnlyBlankPagesIntoMinimalRenderRanges(){
        var ranges=DocumentExtractionService.blankPageRanges(java.util.List.of("Digital invoice"," ","","Digital packing list",""));
        assertThat(ranges).hasSize(2);assertThat(ranges.get(0)).containsExactly(2,3);assertThat(ranges.get(1)).containsExactly(5,5);
        assertThat(DocumentExtractionService.blankPageRanges(java.util.List.of("Digital only"))).isEmpty();
    }
    @Test void digitalPagesKeepTheirOrderWithoutNeedingOcr()throws Exception{
        var content=PdfDocumentSplitterTest.pdf("COMMERCIAL INVOICE","PACKING LIST");
        assertThat(DocumentExtractionService.readPdfPages(content)).hasSize(2);
        var result=new DocumentExtractionService().extractFile(content,"synthetic.pdf","application/pdf");
        assertThat(result.status()).isEqualTo("EXTRACTED");assertThat(result.text()).contains("COMMERCIAL INVOICE","PACKING LIST");assertThat(result.ocrEvidence().method()).isEqualTo("PDF_TEXT_POSITIONS");assertThat(result.ocrEvidence().words()).allMatch(word->word.confidence()==null);assertThat(result.ocrEvidence().words().stream().map(OcrEvidence.Word::page)).contains(1,2);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"synchronous","background","checkpoint","recognized"})
    void rejectsForeignDocumentBeforeContentAccessOrMetadataMutation(String entryPoint){
        LcDocument foreign;try(var scope=de.ostms.lc.tenant.domain.TenantContext.open(java.util.UUID.randomUUID())){foreign=spy(new LcDocument());}
        foreign.setExtractionStatus("QUEUED");foreign.setExtractedText("Synthetic existing text");foreign.setOcrEvidenceJson("Synthetic existing evidence");foreign.setClassificationHistoryJson("Synthetic existing classification");clearInvocations(foreign);
        var service=new DocumentExtractionService();
        assertThatThrownBy(()->{switch(entryPoint){case "synchronous"->service.extract(foreign);case "background"->service.extractInBackground(foreign);case "checkpoint"->service.extractInBackground(foreign,ignored->{throw new AssertionError("Foreign checkpoint");});default->service.applyRecognizedText(foreign,"Synthetic replacement","EXTRACTED");}}).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verify(foreign,never()).getContent();verify(foreign,never()).setExtractionStatus(any());verify(foreign,never()).setExtractedText(any());verify(foreign,never()).setOcrEvidenceJson(any());verify(foreign,never()).setClassificationHistoryJson(any());
        assertThat(foreign.getExtractionStatus()).isEqualTo("QUEUED");assertThat(foreign.getExtractedText()).isEqualTo("Synthetic existing text");assertThat(foreign.getOcrEvidenceJson()).isEqualTo("Synthetic existing evidence");
    }
    @Test void selectedTenantCanExtractOwnTextAndApplyPageRecognition(){
        var tenant=java.util.UUID.randomUUID();try(var scope=de.ostms.lc.tenant.domain.TenantContext.open(tenant)){
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
