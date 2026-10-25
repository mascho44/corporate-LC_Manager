package de.ostms.lc.swift;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

public class Mt760AndFreeFormatTest {
public static final String MT760=":27:1/1\n:20:GTEE20267829\n:22A:ISSU\n:22D:DGAR\n:30:260930\n:40C:URDG\n:32B:EUR250000,50\n:31E:271231FRANKFURT\n:50:ACME GMBH\nHAMBURG\n:52A:ISSUBANKXXX\n:59:SUPPLIER LTD\nSHANGHAI\n:77C:WE HEREBY ISSUE THIS DEMAND GUARANTEE.\nPAYABLE ON FIRST DEMAND.\n";
 @Test void guaranteeBecomesADossierOfKindGuarantee(){
  var g=new Mt760Parser().parse(MT760);
  assertThat(g.getInstrumentType()).isEqualTo("GUARANTEE");
  assertThat(g.getReference()).isEqualTo("GTEE20267829");
  assertThat(g.getCurrency()).isEqualTo("EUR");assertThat(g.getAmount()).isEqualByComparingTo("250000.50");
  assertThat(g.getIssueDate()).hasToString("2026-09-30");assertThat(g.getExpiryDate()).hasToString("2027-12-31");assertThat(g.getExpiryPlace()).isEqualTo("FRANKFURT");
  assertThat(g.getApplicant()).startsWith("ACME");assertThat(g.getBeneficiary()).startsWith("SUPPLIER");assertThat(g.getIssuingBank()).isEqualTo("ISSUBANKXXX");
  assertThat(g.getAdditionalFields()).containsKey("Garantiebedingungen").containsEntry("Instrument","Garantie / Standby (MT760)")
   .containsKey("40C - Anwendbare Regeln").containsKey("22D - Garantieform").containsKey("27 - Sequenz / Gesamtzahl");
  assertThat(g.getAdditionalFields().get("Garantiebedingungen")).contains("DEMAND GUARANTEE").contains("FIRST DEMAND");
  assertThat(g.getRequiredDocuments()).isEmpty();
 }
 @Test void guaranteeNeedsAReferenceAndValidDates(){
  assertThatThrownBy(()->new Mt760Parser().parse(":27:1/1\n:40C:URDG\n")).hasMessageContaining(":20:");
  assertThatThrownBy(()->new Mt760Parser().parse(":20:X\n:30:999999\n")).hasMessageContaining(":30:");
  assertThatThrownBy(()->new Mt760Parser().parse(":20:X\n:32B:EURabc\n")).hasMessageContaining(":32B:");
 }
 @Test void guaranteeWithoutExpiryOrAmountStillParses(){
  var g=new Mt760Parser().parse(":20:G1\n:77U:TERMS\n");
  assertThat(g.getExpiryDate()).isNull();assertThat(g.getAmount()).isNull();assertThat(g.getAdditionalFields()).containsKey("Garantiebedingungen");
 }
 @Test void freeFormatMessageReadsReferencesAndNarrative(){
  var m=FreeFormatMessage.parse("MT799",":20:BANKREF9\n:21:LC2026000042\n:79:PLEASE ADVISE STATUS OF\nDOCUMENTS RECEIVED.\n");
  assertThat(m.reference()).isEqualTo("BANKREF9");assertThat(m.relatedReference()).isEqualTo("LC2026000042");
  assertThat(m.narrative()).isEqualTo("PLEASE ADVISE STATUS OF\nDOCUMENTS RECEIVED.");
 }
 @Test void freeFormatMessageRequiresReferenceAndNarrative(){
  assertThatThrownBy(()->FreeFormatMessage.parse("MT199",":79:TEXT")).hasMessageContaining(":20:");
  assertThatThrownBy(()->FreeFormatMessage.parse("MT199",":20:X\n")).hasMessageContaining(":79:");
  assertThat(FreeFormatMessage.parse("MT199",":20:X\n:79:HI").relatedReference()).isNull();
 }
}
