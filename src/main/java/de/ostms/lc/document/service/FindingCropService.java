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
