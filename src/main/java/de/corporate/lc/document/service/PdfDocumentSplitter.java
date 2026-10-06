package de.corporate.lc.document.service;

import de.corporate.lc.document.domain.DocumentType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import java.io.ByteArrayOutputStream;
import java.util.*;

/** Page-boundary proposals, never an automatic destructive split. */
public final class PdfDocumentSplitter {
    public record Part(int fromPage,int toPage,DocumentType documentType) {}
    public record Page(int number,DocumentClassifier.Classification classification,String textSource) {}
    public record Proposal(int pageCount,List<Page> pages,List<Part> parts) {}
    public record Output(Part part,byte[] content,String text,OcrEvidence evidence) {}
    private PdfDocumentSplitter() {}

    public static Proposal propose(byte[] content,String evidenceJson) throws Exception {
        try(var slot=PdfProcessingSafety.acquire();var pdf=Loader.loadPDF(content)) {
            PdfProcessingSafety.validate(pdf);
            var texts=pageTexts(pdf,DocumentExtractionService.readEvidence(evidenceJson));
            List<Page> pages=new ArrayList<>();List<Part> parts=new ArrayList<>();
            DocumentType current=null;int start=1;
            for(int i=0;i<texts.size();i++) {
                var classification=DocumentClassifier.classify(null,texts.get(i));
                var detected=classification.score()>=.8?classification.suggestedType():null;
                pages.add(new Page(i+1,classification,texts.get(i).isBlank()?"UNAVAILABLE":"PAGE_TEXT"));
                // Unknown pages are isolated instead of silently treated as continuations.
                if(i>0&&(detected==null||current==null||detected!=current)) {
                    parts.add(new Part(start,i,current==null?DocumentType.OTHER:current));start=i+1;
                }
                current=detected;
            }
            parts.add(new Part(start,texts.size(),current==null?DocumentType.OTHER:current));
            return new Proposal(texts.size(),List.copyOf(pages),List.copyOf(parts));
        }
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

    static void validate(List<Part> parts,int pageCount) {
        if(parts==null||parts.size()<2||parts.size()>100)throw new IllegalArgumentException("Bitte 2 bis 100 Teil-Dokumente angeben.");
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
