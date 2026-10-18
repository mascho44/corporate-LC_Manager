package de.ostms.lc.check.service;

import static de.ostms.lc.check.service.AdditionalConditionsCheck.Kind.*;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AdditionalConditionsCheckTest {
    @Test void splitsNumberedItemsAndRecognisesTheCheckableOnes() {
        var items = AdditionalConditionsCheck.parse("1. ALL DOCUMENTS MUST SHOW THIS DOCUMENTARY CREDIT NUMBER 2. ALL DOCUMENTS MUST BE ISSUED IN ENGLISH 3. INSPECTION CERTIFICATE REQUIRED");
        assertThat(items).extracting(AdditionalConditionsCheck.Item::kind).containsExactly(CREDIT_NUMBER_ON_ALL_DOCUMENTS, ENGLISH_ON_ALL_DOCUMENTS, OTHER);
        assertThat(items.get(2).text()).isEqualTo("INSPECTION CERTIFICATE REQUIRED");
    }

    @Test void plusMarkersAndPlainTextWork() {
        assertThat(AdditionalConditionsCheck.parse("+ALL DOCUMENTS MUST QUOTE L/C NUMBER\n+ALL DOCUMENTS TO BE PREPARED IN ENGLISH LANGUAGE"))
            .extracting(AdditionalConditionsCheck.Item::kind).containsExactly(CREDIT_NUMBER_ON_ALL_DOCUMENTS, ENGLISH_ON_ALL_DOCUMENTS);
        assertThat(AdditionalConditionsCheck.parse("DOCUMENTS MUST BE PRESENTED WITHIN 21 DAYS")).extracting(AdditionalConditionsCheck.Item::kind).containsExactly(OTHER);
        assertThat(AdditionalConditionsCheck.parse("  ")).isEmpty();
    }

    @Test void referenceIsFoundIgnoringSeparators() {
        assertThat(AdditionalConditionsCheck.showsReference("Our ref LC 2026 / 003 date", "LC-2026-003")).isTrue();
        assertThat(AdditionalConditionsCheck.showsReference("Our ref LC-2026-004", "LC-2026-003")).isFalse();
        assertThat(AdditionalConditionsCheck.showsReference("anything", "AB")).isFalse();
    }

    @Test void recognisesEnglishGermanAndShortText() {
        String english = "Commercial invoice for the goods shipped by the seller to the buyer. The total amount of this invoice is payable at sight and the payment shall be made by bank transfer. Please note that the goods are delivered as agreed and the date of shipment is shown above.";
        String german = "Rechnung für die Ware, die der Verkäufer an den Käufer liefert. Der Betrag dieser Rechnung ist bei Sicht zahlbar und die Zahlung erfolgt per Überweisung. Bitte beachten Sie, dass die Lieferung wie vereinbart erfolgt und das Datum der Lieferung oben steht.";
        assertThat(AdditionalConditionsCheck.guess(english).language()).isEqualTo(AdditionalConditionsCheck.Language.ENGLISH);
        var g = AdditionalConditionsCheck.guess(german);
        assertThat(g.language()).isEqualTo(AdditionalConditionsCheck.Language.OTHER);
        assertThat(g.detected()).isEqualTo("Deutsch");
        assertThat(AdditionalConditionsCheck.guess("INVOICE 123").language()).isEqualTo(AdditionalConditionsCheck.Language.UNSURE);
        assertThat(AdditionalConditionsCheck.guess(null).language()).isEqualTo(AdditionalConditionsCheck.Language.UNSURE);
    }
}
