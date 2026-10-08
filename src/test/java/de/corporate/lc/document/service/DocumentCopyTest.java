package de.corporate.lc.document.service;
import de.corporate.lc.document.domain.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class DocumentCopyTest {
 @Test void designationIsExplicitAndUnknownRemainsUnknown(){
  var document=new LcDocument();assertThat(document.getCopyNumber()).isNull();
  for(int value=0;value<=3;value++){document.setCopyNumber(value);assertThat(document.getCopyNumber()).isEqualTo(value);}
  document.setCopyNumber(null);assertThat(document.getCopyNumber()).isNull();
  assertThatThrownBy(()->document.setCopyNumber(-1)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->document.setCopyNumber(4)).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void splittingCarriesDesignationAndRejectsInvalidCopies()throws Exception{
  var parts=java.util.List.of(new PdfDocumentSplitter.Part(1,1,DocumentType.COMMERCIAL_INVOICE,0),new PdfDocumentSplitter.Part(2,2,DocumentType.COMMERCIAL_INVOICE,2));
  var output=PdfDocumentSplitter.split(PdfDocumentSplitterTest.pdf("Invoice original","Invoice copy"),null,parts);
  assertThat(output).extracting(p->p.part().copyNumber()).containsExactly(0,2);
  assertThatThrownBy(()->new PdfDocumentSplitter.Part(1,1,DocumentType.OTHER,4)).isInstanceOf(IllegalArgumentException.class);
 }
}
