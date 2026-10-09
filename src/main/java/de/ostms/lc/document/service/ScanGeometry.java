package de.ostms.lc.document.service;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

/** Bounded raster preprocessing; OCR rectangles are always mapped back to original pixels. */
final class ScanGeometry {
 private static final long MAX_PIXELS=20_000_000;
 private ScanGeometry(){}
 record Prepared(BufferedImage image,AffineTransform inverse,int originalWidth,int originalHeight,double degrees){
  List<OcrEvidence.Word> map(List<OcrEvidence.Word> words){
   return words.stream().map(word->{
    double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX;
    for(double x:new double[]{word.left(),(double)word.left()+word.width()})
     for(double y:new double[]{word.top(),(double)word.top()+word.height()}){
      var point=inverse.transform(new Point2D.Double(x,y),null);
      minX=Math.min(minX,point.getX());minY=Math.min(minY,point.getY());maxX=Math.max(maxX,point.getX());maxY=Math.max(maxY,point.getY());
     }
    int left=(int)Math.max(0,Math.min(originalWidth,Math.floor(minX))),top=(int)Math.max(0,Math.min(originalHeight,Math.floor(minY)));
    int right=(int)Math.max(left,Math.min(originalWidth,Math.ceil(maxX))),bottom=(int)Math.max(top,Math.min(originalHeight,Math.ceil(maxY)));
    return new OcrEvidence.Word(word.text(),word.confidence(),word.page(),left,top,right-left,bottom-top);
   }).toList();
  }
 }
 static BufferedImage read(Path path)throws IOException{
  try(var input=ImageIO.createImageInputStream(path.toFile())){
   if(input==null)throw new IOException("Invalid scan raster");
   var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IOException("Unsupported scan raster");
   var reader=readers.next();
   try{reader.setInput(input);validate(reader.getWidth(0),reader.getHeight(0));return reader.read(0);}
   finally{reader.dispose();}
  }
 }
 static void validate(int width,int height)throws IOException{
  if(width<1||height<1||(long)width*height>MAX_PIXELS)throw new IOException("Scan raster exceeds preprocessing limits");
 }
 static Prepared rotate(BufferedImage source,double degrees)throws IOException{
  validate(source.getWidth(),source.getHeight());
  if(!Double.isFinite(degrees)||Math.abs(degrees)>365)throw new IOException("Invalid scan angle");
  var rotation=AffineTransform.getRotateInstance(Math.toRadians(degrees));
  var bounds=rotation.createTransformedShape(new Rectangle(0,0,source.getWidth(),source.getHeight())).getBounds2D();
  int width=(int)Math.ceil(bounds.getWidth()-1e-8),height=(int)Math.ceil(bounds.getHeight()-1e-8);validate(width,height);
  var transform=AffineTransform.getTranslateInstance(-bounds.getMinX(),-bounds.getMinY());transform.concatenate(rotation);
  var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
  var graphics=image.createGraphics();
  try{graphics.setColor(Color.WHITE);graphics.fillRect(0,0,width,height);graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);graphics.drawImage(source,transform,null);}
  finally{graphics.dispose();}
  try{return new Prepared(image,transform.createInverse(),source.getWidth(),source.getHeight(),degrees);}
  catch(NoninvertibleTransformException impossible){image.flush();throw new IOException("Invalid scan transform",impossible);}
 }
 /** Projection-profile heuristic for small skew only, with bounded sample size and gain threshold. */
 static double deskewAngle(BufferedImage image){
  int step=Math.max(1,(Math.max(image.getWidth(),image.getHeight())+599)/600);
  var points=new ArrayList<Point>();
  for(int y=0;y<image.getHeight();y+=step)for(int x=0;x<image.getWidth();x+=step){
   int rgb=image.getRGB(x,y);if((((rgb>>16)&255)+((rgb>>8)&255)+(rgb&255))<300)points.add(new Point(x/step,y/step));
  }
  if(points.size()<100||points.size()>120_000)return 0;
  double baseline=projection(points,0),best=baseline,angle=0;
  for(int half=-10;half<=10;half++){double candidate=half*.5,score=projection(points,candidate);if(score>best){best=score;angle=candidate;}}
  return Math.abs(angle)>=.5&&best>baseline*1.15?angle:0;
 }
 private static double projection(List<Point> points,double degrees){
  double radians=Math.toRadians(degrees),sin=Math.sin(radians),cos=Math.cos(radians);
  int[] rows=new int[1400];
  for(var p:points){int row=(int)Math.round(p.x*sin+p.y*cos)+650;if(row>=0&&row<rows.length)rows[row]++;}
  double sum=0;for(int count:rows)sum+=(double)count*count;return sum;
 }
 static int orientation(String report){
  if(report==null||report.length()>16_384)return 0;
  var confidence=java.util.regex.Pattern.compile("(?m)^Orientation confidence:\\s*([0-9]+(?:\\.[0-9]+)?)\\s*$").matcher(report);
  var rotate=java.util.regex.Pattern.compile("(?m)^Rotate:\\s*(0|90|180|270)\\s*$").matcher(report);
  if(!confidence.find()||!rotate.find()||Double.parseDouble(confidence.group(1))<5)return 0;
  return Integer.parseInt(rotate.group(1));
 }
}
