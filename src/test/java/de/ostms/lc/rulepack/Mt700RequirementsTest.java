package de.ostms.lc.rulepack;

import static de.ostms.lc.rulepack.PackDefinition.Field;
import static org.assertj.core.api.Assertions.assertThat;

import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.lc.domain.LetterOfCredit;
import java.util.List;
import org.junit.jupiter.api.Test;

class Mt700RequirementsTest {
    private List<Mt700Requirements.Suggestion> detect(String raw) {
        var lc = new LetterOfCredit();
        lc.setRawMessage(raw);
        return Mt700Requirements.detect(lc);
    }

    private boolean has(List<Mt700Requirements.Suggestion> all, DocumentType type, Field field, String value) {
        return all.stream().anyMatch(s -> s.documentType() == type && s.field() == field && s.value().equals(value));
    }

    @Test void readsSignatureOriginalsAndIssuerPerDocumentType() {
        var s = detect(":46A:+SIGNED COMMERCIAL INVOICE IN 3 ORIGINALS\n+FULL SET 3/3 ORIGINAL BILLS OF LADING\n+CERTIFICATE OF ORIGIN ISSUED BY CHAMBER OF COMMERCE SHOWING GOODS ORIGIN\n:47A:+OTHER");
        assertThat(has(s, DocumentType.COMMERCIAL_INVOICE, Field.LC_SIGNATURE_REQUIRED, "true")).isTrue();
        assertThat(has(s, DocumentType.COMMERCIAL_INVOICE, Field.LC_REQUIRED_ORIGINAL_COUNT, "3")).isTrue();
        assertThat(has(s, DocumentType.BILL_OF_LADING, Field.LC_REQUIRED_ORIGINAL_COUNT, "3")).isTrue();
        assertThat(has(s, DocumentType.CERTIFICATE_OF_ORIGIN, Field.LC_REQUIRED_ISSUER, "CHAMBER OF COMMERCE")).isTrue();
        assertThat(s).noneMatch(x -> x.documentType() == DocumentType.BILL_OF_LADING && x.field() == Field.LC_SIGNATURE_REQUIRED);
    }

    @Test void differentCountsForOneTypeAreNotGuessed() {
        var s = detect(":46A:+COMMERCIAL INVOICE IN 3 ORIGINALS\n+COMMERCIAL INVOICE IN 2 ORIGINALS");
        assertThat(s).noneMatch(x -> x.field() == Field.LC_REQUIRED_ORIGINAL_COUNT);
    }

    @Test void numberWordsAndItemsWithoutKnownDocumentsAreHandled() {
        var s = detect(":46A:+PACKING LIST IN TWO ORIGINALS\n+SOMETHING ELSE IN 5 ORIGINALS");
        assertThat(has(s, DocumentType.PACKING_LIST, Field.LC_REQUIRED_ORIGINAL_COUNT, "2")).isTrue();
        assertThat(s).hasSize(1);
        assertThat(detect(":45A:ONLY GOODS")).isEmpty();
    }
}
