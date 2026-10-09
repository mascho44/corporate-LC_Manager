package de.ostms.lc.document.service;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.*;
import java.util.*;
import java.io.IOException;
/** Digital PDF word boxes use the same 200-DPI coordinate scale as OCR. */
final class PdfWordPositions {
 static List<OcrEvidence.Word> read(byte[] content)throws IOException{
  var words=new ArrayList<OcrEvidence.Word>();
  try(var pdf=Loader.loadPDF(content)){
   PdfProcessingSafety.validate(pdf);
   var stripper=new PDFTextStripper(){
    @Override protected void writeString(String text,List<TextPosition> positions){
     StringBuilder token=new StringBuilder();float left=0,top=0,right=0,bottom=0,previousRight=0;
     for(var p:positions){
      String s=p.getUnicode();boolean space=s.isBlank(),gap=token.length()>0&&p.getXDirAdj()-previousRight>Math.max(2,p.getWidthOfSpace()*.6f);
      if(space||gap){if(token.length()>0)add(token.toString(),left,top,right,bottom,getCurrentPageNo());token.setLength(0);}
      if(space)continue;
      if(token.length()==0){left=p.getXDirAdj();top=p.getYDirAdj()-p.getHeightDir();right=left;bottom=p.getYDirAdj();}
      token.append(s);right=Math.max(right,p.getXDirAdj()+p.getWidthDirAdj());bottom=Math.max(bottom,p.getYDirAdj());previousRight=p.getXDirAdj()+p.getWidthDirAdj();
     }
     if(token.length()>0)add(token.toString(),left,top,right,bottom,getCurrentPageNo());
    }
    private void add(String text,float left,float top,float right,float bottom,int page){
     if(words.size()>=100_000||text.length()>255)return;double scale=200d/72;
     words.add(new OcrEvidence.Word(text,null,page,Math.max(0,(int)(left*scale)),Math.max(0,(int)(top*scale)),Math.max(1,(int)((right-left)*scale)),Math.max(1,(int)((bottom-top)*scale))));
    }
   };
   stripper.setSortByPosition(true);stripper.getText(pdf);
  }
  return List.copyOf(words);
 }
}
