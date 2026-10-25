package de.ostms.lc.document.service;

import de.ostms.lc.swift.PrintedSwiftNormalizer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InboxAvisServiceTest {
    final PrintedSwiftNormalizer normalizer = new PrintedSwiftNormalizer();

    @Test void creditTextIsTakenFromAdviceWithColonFieldsAndTheClosingIsCutOff() {
        String advice = """
                Musterbank AG, Trade Finance
                Sehr geehrte Damen und Herren,
                wir avisieren Ihnen unverbindlich das folgende Akkreditiv der Issuing Bank Example Bank.
                :27:1/1
                :40A:IRREVOCABLE
                :20:LC-2026-0815
                :31D:261130 HAMBURG
                :50:Buyer Ltd, London
                :59:Seller GmbH, Stuttgart
                :32B:EUR125000,00
                :44C:261031
                :46A:+SIGNED COMMERCIAL INVOICE IN 3 COPIES
                +FULL SET OF CLEAN ON BOARD BILLS OF LADING
                Mit freundlichen Grüßen
                Ihre Musterbank AG
                """;
        String raw = InboxAvisService.creditText(normalizer, advice);
        assertThat(raw).contains(":20:LC-2026-0815").contains(":32B:EUR125000,00").contains(":44C:261031").contains("FULL SET OF CLEAN ON BOARD");
        assertThat(raw).doesNotContain("Musterbank AG").doesNotContain("Mit freundlichen").doesNotContain("Sehr geehrte");
    }

    @Test void printedTableFormIsRecognizedToo() {
        String advice = """
                Anschreiben der Bank
                Fieldtag Fieldname Fielddetails
                20 Documentary Credit Number LC-77
                32B Currency Code, Amount USD1000,00
                31D Date and Place of Expiry 261201 NEW YORK
                """;
        String raw = InboxAvisService.creditText(normalizer, advice);
        assertThat(raw).contains(":32B:").contains(":31D:");
    }

    @Test void aLetterWithoutCreditTextGivesNothing() {
        assertThat(InboxAvisService.creditText(normalizer, "Sehr geehrte Damen und Herren, anbei unsere Rechnung. Mit freundlichen Grüßen")).isEmpty();
        assertThat(InboxAvisService.creditText(normalizer, null)).isEmpty();
    }

    @Test void adviceWithoutCreditTextOffersWhatTheCoverLetterSays() {
        var inbox = org.mockito.Mockito.mock(DocumentInboxService.class);
        var item = new de.ostms.lc.document.domain.DocumentInboxItem();
        item.setOriginalFilename("avis.pdf");
        item.setExtractedText("Ort, 08. Juni 2026\nDokumentenakkreditiv – Avisierung\nAkkreditivnummer ABC123456\nAkkreditiv über EUR 1.000,00\nGültig bis 30.11.2026 / GERMANY\nSehr geehrte Damen und Herren,\nden Inhalt entnehmen Sie bitte der beigefügten Kopie.");
        var id = java.util.UUID.randomUUID();
        org.mockito.Mockito.when(inbox.openItem(id)).thenReturn(item);
        var preview = new InboxAvisService(inbox, normalizer, null, null).preview(id);
        assertThat(preview.creditTextFound()).isFalse();assertThat(preview.credit()).isNull();
        assertThat(preview.cover().fields()).containsEntry("reference", "ABC123456").containsEntry("amount", "1000.00").containsEntry("expiryDate", "2026-11-30");
    }

    @Test void bankPrintFormatIsFoundAfterACoverLetterWithClosing() {
        String advice = """
                Dear Sirs,
                please find below the documentary credit.
                Best regards
                Sample Bank
                MESSAGE TYPE: 700
                27: SEQUENCE OF TOTAL
                1/1
                20: DOCUMENTARY CREDIT NUMBER
                ILC250B000001
                31D: DATE AND PLACE OF EXPIRY
                260222GERMANY
                32B: CURRENCY CODE, AMOUNT
                EUR1000,00
                """;
        String raw = InboxAvisService.creditText(normalizer, advice);
        assertThat(raw).contains(":20:ILC250B000001", ":31D:260222GERMANY", ":32B:EUR1000,00");
    }

    @Test void aCoverLetterFollowedByALabelledAttachmentIsRead() throws Exception {
        String letter = """
                Musterbank AG, Trade Finance
                Referenz: AV-2026-4711
                Sehr geehrte Damen und Herren,
                wir avisieren Ihnen unverbindlich das beigefügte Akkreditiv. Der Betrag: EUR 125.000,00 steht zur Verfügung.
                Mit freundlichen Grüßen
                Ihre Musterbank AG

                """;
        String attachment = java.nio.file.Files.readString(java.nio.file.Path.of("src/test/resources/avis/avis-de-attachment.txt"));
        String raw = InboxAvisService.creditText(normalizer, letter + attachment);
        assertThat(raw).contains(":20:TEST-LC-2026-008", ":32B:EUR125000,00", ":31D:261231STUTTGART", ":44C:261215");
        var lc = new de.ostms.lc.swift.Mt700Parser().parse(raw);
        assertThat(lc.getReference()).isEqualTo("TEST-LC-2026-008");
        assertThat(lc.getCurrency()).isEqualTo("EUR");
        assertThat(lc.getAmount()).isEqualByComparingTo("125000");
        assertThat(lc.getExpiryDate()).isEqualTo(java.time.LocalDate.of(2026, 12, 31));
        assertThat(lc.getLatestShipmentDate()).isEqualTo(java.time.LocalDate.of(2026, 12, 15));
        assertThat(lc.getBeneficiary()).contains("Beispiel Maschinenbau GmbH");
    }

    @Test void aPortalPrintInEnglishBecomesAnImportableCredit() throws Exception {
        String raw = InboxAvisService.creditText(normalizer, java.nio.file.Files.readString(java.nio.file.Path.of("src/test/resources/avis/portal-print-en.txt")));
        var lc = new de.ostms.lc.swift.Mt700Parser().parse(raw);
        assertThat(lc.getReference()).isEqualTo("TEST-LC-2026-007");
        assertThat(lc.getAmount()).isEqualByComparingTo("125000");
        assertThat(lc.getExpiryDate()).isEqualTo(java.time.LocalDate.of(2026, 11, 30));
        assertThat(lc.getApplicant()).contains("MUSTER IMPORT TRADING LLC");
    }

    @Test void aLetterWithoutACreditTextIsStillNotACredit() {
        assertThat(InboxAvisService.creditText(normalizer, "Sehr geehrte Damen und Herren,\nReferenz: X-1\nBetrag: EUR 1.000,00\nMit freundlichen Grüßen")).isEmpty();
    }
}
