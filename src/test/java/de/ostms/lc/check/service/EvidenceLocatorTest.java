package de.ostms.lc.check.service;
import de.ostms.lc.document.domain.LcDocument;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import static org.assertj.core.api.Assertions.assertThat;
class EvidenceLocatorTest {
 @Test void dateMetadataLocatesFormattedValueWithoutClaimingExactQuote()throws Exception{
  var doc=document("Other text","Document issued 08.10.2026");doc.setDocumentDate(java.time.LocalDate.of(2026,10,8));
  var location=EvidenceLocator.locate(doc,"Dokumentdatum: 2026-10-08");assertThat(location.status()).isEqualTo("MATCH");assertThat(location.method()).isEqualTo("PDF_VALUE_ANCHOR");assertThat(location.pages()).containsExactly(2);
 }
 @Test void referenceAnchorsRemainAmbiguousAndNeverMatchSubstring()throws Exception{
  var doc=document("Reference LC123456","Reference LC123456");doc.setExtractedReference("LC123456");assertThat(EvidenceLocator.locate(doc,"Reference captured: LC123456").status()).isEqualTo("AMBIGUOUS");
  var other=document("Reference XLC123456Y");other.setExtractedReference("LC123456");assertThat(EvidenceLocator.locate(other,"Reference captured: LC123456").status()).isEqualTo("UNAVAILABLE");
 }
 private LcDocument document(String... texts)throws Exception{
  var doc=new LcDocument();doc.setContentType("application/pdf");
  try(var pdf=new PDDocument();var out=new ByteArrayOutputStream()){
   for(String text:texts){var page=new PDPage();pdf.addPage(page);try(var stream=new PDPageContentStream(pdf,page)){stream.beginText();stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12);stream.newLineAtOffset(40,700);stream.showText(text);stream.endText();}}
   pdf.save(out);doc.setContent(out.toByteArray());
  }return doc;
 }
 @Test void locatesOnlyExactPage()throws Exception{var result=EvidenceLocator.locate(document("Invoice number 123","Shipped on Board: 18 SEP 2026"),"Shipped on Board: 18 SEP 2026");assertThat(result.status()).isEqualTo("MATCH");assertThat(result.pages()).containsExactly(2);}
 @Test void repeatedTextIsAmbiguousEvenOnSamePage()throws Exception{assertThat(EvidenceLocator.locate(document("Invoice 123 Invoice 123"),"Invoice 123").status()).isEqualTo("AMBIGUOUS");assertThat(EvidenceLocator.locate(document("Invoice 123","Invoice 123"),"Invoice 123").pages()).containsExactly(1,2);}
 @Test void missingOrChangedTextNeverInventsPage()throws Exception{assertThat(EvidenceLocator.locate(document("18 SEP 2026"),"2026-09-18").status()).isEqualTo("UNAVAILABLE");assertThat(EvidenceLocator.locate(document("Invoice 123"),null).pages()).isEmpty();}
 @Test void brokenPdfIsUnavailable(){var doc=new LcDocument();doc.setContentType("application/pdf");doc.setContent(new byte[]{1,2});assertThat(EvidenceLocator.locate(doc,"Invoice 123").status()).isEqualTo("UNAVAILABLE");}
 @Test void usesStoredOcrPageWithoutReadingScan()throws Exception{
  var doc=new LcDocument();var word=new de.ostms.lc.document.service.OcrEvidence.Word("Invoice123",.7,2,10,20,50,10);
  var evidence=new de.ostms.lc.document.service.OcrEvidence("test","OCR",200,.8,java.util.List.of(word));
  doc.setOcrEvidenceJson(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(evidence));
  var result=EvidenceLocator.locate(doc,"Invoice123");assertThat(result.method()).isEqualTo("OCR_TEXT");assertThat(result.pages()).containsExactly(2);assertThat(result.status()).isEqualTo("MATCH");
 }
}
