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
 // Back sides that OCR reads so badly that only typical fields of the type remain (no heading): they join the running bill of lading instead of becoming parts of their own.
 private static final String BL_BACK_POOR="shipper consignee freight vessel\nTerma an cowDmONS 08 ah Corts tera\n";
 @Test void aBadlyReadBackSideWithTheTypicalFieldsOfTheTypeJoinsItsFront(){
  var result=parts(List.of(bl("ORIGINAL"),BL_BACK_POOR,bl("ORIGINAL"),BL_BACK_POOR));
  assertThat(result).containsExactly("1-2 BILL_OF_LADING","3-4 BILL_OF_LADING");
  var proposal=PdfDocumentSplitter.proposeFromTexts(List.of(bl("ORIGINAL"),BL_BACK_POOR));
  assertThat(proposal.pages().get(1).classification().method()).isEqualTo("KEYWORD_CONTINUATION_V1");
  assertThat(InboxAutomaticSplitter.reliablePage(proposal.pages().get(1))).isFalse(); // a guess stays a proposal for review, never an automatic split
 }
 @Test void aWeakPageOfAnotherTypeStaysSeparate(){
  var result=parts(List.of(bl("ORIGINAL"),"packing gross weight net weight packages carton measurement\n"));
  assertThat(result).hasSize(2);
 }
 @Test void continuationPagesWithBlankBoilerplateOrPageTotalCountAsReliableForAutomaticSplitting(){
  for(String method:new String[]{"BLANK_PAGE_V1","BOILERPLATE_PAGE_V1","PAGE_TOTAL_V1","PAGE_SEQUENCE_V1"}){
   var page=new PdfDocumentSplitter.Page(2,new DocumentClassifier.Classification(DocumentType.BILL_OF_LADING,.8,"REVIEW",method,List.of("x")),"PAGE_TEXT",DocumentCopyDetector.detect(null),"");
   assertThat(InboxAutomaticSplitter.reliablePage(page)).as(method).isTrue();
  }
  for(String method:new String[]{"LETTERHEAD_CONTINUITY_V1","SHARED_IDENTIFIERS_V1","KEYWORD_CONTINUATION_V1","KEYWORD_PROFILE_V1"}){
   var page=new PdfDocumentSplitter.Page(2,new DocumentClassifier.Classification(DocumentType.BILL_OF_LADING,.8,"REVIEW",method,List.of("x")),"PAGE_TEXT",DocumentCopyDetector.detect(null),"");
   assertThat(InboxAutomaticSplitter.reliablePage(page)).as(method).isFalse();
  }
 }
}
