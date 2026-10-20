package de.ostms.lc.lc.service;

import static org.assertj.core.api.Assertions.assertThat;

import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.document.domain.LcDocument;
import de.ostms.lc.lc.domain.Amendment;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.service.AmendmentImpact.Level;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AmendmentImpactTest {
    private final LocalDate today = LocalDate.of(2026, 10, 20);

    private LetterOfCredit lc() {
        var lc = new LetterOfCredit();
        lc.setAmount(new BigDecimal("100000.00")); lc.setCurrency("EUR"); lc.setExpiryDate(LocalDate.of(2026, 12, 31));
        return lc;
    }

    private LcDocument document(DocumentType type, String name, LocalDate date, String amount) {
        var d = new LcDocument();
        d.setDocumentType(type); d.setOriginalFilename(name); d.setDocumentDate(date);
        if (amount != null) d.setAmount(new BigDecimal(amount));
        return d;
    }

    @Test void aReductionBelowTheInvoicesNeedsAction() {
        var a = new Amendment(); a.setAmountDecrease(new BigDecimal("30000.00"));
        var items = AmendmentImpact.of(lc(), a, List.of(document(DocumentType.COMMERCIAL_INVOICE, "inv.pdf", null, "90000.00")), today);
        assertThat(items).hasSize(1);
        assertThat(items.get(0).level()).isEqualTo(Level.ACTION);
        assertThat(items.get(0).message()).contains("70000.00 EUR").contains("90000.00");
        assertThat(items.get(0).documents()).containsExactly("inv.pdf");
    }

    @Test void anIncreaseIsOnlyInformation() {
        var a = new Amendment(); a.setAmountIncrease(new BigDecimal("5000.00"));
        var items = AmendmentImpact.of(lc(), a, List.of(), today);
        assertThat(items.get(0).level()).isEqualTo(Level.INFO);
        assertThat(items.get(0).message()).contains("105000.00 EUR");
    }

    @Test void datesAreComparedWithTheDocuments() {
        var a = new Amendment(); a.setNewExpiryDate(LocalDate.of(2026, 11, 15)); a.setNewLatestShipmentDate(LocalDate.of(2026, 10, 1));
        var items = AmendmentImpact.of(lc(), a, List.of(document(DocumentType.BILL_OF_LADING, "bl.pdf", LocalDate.of(2026, 10, 5), null),
            document(DocumentType.COMMERCIAL_INVOICE, "inv.pdf", LocalDate.of(2026, 11, 20), null)), today);
        assertThat(items.get(0).area()).startsWith("Ablauf");
        assertThat(items.get(0).level()).isEqualTo(Level.ACTION);
        assertThat(items.get(0).documents()).containsExactly("inv.pdf");
        assertThat(items.get(1).documents()).containsExactly("bl.pdf");
        var past = new Amendment(); past.setNewExpiryDate(LocalDate.of(2026, 10, 1));
        assertThat(AmendmentImpact.of(lc(), past, List.of(), today).get(0).message()).contains("Vergangenheit");
    }

    @Test void goodsAndRequirementsPointAtTheAffectedDocuments() {
        var a = new Amendment(); a.setChangedGoods("2 UNITS NEW MODEL"); a.setChangedDocuments("+ PACKING LIST IN 2 COPIES\n+ CERTIFICATE OF ORIGIN");
        var items = AmendmentImpact.of(lc(), a, List.of(document(DocumentType.COMMERCIAL_INVOICE, "inv.pdf", null, null), document(DocumentType.PACKING_LIST, "pl.pdf", null, null)), today);
        assertThat(items.get(0).area()).contains("45B");
        assertThat(items.get(0).documents()).containsExactly("inv.pdf", "pl.pdf");
        assertThat(items.get(1).message()).contains("Noch nicht vorhanden: Certificate of Origin");
        assertThat(items.get(1).documents()).containsExactly("pl.pdf");
    }

    @Test void anEmptyChangeSaysSo() {
        assertThat(AmendmentImpact.of(lc(), new Amendment(), List.of(), today)).singleElement().extracting(AmendmentImpact.Item::level).isEqualTo(Level.INFO);
    }
}
