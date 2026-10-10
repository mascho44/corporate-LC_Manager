package de.ostms.lc.swift;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class Mt700RequiredDocumentsTest {
 @Test void wrappedLinesJoinTheirCondition(){
  var result=Mt700Parser.splitConditions("+SIGNED COMMERCIAL INVOICE SHOWING CONTRACT\nNO.: 10001046/10003010 AND L/C NO.\n+PACKING LIST IN 3 COPIES\n-FULL SET OF ORIGINAL B/L\nMADE OUT TO ORDER");
  assertThat(result).containsExactly("SIGNED COMMERCIAL INVOICE SHOWING CONTRACT NO.: 10001046/10003010 AND L/C NO.","PACKING LIST IN 3 COPIES","FULL SET OF ORIGINAL B/L MADE OUT TO ORDER");
 }
 @Test void numberedConditionsAndUnmarkedFieldsKeepOldBehavior(){
  assertThat(Mt700Parser.splitConditions("1) INVOICE\nIN TRIPLICATE\n2) PACKING LIST")).containsExactly("INVOICE IN TRIPLICATE","PACKING LIST");
  assertThat(Mt700Parser.splitConditions("COMMERCIAL INVOICE\nPACKING LIST")).containsExactly("COMMERCIAL INVOICE","PACKING LIST");
  assertThat(Mt700Parser.splitConditions("INTRO TEXT\n+INVOICE")).containsExactly("INTRO TEXT","INVOICE");
 }
 @Test void doublePlusMarkersAreRemovedAndJoinWrappedLines(){
  assertThat(Mt700Parser.splitConditions("++SIGNED COMMERCIAL INVOICE IN 1 ORIGINAL INDICATING CONTRACT\nNO.: 1 AND L/C NO.\n++FULL SET OF ORIGINAL B/L\nTO ORDER")).containsExactly("SIGNED COMMERCIAL INVOICE IN 1 ORIGINAL INDICATING CONTRACT NO.: 1 AND L/C NO.","FULL SET OF ORIGINAL B/L TO ORDER");
 }
 @Test void standardTermsHaveTheirOwnColumnsAndUnknownTagsStayAdditional(){
  var lc=new Mt700Parser().parse(":20:REF\n:27:1/1\n:40A:IRREVOCABLE\n:41D:ANY BANK\nBY NEGOTIATION\n:42C:180 DAYS AFTER INVOICE DATE\n:42A:DEUTDEFFXXX\n:45A:GOODS\n:49:WITHOUT\n:53A:BANKAAAA\n:58A:BANKBBBB\n:71D:ALL CHARGES OUTSIDE ISSUING BANK FOR BENEFICIARY\n:78:SEND DOCS BY COURIER\n:99X:SOMETHING NEW\n:32B:EUR1,\n");
  assertThat(lc.getSequenceOfTotal()).isEqualTo("1/1");assertThat(lc.getFormOfCredit()).isEqualTo("IRREVOCABLE");assertThat(lc.getAvailableWith()).isEqualTo("ANY BANK\nBY NEGOTIATION");
  assertThat(lc.getDraftsAt()).isEqualTo("180 DAYS AFTER INVOICE DATE");assertThat(lc.getDraweeBank()).isEqualTo("DEUTDEFFXXX");assertThat(lc.getConfirmationInstructions()).isEqualTo("WITHOUT");
  assertThat(lc.getReimbursingBank()).isEqualTo("BANKAAAA");assertThat(lc.getConfirmationParty()).isEqualTo("BANKBBBB");assertThat(lc.getCharges()).contains("ALL CHARGES");assertThat(lc.getBankInstructions()).isEqualTo("SEND DOCS BY COURIER");
  assertThat(lc.getAdditionalFields().keySet()).containsExactly("99X - Feld 99X");
 }
}
