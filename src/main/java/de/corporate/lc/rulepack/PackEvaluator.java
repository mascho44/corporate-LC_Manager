package de.corporate.lc.rulepack;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static de.corporate.lc.rulepack.PackDefinition.*;

public final class PackEvaluator {
 private PackEvaluator(){}
 public static Outcome compare(Rule rule,String left,String right){
  if(left==null||right==null||left.isBlank()||right.isBlank())return Outcome.NOT_EVALUABLE;
  try{
   int comparison=switch(rule.left().kind()){
    case "NUMBER"->number(left).compareTo(number(right));
    case "DATE"->LocalDate.parse(left).compareTo(LocalDate.parse(right));
    case "CURRENCY"->currency(left).compareTo(currency(right));
    case "BOOLEAN"->bool(left).compareTo(bool(right));
    default->text(left).compareTo(text(right));
   };
   boolean pass=switch(rule.operator()){
    case EQ->comparison==0;case NE->comparison!=0;case LTE->comparison<=0;case GTE->comparison>=0;
   };
   return pass?Outcome.PASS:Outcome.FAIL;
  }catch(RuntimeException invalid){return Outcome.NOT_EVALUABLE;}
 }
 public static Outcome evaluate(Rule rule,Map<Field,String> facts){
  boolean unknown=false,excluded=false;
  if(rule.conditions()!=null)for(var c:rule.conditions()){
   var probe=new Rule("condition","1.0.0",rule.documentType(),c.field(),c.operator(),c.field(),Level.WARNING,"condition","condition");
   var result=compare(probe,facts.get(c.field()),c.value());
   if(result==Outcome.FAIL)excluded=true;
   if(result==Outcome.NOT_EVALUABLE)unknown=true;
  }
  if(excluded)return Outcome.NOT_APPLICABLE;
  if(unknown)return Outcome.NOT_EVALUABLE;
  if(rule.effectiveMode()==Mode.MANUAL){
   if(facts.get(rule.left())==null||facts.get(rule.right())==null||facts.get(rule.left()).isBlank()||facts.get(rule.right()).isBlank())return Outcome.NOT_EVALUABLE;
   return Outcome.MANUAL_REVIEW;
  }
  return compare(rule,facts.get(rule.left()),facts.get(rule.right()));
 }
 private static String text(String value){return value.strip().replaceAll("\\s+"," ").toUpperCase(Locale.ROOT);}
 private static Boolean bool(String value){
  if(!"true".equals(value)&&!"false".equals(value))throw new IllegalArgumentException("Ungültiger Wahrheitswert");
  return Boolean.valueOf(value);
 }
 private static BigDecimal number(String value){
  if(!value.matches("-?[0-9]{1,19}(\\.[0-9]{1,6})?"))throw new IllegalArgumentException("Ungültiger Betrag");
  return new BigDecimal(value);
 }
 private static String currency(String value){
  String code=value.toUpperCase(Locale.ROOT);
  if(!code.matches("[A-Z]{3}"))throw new IllegalArgumentException("Ungültige Währung");
  Currency.getInstance(code);return code;
 }
 public record TestResult(String name,String ruleId,Outcome expected,Outcome actual,boolean passed){}
 static Map<Field,String> testFacts(Rule rule,TestCase test){
  var facts=new EnumMap<Field,String>(Field.class);if(test.facts()!=null)facts.putAll(test.facts());
  facts.put(rule.left(),test.left());facts.put(rule.right(),test.right());return facts;
 }
 public static List<TestResult> test(PackDefinition pack){
  return pack.tests().stream().map(t->{
   var rule=pack.rules().stream().filter(r->r.id().equals(t.ruleId())).findFirst().orElseThrow();
   var facts=testFacts(rule,t);
   var actual=evaluate(rule,facts);
   return new TestResult(t.name(),t.ruleId(),t.expected(),actual,t.expected()==actual);
  }).toList();
 }
}
