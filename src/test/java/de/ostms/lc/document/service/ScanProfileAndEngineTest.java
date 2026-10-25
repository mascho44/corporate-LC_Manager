package de.ostms.lc.document.service;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class ScanProfileAndEngineTest {
 @Test void profilesHaveStableIdsAndUnknownOnesFallBackToStandard(){
  assertThat(ScanProfile.all()).extracting(ScanProfile::id).containsExactly("STANDARD","PROFI_SCANNER","SCHLECHTER_SCAN");
  assertThat(ScanProfile.byId(null)).isSameAs(ScanProfile.STANDARD);assertThat(ScanProfile.byId("GONE")).isSameAs(ScanProfile.STANDARD);
  assertThat(ScanProfile.byId("PROFI_SCANNER")).isSameAs(ScanProfile.PROFI_SCANNER);
  assertThat(ScanProfile.exists("STANDARD")).isTrue();assertThat(ScanProfile.exists("x")).isFalse();
  assertThat(ScanProfile.STANDARD.renderDpi()).isEqualTo(300);assertThat(ScanProfile.STANDARD.binarization()).isEqualTo(ScanProfile.Binarization.SAUVOLA);
  assertThat(ScanProfile.EVIDENCE_DPI).isEqualTo(200);
 }
 @Test void standardKeepsTheOldEvidenceMethodOthersAreMarked(){
  assertThat(ScanProfile.STANDARD.methodSuffix()).isEmpty();
  assertThat(ScanProfile.PROFI_SCANNER.methodSuffix()).isEqualTo("+PROFI_SCANNER");
  assertThat(new TesseractEngine().method()+ScanProfile.STANDARD.methodSuffix()).isEqualTo("TESSERACT_WORD_MIN_V2");
 }

 /** Fake step runner: records the command and writes the files Tesseract would write. */
 static class Runner implements OcrEngine.StepRunner {
  final List<List<String>> commands=new ArrayList<>();final List<String> environments=new ArrayList<>();
  String text="Hello world",tsv="5\t1\t1\t1\t1\t1\t10\t10\t50\t20\t95\tHello\n",osd=null;boolean writeText=true,writeTsv=true,fail=false;
  @Override public void run(ProcessBuilder step,long seconds)throws IOException{
   commands.add(step.command());environments.add(step.environment().get("OMP_THREAD_LIMIT"));
   if(fail)throw new IOException("tool failed");
   var args=step.command();String base=args.get(2);
   if(args.contains("osd")){if(osd!=null)Files.writeString(Path.of(base+".osd"),osd);return;}
   if(writeText)Files.writeString(Path.of(base+".txt"),text);
   if(writeTsv)Files.writeString(Path.of(base+".tsv"),tsv);
  }
 }
 private static Path dir()throws IOException{return Files.createTempDirectory("engine-test");}

 @Test void sauvolaIsOnlyRequestedByProfilesThatAskForIt()throws Exception{
  var engine=new TesseractEngine();var runner=new Runner();var d=dir();
  var result=engine.recognize(d.resolve("p.png"),d.resolve("out"),"deu+eng",ScanProfile.Binarization.SAUVOLA,3,runner,30);
  var sauvola=runner.commands.get(0);
  assertThat(sauvola).containsSubsequence("tesseract",d.resolve("p.png").toString(),d.resolve("out").toString(),"-l","deu+eng","-c","thresholding_method=2","-c","tessedit_create_txt=1","-c","tessedit_create_tsv=1");
  assertThat(result.text()).isEqualTo("Hello world");assertThat(result.words()).hasSize(1);assertThat(result.words().get(0).page()).isEqualTo(3);
  engine.recognize(d.resolve("p.png"),d.resolve("out2"),"deu",ScanProfile.Binarization.NONE,1,runner,30);
  assertThat(runner.commands.get(1)).doesNotContain("thresholding_method=2").contains("deu");
  assertThat(runner.environments).containsOnly("1");
  assertThat(d.resolve("out.txt")).doesNotExist();assertThat(d.resolve("out.tsv")).doesNotExist();
 }
 @Test void aMissingTextFileIsAnErrorButMissingPositionsAreNot()throws Exception{
  var engine=new TesseractEngine();var runner=new Runner();var d=dir();
  runner.writeText=false;
  assertThatThrownBy(()->engine.recognize(d.resolve("p.png"),d.resolve("a"),"deu",ScanProfile.Binarization.SAUVOLA,1,runner,30)).isInstanceOf(IOException.class).hasMessageContaining("no text file");
  runner.writeText=true;runner.writeTsv=false;
  assertThat(engine.recognize(d.resolve("p.png"),d.resolve("b"),"deu",ScanProfile.Binarization.SAUVOLA,1,runner,30).words()).isEmpty();
 }
 @Test void theAlternativeAttemptUsesSparseLayoutAndReturnsNullWhenUnusable()throws Exception{
  var engine=new TesseractEngine();var runner=new Runner();var d=dir();
  var alt=engine.recognizeAlternative(d.resolve("c.png"),d.resolve("alt"),"deu+eng",2,runner,30);
  assertThat(runner.commands.get(0)).contains("--psm","11").doesNotContain("thresholding_method=2");assertThat(alt.words().get(0).page()).isEqualTo(2);
  runner.writeTsv=false;assertThat(engine.recognizeAlternative(d.resolve("c.png"),d.resolve("alt2"),"deu",2,runner,30)).isNull();
 }
 @Test void rotationComesFromTheOsdReportAndDefaultsToZero()throws Exception{
  var engine=new TesseractEngine();var runner=new Runner();var d=dir();
  runner.osd="Page number: 0\nOrientation in degrees: 90\nRotate: 90\nOrientation confidence: 12.00\n";
  assertThat(engine.detectRotation(d.resolve("p.png"),d.resolve("o"),runner,10)).isEqualTo(90);
  assertThat(runner.commands.get(0)).containsSubsequence("-l","osd","--psm","0");
  runner.osd=null;assertThat(engine.detectRotation(d.resolve("p.png"),d.resolve("o2"),runner,10)).isZero();
  runner.fail=true;assertThat(engine.detectRotation(d.resolve("p.png"),d.resolve("o3"),runner,10)).isZero();
 }
}
