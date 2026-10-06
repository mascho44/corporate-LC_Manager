package de.corporate.lc.document.service;

import de.corporate.lc.document.domain.DocumentType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class PdfDocumentSplitterTest {
    @Test void proposesTypeChangesAndIsolatesUnknownPages() throws Exception {
        var proposal=PdfDocumentSplitter.propose(pdf("COMMERCIAL INVOICE","COMMERCIAL INVOICE","Unknown continuation","PACKING LIST"),null);
        assertThat(proposal.pageCount()).isEqualTo(4);
        assertThat(proposal.parts()).containsExactly(new PdfDocumentSplitter.Part(1,2,DocumentType.COMMERCIAL_INVOICE),new PdfDocumentSplitter.Part(3,3,DocumentType.OTHER),new PdfDocumentSplitter.Part(4,4,DocumentType.PACKING_LIST));
    }
    @Test void completeRangesProduceRealIndependentPdfs() throws Exception {
        var parts=List.of(new PdfDocumentSplitter.Part(1,1,DocumentType.COMMERCIAL_INVOICE),new PdfDocumentSplitter.Part(2,3,DocumentType.PACKING_LIST));
        var output=PdfDocumentSplitter.split(pdf("COMMERCIAL INVOICE","PACKING LIST","Packing details"),null,parts);
        assertThat(output).hasSize(2);
        try(var first=Loader.loadPDF(output.get(0).content());var second=Loader.loadPDF(output.get(1).content())) {
            assertThat(first.getNumberOfPages()).isEqualTo(1);assertThat(second.getNumberOfPages()).isEqualTo(2);
        }
        assertThat(output.get(0).text()).contains("COMMERCIAL INVOICE").doesNotContain("PACKING LIST");
        assertThat(output.get(1).text()).contains("PACKING LIST","Packing details");
    }
    @Test void rejectsOmissionsOverlapsAndInvalidTypes() {
        for(var parts:List.of(List.of(new PdfDocumentSplitter.Part(1,1,DocumentType.OTHER),new PdfDocumentSplitter.Part(3,3,DocumentType.OTHER)),List.of(new PdfDocumentSplitter.Part(1,2,DocumentType.OTHER),new PdfDocumentSplitter.Part(2,3,DocumentType.OTHER)),List.of(new PdfDocumentSplitter.Part(1,1,null),new PdfDocumentSplitter.Part(2,3,DocumentType.OTHER)),List.of(new PdfDocumentSplitter.Part(1,1,DocumentType.OTHER),new PdfDocumentSplitter.Part(2,2,DocumentType.OTHER))))
            assertThatThrownBy(()->PdfDocumentSplitter.validate(parts,3)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void scansReuseOcrEvidenceAndRemapCoordinates() throws Exception {
        var evidence=new OcrEvidence("test","test",200,.8,List.of(new OcrEvidence.Word("PACKING LIST",.9,2,20,30,100,10)));
        String json=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(evidence);
        var content=pdf("","");var proposal=PdfDocumentSplitter.propose(content,json);
        assertThat(proposal.pages().get(1).classification().suggestedType()).isEqualTo(DocumentType.PACKING_LIST);
        var result=PdfDocumentSplitter.split(content,json,List.of(new PdfDocumentSplitter.Part(1,1,DocumentType.OTHER),new PdfDocumentSplitter.Part(2,2,DocumentType.PACKING_LIST)));
        assertThat(result.get(1).text()).contains("PACKING LIST");assertThat(result.get(1).evidence().words().get(0).page()).isEqualTo(1);
        assertThat(result.get(0).evidence().words()).isEmpty();
    }
    static byte[] pdf(String... texts) throws Exception {
        try(var pdf=new PDDocument();var output=new ByteArrayOutputStream()) {
            for(String text:texts){var page=new PDPage();pdf.addPage(page);try(var stream=new PDPageContentStream(pdf,page)){stream.beginText();stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12);stream.newLineAtOffset(30,700);stream.showText(text);stream.endText();}}
            pdf.save(output);return output.toByteArray();
        }
    }
}
