package de.corporate.lc.rulepack;
import de.corporate.lc.document.domain.DocumentType;
import java.util.List;
import java.util.Map;

/** Declarative data only. No expressions, scripts, reflection or plugin class names. */
public record PackDefinition(int schemaVersion, String packId, String version, String name,
    String origin, String license, String rightsStatement, List<Rule> rules, List<TestCase> tests) {
 public enum Field {
  DOCUMENT_AMOUNT, DOCUMENT_CURRENCY, DOCUMENT_DATE,
  LC_AMOUNT, LC_CURRENCY, LC_EXPIRY_DATE, LC_LATEST_SHIPMENT_DATE,
  DOCUMENT_ISSUER, DOCUMENT_RECIPIENT, DOCUMENT_GOODS_DESCRIPTION,
  LC_BENEFICIARY, LC_APPLICANT, LC_GOODS_DESCRIPTION,
  LC_RULE_STANDARD, LC_TRANSFERRED, LC_SECOND_BENEFICIARY;
  public boolean document(){return name().startsWith("DOCUMENT_");}
  public String kind(){return name().endsWith("AMOUNT")?"NUMBER":name().endsWith("CURRENCY")?"CURRENCY":name().endsWith("DATE")?"DATE":this==LC_TRANSFERRED?"BOOLEAN":"TEXT";}
 }
 public enum Operator { EQ, NE, LTE, GTE }
 public enum Outcome { PASS, FAIL, NOT_EVALUABLE, NOT_APPLICABLE, MANUAL_REVIEW }
 public enum Mode { AUTOMATIC, MANUAL }
 public record Condition(Field field,Operator operator,String value){}
 public enum Level { WARNING, DISCREPANCY }
 public record Rule(String id,String version,DocumentType documentType,Field left,
                    Operator operator,Field right,Level severity,String message,String sourceReference,
                    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) Mode mode,
                    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) List<Condition> conditions){
  public Rule(String id,String version,DocumentType documentType,Field left,Operator operator,Field right,Level severity,String message,String sourceReference){this(id,version,documentType,left,operator,right,severity,message,sourceReference,null,null);}
  public Mode effectiveMode(){return mode==null?Mode.AUTOMATIC:mode;}
 }
 public record TestCase(String name,String ruleId,String left,String right,Outcome expected,
  @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) Map<Field,String> facts){
  public TestCase(String name,String ruleId,String left,String right,Outcome expected){this(name,ruleId,left,right,expected,null);}
 }
}
