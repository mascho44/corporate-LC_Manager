package de.ostms.lc.training.service;

import de.ostms.lc.training.domain.TrainingSession;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;

@Service
public class PdfFieldSnippetService {
    private static final Pattern FIELD=Pattern.compile("(?m)^:(\\d{2}[A-Z]?):");
    private static final int MAX_CACHE_SESSIONS=12;
    private final Map<UUID,List<byte[]>> cache=Collections.synchronizedMap(new LinkedHashMap<>(16,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<UUID,List<byte[]>> eldest){return size()>MAX_CACHE_SESSIONS;}
    });

    public byte[] snippet(TrainingSession session,int index){
        List<byte[]> snippets;
        synchronized(cache){snippets=cache.get(session.getId());if(snippets==null){snippets=create(session);cache.put(session.getId(),snippets);}}
        if(index<0||index>=snippets.size())throw new IllegalArgumentException("PDF-Ausschnitt wurde nicht gefunden.");
        return snippets.get(index);
    }

    private List<byte[]> create(TrainingSession session){
        List<String> codes=codes(session.getExtractedText());
        boolean advice="ADVISING_LETTER".equals(session.getMessageType());
        if(advice){try{codes=new ArrayList<>();for(var field:new com.fasterxml.jackson.databind.ObjectMapper().readTree(session.getReviewsJson())){String label=field.path("sourceLabel").asText();codes.add(label.startsWith("Empfänger (Briefkopf")?field.path("originalValue").asText().split("\\R")[0]:label);}}catch(Exception e){return List.of(placeholder("Trainingsfelder nicht lesbar"));}}
        if(codes.isEmpty())return List.of(placeholder("Keine SWIFT-Felder erkannt"));
        Path directory=null;
        try(var slot=de.ostms.lc.document.service.PdfProcessingSafety.acquire()){
            List<PageData> pageData=digitalPages(session.getOriginalPdf());
            if(!pageData.isEmpty())return advice?cropAdvice(codes,pageData):cropFields(codes,pageData);
            directory=Files.createTempDirectory("lc-snippets-"); Path pdf=directory.resolve("source.pdf");Files.write(pdf,session.getOriginalPdf());
            ProcessBuilder render=new ProcessBuilder("pdftoppm","-png","-r","150","-f","1","-l","20",pdf.toString(),directory.resolve("page").toString()).redirectErrorStream(true);
            de.ostms.lc.document.service.BoundedProcess.run(render,90);
            List<Path> pages;try(var files=Files.list(directory)){pages=files.filter(p->p.getFileName().toString().matches("page-\\d+\\.png")).sorted(Comparator.comparingInt(this::pageNumber)).toList();}
            pageData=new ArrayList<>();for(Path page:pages)pageData.add(new PageData(ImageIO.read(page.toFile()),ocr(page)));
            return advice?cropAdvice(codes,pageData):cropFields(codes,pageData);
        }catch(Exception e){return codes.stream().map(code->placeholder(":"+code+": Ausschnitt nicht verfügbar")).toList();}
        finally{if(directory!=null)delete(directory);}
    }

    private List<PageData> digitalPages(byte[] content)throws IOException{
        try(PDDocument document=Loader.loadPDF(content)){
            de.ostms.lc.document.service.PdfProcessingSafety.validate(document);
            PositionStripper stripper=new PositionStripper();stripper.setEndPage(20);stripper.setSortByPosition(true);stripper.getText(document);
            if(stripper.lines.isEmpty())return List.of();
            PDFRenderer renderer=new PDFRenderer(document);List<PageData> pages=new ArrayList<>();
            for(int page=0;page<Math.min(document.getNumberOfPages(),20);page++)pages.add(new PageData(renderer.renderImageWithDPI(page,150),stripper.lines.getOrDefault(page,List.of()).stream().map(line->line.scaled(150f/72f)).toList()));
            return pages;
        }
    }

    private List<byte[]> cropFields(List<String> codes,List<PageData> pages)throws IOException{
        List<byte[]> result=new ArrayList<>();int pageCursor=0,lineCursor=0;
        for(String code:codes){Match match=find(pages,code,pageCursor,lineCursor);if(match==null)match=find(pages,code,0,0);if(match==null){result.add(placeholder(":"+code+": nicht lokalisiert"));continue;}result.add(crop(match));pageCursor=match.page;lineCursor=match.line+1;}
        return result;
    }
    private List<byte[]> cropAdvice(List<String> labels,List<PageData> pages)throws IOException{
        List<byte[]> result=new ArrayList<>();
        for(String label:labels){List<Match> matches=new ArrayList<>();for(int page=0;page<pages.size();page++)for(int line=0;line<pages.get(page).lines.size();line++){var value=pages.get(page).lines.get(line);if(value.text.trim().equalsIgnoreCase(label)||value.text.trim().matches("(?i)^"+Pattern.quote(label)+"(?:\\s*:|\\s+).*"))matches.add(new Match(page,line,pages.get(page),value));}result.add(matches.size()==1?crop(matches.get(0)):placeholder("Keine eindeutige Fundstelle für "+label));}
        return result;
    }

    private List<Line> ocr(Path image)throws Exception{
        Path tsv=image.resolveSibling(image.getFileName()+".tsv");
        de.ostms.lc.document.service.BoundedProcess.run(new ProcessBuilder("tesseract",image.toString(),"stdout","-l","deu+eng","tsv").redirectOutput(tsv.toFile()),60);
        if(Files.size(tsv)>10*1024*1024)throw new IOException("OCR-Ausgabe zu groß");
        String output=Files.readString(tsv,StandardCharsets.UTF_8);
        Map<String,LineBuilder> grouped=new LinkedHashMap<>();
        for(String row:output.split("\\R")){String[] c=row.split("\\t",12);if(c.length<12||!c[0].matches("\\d+"))continue;String text=c[11].trim();if(text.isEmpty())continue;String key=c[1]+"-"+c[2]+"-"+c[3]+"-"+c[4];int left=number(c[6]),top=number(c[7]),width=number(c[8]),height=number(c[9]);grouped.computeIfAbsent(key,k->new LineBuilder()).add(text,left,top,width,height);}
        return grouped.values().stream().map(LineBuilder::build).toList();
    }

    private Match find(List<PageData> pages,String code,int startPage,int startLine){
        String marker=code.toUpperCase(Locale.ROOT);for(int p=startPage;p<pages.size();p++){List<Line> lines=pages.get(p).lines;for(int l=p==startPage?startLine:0;l<lines.size();l++){String text=lines.get(l).text.toUpperCase(Locale.ROOT).trim();if(text.contains(":"+marker+":")||text.matches("^(?:FIELDTAG\\s+)?"+Pattern.quote(marker)+"(?:[:.]|\\s+).*$"))return new Match(p,l,pages.get(p),lines.get(l));}}return null;
    }

    private byte[] crop(Match match)throws IOException{
        BufferedImage page=match.data.image;int y=Math.max(0,match.value.top-24);int height=Math.min(260,page.getHeight()-y);BufferedImage excerpt=page.getSubimage(0,y,page.getWidth(),height);ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(excerpt,"png",out);return out.toByteArray();
    }

    private byte[] placeholder(String message){try{BufferedImage image=new BufferedImage(900,170,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();g.setColor(new Color(248,250,252));g.fillRect(0,0,900,170);g.setColor(new Color(102,112,133));g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,20));g.drawString(message,28,88);g.dispose();ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);return out.toByteArray();}catch(IOException e){return new byte[0];}}
    private List<String> codes(String raw){List<String> result=new ArrayList<>();Matcher m=FIELD.matcher(raw==null?"":raw);while(m.find())result.add(m.group(1));return result;}
    private int pageNumber(Path path){Matcher m=Pattern.compile("(\\d+)").matcher(path.getFileName().toString());return m.find()?Integer.parseInt(m.group(1)):0;}
    private int number(String value){try{return Integer.parseInt(value);}catch(NumberFormatException e){return 0;}}
    private void delete(Path directory){try(var paths=Files.walk(directory)){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException ignored){}});}catch(IOException ignored){}}
    private record Line(String text,int left,int top,int width,int height){Line scaled(float factor){return new Line(text,Math.round(left*factor),Math.round(top*factor),Math.round(width*factor),Math.round(height*factor));}}
    private record PageData(BufferedImage image,List<Line> lines){}
    private record Match(int page,int line,PageData data,Line value){}
    private static class LineBuilder{StringBuilder text=new StringBuilder();int left=Integer.MAX_VALUE,top=Integer.MAX_VALUE,right,bottom;void add(String word,int x,int y,int w,int h){if(!text.isEmpty())text.append(' ');text.append(word);left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,x+w);bottom=Math.max(bottom,y+h);}Line build(){return new Line(text.toString(),left,top,right-left,bottom-top);}}
    private static class PositionStripper extends PDFTextStripper{
        final Map<Integer,List<Line>> lines=new HashMap<>();
        PositionStripper()throws IOException{}
        @Override protected void writeString(String text,List<TextPosition> positions){if(text==null||text.isBlank()||positions.isEmpty())return;float left=Float.MAX_VALUE,top=Float.MAX_VALUE,right=0,bottom=0;for(TextPosition p:positions){left=Math.min(left,p.getXDirAdj());top=Math.min(top,p.getYDirAdj()-p.getHeightDir());right=Math.max(right,p.getXDirAdj()+p.getWidthDirAdj());bottom=Math.max(bottom,p.getYDirAdj());}lines.computeIfAbsent(getCurrentPageNo()-1,k->new ArrayList<>()).add(new Line(text.strip(),Math.round(left),Math.round(top),Math.round(right-left),Math.round(bottom-top)));}
    }
}
