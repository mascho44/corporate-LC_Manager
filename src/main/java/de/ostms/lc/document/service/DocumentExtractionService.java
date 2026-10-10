package de.ostms.lc.document.service;

import de.ostms.lc.document.domain.LcDocument;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class DocumentExtractionService {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(DocumentExtractionService.class);
    @org.springframework.beans.factory.annotation.Value("${lc.ocr.confidence-threshold:0.8}")
    private double ocrThreshold=0.8;
    @org.springframework.beans.factory.annotation.Value("${lc.ocr.page-timeout-seconds:120}")
    private long ocrPageTimeoutSeconds=120;
    @org.springframework.beans.factory.annotation.Value("${lc.ocr.document-timeout-seconds:900}")
    private long ocrDocumentTimeoutSeconds=900;
    private static final int MAX_TEXT_LENGTH = 100_000;
    private static final java.util.regex.Pattern LANGUAGES=java.util.regex.Pattern.compile("[a-z]{3}(?:_[a-z]{3,4})?(?:\\+[a-z]{3}(?:_[a-z]{3,4})?){0,3}");
    /** Tesseract languages; OCR_LANGUAGES (e.g. "deu+eng+chi_sim") needs the matching traineddata in the image. */
    static String ocrLanguages(){return ocrLanguages(System.getenv("OCR_LANGUAGES"));}
    static String ocrLanguages(String configured){return configured!=null&&LANGUAGES.matcher(configured.trim()).matches()?configured.trim():"deu+eng";}
    /** Recognition runs at 300 DPI with Sauvola thresholding (clearly better on scans); stored positions stay in the 200-DPI evidence raster. */
    static final int OCR_DPI = 300, EVIDENCE_DPI = 200;
    static List<OcrEvidence.Word> toEvidenceRaster(List<OcrEvidence.Word> words){return toEvidenceRaster(words,OCR_DPI);}
    /** Words are stored in the 200-DPI evidence raster, whatever resolution the profile recognised at. */
    static List<OcrEvidence.Word> toEvidenceRaster(List<OcrEvidence.Word> words,int recognitionDpi){
        if(recognitionDpi==EVIDENCE_DPI)return words;
        var scaled=new java.util.ArrayList<OcrEvidence.Word>(words.size());
        for(var w:words)scaled.add(new OcrEvidence.Word(w.text(),w.confidence(),w.page(),scale(w.left(),recognitionDpi),scale(w.top(),recognitionDpi),Math.max(1,scale(w.width(),recognitionDpi)),Math.max(1,scale(w.height(),recognitionDpi))));
        return java.util.List.copyOf(scaled);
    }
    private static int scale(int value,int dpi){return (int)Math.round(value*(double)EVIDENCE_DPI/dpi);}
    @org.springframework.beans.factory.annotation.Autowired(required=false) private OcrEngine ocrEngine;
    @org.springframework.beans.factory.annotation.Autowired(required=false) private ScanProfileService scanProfiles;
    private OcrEngine engine(){if(ocrEngine==null)ocrEngine=new TesseractEngine();return ocrEngine;}
    private ScanProfile profile(){try{return scanProfiles==null?ScanProfile.STANDARD:scanProfiles.current();}catch(RuntimeException unavailable){log.warn("Scan profile unavailable, using the standard profile: {}",unavailable.getClass().getSimpleName());return ScanProfile.STANDARD;}}
    @org.springframework.beans.factory.annotation.Value("${lc.ocr.max-pages:100}")
    private int maxOcrPages=100;
    private static final class PageLimitException extends IOException{}
    private static final Pattern DOCUMENT_NUMBER = Pattern.compile("(?im)^(?:invoice|commercial invoice|document)[\\t ]*+(?:no\\.?|number|#)?[\\t ]*+[:#-]?[\\t ]*+([A-Z0-9][A-Z0-9./_-]{2,})[\\t ]*+$");
    private static final Pattern LC_REFERENCE = Pattern.compile("(?im)(?:letter of credit|documentary credit|lc|l/c)[\\t ]*+(?:no\\.?|number|reference|ref\\.?|#)?[\\t ]*+[:#-]?[\\t ]*+([A-Z0-9][A-Z0-9./_-]{3,})");
    private static final Pattern AMOUNT = Pattern.compile("(?im)(?:total|invoice amount|grand total|amount due)[\\t ]*+[:]?[\\t ]*+(EUR|USD|GBP|CHF|JPY)?[\\t ]*+([0-9][0-9., ]{0,20})[\\t ]*+(EUR|USD|GBP|CHF|JPY)?");

    public record TextExtraction(String text,String status,OcrEvidence ocrEvidence) {public TextExtraction(String text,String status){this(text,status,null);}}
    public TextExtraction extractFile(byte[] content,String filename,String contentType) { LcDocument document=new LcDocument();document.setContent(content);document.setOriginalFilename(filename==null?"document":filename);document.setContentType(contentType==null?"application/octet-stream":contentType);extract(document);return new TextExtraction(document.getExtractedText(),document.getExtractionStatus(),readEvidence(document.getOcrEvidenceJson())); }

    public void extract(LcDocument document) {
        extract(document,Math.min(300,documentTimeoutSeconds()));
    }
    public void extractInBackground(LcDocument document){extract(document,documentTimeoutSeconds());}
    public void extractInBackground(LcDocument document,java.util.function.Consumer<LcDocument> checkpoint){extract(document,documentTimeoutSeconds(),checkpoint);}

    private void extract(LcDocument document,long documentBudget) {
        extract(document,documentBudget,ignored->{});
    }
    private void extract(LcDocument document,long documentBudget,java.util.function.Consumer<LcDocument> checkpoint) {
        // Authorization errors must escape, not become an extraction failure on a foreign object.
        de.ostms.lc.tenant.domain.TenantContext.require(document.getTenantId());
        var priorEvidence=readEvidence(document.getOcrEvidenceJson());
        document.setOcrEvidenceJson(null);
        long started=System.nanoTime();
        try (var slot=PdfProcessingSafety.acquire()) {
            List<String> pdfPages=isPdf(document)?resumePages(readPdfPages(document.getContent()),priorEvidence):null;
            String text = pdfPages==null?readText(document):String.join("\n",pdfPages);
            if (text == null) {
                document.setExtractionStatus("UNSUPPORTED");
                return;
            }
            boolean ocrUsed = false;
            text = normalize(text);
            if(pdfPages!=null){var positions=new java.util.ArrayList<>(PdfWordPositions.read(document.getContent()));if(priorEvidence!=null)positions.addAll(priorEvidence.words().stream().filter(w->w.confidence()!=null).toList());document.setOcrEvidenceJson(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(new OcrEvidence("PDFBOX","PDF_TEXT_POSITIONS",200,ocrThreshold,positions,priorEvidence==null?List.of():priorEvidence.pages())));}
            if (pdfPages!=null&&pdfPages.stream().anyMatch(String::isBlank)) {
                text = normalize(readPdfWithOcr(document,documentBudget,pdfPages,checkpoint));
                ocrUsed = !text.isBlank();
            }
            document.setExtractedText(limit(text));
            if (text.isBlank()) {
                document.setExtractionStatus("NO_TEXT");
                return;
            }
            var evidence=readEvidence(document.getOcrEvidenceJson());
            boolean failed=evidence!=null&&evidence.pages().stream().anyMatch(p->!List.of("EXTRACTED","OCR_EXTRACTED").contains(p.status()));
            applyRecognizedText(document,text,failed?"OCR_PARTIAL":ocrUsed||priorEvidence!=null&&!priorEvidence.pages().isEmpty() ? "OCR_EXTRACTED" : "EXTRACTED");
        } catch (PageLimitException exception) {
            document.setExtractionStatus("OCR_PAGE_LIMIT");
        } catch (BoundedProcess.TimeoutException exception) {
            document.setExtractionStatus("OCR_TIMEOUT");
        } catch (BoundedProcess.UnavailableException exception) {
            log.warn("Document extraction: {}",exception.getMessage());
            document.setExtractionStatus("OCR_UNAVAILABLE");
        } catch (Exception exception) {
            if(exception instanceof InterruptedException)Thread.currentThread().interrupt();
            // Do not log filenames, document text or arbitrary exception messages.
            log.warn("Document extraction failed: type={}",exception.getClass().getSimpleName());
            document.setExtractionStatus("FAILED");
        } finally {
            document.setClassificationHistoryJson(ClassificationHistory.automatic(document.getOriginalFilename(),document.getExtractedText()));
            log.info("Document extraction completed: status={}, elapsedMillis={}",document.getExtractionStatus(),java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));
        }
    }

    /** Reuse page-local recognition without rerunning OCR or copying aggregate metadata. */
    public void applyRecognizedText(LcDocument document,String recognized,String status) {
            de.ostms.lc.tenant.domain.TenantContext.require(document.getTenantId());
            String text=limit(normalize(recognized));document.setExtractedText(text);
            if(document.getDocumentDate()==null)document.setDocumentDate(DocumentDateDetector.detect(text).date());
            match(DOCUMENT_NUMBER, text, 1).ifPresent(document::setExtractedDocumentNumber);
            document.setExtractedReference(DocumentReferenceDetector.detect(text));
            var amountMatcher = AMOUNT.matcher(text);
            if (amountMatcher.find()) {
                String currency = amountMatcher.group(1) != null ? amountMatcher.group(1) : amountMatcher.group(3);
                document.setExtractedCurrency(currency == null ? null : currency.toUpperCase(Locale.ROOT));
                parseAmount(amountMatcher.group(2)).ifPresent(document::setExtractedAmount);
            }
            document.setExtractionStatus(status);
            document.setClassificationHistoryJson(ClassificationHistory.automatic(document.getOriginalFilename(),document.getExtractedText()));
    }

    private String readText(LcDocument document) throws Exception {
        String type = document.getContentType().toLowerCase(Locale.ROOT);
        String name = document.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (isPdf(document)) {
            try (var pdf = Loader.loadPDF(document.getContent())) {
                PdfProcessingSafety.validate(pdf);
                return new PDFTextStripper().getText(pdf);
            }
        }
        if (type.startsWith("text/") || name.endsWith(".txt") || name.endsWith(".xml") || name.endsWith(".csv"))
            return new String(document.getContent(), StandardCharsets.UTF_8);
        return null;
    }

    static List<String> readPdfPages(byte[] content)throws Exception{
        try(var pdf=Loader.loadPDF(content)){
            PdfProcessingSafety.validate(pdf);var stripper=new PDFTextStripper();var pages=new java.util.ArrayList<String>();
            for(int page=1;page<=pdf.getNumberOfPages();page++){stripper.setStartPage(page);stripper.setEndPage(page);String text=stripper.getText(pdf);pages.add(text.length()>MAX_TEXT_LENGTH?text.substring(0,MAX_TEXT_LENGTH):text);}
            return List.copyOf(pages);
        }
    }

    static List<String> resumePages(List<String> texts,OcrEvidence evidence){
        if(evidence==null)return texts;
        var result=new java.util.ArrayList<>(texts);
        for(int i=0;i<result.size();i++)if(result.get(i).isBlank()){String completed=evidence.completedPageText(i+1);if(completed!=null&&!completed.isBlank())result.set(i,completed);}
        return List.copyOf(result);
    }

    static List<int[]> blankPageRanges(List<String> texts){
        var ranges=new java.util.ArrayList<int[]>();int start=-1;
        for(int i=0;i<=texts.size();i++){boolean blank=i<texts.size()&&texts.get(i).isBlank();if(blank&&start<0)start=i+1;if(!blank&&start>0){ranges.add(new int[]{start,i});start=-1;}}
        return ranges;
    }

    private String readPdfWithOcr(LcDocument document,long documentBudget,List<String> pageTexts,java.util.function.Consumer<LcDocument> checkpoint) throws Exception {
        long requiredPages=pageTexts.stream().filter(String::isBlank).count();
        int limit=Math.max(1,Math.min(200,maxOcrPages));
        if(requiredPages>limit){log.warn("OCR page limit: scannedPages={}, maxPages={}",requiredPages,limit);throw new PageLimitException();}
        long started=System.nanoTime();
        Path directory = Files.createTempDirectory("lc-ocr-");
        try {
            Path input = directory.resolve("input.pdf");
            Files.write(input, document.getContent(), StandardOpenOption.CREATE_NEW);
            var images=new java.util.HashMap<Integer,Path>();
            StringBuilder result = new StringBuilder();
            java.util.ArrayList<OcrEvidence.Word> words=new java.util.ArrayList<>();
            var digital=readEvidence(document.getOcrEvidenceJson());if(digital!=null)words.addAll(digital.words());
            var pages=new java.util.ArrayList<OcrEvidence.PageResult>();
            for(int i=0;i<pageTexts.size();i++)pages.add(new OcrEvidence.PageResult(i+1,pageTexts.get(i).isBlank()?"QUEUED":"EXTRACTED",0));
            final ScanProfile profile=profile();
            final String languages=ocrLanguages();
            String engineVersion=engine().version(directory);
            for (int index = 0; index < pageTexts.size(); index++) {
                if(!pageTexts.get(index).isBlank()){if(result.length()<MAX_TEXT_LENGTH)result.append(pageTexts.get(index)).append('\n');int number=index+1;var previous=digital==null?null:digital.pages().stream().filter(p->p.page()==number&&"OCR_EXTRACTED".equals(p.status())).findFirst().orElse(null);pages.set(index,previous==null?new OcrEvidence.PageResult(number,"EXTRACTED",0):previous);continue;}
                try {
                if(!images.containsKey(index+1)){
                    int last=index;while(last+1<pageTexts.size()&&last-index<2&&pageTexts.get(last+1).isBlank())last++;
                    long remaining=documentBudget-java.util.concurrent.TimeUnit.NANOSECONDS.toSeconds(System.nanoTime()-started);
                    if(remaining<=0)throw new BoundedProcess.TimeoutException("OCR");
                    runOcrStep(new ProcessBuilder("pdftoppm","-png","-r",String.valueOf(profile.renderDpi()),"-f",String.valueOf(index+1),"-l",String.valueOf(last+1),input.toString(),directory.resolve("page").toString()).redirectErrorStream(true),Math.min(60,remaining));
                    try(var files=Files.list(directory)){files.filter(p->p.getFileName().toString().matches("page-[0-9]+\\.png")).forEach(p->images.put(Integer.parseInt(p.getFileName().toString().replaceAll("[^0-9]","")),p));}
                }
                Path image=images.remove(index+1);if(image==null)throw new IOException("PDF renderer produced no page");
                Path output = directory.resolve("ocr-" + index);
                int page=index+1;
                long elapsed=java.util.concurrent.TimeUnit.NANOSECONDS.toSeconds(System.nanoTime()-started);
                long remaining=documentBudget-elapsed;
                if(remaining<=0)throw new BoundedProcess.TimeoutException("OCR");
                long pageBudget=Math.min(pageTimeoutSeconds(),remaining);
                OcrEngine.Recognition primary;
                try{primary=engine().recognize(image,output,languages,profile.binarization(),page,this::runOcrStep,pageBudget);}catch(BoundedProcess.TimeoutException timeout){
                    log.warn("OCR timeout: page={}, pages={}, pageBudgetSeconds={}, documentBudgetSeconds={}, elapsedSeconds={}",index+1,pageTexts.size(),pageBudget,documentBudget,java.util.concurrent.TimeUnit.NANOSECONDS.toSeconds(System.nanoTime()-started));
                    throw timeout;
                }
                String recognized=primary.text();
                var pageWords=primary.words();
                int attempts=1;double correctionDegrees=0;
                long retryBudget=documentBudget-java.util.concurrent.TimeUnit.NANOSECONDS.toSeconds(System.nanoTime()-started);
                if(OcrQualityPolicy.needsRetry(pageWords)&&retryBudget>=10){
                    attempts=2;Path alternative=directory.resolve("alternative-"+index);
                    ScanGeometry.Prepared prepared=null;
                    try{
                        prepared=prepareScan(image,directory,index,Math.min(10,retryBudget));
                        Path corrected=directory.resolve("corrected-"+index+".png");
                        javax.imageio.ImageIO.write(prepared.image(),"png",corrected.toFile());
                        retryBudget=documentBudget-java.util.concurrent.TimeUnit.NANOSECONDS.toSeconds(System.nanoTime()-started);
                        if(retryBudget<=0)throw new BoundedProcess.TimeoutException("OCR");
                        var candidate=engine().recognizeAlternative(corrected,alternative,languages,page,this::runOcrStep,Math.min(pageTimeoutSeconds(),retryBudget));
                        if(candidate!=null&&OcrQualityPolicy.better(candidate.words(),pageWords)){pageWords=prepared.map(candidate.words());recognized=candidate.text();correctionDegrees=prepared.degrees();}
                    }catch(IOException failure){log.info("Alternative OCR kept primary result: page={}, reason={}",page,failure.getClass().getSimpleName());}
                    finally{
                        if(prepared!=null)prepared.image().flush();
                        Files.deleteIfExists(directory.resolve("corrected-"+index+".png"));
                    }
                }
                if(result.length()<MAX_TEXT_LENGTH)result.append(recognized).append('\n');
                words.addAll(toEvidenceRaster(pageWords,profile.renderDpi()));
                Files.deleteIfExists(image);
                pages.set(index,new OcrEvidence.PageResult(page,recognized.isBlank()?"NO_TEXT":"OCR_EXTRACTED",attempts,correctionDegrees,limit(recognized)));
                } catch(BoundedProcess.TimeoutException failure){
                    pages.set(index,new OcrEvidence.PageResult(index+1,"OCR_TIMEOUT",1));
                } catch(BoundedProcess.UnavailableException failure){throw failure;
                } catch(IOException failure){
                    pages.set(index,new OcrEvidence.PageResult(index+1,"FAILED",1));
                }
                document.setExtractedText(limit(result.toString()));
                document.setOcrEvidenceJson(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(new OcrEvidence(engineVersion,engine().method()+profile.methodSuffix(),ScanProfile.EVIDENCE_DPI,ocrThreshold,words,pages)));
                checkpoint.accept(document);
            }
            log.info("OCR completed: pages={}, ocrPages={}, textPages={}, elapsedMillis={}",pageTexts.size(),requiredPages,pageTexts.size()-requiredPages,java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));
            if(!Double.isFinite(ocrThreshold)||ocrThreshold<0||ocrThreshold>1)throw new IllegalArgumentException("Ungültige OCR-Konfidenzschwelle");
            document.setOcrEvidenceJson(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(new OcrEvidence(engineVersion,engine().method()+profile.methodSuffix(),ScanProfile.EVIDENCE_DPI,ocrThreshold,List.copyOf(words),pages)));
            return limit(result.toString());
        } finally {
            deleteDirectory(directory);
        }
    }

    public static OcrEvidence readEvidence(String json){if(json==null)return null;try{return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json,OcrEvidence.class);}catch(Exception e){return null;}}

    private ScanGeometry.Prepared prepareScan(Path image,Path directory,int index,long seconds)throws IOException,InterruptedException{
        var source=ScanGeometry.read(image);
        ScanGeometry.Prepared upright=null;
        try{
            int rotation=engine().detectRotation(image,directory.resolve("orientation-"+index),this::runOcrStep,seconds);
            upright=ScanGeometry.rotate(source,rotation);
            double skew=ScanGeometry.deskewAngle(upright.image());
            if(skew==0)return upright;
            var corrected=ScanGeometry.rotate(upright.image(),skew);
            var inverse=new java.awt.geom.AffineTransform(upright.inverse());inverse.concatenate(corrected.inverse());
            upright.image().flush();
            return new ScanGeometry.Prepared(corrected.image(),inverse,source.getWidth(),source.getHeight(),rotation+skew);
        }catch(IOException|RuntimeException failure){if(upright!=null)upright.image().flush();throw failure;}
        finally{source.flush();}
    }

    long pageTimeoutSeconds(){return Math.max(1,Math.min(300,ocrPageTimeoutSeconds));}
    long documentTimeoutSeconds(){return Math.max(60,Math.min(1800,ocrDocumentTimeoutSeconds));}

    private boolean isPdf(LcDocument document) {
        String type = document.getContentType() == null ? "" : document.getContentType().toLowerCase(Locale.ROOT);
        String name = document.getOriginalFilename() == null ? "" : document.getOriginalFilename().toLowerCase(Locale.ROOT);
        return type.equals("application/pdf") || name.endsWith(".pdf");
    }

    private String normalize(String text) { return text == null ? "" : text.replace('\u0000', ' ').replaceAll("[ \\t]+", " ").trim(); }
    private String limit(String text) { return text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) : text; }
    private void deleteDirectory(Path directory) {
        try (var paths = Files.walk(directory)) { paths.sorted(Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (IOException ignored) {} }); }
        catch (IOException ignored) {}
    }
    void runOcrStep(ProcessBuilder builder,long seconds)throws IOException,InterruptedException {
        try { BoundedProcess.run(builder,seconds); }
        catch(IOException failure) {
            // BoundedProcess messages contain only fixed tool names and exit/timeout status.
            log.warn("OCR step failed: {}",failure.getMessage());
            throw failure;
        }
    }

    private java.util.Optional<String> match(Pattern pattern, String text, int group) {
        var matcher = pattern.matcher(text);
        return matcher.find() ? java.util.Optional.of(matcher.group(group).trim()) : java.util.Optional.empty();
    }

    private java.util.Optional<BigDecimal> parseAmount(String raw) {
        String value = raw.replace(" ", "");
        int comma = value.lastIndexOf(','); int dot = value.lastIndexOf('.');
        if (comma > dot) value = value.replace(".", "").replace(',', '.');
        else if (dot > comma && comma >= 0) value = value.replace(",", "");
        else if (comma >= 0) value = value.replace(',', '.');
        try { return java.util.Optional.of(new BigDecimal(value)); }
        catch (NumberFormatException exception) { return java.util.Optional.empty(); }
    }
}
