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
}
