package de.corporate.lc.check.service;

import de.corporate.lc.document.domain.LcDocument;
import de.corporate.lc.document.service.DocumentExtractionService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import java.util.*;

/** Exact whitespace-normalized evidence only; never invents page references. */
public final class EvidenceLocator {
    public record Location(String status,String method,List<Integer> pages){}
    public static Location locate(LcDocument doc,String evidence){
        String needle=normalize(evidence);
        if(needle.length()<4)return new Location("UNAVAILABLE","NONE",List.of());
        List<Integer> matches=new ArrayList<>();
        var ocr=DocumentExtractionService.readEvidence(doc.getOcrEvidenceJson());
        if(ocr!=null){
            Map<Integer,StringBuilder> pages=new TreeMap<>();
            for(var word:ocr.words())pages.computeIfAbsent(word.page(),ignored->new StringBuilder()).append(word.text()).append(' ');
            pages.forEach((page,text)->occurrences(text.toString(),needle,page,matches));
            return result(matches,"OCR_TEXT");
        }
        if(doc.getContent()==null||!"application/pdf".equalsIgnoreCase(doc.getContentType()))return result(matches,"NONE");
        try(var pdf=Loader.loadPDF(doc.getContent())){
            if(pdf.getNumberOfPages()>100)return result(matches,"PAGE_LIMIT");
            var stripper=new PDFTextStripper();
            for(int page=1;page<=pdf.getNumberOfPages();page++){stripper.setStartPage(page);stripper.setEndPage(page);occurrences(stripper.getText(pdf),needle,page,matches);}
            return result(matches,"PDF_TEXT");
        }catch(java.io.IOException exception){return result(List.of(),"UNREADABLE");}
    }
    private static void occurrences(String text,String needle,int page,List<Integer> matches){String haystack=normalize(text);for(int at=haystack.indexOf(needle);at>=0;at=haystack.indexOf(needle,at+needle.length()))matches.add(page);}
    private static String normalize(String text){return text==null?"":text.replaceAll("\\s+"," ").trim();}
    private static Location result(List<Integer> pages,String method){return new Location(pages.isEmpty()?"UNAVAILABLE":pages.size()==1?"MATCH":"AMBIGUOUS",method,pages.stream().distinct().toList());}
    private EvidenceLocator(){}
}
