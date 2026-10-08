package de.ostms.lc.rulepack;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static de.ostms.lc.rulepack.PackDefinition.*;
class PackV2Test {
 final PackCodec codec=new PackCodec(new ObjectMapper());
 PackDefinition demo()throws Exception{
  try(var input=getClass().getResourceAsStream("/static/rule-pack-example-v2.json")){return codec.parse(input.readAllBytes());}
 }
 @Test void demoPassesAllScopeAndManualTests()throws Exception{
  var pack=demo();assertThat(PackEvaluator.test(pack)).hasSize(15).allMatch(PackEvaluator.TestResult::passed);
 }
 @Test void missingConditionCannotPassAndFalseConditionNeverReportsSuccess()throws Exception{
  var rule=demo().rules().get(0);
  var facts=new EnumMap<Field,String>(Field.class);facts.put(Field.DOCUMENT_ISSUER,"Demo");facts.put(Field.LC_BENEFICIARY,"Demo");
  assertThat(PackEvaluator.evaluate(rule,facts)).isEqualTo(Outcome.NOT_EVALUABLE);
  facts.put(Field.LC_TRANSFERRED,"true");assertThat(PackEvaluator.evaluate(rule,facts)).isEqualTo(Outcome.NOT_APPLICABLE);
  facts.put(Field.LC_TRANSFERRED,"invalid");assertThat(PackEvaluator.evaluate(rule,facts)).isEqualTo(Outcome.NOT_EVALUABLE);
  facts.put(Field.LC_TRANSFERRED,"false");assertThat(PackEvaluator.evaluate(rule,facts)).isEqualTo(Outcome.PASS);
 }
 @Test void manualGoodsNeverAutomaticallyPassEvenWhenTextIsEqual()throws Exception{
  var rule=demo().rules().get(2);var facts=new EnumMap<Field,String>(Field.class);
  facts.put(Field.LC_TRANSFERRED,"false");facts.put(Field.DOCUMENT_GOODS_DESCRIPTION,"same");facts.put(Field.LC_GOODS_DESCRIPTION,"same");
  assertThat(PackEvaluator.evaluate(rule,facts)).isEqualTo(Outcome.MANUAL_REVIEW);
  facts.remove(Field.DOCUMENT_GOODS_DESCRIPTION);assertThat(PackEvaluator.evaluate(rule,facts)).isEqualTo(Outcome.NOT_EVALUABLE);
  var unsafe=new Rule(rule.id(),rule.version(),rule.documentType(),rule.left(),rule.operator(),rule.right(),rule.severity(),rule.message(),rule.sourceReference(),Mode.AUTOMATIC,rule.conditions());
  var pack=demo();
  assertThatThrownBy(()->codec.validate(new PackDefinition(2,pack.packId(),pack.version(),pack.name(),pack.origin(),pack.license(),pack.rightsStatement(),List.of(unsafe),pack.tests()))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("manuelle");
 }
 @Test void oldSchemaRemainsCanonicalAndRejectsNewFeatures()throws Exception{
  var old=codec.parse(PackCodecTest.example());var json=codec.canonical(old);
  assertThat(json).doesNotContain("\"mode\"","\"conditions\"","\"facts\"");
  var p=demo();assertThatThrownBy(()->codec.validate(new PackDefinition(1,p.packId(),p.version(),p.name(),p.origin(),p.license(),p.rightsStatement(),p.rules(),p.tests()))).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void supplementaryFactsHaveBoundedTypedScopes(){
  assertThat(RuleFacts.read(RuleFacts.encode(Map.of(Field.LC_TRANSFERRED,"false"),false))).containsEntry(Field.LC_TRANSFERRED,"false");
  assertThatThrownBy(()->RuleFacts.encode(Map.of(Field.DOCUMENT_ISSUER,"Demo"),false)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->RuleFacts.encode(Map.of(Field.LC_TRANSFERRED,"yes"),false)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->RuleFacts.encode(Map.of(Field.DOCUMENT_ISSUER,"x".repeat(501)),true)).isInstanceOf(IllegalArgumentException.class);
  assertThat(RuleFacts.read(RuleFacts.encode(Map.of(Field.DOCUMENT_ISSUER,""),true))).isEmpty();
  assertThatThrownBy(()->RuleFacts.read("{bad")).isInstanceOf(IllegalStateException.class);
  assertThatThrownBy(()->RuleFacts.decodeRequest(new byte[RuleFacts.MAX_BYTES+1])).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->RuleFacts.decodeRequest("{\"LC_TRANSFERRED\":\"false\",\"LC_TRANSFERRED\":\"true\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8))).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->RuleFacts.decodeRequest("{\"LC_TRANSFERRED\":false}".getBytes(java.nio.charset.StandardCharsets.UTF_8))).isInstanceOf(IllegalArgumentException.class);
 }
}
