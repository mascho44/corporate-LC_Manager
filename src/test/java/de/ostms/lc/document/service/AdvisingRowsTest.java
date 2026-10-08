package de.ostms.lc.document.service;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class AdvisingRowsTest {
 @Test void noColonTableProducesUsefulFields(){String text="Akkreditivnummer LC-TEST\nUnsere Referenz OWN-TEST\nAkkreditiv über EUR 12.345,67\nGültig bis 30.11.2026 / GERMANY\nEröffnende Bank EXAMPLE BANK\nAuftraggeber IMPORTER";var rows=AdvisingRows.read(text);assertThat(rows).hasSize(6);assertThat(rows.get(1).target()).isEmpty();var result=AdvisingLetterExtractor.extract(text);assertThat(result.fields()).containsEntry("amount","12345.67").containsEntry("expiryDate","2026-11-30").containsEntry("expiryPlace","GERMANY").containsEntry("issuingBank","EXAMPLE BANK");}
 @Test void separateLinesAndAmbiguity(){var result=AdvisingLetterExtractor.extract("Akkreditivnummer\nFIRST\nAkkreditivnummer SECOND\nAkkreditiv über\nEUR 100,00");assertThat(result.fields()).doesNotContainKey("reference");assertThat(result.fields()).containsEntry("amount","100.00");}
}
