package de.ostms.lc.intake;

import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.file.*;

/** A file that is already in memory (from a mailbox, a folder), shaped like an upload so the inbox has one way in. */
final class BytesMultipartFile implements MultipartFile {
 private final String name,contentType;private final byte[] bytes;
 BytesMultipartFile(String name,String contentType,byte[] bytes){this.name=name;this.contentType=contentType;this.bytes=bytes;}
 @Override public String getName(){return "file";}
 @Override public String getOriginalFilename(){return name;}
 @Override public String getContentType(){return contentType;}
 @Override public boolean isEmpty(){return bytes.length==0;}
 @Override public long getSize(){return bytes.length;}
 @Override public byte[] getBytes(){return bytes.clone();}
 @Override public InputStream getInputStream(){return new ByteArrayInputStream(bytes);}
 @Override public void transferTo(File dest)throws IOException{Files.write(dest.toPath(),bytes);}
}
