package de.ostms.lc.capture;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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
 public record Info(String source,String batchId,String scanner,Integer declaredPages,Integer actualPages,boolean pageCountMatches,LocalDateTime scannedAt,String channel,String reference,String sender,String subject){}

 private final de.ostms.lc.intake.InboxIntakeService intake;private final InboxCaptureInfoRepository infos;
 public CaptureUploadService(de.ostms.lc.intake.InboxIntakeService intake,InboxCaptureInfoRepository infos){this.intake=intake;this.infos=infos;}

 static String sha256(byte[] bytes){return de.ostms.lc.intake.InboxIntakeService.sha256(bytes);}
 static String clean(String value,int max,String label){
  if(value==null||value.isBlank())return null;
  String v=value.strip();if(v.length()>max)throw new IllegalArgumentException(label+" ist zu lang (höchstens "+max+" Zeichen).");return v;
 }
 static LocalDateTime time(String value){
  if(value==null||value.isBlank())return null;
  try{return OffsetDateTime.parse(value.strip()).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();}
  catch(RuntimeException notOffset){try{return LocalDateTime.parse(value.strip());}catch(RuntimeException invalid){throw new IllegalArgumentException("Der Scanzeitpunkt ist kein gültiges Datum (z. B. 2026-10-13T08:15:00+02:00).");}}
 }

 /** Delivery of a scan station; storing, duplicates and page counts are done by the common intake. */
 @Transactional
 public List<Receipt> receive(List<MultipartFile> files,Meta meta,String source) throws IOException {
  if(files==null||files.isEmpty())throw new IllegalArgumentException("Es wurde keine Datei gesendet.");
  boolean single=files.size()==1;
  if(!single&&(meta.sha256()!=null||meta.declaredPages()!=null))throw new IllegalArgumentException("Prüfwert und Seitenzahl gelten für genau eine Datei; bitte die Dateien einzeln senden.");
  String batch=clean(meta.batchId(),100,"Die Stapel-Kennung"),scanner=clean(meta.scanner(),100,"Die Scanner-Kennung");
  Integer declared=meta.declaredPages();
  if(declared!=null&&(declared<1||declared>10000))throw new IllegalArgumentException("Die Seitenzahl muss zwischen 1 und 10000 liegen.");
  LocalDateTime scannedAt=time(meta.scannedAt());
  List<de.ostms.lc.intake.IntakeItem> items=new ArrayList<>();
  for(MultipartFile file:files){
   String name=Optional.ofNullable(file.getOriginalFilename()).orElse("scan");
   if(name.toLowerCase(Locale.ROOT).endsWith(".zip"))throw new IllegalArgumentException("ZIP-Archive bitte nicht über die Erfassung senden; jede Datei einzeln.");
   var origin=new de.ostms.lc.intake.IntakeOrigin(de.ostms.lc.intake.IntakeOrigin.SCAN_STATION,null,batch,scanner,null,null,single?declared:null,scannedAt,meta.sha256(),source);
   items.add(new de.ostms.lc.intake.IntakeItem(name,file.getBytes(),file.getContentType(),origin));
  }
  return intake.accept(items).stream().map(r->new Receipt(r.filename(),r.status(),r.inboxItemId(),r.sha256(),r.declaredPages(),r.actualPages(),r.pageCountMatches(),r.note())).toList();
 }

 @Transactional(readOnly=true)
 public Map<UUID,Info> overview(){
  Map<UUID,Info> out=new LinkedHashMap<>();
  infos.findAll().forEach(i->out.put(i.getInboxItemId(),new Info(i.getSource(),i.getBatchId(),i.getScanner(),i.getDeclaredPages(),i.getActualPages(),i.pageCountMatches(),i.getScannedAt(),i.getChannel(),i.getOriginReference(),i.getSender(),i.getSubject())));
  return out;
 }
}
