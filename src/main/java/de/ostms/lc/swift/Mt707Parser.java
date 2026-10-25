package de.ostms.lc.swift;
import de.ostms.lc.lc.domain.Amendment;
import org.springframework.stereotype.Component;
import java.math.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.regex.*;

/** MT707 (amendment of a documentary credit). The credit number may stand in :21:, :23: or :20: depending on the bank, so all three are candidates. */
@Component public class Mt707Parser{
 private static final Pattern FIELD=Pattern.compile("(?m)^:(\\d{2}[A-Z]?):(.*?)(?=^:\\d{2}[A-Z]?:|\\z)",Pattern.DOTALL);
 /** Fields with their own column or handled explicitly; everything else is kept as an additional change. */
 private static final Set<String> HANDLED=Set.of("20","21","23","26E","30","31E","32B","33B","44C","45B","46B","47B");
 /** Fields that only describe the message itself. */
 private static final Set<String> IGNORED=Set.of("27","31C");
 public record Parsed(String lcReference,Amendment amendment,List<String> references){}
 public Parsed parse(String raw){
  Map<String,String> f=new LinkedHashMap<>();Matcher m=FIELD.matcher(raw.strip());while(m.find())f.put(m.group(1),m.group(2).trim());
  var references=new ArrayList<String>();
  for(String tag:List.of("21","23","20")){String v=f.get(tag);if(v==null)continue;String r=v.strip().replaceAll("\\s+","");if(!r.isEmpty()&&!references.contains(r))references.add(r);}
  if(references.isEmpty())throw new IllegalArgumentException("MT707: weder :21:, :23: noch :20: enthält eine Referenz");
  Amendment a=new Amendment();a.setRawMessage(raw);a.setAmendmentNumber(f.get("26E"));
  if(f.containsKey("30"))a.setAmendmentDate(date(f.get("30")));
  if(f.containsKey("31E")){String x=f.get("31E");a.setNewExpiryDate(date(x.substring(0,6)));if(x.length()>6)a.setNewExpiryPlace(x.substring(6).trim());}
  if(f.containsKey("44C"))a.setNewLatestShipmentDate(date(f.get("44C").substring(0,6)));
  if(f.containsKey("32B"))a.setAmountIncrease(amount(f.get("32B")));
  if(f.containsKey("33B"))a.setAmountDecrease(amount(f.get("33B")));
  a.setChangedGoods(f.get("45B"));a.setChangedDocuments(f.get("46B"));a.setChangedConditions(f.get("47B"));
  var other=new LinkedHashMap<String,String>();
  f.forEach((tag,value)->{if(!HANDLED.contains(tag)&&!IGNORED.contains(tag)&&value!=null&&!value.isBlank())other.put(tag,value.length()>4000?value.substring(0,4000):value);});
  if(!other.isEmpty()){try{a.setOtherChanges(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(other));}catch(com.fasterxml.jackson.core.JsonProcessingException impossible){throw new IllegalStateException(impossible);}}
  return new Parsed(references.get(0),a,List.copyOf(references));
 }
 private BigDecimal amount(String x){x=x.replace("\n","").trim();return new BigDecimal(x.substring(3).replace(".","").replace(',','.'));}
 private LocalDate date(String s){return LocalDate.parse(s.trim(),DateTimeFormatter.ofPattern("yyMMdd"));}
}
