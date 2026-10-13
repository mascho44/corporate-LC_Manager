package de.ostms.lc.customer;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class SubmissionLetterTest {
 static final String LETTER = """
   Musterbank AG – Trade Finance
   Einreichung der Dokumente
   Kunde: Beispiel Export GmbH
   Kundennummer: 4711-08
   Kontonummer: 0012345678
   IBAN: DE89 3704 0044 0532 0130 00
   BIC: COBADEFFXXX
   Wir reichen folgende Dokumente zum Akkreditiv LC-2026-0815 ein: Rechnung, Packliste, Konnossement.
   """;

 Customer customer(String name,String number,String account,String iban){var c=new Customer();c.set(name,number,account,iban,null,"test");return c;}

 @Test void readsTheLabelledCustomerData() {
  var f = SubmissionLetterReader.read(LETTER);
  assertThat(f.customerName()).isEqualTo("Beispiel Export GmbH");
  assertThat(f.customerNumber()).isEqualTo("4711-08");
  assertThat(f.accountNumber()).isEqualTo("0012345678");
  assertThat(f.iban()).isEqualTo("DE89370400440532013000");
  assertThat(f.bic()).isEqualTo("COBADEFFXXX");
 }
 @Test void englishLabelsAndInvalidIbansAreHandled() {
  var f = SubmissionLetterReader.read("Customer name: Example Trading Ltd\nCustomer no. C-99\nAccount number 123 456 789\nIBAN GB00 WEST 1234 5698 7654 32\nIBAN: GB29 NWBK 6016 1331 9268 19");
  assertThat(f.customerName()).isEqualTo("Example Trading Ltd");
  assertThat(f.customerNumber()).isEqualTo("C-99");
  assertThat(f.iban()).isEqualTo("GB29NWBK60161331926819");
  assertThat(SubmissionLetterReader.validIban("DE00370400440532013000")).isFalse();
  assertThat(SubmissionLetterReader.read("Sehr geehrte Damen und Herren, anbei Unterlagen.").any()).isFalse();
 }
 @Test void aLetterIsRecognisedByHeadingOrByItsCustomerData() {
  assertThat(SubmissionLetterReader.looksLikeSubmission("brief.pdf", LETTER)).isTrue();
  assertThat(SubmissionLetterReader.looksLikeSubmission("rechnung.pdf", "Rechnung Nr. 5\nBetrag 100,00 EUR")).isFalse();
  assertThat(SubmissionLetterReader.looksLikeSubmission("x.pdf", null)).isFalse();
 }
 @Test void ibanWinsAndOnlyAUniqueIdentifierMatchIsClear() {
  var a = customer("Beispiel Export GmbH", "4711-08", "12345678", "DE89 3704 0044 0532 0130 00");
  var b = customer("Beispiel Import AG", "9", "55555", null);
  var result = CustomerMatcher.suggest(SubmissionLetterReader.read(LETTER), List.of(b, a));
  assertThat(result.best().customerId()).isEqualTo(a.getId());
  assertThat(result.best().reason()).isEqualTo("IBAN");
  assertThat(result.clear()).isTrue();
 }
 @Test void numbersIgnoreFormatAndNamesAloneAreNeverClear() {
  var byNumber = customer("Andere Firma", "4711 08", null, null);
  var r1 = CustomerMatcher.suggest(new SubmissionLetterReader.Fields(null, "4711-08", null, null, null), List.of(byNumber));
  assertThat(r1.best().reason()).isEqualTo("Kundennummer"); assertThat(r1.clear()).isTrue();
  var byAccount = customer("X", null, "00012345678", null);
  assertThat(CustomerMatcher.suggest(new SubmissionLetterReader.Fields(null, null, "12345678", null, null), List.of(byAccount)).best().reason()).isEqualTo("Kontonummer");
  var byName = customer("Beispiel Export GmbH", null, null, null);
  var r2 = CustomerMatcher.suggest(new SubmissionLetterReader.Fields("beispiel export gmbh", null, null, null, null), List.of(byName));
  assertThat(r2.best().reason()).isEqualTo("Name"); assertThat(r2.clear()).isFalse();
  assertThat(CustomerMatcher.suggest(new SubmissionLetterReader.Fields("Völlig Anders", null, null, null, null), List.of(byName)).candidates()).isEmpty();
 }
 @Test void twoCustomersWithTheSameIdentifierNeedAHumanChoice() {
  var a = customer("A", null, null, "DE89370400440532013000"); var b = customer("B", null, null, "DE89 3704 0044 0532 0130 00");
  assertThat(CustomerMatcher.suggest(new SubmissionLetterReader.Fields(null, null, null, "DE89370400440532013000", null), List.of(a, b)).clear()).isFalse();
 }

 /** Layout of a bank's submission form: no customer name or number; the credit account (IBAN) identifies the customer, "Auftraggeber" is the applicant of the credit. */
 static final String BANK_FORM = """
   Dokumenteneinreichung

   Akkreditivnummer            LCB0319990000001
   Ihre Referenz               BW000AE0000001
   Akkreditiv über             EUR 100.000,00 +05%/-05%
   Eröffnende Bank             EXAMPLE BANK OF TRADE CO. LTD.
   Auftraggeber                EXAMPLE VISION LTD

   Sehr geehrte Damen und Herren,
   anbei erhalten Sie folgende Dokumente zur Ausnutzung des oben genannten Akkreditivs.
   Dokumentengegenwert / Währung:   EuR 60.229,56
   Dokument            Original   Kopien
   Handelsrechnung        1          1
   Konnossement          3/3         3
   [X] Die geforderten Kopien für die Auslandsbank sind beigefügt.
   [X] Es handelt sich um eine Teillieferung.
   Wir bitten um Gutschrift des Dokumentengegenwertes zu Gunsten:
   [X] EUR-Konto Nummer: DE89 3704 0044 0532 0130 00
   [ ] FW-Konto Nummer:
   Mit freundlichen Grüßen
   """;

 @Test void bankFormIdentifiesTheCustomerByTheCreditAccountAndDoesNotMistakeTheApplicantForTheCustomer() {
  var f = SubmissionLetterReader.read(BANK_FORM);
  assertThat(f.customerName()).isNull();
  assertThat(f.iban()).isEqualTo("DE89370400440532013000");
  assertThat(f.lcReference()).isEqualTo("LCB0319990000001");
  assertThat(f.yourReference()).isEqualTo("BW000AE0000001");
  assertThat(f.issuingBank()).isEqualTo("EXAMPLE BANK OF TRADE CO. LTD.");
  assertThat(f.applicant()).isEqualTo("EXAMPLE VISION LTD");
  assertThat(f.documentValue()).isEqualTo("EUR 60.229,56");
  assertThat(SubmissionLetterReader.looksLikeSubmission("scan.pdf", BANK_FORM)).isTrue();
  var customer = customer("Beispiel Export GmbH", "4711", null, "DE89 3704 0044 0532 0130 00");
  var result = CustomerMatcher.suggest(f, List.of(customer));
  assertThat(result.clear()).isTrue();
  assertThat(result.best().reason()).isEqualTo("IBAN");
 }

 @Test void anIbanReadWithHandwritingErrorsIsShownButNeverUsedForMatching() {
  var f = SubmissionLetterReader.read(BANK_FORM.replace("0532 0130 00", "0532 0130 01"));
  assertThat(f.iban()).isNull();
  assertThat(f.ibanCandidate()).isEqualTo("DE89370400440532013001");
  assertThat(CustomerMatcher.suggest(f, List.of(customer("Beispiel Export GmbH", null, null, "DE89370400440532013000"))).candidates()).isEmpty();
 }

 @Test void creditAccountLabelsWithCurrencyPrefixAreAccountNumbers() {
  assertThat(SubmissionLetterReader.read("EUR-Konto Nummer: 0012 3456 78\nFW-Konto Nummer:").accountNumber()).isEqualTo("0012345678");
 }
}
