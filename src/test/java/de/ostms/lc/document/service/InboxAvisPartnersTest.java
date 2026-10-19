package de.ostms.lc.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import de.ostms.lc.capture.InboxCaptureInfo;
import de.ostms.lc.capture.InboxCaptureInfoRepository;
import de.ostms.lc.document.domain.DocumentInboxItem;
import de.ostms.lc.swift.PrintedSwiftNormalizer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InboxAvisPartnersTest {
    private final UUID letterId = UUID.randomUUID(), sameMailId = UUID.randomUUID(), mentionsId = UUID.randomUUID(), otherCreditId = UUID.randomUUID(), noCreditId = UUID.randomUUID();
    private final DocumentInboxService inbox = mock(DocumentInboxService.class);
    private final InboxCaptureInfoRepository origins = mock(InboxCaptureInfoRepository.class);

    private DocumentInboxItem item(UUID id, String name, String text) {
        var item = mock(DocumentInboxItem.class);
        when(item.getId()).thenReturn(id);
        when(item.getOriginalFilename()).thenReturn(name);
        when(item.getExtractedText()).thenReturn(text);
        when(item.getContent()).thenReturn(new byte[]{1});
        when(inbox.openItem(id)).thenReturn(item);
        return item;
    }

    @Test void ranksTheAnnexOfTheSameMailFirstThenTheOneNamingTheCreditNumber() throws Exception {
        String credit = Files.readString(Path.of("src/test/resources/avis/avis-de-attachment.txt"));
        String otherCredit = credit.replace("TEST-LC-2026-008", "ANDERE-LC-77");
        var letter = item(letterId, "avis-schreiben.pdf", "Sehr geehrte Damen und Herren,\nAkkreditivnummer: TEST-LC-2026-008\nMit freundlichen Grüßen");
        var sameMail = item(sameMailId, "anlage-mail.pdf", otherCredit);
        var mentions = item(mentionsId, "anlage-nummer.pdf", credit);
        var otherCreditItem = item(otherCreditId, "irgendein-akkreditiv.pdf", otherCredit.replace("ANDERE-LC-77", "NOCH-EIN-LC-5"));
        var noCredit = item(noCreditId, "rechnung.pdf", "Invoice 1\nTotal EUR 5,00");
        when(inbox.openEntities()).thenReturn(List.of(letter, noCredit, otherCreditItem, mentions, sameMail));
        when(origins.byInboxItem(any())).thenAnswer(call -> {
            UUID id = call.getArgument(0);
            if (id.equals(letterId) || id.equals(sameMailId)) { var info = mock(InboxCaptureInfo.class); when(info.getOriginReference()).thenReturn("<mail-4711@example.test>"); return Optional.of(info); }
            return Optional.empty();
        });

        var partners = new InboxAvisService(inbox, new PrintedSwiftNormalizer(), null, origins).partners(letterId);

        assertThat(partners).extracting(InboxAvisService.Partner::id).containsExactly(sameMailId, mentionsId, otherCreditId);
        assertThat(partners.get(0).reason()).isEqualTo("kam mit derselben E-Mail");
        assertThat(partners.get(1).reason()).contains("TEST-LC-2026-008");
        assertThat(partners.get(2).reason()).isEqualTo("enthält einen Akkreditivtext");
    }

    @Test void recognisesTheCreditNumberIgnoringSeparatorsAndCase() {
        assertThat(InboxAvisService.mentions("Our ref lc 2026 / 008 attached", "LC-2026-008")).isTrue();
        assertThat(InboxAvisService.mentions("Our ref LC-2026-009", "LC-2026-008")).isFalse();
        assertThat(InboxAvisService.mentions("anything", "AB")).isFalse();
        assertThat(InboxAvisService.mentions(null, "LC-2026-008")).isFalse();
        assertThat(InboxAvisService.mentions("text", null)).isFalse();
    }
}
