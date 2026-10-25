package de.ostms.lc.document.service;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class OcrEvidenceSequenceTest {
 private static OcrEvidence evidence(Object... tokens){
  var words=new ArrayList<OcrEvidence.Word>();int x=0;
  for(int i=0;i<tokens.length;i+=2){words.add(new OcrEvidence.Word((String)tokens[i],(Double)tokens[i+1],1,x,10,40,10));x+=50;}
  return new OcrEvidence("t","m",200,0.8,words);
 }
 @Test void repeatedValuesAreLocatedInDocumentOrder(){
  var e=evidence(":20:",.99,"ABC123",.99,":21:",.99,"ABC123",.5);
  var a=e.assessAll(List.of("ABC123","ABC123"),0.8);
  assertThat(a.get(0).status()).isEqualTo("MEASURED");assertThat(a.get(1).status()).isEqualTo("REVIEW");
 }
 @Test void linesDroppedByTheNormaliserDoNotBreakTheMatch(){
  var e=evidence(":45A:",.99,"1000",.97,"PCS",.96,"Page",.9,"2",.9,"WIDGETS",.95,"CIF",.99);
  var a=e.assessAll(List.of("1000 PCS WIDGETS CIF"),0.8).get(0);
  assertThat(a.status()).isEqualTo("MEASURED");assertThat(a.words()).extracting(OcrEvidence.Word::text).containsExactly("1000","PCS","WIDGETS","CIF");
 }
 @Test void aLowConfidenceWordInsideFlagsTheWholeField(){
  var e=evidence(":59:",.99,"ACME",.99,"GMBH",.4,"BERLIN",.99);
  assertThat(e.assessAll(List.of("ACME GMBH BERLIN"),0.8).get(0).status()).isEqualTo("REVIEW");
 }
 @Test void unrelatedValuesStayUnavailable(){
  var e=evidence(":20:",.99,"ABC123",.99);
  assertThat(e.assessAll(List.of("COMPLETELY DIFFERENT TEXT"),0.8).get(0).status()).isEqualTo("UNAVAILABLE");
 }
 @Test void digitalTextLayerIsNotApplicableInsteadOfUnavailable(){
  var e=new OcrEvidence("PDFBOX","PDF_TEXT_POSITIONS",200,0.8,List.of(new OcrEvidence.Word("ABC",null,1,0,0,10,10)));
  assertThat(e.assessAll(List.of("ABC","XYZ"),0.8)).extracting(OcrEvidence.Assessment::status).containsOnly("NOT_APPLICABLE");
 }
}
