package de.ostms.lc.ai;
import de.ostms.lc.document.domain.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static de.ostms.lc.rulepack.PackDefinition.Field;
import static org.assertj.core.api.Assertions.assertThat;

class AiFactSuggesterTest {
 private static final String TEXT="BILL OF LADING\nB/L No: HLC123456\nVessel / Voyage: MSC AURORA V.2605E\nConsignee: TO ORDER OF EXAMPLE BANK\nGross weight: 12,345.60 KGS\nNumber of original Bills of Lading: THREE (3)\nSHIPPED ON BOARD 12.03.2026\nSigned by Captain J. Meyer, Master\n";
 private LcDocument doc(DocumentType t){var d=new LcDocument();d.setDocumentType(t);d.setExtractedText(TEXT);return d;}
 private static LlmClient answering(String json){return new LlmClient(){public String provider(){return "test";}public String model(){return "m";}public String generateJson(String p){return json;}};}
 private static Map<Field,String> asMap(List<de.ostms.lc.rulepack.DocumentFactSuggester.Suggestion> l){var m=new EnumMap<Field,String>(Field.class);l.forEach(s->m.put(s.field(),s.value()));return m;}

 @Test void withoutAClientNothingHappens()throws Exception{
  var s=new AiFactSuggester(()->java.util.Optional.empty(),null);assertThat(s.available()).isFalse();assertThat(s.suggest(doc(DocumentType.BILL_OF_LADING))).isEmpty();
 }
 @Test void textNumbersDatesAndCountsAreVerifiedAgainstTheirQuotes()throws Exception{
  String answer="{\"document_number\":{\"value\":\"HLC123456\",\"quote\":\"B/L No: HLC123456\"},"
   +"\"vessel\":{\"value\":\"MSC AURORA\",\"quote\":\"Vessel / Voyage: MSC AURORA V.2605E\"},"
   +"\"gross_weight\":{\"value\":12345.6,\"quote\":\"Gross weight: 12,345.60 KGS\"},"          // number written differently than in the text
   +"\"on_board_date\":{\"value\":\"2026-03-12\",\"quote\":\"SHIPPED ON BOARD 12.03.2026\"},"  // ISO value, German date in the quote
   +"\"original_count\":{\"value\":3,\"quote\":\"original Bills of Lading: THREE (3)\"},"
   +"\"consignee\":{\"value\":\"ACME TRADING\",\"quote\":\"Consignee: ACME TRADING\"},"        // quote not in the document
   +"\"signer_name\":{\"value\":\"Captain J. Meyer\",\"quote\":\"Signed by Master\"},"       // quote does not contain the value
   +"\"carrier\":{\"value\":null,\"quote\":null}}";
  var f=asMap(new AiFactSuggester(()->java.util.Optional.of(answering(answer)),null).suggest(doc(DocumentType.BILL_OF_LADING)));
  assertThat(f).containsEntry(Field.DOCUMENT_VESSEL,"MSC AURORA").containsEntry(Field.DOCUMENT_GROSS_WEIGHT,"12345.6")
   .containsEntry(Field.DOCUMENT_ON_BOARD_DATE,"2026-03-12").containsEntry(Field.DOCUMENT_ORIGINAL_COUNT,"3");
  assertThat(f).doesNotContainKeys(Field.DOCUMENT_CONSIGNEE,Field.DOCUMENT_SIGNER_NAME,Field.DOCUMENT_CARRIER);
 }
 @Test void aWrongNumberOrDateIsRejectedEvenWithARealQuote()throws Exception{
  String answer="{\"gross_weight\":{\"value\":99999,\"quote\":\"Gross weight: 12,345.60 KGS\"},\"on_board_date\":{\"value\":\"2026-04-12\",\"quote\":\"SHIPPED ON BOARD 12.03.2026\"}}";
  assertThat(new AiFactSuggester(()->java.util.Optional.of(answering(answer)),null).suggest(doc(DocumentType.BILL_OF_LADING))).isEmpty();
 }
 @Test void onlyFieldsThatFitTheDocumentTypeAreAsked(){
  var invoiceKeys=AiFactSuggester.specsFor(DocumentType.COMMERCIAL_INVOICE,Set.of()).stream().map(AiFactSuggester.Spec::key).toList();
  assertThat(invoiceKeys).contains("unit_price").doesNotContain("consignee","drawee","notify_party");
  assertThat(AiFactSuggester.specsFor(DocumentType.BILL_OF_LADING,Set.of(Field.DOCUMENT_VESSEL)).stream().map(AiFactSuggester.Spec::key)).doesNotContain("vessel");
  assertThat(AiFactSuggester.specsFor(DocumentType.ADVISING_LETTER,Set.of())).isEmpty();
  assertThat(AiFactSuggester.prompt(AiFactSuggester.specsFor(DocumentType.COMMERCIAL_INVOICE,Set.of()),"x")).contains("unit price");
 }
 @Test void invalidAnswersYieldNothing(){
  assertThat(AiFactSuggester.verify("not json",AiFactSuggester.specsFor(DocumentType.BILL_OF_LADING,Set.of()),TEXT,"t/m",Map.of())).isEmpty();
 }
 @Test void currencyMustAppearInTheQuote(){
  assertThat(AiFactSuggester.accept(AiFactSuggester.Kind.CURRENCY,"eur","Sum insured 66.252,52 EUR")).isEqualTo("EUR");
  assertThat(AiFactSuggester.accept(AiFactSuggester.Kind.CURRENCY,"USD","Sum insured 66.252,52 EUR")).isNull();
 }
 @Test void everyAskedFieldCanActuallyBeStored(){
  for(var type:DocumentType.values())for(var spec:AiFactSuggester.specsFor(type,Set.of()))
   assertThat(de.ostms.lc.rulepack.RuleFacts.DOCUMENT).as(type+" "+spec.key()).contains(spec.field());
 }
}
