package de.ostms.lc.ai;
import de.ostms.lc.document.domain.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class AiHintServiceTest {
 private static LcDocument doc(DocumentType t,String text){var d=new LcDocument();d.setDocumentType(t);d.setExtractedText(text);return d;}
 private static LlmClient answering(String json){return new LlmClient(){public String provider(){return "test";}public String model(){return "m";}public String generateJson(String p){return json;}};}

 @Test void onlyHintsWithEvidenceFromTheDocumentsSurviveAndAreSortedByTheUi()throws Exception{
  var docs=List.of(doc(DocumentType.COMMERCIAL_INVOICE,"TOTAL GROSS WEIGHT: 21.358 KGS\nBY VESSEL ALPHA"),doc(DocumentType.BILL_OF_LADING,"VESSEL BETA\nGROSS WEIGHT 21358.0"));
  String answer="{\"discrepancies\":["
   +"{\"severity\":\"HIGH\",\"check\":\"Vessel differs\",\"documents\":[\"COMMERCIAL_INVOICE\",\"BILL_OF_LADING\"],\"evidence\":\"BY VESSEL ALPHA\",\"explanation\":\"Invoice names ALPHA, B/L names BETA.\"},"
   +"{\"severity\":\"MEDIUM\",\"check\":\"Invented\",\"documents\":[],\"evidence\":\"CONTAINER NO 123 SEAL 9\",\"explanation\":\"not in any text\"},"
   +"{\"severity\":\"WEIRD\",\"check\":\"Weight\",\"documents\":[\"BILL_OF_LADING\"],\"evidence\":\"GROSS WEIGHT 21358.0\",\"explanation\":\"x\"}]}";
  var hints=new AiHintService(answering(answer)).hints(docs);
  assertThat(hints).extracting(AiHintService.Hint::check).containsExactly("Vessel differs","Weight");
  assertThat(hints.get(1).severity()).isEqualTo("LOW");
 }
 @Test void fewerThanTwoDocumentsOrBrokenAnswersGiveNoHints()throws Exception{
  assertThat(new AiHintService(answering("{}")).hints(List.of(doc(DocumentType.COMMERCIAL_INVOICE,"x y z")))).isEmpty();
  assertThat(AiHintService.verify("garbage","anything")).isEmpty();
 }
}
