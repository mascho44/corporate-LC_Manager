package de.ostms.lc.document.service;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Tesseract adapter: text from the txt output, positions and confidences from the tsv output. */
@Component
public class TesseractEngine implements OcrEngine {
 private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(TesseractEngine.class);
 private static final long MAX_OUTPUT_BYTES=10L*1024*1024;

 private static ProcessBuilder process(List<String> command){
  var builder=new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectErrorStream(true);
  builder.environment().put("OMP_THREAD_LIMIT","1");
  return builder;
 }

 @Override public Recognition recognize(Path image,Path outputBase,String languages,ScanProfile.Binarization binarization,int page,StepRunner runner,long seconds)throws IOException,InterruptedException{
  var command=new java.util.ArrayList<>(List.of("tesseract",image.toString(),outputBase.toString(),"-l",languages));
  if(binarization==ScanProfile.Binarization.SAUVOLA){command.add("-c");command.add("thresholding_method=2");}
  command.addAll(List.of("-c","tessedit_create_txt=1","-c","tessedit_create_tsv=1"));
  runner.run(process(command),seconds);
  Path textFile=Path.of(outputBase+".txt"),tsvFile=Path.of(outputBase+".tsv");
  try{
   if(!Files.isRegularFile(textFile))throw new IOException("OCR produced no text file");
   if(Files.size(textFile)>MAX_OUTPUT_BYTES)throw new IOException("OCR-Ausgabe zu groß");
   String text=Files.readString(textFile,StandardCharsets.UTF_8);
   if(Files.exists(tsvFile)&&Files.size(tsvFile)>MAX_OUTPUT_BYTES)throw new IOException("OCR-Ausgabe zu groß");
   var words=Files.exists(tsvFile)?OcrEvidence.parseTsv(Files.readString(tsvFile,StandardCharsets.UTF_8),page):List.<OcrEvidence.Word>of();
   return new Recognition(text,words);
  }finally{Files.deleteIfExists(textFile);Files.deleteIfExists(tsvFile);}
 }

 @Override public Recognition recognizeAlternative(Path image,Path outputBase,String languages,int page,StepRunner runner,long seconds)throws IOException,InterruptedException{
  runner.run(process(List.of("tesseract",image.toString(),outputBase.toString(),"-l",languages,"--psm","11","-c","tessedit_create_txt=1","-c","tessedit_create_tsv=1")),seconds);
  Path text=Path.of(outputBase+".txt"),tsv=Path.of(outputBase+".tsv");
  try{
   if(!Files.isRegularFile(text)||!Files.isRegularFile(tsv)||Files.size(text)>MAX_OUTPUT_BYTES||Files.size(tsv)>MAX_OUTPUT_BYTES)return null;
   return new Recognition(Files.readString(text,StandardCharsets.UTF_8),OcrEvidence.parseTsv(Files.readString(tsv,StandardCharsets.UTF_8),page));
  }finally{Files.deleteIfExists(text);Files.deleteIfExists(tsv);}
 }

 @Override public int detectRotation(Path image,Path outputBase,StepRunner runner,long seconds)throws InterruptedException{
  Path report=Path.of(outputBase+".osd");
  try{
   runner.run(process(List.of("tesseract",image.toString(),outputBase.toString(),"-l","osd","--psm","0")),seconds);
   if(Files.isRegularFile(report)&&Files.size(report)<=16_384)return ScanGeometry.orientation(Files.readString(report,StandardCharsets.UTF_8));
  }catch(IOException unavailable){log.debug("Scan orientation not available: reason={}",unavailable.getClass().getSimpleName());}
  finally{try{Files.deleteIfExists(report);}catch(IOException ignored){}}
  return 0;
 }

 @Override public String version(Path directory){
  try{Path file=directory.resolve("version.txt");BoundedProcess.run(new ProcessBuilder("tesseract","--version").redirectErrorStream(true).redirectOutput(file.toFile()),5);return Files.readAllLines(file).stream().findFirst().orElse("unknown");}catch(Exception e){return "unknown";}
 }
 @Override public String method(){return "TESSERACT_WORD_MIN_V2";}
}
