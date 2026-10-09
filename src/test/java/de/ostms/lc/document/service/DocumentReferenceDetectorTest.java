package de.ostms.lc.document.service;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class DocumentReferenceDetectorTest {
 @Test void explicitLabelsRecognizedAcrossLines(){
  for(String text:new String[]{"LC Reference: LCB0310202600653","L/C No.\nLCB0310202600653","Akkreditiv Nummer: LCB0310202600653","Letter of credit number: LCB0310202600653"})
   assertThat(DocumentReferenceDetector.detect(text)).as(text).isEqualTo("LCB0310202600653");
 }
 @Test void conflictingAndUnrelatedReferencesRemainUnknown(){
  for(String text:new String[]{"Invoice number: INV1234","LC conditions apply","LC No: ABC123\nLC No: DEF456","public1234"})
   assertThat(DocumentReferenceDetector.detect(text)).as(text).isNull();
 }
}
