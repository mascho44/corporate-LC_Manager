package de.ostms.lc.swift;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LabelledCreditNormalizerTest {
    private static String fixture(String name) throws Exception {
        return Files.readString(Path.of("src/test/resources/avis", name));
    }

    @Test void readsAnEnglishPortalPrintAcrossPageBreaks() throws Exception {
        String result = LabelledCreditNormalizer.normalize(fixture("portal-print-en.txt"));
        assertThat(result).contains(":20:TEST-LC-2026-007", ":31C:260901", ":40A:IRREVOCABLE", ":40E:UCP LATEST VERSION", ":31D:261130HAMBURG GERMANY",
            ":32B:EUR125000,00", ":43P:NOT ALLOWED", ":43T:ALLOWED", ":44E:HAMBURG", ":44F:JEBEL ALI", ":44C:261010", ":48:21");
        assertThat(result).contains(":50:MUSTER IMPORT TRADING LLC\nP.O. BOX 1234\nDUBAI, UNITED ARAB EMIRATES");
        assertThat(result).contains(":59:BEISPIEL MASCHINENBAU GMBH\nINDUSTRIESTRASSE 10\n70565 STUTTGART, GERMANY");
        assertThat(result).contains("+SIGNED COMMERCIAL INVOICE IN 3 ORIGINALS\n+PACKING LIST IN 2 COPIES\n+FULL SET 3/3 CLEAN ON BOARD OCEAN BILLS OF LADING");
        assertThat(result).contains(":47A:1. ALL DOCUMENTS MUST SHOW THIS DOCUMENTARY CREDIT NUMBER\n2. ALL DOCUMENTS MUST BE ISSUED IN ENGLISH");
        assertThat(result).doesNotContain("Page 2 of 3").doesNotContain("Page 3 of 3");
    }

    @Test void readsAGermanPrintWithLabelsOnTheirOwnLine() throws Exception {
        String result = LabelledCreditNormalizer.normalize(fixture("avis-de-attachment.txt"));
        assertThat(result).contains(":20:TEST-LC-2026-008", ":31C:260905", ":40A:IRREVOCABLE", ":31D:261231STUTTGART", ":32B:EUR125000,00", ":43P:NOT ALLOWED", ":43T:ALLOWED",
            ":44E:Hamburg", ":44F:Jebel Ali", ":44C:261215", ":48:21");
        assertThat(result).contains(":50:Muster Import Trading LLC\nDubai, Vereinigte Arabische Emirate", ":59:Beispiel Maschinenbau GmbH\nIndustriestrasse 10\n70565 Stuttgart");
        assertThat(result).contains(":46A:Handelsrechnung in 3 Originalen\nPackliste in 2 Kopien");
    }

    @Test void amountsAndDatesInManyPrintedForms() {
        assertThat(LabelledCreditNormalizer.amount("EUR 125.000,00")).isEqualTo("EUR125000,00");
        assertThat(LabelledCreditNormalizer.amount("125,000.50 USD")).isEqualTo("USD125000,50");
        assertThat(LabelledCreditNormalizer.amount("CHF 1'250'000")).isEqualTo("CHF1250000,");
        assertThat(LabelledCreditNormalizer.amount("EUR 125 000,00")).isEqualTo("EUR125000,00");
        assertThat(LabelledCreditNormalizer.amount("ohne Betrag")).isNull();
        assertThat(LabelledCreditNormalizer.yymmdd("5. Oktober 2026")).isEqualTo("261005");
        assertThat(LabelledCreditNormalizer.yymmdd("October 5, 2026")).isEqualTo("261005");
        assertThat(LabelledCreditNormalizer.yymmdd("2026-10-05")).isEqualTo("261005");
        assertThat(LabelledCreditNormalizer.yymmdd("05/10/26")).isEqualTo("261005");
        assertThat(LabelledCreditNormalizer.yymmdd("261005")).isEqualTo("261005");
        assertThat(LabelledCreditNormalizer.yymmdd("kein Datum")).isNull();
    }

    @Test void aLetterWithOnlyAFewLabelsIsNotACredit() {
        assertThat(LabelledCreditNormalizer.normalize("Sehr geehrte Damen und Herren,\n\nReferenz: TEST-LC-1\nBetrag: EUR 1.000,00\nBegünstigter: Beispiel GmbH\n\nMit freundlichen Grüßen")).isEmpty();
        assertThat(LabelledCreditNormalizer.normalize(null)).isEmpty();
    }

    @Test void aSecondMentionOfALabelKeepsTheFirstReading() {
        String text = "Documentary Credit Number: A-1\nAmount: EUR 100,00\nApplicant: X\nBeneficiary: Y\nAmount: EUR 999,00\nin the summary below\nDate of Issue: 01.09.2026";
        assertThat(LabelledCreditNormalizer.normalize(text)).contains(":32B:EUR100,00").doesNotContain("999");
    }
}
