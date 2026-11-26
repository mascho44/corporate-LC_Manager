package de.ostms.lc.document.api;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.document.domain.DocumentMark;
import de.ostms.lc.document.repository.DocumentMarkRepository;
import de.ostms.lc.document.repository.LcDocumentRepository;
import de.ostms.lc.document.service.SignatureDetector;
import de.ostms.lc.document.service.SignatureEvidenceService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

/** Stamp and signature decisions per document page. A person decides; the decisions are kept per tenant. */
@RestController @RequestMapping("/api/documents/{id}")
public class DocumentMarkController {
 static final int MAX_MARKS=300,MAX_PAGE=500,MAX_CANDIDATES=200;
 private final LcDocumentRepository documents;private final DocumentMarkRepository marks;private final SignatureEvidenceService signatures;private final AuditService audit;
 public DocumentMarkController(LcDocumentRepository documents,DocumentMarkRepository marks,SignatureEvidenceService signatures,AuditService audit){this.documents=documents;this.marks=marks;this.signatures=signatures;this.audit=audit;}

 public record MarkView(UUID id,int page,double x,double y,double w,double h,String kind,String markedBy){}
 public record MarkInput(int page,double x,double y,double w,double h,String kind){}
 public record Candidate(int page,double x,double y,double w,double h){}

 static MarkView view(DocumentMark m){return new MarkView(m.getId(),m.getPage(),m.getX(),m.getY(),m.getW(),m.getH(),m.getKind().name(),m.getMarkedBy());}
 private de.ostms.lc.document.domain.LcDocument document(UUID id){return documents.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));}

 /** Checks a mark before it is stored; returns the kind. */
 static DocumentMark.Kind validate(MarkInput in){
  if(in==null||in.kind()==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ungültige Markierung.");
  boolean sane=Double.isFinite(in.x())&&Double.isFinite(in.y())&&Double.isFinite(in.w())&&Double.isFinite(in.h());
  if(!sane||in.page()<1||in.page()>MAX_PAGE||in.x()<0||in.y()<0||in.x()>=1||in.y()>=1||in.w()<0.005||in.h()<0.005||in.w()>1||in.h()>1)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ungültige Markierung.");
  try{return DocumentMark.Kind.valueOf(in.kind());}catch(IllegalArgumentException unknown){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ungültige Markierung.");}
 }

 @GetMapping("/marks") @Transactional(readOnly=true)
 public List<MarkView> list(@PathVariable UUID id){document(id);return marks.forDocument(id).stream().map(DocumentMarkController::view).toList();}

 @PostMapping("/marks") @Transactional @ResponseStatus(HttpStatus.CREATED)
 public MarkView create(@PathVariable UUID id,@RequestBody MarkInput input,Authentication auth){
  document(id);var kind=validate(input);
  if(marks.forDocument(id).size()>=MAX_MARKS)throw new ResponseStatusException(HttpStatus.CONFLICT,"Zu viele Markierungen an diesem Dokument.");
  var saved=marks.save(new DocumentMark(id,input.page(),input.x(),input.y(),Math.min(input.w(),1-input.x()),Math.min(input.h(),1-input.y()),kind,auth==null?"unknown":auth.getName()));
  audit.recordInTransaction(auth,"DOCUMENT_MARK_SET","DOCUMENT",id,"Seite "+input.page()+" · "+kind);
  return view(saved);
 }

 @DeleteMapping("/marks/{markId}") @Transactional @ResponseStatus(HttpStatus.NO_CONTENT)
 public void delete(@PathVariable UUID id,@PathVariable UUID markId,Authentication auth){
  document(id);var mark=marks.findById(markId).filter(m->m.getDocumentId().equals(id)).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
  marks.delete(mark);
  audit.recordInTransaction(auth,"DOCUMENT_MARK_REMOVED","DOCUMENT",id,"Seite "+mark.getPage()+" · "+mark.getKind());
 }

 /** Regions of ink without text that may be a stamp or a signature, for the person to decide. Positions in the 200-DPI evidence raster. */
 @GetMapping("/mark-candidates") @Transactional(readOnly=true)
 public List<Candidate> candidates(@PathVariable UUID id){
  var doc=document(id);
  var evidence=signatures.evidence(doc);
  if(!evidence.available()||doc.getContent()==null)return List.of();
  var sizes=pageSizes(doc.getContent(),evidence.pages().stream().mapToInt(p->p.page()).max().orElse(0));
  var out=new ArrayList<Candidate>();
  for(var page:evidence.pages()){
   double[] size=page.page()<=sizes.size()?sizes.get(page.page()-1):null;
   if(size==null)continue;
   for(SignatureDetector.Box box:page.clusters()){
    double x=box.left()/size[0],y=box.top()/size[1],w=box.width()/size[0],h=box.height()/size[1];
    if(x>=1||y>=1||w<=0||h<=0)continue;
    out.add(new Candidate(page.page(),x,y,Math.min(w,1-x),Math.min(h,1-y)));
   }
  }
  return out.size()>MAX_CANDIDATES?out.subList(0,MAX_CANDIDATES):out;
 }
 /** Pixel size of each page in the 200-DPI evidence raster (crop box, rotation respected), as the renderer produces it. */
 static List<double[]> pageSizes(byte[] pdfBytes,int pages){
  var sizes=new ArrayList<double[]>();
  try(var slot=de.ostms.lc.document.service.PdfProcessingSafety.acquire();var pdf=org.apache.pdfbox.Loader.loadPDF(pdfBytes)){
   int n=Math.min(pages,pdf.getNumberOfPages());
   for(int i=0;i<n;i++){
    var page=pdf.getPage(i);var box=page.getCropBox();int rotation=((page.getRotation()%360)+360)%360;
    double w=box.getWidth()*200/72.0,h=box.getHeight()*200/72.0;
    sizes.add(rotation==90||rotation==270?new double[]{h,w}:new double[]{w,h});
   }
  }catch(Exception failure){return List.of();}
  return sizes;
 }
}
