package de.ostms.lc.document.service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class ScanGeometryTest {
 @Test void correctedOcrIsAcceptedWithOriginalCoordinatesAndPreservedText()throws Exception{
  class FakeOcr extends DocumentExtractionService{
   @Override void runOcrStep(ProcessBuilder builder,long seconds)throws java.io.IOException{
    var args=builder.command();
    if(args.get(0).equals("pdftoppm")){
     var image=blank(200,300);javax.imageio.ImageIO.write(image,"png",java.nio.file.Path.of(args.get(args.size()-1)+"-1.png").toFile());image.flush();
    }else if(args.contains("osd")){
     java.nio.file.Files.writeString(java.nio.file.Path.of(args.get(2)+".osd"),"Rotate: 90\nOrientation confidence: 12\n");
    }else{
     boolean alternative=args.contains("11");
     java.nio.file.Files.writeString(java.nio.file.Path.of(args.get(2)+".txt"),alternative?"COMMERCIAL INVOICE\nInvoice No: SYN-42":"noisy text");
     var tsv=new StringBuilder();
     for(String token:List.of("COMMERCIAL","INVOICE","SYN-42","TOTAL")){
      tsv.append("5\t1\t1\t1\t1\t1\t").append(alternative?"240\t20\t30\t40\t99\t":"20\t30\t40\t30\t10\t").append(token).append('\n');
     }
     java.nio.file.Files.writeString(java.nio.file.Path.of(args.get(2)+".tsv"),tsv.toString());
    }
   }
  }
  var result=new FakeOcr().extractFile(PdfDocumentSplitterTest.pdf(""),"synthetic.pdf","application/pdf");
  assertThat(result.status()).isEqualTo("OCR_EXTRACTED");assertThat(result.text()).contains("COMMERCIAL INVOICE");
  assertThat(result.ocrEvidence().pages().get(0).correctionDegrees()).isEqualTo(90);
  // Positions are mapped back to the original scan raster (300 DPI) and then stored in the 200-DPI evidence raster (x 2/3).
  assertThat(result.ocrEvidence().words()).allMatch(w->w.left()==13&&w.top()==20&&w.width()==27&&w.height()==20);
  var split=PdfDocumentSplitter.split(PdfDocumentSplitterTest.pdf(""),new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(result.ocrEvidence()),List.of(new PdfDocumentSplitter.Part(1,1,de.ostms.lc.document.domain.DocumentType.COMMERCIAL_INVOICE)));
  assertThat(split.get(0).text()).contains("COMMERCIAL INVOICE\nInvoice No: SYN-42");
 }
 private BufferedImage blank(int width,int height){
  var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
  var graphics=image.createGraphics();graphics.setColor(Color.WHITE);graphics.fillRect(0,0,width,height);graphics.dispose();return image;
 }
 @ParameterizedTest @ValueSource(doubles={0,90,180,270,-3,3})
 void inverseBoxesCoverOriginalAfterRotation(double degrees)throws Exception{
  var source=blank(200,300);var prepared=ScanGeometry.rotate(source,degrees);
  var forward=prepared.inverse().createInverse();
  var box=forward.createTransformedShape(new Rectangle(20,30,40,30)).getBounds2D();
  int left=(int)Math.floor(box.getMinX()),top=(int)Math.floor(box.getMinY());
  var word=new OcrEvidence.Word("SYNTHETIC",.9,2,left,top,(int)Math.ceil(box.getMaxX())-left,(int)Math.ceil(box.getMaxY())-top);
  var mapped=prepared.map(List.of(word)).get(0);
  assertThat(mapped.left()).isBetween(0,20);assertThat(mapped.top()).isBetween(0,30);
  assertThat(mapped.left()+mapped.width()).isGreaterThanOrEqualTo(60);assertThat(mapped.top()+mapped.height()).isGreaterThanOrEqualTo(60);
  assertThat(mapped.page()).isEqualTo(2);assertThat(mapped.confidence()).isEqualTo(.9);
  if(degrees%90==0){assertThat(mapped.width()).isBetween(40,42);assertThat(mapped.height()).isBetween(30,32);}
  prepared.image().flush();source.flush();
 }
 @Test void onlyConfidentOrientationReportsAreUsed(){
  assertThat(ScanGeometry.orientation("Rotate: 90\nOrientation confidence: 12.5\n")).isEqualTo(90);
  assertThat(ScanGeometry.orientation("Rotate: 270\nOrientation confidence: 2\n")).isZero();
  assertThat(ScanGeometry.orientation("Rotate: 13\nOrientation confidence: 12\n")).isZero();
  assertThat(ScanGeometry.orientation("noise")).isZero();
 }
 @Test void blankAndStraightScansAreNotSkewedAndTiltIsRecovered()throws Exception{
  var source=blank(600,800);assertThat(ScanGeometry.deskewAngle(source)).isZero();
  var graphics=source.createGraphics();graphics.setColor(Color.BLACK);
  for(int y=80;y<720;y+=40)for(int x=60;x<520;x+=40)graphics.fillRect(x,y,25,4);
  graphics.dispose();assertThat(ScanGeometry.deskewAngle(source)).isZero();
  var tilted=ScanGeometry.rotate(source,3);
  assertThat(ScanGeometry.deskewAngle(tilted.image())).isBetween(-3.5,-2.5);
  tilted.image().flush();source.flush();
 }
 @Test void limitsPreventUnboundedRasterAllocation(){
  assertThatThrownBy(()->ScanGeometry.validate(10000,10000)).isInstanceOf(java.io.IOException.class);
  assertThatThrownBy(()->ScanGeometry.rotate(blank(10,10),Double.NaN)).isInstanceOf(java.io.IOException.class);
 }
 @Test void transformedPageTextKeepsReadingOrderOnResume()throws Exception{
  var evidence=new OcrEvidence("synthetic","test",200,.8,List.of(),List.of(new OcrEvidence.PageResult(1,"OCR_EXTRACTED",2,90,"Invoice date: 31.08.2026\nInvoice No: SYN-42")));
  var json=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(evidence);
  assertThat(DocumentExtractionService.readEvidence(json).completedPageText(1)).isEqualTo("Invoice date: 31.08.2026\nInvoice No: SYN-42");
 }
 @Test void progressViewDoesNotExposeInternalRecognizedText()throws Exception{
  var item=new de.ostms.lc.document.domain.DocumentInboxItem();item.setOcrEvidenceJson(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(new OcrEvidence("synthetic","test",200,.8,List.of(),List.of(new OcrEvidence.PageResult(1,"OCR_EXTRACTED",2,90,"SYNTHETIC PRIVATE PAGE TEXT")))));
  var view=de.ostms.lc.document.api.DocumentInboxItemView.from(item,List.of());
  assertThat(view.recognitionPages()).hasSize(1);assertThat(view.recognitionPages().get(0).recognizedText()).isNull();assertThat(view.recognitionPages().get(0).correctionDegrees()).isEqualTo(90);
 }
}
