package de.ostms.lc.swift;
import de.ostms.lc.lc.domain.LetterOfCredit;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.DateTimeException;
import java.util.*;
import java.util.regex.*;

/** MT760 (issue of a demand guarantee or standby): kept as a dossier of kind GUARANTEE. Terms and all other fields stay readable as additional fields. */
@Component
public class Mt760Parser {
 private static final Pattern FIELD=Pattern.compile("(?m)^:(\\d{2}[A-Z]?):(.*?)(?=^:\\d{2}[A-Z]?:|\\z)",Pattern.DOTALL);
 private static final Set<String> MAPPED=Set.of("20","30","31E","31D","32B","50","59","52A","52D","57A","77C","77U");
 private static final Map<String,String> LABELS=Map.ofEntries(
  Map.entry("27","Sequenz / Gesamtzahl"),Map.entry("21","Bezugsreferenz"),Map.entry("22A","Zweck der Nachricht"),Map.entry("22D","Garantieform"),
  Map.entry("23","Weitere Identifikation"),Map.entry("23B","Ablaufart"),Map.entry("23H","Funktion der Nachricht"),Map.entry("24E","Zustellung der Garantie"),
  Map.entry("24G","Zustellung an / Abholung durch"),Map.entry("40C","Anwendbare Regeln"),Map.entry("45C","Dokumente und Vorlageanweisungen"),
  Map.entry("45L","Grundgeschäft"),Map.entry("15A","Neue Sequenz"),Map.entry("15B","Neue Sequenz"));
 public static String label(String tag){return LABELS.getOrDefault(tag,"Feld "+tag);}
 public static Map<String,String> fields(String raw){
  var f=new LinkedHashMap<String,String>();Matcher m=FIELD.matcher(raw.strip());
  while(m.find())f.put(m.group(1),m.group(2).trim());
  return f;
 }
 public LetterOfCredit parse(String raw){
  var f=fields(raw);
  String reference=f.get("20");if(reference==null||reference.isBlank())throw new IllegalArgumentException("MT760 field :20: is required");
  var g=new LetterOfCredit();g.setRawMessage(raw);g.setInstrumentType("GUARANTEE");g.setReference(reference.trim().replaceAll("\\s+",""));
  if(f.containsKey("30"))g.setIssueDate(date("30",f.get("30")));
  String expiry=f.containsKey("31E")?f.get("31E"):f.get("31D");
  if(expiry!=null&&!expiry.isBlank()){var parsed=leadingDate(expiry);g.setExpiryDate(parsed.date());g.setExpiryPlace(parsed.rest());}
  if(f.containsKey("32B")){
   String x=f.get("32B").replace("\n","").trim();
   if(x.length()>3){g.setCurrency(x.substring(0,3));try{g.setAmount(new BigDecimal(x.substring(3).trim().replace(" ","").replace(".","").replace(',','.')));}catch(NumberFormatException bad){throw new IllegalArgumentException("Feld :32B: enthält keinen gültigen Betrag: „"+x+"“.");}}
  }
  if(f.containsKey("50"))g.setApplicant(f.get("50"));
  if(f.containsKey("59"))g.setBeneficiary(f.get("59"));
  String issuer=f.containsKey("52A")?f.get("52A"):f.get("52D");if(issuer!=null)g.setIssuingBank(issuer);
  if(f.containsKey("57A"))g.setAdvisingBank(f.get("57A"));
  var extra=new LinkedHashMap<String,String>();
  extra.put("Instrument","Garantie / Standby (MT760)");
  String terms=f.containsKey("77C")?f.get("77C"):f.get("77U");
  if(terms!=null&&!terms.isBlank())extra.put("Garantiebedingungen",terms.length()>4000?terms.substring(0,4000):terms);
  f.forEach((tag,value)->{if(!MAPPED.contains(tag)&&value!=null&&!value.isBlank())extra.put(tag+" - "+label(tag),value.length()>4000?value.substring(0,4000):value);});
  g.setAdditionalFields(extra);
  return g;
 }
 private record Parsed(LocalDate date,String rest){}
 private static Parsed leadingDate(String raw){
  String x=raw.trim();
  if(x.length()<6)throw new IllegalArgumentException("Feld :31E: enthält kein gültiges SWIFT-Datum (JJMMTT): „"+x+"“.");
  return new Parsed(date("31E",x.substring(0,6)),x.substring(6).trim().isEmpty()?null:x.substring(6).trim());
 }
 private static LocalDate date(String code,String s){
  String x=s.trim();
  try{return LocalDate.parse(x.length()>6?x.substring(0,6):x,DateTimeFormatter.ofPattern("yyMMdd"));}
  catch(DateTimeException e){throw new IllegalArgumentException("Feld :"+code+": enthält kein gültiges SWIFT-Datum (JJMMTT): „"+x+"“.");}
 }
}
