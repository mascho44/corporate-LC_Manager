package de.ostms.lc.rulepack;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DocumentPartyFactsTest {
 @Test void countriesAreReadFromTheBlocksAfterBuyerAndSellerLabels(){
  var r=DocumentPartyFacts.detect("COMMERCIAL INVOICE\nSeller:\nSUPPLIER CO. LTD\nNO. 88 JIANGUO ROAD\nSHANGHAI 200000\nCHINA\n\nBuyer: BUYER AG\nBahnhofstr. 1\n8001 Zürich\nSchweiz\n\nInvoice No: INV-1\n");
  assertThat(r.beneficiaryCountry()).isEqualTo("China");assertThat(r.applicantCountry()).isEqualTo("Switzerland");
  assertThat(r.beneficiarySource()).contains("Seller");assertThat(r.applicantSource()).contains("Buyer");
 }
 @Test void blockEndsAtTheNextFieldSoNeighbouringDataIsNotMistakenForTheAddress(){
  var r=DocumentPartyFacts.detect("Buyer: BUYER AG\nBahnhofstr. 1\nZürich\nSchweiz\nTotal number of cartons: 48\nGross weight: 100 kg\nNet weight: 90 kg\nOrigin: China");
  assertThat(r.applicantCountry()).isEqualTo("Switzerland");
 }
 @Test void messrsAndGermanLabelsWork(){
  assertThat(DocumentPartyFacts.detect("Messrs. ACME INC\n1 Main Street\nNew York\nUSA").applicantCountry()).isEqualTo("United States");
  var de=DocumentPartyFacts.detect("Verkäufer: Muster GmbH\nHauptstraße 5\n20095 Hamburg\nDeutschland\n\nKäufer: Beispiel SARL\nRue 1\nParis\nFrance");
  assertThat(de.beneficiaryCountry()).isEqualTo("Germany");assertThat(de.applicantCountry()).isEqualTo("France");
 }
 @Test void conflictingMissingOrUnclearBlocksYieldNothing(){
  var conflict=DocumentPartyFacts.detect("Buyer: A\nStreet 1\nBerlin\nGermany\n\nBuyer: B\nStreet 2\nParis\nFrance");
  assertThat(conflict.applicantCountry()).isNull();
  var none=DocumentPartyFacts.detect("Buyer: ACME\nSome street 1\nSome city");
  assertThat(none.applicantCountry()).isNull();assertThat(none.beneficiaryCountry()).isNull();
  assertThat(DocumentPartyFacts.detect(null).applicantCountry()).isNull();assertThat(DocumentPartyFacts.detect("").beneficiaryCountry()).isNull();
 }
 @Test void theSameCountryTwiceIsStillUnambiguous(){
  assertThat(DocumentPartyFacts.detect("Buyer: A\nStreet 1\nBerlin\nGermany\n\nBill to: A\nStreet 1\nBerlin\nDeutschland").applicantCountry()).isEqualTo("Germany");
 }
}
