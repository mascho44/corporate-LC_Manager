package de.ostms.lc.document.api;

import de.ostms.lc.document.domain.DocumentMark;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

/** A mark is a person's decision about a region of a page: fractions of the page, one of four kinds. */
class DocumentMarkControllerTest {
 private static DocumentMarkController.MarkInput mark(int page,double x,double y,double w,double h,String kind){return new DocumentMarkController.MarkInput(page,x,y,w,h,kind);}

 @Test void acceptsAllFourKindsIncludingAStampThatWasSigned(){
  for(var kind:DocumentMark.Kind.values())assertThat(DocumentMarkController.validate(mark(2,.1,.2,.3,.1,kind.name()))).isEqualTo(kind);
  assertThat(DocumentMark.Kind.values()).extracting(Enum::name).contains("SIGNED_STAMP");
 }
 @Test void rejectsRegionsOutsideThePageOrTooSmall(){
  for(var bad:new DocumentMarkController.MarkInput[]{mark(0,.1,.1,.2,.2,"STAMP"),mark(1,-.1,.1,.2,.2,"STAMP"),mark(1,1,.1,.2,.2,"STAMP"),mark(1,.1,.1,.001,.2,"STAMP"),mark(1,.1,.1,.2,1.5,"STAMP"),mark(1,Double.NaN,.1,.2,.2,"STAMP"),mark(1,.1,.1,.2,.2,"BOGUS"),mark(1,.1,.1,.2,.2,null),null})
   assertThatThrownBy(()->DocumentMarkController.validate(bad)).isInstanceOf(ResponseStatusException.class);
 }
 private static byte[] twoLetterPages()throws Exception{
  try(var pdf=new org.apache.pdfbox.pdmodel.PDDocument();var out=new java.io.ByteArrayOutputStream()){
   pdf.addPage(new org.apache.pdfbox.pdmodel.PDPage(org.apache.pdfbox.pdmodel.common.PDRectangle.LETTER));
   pdf.addPage(new org.apache.pdfbox.pdmodel.PDPage(org.apache.pdfbox.pdmodel.common.PDRectangle.LETTER));
   pdf.save(out);return out.toByteArray();
  }
 }
 @Test void pageSizesFollowTheRenderedRasterAtTwoHundredDpi()throws Exception{
  var sizes=DocumentMarkController.pageSizes(twoLetterPages(),2);
  assertThat(sizes).hasSize(2);
  assertThat(sizes.get(0)[0]).isCloseTo(612*200/72.0,within(1.0));
  assertThat(sizes.get(0)[1]).isCloseTo(792*200/72.0,within(1.0));
 }
}
