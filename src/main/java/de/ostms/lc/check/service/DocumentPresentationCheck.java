package de.ostms.lc.check.service;
import de.ostms.lc.document.domain.*;
import de.ostms.lc.check.api.CheckResult;
import java.util.*;
import java.util.regex.Pattern;

/** Counts captured designations only; never asserts authenticity or physical presentation. */
final class DocumentPresentationCheck {
 record Counts(int originals,int copies,int unspecified,boolean duplicateNumbers,boolean multipleDocuments,int fromText){}
 static Counts count(List<LcDocument> docs){
  int originals=0,copies=0,unknown=0,fromText=0;boolean duplicates=false;var numbers=new HashSet<Integer>();var references=new HashSet<String>();
  for(var doc:docs){
   if(doc.getExtractedDocumentNumber()!=null&&!doc.getExtractedDocumentNumber().isBlank())references.add(doc.getExtractedDocumentNumber().strip().toUpperCase(Locale.ROOT));
   Integer copy=doc.getCopyNumber();
   if(copy==null){
    // nothing entered: the stamp text (ORIGINAL / COPY / KOPIE ...) counts, marked as recognised, not confirmed
    var hint=de.ostms.lc.document.service.DocumentCopyDetector.detect(doc.getExtractedText());
    if("ORIGINAL".equals(hint.kind())){originals++;fromText++;}else if("COPY".equals(hint.kind())){copies++;fromText++;}else unknown++;
    continue;
   }
   if(copy<=0)originals++;else copies++;
  }
  return new Counts(originals,copies,unknown,duplicates,references.size()>1||(docs.size()>1&&docs.stream().anyMatch(d->d.getExtractedDocumentNumber()==null||d.getExtractedDocumentNumber().isBlank())),fromText);
 }
 static OptionalInt required(String requirement,boolean originals){
  if(requirement.toLowerCase(Locale.ROOT).matches("(?s).*\\b(or|alternatively|unless|optional|oder|wahlweise)\\b.*"))return OptionalInt.empty();
  String number="(?:[1-9][0-9]?|one|two|three|four|five|six|ein|eine|einem|einen|zwei|drei|vier|fünf|sechs)";
  String noun=originals?"originals?|originalen?|originale":"copies|copy|kopien?|kopie";
  var fractions=Pattern.compile("(?iu)\\b([0-9]+)\\s*/\\s*([0-9]+)\\s+(?:"+noun+")\\b").matcher(requirement);
  while(fractions.find())if(!fractions.group(1).equals(fractions.group(2)))return OptionalInt.empty();
  var matcher=Pattern.compile("(?iu)(?<![\\w/])\\b("+number+")(?:\\s*/\\s*\\1)?\\s+(?:signed\\s+|(?:non[- ]?negotiable|additional|extra|true|photo)\\s+){0,2}(?:"+noun+")\\b").matcher(requirement);
  var values=new HashSet<Integer>();while(matcher.find())values.add(switch(matcher.group(1).toLowerCase(Locale.ROOT)){case "one","ein","eine","einem","einen"->1;case "two","zwei"->2;case "three","drei"->3;case "four","vier"->4;case "five","fünf"->5;case "six","sechs"->6;default->Integer.parseInt(matcher.group(1));});
  return values.size()==1?OptionalInt.of(values.iterator().next()):OptionalInt.empty();
 }
 private static final Pattern FULL_SET=Pattern.compile("(?i)\\bfull\\s+sets?\\b|\\bvollst(?:ä|ae)ndig\\w*\\s+(?:satz|ausfertigung)");
 /** "Full set" names no number; the number of originals is what the bill of lading itself states. */
 static OptionalInt fullSetOriginals(String requirement,List<LcDocument> docs){
  if(!FULL_SET.matcher(requirement).find())return OptionalInt.empty();
  var stated=new TreeSet<Integer>();
  for(var doc:docs){var n=de.ostms.lc.document.service.StatedOriginals.detect(doc.getExtractedText());if(n.isPresent())stated.add(n.getAsInt());}
  return stated.size()==1?OptionalInt.of(stated.first()):OptionalInt.empty();
 }
 static List<CheckResult> evaluate(String requirement,List<LcDocument> docs){
  if(docs.isEmpty())return List.of();var counts=count(docs);var results=new ArrayList<CheckResult>();
  String evidence="Erfasste Kennzeichnungen: "+counts.originals()+" "+(counts.originals()==1?"Original":"Originale")+", "+counts.copies()+" "+(counts.copies()==1?"Kopie":"Kopien")+", "+counts.unspecified()+" ohne Kennzeichnung. Jedes Dokument zählt einmal. Physische Originale und Echtheit müssen von einer Person geprüft werden.";
  String name=docs.get(0).getOriginalFilename();boolean uncertain=counts.unspecified()>0||counts.duplicateNumbers()||counts.multipleDocuments();
  var original=required(requirement,true);if(original.isEmpty())original=fullSetOriginals(requirement,docs);var copy=required(requirement,false);
  for(boolean originals:new boolean[]{true,false}){
   var expected=originals?original:copy;if(expected.isEmpty())continue;
   int actual=originals?counts.originals():counts.copies();var severity=uncertain?CheckResult.Severity.WARNING:actual<expected.getAsInt()?(counts.fromText()>0?CheckResult.Severity.WARNING:CheckResult.Severity.DISCREPANCY):CheckResult.Severity.OK;
   String reason=counts.multipleDocuments()?"Unterschiedliche oder fehlende Dokumentnummern: bitte prüfen, ob die Dokumente zu einem Satz gehören.":counts.duplicateNumbers()?"Doppelte Original-/Kopie-Nummern müssen geprüft werden.":counts.unspecified()>0?"Dokumente ohne Original-/Kopie-Kennzeichnung müssen geprüft werden.":counts.fromText()>0?"Davon "+counts.fromText()+" aus dem Stempeltext erkannt, nicht bestätigt; bitte die Kennzeichnung am Dokument bestätigen. Keine Aussage zur Echtheit.":"Nur erfasste Kennzeichnungen, keine Aussage zur Echtheit.";
   results.add(new CheckResult(severity,originals?"DOCUMENT_ORIGINAL_COUNT":"DOCUMENT_COPY_COUNT",(originals?"Originale":"Kopien")+": "+actual+(counts.fromText()>0?" erkannt / ":" erfasst / ")+expected.getAsInt()+" gefordert. "+reason,requirement,name,evidence));
  }
  if(results.isEmpty())results.add(new CheckResult(CheckResult.Severity.WARNING,"DOCUMENT_COPIES_MANUAL_REVIEW","Die geforderte Anzahl Originale/Kopien ist nicht eindeutig (z. B. „in duplicate“). Bitte manuell prüfen.",requirement,name,evidence));
  return List.copyOf(results);
 }
 private DocumentPresentationCheck(){}
}
