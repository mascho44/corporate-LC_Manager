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
 @Test void digitalPdfRecordsBoxesWithoutInventingOcrConfidence()throws Exception{
  var words=PdfWordPositions.read(PdfDocumentSplitterTest.pdf("Invoice number: INV999"));assertThat(words).isNotEmpty().allMatch(w->w.page()==1&&w.width()>0&&w.confidence()==null);
  assertThat(words.stream().map(OcrEvidence.Word::text)).contains("INV999");
 }
}
