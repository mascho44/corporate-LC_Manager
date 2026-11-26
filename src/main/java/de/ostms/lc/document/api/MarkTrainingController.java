package de.ostms.lc.document.api;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.document.domain.DocumentMark;
import de.ostms.lc.document.domain.MarkTrainingDocument;
import de.ostms.lc.document.repository.DocumentMarkRepository;
import de.ostms.lc.document.repository.LcDocumentRepository;
import de.ostms.lc.document.repository.MarkTrainingDocumentRepository;
import de.ostms.lc.document.service.PdfPagePreviewService;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

/** Page "Stempel-Training": the decisions made so far, and training documents (PDFs without an LC file) to mark more. */
@RestController @RequestMapping("/api/mark-training")
public class MarkTrainingController {
 static final int MAX_DOCUMENTS=200,MAX_BYTES=20*1024*1024,MAX_PAGES=100;
 private final DocumentMarkRepository marks;private final LcDocumentRepository documents;private final MarkTrainingDocumentRepository training;private final PdfPagePreviewService preview;private final AuditService audit;
 public MarkTrainingController(DocumentMarkRepository marks,LcDocumentRepository documents,MarkTrainingDocumentRepository training,PdfPagePreviewService preview,AuditService audit){this.marks=marks;this.documents=documents;this.training=training;this.preview=preview;this.audit=audit;}

 public record DocumentRow(UUID documentId,String filename,int marks,boolean training){}
 public record Summary(Map<String,Long> perKind,int documents,int total,List<DocumentRow> recent){}

 @GetMapping("/summary") @Transactional(readOnly=true)
 public Summary summary(){
  var all=marks.findAll();
  Map<String,Long> perKind=new LinkedHashMap<>();
  for(var k:DocumentMark.Kind.values())perKind.put(k.name(),0L);
  Map<UUID,Integer> perDocument=new LinkedHashMap<>(),perTraining=new LinkedHashMap<>();
  for(var m:all){
   perKind.merge(m.getKind().name(),1L,Long::sum);
   if(m.getTrainingDocumentId()!=null)perTraining.merge(m.getTrainingDocumentId(),1,Integer::sum);else perDocument.merge(m.getDocumentId(),1,Integer::sum);
  }
  List<DocumentRow> rows=new ArrayList<>();
  for(var t:training.rows())rows.add(new DocumentRow(t.id(),t.filename(),perTraining.getOrDefault(t.id(),0),true));
  perDocument.entrySet().stream().limit(100).forEach(e->documents.findById(e.getKey()).ifPresent(d->rows.add(new DocumentRow(d.getId(),d.getOriginalFilename(),e.getValue(),false))));
  return new Summary(perKind,perDocument.size()+perTraining.size(),all.size(),rows);
 }

 static String cleanName(String name){
  String n=name==null?"":name.replaceAll("[\\p{Cntrl}/\\\\]","_").trim();
  if(n.length()>200)n=n.substring(0,200);
  return n.isEmpty()?"training.pdf":n;
 }
 static boolean looksLikePdf(byte[] content){return content!=null&&content.length>8&&content[0]=='%'&&content[1]=='P'&&content[2]=='D'&&content[3]=='F'&&content[4]=='-';}

 @PostMapping(value="/documents",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @Transactional @ResponseStatus(HttpStatus.CREATED)
 public DocumentRow upload(@RequestPart("file") MultipartFile file,Authentication auth)throws Exception{
  if(file==null||file.isEmpty()||file.getSize()>MAX_BYTES)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Bitte eine PDF-Datei bis 20 MB wählen.");
  byte[] content=file.getBytes();
  if(!looksLikePdf(content))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Bitte eine PDF-Datei bis 20 MB wählen.");
  if(training.rows().size()>=MAX_DOCUMENTS)throw new ResponseStatusException(HttpStatus.CONFLICT,"Zu viele Trainingsdokumente.");
  int pages;
  try{pages=preview.pageCount(content);}catch(Exception unreadable){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Die PDF-Datei konnte nicht gelesen werden.");}
  if(pages<1||pages>MAX_PAGES)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Die PDF-Datei hat zu viele Seiten.");
  var saved=training.save(new MarkTrainingDocument(cleanName(file.getOriginalFilename()),pages,content,auth==null?"unknown":auth.getName()));
  audit.recordInTransaction(auth,"MARK_TRAINING_DOCUMENT_ADDED","MARK_TRAINING_DOCUMENT",saved.getId(),saved.getFilename());
  return new DocumentRow(saved.getId(),saved.getFilename(),0,true);
 }

 private MarkTrainingDocument one(UUID id){return training.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));}

 @DeleteMapping("/documents/{id}") @Transactional @ResponseStatus(HttpStatus.NO_CONTENT)
 public void delete(@PathVariable UUID id,Authentication auth){
  var doc=one(id);
  training.delete(doc);
  audit.recordInTransaction(auth,"MARK_TRAINING_DOCUMENT_REMOVED","MARK_TRAINING_DOCUMENT",id,doc.getFilename());
 }

