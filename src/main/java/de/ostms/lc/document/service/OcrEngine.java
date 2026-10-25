package de.ostms.lc.document.service;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** The recognition engine behind the extraction: image in, page text and positioned words out. The rest of the application only knows {@link OcrEvidence}. */
public interface OcrEngine {
 record Recognition(String text,List<OcrEvidence.Word> words){}
 /** Runs one external step (with timeout) on behalf of the engine; the extraction service supplies it so limits and logging stay in one place. */
 @FunctionalInterface interface StepRunner { void run(ProcessBuilder step,long seconds) throws IOException, InterruptedException; }

 /** Primary recognition of a page rendered by the profile. Throws when the engine produced no text file. */
 Recognition recognize(Path image,Path outputBase,String languages,ScanProfile.Binarization binarization,int page,StepRunner runner,long seconds) throws IOException, InterruptedException;
 /** Second attempt with a sparse-text layout on a corrected image; null when the engine's output is unusable (the primary result is then kept). */
 Recognition recognizeAlternative(Path image,Path outputBase,String languages,int page,StepRunner runner,long seconds) throws IOException, InterruptedException;
 /** Degrees the page has to be rotated to stand upright (0, 90, 180, 270); 0 when the orientation cannot be determined. */
 int detectRotation(Path image,Path outputBase,StepRunner runner,long seconds) throws InterruptedException;
 String version(Path workDirectory);
 /** Identifier stored with the evidence. */
 String method();
}
