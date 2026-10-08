package de.corporate.lc.document.service;

import org.apache.pdfbox.Loader;
import org.springframework.stereotype.Service;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;

/** Small raster pages, no OCR and no browser PDF plug-in. */
@Service
public class PdfPagePreviewService {
 public byte[] render(byte[] content,int page,boolean enlarged)throws Exception{
  try(var slot=PdfProcessingSafety.acquire();var pdf=Loader.loadPDF(content)){
   PdfProcessingSafety.validate(pdf);
   if(page<1||page>pdf.getNumberOfPages())throw new IllegalArgumentException("PDF-Seite nicht vorhanden.");
   int max=enlarged?1400:280;
   // Poppler also decodes JPEG2000 scans without an optional Java image reader.
   var directory=Files.createTempDirectory("lc-page-preview-");
   var source=directory.resolve("source.pdf");
   var prefix=directory.resolve("page");
   var result=directory.resolve("page.png");
   try{
    Files.write(source,content);
    BoundedProcess.run(new ProcessBuilder("pdftoppm","-png","-singlefile","-f",Integer.toString(page),"-l",Integer.toString(page),"-cropbox","-scale-to",Integer.toString(max),source.toString(),prefix.toString()),30);
    if(Files.size(result)>8_000_000)throw new IOException("PDF-Vorschau ist zu groß.");
    byte[] png=Files.readAllBytes(result);
    var image=ImageIO.read(new ByteArrayInputStream(png));
    if(image==null)throw new IOException("PDF-Vorschau konnte nicht erzeugt werden.");
    try{
     if(image.getWidth()>max||image.getHeight()>max)throw new IOException("PDF-Vorschau überschreitet das Größenlimit.");
    }finally{image.flush();}
    return png;
   }finally{
    Files.deleteIfExists(result);
    Files.deleteIfExists(source);
    Files.deleteIfExists(directory);
   }
  }
 }
}
