package de.corporate.lc.document.service;
import org.junit.jupiter.api.Test;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import static org.assertj.core.api.Assertions.*;
class ProcessingSafetyTest {
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
            .isInstanceOf(java.io.IOException.class).hasMessageContaining("Zeitlimit");
    }
    @Test void ignoresLargeStandardOutputInsteadOfBlocking()throws Exception{
        BoundedProcess.run(new ProcessBuilder("sh","-c","i=0; while [ $i -lt 10000 ]; do printf 'abcdefghijklmnopqrstuvwxyz\\n'; i=$((i+1)); done"),5);
    }
}
