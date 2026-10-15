package de.ostms.lc.document.service;

import de.ostms.lc.document.domain.DocumentType;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class ExpectedDocumentClassificationTest {
    @Test void theLcRequirementsDecideBetweenTwoHeadings() {
        String text="COMMERCIAL INVOICE\nPACKING LIST\nGoods as per contract";
        assertThat(DocumentClassifier.classify(null,text).status()).isEqualTo("AMBIGUOUS");
        var withLc=DocumentClassifier.classify(null,text,Set.of(DocumentType.PACKING_LIST,DocumentType.BILL_OF_LADING));
        assertThat(withLc.suggestedType()).isEqualTo(DocumentType.PACKING_LIST);assertThat(withLc.method()).isEqualTo("LC_EXPECTED_V1");
    }

    @Test void aTypeTheLcDoesNotAskForIsOnlyOfferedForReview() {
        var c=DocumentClassifier.classify(null,"INSURANCE CERTIFICATE\nInsured value EUR 1000",Set.of(DocumentType.COMMERCIAL_INVOICE));
        assertThat(c.suggestedType()).isEqualTo(DocumentType.INSURANCE_CERTIFICATE);assertThat(c.status()).isEqualTo("REVIEW");
        assertThat(c.evidence()).anyMatch(e->e.contains("nicht gefordert"));
        assertThat(DocumentClassifier.classify(null,"INSURANCE CERTIFICATE",Set.of()).status()).isEqualTo("SUGGESTED");
    }

    @Test void newProfilesRecognisePagesWithoutHeading() {
        var awb=DocumentClassifier.classify(null,"Airport of Departure FRANKFURT\nAirport of Destination SHANGHAI\nShipper's Account Number\nIssuing Carrier's Agent\nChargeable Weight 120");
        assertThat(awb.suggestedType()).isEqualTo(DocumentType.AIR_WAYBILL);
        // Three typical fields are enough when the LC asks for that document.
        String draft="Pay to the order of ABC Bank\nat sight\nvalue received";
        assertThat(DocumentClassifier.classify(null,draft).suggestedType()).isNull();
        assertThat(DocumentClassifier.classify(null,draft,Set.of(DocumentType.BILL_OF_EXCHANGE)).suggestedType()).isEqualTo(DocumentType.BILL_OF_EXCHANGE);
    }
}
