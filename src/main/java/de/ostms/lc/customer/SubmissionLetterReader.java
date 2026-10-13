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
 public record Fields(String customerName,String customerNumber,String accountNumber,String iban,String bic){
  public boolean any(){return customerName!=null||customerNumber!=null||accountNumber!=null||iban!=null||bic!=null;}
  public int count(){return (customerName!=null?1:0)+(customerNumber!=null?1:0)+(accountNumber!=null?1:0)+(iban!=null?1:0)+(bic!=null?1:0);}
 }
 private static final String SEP="[\\t ]*[:.]?[\\t ]*";
 private static final Pattern NUMBER=Pattern.compile("(?im)^[\\t >*-]*(?:kunden[-\\s]?(?:nummer|nr\\.?)|kd\\.?[-\\s]?nr\\.?|customer[\\s-]?(?:no\\.?|number|id)|client[\\s-]?(?:no\\.?|number))"+SEP+"([A-Z0-9][A-Z0-9/.-]{1,29})\\b");
 private static final Pattern ACCOUNT=Pattern.compile("(?im)^[\\t >*-]*(?:konto[-\\s]?(?:nummer|nr\\.?)|kontonr\\.?|account[\\s-]?(?:no\\.?|number)|a/c\\s?(?:no\\.?)?)"+SEP+"([0-9][0-9 .-]{3,28}[0-9])");
 private static final Pattern NAME=Pattern.compile("(?im)^[\\t >*-]*(?:kunde|kundenname|einreicher|auftraggeber|customer(?: name)?|presenter|remitter)"+SEP+"(\\S[^\\r\\n]{2,120})$");
 private static final Pattern IBAN=Pattern.compile("(?i)\\b([A-Z]{2}\\d{2}(?:[\\t ]?[A-Z0-9]{4}){2,7}(?:[\\t ]?[A-Z0-9]{1,4})?)\\b");
 private static final Pattern BIC=Pattern.compile("(?im)^[\\t >*-]*(?:bic|swift(?:[-\\s]?code)?)"+SEP+"([A-Z]{6}[A-Z0-9]{2}(?:[A-Z0-9]{3})?)\\b");

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
  return new Fields(first(NAME,body),clean(first(NUMBER,body)),digits(first(ACCOUNT,body)),firstValidIban(body),upper(first(BIC,body)));
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
