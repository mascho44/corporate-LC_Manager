package de.ostms.lc.swiftfolder;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Splits the content of a delivered file into single SWIFT messages. Understands plain text with :20:, FIN with blocks ({1:…}{2:…}{4:…-}) and RJE ($ between messages, control characters). */
public final class SwiftFileSplitter {
 private SwiftFileSplitter(){}
 private static final Pattern BLOCK_ONE=Pattern.compile("(?=\\{1:)");
 /** Control characters of RJE and FIN transport (SOH, STX, ETX) are removed; line ends become \n. */
 static String normalise(String text){return text.replace("\r\n","\n").replace('\r','\n').replaceAll("[\\u0001\\u0002\\u0003]","").strip();}

 public static List<String> split(String raw){
  String text=normalise(raw);
  var out=new ArrayList<String>();
  if(text.isEmpty())return out;
  List<String> parts;
  if(text.contains("{1:"))parts=List.of(BLOCK_ONE.split(text));
  else if(Pattern.compile("(?m)^\\$\\s*$").matcher(text).find())parts=List.of(text.split("(?m)^\\$\\s*$"));
  else parts=List.of(text);
  for(String part:parts){String t=part.strip();if(!t.isEmpty())out.add(t);}
  return out;
 }
}
