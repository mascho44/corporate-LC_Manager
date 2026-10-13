package de.ostms.lc.customer;

import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.document.service.DocumentClassifier;

import java.math.BigInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the customer data of a submission letter (Einreichungsschreiben): customer, customer number, account number, IBAN and BIC. Labelled values only;
 * an IBAN counts only when its check digits are right. What is not found stays empty and the customer has to be chosen by hand.
 */
public final class SubmissionLetterReader {
 public record Fields(String customerName,String customerNumber,String accountNumber,String iban,String bic,
   String lcReference,String yourReference,String issuingBank,String applicant,String documentValue,String ibanCandidate){
  public Fields(String customerName,String customerNumber,String accountNumber,String iban,String bic){this(customerName,customerNumber,accountNumber,iban,bic,null,null,null,null,null,null);}
  public boolean any(){return customerName!=null||customerNumber!=null||accountNumber!=null||iban!=null||bic!=null;}
  /** Customer identifiers only; the letter's other data (credit, references, amount) never makes a document a submission letter. */
  public int count(){return (customerName!=null?1:0)+(customerNumber!=null?1:0)+(accountNumber!=null?1:0)+(iban!=null?1:0)+(bic!=null?1:0);}
 }
 private static final String SEP="[\\t ]*[:.]?[\\t ]*";
 private static final Pattern NUMBER=Pattern.compile("(?im)^[\\t >*-]*(?:kunden[-\\s]?(?:nummer|nr\\.?)|kd\\.?[-\\s]?nr\\.?|customer[\\s-]?(?:no\\.?|number|id)|client[\\s-]?(?:no\\.?|number))"+SEP+"([A-Z0-9][A-Z0-9/.-]{1,29})\\b");
 private static final Pattern ACCOUNT=Pattern.compile("(?im)^[\\t >*-]*(?:(?:[A-Z]{3}|FW)[-\\s])?(?:konto[-\\s]?(?:nummer|nr\\.?)|kontonr\\.?|account[\\s-]?(?:no\\.?|number)|a/c\\s?(?:no\\.?)?)"+SEP+"([0-9][0-9 .-]{3,28}[0-9])");
 private static final Pattern NAME=Pattern.compile("(?im)^[\\t >*-]*(?:kunde|kundenname|einreicher|customer(?: name)?|presenter|remitter)"+SEP+"(\\S[^\\r\\n]{2,120})$");
 private static final Pattern IBAN=Pattern.compile("(?i)\\b([A-Z]{2}\\d{2}(?:[\\t ]?[A-Z0-9]{4}){2,7}(?:[\\t ]?[A-Z0-9]{1,4})?)\\b");
 private static final Pattern BIC=Pattern.compile("(?im)^[\\t >*-]*(?:bic|swift(?:[-\\s]?code)?)"+SEP+"([A-Z]{6}[A-Z0-9]{2}(?:[A-Z0-9]{3})?)\\b");

private static final String REF="([A-Z0-9][A-Z0-9/.-]{3,38})\\b";
 private static final Pattern LC_REFERENCE=Pattern.compile("(?im)^[\\t >*-]*(?:akkreditiv[-\\s]?(?:nummer|nr\\.?)|akkreditivnr\\.?|lc[\\s-]?(?:number|no\\.?|reference)|documentary credit (?:no\\.?|number))"+SEP+REF);
 private static final Pattern YOUR_REFERENCE=Pattern.compile("(?im)^[\\t >*-]*(?:ihre referenz|your ref(?:erence)?\\.?)"+SEP+REF);
 private static final Pattern ISSUING_BANK=Pattern.compile("(?im)^[\\t >*-]*(?:er[öo]ffnende bank|issuing bank)"+SEP+"(\\S[^\\r\\n]{2,120})$");
 private static final Pattern APPLICANT=Pattern.compile("(?im)^[\\t >*-]*(?:auftraggeber|applicant)"+SEP+"(\\S[^\\r\\n]{2,120})$");
 private static final Pattern DOCUMENT_VALUE=Pattern.compile("(?im)dokumentengegenwert(?:[\\t ]*/[\\t ]*w[äa]hrung)?[\\t ]*:?[\\t _]*([A-Z]{3})[\\t ]*([0-9][0-9.,]*[0-9])");
 /** An account label followed by something shaped like an IBAN (handwriting is often read with errors, so this one may fail the check digits). */
 private static final Pattern ACCOUNT_IBAN=Pattern.compile("(?im)konto[-\\s]*(?:nummer|nr\\.?)?[\\t ]*[:.]?[\\t _]*([A-Z]{2}\\d{2}(?:[\\t ]?[A-Z0-9]{4}){3,7}(?:[\\t ]?[A-Z0-9]{1,4})?)");

 private SubmissionLetterReader(){}

 /** A document is treated as submission letter when it is classified as one, or when it names at least two customer identifiers. */
 public static boolean looksLikeSubmission(String filename,String text){
  if(text==null||text.isBlank())return false;
  var classification=DocumentClassifier.classify(filename,text);
  return classification.suggestedType()==DocumentType.DOCUMENT_SUBMISSION||read(text).count()>=3;
 }

 public static Fields read(String text){
  if(text==null)return new Fields(null,null,null,null,null);
  String body=text.length()>20_000?text.substring(0,20_000):text;
  String candidate=null,iban=null;
  Matcher account=ACCOUNT_IBAN.matcher(body);
  while(account.find()){String value=normalizeIban(account.group(1));if(validIban(value)){iban=value;break;}if(candidate==null)candidate=value;}
  if(iban==null)iban=firstValidIban(body);
  if(iban!=null)candidate=null;
  Matcher value=DOCUMENT_VALUE.matcher(body);
  String documentValue=value.find()?value.group(1).toUpperCase()+" "+value.group(2):null;
  return new Fields(first(NAME,body),clean(first(NUMBER,body)),digits(first(ACCOUNT,body)),iban,upper(first(BIC,body)),
    upper(first(LC_REFERENCE,body)),upper(first(YOUR_REFERENCE,body)),first(ISSUING_BANK,body),first(APPLICANT,body),documentValue,candidate);
 }

 private static String first(Pattern pattern,String text){Matcher m=pattern.matcher(text);return m.find()?m.group(1).strip():null;}
 private static String clean(String value){return value==null||value.isBlank()?null:value.strip();}
 private static String upper(String value){return value==null?null:value.toUpperCase();}
 private static String digits(String value){if(value==null)return null;String d=value.replaceAll("[^0-9]","");return d.isEmpty()?null:d;}

 static String firstValidIban(String text){
  Matcher m=IBAN.matcher(text);
  while(m.find()){String candidate=normalizeIban(m.group(1));if(validIban(candidate))return candidate;}
  return null;
 }
 public static String normalizeIban(String value){return value==null?null:value.replaceAll("\\s+","").toUpperCase();}
 public static boolean validIban(String iban){
  if(iban==null||iban.length()<15||iban.length()>34||!iban.matches("[A-Z]{2}\\d{2}[A-Z0-9]+"))return false;
  String rearranged=iban.substring(4)+iban.substring(0,4);
  StringBuilder digits=new StringBuilder();
  for(char c:rearranged.toCharArray())digits.append(Character.isDigit(c)?String.valueOf(c):String.valueOf(c-'A'+10));
  return new BigInteger(digits.toString()).mod(BigInteger.valueOf(97)).intValue()==1;
 }
}
