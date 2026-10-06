package de.corporate.lc.rulepack;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static de.corporate.lc.rulepack.PackDefinition.*;
class PackV4Test {
 final PackCodec codec=new PackCodec(new ObjectMapper());
 @Test void basisUsesExplicitValueOrBothFallbackValues(){
  assertThat(ExtendedRuleFacts.insuranceBasis(Map.of(Field.LC_CIF_CIP_VALUE_AMOUNT,"1500",Field.LC_CLAIMED_AMOUNT,"1700"))).isEqualTo("1500");
  assertThat(ExtendedRuleFacts.insuranceBasis(Map.of(Field.LC_CLAIMED_AMOUNT,"1000",Field.LC_GROSS_GOODS_AMOUNT,"1200"))).isEqualTo("1200");
  assertThat(ExtendedRuleFacts.insuranceBasis(Map.of(Field.LC_CLAIMED_AMOUNT,"1300",Field.LC_GROSS_GOODS_AMOUNT,"1200"))).isEqualTo("1300");
  assertThat(ExtendedRuleFacts.insuranceBasis(Map.of(Field.LC_CLAIMED_AMOUNT,"1000"))).isNull();
  assertThat(ExtendedRuleFacts.insuranceBasis(Map.of(Field.LC_CIF_CIP_VALUE_AMOUNT,"0",Field.LC_CLAIMED_AMOUNT,"1000",Field.LC_GROSS_GOODS_AMOUNT,"1200"))).isNull();
  assertThat(ExtendedRuleFacts.insuranceBasis(Map.of(Field.LC_CLAIMED_AMOUNT,"-1",Field.LC_GROSS_GOODS_AMOUNT,"1200"))).isNull();
 }
 @Test void derivedBasisCannotBeOverwrittenAndNewFactsAreTyped(){
  assertThatThrownBy(()->RuleFacts.encode(Map.of(Field.LC_INSURANCE_BASE_AMOUNT,"100"),false)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->RuleFacts.encode(Map.of(Field.DOCUMENT_ISSUED_ORIGINAL_COUNT,"1.5"),true)).isInstanceOf(IllegalArgumentException.class);
  assertThat(RuleFacts.definitions(true)).allMatch(d->d.label()!=null&&!d.label().isBlank());
  assertThat(RuleFacts.read(RuleFacts.encode(Map.of(Field.LC_CLAIMED_AMOUNT,"123.45"),false))).containsEntry(Field.LC_CLAIMED_AMOUNT,"123.45");
 }
 @Test void schemaFourDemoPassesAndCannotBeDowngraded()throws Exception{
  try(var input=getClass().getResourceAsStream("/static/rule-pack-example-v4.json")){
   var p=codec.parse(input.readAllBytes());assertThat(PackEvaluator.test(p)).allMatch(PackEvaluator.TestResult::passed);
   assertThatThrownBy(()->codec.validate(new PackDefinition(3,p.packId(),p.version(),p.name(),p.origin(),p.license(),p.rightsStatement(),p.rules(),p.tests()))).isInstanceOf(IllegalArgumentException.class);
  }
 }
}
