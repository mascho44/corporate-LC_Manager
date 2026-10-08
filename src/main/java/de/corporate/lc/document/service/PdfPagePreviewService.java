package de.corporate.lc.document.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;

/** Small raster pages, no OCR and no browser PDF plug-in. */
@Service
public class PdfPagePreviewService {
 public byte[] render(byte[] content,int page,boolean enlarged)throws Exception{
  try(var slot=PdfProcessingSafety.acquire();var pdf=Loader.loadPDF(content)){
   PdfProcessingSafety.validate(pdf);
   if(page<1||page>pdf.getNumberOfPages())throw new IllegalArgumentException("PDF-Seite nicht vorhanden.");
   var box=pdf.getPage(page-1).getCropBox();float max=enlarged?1400:280;
   float scale=Math.min(enlarged?2f:1f,max/Math.max(box.getWidth(),box.getHeight()));
   var renderer=new PDFRenderer(pdf);renderer.setSubsamplingAllowed(true);
   var image=renderer.renderImage(page-1,scale,org.apache.pdfbox.rendering.ImageType.RGB);
   try(var output=new ByteArrayOutputStream()){ImageIO.write(image,"png",output);image.flush();return output.toByteArray();}
  }
 }
}
