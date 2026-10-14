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
        var preview = new InboxAvisService(inbox, normalizer, null).preview(id);
        assertThat(preview.creditTextFound()).isFalse();assertThat(preview.credit()).isNull();
        assertThat(preview.cover().fields()).containsEntry("reference", "ABC123456").containsEntry("amount", "1000.00").containsEntry("expiryDate", "2026-11-30");
    }
}
