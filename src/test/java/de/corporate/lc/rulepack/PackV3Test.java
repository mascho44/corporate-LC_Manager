package de.corporate.lc.rulepack;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.corporate.lc.document.domain.DocumentType;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static de.corporate.lc.rulepack.PackDefinition.*;
class PackV3Test {
 final PackCodec codec=new PackCodec(new ObjectMapper());
 PackDefinition demo()throws Exception{
  try(var input=getClass().getResourceAsStream("/static/rule-pack-example-v3.json")){return codec.parse(input.readAllBytes());}
 }
 @Test void syntheticExamplesCoverBoundariesAndUnknownInputs()throws Exception{
  var pack=demo();assertThat(pack.rules()).hasSize(10);
  assertThat(PackEvaluator.test(pack)).hasSize(44).allMatch(PackEvaluator.TestResult::passed);
 }
 @Test void datesAndCountsAreStrictlyTyped(){
  assertThatThrownBy(()->RuleFacts.encode(Map.of(Field.DOCUMENT_SHIPMENT_DATE,"2026-02-30"),true)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->RuleFacts.encode(Map.of(Field.DOCUMENT_ORIGINAL_COUNT,"1.5"),true)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->RuleFacts.encode(Map.of(Field.DOCUMENT_SIGNED,"yes"),true)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->RuleFacts.encode(Map.of(Field.DOCUMENT_QUANTITY,"-1"),true)).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void requirementsAreIsolatedByDocumentType(){
  var value=RuleRequirements.update(null,DocumentType.BILL_OF_LADING,Map.of(Field.LC_REQUIRED_ORIGINAL_COUNT,"3"));
  value=RuleRequirements.update(value,DocumentType.INSURANCE_CERTIFICATE,Map.of(Field.LC_SIGNATURE_REQUIRED,"true"));
  var parsed=RuleRequirements.read(value);
  assertThat(parsed.get(DocumentType.BILL_OF_LADING)).containsOnlyKeys(Field.LC_REQUIRED_ORIGINAL_COUNT);
  assertThat(parsed.get(DocumentType.INSURANCE_CERTIFICATE)).containsOnlyKeys(Field.LC_SIGNATURE_REQUIRED);
  assertThatThrownBy(()->RuleRequirements.update(null,DocumentType.BILL_OF_LADING,Map.of(Field.LC_AMOUNT,"100"))).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void calendarsAndParametersDoNotChangeLegacySerialization()throws Exception{
  var old=codec.parse(PackCodecTest.example());
  assertThat(codec.canonical(old)).doesNotContain("\"parameters\"","\"calendars\"");
  var p=demo();assertThatThrownBy(()->codec.validate(new PackDefinition(2,p.packId(),p.version(),p.name(),p.origin(),p.license(),p.rightsStatement(),p.rules(),p.tests(),p.calendars()))).isInstanceOf(IllegalArgumentException.class);
 }
}
