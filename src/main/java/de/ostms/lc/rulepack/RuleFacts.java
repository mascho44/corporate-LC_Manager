package de.ostms.lc.rulepack;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.*;
import static de.ostms.lc.rulepack.PackDefinition.Field;

/** Explicitly reviewed supplementary data, never guessed from OCR or a filename. */
public final class RuleFacts {
 private RuleFacts(){}
 private static final ObjectMapper JSON=new ObjectMapper().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
 public static final int MAX_BYTES=64*1024;
 public static final Set<Field> DOCUMENT=Set.of(Field.DOCUMENT_CONSIGNEE_ADDRESS_COUNTRY,Field.DOCUMENT_NOTIFY_ADDRESS_COUNTRY,Field.DOCUMENT_PARTIAL_SHIPMENT_INDICATED,Field.DOCUMENT_ISSUER,Field.DOCUMENT_RECIPIENT,Field.DOCUMENT_GOODS_DESCRIPTION,
  Field.DOCUMENT_SHIPMENT_DATE,Field.DOCUMENT_PRESENTATION_GROUP,Field.DOCUMENT_QUANTITY,Field.DOCUMENT_QUANTITY_UNIT,
  Field.DOCUMENT_NET_WEIGHT,Field.DOCUMENT_GROSS_WEIGHT,Field.DOCUMENT_WEIGHT_UNIT,
  Field.DOCUMENT_INSURED_AMOUNT,Field.DOCUMENT_INSURANCE_CURRENCY,Field.DOCUMENT_INSURANCE_EFFECTIVE_DATE,
  Field.DOCUMENT_INSURANCE_RISKS,Field.DOCUMENT_SIGNED,Field.DOCUMENT_ORIGINAL_COUNT,Field.DOCUMENT_EXAMINATION_START_DATE,
  Field.DOCUMENT_CARRIER,Field.DOCUMENT_SIGNER_ROLE,Field.DOCUMENT_SIGNED_FOR,Field.DOCUMENT_LOADING_PORT,Field.DOCUMENT_DISCHARGE_PORT,
  Field.DOCUMENT_DEPARTURE_AIRPORT,Field.DOCUMENT_DESTINATION_AIRPORT,Field.DOCUMENT_VESSEL,Field.DOCUMENT_TRANSPORT_NOTATION,
  Field.DOCUMENT_ISSUED_ORIGINAL_COUNT,Field.DOCUMENT_INSURANCE_TYPE,Field.DOCUMENT_COVERAGE_FROM,Field.DOCUMENT_COVERAGE_TO,
  Field.DOCUMENT_UNIT_PRICE_AMOUNT,Field.DOCUMENT_ORIGIN_COUNTRY,
  Field.DOCUMENT_PRESENTATION_DATE,Field.DOCUMENT_TRANSSHIPMENT_INDICATED,Field.DOCUMENT_SHIPMENT_COUNT,Field.DOCUMENT_ON_BOARD_NOTATION_PRESENT,Field.DOCUMENT_ON_BOARD_DATE,Field.DOCUMENT_INTENDED_VESSEL_INDICATED,Field.DOCUMENT_CONSIGNEE,Field.DOCUMENT_NOTIFY_PARTY,Field.DOCUMENT_APPLICANT_ADDRESS_COUNTRY,Field.DOCUMENT_BENEFICIARY_ADDRESS_COUNTRY,Field.DOCUMENT_FREIGHT_PREPAID,Field.DOCUMENT_FREIGHT_TERMS,Field.DOCUMENT_INCOTERM,Field.DOCUMENT_INCOTERM_SOURCE,Field.DOCUMENT_INVOICE_REFERENCE,Field.DOCUMENT_FRANCHISE_PRESENT,Field.DOCUMENT_IRRESPECTIVE_OF_PERCENTAGE,Field.DOCUMENT_INSURED_PARTY,Field.DOCUMENT_ENDORSEMENT_PRESENT,Field.DOCUMENT_ENDORSEMENT_TO,Field.DOCUMENT_DRAWEE,Field.DOCUMENT_DRAFT_TENOR_DAYS,Field.DOCUMENT_DRAFT_TENOR_BASIS,Field.DOCUMENT_PACKAGE_COUNT,Field.DOCUMENT_SHIPPING_MARKS,Field.DOCUMENT_SIGNER_NAME );
 public static final Set<Field> LC=Set.of(Field.LC_CONSIGNEE_ADDRESS_COUNTRY,Field.LC_NOTIFY_ADDRESS_COUNTRY,Field.LC_RULE_STANDARD,Field.LC_TRANSFERRED,Field.LC_SECOND_BENEFICIARY,Field.LC_GOODS_DESCRIPTION,
  Field.LC_PRESENTATION_DATE,Field.LC_PRESENTATION_PERIOD_DAYS,Field.LC_INSURANCE_MIN_PERCENT,Field.LC_TOLERANCE_PERCENT,
  Field.LC_INSURANCE_RISKS,Field.LC_EXAMINATION_DECISION_DATE,
  Field.LC_CIF_CIP_VALUE_AMOUNT,Field.LC_CLAIMED_AMOUNT,Field.LC_GROSS_GOODS_AMOUNT,
  Field.LC_QUANTITY,Field.LC_QUANTITY_UNIT,Field.LC_UNIT_PRICE_AMOUNT,
  Field.LC_LOADING_PORT,Field.LC_DISCHARGE_PORT,Field.LC_DEPARTURE_AIRPORT,Field.LC_DESTINATION_AIRPORT,
  Field.LC_COVERAGE_FROM,Field.LC_COVERAGE_TO,Field.LC_ORIGIN_COUNTRY,
  Field.LC_TRANSSHIPMENT_ALLOWED,Field.LC_PARTIAL_SHIPMENT_ALLOWED,Field.LC_ON_BOARD_NOTATION_REQUIRED,Field.LC_APPLICANT_ADDRESS_COUNTRY,Field.LC_BENEFICIARY_ADDRESS_COUNTRY,Field.LC_CONSIGNEE,Field.LC_NOTIFY_PARTY,Field.LC_FREIGHT_PREPAID_REQUIRED,Field.LC_FREIGHT_TERMS,Field.LC_INCOTERM,Field.LC_INCOTERM_SOURCE,Field.LC_FRANCHISE_ALLOWED,Field.LC_IRRESPECTIVE_OF_PERCENTAGE_REQUIRED,Field.LC_INSURED_PARTY,Field.LC_ENDORSEMENT_REQUIRED,Field.LC_ENDORSEMENT_TO,Field.LC_DRAWEE,Field.LC_DRAFT_TENOR_DAYS,Field.LC_DRAFT_TENOR_BASIS );
 public static final Set<Field> REQUIREMENTS=Set.of(Field.LC_SIGNATURE_REQUIRED,Field.LC_REQUIRED_ORIGINAL_COUNT,Field.LC_REQUIRED_ISSUER);
 public static Map<Field,String> read(String source){
  if(source==null||source.isBlank())return Map.of();
  try{return JSON.readValue(source,new TypeReference<EnumMap<Field,String>>(){});}
  catch(Exception invalid){throw new IllegalStateException("Gespeicherte Prüfdaten sind ungültig.",invalid);}
 }
 public static String encode(Map<Field,String> facts,boolean document){
  return encodeFor(facts,document?DOCUMENT:LC);
 }
 public static String encodeRequirements(Map<Field,String> facts){return encodeFor(facts,REQUIREMENTS);}
 private static String encodeFor(Map<Field,String> facts,Set<Field> allowed){
  if(facts==null||facts.size()>96)throw new IllegalArgumentException("Ungültige Prüfdaten.");
  var values=new EnumMap<Field,String>(Field.class);
  for(var e:facts.entrySet()){
   if(!allowed.contains(e.getKey()))throw new IllegalArgumentException("Dieses Prüffeld ist hier nicht zulässig.");
   String value=e.getValue();
   if(value==null||value.isBlank())continue;
   int max=maxLength(e.getKey());
   if(value.length()>max)throw new IllegalArgumentException("Prüfwert ist zu lang.");
   value=value.strip();
   if(e.getKey().kind().equals("BOOLEAN")&&!Set.of("true","false").contains(value))throw new IllegalArgumentException("Wahrheitswert muss true oder false sein.");
   if(e.getKey()==Field.LC_RULE_STANDARD&&!Set.of("UCP600","OTHER").contains(value))throw new IllegalArgumentException("Regelstandard muss UCP600 oder OTHER sein.");
   if(e.getKey().kind().equals("DATE")){
    if(!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw new IllegalArgumentException("Datum muss ISO-Format YYYY-MM-DD haben.");
    try{java.time.LocalDate.parse(value);}catch(Exception invalid){throw new IllegalArgumentException("Ungültiges Datum.");}
   }
   if(e.getKey().kind().equals("NUMBER")){
    if(!value.matches("[0-9]{1,19}(\\.[0-9]{1,6})?"))throw new IllegalArgumentException("Zahl muss nichtnegativ sein und Dezimalpunkt verwenden.");
    var number=new java.math.BigDecimal(value);
    if(e.getKey().name().endsWith("COUNT")||e.getKey().name().endsWith("DAYS")){
     try{int n=number.intValueExact();if(n>(e.getKey().name().endsWith("DAYS")?3660:10000))throw new ArithmeticException();}
     catch(ArithmeticException invalid){throw new IllegalArgumentException("Ungültige Anzahl oder Tagesgrenze.");}
    }
    if(e.getKey().name().endsWith("PERCENT")&&number.compareTo(new java.math.BigDecimal("1000"))>0)throw new IllegalArgumentException("Prozentwert über 1000.");
   }
   if(e.getKey().kind().equals("CURRENCY")){
    value=value.toUpperCase(Locale.ROOT);
    try{if(!value.matches("[A-Z]{3}"))throw new IllegalArgumentException();Currency.getInstance(value);}
    catch(Exception invalid){throw new IllegalArgumentException("Ungültiger ISO-Währungscode.");}
   }
   if(e.getKey()==Field.DOCUMENT_PRESENTATION_GROUP&&!value.matches("[A-Za-z0-9._-]{1,100}"))throw new IllegalArgumentException("Dokumentensatzkennung benötigt 1–100 Buchstaben, Ziffern, Punkt, Unterstrich oder Bindestrich.");
   if(e.getKey()==Field.DOCUMENT_WEIGHT_UNIT){value=value.toUpperCase(Locale.ROOT);if(!Set.of("KG","T","LB").contains(value))throw new IllegalArgumentException("Gewichtseinheit muss KG, T oder LB sein.");}
   values.put(e.getKey(),value);
  }
  try{return JSON.writeValueAsString(values);}catch(Exception invalid){throw new IllegalStateException(invalid);}
 }
 public static Map<Field,String> decodeRequest(byte[] source){
  if(source.length>MAX_BYTES)throw new IllegalArgumentException("Prüfdaten überschreiten 64 KB.");
  try{
   var root=JSON.readTree(source);
   if(root==null||!root.isObject()||root.size()>96)throw new IllegalArgumentException("Prüfdaten müssen ein JSON-Objekt mit höchstens 96 Feldern sein.");
   var facts=new EnumMap<Field,String>(Field.class);
   var fields=root.fields();
   while(fields.hasNext()){
    var entry=fields.next();var value=entry.getValue();
    if(!value.isNull()&&!value.isTextual())throw new IllegalArgumentException("Prüfwerte müssen Zeichenketten oder null sein.");
    facts.put(Field.valueOf(entry.getKey()),value.isNull()?null:value.textValue());
   }
   return facts;
  }catch(IllegalArgumentException invalid){throw invalid;}
  catch(Exception invalid){throw new IllegalArgumentException("Ungültige Prüfdaten oder doppelte JSON-Felder.");}
 }
 public static int maxLength(Field field){return field.name().contains("GOODS_DESCRIPTION")||field.name().endsWith("RISKS")?4000:500;}
 public record Definition(Field field,String label,String kind,List<String> choices,int maxLength){}
 public static List<Definition> definitions(boolean document){
  return definitionsFor(document?DOCUMENT:LC);
 }
 public static List<Definition> requirementDefinitions(){return definitionsFor(REQUIREMENTS);}
 private static List<Definition> definitionsFor(Set<Field> fields){
  var labels=Map.ofEntries(
   Map.entry(Field.DOCUMENT_ISSUER,"Dokumentaussteller"),Map.entry(Field.DOCUMENT_RECIPIENT,"Dokumentempfänger"),Map.entry(Field.DOCUMENT_GOODS_DESCRIPTION,"Warenbeschreibung"),
   Map.entry(Field.DOCUMENT_SHIPMENT_DATE,"Geprüftes Versanddatum"),Map.entry(Field.DOCUMENT_PRESENTATION_GROUP,"Dokumentensatzkennung"),Map.entry(Field.DOCUMENT_QUANTITY,"Menge"),Map.entry(Field.DOCUMENT_QUANTITY_UNIT,"Mengeneinheit"),
   Map.entry(Field.DOCUMENT_NET_WEIGHT,"Nettogewicht"),Map.entry(Field.DOCUMENT_GROSS_WEIGHT,"Bruttogewicht"),Map.entry(Field.DOCUMENT_WEIGHT_UNIT,"Gewichtseinheit"),
   Map.entry(Field.DOCUMENT_INSURED_AMOUNT,"Versicherte Summe"),Map.entry(Field.DOCUMENT_INSURANCE_CURRENCY,"Versicherungswährung"),Map.entry(Field.DOCUMENT_INSURANCE_EFFECTIVE_DATE,"Geprüfter Beginn der Versicherungsdeckung"),
   Map.entry(Field.DOCUMENT_INSURANCE_RISKS,"Nachgewiesene Versicherungsrisiken"),Map.entry(Field.DOCUMENT_SIGNED,"Unterschrift fachlich geprüft und vorhanden?"),Map.entry(Field.DOCUMENT_ORIGINAL_COUNT,"Geprüfte Anzahl Originale"),
   Map.entry(Field.DOCUMENT_EXAMINATION_START_DATE,"Geprüfter Beginn der Bankprüfung"),Map.entry(Field.LC_EXAMINATION_DECISION_DATE,"Geprüftes Datum der Bankentscheidung"),
   Map.entry(Field.LC_RULE_STANDARD,"Anzuwendender Regelstandard"),Map.entry(Field.LC_TRANSFERRED,"LC tatsächlich übertragen?"),Map.entry(Field.LC_SECOND_BENEFICIARY,"Zweiter Begünstigter"),Map.entry(Field.LC_GOODS_DESCRIPTION,"Gültige LC-Warenbeschreibung"),
   Map.entry(Field.LC_PRESENTATION_DATE,"Geprüftes Datum der Dokumentenvorlage"),Map.entry(Field.LC_PRESENTATION_PERIOD_DAYS,"Vereinbarte Vorlagefrist in Tagen"),Map.entry(Field.LC_INSURANCE_MIN_PERCENT,"Geprüfte Mindestdeckung in Prozent"),
   Map.entry(Field.LC_TOLERANCE_PERCENT,"Geprüfte Toleranz in Prozent"),Map.entry(Field.LC_SIGNATURE_REQUIRED,"Unterschrift für diese Pack-Prüfung gefordert?"),Map.entry(Field.LC_REQUIRED_ORIGINAL_COUNT,"Geforderte Originalanzahl für diese Pack-Prüfung"),
   Map.entry(Field.LC_INSURANCE_RISKS,"Geforderte Versicherungsrisiken"));
  return fields.stream().sorted(Comparator.comparingInt(Enum::ordinal)).map(field->{
   List<String> choices=field.kind().equals("BOOLEAN")?List.of("true","false"):field==Field.LC_RULE_STANDARD?List.of("UCP600","OTHER"):field==Field.DOCUMENT_WEIGHT_UNIT?List.of("KG","T","LB"):List.of();
   return new Definition(field,labels.getOrDefault(field,ExtendedRuleFacts.label(field)),field.kind(),choices,maxLength(field));
  }).toList();
 }
 public static String fingerprint(String source){
  return contentFingerprint(Objects.toString(source,"").getBytes(java.nio.charset.StandardCharsets.UTF_8));
 }
 public static String contentFingerprint(byte[] source){
  try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(source==null?new byte[0]:source));}
  catch(Exception invalid){throw new IllegalStateException(invalid);}
 }
}
