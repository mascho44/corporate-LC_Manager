package de.ostms.lc.document.service;
import de.ostms.lc.document.domain.DocumentInboxItem;
import java.util.*;
/** Automatic values remain proposals. Missing or ambiguous evidence is explicitly unavailable. */
public final class RecognitionFacts {
 private RecognitionFacts(){}
 public record Field(String name,String value,String status,List<OcrEvidence.Word> words){}
 public static List<Field> from(DocumentInboxItem item){
  var evidence=DocumentExtractionService.readEvidence(item.getOcrEvidenceJson());
  var values=new LinkedHashMap<String,String>();
  values.put("lcReference",item.getExtractedReference());values.put("documentNumber",item.getExtractedDocumentNumber());
  var date=DocumentDateDetector.detect(item.getExtractedText());
  values.put("documentDate",date.date()==null?null:date.date().toString());
  var copy=DocumentCopyDetector.detect(item.getExtractedText());
  values.put("originalCopy",copy.copyNumber()==null?null:copy.evidence());
  var fields=new ArrayList<Field>();
  for(var entry:values.entrySet()){
   if(entry.getValue()==null){fields.add(new Field(entry.getKey(),null,"NOT_FOUND_OR_AMBIGUOUS",List.of()));continue;}
   var assessment=evidence==null?null:evidence.assess(entry.getValue(),evidence.threshold());
   fields.add(new Field(entry.getKey(),entry.getValue(),assessment==null||assessment.words().isEmpty()?"REVIEW_NO_EXACT_LOCATION":"REVIEW",assessment==null?List.of():assessment.words()));
  }
  return List.copyOf(fields);
 }
}
