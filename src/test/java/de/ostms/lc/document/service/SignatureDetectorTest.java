package de.ostms.lc.document.service;

import org.junit.jupiter.api.Test;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class SignatureDetectorTest {
 private static BufferedImage page(){var image=new BufferedImage(1654,2339,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,1654,2339);g.dispose();return image;}
 private static void scribble(BufferedImage image,int x,int y){
  var g=image.createGraphics();g.setColor(Color.BLACK);g.setStroke(new BasicStroke(4f));g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
  var path=new java.awt.geom.Path2D.Double();path.moveTo(x,y+60);path.curveTo(x+40,y-10,x+80,y+120,x+130,y+30);path.curveTo(x+170,y-20,x+210,y+90,x+280,y+20);path.lineTo(x+330,y+70);
  g.draw(path);g.drawLine(x+20,y+90,x+300,y+40);g.dispose();
 }
 private static void printedRule(BufferedImage image,int x,int y,int width){var g=image.createGraphics();g.setColor(Color.BLACK);g.fillRect(x,y,width,3);g.dispose();}
 private static OcrEvidence.Word word(String text,double confidence,int left,int top,int width){return new OcrEvidence.Word(text,confidence,1,left,top,width,30);}

 @Test void inkAboveTheCaptionIsFoundAsASignature(){
  var image=page();scribble(image,300,1500);printedRule(image,290,1640,420);
  var result=SignatureDetector.analyze(image,List.of(word("Signature",.95,300,1660,160)),1);
  assertThat(result.anchors()).hasSize(1);assertThat(result.anchors().get(0).inkFound()).isTrue();
  assertThat(result.anchors().get(0).ink().top()).isBetween(1480,1560);
 }
 @Test void emptyFieldWithOnlyARuleAndPrintedTextIsReportedEmpty(){
  var image=page();printedRule(image,290,1640,420);
  var g=image.createGraphics();g.setColor(Color.BLACK);g.setFont(new Font("SansSerif",Font.PLAIN,28));var metrics=g.getFontMetrics();
  g.drawString("Signature",300,1690);g.dispose();
  var caption=new OcrEvidence.Word("Signature",.95,1,300,1690-metrics.getAscent(),metrics.stringWidth("Signature"),metrics.getHeight());
  var result=SignatureDetector.analyze(image,List.of(caption),1);
  assertThat(result.anchors()).hasSize(1);assertThat(result.anchors().get(0).inkFound()).isFalse();
 }
 @Test void confidentlyReadTextIsNeverInkButLowConfidenceScribbleIs(){
  var image=page();var g=image.createGraphics();g.setColor(Color.BLACK);var font=new Font("SansSerif",Font.BOLD,40);g.setFont(font);
  // Box sizes come from the real font metrics so the test does not depend on the platform's font widths.
  var metrics=g.getFontMetrics();int x=300,baseline=1560;
  g.drawString("COMMERCIAL INVOICE",x,baseline);
  int widthA=metrics.stringWidth("COMMERCIAL"),widthB=metrics.stringWidth("INVOICE"),space=metrics.stringWidth(" "),top=baseline-metrics.getAscent(),height=metrics.getHeight();
  g.dispose();
  var printed=List.of(new OcrEvidence.Word("COMMERCIAL",.97,1,x,top,widthA,height),new OcrEvidence.Word("INVOICE",.97,1,x+widthA+space,top,widthB,height),word("Signature",.95,300,1660,160));
  var printedOnly=SignatureDetector.analyze(image,printed,1);
  assertThat(printedOnly.anchors().get(0).inkFound()).isFalse();
  var signed=page();scribble(signed,300,1500);
  // OCR often reads a scribble as junk with low confidence; that must not hide the ink.
  var withJunk=SignatureDetector.analyze(signed,List.of(word("~/\\_",.2,300,1500,330),word("Signature",.95,300,1660,160)),1);
  assertThat(withJunk.anchors().get(0).inkFound()).isTrue();
 }
 @Test void pageWithoutCaptionOnlyCountsFreeInkInTheLowerPart(){
  var image=page();scribble(image,1000,1900);
  var result=SignatureDetector.analyze(image,List.of(),1);
  assertThat(result.anchors()).isEmpty();assertThat(result.freeInkClusters()).isEqualTo(1);
  var top=page();scribble(top,1000,200);assertThat(SignatureDetector.analyze(top,List.of(),1).freeInkClusters()).isZero();
 }
 @Test void signedForOnBehalfCaptionIsAnAnchor(){
  var image=page();scribble(image,300,1500);
  var result=SignatureDetector.analyze(image,List.of(word("Signed",.95,300,1660,90),word("for",.95,400,1660,50)),1);
  assertThat(result.anchors()).hasSize(1);assertThat(result.anchors().get(0).inkFound()).isTrue();
 }
}
