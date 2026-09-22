package de.corporate.lc.swift;
import org.springframework.stereotype.Component; import java.util.*; import java.util.regex.*;
@Component public class PrintedSwiftNormalizer {private static final Pattern FIELD=Pattern.compile("^.*?:(\\d{2}[A-Z]?):\\s*(.*)$");
 public String normalize(String text){if(text==null)return "";List<String> out=new ArrayList<>();String code=null;StringBuilder value=new StringBuilder();for(String line:text.replace('\f','\n').split("\\R")){Matcher m=FIELD.matcher(line);if(m.matches()){if(code!=null)add(out,code,value);code=m.group(1);value=new StringBuilder(m.group(2).trim());}else if(code!=null&&!line.isBlank()&&!line.matches("(?i)^\\s*(Seite|QUOTE:|UNQUOTE|Trailer|Checksum).*")){String continuation=line.strip();if(!continuation.isBlank())value.append('\n').append(continuation);}}if(code!=null)add(out,code,value);return String.join("\n",out);}
 private void add(List<String> out,String code,StringBuilder value){out.add(":"+code+":"+value.toString().trim());}}
