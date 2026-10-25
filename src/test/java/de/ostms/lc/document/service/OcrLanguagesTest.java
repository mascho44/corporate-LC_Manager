package de.ostms.lc.document.service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OcrLanguagesTest {
 @Test void defaultsWhenUnsetOrInvalid(){
  assertEquals("deu+eng",DocumentExtractionService.ocrLanguages(null));
  assertEquals("deu+eng",DocumentExtractionService.ocrLanguages(""));
  assertEquals("deu+eng",DocumentExtractionService.ocrLanguages("deu;rm -rf"));
 }
 @Test void acceptsConfiguredCombination(){assertEquals("deu+eng+chi_sim",DocumentExtractionService.ocrLanguages(" deu+eng+chi_sim "));}
}
