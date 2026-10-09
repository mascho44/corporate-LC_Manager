package de.ostms.lc.rulepack;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static de.ostms.lc.rulepack.PackDefinition.*;
import de.ostms.lc.document.domain.DocumentType;

class PackV5Test {
 final PackCodec codec=new PackCodec(new ObjectMapper().findAndRegisterModules());
 Rule literal(Field left,Operator op,String value,Parameters params){
  return new Rule("synthetic-check","1.0.0",DocumentType.SEA_WAYBILL,left,op,Field.LITERAL,Level.WARNING,"Synthetic check","Internal synthetic test",Mode.AUTOMATIC,null,params,value);
 }
 PackDefinition pack(Rule rule,List<TestCase> tests){return new PackDefinition(5,"synthetic-v-five","1.0.0","Synthetic","OWN_INTERNAL","Internal","Synthetic test only",List.of(rule),tests);}
 List<TestCase> cases(String pass,String fail){return List.of(new TestCase("pass","synthetic-check",pass,null,Outcome.PASS),new TestCase("fail","synthetic-check",fail,null,Outcome.FAIL),new TestCase("unknown","synthetic-check",null,null,Outcome.NOT_EVALUABLE));}
 @Test void booleanAndTextLiteralsAreTypedAndCannotBeOverriddenByTests(){
  for(var rule:List.of(literal(Field.DOCUMENT_SIGNED,Operator.EQ,"true",null),literal(Field.DOCUMENT_SIGNER_ROLE,Operator.EQ,"Carrier",null))){
   var tests=rule.left()==Field.DOCUMENT_SIGNED?cases("true","false"):cases(" carrier ","agent");
   var p=pack(rule,tests);codec.validate(p);assertThat(PackEvaluator.test(p)).allMatch(PackEvaluator.TestResult::passed);
   assertThatThrownBy(()->codec.validate(pack(rule,List.of(new TestCase("override",rule.id(),"false","false",Outcome.PASS))))).isInstanceOf(IllegalArgumentException.class);
  }
  assertThatThrownBy(()->codec.validate(pack(literal(Field.DOCUMENT_SIGNED,Operator.EQ,"yes",null),cases("true","false")))).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->codec.validate(pack(literal(Field.DOCUMENT_AMOUNT,Operator.EQ,"10",null),cases("10","11")))).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void listsNormalizeTextAndHandleUnknownWithoutTreatingItAsNotIn(){
  var params=new Parameters(null,null,null,null,null,null,List.of("CARRIER","AGENT FOR CARRIER"));
  for(var op:List.of(Operator.IN,Operator.NOT_IN)){
   var rule=literal(Field.DOCUMENT_SIGNER_ROLE,op,null,params);
   var p=pack(rule,op==Operator.IN?cases("agent   for carrier","other"):cases("other","carrier"));
   codec.validate(p);assertThat(PackEvaluator.test(p)).allMatch(PackEvaluator.TestResult::passed);
   assertThat(PackEvaluator.evaluate(rule,Map.of(Field.DOCUMENT_SIGNER_ROLE,""))).isEqualTo(Outcome.NOT_EVALUABLE);
  }
  var empty=literal(Field.DOCUMENT_SIGNER_ROLE,Operator.IN,null,new Parameters(null,null,null,null,null,null,List.of()));
  assertThatThrownBy(()->codec.validate(pack(empty,cases("carrier","other")))).isInstanceOf(IllegalArgumentException.class);
  var duplicate=literal(Field.DOCUMENT_SIGNER_ROLE,Operator.IN,null,new Parameters(null,null,null,null,null,null,List.of("CARRIER","CARRIER")));
  assertThatThrownBy(()->codec.validate(pack(duplicate,cases("carrier","other")))).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->codec.validate(pack(literal(Field.DOCUMENT_SIGNER_ROLE,Operator.IN,"different",params),cases("carrier","other")))).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void newFactsArePersistableAndAllFormFieldsFitRequestLimit(){
  var values=new EnumMap<Field,String>(Field.class);
  RuleFacts.definitions(true).forEach(d->values.put(d.field(),""));
  assertThat(values.size()).isGreaterThan(96);
  assertThat(RuleFacts.read(RuleFacts.encode(values,true))).isEmpty();
  assertThat(RuleFacts.read(RuleFacts.encode(Map.of(Field.DOCUMENT_CARRIER_INDICATED,"false"),true))).containsEntry(Field.DOCUMENT_CARRIER_INDICATED,"false");
  assertThat(RuleFacts.read(RuleFacts.encode(Map.of(Field.LC_EXTENDED_EXPIRY_DATE,"2026-12-31"),false))).containsEntry(Field.LC_EXTENDED_EXPIRY_DATE,"2026-12-31");
  assertThatThrownBy(()->RuleFacts.encode(Map.of(Field.LITERAL,"true"),true)).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void claimedAmountComparisonRequiresTheSameKnownCurrency(){
  var rule=new Rule("synthetic-claimed","1.0.0",DocumentType.BILL_OF_EXCHANGE,Field.DOCUMENT_AMOUNT,Operator.EQ,Field.LC_CLAIMED_AMOUNT,Level.WARNING,"Synthetic amount","Internal synthetic");
  var values=new EnumMap<Field,String>(Field.class);
  values.put(Field.DOCUMENT_AMOUNT,"100");values.put(Field.LC_CLAIMED_AMOUNT,"100");
  values.put(Field.DOCUMENT_CURRENCY,"EUR");values.put(Field.LC_CURRENCY,"EUR");
  assertThat(PackEvaluator.evaluate(rule,values,List.of(),true)).isEqualTo(Outcome.PASS);
  values.put(Field.DOCUMENT_AMOUNT,"101");
  assertThat(PackEvaluator.evaluate(rule,values,List.of(),true)).isEqualTo(Outcome.FAIL);
  values.put(Field.LC_CURRENCY,"USD");
  assertThat(PackEvaluator.evaluate(rule,values,List.of(),true)).isEqualTo(Outcome.NOT_EVALUABLE);
  values.remove(Field.LC_CURRENCY);
  assertThat(PackEvaluator.evaluate(rule,values,List.of(),true)).isEqualTo(Outcome.NOT_EVALUABLE);
  values.put(Field.LC_CURRENCY,"EUR");values.remove(Field.DOCUMENT_CURRENCY);
  assertThat(PackEvaluator.evaluate(rule,values,List.of(),true)).isEqualTo(Outcome.NOT_EVALUABLE);
 }
 @Test void schemaFourCannotSilentlyEnableNewCapabilities(){
  var p=pack(literal(Field.DOCUMENT_SIGNED,Operator.EQ,"true",null),cases("true","false"));
  assertThatThrownBy(()->codec.validate(new PackDefinition(4,p.packId(),p.version(),p.name(),p.origin(),p.license(),p.rightsStatement(),p.rules(),p.tests()))).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void specificationInspectionIsReadOnlyAndStrict()throws Exception{
  String spec="{\"specVersion\":\"1.0.0\",\"rules\":[{\"id\":\"synthetic-check\",\"version\":\"1.0.0\",\"documentType\":\"SEA_WAYBILL\",\"left\":\"DOCUMENT_SIGNED\",\"operator\":\"EQ\",\"right\":\"LITERAL\",\"rightValue\":\"true\",\"severity\":\"WARNING\",\"message\":\"Synthetic\",\"sourceReference\":\"Internal\",\"status\":\"NEEDS_ENGINE\",\"requires\":[\"LITERAL\"]}]}";
  var report=(RuleSpecificationInspector.Report)codec.inspectSpecification(spec.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  assertThat(report.supported()).isEqualTo(1);assertThat(report.importable()).isFalse();
  assertThatThrownBy(()->codec.parse(spec.getBytes(java.nio.charset.StandardCharsets.UTF_8))).isInstanceOf(IllegalArgumentException.class);
  var invalid=(RuleSpecificationInspector.Report)codec.inspectSpecification(spec.replace("\"status\":","\"script\":").getBytes(java.nio.charset.StandardCharsets.UTF_8));
  assertThat(invalid.supported()).isZero();assertThat(invalid.issues()).hasSize(1);
 }
}
