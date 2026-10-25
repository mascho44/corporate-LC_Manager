package de.ostms.lc.check.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GoodsDescriptionLabelTest {
    @Test void usualLabelsAreRecognised() {
        assertThat(DocumentCheckService.goodsDescription("COMMERCIAL INVOICE\nDescription of goods: 2 UNITS CNC MILLING MACHINE")).contains("2 UNITS CNC MILLING MACHINE");
        assertThat(DocumentCheckService.goodsDescription("Goods description - WOODEN CRATES")).contains("WOODEN CRATES");
        assertThat(DocumentCheckService.goodsDescription("Description: OFFICE CHAIRS")).contains("OFFICE CHAIRS");
        assertThat(DocumentCheckService.goodsDescription("Warenbeschreibung: Fräsmaschine")).contains("Fräsmaschine");
        assertThat(DocumentCheckService.goodsDescription("Total amount: EUR 1")).isEmpty();
    }
}
