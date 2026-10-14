package de.ostms.lc.document.service;
import de.ostms.lc.document.domain.DocumentType;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Synthetic scan texts modelled on a real duplex-scanned document set (front pages, back sides, copies, enclosures). */
class PdfDocumentSplitterAttachmentTest {
 private static String bl(String kind){return "ACME SEAWAYS\nBILL OF LADING\n"+kind+"\nSHIPPER B/L NO. REF\nSAMPLE TRADING GMBH DE1112223 9990001\nPORT OF LOADING ROTTERDAM\n";}
 private static final String BL_BACK="ACME SEAWAYS\nterms of carriage the carrier shall not be liable for loss or damage to the goods\n";
 private static List<String> parts(List<String> texts){var out=new ArrayList<String>();PdfDocumentSplitter.proposeFromTexts(texts).parts().forEach(p->out.add(p.fromPage()+"-"+p.toPage()+" "+p.documentType()));return out;}

 @Test void backSidesJoinTheirFrontAndEachFrontStartsANewDocument(){
  var result=parts(List.of(bl("ORIGINAL"),BL_BACK,bl("ORIGINAL"),BL_BACK,bl("NON-NEGOTIABLE COPY"),BL_BACK,bl("NON-NEGOTIABLE COPY"),BL_BACK));
  assertThat(result).containsExactly("1-2 BILL_OF_LADING","3-4 BILL_OF_LADING","5-6 BILL_OF_LADING","7-8 BILL_OF_LADING");
 }
 @Test void boilerplateTermsAndStampPagesStayWithTheInsurancePolicy(){
  String policy="ACME INSURANCE AG\nMARINE INSURANCE POLICY No. 050.060\nInsured: SAMPLE TRADING GMBH\nORIGINAL\n";
  String terms="Important\nProcedure in the event of loss or damage for which underwriters may be liable.\nIf on opening packages you find any loss or damage original copy\n";
  var result=parts(List.of(policy,"SAMPLE TRADING GMBH Musterweg 1 stamp",terms,policy.replace("ORIGINAL","COPY"),terms));
  assertThat(result).containsExactly("1-3 INSURANCE_CERTIFICATE","4-5 INSURANCE_CERTIFICATE");
 }
 @Test void enclosureSharingContainerAndOrderNumbersJoinsThePackingList(){
  String list="SAMPLE TRADING GMBH\nPACKING LIST / WEIGHT LIST\nContainer ABCU1234567 seal S1234567\n";
  String attachment="Attachment for roll list\nContainer ABCU1234567 seal S1234567\nroll 9000000001\n";
  var result=parts(List.of(list,attachment,attachment));
  assertThat(result).containsExactly("1-3 PACKING_LIST");
 }
 @Test void submissionLetterWithPageHeaderKeepsItsSecondPage(){
  String first="DOKUMENTENEINREICHUNG\nAkkreditivnummer LC1\nIhre Referenz REF2026000123\nPackliste\nRechnung\nKonnossement\n";
  String second="REF2026000123 - Seite 2\nPackliste\nRechnung\nUnterschrift\n";
  assertThat(parts(List.of(first,second))).containsExactly("1-2 DOCUMENT_SUBMISSION");
 }
 @Test void bilingualCertificateOfOriginFormIsRecognised(){
  assertThat(DocumentClassifier.classify(null,"Certificate of origin - Certificat d'origine -\nSample").suggestedType()).isEqualTo(DocumentType.CERTIFICATE_OF_ORIGIN);
  assertThat(DocumentClassifier.classify(null,"PACKING LIST / WEIGHT LIST\nPARTIAL SHIPMENT").suggestedType()).isEqualTo(DocumentType.PACKING_LIST);
 }
 @Test void proposalShowsWhatWasReadFromEachPage(){
  var proposal=PdfDocumentSplitter.proposeFromTexts(List.of("","COMMERCIAL   INVOICE\nNo. 1 "+"x".repeat(300)));
  assertThat(proposal.pages().get(0).textSource()).isEqualTo("UNAVAILABLE");assertThat(proposal.pages().get(0).sample()).isNull();
  assertThat(proposal.pages().get(1).sample()).startsWith("COMMERCIAL INVOICE No. 1 x").hasSizeLessThanOrEqualTo(161).endsWith("…");
 }
}
