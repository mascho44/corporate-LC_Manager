package de.ostms.lc.document.service;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import static org.assertj.core.api.Assertions.*;
class PdfPagePreviewServiceTest {
 @Test void repeatedRequestsAreServedFromTheCache()throws Exception{
  var service=new PdfPagePreviewService();byte[] pdf=PdfDocumentSplitterTest.pdf("Synthetic first page");
  byte[] first=service.render(pdf,1,false);
  assertThat(service.render(pdf.clone(),1,false)).isSameAs(first);
  assertThat(service.render(pdf,1,true)).isNotSameAs(first);
 }
 @Test void prewarmFillsTheCacheInTheBackgroundAndIgnoresOtherTypes()throws Exception{
  var service=new PdfPagePreviewService();byte[] pdf=PdfDocumentSplitterTest.pdf("Synthetic first page","Synthetic second page");
  service.prewarm(pdf,"image/png");service.prewarm(null,"application/pdf");
  assertThat(service.cachedPages()).isZero();
  service.prewarm(pdf,"application/pdf");
  long deadline=System.currentTimeMillis()+30000;
  while(service.cachedPages()<2&&System.currentTimeMillis()<deadline)Thread.sleep(50);
  assertThat(service.cachedPages()).isEqualTo(2);
 }
 @Test void producesSmallAndEnlargedPngPagesWithoutOcr()throws Exception{
  var service=new PdfPagePreviewService();byte[] pdf=PdfDocumentSplitterTest.pdf("Synthetic first page","Synthetic second page");
  var small=ImageIO.read(new ByteArrayInputStream(service.render(pdf,2,false)));
  var large=ImageIO.read(new ByteArrayInputStream(service.render(pdf,2,true)));
  assertThat(small).isNotNull();assertThat(Math.max(small.getWidth(),small.getHeight())).isLessThanOrEqualTo(720);
  assertThat(large.getHeight()).isGreaterThan(small.getHeight());assertThat(Math.max(large.getWidth(),large.getHeight())).isLessThanOrEqualTo(1400);
  boolean visible=false;
  for(int y=0;y<small.getHeight();y++)for(int x=0;x<small.getWidth();x++)if((small.getRGB(x,y)&0xffffff)!=0xffffff)visible=true;
  assertThat(visible).as("Preview must contain rendered page content, not a blank image").isTrue();
 }
 @Test void refusesMissingPages()throws Exception{
  var service=new PdfPagePreviewService();byte[] pdf=PdfDocumentSplitterTest.pdf("Synthetic page");
  for(int page:new int[]{-1,0,2})assertThatThrownBy(()->service.render(pdf,page,false)).isInstanceOf(IllegalArgumentException.class);
 }
}
