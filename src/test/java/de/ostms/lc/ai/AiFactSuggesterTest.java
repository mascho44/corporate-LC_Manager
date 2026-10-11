package de.ostms.lc.ai;
import de.ostms.lc.document.domain.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static de.ostms.lc.rulepack.PackDefinition.Field;
import static org.assertj.core.api.Assertions.assertThat;

class AiFactSuggesterTest {
 private static final String TEXT="BILL OF LADING\nVessel / Voyage: MSC AURORA V.2605E\nConsignee: TO ORDER OF DEUTSCHE BANK AG\nSigned by Captain J. Meyer, Master\n";
 private LcDocument doc(){var d=new LcDocument();d.setDocumentType(DocumentType.BILL_OF_LADING);d.setExtractedText(TEXT);return d;}
 private static LlmClient answering(String json){return new LlmClient(){public String model(){return "test-model";}public String generateJson(String p){return json;}};}

 @Test void disabledWithoutClient()throws Exception{
  var s=new AiFactSuggester(null,null);assertThat(s.enabled()).isFalse();assertThat(s.suggest(doc())).isEmpty();
 }
 @Test void verifiedQuotesBecomeProposalsAndInventedOnesAreDropped()throws Exception{
  String answer="{\"vessel\":{\"value\":\"MSC AURORA\",\"quote\":\"Vessel / Voyage: MSC AURORA V.2605E\"},"
   +"\"consignee\":{\"value\":\"ACME TRADING\",\"quote\":\"Consignee: ACME TRADING\"},"            // quote not in the document
   +"\"signer_name\":{\"value\":\"Captain J. Meyer\",\"quote\":\"Signed by Master\"},"            // quote does not contain the value
   +"\"signer_role\":{\"value\":\"Master\",\"quote\":\"Captain J. Meyer, Master\"},"
   +"\"carrier\":{\"value\":null,\"quote\":null}}";
  var out=new AiFactSuggester(answering(answer),null).suggest(doc());
  assertThat(out).extracting(x->x.field()).containsExactlyInAnyOrder(Field.DOCUMENT_VESSEL,Field.DOCUMENT_SIGNER_ROLE);
  assertThat(out.get(0).source()).contains("KI-Vorschlag").contains("test-model").contains("bitte prüfen");
 }
 @Test void lineBreaksAndPunctuationDoNotBreakTheQuoteCheck(){
  var out=AiFactSuggester.verify("{\"consignee\":{\"value\":\"TO ORDER OF DEUTSCHE BANK AG\",\"quote\":\"Consignee:\\nTO ORDER OF DEUTSCHE BANK AG\"}}",List.of("consignee"),"x Consignee: TO ORDER OF DEUTSCHE BANK AG y","m",Map.of());
  assertThat(out).hasSize(1);
 }
 @Test void invalidAnswerAndAlreadyKnownValuesYieldNothing(){
  assertThat(AiFactSuggester.verify("not json",List.of("vessel"),TEXT,"m",Map.of())).isEmpty();
  assertThat(AiFactSuggester.verify("{\"vessel\":{\"value\":\"MSC AURORA\",\"quote\":\"Vessel / Voyage: MSC AURORA V.2605E\"}}",List.of("vessel"),TEXT,"m",Map.of(Field.DOCUMENT_VESSEL,"MSC AURORA"))).isEmpty();
 }
}
