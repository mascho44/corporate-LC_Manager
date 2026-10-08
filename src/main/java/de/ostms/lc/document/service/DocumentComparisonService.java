package de.ostms.lc.document.service;
import de.ostms.lc.document.domain.*;
import de.ostms.lc.document.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
@Service
public class DocumentComparisonService {
 private final LcDocumentRepository documents;private final DocumentComparisonRepository comparisons;
 private final ObjectMapper json;
 public DocumentComparisonService(LcDocumentRepository documents,DocumentComparisonRepository comparisons,ObjectMapper json){this.documents=documents;this.comparisons=comparisons;this.json=json;}
 public record Field(String name,String before,String after,boolean changed){}
 public record Result(String beforeName,String afterName,List<Field> fields){}
 @Transactional public DocumentComparison compare(UUID lcId,UUID beforeId,UUID afterId,String username){
  if(beforeId.equals(afterId))throw new IllegalArgumentException("Bitte zwei verschiedene Dokumente wählen.");
  var before=owned(lcId,beforeId);var after=owned(lcId,afterId);
  if(before.getDocumentType()!=after.getDocumentType())throw new IllegalArgumentException("Dokumentversionen müssen denselben Dokumenttyp haben.");
  var result=diff(before,after);var saved=new DocumentComparison();saved.lcId=lcId;saved.beforeDocumentId=beforeId;saved.afterDocumentId=afterId;saved.createdBy=username;saved.createdAt=LocalDateTime.now();
  try{saved.resultJson=json.writeValueAsString(result);}catch(Exception e){throw new IllegalStateException("Vergleich konnte nicht gespeichert werden",e);}
  return comparisons.save(saved);
 }
 private LcDocument owned(UUID lcId,UUID id){var d=documents.findById(id).orElseThrow();if(!lcId.equals(d.getLetterOfCredit().getId()))throw new IllegalArgumentException("Dokument gehört nicht zur LC-Akte.");return d;}
 @Transactional(readOnly=true) public List<DocumentComparison> history(UUID lcId){return comparisons.findByLcIdOrderByCreatedAtDesc(lcId);}
 static Result diff(LcDocument before,LcDocument after){
  Map<String,Object> a=values(before),b=values(after);List<Field> fields=new ArrayList<>();
  a.forEach((key,value)->fields.add(new Field(key,text(value),text(b.get(key)),!Objects.equals(text(value),text(b.get(key))))));
  return new Result(before.getOriginalFilename(),after.getOriginalFilename(),fields);
 }
 private static String text(Object value){return value==null?null:value instanceof java.math.BigDecimal n?n.stripTrailingZeros().toPlainString():value.toString();}
 private static Map<String,Object> values(LcDocument d){Map<String,Object> result=new LinkedHashMap<>();result.put("Dokumentdatum",d.getDocumentDate());result.put("Betrag",d.getAmount());result.put("Währung",d.getCurrency());result.put("LC-Referenz",d.getExtractedReference());result.put("Dokumentnummer",d.getExtractedDocumentNumber());result.put("Extrahierter Betrag",d.getExtractedAmount());result.put("Extrahierte Währung",d.getExtractedCurrency());result.put("Dokumenttext",d.getExtractedText());return result;}
}
