package de.ostms.lc.rulepack;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Component
public class PackCodec {
 public static final int MAX_BYTES=5*1024*1024;
 public static final int MAX_RULES=500;
 public static final int MAX_TESTS=3000;
 private final ObjectMapper json;
 public PackCodec(ObjectMapper mapper){json=mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
  .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);}
 public PackDefinition parse(byte[] bytes){
  if(bytes.length>MAX_BYTES)throw new IllegalArgumentException("Rule Pack überschreitet 5 MB.");
  try{var pack=json.readValue(bytes,PackDefinition.class);validate(pack);return pack;}
  catch(IllegalArgumentException e){throw e;}
  catch(Exception e){throw new IllegalArgumentException("Ungültiges Rule-Pack-JSON oder unbekannte Felder.");}
 }
 public Object inspectSpecification(byte[] bytes){
  if(bytes.length>MAX_BYTES)throw new IllegalArgumentException("Rule Pack überschreitet 5 MB.");
  try{
   var root=json.readTree(bytes);
   return root!=null&&root.has("specVersion")?RuleSpecificationInspector.inspect(root,json,this):null;
  }catch(IllegalArgumentException invalid){throw invalid;}
  catch(Exception invalid){throw new IllegalArgumentException("Ungültiges JSON oder doppelte Felder.");}
 }
 public String canonical(PackDefinition pack){
  try{return json.writeValueAsString(pack);}catch(Exception e){throw new IllegalStateException("Pack konnte nicht serialisiert werden.");}
 }
 public String digest(String source){
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));}
  catch(Exception e){throw new IllegalStateException(e);}
 }
 public void validate(PackDefinition p){
  validate(p,true);
 }
 public void validateCapabilities(PackDefinition p){validate(p,false);}
 private void validate(PackDefinition p,boolean checkTests){
  if(p==null||p.schemaVersion()<1||p.schemaVersion()>5)bad("Schema-Version 1 bis 5 erforderlich.");
  if(p.calendars()!=null){
   if(p.schemaVersion()<3||p.calendars().size()>5)bad("Kalender benötigen Schema 3 oder neuer; maximal fünf Kalender erlaubt.");
   var calendarIds=new HashSet<String>();
   for(var c:p.calendars()){
    if(c==null)bad("Leerer Kalender.");
    token(c.id(),"[a-z][a-z0-9-]{2,30}","Kalender-ID");if(!calendarIds.add(c.id()))bad("Kalender-ID doppelt.");
    try{
     var from=java.time.LocalDate.parse(c.coveredFrom());var to=java.time.LocalDate.parse(c.coveredTo());
     if(to.isBefore(from)||java.time.temporal.ChronoUnit.DAYS.between(from,to)>3660)bad("Kalenderzeitraum ist ungültig.");
     if(c.closedWeekdays()==null||c.closedWeekdays().size()>7||c.closedWeekdays().contains(null)||new HashSet<>(c.closedWeekdays()).size()!=c.closedWeekdays().size())bad("Ungültige Schließtage.");
     if(c.closedDates()==null||c.closedDates().size()>500||new HashSet<>(c.closedDates()).size()!=c.closedDates().size())bad("Ungültige Feiertagsliste.");
     for(var date:c.closedDates()){var day=java.time.LocalDate.parse(date);if(day.isBefore(from)||day.isAfter(to))bad("Schließdatum außerhalb des Kalenderzeitraums.");}
    }catch(RuntimeException invalid){bad("Ungültiger Kalender: "+c.id());}
   }
  }
  token(p.packId(),"[a-z][a-z0-9-]{2,30}","Pack-ID");
  token(p.version(),"[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}","Pack-Version");
  text(p.name(),100,"Name");text(p.license(),100,"Lizenz");text(p.rightsStatement(),500,"Rechteerklärung");
  if(!"OWN_INTERNAL".equals(p.origin())&&!"ICC_LICENSED".equals(p.origin()))bad("Herkunft muss OWN_INTERNAL oder ICC_LICENSED sein.");
  if(p.rules()==null||p.rules().isEmpty()||p.rules().size()>MAX_RULES)bad("1 bis "+MAX_RULES+" Regeln erforderlich.");
  if(checkTests&&(p.tests()==null||p.tests().isEmpty()||p.tests().size()>MAX_TESTS))bad("1 bis "+MAX_TESTS+" synthetische Testfälle erforderlich.");
  var ids=new HashSet<String>();
  for(var r:p.rules()){
   if(r==null)bad("Leere Regel.");
   token(r.id(),"[a-z][a-z0-9-]{1,30}","Regel-ID");
   token(r.version(),"[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}","Regel-Version");
   if(!ids.add(r.id()))bad("Regel-ID doppelt.");
   text(r.message(),200,"Meldung");text(r.sourceReference(),160,"Quellenreferenz");
   if(r.documentType()==null||r.left()==null||r.right()==null||r.operator()==null||r.severity()==null)bad("Regel ist unvollständig.");
   boolean literal=r.right()==PackDefinition.Field.LITERAL;
   if(p.schemaVersion()<5&&(v5(r.left())||v5(r.right())||r.rightValue()!=null||r.operator()==PackDefinition.Operator.IN||r.operator()==PackDefinition.Operator.NOT_IN||r.documentType().ordinal()>de.ostms.lc.document.domain.DocumentType.OTHER.ordinal()))bad("Diese Funktionen benötigen Schema 5.");
   if(literal){
    boolean membership=r.operator()==PackDefinition.Operator.IN||r.operator()==PackDefinition.Operator.NOT_IN;
    if(!r.left().document()||!Set.of("TEXT","BOOLEAN").contains(r.left().kind())||(!membership&&r.operator()!=PackDefinition.Operator.EQ&&r.operator()!=PackDefinition.Operator.NE))bad("Festwerte erlauben nur Dokument-Text-/Wahrheitswerte und EQ/NE oder Listenoperatoren.");
    if(!membership||r.rightValue()!=null)text(r.rightValue(),100,"Festwert");
    var probe=new PackDefinition.Rule("probe","1.0.0",r.documentType(),r.left(),PackDefinition.Operator.EQ,r.left(),r.severity(),"probe","probe");
    if(r.operator()!=PackDefinition.Operator.IN&&r.operator()!=PackDefinition.Operator.NOT_IN&&PackEvaluator.compare(probe,r.rightValue(),r.rightValue())!=PackDefinition.Outcome.PASS)bad("Festwert hat ein ungültiges Format.");
   }else if(r.rightValue()!=null)bad("Festwert nur mit LITERAL erlaubt.");
   boolean documentPresentationPair=r.right()==PackDefinition.Field.DOCUMENT_PRESENTATION_DATE&&Set.of(PackDefinition.Field.DOCUMENT_SHIPMENT_DATE,PackDefinition.Field.DOCUMENT_ON_BOARD_DATE).contains(r.left());
   if((!r.left().document()&&r.left()!=PackDefinition.Field.LC_PRESENTATION_DATE)||(r.right().document()&&!documentPresentationPair)||(!literal&&!r.left().kind().equals(r.right().kind())))bad("Nur typgleiche freigegebene Prüffeld-Vergleiche sind erlaubt.");
   if((r.left()==PackDefinition.Field.DOCUMENT_GOODS_DESCRIPTION||r.left()==PackDefinition.Field.DOCUMENT_INSURANCE_RISKS)&&r.effectiveMode()!=PackDefinition.Mode.MANUAL)bad("Warenbeschreibungen und Versicherungsrisiken benötigen eine manuelle fachliche Prüfung.");
   boolean pair=switch(r.left()){
    case DOCUMENT_ISSUER->r.right()==PackDefinition.Field.LC_BENEFICIARY||r.right()==PackDefinition.Field.LC_SECOND_BENEFICIARY||r.right()==PackDefinition.Field.LC_REQUIRED_ISSUER;
    case DOCUMENT_PRESENTATION_DATE,LC_PRESENTATION_DATE->r.right()==PackDefinition.Field.LC_EXPIRY_DATE;
    case DOCUMENT_ON_BOARD_DATE->Set.of(PackDefinition.Field.LC_LATEST_SHIPMENT_DATE,PackDefinition.Field.LC_PRESENTATION_DATE,PackDefinition.Field.PEER_SHIPMENT_DATE,PackDefinition.Field.DOCUMENT_PRESENTATION_DATE).contains(r.right());
    case DOCUMENT_TRANSSHIPMENT_INDICATED->r.right()==PackDefinition.Field.LC_TRANSSHIPMENT_ALLOWED;
    case DOCUMENT_PARTIAL_SHIPMENT_INDICATED->r.right()==PackDefinition.Field.LC_PARTIAL_SHIPMENT_ALLOWED;
    case DOCUMENT_ON_BOARD_NOTATION_PRESENT->r.right()==PackDefinition.Field.LC_ON_BOARD_NOTATION_REQUIRED;
    case DOCUMENT_CONSIGNEE->r.right()==PackDefinition.Field.LC_CONSIGNEE;
    case DOCUMENT_CONSIGNEE_ADDRESS_COUNTRY->r.right()==PackDefinition.Field.LC_CONSIGNEE_ADDRESS_COUNTRY;
    case DOCUMENT_NOTIFY_ADDRESS_COUNTRY->r.right()==PackDefinition.Field.LC_NOTIFY_ADDRESS_COUNTRY;
    case DOCUMENT_NOTIFY_PARTY->r.right()==PackDefinition.Field.LC_NOTIFY_PARTY;
    case DOCUMENT_APPLICANT_ADDRESS_COUNTRY->r.right()==PackDefinition.Field.LC_APPLICANT_ADDRESS_COUNTRY;
    case DOCUMENT_BENEFICIARY_ADDRESS_COUNTRY->r.right()==PackDefinition.Field.LC_BENEFICIARY_ADDRESS_COUNTRY;
    case DOCUMENT_FREIGHT_PREPAID->r.right()==PackDefinition.Field.LC_FREIGHT_PREPAID_REQUIRED;
    case DOCUMENT_FREIGHT_TERMS->r.right()==PackDefinition.Field.LC_FREIGHT_TERMS;
    case DOCUMENT_INCOTERM->r.right()==PackDefinition.Field.LC_INCOTERM;
    case DOCUMENT_INVOICE_REFERENCE->r.right()==PackDefinition.Field.PEER_DOCUMENT_NUMBER;
    case DOCUMENT_FRANCHISE_PRESENT->r.right()==PackDefinition.Field.LC_FRANCHISE_ALLOWED;
    case DOCUMENT_IRRESPECTIVE_OF_PERCENTAGE->r.right()==PackDefinition.Field.LC_IRRESPECTIVE_OF_PERCENTAGE_REQUIRED;
    case DOCUMENT_INSURED_PARTY->Set.of(PackDefinition.Field.LC_INSURED_PARTY,PackDefinition.Field.LC_BENEFICIARY).contains(r.right());
    case DOCUMENT_ENDORSEMENT_PRESENT->r.right()==PackDefinition.Field.LC_ENDORSEMENT_REQUIRED;
    case DOCUMENT_ENDORSEMENT_TO->Set.of(PackDefinition.Field.LC_ENDORSEMENT_TO,PackDefinition.Field.LC_BENEFICIARY).contains(r.right());
    case DOCUMENT_DRAWEE->Set.of(PackDefinition.Field.LC_DRAWEE,PackDefinition.Field.LC_APPLICANT).contains(r.right());
    case DOCUMENT_DRAFT_TENOR_DAYS->r.right()==PackDefinition.Field.LC_DRAFT_TENOR_DAYS;
    case DOCUMENT_DRAFT_TENOR_BASIS->r.right()==PackDefinition.Field.LC_DRAFT_TENOR_BASIS;
    case DOCUMENT_PACKAGE_COUNT->r.right()==PackDefinition.Field.PEER_PACKAGE_COUNT;
    case DOCUMENT_SHIPPING_MARKS->r.right()==PackDefinition.Field.PEER_SHIPPING_MARKS;
    case DOCUMENT_RECIPIENT->r.right()==PackDefinition.Field.LC_APPLICANT;
    case DOCUMENT_GOODS_DESCRIPTION->Set.of(PackDefinition.Field.LC_GOODS_DESCRIPTION,PackDefinition.Field.PEER_GOODS_DESCRIPTION).contains(r.right());
    case DOCUMENT_AMOUNT->Set.of(PackDefinition.Field.LC_AMOUNT,PackDefinition.Field.PEER_AMOUNT).contains(r.right());
    case DOCUMENT_INSURED_AMOUNT->Set.of(PackDefinition.Field.LC_AMOUNT,PackDefinition.Field.PEER_AMOUNT,PackDefinition.Field.LC_INSURANCE_BASE_AMOUNT).contains(r.right());
    case DOCUMENT_CURRENCY,DOCUMENT_INSURANCE_CURRENCY->Set.of(PackDefinition.Field.LC_CURRENCY,PackDefinition.Field.PEER_CURRENCY).contains(r.right());
    case DOCUMENT_DATE,DOCUMENT_SHIPMENT_DATE->Set.of(PackDefinition.Field.LC_EXPIRY_DATE,PackDefinition.Field.LC_LATEST_SHIPMENT_DATE,PackDefinition.Field.LC_PRESENTATION_DATE,PackDefinition.Field.PEER_SHIPMENT_DATE).contains(r.right())||documentPresentationPair;
    case DOCUMENT_INSURANCE_EFFECTIVE_DATE->r.right()==PackDefinition.Field.PEER_SHIPMENT_DATE;
    case DOCUMENT_EXAMINATION_START_DATE->r.right()==PackDefinition.Field.LC_EXAMINATION_DECISION_DATE;
    case DOCUMENT_INSURANCE_RISKS->r.right()==PackDefinition.Field.LC_INSURANCE_RISKS;
    case DOCUMENT_SIGNED->r.right()==PackDefinition.Field.LC_SIGNATURE_REQUIRED;
    case DOCUMENT_ORIGINAL_COUNT->Set.of(PackDefinition.Field.LC_REQUIRED_ORIGINAL_COUNT,PackDefinition.Field.LC_DOCUMENT_ISSUED_ORIGINAL_COUNT).contains(r.right());
    case DOCUMENT_QUANTITY->Set.of(PackDefinition.Field.PEER_QUANTITY,PackDefinition.Field.LC_QUANTITY).contains(r.right());
    case DOCUMENT_QUANTITY_UNIT->Set.of(PackDefinition.Field.PEER_QUANTITY_UNIT,PackDefinition.Field.LC_QUANTITY_UNIT).contains(r.right());
    case DOCUMENT_NET_WEIGHT->r.right()==PackDefinition.Field.PEER_NET_WEIGHT;
    case DOCUMENT_GROSS_WEIGHT->r.right()==PackDefinition.Field.PEER_GROSS_WEIGHT;
    case DOCUMENT_WEIGHT_UNIT->r.right()==PackDefinition.Field.PEER_WEIGHT_UNIT;
    case DOCUMENT_UNIT_PRICE_AMOUNT->r.right()==PackDefinition.Field.LC_UNIT_PRICE_AMOUNT;
    case DOCUMENT_LOADING_PORT->r.right()==PackDefinition.Field.LC_LOADING_PORT;
    case DOCUMENT_DISCHARGE_PORT->r.right()==PackDefinition.Field.LC_DISCHARGE_PORT;
    case DOCUMENT_DEPARTURE_AIRPORT->r.right()==PackDefinition.Field.LC_DEPARTURE_AIRPORT;
    case DOCUMENT_DESTINATION_AIRPORT->r.right()==PackDefinition.Field.LC_DESTINATION_AIRPORT;
    case DOCUMENT_COVERAGE_FROM->r.right()==PackDefinition.Field.LC_COVERAGE_FROM;
    case DOCUMENT_COVERAGE_TO->r.right()==PackDefinition.Field.LC_COVERAGE_TO;
    case DOCUMENT_ORIGIN_COUNTRY->r.right()==PackDefinition.Field.LC_ORIGIN_COUNTRY;
    default->false;
   };
   if(!pair&&!literal&&!(p.schemaVersion()>=5&&V5RuleCapabilities.pair(r.left(),r.right())))bad("Unzulässige Kombination von Prüffeldern.");
   if(Set.of("TEXT","CURRENCY","BOOLEAN").contains(r.left().kind())&&r.operator()!=PackDefinition.Operator.EQ&&r.operator()!=PackDefinition.Operator.NE&&r.operator()!=PackDefinition.Operator.IN&&r.operator()!=PackDefinition.Operator.NOT_IN)bad("Textfelder erlauben nur EQ, NE, IN oder NOT_IN.");
   if(p.schemaVersion()==1&&(r.mode()!=null||r.conditions()!=null||r.left().ordinal()>6||r.right().ordinal()>6))bad("Erweiterte Regeln benötigen Schema-Version 2.");
   if(p.schemaVersion()<3&&(r.parameters()!=null||r.left().ordinal()>15||r.right().ordinal()>15||r.operator().ordinal()>3))bad("Diese Regel benötigt Schema 3.");
   if(p.schemaVersion()<4&&(extended(r.left())||extended(r.right())||r.left()==PackDefinition.Field.LC_PRESENTATION_DATE))bad("Diese Prüffelder benötigen Schema 4.");
   if(Set.of(PackDefinition.Field.DOCUMENT_LOADING_PORT,PackDefinition.Field.DOCUMENT_DISCHARGE_PORT,PackDefinition.Field.DOCUMENT_DEPARTURE_AIRPORT,PackDefinition.Field.DOCUMENT_DESTINATION_AIRPORT,PackDefinition.Field.DOCUMENT_COVERAGE_FROM,PackDefinition.Field.DOCUMENT_COVERAGE_TO,PackDefinition.Field.DOCUMENT_ORIGIN_COUNTRY).contains(r.left())&&r.effectiveMode()!=PackDefinition.Mode.MANUAL)bad("Geografische Angaben benötigen eine manuelle Kontextprüfung.");
   validateParameters(p,r);
   if(r.conditions()!=null){
    if(r.conditions().size()>8)bad("Maximal acht Anwendungsbedingungen erlaubt.");
    for(var c:r.conditions()){
     if(c==null||c.field()==null||c.field()==PackDefinition.Field.LITERAL||(c.operator()!=PackDefinition.Operator.EQ&&c.operator()!=PackDefinition.Operator.NE))bad("Bedingungen benötigen ein Feld und EQ oder NE.");
     if(p.schemaVersion()<5&&v5(c.field()))bad("Dieses Bedingungsfeld benötigt Schema 5.");
     if(p.schemaVersion()<3&&c.field().ordinal()>15)bad("Dieses Bedingungsfeld benötigt Schema 3.");
     if(p.schemaVersion()<4&&extended(c.field()))bad("Dieses Bedingungsfeld benötigt Schema 4.");
     text(c.value(),100,"Bedingungswert");
     var probe=new PackDefinition.Rule("probe","1.0.0",r.documentType(),c.field(),PackDefinition.Operator.EQ,c.field(),r.severity(),"probe","probe");
     if(PackEvaluator.compare(probe,c.value(),c.value())!=PackDefinition.Outcome.PASS)bad("Bedingungswert hat ein ungültiges Format.");
    }
   }
  }
  if(!checkTests)return;
  var names=new HashSet<String>();
  for(var t:p.tests()){
   if(t==null||t.expected()==null)bad("Testfall ist unvollständig.");
   if(p.schemaVersion()==1&&t.expected().ordinal()>2)bad("Erweiterte Testergebnisse benötigen Schema 2.");
   text(t.name(),100,"Testname");if(!names.add(t.name()))bad("Testname doppelt.");
   if(!ids.contains(t.ruleId()))bad("Testfall verweist auf unbekannte Regel.");
   if(t.left()!=null&&t.left().length()>100||t.right()!=null&&t.right().length()>100)bad("Testwerte zu lang.");
   var testRule=p.rules().stream().filter(r->r.id().equals(t.ruleId())).findFirst().orElseThrow();
   if(testRule.right()==PackDefinition.Field.LITERAL&&t.right()!=null)bad("Bei Festwerttests muss right null sein.");
   if(t.facts()!=null){
    if(p.schemaVersion()<2||t.facts().size()>(p.schemaVersion()>=5?RuleFacts.MAX_FIELDS:p.schemaVersion()==4?96:p.schemaVersion()==3?60:20))bad("Zu viele Test-Prüfdaten oder falsche Schema-Version.");
    if(p.schemaVersion()<5&&t.facts().keySet().stream().anyMatch(this::v5))bad("Diese Test-Prüfdaten benötigen Schema 5.");
    if(p.schemaVersion()<4&&t.facts().keySet().stream().anyMatch(this::extended))bad("Diese Test-Prüfdaten benötigen Schema 4.");
    if(p.schemaVersion()<3&&t.facts().keySet().stream().anyMatch(f->f.ordinal()>15))bad("Diese Test-Prüfdaten benötigen Schema 3.");
    var rule=p.rules().stream().filter(r->r.id().equals(t.ruleId())).findFirst().orElseThrow();
    if(t.facts().containsKey(rule.left())||t.facts().containsKey(rule.right())||t.facts().containsKey(PackDefinition.Field.LITERAL))bad("Vergleichswerte dürfen nicht zusätzlich unter facts stehen.");
    for(var entry:t.facts().entrySet())if(entry.getKey()==null||entry.getValue()!=null&&entry.getValue().length()>100)bad("Ungültige Test-Prüfdaten.");
   }
  }
  for(var id:ids){
   var outcomes=EnumSet.noneOf(PackDefinition.Outcome.class);
   p.tests().stream().filter(t->id.equals(t.ruleId())).forEach(t->outcomes.add(t.expected()));
   var rule=p.rules().stream().filter(r->r.id().equals(id)).findFirst().orElseThrow();
   var required=rule.effectiveMode()==PackDefinition.Mode.MANUAL
    ?EnumSet.of(PackDefinition.Outcome.MANUAL_REVIEW,PackDefinition.Outcome.NOT_EVALUABLE)
    :EnumSet.of(PackDefinition.Outcome.PASS,PackDefinition.Outcome.FAIL,PackDefinition.Outcome.NOT_EVALUABLE);
   if(rule.conditions()!=null&&!rule.conditions().isEmpty()){required.add(PackDefinition.Outcome.NOT_APPLICABLE);required.add(PackDefinition.Outcome.NOT_EVALUABLE);}
   if(!outcomes.containsAll(required))bad("Testfälle müssen Erfolg, Fehler, fehlende Daten sowie Anwendbarkeit der jeweiligen Regel abdecken.");
   if(rule.conditions()!=null&&!rule.conditions().isEmpty()){
    boolean missingScope=p.tests().stream().filter(t->id.equals(t.ruleId())&&t.expected()==PackDefinition.Outcome.NOT_EVALUABLE).anyMatch(t->{
     var facts=PackEvaluator.testFacts(rule,t);
     return rule.conditions().stream().anyMatch(c->facts.get(c.field())==null||facts.get(c.field()).isBlank())
      &&PackEvaluator.evaluate(rule,facts,p.calendars()==null?List.of():p.calendars(),p.schemaVersion()>=3)==PackDefinition.Outcome.NOT_EVALUABLE;
    });
    if(!missingScope)bad("Bedingte Regeln benötigen einen Test mit fehlenden Anwendungsdaten.");
   }
  }
 }
 private boolean extended(PackDefinition.Field field){return field.ordinal()>PackDefinition.Field.LC_EXAMINATION_DECISION_DATE.ordinal();}
 private boolean v5(PackDefinition.Field field){return field.ordinal()>PackDefinition.Field.LC_NOTIFY_ADDRESS_COUNTRY.ordinal();}
 private void validateParameters(PackDefinition p,PackDefinition.Rule r){
  var options=r.parameters();
  boolean membership=r.operator()==PackDefinition.Operator.IN||r.operator()==PackDefinition.Operator.NOT_IN;
  if(membership){
   if(p.schemaVersion()<5||r.right()!=PackDefinition.Field.LITERAL||!r.left().kind().equals("TEXT"))bad("IN/NOT_IN benötigen Schema 5 und einen Text-Festwertvergleich.");
   if(options==null||options.values()==null||options.values().isEmpty()||options.values().size()>20||new HashSet<>(options.values()).size()!=options.values().size())bad("Werteliste benötigt 1 bis 20 eindeutige Werte.");
   if(r.rightValue()!=null&&!r.rightValue().equals(String.join(",",options.values())))bad("Lesbare Festwertliste muss parameters.values entsprechen.");
   if(options.peerDocumentType()!=null||options.days()!=null||options.daysField()!=null||options.calendarId()!=null||options.percent()!=null||options.percentField()!=null)bad("Unbenutzte Listenparameter.");
   var probe=new PackDefinition.Rule("probe","1.0.0",r.documentType(),r.left(),PackDefinition.Operator.EQ,r.left(),r.severity(),"probe","probe");
   for(var value:options.values()){text(value,100,"Listenwert");if(PackEvaluator.compare(probe,value,value)!=PackDefinition.Outcome.PASS)bad("Ungültiger Listenwert.");}
   return;
  }
  if(options!=null&&options.values()!=null)bad("Werteliste nur für IN/NOT_IN erlaubt.");
  boolean peer=r.right().peer()||(r.conditions()!=null&&r.conditions().stream().anyMatch(c->c!=null&&c.field()!=null&&c.field().peer()));
  if(peer&&(options==null||options.peerDocumentType()==null))bad("Peer-Vergleich benötigt einen Gegen-Dokumenttyp.");
   if(options==null){if(r.operator().ordinal()>3)bad("Dieser Operator benötigt Parameter.");return;}
  if(!peer&&options.peerDocumentType()!=null)bad("Unbenutzter Gegen-Dokumenttyp.");
  if(options.peerDocumentType()==r.documentType())bad("Peer-Dokumenttyp muss vom Ausgangsdokumenttyp abweichen.");
  if(r.operator()==PackDefinition.Operator.WITHIN_DAYS){
   if(!r.left().kind().equals("DATE")||(options.days()==null)==(options.daysField()==null))bad("Frist benötigt Datumsfelder und genau eine Tagesgrenze.");
   if(options.days()!=null&&(options.days()<0||options.days()>3660))bad("Tagesgrenze außerhalb 0–3660.");
   if(options.daysField()!=null&&options.daysField()!=PackDefinition.Field.LC_PRESENTATION_PERIOD_DAYS)bad("Unzulässiges Fristfeld.");
   if(options.calendarId()!=null&&(p.calendars()==null||p.calendars().stream().noneMatch(c->c.id().equals(options.calendarId()))))bad("Unbekannter Bankkalender.");
   if(options.percent()!=null||options.percentField()!=null)bad("Prozentparameter sind bei Fristen unzulässig.");
  }else if(Set.of(PackDefinition.Operator.PERCENT_GTE,PackDefinition.Operator.PERCENT_LTE,PackDefinition.Operator.WITHIN_TOLERANCE).contains(r.operator())){
   if(!r.left().kind().equals("NUMBER")||r.left()==PackDefinition.Field.DOCUMENT_ORIGINAL_COUNT||(options.percent()==null)==(options.percentField()==null))bad("Prozentvergleich benötigt geeignete Zahlfelder und genau eine Prozentgrenze.");
   if(options.percent()!=null&&(options.percent().signum()<0||options.percent().compareTo(new java.math.BigDecimal("1000"))>0||options.percent().scale()>4||options.percent().precision()>8))bad("Ungültige Prozentgrenze.");
   if(options.percentField()!=null&&!Set.of(PackDefinition.Field.LC_INSURANCE_MIN_PERCENT,PackDefinition.Field.LC_TOLERANCE_PERCENT).contains(options.percentField()))bad("Unzulässiges Prozentfeld.");
   if(options.days()!=null||options.daysField()!=null||options.calendarId()!=null)bad("Fristparameter sind bei Prozentvergleichen unzulässig.");
  }else if(options.days()!=null||options.daysField()!=null||options.calendarId()!=null||options.percent()!=null||options.percentField()!=null)bad("Unbenutzte Vergleichsparameter.");
 }
 private void text(String value,int max,String label){
  if(value==null||value.isBlank()||value.length()>max)bad(label+" fehlt oder ist zu lang.");
 }
 private void token(String value,String pattern,String label){
  text(value,80,label);if(!value.matches(pattern))bad(label+" hat ein ungültiges Format.");
 }
 private void bad(String message){throw new IllegalArgumentException(message);}
}
