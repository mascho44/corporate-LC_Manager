package de.ostms.lc.intake;

import de.ostms.lc.tenant.domain.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * "Scan to folder": the scanner (or its software) drops files into <code>eingang</code>; each run delivers the files that have settled, moves delivered
 * ones to <code>verarbeitet</code> and refused ones to <code>fehler</code> (with a note), so nothing is delivered twice by accident and nothing is lost.
 * The folder is derived from the tenant and the channel name (<code>root/tenant/name/...</code>); an administrator never enters a path, symbolic links are ignored.
 * Scanners that produce a searchable PDF (text layer) need nothing more: the extraction uses the text of such a file directly.
 */
@Component
public class FolderIntakeChannel implements IntakeChannel {
 public static final String TYPE="FOLDER";
 static final int MAX_FILES_PER_RUN=50;
 static final long MAX_BYTES=10L*1024*1024;
 private static final Map<String,String> TYPES=Map.of("pdf","application/pdf","png","image/png","jpg","image/jpeg","jpeg","image/jpeg","tif","image/tiff","tiff","image/tiff");
 private static final DateTimeFormatter STAMP=DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
 private final Path root;private final long settleSeconds;
 public FolderIntakeChannel(@Value("${app.intake.folder-root:/data/intake}") String root,@Value("${app.intake.settle-seconds:10}") long settleSeconds){this.root=Path.of(root).toAbsolutePath().normalize();this.settleSeconds=Math.max(0,settleSeconds);}

 @Override public String type(){return TYPE;}

 /** The three folders of a channel; created when missing. */
 public Folders folders(UUID tenant,String name)throws IOException{
  if(name==null||!name.matches("[a-z0-9][a-z0-9-]{0,39}"))throw new IllegalArgumentException("Der Name darf nur Kleinbuchstaben, Ziffern und Bindestriche enthalten (höchstens 40 Zeichen).");
  Path base=root.resolve(tenant.toString()).resolve(name).normalize();
  if(!base.startsWith(root))throw new IllegalArgumentException("Ungültiger Ordner.");
  var f=new Folders(base.resolve("eingang"),base.resolve("verarbeitet"),base.resolve("fehler"));
  Files.createDirectories(f.in());Files.createDirectories(f.done());Files.createDirectories(f.failed());
  return f;
 }
 /** Path of the drop folder for display; nothing is created. */
 public Path dropFolder(UUID tenant,String name){return root.resolve(tenant.toString()).resolve(name).resolve("eingang").normalize();}
 public record Folders(Path in,Path done,Path failed){}

 @Override public Result poll(IntakeChannelConfig config,IntakeSink sink)throws IOException{
  var folders=folders(TenantContext.currentId(),config.getName());
  int created=0,duplicates=0,failed=0,waiting=0;
  List<Path> files;
  try(var stream=Files.list(folders.in())){files=stream.sorted().limit(MAX_FILES_PER_RUN*4L).toList();}
  int handled=0;
  for(Path file:files){
   if(handled>=MAX_FILES_PER_RUN)break;
   String name=file.getFileName().toString();
   if(name.startsWith(".")||name.endsWith("~")||name.endsWith(".tmp")||name.endsWith(".part")||name.endsWith(".crdownload"))continue;
   BasicFileAttributes attributes;
   try{attributes=Files.readAttributes(file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);}catch(IOException vanished){continue;}
   if(!attributes.isRegularFile())continue;
   handled++;
   if(Instant.now().minusSeconds(settleSeconds).isBefore(attributes.lastModifiedTime().toInstant())){waiting++;continue;}
   String extension=name.contains(".")?name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT):"";
   String contentType=TYPES.get(extension);
   if(contentType==null){reject(file,folders,"Dateityp nicht unterstützt (erlaubt: PDF, PNG, JPG, TIF).");failed++;continue;}
   if(attributes.size()==0){reject(file,folders,"Die Datei ist leer.");failed++;continue;}
   if(attributes.size()>MAX_BYTES){reject(file,folders,"Die Datei ist größer als 10 MB.");failed++;continue;}
   try{
    byte[] bytes=Files.readAllBytes(file);
    var origin=new IntakeOrigin(IntakeOrigin.FOLDER,config.getName()+"/"+name,null,config.getName(),null,null,null,LocalDateTime.ofInstant(attributes.lastModifiedTime().toInstant(),java.time.ZoneId.systemDefault()),null,"folder:"+config.getName());
    var receipts=sink.deliver(List.of(new IntakeItem(name,bytes,contentType,origin)));
    if(receipts.stream().anyMatch(IntakeReceipt::created))created++;else duplicates++;
    moveTo(file,folders.done(),name);
   }catch(IllegalArgumentException|IOException refused){
    reject(file,folders,refused.getMessage()==null?refused.getClass().getSimpleName():refused.getMessage());failed++;
   }
  }
  return new Result(created,duplicates,failed,waiting,null);
 }

 private void reject(Path file,Folders folders,String reason)throws IOException{
  Path target=moveTo(file,folders.failed(),file.getFileName().toString());
  Files.writeString(target.resolveSibling(target.getFileName()+".fehler.txt"),reason+"\n");
 }
 private Path moveTo(Path file,Path directory,String name)throws IOException{
  Path target=directory.resolve(STAMP.format(LocalDateTime.now())+"-"+name);
  for(int n=1;Files.exists(target);n++)target=directory.resolve(STAMP.format(LocalDateTime.now())+"-"+n+"-"+name);
  return Files.move(file,target);
 }
}
