package de.corporate.lc.rulepack;
import de.corporate.lc.document.domain.DocumentType;
import java.util.List;
import java.util.Map;

/** Declarative data only. No expressions, scripts, reflection or plugin class names. */
public record PackDefinition(int schemaVersion, String packId, String version, String name,
    String origin, String license, String rightsStatement, List<Rule> rules, List<TestCase> tests,
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) List<BankCalendar> calendars) {
 public PackDefinition(int schemaVersion,String packId,String version,String name,String origin,String license,String rightsStatement,List<Rule> rules,List<TestCase> tests){this(schemaVersion,packId,version,name,origin,license,rightsStatement,rules,tests,null);}
 public enum Field {
  DOCUMENT_AMOUNT, DOCUMENT_CURRENCY, DOCUMENT_DATE,
  LC_AMOUNT, LC_CURRENCY, LC_EXPIRY_DATE, LC_LATEST_SHIPMENT_DATE,
  DOCUMENT_ISSUER, DOCUMENT_RECIPIENT, DOCUMENT_GOODS_DESCRIPTION,
  LC_BENEFICIARY, LC_APPLICANT, LC_GOODS_DESCRIPTION,
  LC_RULE_STANDARD, LC_TRANSFERRED, LC_SECOND_BENEFICIARY,
  DOCUMENT_SHIPMENT_DATE, DOCUMENT_PRESENTATION_GROUP, DOCUMENT_QUANTITY, DOCUMENT_QUANTITY_UNIT,
  DOCUMENT_NET_WEIGHT, DOCUMENT_GROSS_WEIGHT, DOCUMENT_WEIGHT_UNIT,
  DOCUMENT_INSURED_AMOUNT, DOCUMENT_INSURANCE_CURRENCY, DOCUMENT_INSURANCE_EFFECTIVE_DATE,
  DOCUMENT_INSURANCE_RISKS, DOCUMENT_SIGNED, DOCUMENT_ORIGINAL_COUNT,
  LC_PRESENTATION_DATE, LC_PRESENTATION_PERIOD_DAYS, LC_INSURANCE_MIN_PERCENT, LC_TOLERANCE_PERCENT,
  LC_SIGNATURE_REQUIRED, LC_REQUIRED_ORIGINAL_COUNT, LC_INSURANCE_RISKS,
  PEER_AMOUNT, PEER_CURRENCY, PEER_SHIPMENT_DATE, PEER_GOODS_DESCRIPTION, PEER_QUANTITY, PEER_QUANTITY_UNIT,
  PEER_NET_WEIGHT, PEER_GROSS_WEIGHT, PEER_WEIGHT_UNIT,
  DOCUMENT_EXAMINATION_START_DATE, LC_EXAMINATION_DECISION_DATE;
  public boolean document(){return name().startsWith("DOCUMENT_");}
  public boolean peer(){return name().startsWith("PEER_");}
  public String kind(){return name().endsWith("AMOUNT")||name().endsWith("QUANTITY")||name().endsWith("WEIGHT")||name().endsWith("COUNT")||name().endsWith("DAYS")||name().endsWith("PERCENT")?"NUMBER":name().endsWith("CURRENCY")?"CURRENCY":name().endsWith("DATE")?"DATE":this==LC_TRANSFERRED||this==DOCUMENT_SIGNED||this==LC_SIGNATURE_REQUIRED?"BOOLEAN":"TEXT";}
 }
 public enum Operator { EQ, NE, LTE, GTE, WITHIN_DAYS, PERCENT_GTE, PERCENT_LTE, WITHIN_TOLERANCE }
 public enum Outcome { PASS, FAIL, NOT_EVALUABLE, NOT_APPLICABLE, MANUAL_REVIEW }
 public enum Mode { AUTOMATIC, MANUAL }
 public record Condition(Field field,Operator operator,String value){}
 public record BankCalendar(String id,String coveredFrom,String coveredTo,List<java.time.DayOfWeek> closedWeekdays,List<String> closedDates){}
 public record Parameters(
  @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) de.corporate.lc.document.domain.DocumentType peerDocumentType,
  @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) Integer days,
  @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) Field daysField,
  @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) String calendarId,
  @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) java.math.BigDecimal percent,
  @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) Field percentField){}
 public enum Level { WARNING, DISCREPANCY }
 public record Rule(String id,String version,DocumentType documentType,Field left,
                    Operator operator,Field right,Level severity,String message,String sourceReference,
                    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) Mode mode,
                    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) List<Condition> conditions,
                    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) Parameters parameters){
  public Rule(String id,String version,DocumentType documentType,Field left,Operator operator,Field right,Level severity,String message,String sourceReference){this(id,version,documentType,left,operator,right,severity,message,sourceReference,null,null,null);}
  public Rule(String id,String version,DocumentType documentType,Field left,Operator operator,Field right,Level severity,String message,String sourceReference,Mode mode,List<Condition> conditions){this(id,version,documentType,left,operator,right,severity,message,sourceReference,mode,conditions,null);}
  public Mode effectiveMode(){return mode==null?Mode.AUTOMATIC:mode;}
 }
 public record TestCase(String name,String ruleId,String left,String right,Outcome expected,
  @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) Map<Field,String> facts){
  public TestCase(String name,String ruleId,String left,String right,Outcome expected){this(name,ruleId,left,right,expected,null);}
 }
}
