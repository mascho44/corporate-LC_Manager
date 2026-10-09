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
}
