package de.corporate.lc.rulepack;
import de.corporate.lc.document.domain.DocumentType;
import java.util.List;

/** Declarative data only. No expressions, scripts, reflection or plugin class names. */
public record PackDefinition(int schemaVersion, String packId, String version, String name,
    String origin, String license, String rightsStatement, List<Rule> rules, List<TestCase> tests) {
 public enum Field {
  DOCUMENT_AMOUNT, DOCUMENT_CURRENCY, DOCUMENT_DATE,
  LC_AMOUNT, LC_CURRENCY, LC_EXPIRY_DATE, LC_LATEST_SHIPMENT_DATE;
  public boolean document(){return name().startsWith("DOCUMENT_");}
  public String kind(){return name().endsWith("AMOUNT")?"NUMBER":name().endsWith("CURRENCY")?"TEXT":"DATE";}
 }
 public enum Operator { EQ, NE, LTE, GTE }
 public enum Outcome { PASS, FAIL, NOT_EVALUABLE }
 public enum Level { WARNING, DISCREPANCY }
 public record Rule(String id,String version,DocumentType documentType,Field left,
                    Operator operator,Field right,Level severity,String message,String sourceReference){}
 public record TestCase(String name,String ruleId,String left,String right,Outcome expected){}
}
