package de.ostms.lc.swift;
import de.ostms.lc.lc.domain.LetterOfCredit; import org.springframework.stereotype.Component; import java.math.BigDecimal; import java.time.*; import java.time.format.DateTimeFormatter; import java.util.*; import java.util.regex.*;
@Component public class Mt700Parser {
 private static final Pattern FIELD=Pattern.compile("(?m)^:(\\d{2}[A-Z]?):(.*?)(?=^:\\d{2}[A-Z]?:|\\z)",Pattern.DOTALL);
 public LetterOfCredit parse(String raw){ Map<String,String> f=new LinkedHashMap<>(); Matcher m=FIELD.matcher(raw.strip()); while(m.find()) f.put(m.group(1),m.group(2).trim());
  LetterOfCredit lc=new LetterOfCredit(); lc.setRawMessage(raw); lc.setReference(req(f,"20"));
  boolean advised=isAdvised(f);
  if(advised)lc.setReference(f.get("21").trim().replaceAll("\\s+",""));
  if(f.containsKey("31C")) lc.setIssueDate(date("31C",f.get("31C")));
  if(f.containsKey("31D")){ParsedDate p=leadingDate("31D",f.get("31D"));lc.setExpiryDate(p.date());lc.setExpiryPlace(p.rest());}
  if(f.containsKey("32B")){String x=f.get("32B").replace("\n","").trim();lc.setCurrency(x.substring(0,3));lc.setAmount(new BigDecimal(x.substring(3).trim().replace(" ","").replace(".","").replace(',','.')));}
  if(f.containsKey("44C"))lc.setLatestShipmentDate(leadingDate("44C",f.get("44C")).date());
  lc.setApplicant(BeneficiaryReferenceResolver.resolveApplicant(f).value()); lc.setBeneficiary(BeneficiaryReferenceResolver.resolve(f).value());
  if(f.containsKey("46A")) lc.setRequiredDocuments(splitConditions(f.get("46A")));
  lc.setSequenceOfTotal(f.get("27"));lc.setFormOfCredit(f.get("40A"));lc.setAvailableWith(first(f,"41A","41D"));lc.setDraweeBank(f.get("42A"));lc.setDraftsAt(f.get("42C"));
  lc.setMixedPaymentDetails(f.get("42M"));lc.setDeferredPaymentDetails(f.get("42P"));lc.setConfirmationInstructions(f.get("49"));lc.setReimbursingBank(first(f,"53A","53D"));
  lc.setConfirmationParty(first(f,"58A","58D"));lc.setCharges(f.get("71D"));lc.setBankInstructions(f.get("78"));
  if(advised){lc.getAdditionalFields().put("MT710 - Nachrichtenart","Von einer Zweitbank avisiertes Akkreditiv (MT710)");lc.getAdditionalFields().put("20 - Referenz der avisierenden Bank",f.get("20").trim());}
  unmappedFields(f).forEach((tag,value)->{if(!(advised&&tag.equals("21")))lc.getAdditionalFields().put(tag+" - "+label(tag),value);}); return lc;
 }
 private static final Pattern CONDITION_MARKER=Pattern.compile("^(?:[+*-]+|\\(?\\d{1,2}[.)])\\s*");
 /** SWIFT wraps lines at 65 characters: a line without "+", "-", "*" or "1." continues the previous condition. Without any marker every line stays its own condition. */
 /** Fields with their own LC column or read through {@code SwiftConditionAdapter}; everything else is kept as an additional field. */
 private static final Set<String> MAPPED=Set.of("20","31C","31D","32B","44C","46A","50","59","45A","47A","39A","48","43P","43T","44A","44E","44F","44B","40E","27","40A","41A","41D","42A","42C","42M","42P","49","53A","53D","58A","58D","71D","78");
 private static final Map<String,String> LABELS=Map.ofEntries(Map.entry("27","Sequenz / Gesamtzahl"),Map.entry("40A","Form des Dokumentenakkreditivs"),Map.entry("41A","Verfügbar bei / durch"),Map.entry("41D","Verfügbar bei / durch (Freitext)"),Map.entry("42A","Bezogene Bank"),Map.entry("42C","Trattenlaufzeit"),Map.entry("42D","Bezogener (Freitext)"),Map.entry("42M","Mixed Payment"),Map.entry("42P","Hinausgeschobene Zahlung"),Map.entry("49","Bestätigungsanweisung"),Map.entry("51A","Eröffnende Bank"),Map.entry("52A","Eröffnende Bank"),Map.entry("52D","Eröffnende Bank"),Map.entry("53A","Erstattende Bank"),Map.entry("57A","Avisierende Bank"),Map.entry("57D","Avisierende Bank"),Map.entry("58A","Bestätigende Bank"),Map.entry("71B","Gebühren"),Map.entry("71D","Gebühren"),Map.entry("72Z","Informationen Sender an Empfänger"),Map.entry("78","Anweisungen an die zahlende/akzeptierende Bank"),Map.entry("23","Referenz vorabinformiert"),Map.entry("33B","Weiterer Betrag"),Map.entry("39B","Höchstbetrag"),Map.entry("39C","Zusätzliche Beträge"),Map.entry("44D","Versandzeitraum"),Map.entry("31A","Frühester Termin"),Map.entry("43S","Zusätzliche Versandinformationen"));
 /** MT710 (advice of a documentary credit from a third bank) carries the credit number in :21: and the issuing bank in :52a:; the sender's own reference is :20:. */
 private static String first(Map<String,String> f,String... tags){for(String t:tags)if(f.get(t)!=null&&!f.get(t).isBlank())return f.get(t);return null;}
 public static boolean isAdvised(Map<String,String> fields){
  String number=fields.get("21");
  return number!=null&&!number.isBlank()&&(fields.containsKey("52A")||fields.containsKey("52D"))&&!fields.containsKey("26E");
 }
 public static String label(String tag){return LABELS.getOrDefault(tag,"Feld "+tag);}
 /** Unmappable fields in message order, trimmed and cut to the column size; the full text stays in the raw message. */
 static Map<String,String> unmappedFields(Map<String,String> fields){
  var result=new LinkedHashMap<String,String>();
  fields.forEach((tag,value)->{if(!MAPPED.contains(tag)&&value!=null&&!value.isBlank())result.put(tag,value.length()>4000?value.substring(0,4000):value.trim());});
  return result;
 }
 public static Optional<String> requiredDocumentsField(String raw){
  if(raw==null)return Optional.empty();
  Matcher m=FIELD.matcher(raw.strip());
  while(m.find())if("46A".equals(m.group(1)))return Optional.of(m.group(2).trim());
  return Optional.empty();
 }
 /** The pre-fix behaviour: every physical line was its own condition. */
 public static List<String> legacyConditions(String field){
  return Arrays.stream(field.split("\\R")).map(String::trim).filter(x->!x.isBlank()).map(x->x.replaceFirst("^[+*-]\\s*","")).toList();
 }
 public static List<String> splitConditions(String field){
  List<String> lines=Arrays.stream(field.split("\\R")).map(String::trim).filter(x->!x.isBlank()).toList();
  boolean marked=lines.stream().anyMatch(x->CONDITION_MARKER.matcher(x).find());
  List<String> result=new ArrayList<>();
  for(String line:lines){
   var marker=CONDITION_MARKER.matcher(line);
   if(!marked||marker.find()||result.isEmpty())result.add(marked&&marker.reset().find()?line.substring(marker.end()):line);
   else result.set(result.size()-1,result.get(result.size()-1)+" "+line);
  }
  return List.copyOf(result);
 }
 private String req(Map<String,String> f,String k){if(!f.containsKey(k)) throw new IllegalArgumentException("MT700 field :"+k+": is required"); return f.get(k);}
 private record ParsedDate(LocalDate date,String rest){}
 private ParsedDate leadingDate(String code,String raw){String x=raw.trim();Matcher printed=Pattern.compile("^(\\d{2})\\s*\\.\\s*(\\d{2})\\s*\\.\\s*(\\d{4})(.*)$",Pattern.DOTALL).matcher(x);try{if(printed.matches())return new ParsedDate(LocalDate.of(Integer.parseInt(printed.group(3)),Integer.parseInt(printed.group(2)),Integer.parseInt(printed.group(1))),printed.group(4).trim());if(x.length()<6)throw new DateTimeException("too short");return new ParsedDate(date(code,x.substring(0,6)),x.substring(6).trim());}catch(DateTimeException|NumberFormatException e){throw invalidDate(code,x);}}
 private LocalDate date(String code,String s){String x=s.trim();try{return LocalDate.parse(x,x.contains(".")?DateTimeFormatter.ofPattern("dd.MM.yyyy"):DateTimeFormatter.ofPattern("yyMMdd"));}catch(DateTimeException e){throw invalidDate(code,x);}}
 private IllegalArgumentException invalidDate(String code,String value){return new IllegalArgumentException("Feld :"+code+": enthält kein gültiges SWIFT-Datum (JJMMTT): „"+value+"“.");}
}
