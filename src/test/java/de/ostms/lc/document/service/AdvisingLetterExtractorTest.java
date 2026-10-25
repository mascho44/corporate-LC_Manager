package de.ostms.lc.document.service;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class AdvisingLetterExtractorTest {
 @Test void labelledAdviceProducesTraceableProposal(){var result=AdvisingLetterExtractor.extract("LC number: LC-123\nApplicant: Importer\nBeneficiary: Exporter\nCredit amount: EUR 1.250,50\nExpiry date: 31.12.2026\nOur reference: BANK-123");assertThat(result.fields()).containsEntry("reference","LC-123").containsEntry("amount","1250.50").containsEntry("currency","EUR").containsEntry("expiryDate","2026-12-31");assertThat(result.fields()).doesNotContainKey("ownBankReference");assertThat(result.evidence().get("amount")).contains("EUR 1.250,50");}
 @Test void embeddedSwiftUsesYearMonthDay(){var result=AdvisingLetterExtractor.extract(":20:LC123\n:31D:261231GERMANY\n:32B:EUR1000,00\n:50:Importer\n:59:Exporter");assertThat(result.fields()).containsEntry("expiryDate","2026-12-31").containsEntry("amount","1000.00").containsEntry("reference","LC123");}
 @Test void ambiguousInvalidAndMissingValuesStayEmpty(){var result=AdvisingLetterExtractor.extract("LC number: ONE\nLC number: TWO\nCredit amount: EUR 1,000\nExpiry date: 31.02.2026");assertThat(result.fields()).doesNotContainKeys("reference","amount","expiryDate");assertThat(result.warnings()).isNotEmpty();assertThat(AdvisingLetterExtractor.extract(null).fields()).isEmpty();}
}
