package de.ostms.lc.document.service;
import java.util.List;
/** Retry hints only, not an accuracy estimate. Coordinates remain on the same raster. */
final class OcrQualityPolicy {
 private OcrQualityPolicy(){}
 static double score(List<OcrEvidence.Word> words){return words.stream().filter(w->w.confidence()!=null).mapToDouble(OcrEvidence.Word::confidence).average().orElse(0);}
 static boolean needsRetry(List<OcrEvidence.Word> words){return words.size()<4||score(words)<.65;}
 static boolean better(List<OcrEvidence.Word> candidate,List<OcrEvidence.Word> primary){
  return !candidate.isEmpty()&&candidate.size()>=primary.size()&&score(candidate)>score(primary);
 }
}
