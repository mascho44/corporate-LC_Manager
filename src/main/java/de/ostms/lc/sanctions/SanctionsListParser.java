package de.ostms.lc.sanctions;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reads the public lists into a common form: one entry per listed person, entity or vessel with all its names. */
public final class SanctionsListParser {
    public enum Kind { PERSON, ENTITY, VESSEL }
    public record Entry(String source,String reference,Kind kind,String name,List<String> aliases,String programme){
        public List<String> names(){var all=new ArrayList<String>();all.add(name);all.addAll(aliases);return all;}
    }
    public record Parsed(List<Entry> entries,String published){}

    private SanctionsListParser(){}

    private static XMLStreamReader reader(InputStream in)throws Exception{
        var factory=XMLInputFactory.newFactory();
        // No external entities or DTDs: the files come from the internet.
        factory.setProperty(XMLInputFactory.SUPPORT_DTD,false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES,false);
        return factory.createXMLStreamReader(in,"UTF-8");
    }
    private static String clean(String v){return v==null?"":v.replaceAll("\\s+"," ").trim();}

    /** UN Security Council Consolidated List (XML, INDIVIDUAL and ENTITY elements). */
    public static Parsed un(InputStream in)throws Exception{
        var r=reader(in);var out=new ArrayList<Entry>();String published=null;
        String kind=null,ref=null,programme=null;List<String> parts=new ArrayList<>();List<String> aliases=new ArrayList<>();String element=null;
        while(r.hasNext()){
            int event=r.next();
            if(event==XMLStreamConstants.START_ELEMENT){
                element=r.getLocalName();
                if("CONSOLIDATED_LIST".equals(element))published=r.getAttributeValue(null,"dateGenerated");
                if("INDIVIDUAL".equals(element)||"ENTITY".equals(element)){kind=element;ref=null;programme=null;parts=new ArrayList<>();aliases=new ArrayList<>();}
            }else if(event==XMLStreamConstants.CHARACTERS&&kind!=null&&element!=null){
                String text=clean(r.getText());if(text.isEmpty())continue;
                switch(element){
                    case "REFERENCE_NUMBER"->ref=text;
                    case "UN_LIST_TYPE"->programme=text;
                    case "FIRST_NAME","SECOND_NAME","THIRD_NAME","FOURTH_NAME"->parts.add(text);
                    case "ALIAS_NAME"->aliases.add(text);
                    default->{}
                }
            }else if(event==XMLStreamConstants.END_ELEMENT){
                String end=r.getLocalName();element=null;
                if(kind!=null&&end.equals(kind)){
                    String name=String.join(" ",parts).trim();
                    if(!name.isEmpty())out.add(new Entry("UN",ref,"ENTITY".equals(kind)?Kind.ENTITY:Kind.PERSON,name,List.copyOf(aliases),programme));
                    kind=null;
                }
            }
        }
        return new Parsed(out,published);
    }

    /** EU Consolidated Financial Sanctions List (XML, sanctionEntity with nameAlias elements). */
    public static Parsed eu(InputStream in)throws Exception{
        var r=reader(in);var out=new ArrayList<Entry>();String published=null;
        boolean inEntity=false;String ref=null,programme=null;Kind kind=Kind.ENTITY;var names=new ArrayList<String>();
        while(r.hasNext()){
            int event=r.next();
            if(event==XMLStreamConstants.START_ELEMENT){
                String el=r.getLocalName();
                if("export".equals(el))published=r.getAttributeValue(null,"generationDate");
                else if("sanctionEntity".equals(el)){inEntity=true;ref=r.getAttributeValue(null,"euReferenceNumber");programme=null;kind=Kind.ENTITY;names=new ArrayList<>();}
                else if(inEntity&&"nameAlias".equals(el)){String whole=clean(r.getAttributeValue(null,"wholeName"));if(!whole.isEmpty()&&!names.contains(whole))names.add(whole);}
                else if(inEntity&&"subjectType".equals(el)){String code=r.getAttributeValue(null,"code");kind="person".equals(code)?Kind.PERSON:"vessel".equals(code)?Kind.VESSEL:Kind.ENTITY;}
                else if(inEntity&&"regulation".equals(el)&&programme==null)programme=r.getAttributeValue(null,"programme");
            }else if(event==XMLStreamConstants.END_ELEMENT&&"sanctionEntity".equals(r.getLocalName())){
                if(!names.isEmpty())out.add(new Entry("EU",ref,kind,names.get(0),List.copyOf(names.subList(1,names.size())),programme));
                inEntity=false;
            }
        }
        return new Parsed(out,published);
    }

    /** OFAC SDN list (sdn.csv) with its alias file (alt.csv). */
    public static Parsed ofac(InputStream sdn,InputStream alt)throws IOException{
        var aliases=new HashMap<String,List<String>>();
        if(alt!=null)for(var row:csv(alt))if(row.size()>3)aliases.computeIfAbsent(row.get(0),k->new ArrayList<>()).add(nullable(row.get(3)));
        var out=new ArrayList<Entry>();
        for(var row:csv(sdn)){
            if(row.size()<4)continue;
            String name=nullable(row.get(1));if(name.isEmpty())continue;
            String type=nullable(row.get(2)).toLowerCase(Locale.ROOT);
            Kind kind="individual".equals(type)?Kind.PERSON:"vessel".equals(type)?Kind.VESSEL:Kind.ENTITY;
            // SDN names of persons are "LAST, First"; the matcher compares word sets, so the order does not matter.
            var alias=aliases.getOrDefault(row.get(0),List.of()).stream().filter(a->!a.isEmpty()).toList();
            out.add(new Entry("OFAC",row.get(0),kind,name,alias,nullable(row.get(3)).replace("] [",", ").replace("[","").replace("]","").trim()));
        }
        return new Parsed(out,null);
    }
    private static String nullable(String v){v=clean(v);return "-0-".equals(v)?"":v;}

    /** Minimal CSV reader (comma separated, double quotes, no line breaks inside fields as in the OFAC files). */
    static List<List<String>> csv(InputStream in)throws IOException{
        var rows=new ArrayList<List<String>>();
        try(var reader=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){
            String line;
            while((line=reader.readLine())!=null){
                if(line.isBlank())continue;
                var row=new ArrayList<String>();var field=new StringBuilder();boolean quoted=false;
                for(int i=0;i<line.length();i++){
                    char c=line.charAt(i);
                    if(c=='"'){if(quoted&&i+1<line.length()&&line.charAt(i+1)=='"'){field.append('"');i++;}else quoted=!quoted;}
                    else if(c==','&&!quoted){row.add(field.toString());field.setLength(0);}
                    else field.append(c);
                }
                row.add(field.toString());rows.add(row);
            }
        }
        return rows;
    }
}
