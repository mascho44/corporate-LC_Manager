package de.ostms.lc.document.service;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class OcrRasterScalingTest {
 @Test void positionsFromTheRecognitionRasterAreStoredInTheEvidenceRaster(){
  var scaled=DocumentExtractionService.toEvidenceRaster(List.of(new OcrEvidence.Word("Unterschrift",.91,2,300,450,150,30),new OcrEvidence.Word("i",.8,2,1,1,1,1)));
  assertThat(scaled.get(0)).isEqualTo(new OcrEvidence.Word("Unterschrift",.91,2,200,300,100,20));
  assertThat(scaled.get(1).width()).isGreaterThanOrEqualTo(1);assertThat(scaled.get(1).height()).isGreaterThanOrEqualTo(1);
  assertThat(DocumentExtractionService.OCR_DPI).isEqualTo(300);assertThat(DocumentExtractionService.EVIDENCE_DPI).isEqualTo(200);
 }
}
