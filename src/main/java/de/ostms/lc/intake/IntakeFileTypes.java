package de.ostms.lc.intake;

import java.util.Locale;
import java.util.Map;

/** File types and the size limit the document inbox takes from the intake channels. */
final class IntakeFileTypes {
 private IntakeFileTypes(){}
 static final long MAX_BYTES=10L*1024*1024;
 private static final Map<String,String> TYPES=Map.of("pdf","application/pdf","png","image/png","jpg","image/jpeg","jpeg","image/jpeg","tif","image/tiff","tiff","image/tiff");
 /** Content type for a file name, or null when the type is not taken. */
 static String contentType(String filename){
  if(filename==null||!filename.contains("."))return null;
  return TYPES.get(filename.substring(filename.lastIndexOf('.')+1).toLowerCase(Locale.ROOT));
 }
}
