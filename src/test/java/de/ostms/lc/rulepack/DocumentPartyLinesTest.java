package de.ostms.lc.rulepack;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static de.ostms.lc.rulepack.PackDefinition.Field;
import static org.assertj.core.api.Assertions.assertThat;

class DocumentPartyLinesTest {
 static Map<Field,String> facts(String text){return DocumentPartyLines.detect(text).stream().collect(Collectors.toMap(DocumentPartyLines.Fact::field,DocumentPartyLines.Fact::value));}

 @Test void invoiceLayoutWithLetterheadAddresseeAndItemTable(){
  var f=facts("Beispiel Mode GmbH\nHauptstraße 1\n72411 Musterstadt\nMuster Handel Limited\nUnit B, 6/F 78 Example Road\nKowloon, HK\nCommercial Invoice\ndate 2026-06-19 customer no. 12345\ninvoice no. 9999\narticlecode article quantity unit price total price\nFW26 Women Clothing 931 pcs 76,08 70.831,40 EUR\nTotal 4.815 Pcs FCA Stuttgart, Germany");
  assertThat(f).containsEntry(Field.DOCUMENT_ISSUER,"Beispiel Mode GmbH").containsEntry(Field.DOCUMENT_RECIPIENT,"Muster Handel Limited");
  assertThat(f).containsEntry(Field.DOCUMENT_GOODS_DESCRIPTION,"Women Clothing");
 }
 @Test void goodsFromTheArticleColumnWhenTheHeaderNamesDescriptionAndQuantity(){
  var f=facts("Beispiel Mode GmbH\nPos Description Quantity Price\n1 Women Clothing 931 pcs 76,08\nTotal 931");
  assertThat(f).containsEntry(Field.DOCUMENT_GOODS_DESCRIPTION,"Women Clothing");
 }
 @Test void labelledLinesWinAndMultiLineGoodsAreJoined(){
  var f=facts("Seller: Alpha Trading Co., Ltd.\nBuyer: Beta Import GmbH\nDescription of goods: Cotton shirts, men,\nwoven, 100% cotton\nTotal 931 pcs");
  assertThat(f).containsEntry(Field.DOCUMENT_ISSUER,"Alpha Trading Co., Ltd.").containsEntry(Field.DOCUMENT_RECIPIENT,"Beta Import GmbH").containsEntry(Field.DOCUMENT_GOODS_DESCRIPTION,"Cotton shirts, men, woven, 100% cotton");
 }
 @Test void contradictoryLabelsAndTextWithoutCompaniesGiveNothing(){
  assertThat(facts("Seller: Alpha Trading Ltd\nSeller: Gamma Export Ltd\nNothing else of interest here")).doesNotContainKey(Field.DOCUMENT_ISSUER);
  assertThat(facts("Rechnung 4711\nBetrag 100,00 EUR")).isEmpty();
  assertThat(DocumentPartyLines.detect(null)).isEmpty();
 }
}
