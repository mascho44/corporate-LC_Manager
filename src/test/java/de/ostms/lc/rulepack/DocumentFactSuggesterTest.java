package de.ostms.lc.rulepack;
import de.ostms.lc.document.domain.*;
import de.ostms.lc.document.service.SignatureEvidenceService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;

class DocumentFactSuggesterTest {
 private final SignatureEvidenceService sig=Mockito.mock(SignatureEvidenceService.class);
 { Mockito.when(sig.evidence(Mockito.any())).thenReturn(SignatureEvidenceService.Evidence.unavailable()); }
 private final DocumentFactSuggester suggester=new DocumentFactSuggester(sig);

 @Test void numberWords(){assertEquals("3",DocumentFactSuggester.number("THREE"));assertEquals("2",DocumentFactSuggester.number("2"));}

 @Test void transportDocumentYieldsOnBoardAndOriginals(){
  var doc=new LcDocument();doc.setDocumentType(DocumentType.BILL_OF_LADING);
  doc.setExtractedText("BILL OF LADING\nNumber of original Bills of Lading: THREE\nShipped on board date: 12.03.2025\n");
  var fields=suggester.suggest(doc).stream().collect(java.util.stream.Collectors.toMap(DocumentFactSuggester.Suggestion::field,DocumentFactSuggester.Suggestion::value));
  assertEquals("3",fields.get(PackDefinition.Field.DOCUMENT_ORIGINAL_COUNT));
  assertEquals("2025-03-12",fields.get(PackDefinition.Field.DOCUMENT_ON_BOARD_DATE));
 }

 @Test void invoiceYieldsNothing(){
  var doc=new LcDocument();doc.setDocumentType(DocumentType.COMMERCIAL_INVOICE);doc.setExtractedText("Invoice on board 12.03.2025");
  assertTrue(suggester.suggest(doc).isEmpty());
 }
 @Test void portsAndWeightsAreExtracted(){
  var doc=new LcDocument();doc.setDocumentType(DocumentType.BILL_OF_LADING);
  doc.setExtractedText("BILL OF LADING\nPort of Loading: Shanghai\nPort of Discharge\nHamburg\nGross Weight: 12,345.60 KGS\nNet Weight 11.800,5 kg\n");
  var f=suggester.suggest(doc).stream().collect(java.util.stream.Collectors.toMap(DocumentFactSuggester.Suggestion::field,DocumentFactSuggester.Suggestion::value));
  assertEquals("Shanghai",f.get(PackDefinition.Field.DOCUMENT_LOADING_PORT));
  assertEquals("Hamburg",f.get(PackDefinition.Field.DOCUMENT_DISCHARGE_PORT));
  assertEquals("12345.60",f.get(PackDefinition.Field.DOCUMENT_GROSS_WEIGHT));
  assertEquals("11800.5",f.get(PackDefinition.Field.DOCUMENT_NET_WEIGHT));
  assertEquals("KG",f.get(PackDefinition.Field.DOCUMENT_WEIGHT_UNIT));
 }
 @Test void conflictingValuesYieldNoSuggestion(){
  var doc=new LcDocument();doc.setDocumentType(DocumentType.BILL_OF_LADING);
  doc.setExtractedText("Port of Loading: Shanghai\nPort of Loading: Ningbo\nGross Weight: 100 kg\nGross Weight: 200 kg");
  var r=suggester.suggest(doc);assertTrue(r.isEmpty(),r.toString());
 }
 @Test void weightsAlsoFromPackingList(){
  var doc=new LcDocument();doc.setDocumentType(DocumentType.PACKING_LIST);doc.setExtractedText("PACKING LIST\nGross weight: 2.5 t");
  var f=suggester.suggest(doc);
  assertEquals("2.5",f.stream().filter(x->x.field()==PackDefinition.Field.DOCUMENT_GROSS_WEIGHT).findFirst().orElseThrow().value());
 }
}
