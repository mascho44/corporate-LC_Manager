package de.ostms.lc.document.service;

import de.ostms.lc.document.domain.LcDocument;
import org.apache.pdfbox.Loader;
import org.springframework.stereotype.Service;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.util.*;

/** Per-document signature hints from the stored file, cached because rendering pages is expensive. */
@Service
public class SignatureEvidenceService {
 public record Evidence(boolean available,List<SignatureDetector.PageResult> pages){
  public static Evidence unavailable(){return new Evidence(false,List.of());}
  public boolean anyInk(){return pages.stream().anyMatch(p->p.anchors().stream().anyMatch(SignatureDetector.Anchor::inkFound));}
  public boolean anyEmptyCaption(){return pages.stream().anyMatch(p->p.anchors().stream().anyMatch(a->!a.inkFound()));}
  public boolean anyCaption(){return pages.stream().anyMatch(p->!p.anchors().isEmpty());}
  public int freeInk(){return pages.stream().mapToInt(SignatureDetector.PageResult::freeInkClusters).sum();}
  public Optional<SignatureDetector.PageResult> firstWithInk(){return pages.stream().filter(p->p.anchors().stream().anyMatch(SignatureDetector.Anchor::inkFound)).findFirst();}
  public Optional<SignatureDetector.PageResult> firstWithEmptyCaption(){return pages.stream().filter(p->p.anchors().stream().anyMatch(a->!a.inkFound())).findFirst();}
 }
 private static final int MAX_PAGES=20,MAX_PIXELS=30_000_000;
 private final Map<String,Evidence> cache=Collections.synchronizedMap(new LinkedHashMap<>(16,.75f,true){
  @Override protected boolean removeEldestEntry(Map.Entry<String,Evidence> eldest){return size()>200;}
 });

 public Evidence evidence(LcDocument document){
  if(document==null||document.getContent()==null)return Evidence.unavailable();
  String key=(document.getId()==null?"x":document.getId().toString())+":"+document.getContent().length+":"+java.util.Objects.hashCode(document.getOcrEvidenceJson());
  var cached=cache.get(key);if(cached!=null)return cached;
  Evidence result;
  try{result=analyze(document);}catch(Exception|OutOfMemoryError failure){result=Evidence.unavailable();}
  if(result.available())cache.put(key,result);
  return result;
 }

 private Evidence analyze(LcDocument document)throws Exception{
  var evidence=DocumentExtractionService.readEvidence(document.getOcrEvidenceJson());
  if(evidence==null||evidence.words().isEmpty())return Evidence.unavailable();
  int dpi=evidence.dpi()>0?evidence.dpi():200;
  String type=document.getContentType()==null?"":document.getContentType().toLowerCase(Locale.ROOT);
  var results=new ArrayList<SignatureDetector.PageResult>();
  if(type.equals("application/pdf")){
   int pages;try(var slot=PdfProcessingSafety.acquire();var pdf=Loader.loadPDF(document.getContent())){PdfProcessingSafety.validate(pdf);pages=Math.min(pdf.getNumberOfPages(),MAX_PAGES);}
   // Poppler renders scanned pages (JPEG2000, JBIG2) that PDFBox shows blank, and matches the OCR raster.
   var directory=Files.createTempDirectory("lc-signature-");
   try{
    Files.write(directory.resolve("source.pdf"),document.getContent());
    BoundedProcess.run(new ProcessBuilder("pdftoppm","-gray","-png","-r",Integer.toString(dpi),"-f","1","-l",Integer.toString(pages),directory.resolve("source.pdf").toString(),directory.resolve("page").toString()),120);
    try(var files=Files.list(directory)){
     var rendered=new TreeMap<Integer,java.nio.file.Path>();
     files.filter(f->f.getFileName().toString().matches("page-[0-9]+\\.png")).forEach(f->rendered.put(Integer.parseInt(f.getFileName().toString().replaceAll("[^0-9]","")),f));
     for(var entry:rendered.entrySet()){
      var words=wordsOf(evidence,entry.getKey());if(words.isEmpty())continue;
      BufferedImage image=ImageIO.read(entry.getValue().toFile());
      if(image==null||(long)image.getWidth()*image.getHeight()>MAX_PIXELS)continue;
      results.add(SignatureDetector.analyze(image,words,entry.getKey()));
     }
    }
   }finally{
    try(var files=Files.list(directory)){files.forEach(f->f.toFile().delete());}
    Files.deleteIfExists(directory);
   }
  }else if(type.equals("image/png")||type.equals("image/jpeg")){
   BufferedImage image=ImageIO.read(new ByteArrayInputStream(document.getContent()));
   if(image==null||(long)image.getWidth()*image.getHeight()>MAX_PIXELS)return Evidence.unavailable();
   results.add(SignatureDetector.analyze(image,wordsOf(evidence,1),1));
  }else return Evidence.unavailable();
  return results.isEmpty()?Evidence.unavailable():new Evidence(true,List.copyOf(results));
 }
 private static List<OcrEvidence.Word> wordsOf(OcrEvidence evidence,int page){return evidence.words().stream().filter(w->w.page()==page).toList();}
}
