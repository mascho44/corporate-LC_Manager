package de.ostms.lc.rulepack;

import org.junit.jupiter.api.Test;
import static de.ostms.lc.rulepack.PackDefinition.Field;
import static org.assertj.core.api.Assertions.assertThat;

class LearnedLabelServiceTest {
 private static final String INVOICE="COMMERCIAL INVOICE\nSeller: BEISPIEL GMBH\nDescription of goods: 2 UNITS CNC MILLING MACHINE MODEL BM-500\nTotal amount: EUR 125,000.00\n";

 @Test void theLabelOfTheLineWithTheEnteredValueIsLearned() {
  assertThat(LearnedLabelService.labelFor(INVOICE,Field.DOCUMENT_GOODS_DESCRIPTION,"2 units cnc milling machine model BM-500")).contains("Description of goods");
  assertThat(LearnedLabelService.labelFor(INVOICE,Field.DOCUMENT_AMOUNT,"125000")).contains("Total amount");
 }
 @Test void nothingIsLearnedWhenTheValueIsMissingOrOnSeveralLines() {
  assertThat(LearnedLabelService.labelFor(INVOICE,Field.DOCUMENT_GOODS_DESCRIPTION,"OFFICE CHAIRS")).isEmpty();
  assertThat(LearnedLabelService.labelFor("Net: EUR 5.00\nGross: EUR 5.00",Field.DOCUMENT_AMOUNT,"5")).isEmpty();
  assertThat(LearnedLabelService.labelFor(INVOICE,Field.DOCUMENT_ON_BOARD_DATE,"2026-12-01")).isEmpty();
 }
 @Test void aLearnedLabelProposesTheValueOfALaterDocument() {
  String next="INVOICE 2\nDescription of goods: 40 PALLETS SPARE PARTS\nTotal amount: USD 9.876,50";
  assertThat(LearnedLabelService.valueFor(next,Field.DOCUMENT_GOODS_DESCRIPTION,"Description of goods")).contains("40 PALLETS SPARE PARTS");
  assertThat(LearnedLabelService.valueFor(next,Field.DOCUMENT_AMOUNT,"Total amount")).contains("9876.50");
  assertThat(LearnedLabelService.valueFor("Description of goods: A\nDescription of goods: B",Field.DOCUMENT_GOODS_DESCRIPTION,"Description of goods")).isEmpty();
 }
}
