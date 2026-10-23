package de.ostms.lc.swiftfolder;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.ebics.EbicsMessage;
import de.ostms.lc.ebics.EbicsMessageRepository;
import de.ostms.lc.ebics.EbicsMessageService;
import de.ostms.lc.imports.service.SwiftImportService;
import de.ostms.lc.tenant.domain.TenantContext;
import de.ostms.lc.tenant.repository.TenantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Receive-only inbox for SWIFT messages that the bank's messaging system drops into a folder. Per bank tenant the folders are
 * <code>root/tenant/eingang</code> (the messaging system writes here), <code>verarbeitet</code> and <code>fehler</code>. Each message of a delivered file is stored once
 * (by content) like a message fetched from EBICS and waits for a person to look at it, import it or discard it; nothing is imported automatically and nothing is
 * ever written back to the messaging system. Symbolic links are ignored; a delivered file is moved, never deleted.
 */
@Component
public class SwiftFolderInbox {
 static final int MAX_FILES_PER_RUN=50;
 static final long MAX_BYTES=512L*1024;
 private static final DateTimeFormatter STAMP=DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
 public record Status(boolean enabled,String path,LocalDateTime lastRunAt,String lastResult){}
 public record Folders(Path in,Path done,Path failed){}
 private final boolean enabled;private final Path root;private final long settleSeconds;
 private final EbicsMessageRepository messages;private final SwiftImportService swift;private final AuditService audit;private final TenantRepository tenants;
 @Autowired(required=false) private de.ostms.lc.lc.service.AutoTaskService autoTasks;
 private final Map<UUID,Status> last=new ConcurrentHashMap<>();

 public SwiftFolderInbox(@Value("${app.swift-folder.enabled:false}") boolean enabled,@Value("${app.swift-folder.root:/data/swift}") String root,@Value("${app.swift-folder.settle-seconds:10}") long settleSeconds,
   EbicsMessageRepository messages,SwiftImportService swift,AuditService audit,TenantRepository tenants){
  this.enabled=enabled;this.root=Path.of(root).toAbsolutePath().normalize();this.settleSeconds=settleSeconds;this.messages=messages;this.swift=swift;this.audit=audit;this.tenants=tenants;
 }
 public boolean enabled(){return enabled;}

 /** The folders of a tenant; created when missing. */
 public Folders folders(UUID tenant)throws IOException{
  Path base=root.resolve(tenant.toString()).normalize();
  if(!base.startsWith(root))throw new IllegalArgumentException("Ungültiger Ordner.");
  var f=new Folders(base.resolve("eingang"),base.resolve("verarbeitet"),base.resolve("fehler"));
  Files.createDirectories(f.in());Files.createDirectories(f.done());Files.createDirectories(f.failed());return f;
 }
 public Status status(){
  UUID tenant=TenantContext.currentId();
  Status s=last.get(tenant);
  String path=root.resolve(tenant.toString()).resolve("eingang").toString();
  return s!=null?new Status(enabled,path,s.lastRunAt(),s.lastResult()):new Status(enabled,path,null,null);
 }

 /** One run for the current tenant (bank profile only). */
 public void pollCurrentTenant(){
  if(!enabled)return;
  UUID tenant=TenantContext.currentId();
  var t=tenants.findById(tenant).orElse(null);
  if(t==null||!t.isActive()||!t.isBankEnabled())return;
  try{
   var f=folders(tenant);int fresh=0,known=0,failed=0;
   List<Path> files;
   try(var stream=Files.list(f.in())){files=stream.filter(this::deliverable).sorted().limit(MAX_FILES_PER_RUN).toList();}
   for(Path file:files){
    int[] r=deliver(file,f);fresh+=r[0];known+=r[1];failed+=r[2];
   }
   if(!files.isEmpty())last.put(tenant,new Status(true,"",LocalDateTime.now(),fresh+" neu, "+known+" bereits bekannt, "+failed+" fehlgeschlagen"));
  }catch(IOException|RuntimeException e){last.put(tenant,new Status(true,"",LocalDateTime.now(),"Fehler beim Lesen des Ordners"));}
 }

