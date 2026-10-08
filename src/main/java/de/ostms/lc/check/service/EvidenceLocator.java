package de.ostms.lc.check.service;

import de.ostms.lc.document.domain.LcDocument;
import de.ostms.lc.document.service.DocumentExtractionService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import java.util.*;

/** Exact whitespace-normalized evidence only; never invents page references. */
public final class EvidenceLocator {
    public record Location(String status,String method,List<Integer> pages){}
    public static Location locate(LcDocument doc,String evidence){
        String needle=normalize(evidence);
        if(needle.length()<4)return new Location("UNAVAILABLE","NONE",List.of());
        List<Integer> matches=new ArrayList<>();Map<Integer,String> pageTexts=new TreeMap<>();
        var ocr=DocumentExtractionService.readEvidence(doc.getOcrEvidenceJson());
        if(ocr!=null){
            Map<Integer,StringBuilder> pages=new TreeMap<>();
            for(var word:ocr.words())pages.computeIfAbsent(word.page(),ignored->new StringBuilder()).append(word.text()).append(' ');
            pages.forEach((page,text)->{pageTexts.put(page,text.toString());occurrences(text.toString(),needle,page,matches);});
            return matches.isEmpty()?anchors(doc,needle,pageTexts,"OCR_VALUE_ANCHOR"):result(matches,"OCR_TEXT");
        }
        if(doc.getContent()==null||!"application/pdf".equalsIgnoreCase(doc.getContentType()))return result(matches,"NONE");
        try(var pdf=Loader.loadPDF(doc.getContent())){
            if(pdf.getNumberOfPages()>100)return result(matches,"PAGE_LIMIT");
            var stripper=new PDFTextStripper();
            for(int page=1;page<=pdf.getNumberOfPages();page++){stripper.setStartPage(page);stripper.setEndPage(page);String text=stripper.getText(pdf);pageTexts.put(page,text);occurrences(text,needle,page,matches);}
            return matches.isEmpty()?anchors(doc,needle,pageTexts,"PDF_VALUE_ANCHOR"):result(matches,"PDF_TEXT");
        }catch(java.io.IOException exception){return result(List.of(),"UNREADABLE");}
    }
    private static void occurrences(String text,String needle,int page,List<Integer> matches){String haystack=normalize(text);for(int at=haystack.indexOf(needle);at>=0;at=haystack.indexOf(needle,at+needle.length()))matches.add(page);}
    private static Location anchors(LcDocument doc,String evidence,Map<Integer,String> pages,String method){
        Set<String> needles=new LinkedHashSet<>();
        String reference=normalize(doc.getExtractedReference());if(reference.length()>=6&&evidence.contains(reference))needles.add(reference);
        if(doc.getDocumentDate()!=null&&evidence.contains(doc.getDocumentDate().toString())){
            for(String pattern:List.of("uuuu-MM-dd","dd.MM.uuuu","dd/MM/uuuu","d/M/uuuu"))needles.add(doc.getDocumentDate().format(java.time.format.DateTimeFormatter.ofPattern(pattern)));
        }
        List<Integer> matches=new ArrayList<>();for(var entry:pages.entrySet())for(String value:needles){
            String text=normalize(entry.getValue());var matcher=java.util.regex.Pattern.compile("(?<![\\p{L}\\p{N}])"+java.util.regex.Pattern.quote(value)+"(?![\\p{L}\\p{N}])").matcher(text);while(matcher.find())matches.add(entry.getKey());
        }
        return result(List.copyOf(matches),matches.isEmpty()?"NONE":method);
    }
    private static String normalize(String text){return text==null?"":text.replaceAll("\\s+"," ").trim();}
    private static Location result(List<Integer> pages,String method){return new Location(pages.isEmpty()?"UNAVAILABLE":pages.size()==1?"MATCH":"AMBIGUOUS",method,pages.stream().distinct().toList());}
    private EvidenceLocator(){}
}
