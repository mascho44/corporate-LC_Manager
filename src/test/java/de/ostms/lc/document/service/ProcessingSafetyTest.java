package de.ostms.lc.document.service;
import org.junit.jupiter.api.Test;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import static org.assertj.core.api.Assertions.*;
class ProcessingSafetyTest {
    @Test void distinguishesMissingProgramFromFailedProcessing(){
        assertThatThrownBy(()->BoundedProcess.run(new ProcessBuilder("/nonexistent/lc-ocr-tool"),1))
            .isInstanceOf(BoundedProcess.UnavailableException.class);
        assertThatThrownBy(()->BoundedProcess.run(new ProcessBuilder("sh","-c","exit 7"),1))
            .isExactlyInstanceOf(java.io.IOException.class).hasMessageContaining("Exit 7");
    }
    @Test void acceptsStandardPageButRejectsHugePage()throws Exception{
        try(var pdf=new PDDocument()){pdf.addPage(new PDPage(PDRectangle.A4));PdfProcessingSafety.validate(pdf);
            pdf.addPage(new PDPage(new PDRectangle(10000,10000)));
            assertThatThrownBy(()->PdfProcessingSafety.validate(pdf)).isInstanceOf(java.io.IOException.class);}
    }
    @Test void boundsConcurrentProcessing()throws Exception{
        try(var first=PdfProcessingSafety.acquire();var second=PdfProcessingSafety.acquire()){
            assertThatThrownBy(PdfProcessingSafety::acquire).isInstanceOf(java.io.IOException.class);
        }
        try(var available=PdfProcessingSafety.acquire()){assertThat(available).isNotNull();}
    }
    @Test void terminatesTimedOutChild(){
        assertThatThrownBy(()->BoundedProcess.run(new ProcessBuilder("sh","-c","exec sleep 30"),1))
            .isInstanceOf(BoundedProcess.TimeoutException.class).hasMessageContaining("Zeitlimit");
    }
    @Test void allowsSlowerOcrPagesButBoundsConfiguration()throws Exception{
        var service=new DocumentExtractionService();
        assertThat(service.pageTimeoutSeconds()).isEqualTo(120);
        var field=DocumentExtractionService.class.getDeclaredField("ocrPageTimeoutSeconds");field.setAccessible(true);
        field.setLong(service,999);assertThat(service.pageTimeoutSeconds()).isEqualTo(300);
        field.setLong(service,-1);assertThat(service.pageTimeoutSeconds()).isEqualTo(1);
    }
    @Test void multiPageOcrHasSeparateBoundedDocumentBudget()throws Exception{
        var service=new DocumentExtractionService();assertThat(service.documentTimeoutSeconds()).isEqualTo(900);
        var field=DocumentExtractionService.class.getDeclaredField("ocrDocumentTimeoutSeconds");field.setAccessible(true);
        field.setLong(service,99999);assertThat(service.documentTimeoutSeconds()).isEqualTo(1800);
        field.setLong(service,-1);assertThat(service.documentTimeoutSeconds()).isEqualTo(60);
        assertThat(service.pageTimeoutSeconds()).isEqualTo(120);
    }
    @Test void ignoresLargeStandardOutputInsteadOfBlocking()throws Exception{
        BoundedProcess.run(new ProcessBuilder("sh","-c","i=0; while [ $i -lt 10000 ]; do printf 'abcdefghijklmnopqrstuvwxyz\\n'; i=$((i+1)); done"),5);
    }
}
