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
    default->currency(left).compareTo(currency(right));
   };
   boolean pass=switch(rule.operator()){
    case EQ->comparison==0;case NE->comparison!=0;case LTE->comparison<=0;case GTE->comparison>=0;
   };
   return pass?Outcome.PASS:Outcome.FAIL;
  }catch(RuntimeException invalid){return Outcome.NOT_EVALUABLE;}
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
 public static List<TestResult> test(PackDefinition pack){
  return pack.tests().stream().map(t->{
   var rule=pack.rules().stream().filter(r->r.id().equals(t.ruleId())).findFirst().orElseThrow();
   var actual=compare(rule,t.left(),t.right());
   return new TestResult(t.name(),t.ruleId(),t.expected(),actual,t.expected()==actual);
  }).toList();
 }
}
