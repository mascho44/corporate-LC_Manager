package de.ostms.lc.rulepack;

import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.document.domain.LcDocument;
import de.ostms.lc.document.service.DocumentExtractionService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ItemTableFactsTest {
 static final String INVOICE="Beispiel Mode GmbH\nMuster Handel Limited\nCommercial Invoice\ndate 2026-06-19 customer no. 12345\ninvoice no. 9999\narticlecode article quantity unit unit price total price\nFW26 Women Clothing 931 pcs 76,08 70.831,40 EUR\nPartial Shipment out of: Women Clothing\nTotal 4.815 Pcs FCA Stuttgart, Germany";

 @Test void theFirstItemRowGivesQuantityUnitPricesAndCurrency(){
  var row=ItemTableFacts.detect(INVOICE).orElseThrow();
  assertThat(row.quantity()).isEqualTo("931");assertThat(row.unit()).isEqualTo("PCS");assertThat(row.unitPrice()).isEqualTo("76.08");assertThat(row.total()).isEqualTo("70831.40");assertThat(row.currency()).isEqualTo("EUR");
  assertThat(ItemTableFacts.detect("Nur Fließtext ohne Tabelle 931 pcs")).isEmpty();
  assertThat(ItemTableFacts.detect("article quantity price\nTotal 5 pcs 10,00 EUR")).isEmpty();
  assertThat(ItemTableFacts.detect(null)).isEmpty();
 }
 @Test void textFactsUseTheTableWhenThereIsNoLabelledQuantity(){
  var facts=DocumentTextFacts.detect(DocumentType.COMMERCIAL_INVOICE,INVOICE).stream().collect(java.util.stream.Collectors.toMap(DocumentTextFacts.Fact::field,DocumentTextFacts.Fact::value,(a,b)->a));
  assertThat(facts).containsEntry(PackDefinition.Field.DOCUMENT_QUANTITY,"931").containsEntry(PackDefinition.Field.DOCUMENT_QUANTITY_UNIT,"PCS").containsEntry(PackDefinition.Field.DOCUMENT_UNIT_PRICE_AMOUNT,"76.08");
  var labelled=DocumentTextFacts.detect(DocumentType.COMMERCIAL_INVOICE,"Quantity: 12 pcs\narticle quantity price\nX 931 pcs 1,00 2,00 EUR");
  assertThat(labelled).filteredOn(f->f.field()==PackDefinition.Field.DOCUMENT_QUANTITY).extracting(DocumentTextFacts.Fact::value).containsExactly("12");
 }
 @Test void aTotalLineWithoutCurrencyIsNotTheAmountAndTheTableTotalIsUsed(){
  var document=new LcDocument();document.setOriginalFilename("rechnung.pdf");
  new DocumentExtractionService().applyRecognizedText(document,INVOICE,"EXTRACTED");
  assertThat(document.getExtractedAmount()).isEqualByComparingTo(new BigDecimal("70831.40"));assertThat(document.getExtractedCurrency()).isEqualTo("EUR");
  var simple=new LcDocument();simple.setOriginalFilename("a.pdf");
  new DocumentExtractionService().applyRecognizedText(simple,"Invoice no. 1\nTotal EUR 1.250,50","EXTRACTED");
  assertThat(simple.getExtractedAmount()).isEqualByComparingTo(new BigDecimal("1250.50"));assertThat(simple.getExtractedCurrency()).isEqualTo("EUR");
 }
 @Test void aLostDecimalCommaInTheTotalOfAScannedItemTableIsRestored(){
  String text="commercial invoice\narticle quantity unit unit price total price\nWomen Clothing 931 pcs 76,08 70.831 40 EUR\n";
  var row=ItemTableFacts.detect(text).orElseThrow();
  assertThat(row.total()).isEqualTo("70831.40");assertThat(row.currency()).isEqualTo("EUR");
  var doc=new de.ostms.lc.document.domain.LcDocument();new DocumentExtractionService().applyRecognizedText(doc,text,"OCR_EXTRACTED");
  assertThat(doc.getExtractedAmount()).isEqualByComparingTo(new BigDecimal("70831.40"));
  assertThat(ItemTableFacts.restoreDecimals(new String[]{"70.831","40","EUR"})).containsExactly("70.831,40","EUR");
  assertThat(ItemTableFacts.restoreDecimals(new String[]{"931","40","pcs"})).containsExactly("931","40","pcs");
 }
}
