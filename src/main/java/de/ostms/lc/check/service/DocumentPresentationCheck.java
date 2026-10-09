package de.ostms.lc.check.service;
import de.ostms.lc.document.domain.*;
import de.ostms.lc.check.api.CheckResult;
import java.util.*;
import java.util.regex.Pattern;

/** Counts captured designations only; never asserts authenticity or physical presentation. */
final class DocumentPresentationCheck {
 record Counts(int originals,int copies,int unspecified,boolean duplicateNumbers,boolean multipleDocuments){}
 static Counts count(List<LcDocument> docs){
  int originals=0,copies=0,unknown=0;boolean duplicates=false;var numbers=new HashSet<Integer>();var references=new HashSet<String>();
  for(var doc:docs){
   if(doc.getExtractedDocumentNumber()!=null&&!doc.getExtractedDocumentNumber().isBlank())references.add(doc.getExtractedDocumentNumber().strip().toUpperCase(Locale.ROOT));
   Integer copy=doc.getCopyNumber();if(copy==null){unknown++;continue;}
   if(copy<=0)originals++;else copies++;
  }
  return new Counts(originals,copies,unknown,duplicates,references.size()>1||(docs.size()>1&&docs.stream().anyMatch(d->d.getExtractedDocumentNumber()==null||d.getExtractedDocumentNumber().isBlank())));
 }
 static OptionalInt required(String requirement,boolean originals){
  if(requirement.toLowerCase(Locale.ROOT).matches("(?s).*\\b(or|alternatively|unless|optional|oder|wahlweise)\\b.*"))return OptionalInt.empty();
  String number="(?:[1-9][0-9]?|one|two|three|four|five|six|ein|eine|einem|einen|zwei|drei|vier|fünf|sechs)";
  String noun=originals?"originals?|originalen?|originale":"copies|copy|kopien?|kopie";
  var fractions=Pattern.compile("(?iu)\\b([0-9]+)\\s*/\\s*([0-9]+)\\s+(?:"+noun+")\\b").matcher(requirement);
  while(fractions.find())if(!fractions.group(1).equals(fractions.group(2)))return OptionalInt.empty();
  var matcher=Pattern.compile("(?iu)(?<![\\w/])\\b("+number+")(?:\\s*/\\s*\\1)?\\s+(?:signed\\s+)?(?:"+noun+")\\b").matcher(requirement);
  var values=new HashSet<Integer>();while(matcher.find())values.add(switch(matcher.group(1).toLowerCase(Locale.ROOT)){case "one","ein","eine","einem","einen"->1;case "two","zwei"->2;case "three","drei"->3;case "four","vier"->4;case "five","fünf"->5;case "six","sechs"->6;default->Integer.parseInt(matcher.group(1));});
  return values.size()==1?OptionalInt.of(values.iterator().next()):OptionalInt.empty();
 }
 static List<CheckResult> evaluate(String requirement,List<LcDocument> docs){
  if(docs.isEmpty())return List.of();var counts=count(docs);var results=new ArrayList<CheckResult>();
  String evidence="Captured designations: "+counts.originals()+" originals, "+counts.copies()+" copies, "+counts.unspecified()+" unspecified. Each document counts once. Physical originals and authenticity require human review.";
  String name=docs.get(0).getOriginalFilename();boolean uncertain=counts.unspecified()>0||counts.duplicateNumbers()||counts.multipleDocuments();
  var original=required(requirement,true);var copy=required(requirement,false);
  for(boolean originals:new boolean[]{true,false}){
   var expected=originals?original:copy;if(expected.isEmpty())continue;
   int actual=originals?counts.originals():counts.copies();var severity=uncertain?CheckResult.Severity.WARNING:actual<expected.getAsInt()?CheckResult.Severity.DISCREPANCY:CheckResult.Severity.OK;
   String reason=counts.multipleDocuments()?"Different or missing document numbers: verify that these belong to one document set.":counts.duplicateNumbers()?"Duplicate Original/Copy numbers require review.":counts.unspecified()>0?"Unspecified designations require review.":"Captured designations only; no authenticity claim.";
   results.add(new CheckResult(severity,originals?"DOCUMENT_ORIGINAL_COUNT":"DOCUMENT_COPY_COUNT",(originals?"Originals":"Copies")+": "+actual+" captured / "+expected.getAsInt()+" required. "+reason,requirement,name,evidence));
  }
  if(results.isEmpty())results.add(new CheckResult(CheckResult.Severity.WARNING,"DOCUMENT_COPIES_MANUAL_REVIEW","The required Original/Copy quantity is not unambiguous. Review manually.",requirement,name,evidence));
  return List.copyOf(results);
 }
 private DocumentPresentationCheck(){}
}
