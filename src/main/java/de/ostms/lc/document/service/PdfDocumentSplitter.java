package de.ostms.lc.document.service;

import de.ostms.lc.document.domain.DocumentType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import java.io.ByteArrayOutputStream;
import java.util.*;

/** Page-boundary proposals, never an automatic destructive split. */
public final class PdfDocumentSplitter {
    public record Part(int fromPage,int toPage,DocumentType documentType,Integer copyNumber) {
        public Part(int fromPage,int toPage,DocumentType type){this(fromPage,toPage,type,null);}
        public Part {de.ostms.lc.document.domain.DocumentCopy.validate(copyNumber);}
    }
    public record Page(int number,DocumentClassifier.Classification classification,String textSource,DocumentCopyDetector.Hint copyHint) {
        public Page(int number,DocumentClassifier.Classification classification,String textSource){this(number,classification,textSource,DocumentCopyDetector.detect(null));}
    }
    public record Proposal(int pageCount,List<Page> pages,List<Part> parts) {}
    public record Output(Part part,byte[] content,String text,OcrEvidence evidence) {}
    private PdfDocumentSplitter() {}

    /** Exact normalized page sequence, not fuzzy matching. Variable numbers are ignored. */
    public static String trainingPattern(byte[] content,String evidenceJson)throws Exception {
        try(var slot=PdfProcessingSafety.acquire();var pdf=Loader.loadPDF(content)) {
            PdfProcessingSafety.validate(pdf);
            var texts=pageTexts(pdf,DocumentExtractionService.readEvidence(evidenceJson));
            var normalized=new ArrayList<String>();
            for(String text:texts){
                String page=text.toLowerCase(Locale.ROOT).replaceAll("\\p{N}+","#").replaceAll("\\s+"," ").trim();
                if(page.replaceAll("[^\\p{L}]","").length()<40)return null;
                normalized.add(page);
            }
            String pattern="SPLIT_V1:"+texts.size()+":"+String.join("\u000c",normalized);
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(pattern.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
    }

    public static Proposal propose(byte[] content,String evidenceJson) throws Exception {
        try(var slot=PdfProcessingSafety.acquire();var pdf=Loader.loadPDF(content)) {
            PdfProcessingSafety.validate(pdf);
            var texts=pageTexts(pdf,DocumentExtractionService.readEvidence(evidenceJson));
            List<Page> pages=new ArrayList<>();List<Part> parts=new ArrayList<>();
            DocumentType current=null;int start=1;int[] previousNumber=null;String reference=null;
            DocumentCopyDetector.Hint previousCopy=DocumentCopyDetector.detect(null);
            for(int i=0;i<texts.size();i++) {
                var classification=DocumentClassifier.classify(null,texts.get(i));
                var detected=classification.score()>=.8?classification.suggestedType():null;
                int[] number=pageNumber(texts.get(i));String nextReference=documentReference(texts.get(i));
                var copy=DocumentCopyDetector.detect(texts.get(i));
                boolean differentCopy=!copy.kind().equals("UNKNOWN")&&!previousCopy.kind().equals("UNKNOWN")&&(!copy.kind().equals(previousCopy.kind())||copy.copyNumber()!=null&&previousCopy.copyNumber()!=null&&!copy.copyNumber().equals(previousCopy.copyNumber()));
                boolean restart=i>0&&number!=null&&number[0]==1;
                boolean differentReference=reference!=null&&nextReference!=null&&!reference.equals(nextReference);
                boolean numberedContinuation=number!=null&&previousNumber!=null&&number[0]==previousNumber[0]+1&&number[1]==previousNumber[1];
                boolean referenceContinuation=number==null&&reference!=null&&reference.equals(nextReference);
                boolean continuation=i>0&&current!=null&&(numberedContinuation||referenceContinuation)&&!differentReference&&!differentCopy&&!restart;
                if(detected==null&&"UNKNOWN".equals(classification.status())&&!texts.get(i).isBlank()&&continuation){
                    detected=current;
                    classification=new DocumentClassifier.Classification(current,.8,"REVIEW",numberedContinuation?"PAGE_SEQUENCE_V1":"DOCUMENT_REFERENCE_V1",List.of(numberedContinuation?"Fortsetzungsseite "+number[0]+" / "+number[1]+"; bitte prüfen":"Gleiche Dokumentnummer wie vorherige Seite; bitte prüfen"));
                }
                pages.add(new Page(i+1,classification,texts.get(i).isBlank()?"UNAVAILABLE":"PAGE_TEXT",DocumentCopyDetector.detect(texts.get(i))));
                // Unknown pages are isolated instead of silently treated as continuations.
                if(i>0&&(detected==null||current==null||detected!=current||restart||differentReference||differentCopy)) {
                    parts.add(new Part(start,i,current==null?DocumentType.OTHER:current));start=i+1;
                    reference=null;
                }
                current=detected;
                if(!copy.kind().equals("UNKNOWN"))previousCopy=copy;
                else if(start==i+1)previousCopy=copy;
                previousNumber=number;if(nextReference!=null)reference=nextReference;
            }
            parts.add(new Part(start,texts.size(),current==null?DocumentType.OTHER:current));
            var suggestedParts=parts.stream().map(part->new Part(part.fromPage(),part.toPage(),part.documentType(),DocumentCopyDetector.detect(String.join("\n",texts.subList(part.fromPage()-1,part.toPage()))).copyNumber())).toList();
            return new Proposal(texts.size(),List.copyOf(pages),suggestedParts);
        }
    }

    private static int[] pageNumber(String text){
        var matcher=java.util.regex.Pattern.compile("(?i)\\b(?:page|seite)\\s*(\\d{1,3})\\s*(?:of|von|/)\\s*(\\d{1,3})\\b").matcher(text);
        int[] result=null;
        while(matcher.find()){int page=Integer.parseInt(matcher.group(1)),total=Integer.parseInt(matcher.group(2));if(page<1||page>total)return null;if(result!=null&&(result[0]!=page||result[1]!=total))return null;result=new int[]{page,total};}
        return result;
    }
    private static String documentReference(String text){
        var matcher=java.util.regex.Pattern.compile("(?im)^\\s*(?:commercial\\s+invoice|invoice|packing\\s+list|handelsrechnung|packliste|bill\\s+of\\s+lading|b/?l|air\\s+waybill|awb|certificate\\s+of\\s+origin)\\s+(?:no\\.?|number|nr\\.?)\\s*[:#]?\\s*([a-z0-9][a-z0-9/-]{1,79})\\b").matcher(text);
        return matcher.find()?matcher.group(1).toUpperCase(Locale.ROOT):null;
    }

    public static List<Output> split(byte[] content,String evidenceJson,List<Part> parts) throws Exception {
        try(var slot=PdfProcessingSafety.acquire();var pdf=Loader.loadPDF(content)) {
            PdfProcessingSafety.validate(pdf);validate(parts,pdf.getNumberOfPages());
            var evidence=DocumentExtractionService.readEvidence(evidenceJson);var texts=pageTexts(pdf,evidence);
            List<Output> results=new ArrayList<>();long total=0;
            for(var part:parts)try(var target=new PDDocument();var bytes=new ByteArrayOutputStream()) {
                for(int page=part.fromPage();page<=part.toPage();page++)target.importPage(pdf.getPage(page-1));
                target.save(bytes);total+=bytes.size();
                if(bytes.size()>10*1024*1024||total>50*1024*1024)throw new IllegalArgumentException("Aufgeteilte PDFs überschreiten das Größenlimit.");
                OcrEvidence mapped=null;
                if(evidence!=null) {
                    var words=evidence.words().stream().filter(w->w.page()>=part.fromPage()&&w.page()<=part.toPage())
                        .map(w->new OcrEvidence.Word(w.text(),w.confidence(),w.page()-part.fromPage()+1,w.left(),w.top(),w.width(),w.height())).toList();
                    mapped=new OcrEvidence(evidence.engineVersion(),evidence.method(),evidence.dpi(),evidence.threshold(),words);
                }
                results.add(new Output(part,bytes.toByteArray(),String.join("\n",texts.subList(part.fromPage()-1,part.toPage())),mapped));
            }
            return List.copyOf(results);
        }
    }

    public static void validate(List<Part> parts,int pageCount) {
        if(parts==null||parts.isEmpty()||parts.size()>100)throw new IllegalArgumentException("Bitte 1 bis 100 Dokumentbereiche angeben.");
        int next=1;
        for(var part:parts) {
            if(part==null||part.documentType()==null||part.fromPage()!=next||part.toPage()<next||part.toPage()>pageCount)
                throw new IllegalArgumentException("Seitenbereiche müssen alle Seiten genau einmal und in Reihenfolge abdecken.");
            next=part.toPage()+1;
        }
        if(next!=pageCount+1)throw new IllegalArgumentException("Es fehlen Seiten in der Aufteilung.");
    }

    private static List<String> pageTexts(PDDocument pdf,OcrEvidence evidence) throws Exception {
        List<String> result=new ArrayList<>();var stripper=new PDFTextStripper();
        for(int page=1;page<=pdf.getNumberOfPages();page++) {
            stripper.setStartPage(page);stripper.setEndPage(page);String text=stripper.getText(pdf);
            if(text.isBlank()&&evidence!=null) {
                int number=page;var words=evidence.words().stream().filter(w->w.page()==number).toList();
                StringBuilder rebuilt=new StringBuilder();int top=-1;
                for(var word:words) {if(top>=0&&Math.abs(word.top()-top)>8)rebuilt.append('\n');else rebuilt.append(' ');rebuilt.append(word.text());top=word.top();}
                text=rebuilt.toString();
            }
            result.add(text.length()>100_000?text.substring(0,100_000):text);
        }
        return result;
    }
}
