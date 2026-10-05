package de.corporate.lc.document.service;

import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.util.*;
import java.util.zip.ZipInputStream;

/** Validates the whole batch before any extraction or database write. */
final class InboxUploadReader {
    private static final int FILE_LIMIT=10*1024*1024;
    private static final long BATCH_LIMIT=50L*1024*1024;
    record Upload(String filename,byte[] content) { }

    static List<Upload> read(List<MultipartFile> files) throws IOException {
        if(files==null||files.isEmpty())throw new IllegalArgumentException("Bitte mindestens eine Datei auswählen.");
        if(files.size()>100)throw new IllegalArgumentException("Maximal 100 Quelldateien auswählen.");
        long uploaded=0,expanded=0;List<Upload> result=new ArrayList<>();
        for(MultipartFile file:files){
            if(file==null||file.isEmpty())throw new IllegalArgumentException("Leere Dateien können nicht aufgenommen werden.");
            String name=file.getOriginalFilename()==null||file.getOriginalFilename().isBlank()?"Dokument":file.getOriginalFilename().trim();
            validatePath(name);
            if(name.contains("/")||name.contains("\\"))throw new IllegalArgumentException("Ungültiger Dateiname.");
            boolean archive=name.toLowerCase(Locale.ROOT).endsWith(".zip");
            if(file.getSize()>(archive?BATCH_LIMIT:FILE_LIMIT))throw new IllegalArgumentException(archive?"ZIP überschreitet 50 MB.":"Datei überschreitet 10 MB.");
            uploaded+=file.getSize();if(uploaded>BATCH_LIMIT)throw new IllegalArgumentException("Der Upload überschreitet insgesamt 50 MB.");
            if(!archive){byte[] content=file.getBytes();expanded+=content.length;checkTotal(expanded);add(result,new Upload(name,content));continue;}
            int entries=0,imported=0;
            try(var zip=new ZipInputStream(file.getInputStream())){
                for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()){
                    if(++entries>1000)throw new IllegalArgumentException("ZIP enthält zu viele Einträge.");
                    String path=entry.getName().replace('\\','/');validatePath(path);
                    byte[] content=zip.readNBytes((int)Math.min(FILE_LIMIT,BATCH_LIMIT-expanded)+1);
                    if(content.length>FILE_LIMIT)throw new IllegalArgumentException("Datei im ZIP überschreitet 10 MB: "+path);
                    expanded+=content.length;checkTotal(expanded);
                    if(entry.isDirectory()||path.startsWith("__MACOSX/")||path.endsWith(".DS_Store"))continue;
                    if(path.toLowerCase(Locale.ROOT).endsWith(".zip"))throw new IllegalArgumentException("ZIP-Dateien innerhalb eines ZIP werden nicht unterstützt.");
                    if(content.length==0)throw new IllegalArgumentException("Leere Datei im ZIP: "+path);
                    add(result,new Upload(path,content));imported++;
                }
            }catch(IOException ex){throw new IllegalArgumentException("ZIP ist beschädigt, verschlüsselt oder ungültig: "+name,ex);}
            if(imported==0)throw new IllegalArgumentException("ZIP enthält keine importierbaren Dateien: "+name);
        }
        return result;
    }

    private static void add(List<Upload> files,Upload upload){if(files.size()>=100)throw new IllegalArgumentException("Ein Import darf höchstens 100 entpackte Dateien enthalten.");files.add(upload);}
    private static void checkTotal(long size){if(size>BATCH_LIMIT)throw new IllegalArgumentException("Die entpackten Dateien überschreiten insgesamt 50 MB.");}
    private static void validatePath(String name){
        String normalized=name.replace('\\','/');
        if(normalized.isBlank()||normalized.length()>255||normalized.startsWith("/")||normalized.contains(":")||normalized.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Ungültiger Dateipfad im Upload.");
        for(String part:normalized.split("/"))if(part.equals("..")||part.equals("."))throw new IllegalArgumentException("Unsicherer Dateipfad im ZIP.");
    }
}
