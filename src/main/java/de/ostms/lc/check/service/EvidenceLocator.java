package de.ostms.lc.check.service;

import de.ostms.lc.document.domain.LcDocument;
import de.ostms.lc.document.service.DocumentExtractionService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import java.util.*;

/** Exact whitespace-normalized evidence only; never invents page references. */
public final class EvidenceLocator {
    /** A highlighted line on a page, as fractions of the page (0..1, origin top left). */
    public record Box(int page,double x,double y,double w,double h){}
    public record Location(String status,String method,List<Integer> pages,List<Box> boxes){
        public Location(String status,String method,List<Integer> pages){this(status,method,pages,List.of());}
    }
    /** Like {@link #locate}, plus the lines on the found pages that contain the evidence or its value anchor (at most five; none when unsure). */
    public static Location locateWithLines(LcDocument doc,String evidence){
        var location=locate(doc,evidence);
        if(location.pages().isEmpty()||!"application/pdf".equalsIgnoreCase(doc.getContentType())||doc.getContent()==null)return location;
        String needle=normalize(evidence);
        Set<String> needles=location.method().endsWith("_TEXT")?Set.of(needle):location.method().contains("FIELD_VALUE")?fieldValues(needle):anchorValues(doc,needle);
        if(needles.isEmpty())return location;
        try{
            List<Box> boxes=new ArrayList<>();
            for(var line:lines(doc)){
                if(!location.pages().contains(line.box().page()))continue;
                String text=normalize(line.text());
                if(needles.stream().anyMatch(n->bounded(n).matcher(text).find()))boxes.add(line.box());
                if(boxes.size()>5)return location;
            }
            return new Location(location.status(),location.method(),location.pages(),List.copyOf(boxes));
        }catch(java.io.IOException|RuntimeException unreadable){return location;}
    }
    private record Line(String text,Box box){}
    /** Text lines of a PDF with their position: from the stored OCR words for scans (only pages without rotation correction), otherwise from the PDF text. */
    private static List<Line> lines(LcDocument doc)throws java.io.IOException{
        List<Line> out=new ArrayList<>();
        try(var pdf=Loader.loadPDF(doc.getContent())){
            if(pdf.getNumberOfPages()>100)return out;
            var ocr=DocumentExtractionService.readEvidence(doc.getOcrEvidenceJson());
            if(ocr!=null){
                Set<Integer> rotated=new HashSet<>();if(ocr.pages()!=null)for(var p:ocr.pages())if(Math.abs(p.correctionDegrees())>0.01)rotated.add(p.page());
                Map<Integer,List<de.ostms.lc.document.service.OcrEvidence.Word>> byPage=new TreeMap<>();
                for(var w:ocr.words())if(!rotated.contains(w.page())&&w.page()>=1&&w.page()<=pdf.getNumberOfPages())byPage.computeIfAbsent(w.page(),k->new ArrayList<>()).add(w);
                double scale=ocr.dpi()/72.0;
                byPage.forEach((page,words)->{
                    var box=pdf.getPage(page-1).getCropBox();double pw=box.getWidth()*scale,ph=box.getHeight()*scale;
                    words.sort(Comparator.comparingInt(w->w.top()+w.height()/2));
                    List<de.ostms.lc.document.service.OcrEvidence.Word> current=new ArrayList<>();
                    for(var w:words){
                        if(!current.isEmpty()){var last=current.get(current.size()-1);if(Math.abs((w.top()+w.height()/2.0)-(last.top()+last.height()/2.0))>Math.max(4,last.height()/2.0)){out.add(ocrLine(page,current,pw,ph));current=new ArrayList<>();}}
                        current.add(w);
                    }
                    if(!current.isEmpty())out.add(ocrLine(page,current,pw,ph));
                });
                return out;
            }
            var stripper=new PDFTextStripper(){
                int page;double pw,ph;final List<org.apache.pdfbox.text.TextPosition> line=new ArrayList<>();final StringBuilder text=new StringBuilder();
                @Override protected void startPage(org.apache.pdfbox.pdmodel.PDPage p){var b=p.getCropBox();pw=b.getWidth();ph=b.getHeight();page=getCurrentPageNo();}
                @Override protected void writeString(String s,List<org.apache.pdfbox.text.TextPosition> positions){if(text.length()>0)text.append(' ');text.append(s);line.addAll(positions);}
                @Override protected void writeLineSeparator(){flush();}
                @Override protected void endPage(org.apache.pdfbox.pdmodel.PDPage p){flush();}
                void flush(){
                    if(!line.isEmpty()&&pw>0&&ph>0){
                        double x0=Double.MAX_VALUE,y0=Double.MAX_VALUE,x1=0,y1=0;
                        for(var t:line){x0=Math.min(x0,t.getXDirAdj());x1=Math.max(x1,t.getXDirAdj()+t.getWidthDirAdj());y0=Math.min(y0,t.getYDirAdj()-t.getHeightDir());y1=Math.max(y1,t.getYDirAdj());}
                        out.add(new Line(text.toString(),box(page,x0/pw,y0/ph,(x1-x0)/pw,(y1-y0)/ph)));
                    }
                    line.clear();text.setLength(0);
                }
            };
            stripper.setSortByPosition(true);stripper.getText(pdf);
        }
        return out;
    }
    private static Line ocrLine(int page,List<de.ostms.lc.document.service.OcrEvidence.Word> words,double pw,double ph){
        int x0=Integer.MAX_VALUE,y0=Integer.MAX_VALUE,x1=0,y1=0;StringBuilder text=new StringBuilder();
        for(var w:words.stream().sorted(Comparator.comparingInt(de.ostms.lc.document.service.OcrEvidence.Word::left)).toList()){if(text.length()>0)text.append(' ');text.append(w.text());x0=Math.min(x0,w.left());y0=Math.min(y0,w.top());x1=Math.max(x1,w.left()+w.width());y1=Math.max(y1,w.top()+w.height());}
        return new Line(text.toString(),box(page,x0/pw,y0/ph,(x1-x0)/pw,(y1-y0)/ph));
    }
    /** A little padding around the line, clamped to the page. */
    private static Box box(int page,double x,double y,double w,double h){
        double px=0.006,py=0.004,left=Math.max(0,x-px),top=Math.max(0,y-py);
        return new Box(page,left,top,Math.min(1-left,w+2*px),Math.min(1-top,h+2*py));
    }
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
        Set<String> needles=anchorValues(doc,evidence);
        List<Integer> matches=new ArrayList<>();for(var entry:pages.entrySet())for(String value:needles){
            String text=normalize(entry.getValue());var matcher=java.util.regex.Pattern.compile("(?<![\\p{L}\\p{N}])"+java.util.regex.Pattern.quote(value)+"(?![\\p{L}\\p{N}])").matcher(text);while(matcher.find())matches.add(entry.getKey());
        }
        if(matches.isEmpty())return fieldAnchors(evidence,pages,method.replace("_VALUE_ANCHOR","_FIELD_VALUE_ANCHOR"));
        return result(List.copyOf(matches),method);
    }
    /** Fallback for rule-engine evidence ("DOCUMENT_TOTAL_AMOUNT = 125000.00 · …"): the checked document value in the spellings it usually has on paper. */
    private static Location fieldAnchors(String evidence,Map<Integer,String> pages,String method){
        Set<String> needles=fieldValues(evidence);
        List<Integer> matches=new ArrayList<>();for(var entry:pages.entrySet()){String text=normalize(entry.getValue());for(String value:needles){var m=bounded(value).matcher(text);while(m.find())matches.add(entry.getKey());}}
        return result(List.copyOf(matches),matches.isEmpty()?"NONE":method);
    }
    private static final java.util.regex.Pattern FIELD_EVIDENCE=java.util.regex.Pattern.compile("^(DOCUMENT_[A-Z_]+) = (.+?) · ");
    private static final java.util.Locale EN=java.util.Locale.ENGLISH;
    static Set<String> fieldValues(String evidence){
        Set<String> out=new LinkedHashSet<>();var m=FIELD_EVIDENCE.matcher(normalize(evidence));if(!m.find())return out;
        String v=m.group(2).trim();
        if(v.equals("nicht erfasst")||v.equalsIgnoreCase("true")||v.equalsIgnoreCase("false")||v.matches("\\d{1,3}")||v.length()<3)return out;
        if(v.matches("\\d+(\\.\\d+)?")){
            var n=new java.math.BigDecimal(v).setScale(2,java.math.RoundingMode.HALF_UP);
            var us=new java.text.DecimalFormat("#,##0.00",java.text.DecimalFormatSymbols.getInstance(EN));var de=new java.text.DecimalFormat("#,##0.00",java.text.DecimalFormatSymbols.getInstance(java.util.Locale.GERMANY));
            out.add(us.format(n));out.add(de.format(n));out.add(n.toPlainString());
            if(n.stripTrailingZeros().scale()<=0){var whole=n.setScale(0,java.math.RoundingMode.UNNECESSARY);out.add(new java.text.DecimalFormat("#,##0",java.text.DecimalFormatSymbols.getInstance(EN)).format(whole));out.add(new java.text.DecimalFormat("#,##0",java.text.DecimalFormatSymbols.getInstance(java.util.Locale.GERMANY)).format(whole));}
            out.removeIf(x->x.matches("\\d{1,3}"));return out;
        }
        if(v.matches("\\d{4}-\\d{2}-\\d{2}")){
            var d=java.time.LocalDate.parse(v);
            for(String pattern:List.of("uuuu-MM-dd","dd.MM.uuuu","dd/MM/uuuu","d/M/uuuu","dd MMM uuuu","d MMM uuuu","MMM d, uuuu","dd-MMM-uuuu"))out.add(d.format(java.time.format.DateTimeFormatter.ofPattern(pattern,EN)));
            return out;
        }
        if(v.length()>60){int cut=v.lastIndexOf(' ',60);v=v.substring(0,cut>20?cut:60);}
        out.add(v);return out;
    }
    /** The value as a whole word or number ("125,000" does not match inside "125,000.00"), ignoring case. */
    private static java.util.regex.Pattern bounded(String value){return java.util.regex.Pattern.compile("(?<![\\p{L}\\p{N}]|\\p{N}[.,])"+java.util.regex.Pattern.quote(value)+"(?![\\p{L}\\p{N}]|[.,]\\p{N})",java.util.regex.Pattern.CASE_INSENSITIVE|java.util.regex.Pattern.UNICODE_CASE);}
    /** Values from the evidence that can be found in the document text: the captured reference and the document date in its usual spellings. */
    private static Set<String> anchorValues(LcDocument doc,String evidence){
        Set<String> needles=new LinkedHashSet<>();
        String reference=normalize(doc.getExtractedReference());if(reference.length()>=6&&evidence.contains(reference))needles.add(reference);
        if(doc.getDocumentDate()!=null&&evidence.contains(doc.getDocumentDate().toString())){
            for(String pattern:List.of("uuuu-MM-dd","dd.MM.uuuu","dd/MM/uuuu","d/M/uuuu"))needles.add(doc.getDocumentDate().format(java.time.format.DateTimeFormatter.ofPattern(pattern)));
        }
        return needles;
    }
    private static String normalize(String text){return text==null?"":text.replaceAll("\\s+"," ").trim();}
    private static Location result(List<Integer> pages,String method){return new Location(pages.isEmpty()?"UNAVAILABLE":pages.size()==1?"MATCH":"AMBIGUOUS",method,pages.stream().distinct().toList());}
    private EvidenceLocator(){}
}
