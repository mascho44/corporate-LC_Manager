package de.ostms.lc.check.service;

import de.ostms.lc.check.api.CheckResult;
import de.ostms.lc.document.domain.LcDocument;
import java.util.*;
import java.util.regex.Pattern;

import static de.ostms.lc.check.api.CheckResult.Severity.*;

/** HS codes (customs tariff numbers): recognised only next to a label, compared between the LC and the documents, and flagged for goods groups that usually need an export-control or sanctions check. */
public final class HsCodeCheck {
    private static final Pattern LABELLED=Pattern.compile("(?i)(?:H\\.?\\s*S\\.?\\s*(?:-\\s*)?(?:code|no\\.?|number|tariff)|harmoni[sz]ed\\s+(?:system\\s+)?code|tariff\\s+(?:code|no\\.?|number|heading)|customs\\s+(?:tariff\\s+)?(?:code|no\\.?)|commodity\\s+code|taric(?:\\s+code)?|(?:zoll|waren)tarif(?:nummer|nr\\.?)|statistische\\s+warennummer|warennummer)\\s*[:#.]?\\s*((?:\\d[\\d .]{3,14}\\d)(?:\\s*[,/;]\\s*\\d[\\d .]{3,14}\\d)*)");
    private static final Pattern CODE=Pattern.compile("\\d[\\d .]{3,14}\\d");

    /** Headings (first digits) that need a closer look, with the reason. A starting list, not a legal classification. */
    private static final Map<String,String> SENSITIVE=new LinkedHashMap<>();
    static{
        SENSITIVE.put("93","Waffen und Munition (Kap. 93)");
        SENSITIVE.put("2844","radioaktive Stoffe (2844)");
        SENSITIVE.put("3601","Pulver und Sprengstoffe (3601–3604)");SENSITIVE.put("3602","Pulver und Sprengstoffe (3601–3604)");SENSITIVE.put("3603","Pulver und Sprengstoffe (3601–3604)");SENSITIVE.put("3604","Pulver und Sprengstoffe (3601–3604)");
        SENSITIVE.put("8401","Kernreaktoren und Teile (8401)");
        SENSITIVE.put("8710","Panzer und Kampffahrzeuge (8710)");
        SENSITIVE.put("8806","unbemannte Luftfahrzeuge / Drohnen (8806)");
        SENSITIVE.put("8802","Luftfahrzeuge (8802)");
        SENSITIVE.put("2709","Rohöl (2709), Ölpreisdeckel und Embargos beachten");SENSITIVE.put("2710","Erdölerzeugnisse (2710), Ölpreisdeckel und Embargos beachten");
        SENSITIVE.put("7108","Gold (7108)");SENSITIVE.put("7102","Diamanten (7102)");
        SENSITIVE.put("8526","Radar- und Funknavigationsgeräte (8526), häufig Dual-Use");
        SENSITIVE.put("8471","Datenverarbeitungsmaschinen (8471), Dual-Use und Russland-Sanktionen prüfen");
        SENSITIVE.put("8542","Elektronische integrierte Schaltungen (8542), Dual-Use und Russland-Sanktionen prüfen");
    }
    private HsCodeCheck(){}

    /** Labelled HS codes in a text, as digits only (at least 4, at most 10). */
    public static List<String> codes(String text){
        var out=new LinkedHashSet<String>();
        if(text==null)return List.of();
        var m=LABELLED.matcher(text);
        while(m.find()){
            var c=CODE.matcher(m.group(1));
            while(c.find()){String digits=c.group().replaceAll("[ .]","");if(digits.length()>=4&&digits.length()<=10)out.add(digits);}
        }
        return List.copyOf(out);
    }
    static String pretty(String digits){
        if(digits.length()<=4)return digits;
        var b=new StringBuilder(digits.substring(0,4)).append('.').append(digits.substring(4,Math.min(6,digits.length())));
        if(digits.length()>6)b.append('.').append(digits.substring(6));
        return b.toString();
    }
    /** Two codes agree when the shorter one is a prefix of the longer one at heading level (4 digits) or deeper. */
    static boolean agree(String a,String b){String s=a.length()<=b.length()?a:b,l=s==a?b:a;return s.length()>=4&&l.startsWith(s);}

    public static List<CheckResult> evaluate(String lcGoods,List<LcDocument> documents){
        var results=new ArrayList<CheckResult>();
        var lcCodes=codes(lcGoods);
        var byDocument=new LinkedHashMap<String,List<String>>();
        for(var d:documents){var c=codes(d.getExtractedText());if(!c.isEmpty())byDocument.put(d.getOriginalFilename(),c);}
        if(lcCodes.isEmpty()&&byDocument.isEmpty())return results;
        String lcText=lcCodes.stream().map(HsCodeCheck::pretty).reduce((a,b)->a+", "+b).orElse(null);
        byDocument.forEach((name,codes)->{
            String docText=String.join(", ",codes.stream().map(HsCodeCheck::pretty).toList());
            if(lcCodes.isEmpty())results.add(new CheckResult(OK,"HS_CODE_FOUND","HS-Code im Dokument erkannt: "+docText+".",null,name,"HS-Code "+docText));
            else{
                var foreign=codes.stream().filter(c->lcCodes.stream().noneMatch(l->agree(c,l))).toList();
                if(foreign.isEmpty())results.add(new CheckResult(OK,"HS_CODE_OK","HS-Code passt zum LC: "+docText+".","HS-Code im LC: "+lcText,name,"HS-Code "+docText));
                else results.add(new CheckResult(DISCREPANCY,"HS_CODE_MISMATCH","HS-Code weicht vom LC ab: "+String.join(", ",foreign.stream().map(HsCodeCheck::pretty).toList())+".","HS-Code im LC: "+lcText,name,"HS-Code "+docText));
            }
        });
        // Documents among each other, when the LC names no code.
        if(lcCodes.isEmpty()&&byDocument.size()>1){
            var names=new ArrayList<>(byDocument.keySet());
            for(int i=1;i<names.size();i++){
                var first=byDocument.get(names.get(0));var other=byDocument.get(names.get(i));
                if(other.stream().noneMatch(c->first.stream().anyMatch(f->agree(c,f))))
                    results.add(new CheckResult(WARNING,"HS_CODE_INCONSISTENT","HS-Codes der Dokumente stimmen nicht überein: "+names.get(0)+" "+String.join(", ",first.stream().map(HsCodeCheck::pretty).toList())+" / "+names.get(i)+" "+String.join(", ",other.stream().map(HsCodeCheck::pretty).toList())+".",null,names.get(i),"HS-Code "+String.join(", ",other.stream().map(HsCodeCheck::pretty).toList())));
            }
        }
        var flagged=new LinkedHashMap<String,String>();
        java.util.function.BiConsumer<String,String> flag=(code,document)->SENSITIVE.entrySet().stream().filter(e->code.startsWith(e.getKey())).findFirst().ifPresent(e->flagged.putIfAbsent(pretty(code)+"|"+(document==null?"":document),e.getValue()));
        lcCodes.forEach(c->flag.accept(c,null));byDocument.forEach((name,codes)->codes.forEach(c->flag.accept(c,name)));
        flagged.forEach((key,reason)->{String[] parts=key.split("\\|",-1);
            results.add(new CheckResult(WARNING,"HS_CODE_EXPORT_CONTROL","HS-Code "+parts[0]+": "+reason+". Exportkontrolle, Genehmigungspflicht und Sanktionsrecht prüfen.","HS-Code "+parts[0],parts[1].isEmpty()?null:parts[1],reason));});
        return results;
    }
}
