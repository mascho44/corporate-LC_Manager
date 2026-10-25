package de.ostms.lc.document.service;
import de.ostms.lc.document.domain.LcDocument;
import org.springframework.stereotype.Service;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Finds the region of a document that a finding refers to (signature caption/ink or the recognised date). */
@Service
public class FindingCropService {
 private static final int RASTER_DPI=200;
 private final SignatureEvidenceService signatures;private final PdfPagePreviewService preview;
 public FindingCropService(SignatureEvidenceService s,PdfPagePreviewService p){signatures=s;preview=p;}

 public Optional<SpatialMetadata.Field> region(LcDocument doc,String kind){
  return switch(kind==null?"":kind){
   case "signature"->signature(doc);
   case "date"->date(doc);
   case "stamp"->stamp(doc);
   default->Optional.empty();
  };
 }
 private Optional<SpatialMetadata.Field> signature(LcDocument doc){
  var evidence=signatures.evidence(doc);
  if(!evidence.available())return Optional.empty();
  for(var page:evidence.pages())for(var anchor:page.anchors())if(anchor.inkFound()&&anchor.ink()!=null)return Optional.of(field(page.page(),union(anchor.box(),anchor.ink()),"Unterschriftszeile mit erkannter Tinte"));
  for(var page:evidence.pages())if(!page.anchors().isEmpty())return Optional.of(field(page.page(),page.anchors().get(0).box(),"Unterschriftszeile ohne erkannte Tinte"));
  return Optional.empty();
 }
 private Optional<SpatialMetadata.Field> date(LcDocument doc){
  if(doc.getDocumentDate()==null)return Optional.empty();
  var ocr=DocumentExtractionService.readEvidence(doc.getOcrEvidenceJson());
  if(ocr==null||ocr.words().isEmpty())return Optional.empty();
  var forms=new HashSet<String>();
  for(String pattern:List.of("uuuu-MM-dd","dd.MM.uuuu","dd/MM/uuuu","d/M/uuuu","d.M.uuuu","dd-MM-uuuu"))forms.add(doc.getDocumentDate().format(DateTimeFormatter.ofPattern(pattern)));
  for(var word:ocr.words()){
   String text=word.text()==null?"":word.text().replaceAll("[^0-9./\\-]","");
   if(forms.contains(text))return Optional.of(field(word.page(),new SignatureDetector.Box(word.left(),word.top(),word.width(),word.height()),"Erkanntes Dokumentdatum"));
  }
  return Optional.empty();
 }
 /** The ORIGINAL/COPY stamp: a confidently read kind word, with a neighbouring number on the same line. Prefers all-caps words near the page head. */
 private Optional<SpatialMetadata.Field> stamp(LcDocument doc){
  var ocr=DocumentExtractionService.readEvidence(doc.getOcrEvidenceJson());
  if(ocr==null||ocr.words().isEmpty())return Optional.empty();
  var words=ocr.words();OcrEvidence.Word best=null;int bestRank=Integer.MAX_VALUE;
  for(int i=0;i<words.size();i++){
   var w=words.get(i);
   if(w.confidence()!=null&&w.confidence()<.5)continue;
   if(!DocumentCopyDetector.isKindWord(w.text()))continue;
   boolean caps=w.text().equals(w.text().toUpperCase(Locale.ROOT));
   int rank=(caps?0:100000)+w.page()*2000+w.top()/4;
   if(rank<bestRank){best=w;bestRank=rank;}
  }
  if(best==null)return Optional.empty();
  var box=new SignatureDetector.Box(best.left(),best.top(),best.width(),best.height());
  for(var w:words){
   if(w==best||w.page()!=best.page()||Math.abs(w.top()-best.top())>best.height()/2)continue;
   String t=w.text()==null?"":w.text().trim();
   int gap=w.left()>=best.left()+best.width()?w.left()-(best.left()+best.width()):best.left()-(w.left()+w.width());
   if(gap<=2*best.height()&&gap>=0&&t.matches("(?i)[1-3]|of|von|/|no\\.?"))box=union(box,new SignatureDetector.Box(w.left(),w.top(),w.width(),w.height()));
  }
  return Optional.of(field(best.page(),box,"Erkannte Original-/Copy-Kennzeichnung"));
 }
 private static SignatureDetector.Box union(SignatureDetector.Box a,SignatureDetector.Box b){
  int l=Math.min(a.left(),b.left()),t=Math.min(a.top(),b.top()),r=Math.max(a.left()+a.width(),b.left()+b.width()),bt=Math.max(a.top()+a.height(),b.top()+b.height());
  return new SignatureDetector.Box(l,t,r-l,bt-t);
 }
 private static SpatialMetadata.Field field(int page,SignatureDetector.Box box,String note){
  return new SpatialMetadata.Field(note,page,box.left(),box.top(),Math.max(1,Math.min(box.width(),1700)),Math.max(1,Math.min(box.height(),1100)),note,"DETECTED");
 }
 public byte[] crop(LcDocument doc,String kind)throws Exception{
  if(!"application/pdf".equalsIgnoreCase(doc.getContentType())||doc.getContent()==null)throw new NoSuchElementException();
  var field=region(doc,kind).orElseThrow(NoSuchElementException::new);
  return preview.markedCrop(doc.getContent(),field);
 }
}
