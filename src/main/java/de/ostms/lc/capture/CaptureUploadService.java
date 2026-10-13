package de.ostms.lc.capture;

import de.ostms.lc.document.service.DocumentInboxService;
import de.ostms.lc.document.service.PdfPagePreviewService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Delivery from a scan station: one file per call item, with batch, scanner, declared page count and checksum. A file that arrived before (same checksum)
 * is not stored a second time - the receipt names the earlier inbox entry, so a scanner may simply retry. The declared page count is compared with the
 * real one, so a lost or stuck page is noticed at the door.
 */
@Service
public class CaptureUploadService {
 public record Meta(String batchId,String scanner,Integer declaredPages,String scannedAt,String sha256){}
 public record Receipt(String filename,String status,UUID inboxItemId,String sha256,Integer declaredPages,Integer actualPages,boolean pageCountMatches,String note){}
 public record Info(String source,String batchId,String scanner,Integer declaredPages,Integer actualPages,boolean pageCountMatches,LocalDateTime scannedAt){}

 private final DocumentInboxService inbox;private final InboxCaptureInfoRepository infos;private final PdfPagePreviewService pdf;
 public CaptureUploadService(DocumentInboxService inbox,InboxCaptureInfoRepository infos,PdfPagePreviewService pdf){this.inbox=inbox;this.infos=infos;this.pdf=pdf;}

 static String sha256(byte[] bytes){
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
 }
 static String clean(String value,int max,String label){
  if(value==null||value.isBlank())return null;
  String v=value.strip();if(v.length()>max)throw new IllegalArgumentException(label+" ist zu lang (höchstens "+max+" Zeichen).");return v;
 }
 static LocalDateTime time(String value){
  if(value==null||value.isBlank())return null;
  try{return OffsetDateTime.parse(value.strip()).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();}
  catch(RuntimeException notOffset){try{return LocalDateTime.parse(value.strip());}catch(RuntimeException invalid){throw new IllegalArgumentException("Der Scanzeitpunkt ist kein gültiges Datum (z. B. 2026-10-13T08:15:00+02:00).");}}
 }

 @Transactional
 public List<Receipt> receive(List<MultipartFile> files,Meta meta,String source) throws IOException {
  if(files==null||files.isEmpty())throw new IllegalArgumentException("Es wurde keine Datei gesendet.");
  boolean single=files.size()==1;
  if(!single&&(meta.sha256()!=null||meta.declaredPages()!=null))throw new IllegalArgumentException("Prüfwert und Seitenzahl gelten für genau eine Datei; bitte die Dateien einzeln senden.");
  String batch=clean(meta.batchId(),100,"Die Stapel-Kennung"),scanner=clean(meta.scanner(),100,"Die Scanner-Kennung");
  Integer declared=meta.declaredPages();
  if(declared!=null&&(declared<1||declared>10000))throw new IllegalArgumentException("Die Seitenzahl muss zwischen 1 und 10000 liegen.");
  LocalDateTime scannedAt=time(meta.scannedAt());
  List<Receipt> receipts=new ArrayList<>();
  for(MultipartFile file:files){
   String name=Optional.ofNullable(file.getOriginalFilename()).orElse("scan");
   if(name.toLowerCase(Locale.ROOT).endsWith(".zip"))throw new IllegalArgumentException("ZIP-Archive bitte nicht über die Erfassung senden; jede Datei einzeln.");
   byte[] bytes=file.getBytes();String sha=sha256(bytes);
   if(meta.sha256()!=null&&!meta.sha256().strip().equalsIgnoreCase(sha))throw new IllegalArgumentException("Der Prüfwert (SHA-256) stimmt nicht mit der Datei überein; bitte erneut senden.");
   var earlier=infos.bySha(sha).orElse(null);
   if(earlier!=null){
    receipts.add(new Receipt(name,"DUPLICATE",earlier.getInboxItemId(),sha,earlier.getDeclaredPages(),earlier.getActualPages(),earlier.pageCountMatches(),"Diese Datei ist schon eingegangen; es wurde nichts neu angelegt."));
    continue;
   }
   var created=inbox.receive(List.of(file),source);
   if(created.size()!=1)throw new IllegalArgumentException("Aus dieser Datei entstand nicht genau ein Eintrag; bitte als einzelne Datei senden.");
   Integer actual=null;
   if(name.toLowerCase(Locale.ROOT).endsWith(".pdf")){try{actual=pdf.pageCount(bytes);}catch(Exception unreadable){actual=null;}}
   var info=infos.save(new InboxCaptureInfo(created.get(0).id(),source,batch,scanner,single?declared:null,actual,scannedAt,sha));
   String note=info.pageCountMatches()?null:"Gemeldet "+info.getDeclaredPages()+" Seiten, erkannt "+info.getActualPages()+". Bitte den Stapel prüfen.";
   receipts.add(new Receipt(name,"CREATED",created.get(0).id(),sha,info.getDeclaredPages(),info.getActualPages(),info.pageCountMatches(),note));
  }
  return receipts;
 }

 @Transactional(readOnly=true)
 public Map<UUID,Info> overview(){
  Map<UUID,Info> out=new LinkedHashMap<>();
  infos.findAll().forEach(i->out.put(i.getInboxItemId(),new Info(i.getSource(),i.getBatchId(),i.getScanner(),i.getDeclaredPages(),i.getActualPages(),i.pageCountMatches(),i.getScannedAt())));
  return out;
 }
}
