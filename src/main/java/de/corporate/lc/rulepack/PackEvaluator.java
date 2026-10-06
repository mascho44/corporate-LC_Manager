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
    default->throw new IllegalArgumentException("Erweiterter Vergleich benötigt Kontext.");
   };
   return pass?Outcome.PASS:Outcome.FAIL;
  }catch(RuntimeException invalid){return Outcome.NOT_EVALUABLE;}
 }
 public static Outcome evaluate(Rule rule,Map<Field,String> facts){
  return evaluate(rule,facts,List.of(),false);
 }
 public static Outcome evaluate(Rule rule,Map<Field,String> facts,List<BankCalendar> calendars,boolean unitChecks){
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
  try{
   String left=facts.get(rule.left()),right=facts.get(rule.right());
   if(left==null||right==null||left.isBlank()||right.isBlank())return Outcome.NOT_EVALUABLE;
   if(unitChecks&&!compatibleUnits(rule,facts))return Outcome.NOT_EVALUABLE;
   if(rule.operator()==Operator.WITHIN_DAYS){
    var params=rule.parameters();if(params==null)return Outcome.NOT_EVALUABLE;
    int limit=params.days()!=null?params.days():number(facts.get(params.daysField())).intValueExact();
    if(limit<0||limit>3660)return Outcome.NOT_EVALUABLE;
    var start=LocalDate.parse(left);var end=LocalDate.parse(right);
    long elapsed=java.time.temporal.ChronoUnit.DAYS.between(start,end);
    if(elapsed<0)return Outcome.FAIL;
    if(elapsed>3660)return Outcome.NOT_EVALUABLE;
    if(params.calendarId()!=null){
     var calendar=calendars.stream().filter(c->c.id().equals(params.calendarId())).findFirst().orElseThrow();
     if(start.isBefore(LocalDate.parse(calendar.coveredFrom()))||end.isAfter(LocalDate.parse(calendar.coveredTo())))return Outcome.NOT_EVALUABLE;
     var closed=new HashSet<>(calendar.closedDates());elapsed=0;
     for(var day=start.plusDays(1);!day.isAfter(end);day=day.plusDays(1))if(!calendar.closedWeekdays().contains(day.getDayOfWeek())&&!closed.contains(day.toString()))elapsed++;
    }
    return elapsed<=limit?Outcome.PASS:Outcome.FAIL;
   }
   if(Set.of(Operator.PERCENT_GTE,Operator.PERCENT_LTE,Operator.WITHIN_TOLERANCE).contains(rule.operator())){
    var params=rule.parameters();if(params==null)return Outcome.NOT_EVALUABLE;
    var percent=params.percent()!=null?params.percent():number(facts.get(params.percentField()));
    if(percent.signum()<0||percent.compareTo(new BigDecimal("1000"))>0)return Outcome.NOT_EVALUABLE;
    var actual=number(left);var basis=number(right);
    if(actual.signum()<0||basis.signum()<=0)return Outcome.NOT_EVALUABLE;
    var threshold=basis.multiply(percent).divide(new BigDecimal("100"));
    boolean pass=switch(rule.operator()){
     case PERCENT_GTE->actual.compareTo(threshold)>=0;
     case PERCENT_LTE->actual.compareTo(threshold)<=0;
     case WITHIN_TOLERANCE->actual.subtract(basis).abs().compareTo(threshold)<=0;
     default->false;
    };
    return pass?Outcome.PASS:Outcome.FAIL;
   }
  }catch(RuntimeException invalid){return Outcome.NOT_EVALUABLE;}
  return compare(rule,facts.get(rule.left()),facts.get(rule.right()));
 }
 private static boolean compatibleUnits(Rule rule,Map<Field,String> facts){
  Field l=unit(rule.left()),r=unit(rule.right());if(l==null&&r==null)return true;
  if(l==null||r==null)return false;
  String a=facts.get(l),b=facts.get(r);if(a==null||b==null||a.isBlank()||b.isBlank())return false;
  return l.kind().equals("CURRENCY")?currency(a).equals(currency(b)):text(a).equals(text(b));
 }
 private static Field unit(Field field){return switch(field){
  case DOCUMENT_AMOUNT->Field.DOCUMENT_CURRENCY;case DOCUMENT_INSURED_AMOUNT->Field.DOCUMENT_INSURANCE_CURRENCY;
  case LC_AMOUNT->Field.LC_CURRENCY;case PEER_AMOUNT->Field.PEER_CURRENCY;
  case DOCUMENT_QUANTITY->Field.DOCUMENT_QUANTITY_UNIT;case PEER_QUANTITY->Field.PEER_QUANTITY_UNIT;
  case DOCUMENT_NET_WEIGHT,DOCUMENT_GROSS_WEIGHT->Field.DOCUMENT_WEIGHT_UNIT;
  case PEER_NET_WEIGHT,PEER_GROSS_WEIGHT->Field.PEER_WEIGHT_UNIT;
  default->null;
 };}
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
   var actual=evaluate(rule,facts,pack.calendars()==null?List.of():pack.calendars(),pack.schemaVersion()>=3);
   return new TestResult(t.name(),t.ruleId(),t.expected(),actual,t.expected()==actual);
  }).toList();
 }
}
