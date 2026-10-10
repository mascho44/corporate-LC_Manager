package de.ostms.lc.rulepack;
import de.ostms.lc.document.domain.DocumentType;
import java.util.*;
import java.util.regex.Pattern;
import static de.ostms.lc.rulepack.PackDefinition.Field;

/** Yes/no facts that only state whether a phrase appears on a document (the *_INDICATED / *_PRESENT facts). Only positive evidence is proposed; a missing phrase proves nothing. */
public final class DocumentIndicators {
 public record Hit(Field field,String source){}
 private enum Scope{ALL,TRANSPORT,INSURANCE}
 private record Rule(Field field,Scope scope,Pattern pattern,String label){}
 private static final Set<DocumentType> TRANSPORT=EnumSet.of(DocumentType.BILL_OF_LADING,DocumentType.SEA_WAYBILL,DocumentType.CHARTER_PARTY_BILL_OF_LADING,DocumentType.MULTIMODAL_TRANSPORT_DOCUMENT,DocumentType.AIR_WAYBILL);
 private static final List<Rule> RULES=List.of(
  rule(Field.DOCUMENT_TRANSSHIPMENT_INDICATED,Scope.TRANSPORT,"trans-?s?hipment","Umladung"),
  rule(Field.DOCUMENT_CHARTER_PARTY_INDICATED,Scope.TRANSPORT,"charter\\s+party","Charter Party"),
  rule(Field.DOCUMENT_ON_DECK_INDICATED,Scope.TRANSPORT,"\\bon\\s+deck\\b","Deckverladung"),
  rule(Field.DOCUMENT_TO_ORDER_INDICATED,Scope.TRANSPORT,"\\bto\\s+(?:the\\s+)?order\\b","An-Order-Klausel"),
  rule(Field.DOCUMENT_ACCEPTED_FOR_CARRIAGE_INDICATED,Scope.TRANSPORT,"accepted\\s+for\\s+carriage","Annahme zur Beförderung"),
  rule(Field.DOCUMENT_SHIPPED_ON_BOARD_INDICATED,Scope.TRANSPORT,"shipped\\s+on\\s+board","Shipped-on-board-Vermerk"),
  rule(Field.DOCUMENT_INTENDED_VESSEL_INDICATED,Scope.TRANSPORT,"intended\\s+vessel","vorgesehenes Schiff"),
  rule(Field.DOCUMENT_INTENDED_PORT_INDICATED,Scope.TRANSPORT,"intended\\s+port","vorgesehener Hafen"),
  rule(Field.DOCUMENT_CONTAINERISED_SHIPMENT_INDICATED,Scope.TRANSPORT,"\\bcontainer(?:i[sz]ed)?\\b","Container"),
  rule(Field.DOCUMENT_AGENT_PRINCIPAL_INDICATED,Scope.TRANSPORT,"\\bas\\s+agent\\s+(?:for|of)\\b|\\bon\\s+behalf\\s+of\\s+the\\s+(?:master|carrier|owner)","Zeichnung als Agent"),
  rule(Field.DOCUMENT_SIGNED_BY_AGENT_INDICATED,Scope.TRANSPORT,"\\bas\\s+agent\\b","Zeichnung durch Agenten"),
  rule(Field.DOCUMENT_PARTIAL_SHIPMENT_INDICATED,Scope.ALL,"partial\\s+shipments?","Teilverladung"),
  rule(Field.DOCUMENT_PRESHIPMENT_INDICATED,Scope.ALL,"pre-?shipment","Vorverladung"),
  rule(Field.DOCUMENT_PROVISIONAL_INDICATED,Scope.ALL,"\\bprovisional\\b","vorläufig"),
  rule(Field.DOCUMENT_DISCOUNT_INDICATED,Scope.ALL,"\\bdiscounts?\\b|\\brebates?\\b","Rabatt"),
  rule(Field.DOCUMENT_ADDITIONAL_COSTS_INDICATED,Scope.ALL,"additional\\s+(?:costs?|charges?)|extra\\s+charges?","Zusatzkosten"),
  rule(Field.DOCUMENT_COVER_DATE_INDICATED,Scope.INSURANCE,"cover(?:age)?\\s+(?:date|from|effective|commences?)|effective\\s+date|date\\s+of\\s+cover","Deckungsbeginn"),
  rule(Field.DOCUMENT_PREMIUM_PAID_INDICATED,Scope.INSURANCE,"premium\\s+(?:has\\s+been\\s+)?paid|premium\\s+received","Prämie bezahlt"),
  rule(Field.DOCUMENT_CLAIM_EXPIRY_INDICATED,Scope.INSURANCE,"claims?\\s+(?:payable|expir|must\\s+be\\s+(?:filed|notified))","Schadensfrist"),
  rule(Field.DOCUMENT_ENDORSEMENT_PRESENT,Scope.ALL,"\\bendorsed\\b|\\bendorsement\\b","Indossament"),
  rule(Field.DOCUMENT_FRANCHISE_PRESENT,Scope.INSURANCE,"franchise|deductible|excess\\s+of","Franchise/Selbstbehalt"),
  rule(Field.DOCUMENT_IRRESPECTIVE_OF_PERCENTAGE,Scope.INSURANCE,"irrespective\\s+of\\s+percentage","Irrespective-of-percentage-Klausel"),
  rule(Field.DOCUMENT_COUNTERSIGNATURE_PRESENT,Scope.ALL,"counter-?sign(?:ed|ature)","Gegenzeichnung"));
 private DocumentIndicators(){}
 private static Rule rule(Field f,Scope s,String regex,String label){return new Rule(f,s,Pattern.compile("(?i)"+regex),label);}

 public static List<Hit> detect(DocumentType type,String text){
  var out=new ArrayList<Hit>();
  if(text==null||text.isBlank())return out;
  boolean transport=TRANSPORT.contains(type),insurance=type==DocumentType.INSURANCE_CERTIFICATE;
  for(var r:RULES){
   if(r.scope==Scope.TRANSPORT&&!transport)continue;
   if(r.scope==Scope.INSURANCE&&!insurance)continue;
   if(r.pattern.matcher(text).find())out.add(new Hit(r.field,r.label+" im Text gefunden"));
  }
  return out;
 }
}
