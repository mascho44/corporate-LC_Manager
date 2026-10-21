package de.ostms.lc.rulepack;

import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.document.domain.LcDocument;
import de.ostms.lc.lc.domain.LetterOfCredit;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static de.ostms.lc.rulepack.PackDefinition.Field;
import static org.assertj.core.api.Assertions.assertThat;

class LcFactSuggesterTest {
    private static final String MESSAGE = ":40E:UCP LATEST VERSION\n:44E:HAMBURG\n:45A:2 UNITS MACHINE CIF JEBEL ALI INCOTERMS 2020\n:46A:+SIGNED COMMERCIAL INVOICE\n+INSURANCE CERTIFICATE\n";

    private static LetterOfCredit lc(String message) {
        var lc = new LetterOfCredit(); lc.setRawMessage(message); lc.setCurrency("USD");
        lc.setRequiredDocuments(List.of("SIGNED COMMERCIAL INVOICE", "INSURANCE CERTIFICATE"));
        return lc;
    }
    private static LcDocument invoice(String amount, String currency) {
        var d = new LcDocument(); d.setDocumentType(DocumentType.COMMERCIAL_INVOICE); d.setOriginalFilename("inv.pdf"); d.setAmount(new BigDecimal(amount)); d.setCurrency(currency); return d;
    }
    private static String value(List<DocumentFactSuggester.Suggestion> list, Field field) {
        return list.stream().filter(s -> s.field() == field).map(DocumentFactSuggester.Suggestion::value).findFirst().orElse(null);
    }

    @Test void ucpStandardsFillPresentationPeriodAndInsuranceCoverWhenTheMessageIsSilent() {
        var found = new LcFactSuggester().suggest(lc(MESSAGE));
        assertThat(value(found, Field.LC_PRESENTATION_PERIOD_DAYS)).isEqualTo("21");
        assertThat(value(found, Field.LC_INSURANCE_MIN_PERCENT)).isEqualTo("110");
    }

    @Test void statedValuesBeatTheStandards() {
        var found = new LcFactSuggester().suggest(lc(MESSAGE + ":48:15 DAYS AFTER SHIPMENT\n"));
        assertThat(value(found, Field.LC_PRESENTATION_PERIOD_DAYS)).isEqualTo("15");
    }

    @Test void noStandardsWithoutUcp() {
        var found = new LcFactSuggester().suggest(lc(MESSAGE.replace("UCP LATEST VERSION", "ISPR")));
        assertThat(value(found, Field.LC_PRESENTATION_PERIOD_DAYS)).isNull();
    }

    @Test void oneInvoiceGivesClaimedGrossAndCifAmounts() {
        var found = new LcFactSuggester().suggest(lc(MESSAGE), List.of(invoice("1000", "USD")));
        assertThat(value(found, Field.LC_CLAIMED_AMOUNT)).isEqualTo("1000.00");
        assertThat(value(found, Field.LC_GROSS_GOODS_AMOUNT)).isEqualTo("1000.00");
        assertThat(value(found, Field.LC_CIF_CIP_VALUE_AMOUNT)).isEqualTo("1000.00");
    }

    @Test void twoInvoicesOrOtherCurrencyGiveNoAmounts() {
        assertThat(value(new LcFactSuggester().suggest(lc(MESSAGE), List.of(invoice("1", "USD"), invoice("2", "USD"))), Field.LC_CLAIMED_AMOUNT)).isNull();
        assertThat(value(new LcFactSuggester().suggest(lc(MESSAGE), List.of(invoice("1", "EUR"))), Field.LC_CLAIMED_AMOUNT)).isNull();
    }
}
