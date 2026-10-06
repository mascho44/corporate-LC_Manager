package de.corporate.lc.rulepack;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static de.corporate.lc.rulepack.PackDefinition.*;
class PackCodecTest {
 final PackCodec codec=new PackCodec(new ObjectMapper().findAndRegisterModules());
 static byte[] example()throws Exception{
  try(var r=PackCodecTest.class.getResourceAsStream("/static/rule-pack-example.json")){return r.readAllBytes();}
 }
 @Test void exampleIsStrictlyDeclarativeAndTestsPass()throws Exception{
  var pack=codec.parse(example());assertThat(PackEvaluator.test(pack)).allMatch(PackEvaluator.TestResult::passed);
  assertThat(codec.digest(codec.canonical(pack))).hasSize(64);
 }
 @Test void unknownFieldsDuplicateKeysAndOversizedBodyAreRejected()throws Exception{
  String source=new String(example(),StandardCharsets.UTF_8);
  assertThatThrownBy(()->codec.parse(source.replace("\"schemaVersion\": 1","\"script\":\"run()\",\"schemaVersion\":1").getBytes(StandardCharsets.UTF_8))).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->codec.parse(source.replace("\"schemaVersion\": 1","\"schemaVersion\":1,\"schemaVersion\":1").getBytes(StandardCharsets.UTF_8))).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->codec.parse(new byte[PackCodec.MAX_BYTES+1])).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void unknownOriginsAndUnboundedExpressionsAreRejected()throws Exception{
  String source=new String(example(),StandardCharsets.UTF_8);
  for(String changed:List.of(source.replace("OWN_INTERNAL","UNKNOWN"),source.replace("LTE","SCRIPT"),source.replace("DOCUMENT_AMOUNT","getClass"))){
   assertThatThrownBy(()->codec.parse(changed.getBytes(StandardCharsets.UTF_8))).isInstanceOf(IllegalArgumentException.class);
  }
 }
 @Test void licensedOriginAndSourceReferencesAreAllowedWithoutBundlingPublicationText()throws Exception{
  String source=new String(example(),StandardCharsets.UTF_8).replace("OWN_INTERNAL","ICC_LICENSED").replace("DEMO-01","ISBP 821");
  var pack=codec.parse(source.getBytes(StandardCharsets.UTF_8));
  assertThat(pack.origin()).isEqualTo("ICC_LICENSED");
  assertThat(pack.rules().get(0).sourceReference()).contains("ISBP 821");
  assertThat(PackEvaluator.test(pack)).allMatch(PackEvaluator.TestResult::passed);
 }
 @Test void wrongFieldTypesAndMissingCoverageAreRejected()throws Exception{
  String source=new String(example(),StandardCharsets.UTF_8);
  assertThatThrownBy(()->codec.parse(source.replace("LC_AMOUNT","LC_CURRENCY").getBytes(StandardCharsets.UTF_8))).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->codec.parse(source.replace("\"expected\":\"FAIL\"","\"expected\":\"PASS\"").getBytes(StandardCharsets.UTF_8))).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void invalidNumbersAndDatesCannotProduceSuccess()throws Exception{
  var r=codec.parse(example()).rules().get(0);
  for(String invalid:List.of("NaN","Infinity","1e100000","9999999999999999999999999999999","1,20","")){
   assertThat(PackEvaluator.compare(r,invalid,"100")).isEqualTo(Outcome.NOT_EVALUABLE);
  }
  var date=new Rule("date-check","1.0.0",r.documentType(),Field.DOCUMENT_DATE,Operator.LTE,Field.LC_EXPIRY_DATE,Level.WARNING,"Date","Own policy");
  assertThat(PackEvaluator.compare(date,"2026-02-30","2026-03-01")).isEqualTo(Outcome.NOT_EVALUABLE);
  assertThat(PackEvaluator.compare(date,"2026-03-01","2026-03-01")).isEqualTo(Outcome.PASS);
  assertThat(PackEvaluator.compare(date,"2026-03-02","2026-03-01")).isEqualTo(Outcome.FAIL);
 }
}