 @GetMapping(value="/documents/{id}/pages/{page}/preview",produces="image/png") @Transactional(readOnly=true)
 public ResponseEntity<byte[]> pagePreview(@PathVariable UUID id,@PathVariable int page)throws Exception{
  var doc=one(id);
  return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore())
   .header("X-Page-Count",Integer.toString(doc.getPageCount())).header("X-Content-Type-Options","nosniff").body(preview.render(doc.getContent(),page,true));
 }

 @GetMapping("/documents/{id}/marks") @Transactional(readOnly=true)
 public List<DocumentMarkController.MarkView> list(@PathVariable UUID id){one(id);return marks.forTrainingDocument(id).stream().map(DocumentMarkController::view).toList();}

 /** No automatic suggestions for training documents. */
 @GetMapping("/documents/{id}/mark-candidates") @Transactional(readOnly=true)
 public List<DocumentMarkController.Candidate> candidates(@PathVariable UUID id){one(id);return List.of();}

 @PostMapping("/documents/{id}/marks") @Transactional @ResponseStatus(HttpStatus.CREATED)
 public DocumentMarkController.MarkView create(@PathVariable UUID id,@RequestBody DocumentMarkController.MarkInput input,Authentication auth){
  var doc=one(id);var kind=DocumentMarkController.validate(input);
  if(input.page()>doc.getPageCount())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ungültige Markierung.");
  if(marks.forTrainingDocument(id).size()>=DocumentMarkController.MAX_MARKS)throw new ResponseStatusException(HttpStatus.CONFLICT,"Zu viele Markierungen an diesem Dokument.");
  var saved=marks.save(DocumentMark.forTraining(id,input.page(),input.x(),input.y(),Math.min(input.w(),1-input.x()),Math.min(input.h(),1-input.y()),kind,auth==null?"unknown":auth.getName()));
  audit.recordInTransaction(auth,"MARK_TRAINING_MARK_SET","MARK_TRAINING_DOCUMENT",id,"Seite "+input.page()+" · "+kind);
  return DocumentMarkController.view(saved);
 }

 @DeleteMapping("/documents/{id}/marks/{markId}") @Transactional @ResponseStatus(HttpStatus.NO_CONTENT)
 public void deleteMark(@PathVariable UUID id,@PathVariable UUID markId,Authentication auth){
  one(id);var mark=marks.findById(markId).filter(m->id.equals(m.getTrainingDocumentId())).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
  marks.delete(mark);
  audit.recordInTransaction(auth,"MARK_TRAINING_MARK_REMOVED","MARK_TRAINING_DOCUMENT",id,"Seite "+mark.getPage()+" · "+mark.getKind());
 }

 static final int MAX_EXPORT_PAGES=400;
 /** All marked pages of the tenant as images with their boxes (fractions of the page), to train a detector offline. */
 @GetMapping("/export.zip") @Transactional(readOnly=true)
 public ResponseEntity<byte[]> export(Authentication auth)throws Exception{
  var all=marks.findAll();
  Map<String,List<DocumentMark>> byPage=new TreeMap<>();
  for(var m:all)byPage.computeIfAbsent((m.getTrainingDocumentId()!=null?"t-"+m.getTrainingDocumentId():"d-"+m.getDocumentId())+"|"+String.format("%04d",m.getPage()),k->new ArrayList<>()).add(m);
  var out=new java.io.ByteArrayOutputStream();var labels=new StringBuilder("[");int n=0;
  try(var zip=new java.util.zip.ZipOutputStream(out)){
   for(var entry:byPage.entrySet()){
    if(n>=MAX_EXPORT_PAGES)break;
    var first=entry.getValue().get(0);int page=first.getPage();
    byte[] content=first.getTrainingDocumentId()!=null?training.findById(first.getTrainingDocumentId()).map(MarkTrainingDocument::getContent).orElse(null):documents.findById(first.getDocumentId()).map(d->d.getContent()).orElse(null);
    if(content==null)continue;
    byte[] png;try{png=preview.render(content,page,true);}catch(Exception unreadable){continue;}
    String name=String.format("images/%04d.png",++n);
    zip.putNextEntry(new java.util.zip.ZipEntry(name));zip.write(png);zip.closeEntry();
    if(n>1)labels.append(',');
    labels.append("\n {\"image\":\"").append(name).append("\",\"boxes\":[");
    boolean firstBox=true;
    for(var m:entry.getValue()){if(!firstBox)labels.append(',');firstBox=false;labels.append(String.format(java.util.Locale.ROOT,"{\"x\":%.5f,\"y\":%.5f,\"w\":%.5f,\"h\":%.5f,\"kind\":\"%s\"}",m.getX(),m.getY(),m.getW(),m.getH(),m.getKind().name()));}
    labels.append("]}");
   }
   labels.append("\n]\n");
   zip.putNextEntry(new java.util.zip.ZipEntry("labels.json"));zip.write(labels.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();
  }
  audit.recordInTransaction(auth,"MARK_TRAINING_EXPORT","MARK_TRAINING_DOCUMENT",null,n+" Seiten");
  return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip")).header("Content-Disposition","attachment; filename=\"stempel-trainingsdaten.zip\"").header("X-Content-Type-Options","nosniff").body(out.toByteArray());
 }
}
