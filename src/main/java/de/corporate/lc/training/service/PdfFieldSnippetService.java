package de.corporate.lc.training.service;

import de.corporate.lc.training.domain.TrainingSession;
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
        if(codes.isEmpty())return List.of(placeholder("Keine SWIFT-Felder erkannt"));
        Path directory=null;
        try{
            directory=Files.createTempDirectory("lc-snippets-"); Path pdf=directory.resolve("source.pdf");Files.write(pdf,session.getOriginalPdf());
            Process render=new ProcessBuilder("pdftoppm","-png","-r","150","-f","1","-l","20",pdf.toString(),directory.resolve("page").toString()).redirectErrorStream(true).start();
            if(!render.waitFor(90,TimeUnit.SECONDS)||render.exitValue()!=0)throw new IOException("PDF rendering failed");
            List<Path> pages;try(var files=Files.list(directory)){pages=files.filter(p->p.getFileName().toString().matches("page-\\d+\\.png")).sorted(Comparator.comparingInt(this::pageNumber)).toList();}
            List<PageData> pageData=new ArrayList<>();for(Path page:pages)pageData.add(new PageData(ImageIO.read(page.toFile()),ocr(page)));
            List<byte[]> result=new ArrayList<>();int pageCursor=0,lineCursor=0;
            for(String code:codes){Match match=find(pageData,code,pageCursor,lineCursor);if(match==null)match=find(pageData,code,0,0);if(match==null){result.add(placeholder(":"+code+": nicht lokalisiert"));continue;}result.add(crop(match));pageCursor=match.page;lineCursor=match.line+1;}
            return result;
        }catch(Exception e){return codes.stream().map(code->placeholder(":"+code+": Ausschnitt nicht verfügbar")).toList();}
        finally{if(directory!=null)delete(directory);}
    }

    private List<Line> ocr(Path image)throws Exception{
        Process process=new ProcessBuilder("tesseract",image.toString(),"stdout","-l","deu+eng","tsv").redirectErrorStream(false).start();
        String output=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
        if(!process.waitFor(60,TimeUnit.SECONDS)||process.exitValue()!=0)throw new IOException("OCR failed");
        Map<String,LineBuilder> grouped=new LinkedHashMap<>();
        for(String row:output.split("\\R")){String[] c=row.split("\\t",12);if(c.length<12||!c[0].matches("\\d+"))continue;String text=c[11].trim();if(text.isEmpty())continue;String key=c[1]+"-"+c[2]+"-"+c[3]+"-"+c[4];int left=number(c[6]),top=number(c[7]),width=number(c[8]),height=number(c[9]);grouped.computeIfAbsent(key,k->new LineBuilder()).add(text,left,top,width,height);}
        return grouped.values().stream().map(LineBuilder::build).toList();
    }

    private Match find(List<PageData> pages,String code,int startPage,int startLine){
        String marker=code.toUpperCase(Locale.ROOT);for(int p=startPage;p<pages.size();p++){List<Line> lines=pages.get(p).lines;for(int l=p==startPage?startLine:0;l<lines.size();l++){String text=lines.get(l).text.toUpperCase(Locale.ROOT).replace(" ","");if(text.contains(":"+marker+":")||text.matches(".*(?:^|[^A-Z0-9])"+Pattern.quote(marker)+"[:.].*"))return new Match(p,l,pages.get(p),lines.get(l));}}return null;
    }

    private byte[] crop(Match match)throws IOException{
        BufferedImage page=match.data.image;int y=Math.max(0,match.value.top-28);int nextTop=match.line+1<match.data.lines.size()?match.data.lines.get(match.line+1).top:y+300;int height=Math.max(150,Math.min(360,nextTop-y+120));height=Math.min(height,page.getHeight()-y);BufferedImage excerpt=page.getSubimage(0,y,page.getWidth(),height);ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(excerpt,"png",out);return out.toByteArray();
    }

    private byte[] placeholder(String message){try{BufferedImage image=new BufferedImage(900,170,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();g.setColor(new Color(248,250,252));g.fillRect(0,0,900,170);g.setColor(new Color(102,112,133));g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,20));g.drawString(message,28,88);g.dispose();ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);return out.toByteArray();}catch(IOException e){return new byte[0];}}
    private List<String> codes(String raw){List<String> result=new ArrayList<>();Matcher m=FIELD.matcher(raw==null?"":raw);while(m.find())result.add(m.group(1));return result;}
    private int pageNumber(Path path){Matcher m=Pattern.compile("(\\d+)").matcher(path.getFileName().toString());return m.find()?Integer.parseInt(m.group(1)):0;}
    private int number(String value){try{return Integer.parseInt(value);}catch(NumberFormatException e){return 0;}}
    private void delete(Path directory){try(var paths=Files.walk(directory)){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException ignored){}});}catch(IOException ignored){}}
    private record Line(String text,int left,int top,int width,int height){}
    private record PageData(BufferedImage image,List<Line> lines){}
    private record Match(int page,int line,PageData data,Line value){}
    private static class LineBuilder{StringBuilder text=new StringBuilder();int left=Integer.MAX_VALUE,top=Integer.MAX_VALUE,right,bottom;void add(String word,int x,int y,int w,int h){if(!text.isEmpty())text.append(' ');text.append(word);left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,x+w);bottom=Math.max(bottom,y+h);}Line build(){return new Line(text.toString(),left,top,right-left,bottom-top);}}
}
