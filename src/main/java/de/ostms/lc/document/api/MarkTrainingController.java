package de.ostms.lc.document.api;

import de.ostms.lc.document.domain.DocumentMark;
import de.ostms.lc.document.repository.DocumentMarkRepository;
import de.ostms.lc.document.repository.LcDocumentRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** Overview for the page "Stempel-Training": how many decisions were made per kind and in which documents. */
@RestController @RequestMapping("/api/mark-training")
public class MarkTrainingController {
 private final DocumentMarkRepository marks;private final LcDocumentRepository documents;
 public MarkTrainingController(DocumentMarkRepository marks,LcDocumentRepository documents){this.marks=marks;this.documents=documents;}

 public record DocumentRow(UUID documentId,String filename,int marks){}
 public record Summary(Map<String,Long> perKind,int documents,int total,List<DocumentRow> recent){}

 @GetMapping("/summary") @Transactional(readOnly=true)
 public Summary summary(){
  var all=marks.findAll();
  Map<String,Long> perKind=new LinkedHashMap<>();
  for(var k:DocumentMark.Kind.values())perKind.put(k.name(),0L);
  Map<UUID,Integer> perDocument=new LinkedHashMap<>();
  for(var m:all){perKind.merge(m.getKind().name(),1L,Long::sum);perDocument.merge(m.getDocumentId(),1,Integer::sum);}
  List<DocumentRow> rows=new ArrayList<>();
  perDocument.entrySet().stream().limit(100).forEach(e->documents.findById(e.getKey()).ifPresent(d->rows.add(new DocumentRow(d.getId(),d.getOriginalFilename(),e.getValue()))));
  return new Summary(perKind,perDocument.size(),all.size(),rows);
 }
}
