package de.corporate.lc.swift;
import de.corporate.lc.lc.domain.LetterOfCredit; import org.springframework.stereotype.Component; import java.math.BigDecimal; import java.time.*; import java.time.format.DateTimeFormatter; import java.util.*; import java.util.regex.*;
@Component public class Mt700Parser {
 private static final Pattern FIELD=Pattern.compile("(?m)^:(\\d{2}[A-Z]?):(.*?)(?=^:\\d{2}[A-Z]?:|\\z)",Pattern.DOTALL);
 public LetterOfCredit parse(String raw){ Map<String,String> f=new LinkedHashMap<>(); Matcher m=FIELD.matcher(raw.strip()); while(m.find()) f.put(m.group(1),m.group(2).trim());
  LetterOfCredit lc=new LetterOfCredit(); lc.setRawMessage(raw); lc.setReference(req(f,"20"));
  if(f.containsKey("31C")) lc.setIssueDate(date(f.get("31C")));
  if(f.containsKey("31D")){ParsedDate p=leadingDate(f.get("31D"));lc.setExpiryDate(p.date());lc.setExpiryPlace(p.rest());}
  if(f.containsKey("32B")){String x=f.get("32B").replace("\n","").trim();lc.setCurrency(x.substring(0,3));lc.setAmount(new BigDecimal(x.substring(3).trim().replace(" ","").replace(".","").replace(',','.')));}
  if(f.containsKey("44C"))lc.setLatestShipmentDate(leadingDate(f.get("44C")).date());
  lc.setApplicant(f.get("50")); lc.setBeneficiary(f.get("59"));
  if(f.containsKey("46A")) lc.setRequiredDocuments(Arrays.stream(f.get("46A").split("\\R")).map(String::trim).filter(s->!s.isBlank()).map(s->s.replaceFirst("^[+*-]\\s*","")).toList()); return lc;
 }
 private String req(Map<String,String> f,String k){if(!f.containsKey(k)) throw new IllegalArgumentException("MT700 field :"+k+": is required"); return f.get(k);}
 private record ParsedDate(LocalDate date,String rest){}
 private ParsedDate leadingDate(String raw){String x=raw.trim();Matcher printed=Pattern.compile("^(\\d{2})\\s*\\.\\s*(\\d{2})\\s*\\.\\s*(\\d{4})(.*)$",Pattern.DOTALL).matcher(x);if(printed.matches())return new ParsedDate(LocalDate.of(Integer.parseInt(printed.group(3)),Integer.parseInt(printed.group(2)),Integer.parseInt(printed.group(1))),printed.group(4).trim());if(x.length()<6)throw new IllegalArgumentException("Datum ist unvollständig: "+x);return new ParsedDate(date(x.substring(0,6)),x.substring(6).trim());}
 private LocalDate date(String s){String x=s.trim();return LocalDate.parse(x,x.contains(".")?DateTimeFormatter.ofPattern("dd.MM.yyyy"):DateTimeFormatter.ofPattern("yyMMdd"));}
}
