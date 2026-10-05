package de.corporate.lc.document.service;
import org.apache.pdfbox.pdmodel.PDDocument;
import java.io.IOException;
import java.util.concurrent.Semaphore;

public final class PdfProcessingSafety {
    private static final Semaphore SLOTS=new Semaphore(2);
    private PdfProcessingSafety(){}
    public static AutoCloseable acquire() throws IOException {
        if(!SLOTS.tryAcquire())throw new IOException("Dokumentenverarbeitung ausgelastet. Bitte später erneut versuchen.");
        return SLOTS::release;
    }
    public static void validate(PDDocument document) throws IOException {
        if(document.getNumberOfPages()>200)throw new IOException("PDF enthält mehr als 200 Seiten.");
        for(var page:document.getPages()){
            var box=page.getCropBox();double scale=200d/72d;
            double width=box.getWidth()*scale,height=box.getHeight()*scale;
            if(!Double.isFinite(width)||!Double.isFinite(height)||width<=0||height<=0||width*height>12_000_000)
                throw new IOException("PDF-Seitengröße überschreitet das sichere Verarbeitungslimit.");
        }
    }
}
