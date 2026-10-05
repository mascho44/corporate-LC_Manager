package de.corporate.lc.imports.service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
@Component
public class ExtractionConfidencePolicy {
 private final double threshold;
 public ExtractionConfidencePolicy(@Value("${lc.extraction.confidence-threshold:0.8}") double threshold){if(!Double.isFinite(threshold)||threshold<0||threshold>1)throw new IllegalArgumentException("Konfidenzschwelle muss zwischen 0 und 1 liegen");this.threshold=threshold;}
 public double threshold(){return threshold;}
 public boolean uncertain(double score){return score<threshold;}
}
