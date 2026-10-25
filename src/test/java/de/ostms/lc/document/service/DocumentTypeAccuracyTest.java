package de.ostms.lc.document.service;

import de.ostms.lc.document.domain.DocumentType;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class DocumentTypeAccuracyTest {
    @Test void comparesTheFirstSuggestionWithTheConfirmedTypePerType() {
        String invoiceText="COMMERCIAL INVOICE\nInvoice No. 1";
        String rightSafe=ClassificationHistory.manual(ClassificationHistory.automatic(null,invoiceText),DocumentType.COMMERCIAL_INVOICE,"anna");
        String wrong=ClassificationHistory.manual(ClassificationHistory.automatic(null,invoiceText),DocumentType.PACKING_LIST,"anna");
        String none=ClassificationHistory.manual(ClassificationHistory.automatic(null,"nothing to see here"),DocumentType.PACKING_LIST,"bob");
        String unconfirmed=ClassificationHistory.automatic(null,invoiceText);
        var report=DocumentTypeAccuracy.evaluate(List.of(rightSafe,wrong,none,unconfirmed,"not json"));
        assertThat(report.documents()).isEqualTo(3);assertThat(report.correct()).isEqualTo(1);
        var invoice=report.types().stream().filter(r->r.type().equals("COMMERCIAL_INVOICE")).findFirst().orElseThrow();
        assertThat(invoice.documents()).isEqualTo(1);assertThat(invoice.rate()).isEqualTo(1.0);assertThat(invoice.correctSafe()).isEqualTo(1);
        var packing=report.types().stream().filter(r->r.type().equals("PACKING_LIST")).findFirst().orElseThrow();
        assertThat(packing.documents()).isEqualTo(2);assertThat(packing.wrong()).isEqualTo(1);assertThat(packing.none()).isEqualTo(1);assertThat(packing.rate()).isEqualTo(0.0);
        assertThat(report.mistakes()).singleElement().satisfies(m->assertThat(m.count()).isEqualTo(1));
    }
}
