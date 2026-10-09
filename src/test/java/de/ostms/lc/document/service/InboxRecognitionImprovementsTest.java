package de.ostms.lc.document.service;

import de.ostms.lc.document.domain.DocumentType;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

class InboxRecognitionImprovementsTest {
 @Test void datesWithOcrSpacingPlaceAndTwoDigitYearAreRead(){
  var expected=LocalDate.of(2026,8,25);
  for(String value:new String[]{"Invoice Date: 25 . 08 . 2026","Stuttgart, den 25. August 2026","Stuttgart, 25.08.2026","Date and place of issue: 25.08.2026 Hamburg","Invoice dated 25.08.2026","Ausgestellt am 25.08.2026","Date: 25-Aug-26"})
   assertThat(DocumentDateDetector.detect(value).date()).as(value).isEqualTo(expected);
 }
 @Test void explicitIssueDateOutranksOtherDatesButConflictsStayOpen(){
  assertThat(DocumentDateDetector.detect("Invoice date: 25.08.2026\nDate: 01.09.2026").date()).isEqualTo(LocalDate.of(2026,8,25));
  assertThat(DocumentDateDetector.detect("Invoice date: 25.08.2026\nInvoice date: 26.08.2026").date()).isNull();
  assertThat(DocumentDateDetector.detect("Shipment date: 25.08.2026").date()).isNull();
 }
 @Test void copyStampsWithDecorationFractionsAndOcrNoise(){
  assertThat(DocumentCopyDetector.detect("*** ORIGINAL ***").kind()).isEqualTo("ORIGINAL");
  assertThat(DocumentCopyDetector.detect("(COPY)").kind()).isEqualTo("COPY");
  assertThat(DocumentCopyDetector.detect("1/3 ORIGINAL").copyNumber()).isEqualTo(0);
  assertThat(DocumentCopyDetector.detect("ORIGINAL 2 of 3").copyNumber()).isEqualTo(0);
  assertThat(DocumentCopyDetector.detect("COPY - NON NEGOTIABLE").kind()).isEqualTo("COPY");
  assertThat(DocumentCopyDetector.detect("ORIGINAL NOT NEGOTIABLE").kind()).isEqualTo("ORIGINAL");
  assertThat(DocumentCopyDetector.detect("COMMERCIAL INVOICE - ORIGINAL").kind()).isEqualTo("ORIGINAL");
  assertThat(DocumentCopyDetector.detect("0RIGINAL").kind()).isEqualTo("ORIGINAL");
  assertThat(DocumentCopyDetector.detect("O R I G I N A L").kind()).isEqualTo("ORIGINAL");
  assertThat(DocumentCopyDetector.detect("Abschrift").kind()).isEqualTo("COPY");
  assertThat(DocumentCopyDetector.detect("Please present original documents and copy of invoice").kind()).isEqualTo("UNKNOWN");
 }
 @Test void headingsToleratePunctuationAndFieldLabelsDoNotBreakThem(){
  assertThat(DocumentClassifier.classify(null,"*** PACKING LIST ***").suggestedType()).isEqualTo(DocumentType.PACKING_LIST);
  assertThat(DocumentClassifier.classify(null,"COMMERCIAL INVOICE:").suggestedType()).isEqualTo(DocumentType.COMMERCIAL_INVOICE);
  assertThat(DocumentClassifier.classify(null,"Packing List\nInvoice No.: INV-5\nCartons 4").suggestedType()).isEqualTo(DocumentType.PACKING_LIST);
  assertThat(DocumentClassifier.classify(null,"INVOICE - ORIGINAL").suggestedType()).isEqualTo(DocumentType.COMMERCIAL_INVOICE);
  assertThat(DocumentClassifier.classify(null,"COMMERCIAL INVOICE\nPACKING LIST").status()).isEqualTo("AMBIGUOUS");
 }
 @Test void fieldProfileGivesWeakSuggestionOnly(){
  var result=DocumentClassifier.classify(null,"Shipper: A\nConsignee: B\nNotify party: C\nPort of loading: X\nPort of discharge: Y");
  assertThat(result.suggestedType()).isEqualTo(DocumentType.BILL_OF_LADING);
  assertThat(result.status()).isEqualTo("REVIEW");assertThat(result.score()).isLessThan(.8);
 }
 @Test void pageNumbersWithoutTotalContinueAndNumberedContinuationsDoNotBlockAutomaticSplit()throws Exception{
  var proposal=PdfDocumentSplitter.propose(PdfDocumentSplitterTest.pdf("COMMERCIAL INVOICE No. INV-1 Page 1","Items Page 2","PACKING LIST"),null);
  assertThat(proposal.parts()).hasSize(2);
  assertThat(proposal.parts().get(0).toPage()).isEqualTo(2);
  assertThat(InboxAutomaticSplitter.eligible(proposal)).isTrue();
 }
 @Test void stampSharingALineWithLogoTextIsFoundOnlyInThePageHead(){
  assertThat(DocumentCopyDetector.detect("munksjo ORIGINAL\nCommercial invoice").kind()).isEqualTo("ORIGINAL");
  assertThat(DocumentCopyDetector.detect("munksjo O R I G I N A L\nInvoice").kind()).isEqualTo("ORIGINAL");
  assertThat(DocumentCopyDetector.detect("logo COPY 2\nInvoice").copyNumber()).isEqualTo(1);
  assertThat(DocumentCopyDetector.detect("Invoice\nPlease present original invoice\n").kind()).isEqualTo("UNKNOWN");
  assertThat(DocumentCopyDetector.detect("\n".repeat(0)+"a\n".repeat(20)+"munksjo ORIGINAL").kind()).isEqualTo("UNKNOWN");
 }
 @Test void layoutsFromRealOcrAreRead(){
  var expected=LocalDate.of(2026,7,21);
  for(String value:new String[]{"SHANGHAI, CHINA DATE: 21.07.2026\nL/C NO. X DTD. 09.06.2026","Frankfurt, 2026-07-21 PREMIUM PAID","21.07.2026\nPlace and date of issue; name, signature and stamp","21.07.2026\nOrt und Datum der Ausstellung; Stempel","INVOICE DATE: 21.07.2026\nL/C NO. X DTD. 09.06.2026 PAYABLE AT 60 DAYS AFTER B/L DATE"})
   assertThat(DocumentDateDetector.detect(value).date()).as(value).isEqualTo(expected);
  assertThat(DocumentDateDetector.detect("Date Shipped on Board: 30 Jul 2026").date()).isNull();
  assertThat(DocumentDateDetector.detect("Place and date of issuance:\n30 Jul 2026").date()).isEqualTo(LocalDate.of(2026,7,30));
  assertThat(DocumentDateDetector.detect("Shipment date: 25.08.2026 Expiry date: 30.09.2026").date()).isNull();
 }
 @Test void testPdfLayoutsFromTheTestSet(){
  assertThat(DocumentClassifier.classify(null,"Versicherung\nMARINE INSURANCE POLICY No. 050.060\nThis is to certify").suggestedType()).isEqualTo(DocumentType.INSURANCE_CERTIFICATE);
  assertThat(DocumentClassifier.classify(null,"Dokumenteneinreichung\nAkkreditiv 123").suggestedType()).isEqualTo(DocumentType.DOCUMENT_SUBMISSION);
  assertThat(DocumentCopyDetector.detect("KOPIE COPY - COPIE - COPIA").kind()).isEqualTo("COPY");
  assertThat(DocumentCopyDetector.detect("A 40449622 DURCHSCHRIFT").kind()).isEqualTo("COPY");
  assertThat(DocumentCopyDetector.detect("DURCHSCHRIFT").kind()).isEqualTo("COPY");
  assertThat(DocumentDateDetector.detect("BILL OF LADING\nDate Shipped on Board:\n\n30 Jul 2026\n").date()).isEqualTo(LocalDate.of(2026,7,30));
  assertThat(DocumentDateDetector.detect("Date Shipped on Board: 30 Jul 2026").date()).isNull();
 }
}
