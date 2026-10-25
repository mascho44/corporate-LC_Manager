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
 @Test void creditNumberAsPrintedOnInsuranceCertificatesIsRecognised(){
  for(String text:new String[]{"Credit No.: LCB0310202600653","CREDIT NO LCB0310202600653","Credit Ref: LCB0310202600653","Credit number\nLCB0310202600653"})
   assertThat(DocumentReferenceDetector.detect(text)).as(text).isEqualTo("LCB0310202600653");
  assertThat(DocumentReferenceDetector.detect("Credit No.: LCB0310202600653\nL/C No.: LCB0310202600653")).as("same value in two labels").isEqualTo("LCB0310202600653");
  for(String text:new String[]{"Credit card number 4111111","Credit terms: 30 days","credit note 1234","Credit No.: ABC123\nL/C No.: DEF456"})
   assertThat(DocumentReferenceDetector.detect(text)).as(text).isNull();
 }
 @Test void scannedFormsNeitherTakeTheInvoiceNumberNorCutTheReference(){
  String row="COMMERCIAL INVOICE\nInvoice No.: Invoice Date: Documentary Credit No.:\nINV-2026-0815 05.12.2026 TEST-LC-2026-003\nPayment: by documentary credit TEST-LC-2026-003";
  assertThat(DocumentReferenceDetector.detect(row)).isEqualTo("TEST-LC-2026-003");
  assertThat(DocumentReferenceDetector.detect(row+"\nDocumentary Credit No.: TEST-LC-2026-003")).isEqualTo("TEST-LC-2026-003");
  // "LC" inside a longer reference is not a label of its own ("2026-003" was read from TEST-LC-2026-003)
  assertThat(DocumentReferenceDetector.detect("Certificate for TEST-LC-2026-003 issued")).isNull();
  assertThat(DocumentReferenceDetector.detect("Documentary Credit No.: TEST-LC-2026-001")).isEqualTo("TEST-LC-2026-001");
 }
}
