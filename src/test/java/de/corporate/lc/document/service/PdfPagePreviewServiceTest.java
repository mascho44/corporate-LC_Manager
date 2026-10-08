package de.corporate.lc.document.service;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import static org.assertj.core.api.Assertions.*;
class PdfPagePreviewServiceTest {
 @Test void producesSmallAndEnlargedPngPagesWithoutOcr()throws Exception{
  var service=new PdfPagePreviewService();byte[] pdf=PdfDocumentSplitterTest.pdf("Synthetic first page","Synthetic second page");
  var small=ImageIO.read(new ByteArrayInputStream(service.render(pdf,2,false)));
  var large=ImageIO.read(new ByteArrayInputStream(service.render(pdf,2,true)));
  assertThat(small).isNotNull();assertThat(Math.max(small.getWidth(),small.getHeight())).isLessThanOrEqualTo(280);
  assertThat(large.getHeight()).isGreaterThan(small.getHeight());assertThat(Math.max(large.getWidth(),large.getHeight())).isLessThanOrEqualTo(1400);
 }
 @Test void refusesMissingPages()throws Exception{
  var service=new PdfPagePreviewService();byte[] pdf=PdfDocumentSplitterTest.pdf("Synthetic page");
  for(int page:new int[]{-1,0,2})assertThatThrownBy(()->service.render(pdf,page,false)).isInstanceOf(IllegalArgumentException.class);
 }
}
