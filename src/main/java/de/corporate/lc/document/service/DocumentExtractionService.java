package de.corporate.lc.document.service;

import de.corporate.lc.document.domain.LcDocument;
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
    private static final int MAX_TEXT_LENGTH = 100_000;
    private static final int MAX_OCR_PAGES = 20;
    private static final Pattern DOCUMENT_NUMBER = Pattern.compile("(?im)^(?:invoice|commercial invoice|document)\\s*(?:no\\.?|number|#)?\\s*[:#-]?\\s*([A-Z0-9][A-Z0-9./_-]{2,})\\s*$");
    private static final Pattern LC_REFERENCE = Pattern.compile("(?im)(?:letter of credit|documentary credit|lc|l/c)\\s*(?:no\\.?|number|reference|ref\\.?|#)?\\s*[:#-]?\\s*([A-Z0-9][A-Z0-9./_-]{3,})");
    private static final Pattern AMOUNT = Pattern.compile("(?im)(?:total|invoice amount|grand total|amount due)\\s*[:]?\\s*(EUR|USD|GBP|CHF|JPY)?\\s*([0-9][0-9., ]{0,20})\\s*(EUR|USD|GBP|CHF|JPY)?");

    public record TextExtraction(String text,String status,OcrEvidence ocrEvidence) {public TextExtraction(String text,String status){this(text,status,null);}}
    public TextExtraction extractFile(byte[] content,String filename,String contentType) { LcDocument document=new LcDocument();document.setContent(content);document.setOriginalFilename(filename==null?"document":filename);document.setContentType(contentType==null?"application/octet-stream":contentType);extract(document);return new TextExtraction(document.getExtractedText(),document.getExtractionStatus(),readEvidence(document.getOcrEvidenceJson())); }

    public void extract(LcDocument document) {
        document.setOcrEvidenceJson(null);
        try (var slot=PdfProcessingSafety.acquire()) {
            String text = readText(document);
            if (text == null) {
                document.setExtractionStatus("UNSUPPORTED");
                return;
            }
            boolean ocrUsed = false;
            text = normalize(text);
            if (text.isBlank() && isPdf(document)) {
                text = normalize(readPdfWithOcr(document));
                ocrUsed = !text.isBlank();
            }
            document.setExtractedText(limit(text));
            if (text.isBlank()) {
                document.setExtractionStatus("NO_TEXT");
                return;
            }
            match(DOCUMENT_NUMBER, text, 1).ifPresent(document::setExtractedDocumentNumber);
            match(LC_REFERENCE, text, 1).ifPresent(document::setExtractedReference);
            var amountMatcher = AMOUNT.matcher(text);
            if (amountMatcher.find()) {
                String currency = amountMatcher.group(1) != null ? amountMatcher.group(1) : amountMatcher.group(3);
                document.setExtractedCurrency(currency == null ? null : currency.toUpperCase(Locale.ROOT));
                parseAmount(amountMatcher.group(2)).ifPresent(document::setExtractedAmount);
            }
            document.setExtractionStatus(ocrUsed ? "OCR_EXTRACTED" : "EXTRACTED");
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
        }
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

    private String readPdfWithOcr(LcDocument document) throws Exception {
        Path directory = Files.createTempDirectory("lc-ocr-");
        try {
            Path input = directory.resolve("input.pdf");
            Files.write(input, document.getContent(), StandardOpenOption.CREATE_NEW);
            ProcessBuilder render = new ProcessBuilder("pdftoppm", "-png", "-r", "200", "-f", "1", "-l",
                    String.valueOf(MAX_OCR_PAGES), input.toString(), directory.resolve("page").toString())
                    .redirectErrorStream(true);
            runOcrStep(render,60);
            List<Path> pages;
            try (var files = Files.list(directory)) {
                pages = files.filter(p -> p.getFileName().toString().startsWith("page-") && p.toString().endsWith(".png"))
                        .sorted(Comparator.comparingInt(p->Integer.parseInt(p.getFileName().toString().replaceAll("[^0-9]","")))).limit(MAX_OCR_PAGES).toList();
            }
            if(pages.isEmpty())throw new IOException("PDF renderer produced no pages");
            StringBuilder result = new StringBuilder();
            java.util.ArrayList<OcrEvidence.Word> words=new java.util.ArrayList<>();
            String engineVersion=tesseractVersion(directory);
            for (int index = 0; index < pages.size() && result.length() < MAX_TEXT_LENGTH; index++) {
                Path output = directory.resolve("ocr-" + index);
                ProcessBuilder ocr;
                ocr = new ProcessBuilder("tesseract", pages.get(index).toString(), output.toString(), "-l", "deu+eng",
                    "-c", "tessedit_create_txt=1", "-c", "tessedit_create_tsv=1")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectErrorStream(true);
                runOcrStep(ocr,30);
                Path textFile = Path.of(output + ".txt");
                if(!Files.isRegularFile(textFile))throw new IOException("OCR produced no text file");
                if(Files.exists(textFile)&&Files.size(textFile)>10*1024*1024)throw new IOException("OCR-Ausgabe zu groß");
                if (Files.exists(textFile)) result.append(Files.readString(textFile, StandardCharsets.UTF_8)).append('\n');
                Path tsvFile=Path.of(output+".tsv");
                int page=Integer.parseInt(pages.get(index).getFileName().toString().replaceAll("[^0-9]",""));
                if(Files.exists(tsvFile)&&Files.size(tsvFile)>10*1024*1024)throw new IOException("OCR-Ausgabe zu groß");
                if(Files.exists(tsvFile))words.addAll(OcrEvidence.parseTsv(Files.readString(tsvFile,StandardCharsets.UTF_8),page));
            }
            if(!Double.isFinite(ocrThreshold)||ocrThreshold<0||ocrThreshold>1)throw new IllegalArgumentException("Ungültige OCR-Konfidenzschwelle");
            document.setOcrEvidenceJson(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(new OcrEvidence(engineVersion,"TESSERACT_WORD_MIN_V2",200,ocrThreshold,List.copyOf(words))));
            return limit(result.toString());
        } finally {
            deleteDirectory(directory);
        }
    }

    public static OcrEvidence readEvidence(String json){if(json==null)return null;try{return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json,OcrEvidence.class);}catch(Exception e){return null;}}

    private String tesseractVersion(Path directory){
        try{Path file=directory.resolve("version.txt");BoundedProcess.run(new ProcessBuilder("tesseract","--version").redirectErrorStream(true).redirectOutput(file.toFile()),5);return Files.readAllLines(file).stream().findFirst().orElse("unknown");}catch(Exception e){return "unknown";}
    }

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
    private void runOcrStep(ProcessBuilder builder,long seconds)throws IOException,InterruptedException {
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
