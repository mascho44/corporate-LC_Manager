package de.ostms.lc.rulepack;
import de.ostms.lc.document.domain.DocumentType;
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
  DOCUMENT_EXAMINATION_START_DATE, LC_EXAMINATION_DECISION_DATE,
  DOCUMENT_CARRIER, DOCUMENT_SIGNER_ROLE, DOCUMENT_SIGNED_FOR, DOCUMENT_LOADING_PORT, DOCUMENT_DISCHARGE_PORT,
  DOCUMENT_DEPARTURE_AIRPORT, DOCUMENT_DESTINATION_AIRPORT, DOCUMENT_VESSEL, DOCUMENT_TRANSPORT_NOTATION,
  DOCUMENT_ISSUED_ORIGINAL_COUNT, DOCUMENT_INSURANCE_TYPE, DOCUMENT_COVERAGE_FROM, DOCUMENT_COVERAGE_TO,
  DOCUMENT_UNIT_PRICE_AMOUNT, DOCUMENT_ORIGIN_COUNTRY,
  LC_CIF_CIP_VALUE_AMOUNT, LC_CLAIMED_AMOUNT, LC_GROSS_GOODS_AMOUNT, LC_INSURANCE_BASE_AMOUNT,
  LC_QUANTITY, LC_QUANTITY_UNIT, LC_UNIT_PRICE_AMOUNT,
  LC_LOADING_PORT, LC_DISCHARGE_PORT, LC_DEPARTURE_AIRPORT, LC_DESTINATION_AIRPORT,
  LC_COVERAGE_FROM, LC_COVERAGE_TO, LC_ORIGIN_COUNTRY, LC_DOCUMENT_ISSUED_ORIGINAL_COUNT,
  DOCUMENT_PRESENTATION_DATE,
  DOCUMENT_TRANSSHIPMENT_INDICATED,
  LC_TRANSSHIPMENT_ALLOWED,
  LC_PARTIAL_SHIPMENT_ALLOWED,
  DOCUMENT_SHIPMENT_COUNT,
  DOCUMENT_ON_BOARD_NOTATION_PRESENT,
  DOCUMENT_ON_BOARD_DATE,
  DOCUMENT_INTENDED_VESSEL_INDICATED,
  LC_ON_BOARD_NOTATION_REQUIRED,
  DOCUMENT_CONSIGNEE,
  DOCUMENT_NOTIFY_PARTY,
  DOCUMENT_APPLICANT_ADDRESS_COUNTRY,
  DOCUMENT_BENEFICIARY_ADDRESS_COUNTRY,
  LC_APPLICANT_ADDRESS_COUNTRY,
  LC_BENEFICIARY_ADDRESS_COUNTRY,
  LC_CONSIGNEE,
  LC_NOTIFY_PARTY,
  DOCUMENT_FREIGHT_PREPAID,
  DOCUMENT_FREIGHT_TERMS,
  LC_FREIGHT_PREPAID_REQUIRED,
  LC_FREIGHT_TERMS,
  DOCUMENT_INCOTERM,
  DOCUMENT_INCOTERM_SOURCE,
  LC_INCOTERM,
  LC_INCOTERM_SOURCE,
  LC_REQUIRED_ISSUER,
  DOCUMENT_INVOICE_REFERENCE,
  PEER_DOCUMENT_NUMBER,
  DOCUMENT_FRANCHISE_PRESENT,
  DOCUMENT_IRRESPECTIVE_OF_PERCENTAGE,
  LC_FRANCHISE_ALLOWED,
  LC_IRRESPECTIVE_OF_PERCENTAGE_REQUIRED,
  DOCUMENT_INSURED_PARTY,
  LC_INSURED_PARTY,
  DOCUMENT_ENDORSEMENT_PRESENT,
  DOCUMENT_ENDORSEMENT_TO,
  LC_ENDORSEMENT_REQUIRED,
  LC_ENDORSEMENT_TO,
  DOCUMENT_DRAWEE,
  LC_DRAWEE,
  DOCUMENT_DRAFT_TENOR_DAYS,
  LC_DRAFT_TENOR_DAYS,
  DOCUMENT_DRAFT_TENOR_BASIS,
  LC_DRAFT_TENOR_BASIS,
  DOCUMENT_PACKAGE_COUNT,
  PEER_PACKAGE_COUNT,
  DOCUMENT_SHIPPING_MARKS,
  PEER_SHIPPING_MARKS,
  DOCUMENT_SIGNER_NAME, DOCUMENT_NUMBER, DOCUMENT_PARTIAL_SHIPMENT_INDICATED,
  DOCUMENT_CONSIGNEE_ADDRESS_COUNTRY, DOCUMENT_NOTIFY_ADDRESS_COUNTRY,
  LC_CONSIGNEE_ADDRESS_COUNTRY, LC_NOTIFY_ADDRESS_COUNTRY;
  public boolean document(){return name().startsWith("DOCUMENT_");}
  public boolean peer(){return name().startsWith("PEER_");}
  public String kind(){return name().endsWith("AMOUNT")||name().endsWith("QUANTITY")||name().endsWith("WEIGHT")||name().endsWith("COUNT")||name().endsWith("DAYS")||name().endsWith("PERCENT")?"NUMBER":name().endsWith("CURRENCY")?"CURRENCY":name().endsWith("DATE")?"DATE":this==LC_TRANSFERRED||this==DOCUMENT_SIGNED||this==LC_SIGNATURE_REQUIRED||name().endsWith("ALLOWED")||name().endsWith("REQUIRED")||name().endsWith("PRESENT")||name().endsWith("INDICATED")||this==DOCUMENT_FREIGHT_PREPAID||this==DOCUMENT_IRRESPECTIVE_OF_PERCENTAGE?"BOOLEAN":"TEXT";}
 }
 public enum Operator { EQ, NE, LTE, GTE, WITHIN_DAYS, PERCENT_GTE, PERCENT_LTE, WITHIN_TOLERANCE }
 public enum Outcome { PASS, FAIL, NOT_EVALUABLE, NOT_APPLICABLE, MANUAL_REVIEW }
 public enum Mode { AUTOMATIC, MANUAL }
 public record Condition(Field field,Operator operator,String value){}
 public record BankCalendar(String id,String coveredFrom,String coveredTo,List<java.time.DayOfWeek> closedWeekdays,List<String> closedDates){}
 public record Parameters(
  @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) de.ostms.lc.document.domain.DocumentType peerDocumentType,
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
