package de.ostms.lc.document.service;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class AdvisingLetterExtractorTest {
 @Test void labelledAdviceProducesTraceableProposal(){var result=AdvisingLetterExtractor.extract("LC number: LC-123\nApplicant: Importer\nBeneficiary: Exporter\nCredit amount: EUR 1.250,50\nExpiry date: 31.12.2026\nOur reference: BANK-123");assertThat(result.fields()).containsEntry("reference","LC-123").containsEntry("amount","1250.50").containsEntry("currency","EUR").containsEntry("expiryDate","2026-12-31");assertThat(result.fields()).doesNotContainKey("ownBankReference");assertThat(result.evidence().get("amount")).contains("EUR 1.250,50");}
 @Test void embeddedSwiftUsesYearMonthDay(){var result=AdvisingLetterExtractor.extract(":20:LC123\n:31D:261231GERMANY\n:32B:EUR1000,00\n:50:Importer\n:59:Exporter");assertThat(result.fields()).containsEntry("expiryDate","2026-12-31").containsEntry("amount","1000.00").containsEntry("reference","LC123");}
 @Test void ambiguousInvalidAndMissingValuesStayEmpty(){var result=AdvisingLetterExtractor.extract("LC number: ONE\nLC number: TWO\nCredit amount: EUR 1,000\nExpiry date: 31.02.2026");assertThat(result.fields()).doesNotContainKeys("reference","amount","expiryDate");assertThat(result.warnings()).isNotEmpty();assertThat(AdvisingLetterExtractor.extract(null).fields()).isEmpty();}
 @Test void tableLayoutAdviceWithoutColonsIsRead(){
  String letter="Beispielbank Süd GmbH\nMusterweg 1\n70000 Musterstadt\nMusterstadt, 08. Juni 2026\nBeispielsparkasse Nord 70001 Nordstadt\nTestkunde Handel GmbH & Co.KG\nHauptstr. 5\n70002 Kundenstadt\nAbwicklung im Auftrag der Sparkasse\nDokumentenakkreditiv – Avisierung\nAkkreditivnummer ABC123456789\nUnsere Referenz XY000AB0000001\nAkkreditiv über EUR 1.945.400,00\nGültig bis 30.11.2026 / GERMANY\nEröffnende Bank EXAMPLE TRADE BANK CORP\nAuftraggeber SAMPLE MACHINERY CO.,LTD.\nSehr geehrte Damen und Herren,\nzu Ihren Gunsten wurde das Akkreditiv eröffnet.";
  var result=AdvisingLetterExtractor.extract(letter);
  assertThat(result.fields()).containsEntry("reference","ABC123456789").containsEntry("amount","1945400.00").containsEntry("currency","EUR")
   .containsEntry("expiryDate","2026-11-30").containsEntry("expiryPlace","GERMANY").containsEntry("issuingBank","EXAMPLE TRADE BANK CORP")
   .containsEntry("applicant","SAMPLE MACHINERY CO.,LTD.").containsEntry("beneficiary","Testkunde Handel GmbH & Co.KG");
  assertThat(result.warnings()).anyMatch(w->w.contains("aus der Anschrift"));
  assertThat(result.fields()).doesNotContainKey("ownBankReference");
 }
 @Test void addresseeIsNotTakenWithoutADateLineOrFromBankOrStreetLines(){
  assertThat(AdvisingLetterExtractor.addressee("Irgendein Text\nOhne Datum")).isNull();
  assertThat(AdvisingLetterExtractor.addressee("Ort, 1. Mai 2026\nBeispielbank AG\nMusterstr. 3\n12345 Stadt\nDokumentenakkreditiv")).isNull();
  assertThat(AdvisingLetterExtractor.addressee("Ort, 01.05.2026\nFirma Beispiel GmbH\nMusterstr. 3")).isEqualTo("Firma Beispiel GmbH");
 }
 @Test void otherWordingsAndSpellingsOfTheSameLabelsAreRead(){
  var result=AdvisingLetterExtractor.extract("Ort, 02.03.2026\nKunde AG\nL/C-Nr. DEF-987\nAkkreditivbetrag USD 20.000,00\nVerfall 15.12.2026 / CHINA\nAusstellende Bank Sample Bank Ltd\nAkkreditivauftraggeber Sample Importer Inc.");
  assertThat(result.fields()).containsEntry("reference","DEF-987").containsEntry("amount","20000.00").containsEntry("currency","USD").containsEntry("expiryDate","2026-12-15")
   .containsEntry("issuingBank","Sample Bank Ltd").containsEntry("applicant","Sample Importer Inc.");
 }
}
