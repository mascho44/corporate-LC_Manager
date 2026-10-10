package de.ostms.lc.rulepack;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PartyAddressTest {
 @Test void countriesAreResolvedFromNamesCodesAndAbbreviations(){
  assertThat(CountryResolver.resolve("China")).contains("China");assertThat(CountryResolver.resolve("P.R. CHINA")).contains("China");assertThat(CountryResolver.resolve("CN")).contains("China");
  assertThat(CountryResolver.resolve("Deutschland")).contains("Germany");assertThat(CountryResolver.resolve("GERMANY")).contains("Germany");assertThat(CountryResolver.resolve("DE")).contains("Germany");assertThat(CountryResolver.resolve("DEU")).contains("Germany");
  assertThat(CountryResolver.resolve("U.S.A.")).contains("United States");assertThat(CountryResolver.resolve("UK")).contains("United Kingdom");assertThat(CountryResolver.resolve("Österreich")).contains("Austria");
  assertThat(CountryResolver.resolve("Türkei")).isPresent();assertThat(CountryResolver.resolve("Turkey")).isPresent();
 }
 @Test void ordinaryWordsAndLowerCaseShortCodesAreNotCountries(){
  for(String text:new String[]{"Shanghai","Road 12","de","in","Hamburg","","  ",null,"ID 12","Main Street 5"})assertThat(CountryResolver.resolve(text)).as(String.valueOf(text)).isEmpty();
 }
 @Test void swiftStylePartyIsSplitIntoNameAddressAndCountry(){
  var p=PartyAddress.parse("ACME TRADING CO., LTD.\nNO. 88 JIANGUO ROAD\nSHANGHAI 200000\nCHINA");
  assertThat(p.name()).isEqualTo("ACME TRADING CO., LTD.");assertThat(p.country()).isEqualTo("China");assertThat(p.addressLines()).containsExactly("NO. 88 JIANGUO ROAD","SHANGHAI 200000");assertThat(p.address()).isEqualTo("NO. 88 JIANGUO ROAD, SHANGHAI 200000");
 }
 @Test void countryAtTheEndOfTheCityLineIsRecognised(){
  var p=PartyAddress.parse("Muster GmbH\nHauptstraße 5\n20095 Hamburg, Deutschland");
  assertThat(p.country()).isEqualTo("Germany");assertThat(p.addressLines()).containsExactly("Hauptstraße 5","20095 Hamburg");
  assertThat(PartyAddress.parse("X Ltd\nLondon - UK").country()).isEqualTo("United Kingdom");
 }
 @Test void accountLinesContactDataAndMissingCountriesAreHandled(){
  var p=PartyAddress.parse("/12345678\nBUYER AG\nBahnhofstr. 1\n8001 Zürich\nTel: +41 44 123 45 67\nSchweiz");
  assertThat(p.name()).isEqualTo("BUYER AG");assertThat(p.country()).isEqualTo("Switzerland");assertThat(p.addressLines()).containsExactly("Bahnhofstr. 1","8001 Zürich");
  var none=PartyAddress.parse("ONLY NAME\nSOME STREET 1\nSOME CITY");assertThat(none.country()).isNull();assertThat(none.addressLines()).hasSize(2);
  assertThat(PartyAddress.parse("")).isNull();assertThat(PartyAddress.parse(null)).isNull();assertThat(PartyAddress.parse("JUST A NAME").addressLines()).isEmpty();
 }
}
