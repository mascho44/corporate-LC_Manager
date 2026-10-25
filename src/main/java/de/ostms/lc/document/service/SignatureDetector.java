package de.ostms.lc.document.service;

import java.awt.image.BufferedImage;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Looks for handwriting-like ink next to printed signature captions. Text that OCR read with confidence
 * is masked out, so printed words never count as ink; rules, speckles and solid blocks are rejected.
 * The result is a hint for a human reviewer, never proof that a document is signed or authentic.
 */
public final class SignatureDetector {
 private SignatureDetector(){}
 public record Box(int left,int top,int width,int height){
  boolean intersects(Box other){return left<other.left+other.width&&other.left<left+width&&top<other.top+other.height&&other.top<top+height;}
  int overlapArea(Box other){int w=Math.min(left+width,other.left+other.width)-Math.max(left,other.left),h=Math.min(top+height,other.top+other.height)-Math.max(top,other.top);return w>0&&h>0?w*h:0;}
 }
 public record Anchor(String text,Box box,boolean inkFound,Box ink){}
 public record PageResult(int page,List<Anchor> anchors,int freeInkClusters,List<Box> clusters){}

 private static final Pattern CAPTION=Pattern.compile("(?i)^(?:signatures?|signatory|signatur|unterschrift(?:en)?|unterzeichner)$");
 private static final int INK_LEVEL=140,MASK_MARGIN=4,MERGE_GAP=22;

 public static PageResult analyze(BufferedImage image,List<OcrEvidence.Word> words,int page){
  int w=image.getWidth(),h=image.getHeight();
  boolean[] ink=new boolean[w*h];
  for(int y=0;y<h;y++)for(int x=0;x<w;x++){int rgb=image.getRGB(x,y);int gray=((rgb>>16&255)*30+(rgb>>8&255)*59+(rgb&255)*11)/100;ink[y*w+x]=gray<INK_LEVEL;}
  // Confidently read (or digital) words are printed text, not handwriting.
  for(var word:words){
   if(word.confidence()!=null&&word.confidence()<.65)continue;
   int x0=Math.max(0,word.left()-MASK_MARGIN),x1=Math.min(w,word.left()+word.width()+MASK_MARGIN),y0=Math.max(0,word.top()-MASK_MARGIN),y1=Math.min(h,word.top()+word.height()+MASK_MARGIN);
   for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++)ink[y*w+x]=false;
  }
  var clusters=clusters(ink,w,h);
  var anchors=new ArrayList<Anchor>();Set<Integer> used=new HashSet<>();
  for(int index=0;index<words.size();index++){
   var word=words.get(index);
   if(word.confidence()!=null&&word.confidence()<.5)continue;
   String token=word.text().replaceAll("[^\\p{L}]","");
   boolean phrase=token.equalsIgnoreCase("signed")&&index+1<words.size()&&words.get(index+1).text().matches("(?i)^(?:for|by)\\W*$");
   if(!CAPTION.matcher(token).matches()&&!phrase)continue;
   Box caption=new Box(word.left(),word.top(),word.width(),word.height());
   var above=new Box(Math.max(0,caption.left()-60),Math.max(0,caption.top()-260),caption.width()+460,256);
   var beside=new Box(caption.left()+caption.width()+8,Math.max(0,caption.top()-40),450,caption.height()+70);
   Box best=null;int bestScore=0;
   for(int i=0;i<clusters.size();i++){
    Box c=clusters.get(i);int score=Math.max(c.overlapArea(above),c.overlapArea(beside));
    if(score*3>=c.width()*c.height()&&score>bestScore){best=c;bestScore=score;used.add(i);}
   }
   anchors.add(new Anchor(word.text(),caption,best!=null,best));
  }
  int free=0;
  for(int i=0;i<clusters.size();i++)if(!used.contains(i)&&clusters.get(i).top()>h*0.55)free++;
  return new PageResult(page,List.copyOf(anchors),free,List.copyOf(clusters));
 }

 /** Connected ink strokes, merged into signature-sized clusters; implausible shapes are dropped. */
 static List<Box> clusters(boolean[] ink,int w,int h){
  int[] label=new int[w*h];List<int[]> parts=new ArrayList<>(); // {minX,minY,maxX,maxY,area}
  int[] stack=new int[1024];
  for(int start=0;start<ink.length;start++){
   if(!ink[start]||label[start]!=0)continue;
   int id=parts.size()+1,sp=0,minX=w,minY=h,maxX=0,maxY=0,area=0;
   stack[sp++]=start;label[start]=id;
   while(sp>0){
    int p=stack[--sp],x=p%w,y=p/w;area++;
    if(x<minX)minX=x;if(x>maxX)maxX=x;if(y<minY)minY=y;if(y>maxY)maxY=y;
    for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){
     int nx=x+dx,ny=y+dy;if(nx<0||ny<0||nx>=w||ny>=h)continue;int q=ny*w+nx;
     if(ink[q]&&label[q]==0){label[q]=id;if(sp==stack.length)stack=Arrays.copyOf(stack,sp*2);stack[sp++]=q;}
    }
   }
   parts.add(new int[]{minX,minY,maxX,maxY,area});
  }
  // Merge parts whose boxes are close, repeatedly (signature strokes are often separate pieces).
  List<int[]> merged=new ArrayList<>(parts.stream().filter(p->p[4]>=12).toList());
  boolean changed=true;
  while(changed){
   changed=false;
   outer:for(int i=0;i<merged.size();i++)for(int j=i+1;j<merged.size();j++){
    int[] a=merged.get(i),b=merged.get(j);
    if(a[0]-MERGE_GAP<=b[2]&&b[0]-MERGE_GAP<=a[2]&&a[1]-MERGE_GAP<=b[3]&&b[1]-MERGE_GAP<=a[3]){
     merged.set(i,new int[]{Math.min(a[0],b[0]),Math.min(a[1],b[1]),Math.max(a[2],b[2]),Math.max(a[3],b[3]),a[4]+b[4]});merged.remove(j);changed=true;break outer;
    }
   }
  }
  var result=new ArrayList<Box>();
  for(int[] c:merged){
   int bw=c[2]-c[0]+1,bh=c[3]-c[1]+1;double fill=c[4]/(double)(bw*bh);
   boolean plausible=bw>=50&&bh>=18&&c[4]>=250&&fill>=.03&&fill<=.55&&!(bh<14&&bw>bh*12)&&bw<=900&&bh<=500;
   if(plausible)result.add(new Box(c[0],c[1],bw,bh));
  }
  return result;
 }
}
