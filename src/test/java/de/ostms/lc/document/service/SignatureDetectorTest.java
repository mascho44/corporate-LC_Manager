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
  var g=image.createGraphics();g.setColor(Color.BLACK);g.setFont(new Font("SansSerif",Font.PLAIN,28));g.drawString("Signature",300,1690);g.dispose();
  var result=SignatureDetector.analyze(image,List.of(word("Signature",.95,300,1660,160)),1);
  assertThat(result.anchors()).hasSize(1);assertThat(result.anchors().get(0).inkFound()).isFalse();
 }
 @Test void confidentlyReadTextIsNeverInkButLowConfidenceScribbleIs(){
  var image=page();var g=image.createGraphics();g.setColor(Color.BLACK);g.setFont(new Font("SansSerif",Font.BOLD,40));g.drawString("COMMERCIAL INVOICE",300,1560);g.dispose();
  var printedOnly=SignatureDetector.analyze(image,List.of(word("COMMERCIAL",.97,300,1530,230),word("INVOICE",.97,540,1530,200),word("Signature",.95,300,1660,160)),1);
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
