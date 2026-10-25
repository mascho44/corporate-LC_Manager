package de.ostms.lc.document.service;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class SpatialMetadataTest {
 OcrEvidence.Word word(String text,int x,int y){return new OcrEvidence.Word(text,null,1,x,y,text.length()*10,20);}
 OcrEvidence evidence(OcrEvidence.Word... words){return new OcrEvidence("test","PDF_TEXT_POSITIONS",200,.8,List.of(words));}
 @Test void usesPositionsInsteadOfInputOrderAndSeparatesColumns(){
  var result=SpatialMetadata.detect(evidence(word("INV999",180,10),word("Invoice",0,10),word("number:",80,10),word("Invoice",400,10),word("date:",480,10),word("26.09.2026",560,10)));
  assertThat(result.get("documentNumber").value()).isEqualTo("INV999");assertThat(result.get("documentDate").value()).isEqualTo("2026-09-26");assertThat(result.get("documentDate").left()).isEqualTo(560);
 }
 @Test void conflictingLocationsAndAmbiguousDatesRemainOpen(){
  var result=SpatialMetadata.detect(evidence(word("Invoice",0,10),word("number:",80,10),word("INV123",180,10),word("Invoice",0,70),word("number:",80,70),word("INV999",180,70),word("Invoice",0,130),word("date:",80,130),word("05/08/2026",180,130)));
  assertThat(result).isEmpty();
 }
 @Test void recognizesDateBelowLabelAcrossTwoLines(){
  var result=SpatialMetadata.detect(evidence(word("Invoice",0,10),word("date:",80,10),word("25",0,40),word("August",30,40),word("2026",0,70)));
  assertThat(result.get("documentDate").value()).isEqualTo("2026-08-25");assertThat(result.get("documentDate").height()).isEqualTo(50);
 }
 @Test void markedCropRendersAnActualHighlightAndRejectsUnboundedBoxes()throws Exception{
  var pdf=PdfDocumentSplitterTest.pdf("Invoice number: INV999");var service=new PdfPagePreviewService();
  assertThatThrownBy(()->service.markedCrop(pdf,new SpatialMetadata.Field("INV999",1,0,0,999999,50,"test","REVIEW"))).isInstanceOf(IllegalArgumentException.class);
  try{var field=SpatialMetadata.detect(new OcrEvidence("PDFBOX","PDF_TEXT_POSITIONS",200,.8,PdfWordPositions.read(pdf))).get("documentNumber");var png=service.markedCrop(pdf,field);var image=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));assertThat(image).isNotNull();boolean colored=false;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){int color=image.getRGB(x,y);if(((color>>16)&255)>180&&((color>>8)&255)>70&&((color>>8)&255)<170&&(color&255)<40)colored=true;}assertThat(colored).isTrue();image.flush();}catch(BoundedProcess.UnavailableException unavailable){org.junit.jupiter.api.Assumptions.assumeTrue(false,"Poppler unavailable");}
 }
 @Test void digitalPdfRecordsBoxesWithoutInventingOcrConfidence()throws Exception{
  var words=PdfWordPositions.read(PdfDocumentSplitterTest.pdf("Invoice number: INV999"));assertThat(words).isNotEmpty().allMatch(w->w.page()==1&&w.width()>0&&w.confidence()==null);
  assertThat(words.stream().map(OcrEvidence.Word::text)).contains("INV999");
 }
}
