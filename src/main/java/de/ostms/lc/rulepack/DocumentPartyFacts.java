package de.ostms.lc.rulepack;
import java.util.*;
import java.util.regex.Pattern;

/** Country of the applicant (buyer) and beneficiary (seller) as printed on a document, read from the block after the party label. */
public final class DocumentPartyFacts {
 public record Result(String applicantCountry,String applicantSource,String beneficiaryCountry,String beneficiarySource){}
 private static final String APPLICANT="buyer|applicant|importer|messrs\\.?|sold\\s+to|bill\\s+to|invoice\\s+to|auftraggeber|k(?:ä|ae)ufer|besteller|rechnung\\s+an";
 private static final String BENEFICIARY="seller|beneficiary|exporter|supplier|verk(?:ä|ae)ufer|beg(?:ü|ue)nstigter|lieferant";
 private static final Pattern APPLICANT_LABEL=Pattern.compile("(?i)^\\s*(?:"+APPLICANT+")\\s*(?:\\([^)]*\\))?(?:\\s*:\\s*(.*)|\\s*)$|^\\s*messrs\\.?\\s+(.+)$");
 private static final Pattern BENEFICIARY_LABEL=Pattern.compile("(?i)^\\s*(?:"+BENEFICIARY+")\\s*(?:\\([^)]*\\))?(?:\\s*:\\s*(.*)|\\s*)$");
 private static final Pattern ANY_LABEL=Pattern.compile("(?i)^\\s*(?:"+APPLICANT+"|"+BENEFICIARY+"|consignee|notify(?:\\s+party)?|shipper|invoice\\s+(?:no|number|date)|date|ship(?:ment)?|port|vessel|payment|terms|description|goods|marks|reference|ref)\\s*(?:\\([^)]*\\))?(?:\\s*:.*)?");
 /** "Label: value" starts the next field, so it ends the address block. */
 private static final Pattern FIELD_LINE=Pattern.compile("^\\s*\\p{L}[\\p{L}\\s./()-]{1,30}:\\s*\\S.*$");
 private static final int MAX_BLOCK_LINES=6;
 private DocumentPartyFacts(){}

 public static Result detect(String text){
  if(text==null||text.isBlank())return new Result(null,null,null,null);
  var lines=Arrays.asList((text.length()>100_000?text.substring(0,100_000):text).split("\\R"));
  var applicant=countries(lines,APPLICANT_LABEL);var beneficiary=countries(lines,BENEFICIARY_LABEL);
  return new Result(unique(applicant),source(applicant,"Käufer/Auftraggeber"),unique(beneficiary),source(beneficiary,"Verkäufer/Begünstigter"));
 }
 private static String unique(Map<String,String> found){return found.size()==1?found.keySet().iterator().next():null;}
 private static String source(Map<String,String> found,String role){return found.size()==1?"Adressblock nach „"+found.values().iterator().next()+"“ ("+role+")":null;}

 /** country → label text of the first block that led to it. */
 private static Map<String,String> countries(List<String> lines,Pattern label){
  var found=new LinkedHashMap<String,String>();
  for(int i=0;i<lines.size();i++){
   var m=label.matcher(lines.get(i));
   if(!m.matches())continue;
   String first=m.group(1)!=null?m.group(1):(m.groupCount()>=2&&m.group(2)!=null?m.group(2):"");
   var block=new StringBuilder(first.strip());
   for(int j=i+1;j<lines.size()&&j<=i+MAX_BLOCK_LINES;j++){
    String next=lines.get(j).strip();
    if(next.isEmpty()||ANY_LABEL.matcher(next).matches()||FIELD_LINE.matcher(next).matches())break;
    if(block.length()>0)block.append('\n');block.append(next);
   }
   var parsed=PartyAddress.parse(block.toString());
   if(parsed!=null&&parsed.country()!=null)found.putIfAbsent(parsed.country(),lines.get(i).strip().split("[:\\s]")[0]);
  }
  return found;
 }
}
