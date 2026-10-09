package de.ostms.lc.document.service;

import org.apache.pdfbox.Loader;
import org.springframework.stereotype.Service;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;

/** Small raster pages, no OCR and no browser PDF plug-in. */
@Service
public class PdfPagePreviewService {
 public byte[] markedCrop(byte[] content,SpatialMetadata.Field field)throws Exception{
  if(field.page()<1||field.left()<0||field.top()<0||field.width()<1||field.height()<1||field.width()>1800||field.height()>1200)throw new IllegalArgumentException("Fundstelle außerhalb der Vorschaugrenzen.");
  try(var slot=PdfProcessingSafety.acquire();var pdf=Loader.loadPDF(content)){
   PdfProcessingSafety.validate(pdf);if(field.page()>pdf.getNumberOfPages())throw new IllegalArgumentException("PDF-Seite nicht vorhanden.");
   var page=pdf.getPage(field.page()-1);var crop=page.getCropBox();var media=page.getMediaBox();
   if(page.getRotation()%360!=0||crop.getLowerLeftX()!=media.getLowerLeftX()||crop.getLowerLeftY()!=media.getLowerLeftY()||crop.getWidth()!=media.getWidth()||crop.getHeight()!=media.getHeight())throw new IllegalArgumentException("Markierung bei gedrehten oder beschnittenen Seiten noch nicht verifiziert. Bitte Original öffnen.");
   var directory=Files.createTempDirectory("lc-field-preview-");var source=directory.resolve("source.pdf");var prefix=directory.resolve("crop");var result=directory.resolve("crop.png");
   int x=Math.max(0,field.left()-30),y=Math.max(0,field.top()-30),width=Math.min(1900,field.width()+60),height=Math.min(1300,field.height()+60);
   try{
    Files.write(source,content);BoundedProcess.run(new ProcessBuilder("pdftoppm","-png","-singlefile","-r","200","-cropbox","-f",Integer.toString(field.page()),"-l",Integer.toString(field.page()),"-x",Integer.toString(x),"-y",Integer.toString(y),"-W",Integer.toString(width),"-H",Integer.toString(height),source.toString(),prefix.toString()),30);
    if(Files.size(result)>8_000_000)throw new IOException("Ausschnitt zu groß.");var image=ImageIO.read(result.toFile());if(image==null)throw new IOException("Ausschnitt nicht verfügbar.");
    try{var graphics=image.createGraphics();try{graphics.setColor(new java.awt.Color(220,120,0));graphics.setStroke(new java.awt.BasicStroke(3));graphics.drawRect(field.left()-x,field.top()-y,Math.min(field.width(),image.getWidth()-1),Math.min(field.height(),image.getHeight()-1));}finally{graphics.dispose();}var bytes=new java.io.ByteArrayOutputStream();ImageIO.write(image,"png",bytes);return bytes.toByteArray();}finally{image.flush();}
   }finally{Files.deleteIfExists(result);Files.deleteIfExists(source);Files.deleteIfExists(directory);}
  }
 }
 public int pageCount(byte[] content)throws Exception{
  try(var slot=PdfProcessingSafety.acquire();var pdf=Loader.loadPDF(content)){PdfProcessingSafety.validate(pdf);return pdf.getNumberOfPages();}
 }
 public byte[] render(byte[] content,int page,boolean enlarged)throws Exception{
  try(var slot=PdfProcessingSafety.acquire();var pdf=Loader.loadPDF(content)){
   PdfProcessingSafety.validate(pdf);
   if(page<1||page>pdf.getNumberOfPages())throw new IllegalArgumentException("PDF-Seite nicht vorhanden.");
   int max=enlarged?1400:560;
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
