package de.ostms.lc.document.service;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class InsuranceNumberDetectorTest {
 @Test void labelledPolicyAndCertificateNumbersAreRecognised(){
  for(String text:new String[]{"Certificate No.: IC-2026-004711","Policy Number: IC-2026-004711","Insurance Certificate No IC-2026-004711","CERTIFICATE OF INSURANCE NO.\nIC-2026-004711","Policennummer: IC-2026-004711","Zertifikatsnummer IC-2026-004711","Versicherungsschein-Nr. IC-2026-004711"})
   assertThat(InsuranceNumberDetector.detect(text)).as(text).contains("IC-2026-004711");
 }
 @Test void headingsConflictsAndUnlabelledNumbersAreIgnored(){
  for(String text:new String[]{"Insurance certificate","Policy conditions apply","Certificate No.: AB-1234\nPolicy No.: CD-5678","IC-2026-004711","Certificate no. ABCDEFG",null})
   assertThat(InsuranceNumberDetector.detect(text)).as(String.valueOf(text)).isEmpty();
 }
 @Test void theSameNumberTwiceIsStillUnambiguous(){
  assertThat(InsuranceNumberDetector.detect("Certificate No.: IC-2026-004711\nPolicy No.: IC-2026-004711")).contains("IC-2026-004711");
 }
}
