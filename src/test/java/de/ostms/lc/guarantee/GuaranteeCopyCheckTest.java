package de.ostms.lc.guarantee;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GuaranteeCopyCheckTest {
 private static final GuaranteeCopyCheck.Expected EXPECTED=new GuaranteeCopyCheck.Expected("EUR",new BigDecimal("210000.00"),"Stadtwerke Musterhafen AG","Nordkueste Bank AG",LocalDate.of(2026,12,1),"NM-AVAL-2602");
 private static Map<String,String> results(String text){return GuaranteeCopyCheck.compare(EXPECTED,text).stream().collect(Collectors.toMap(GuaranteeCopyCheck.Item::field,GuaranteeCopyCheck.Item::result));}

 @Test void matchingGermanText(){
  var r=results("Nordkueste Bank AG\nGarantie zugunsten Stadtwerke Musterhafen AG\nBetrag: EUR 210.000,00\nGueltig bis 01.12.2026");
  assertEquals("OK",r.get("currency"));assertEquals("OK",r.get("amount"));assertEquals("OK",r.get("beneficiary"));assertEquals("OK",r.get("bank"));assertEquals("OK",r.get("expiry"));
 }
 @Test void matchingEnglishAndSwiftText(){
  var r=results(":20:X\n:32B:EUR210000,00\n:31E:261201\nBeneficiary: Stadtwerke Musterhafen AG\nIssuer: NORDKUESTE BANK AG\nvalid until 1 December 2026");
  assertEquals("OK",r.get("amount"));assertEquals("OK",r.get("expiry"));assertEquals("OK",r.get("beneficiary"));assertEquals("OK",r.get("bank"));
 }
 @Test void deviationsAreFlagged(){
  var r=results("Nordkueste Bank AG\nzugunsten Stadtwerke Musterhafen AG\nBetrag: USD 200.000,00\nGueltig bis 01.11.2026");
  assertEquals("DEVIATION",r.get("currency"));assertEquals("DEVIATION",r.get("amount"));assertEquals("DEVIATION",r.get("expiry"));assertEquals("OK",r.get("beneficiary"));
 }
 @Test void emptyTextIsUnknownNotDeviation(){
  assertEquals("UNKNOWN",results("").get("amount"));assertEquals("UNKNOWN",results(null).get("expiry"));
 }
}