 private boolean deliverable(Path p){
  try{
   String name=p.getFileName().toString();
   if(name.startsWith(".")||name.startsWith("~")||name.endsWith(".tmp")||name.endsWith(".part")||name.endsWith(".crdownload")||name.endsWith("~"))return false;
   var attrs=Files.readAttributes(p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
   if(!attrs.isRegularFile())return false;
   return Instant.now().minusSeconds(settleSeconds).isAfter(attrs.lastModifiedTime().toInstant());
  }catch(IOException e){return false;}
 }

 /** Returns {new, known, failed} messages of the file and moves the file. */
 int[] deliver(Path file,Folders f){
  String name=file.getFileName().toString();
  try{
   long size=Files.size(file);
   if(size==0)throw new IllegalArgumentException("Die Datei ist leer.");
   if(size>MAX_BYTES)throw new IllegalArgumentException("Die Datei ist größer als 512 KB.");
   String text;
   try{text=EbicsMessageService.decode(Files.readAllBytes(file));}catch(java.nio.charset.CharacterCodingException notText){throw new IllegalArgumentException("Die Datei ist kein gültiger Text (UTF-8).");}
   var parts=SwiftFileSplitter.split(text);
   if(parts.isEmpty())throw new IllegalArgumentException("Die Datei enthält keine Nachricht.");
   int fresh=0,known=0;var problems=new ArrayList<String>();
   for(String part:parts){
    if(!part.contains(":20:")&&!part.contains(":21:")){problems.add("Eine Nachricht ohne SWIFT-Felder (:20:) wurde übersprungen.");continue;}
    String type=swift.detect(part);
    if(!EbicsMessageService.SUPPORTED.contains(type)){problems.add("Der Nachrichtentyp "+type+" wird nicht unterstützt.");continue;}
    String sha=EbicsMessageService.sha256(part);
    if(messages.findBySha(sha).isPresent()){known++;continue;}
    var saved=messages.save(new EbicsMessage(type,sha,part,"FOLDER",name));
    fresh++;
    if(autoTasks!=null)try{autoTasks.onEbicsMessage(saved.getId(),type,EbicsMessageService.reference(part));}catch(RuntimeException ignored){}
    audit.record("system:swift-folder","SWIFT_FOLDER_RECEIVED","EBICS_MESSAGE",saved.getId(),type+" · "+EbicsMessageService.reference(part)+" · "+name,true,null);
   }
   if(fresh+known==0)throw new IllegalArgumentException(String.join(" ",problems));
   move(file,f.done(),name,null);
   return new int[]{fresh,known,problems.size()};
  }catch(Exception e){
   String reason=e.getMessage()==null?"Die Datei konnte nicht gelesen werden.":e.getMessage().replaceAll("[\\r\\n]+"," ");
   audit.record("system:swift-folder","SWIFT_FOLDER_REFUSED","EBICS_MESSAGE",null,name+" · "+(reason.length()>300?reason.substring(0,300):reason),false,null);
   try{move(file,f.failed(),name,reason);}catch(IOException ignored){}
   return new int[]{0,0,1};
  }
 }
 private static void move(Path file,Path target,String name,String reason)throws IOException{
  String safe=name.replaceAll("[^A-Za-z0-9._-]","_");if(safe.length()>100)safe=safe.substring(safe.length()-100);
  String stamped=LocalDateTime.now().format(STAMP)+"-"+safe;
  Path destination=target.resolve(stamped);
  Files.move(file,destination,StandardCopyOption.ATOMIC_MOVE);
  if(reason!=null)Files.writeString(target.resolve(stamped+".fehler.txt"),reason+"\n");
 }
}
