package de.ostms.lc.document.service;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import de.ostms.lc.document.domain.LcDocument;
import static org.assertj.core.api.Assertions.*;
class DocumentDateDetectorTest {
 @Test void labeledNumericAndNamedDatesAreRecognized(){
  for(String value:new String[]{"Invoice Date: 2026-08-25","Datum: 25.08.2026","Date of issue\n25 AUG 2026","Document date: August 25, 2026","Rechnungsdatum: 25. August 2026","Date: 25/08/2026","Date: 08/25/2026"}){
   assertThat(DocumentDateDetector.detect(value).date()).as(value).isEqualTo(LocalDate.of(2026,8,25));
  }
 }
 @Test void ambiguousInvalidAndUnrelatedDatesRemainUnknown(){
  for(String value:new String[]{"Date: 05/08/2026","Invoice date: 31.02.2026","Shipment date: 25.08.2026\nExpiry date: 30.09.2026","On board: 25.08.2026","Invoice date: 25.08.2026\nInvoice date: 26.08.2026"}){
   assertThat(DocumentDateDetector.detect(value).date()).as(value).isNull();
  }
 }
 @Test void repeatedSameIssueDateIsNotConflicting(){assertThat(DocumentDateDetector.detect("Date: 25.08.2026\nDate: 25.08.2026").date()).isEqualTo(LocalDate.of(2026,8,25));}
 @Test void recognitionFillsMetadataButDoesNotOverwriteManualDates(){
  var service=new DocumentExtractionService();var document=new LcDocument();
  service.applyRecognizedText(document,"Invoice date: 25.08.2026","EXTRACTED");
  assertThat(document.getDocumentDate()).isEqualTo(LocalDate.of(2026,8,25));
  document.setDocumentDate(LocalDate.of(2026,8,24));
  service.applyRecognizedText(document,"Invoice date: 26.08.2026","OCR_EXTRACTED");
  assertThat(document.getDocumentDate()).isEqualTo(LocalDate.of(2026,8,24));
 }
}
