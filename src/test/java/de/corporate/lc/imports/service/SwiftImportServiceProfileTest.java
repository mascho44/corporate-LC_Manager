package de.corporate.lc.imports.service;

import de.corporate.lc.imports.api.SwiftImportRequest;
import de.corporate.lc.lc.repository.AmendmentRepository;
import de.corporate.lc.lc.repository.LetterOfCreditRepository;
import de.corporate.lc.lc.service.AmendmentService;
import de.corporate.lc.lc.service.LetterOfCreditService;
import de.corporate.lc.swift.Mt700Parser;
import de.corporate.lc.swift.Mt707Parser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SwiftImportServiceProfileTest {
    private final SwiftImportService service = new SwiftImportService(
            new Mt700Parser(), new Mt707Parser(), mock(LetterOfCreditRepository.class),
            mock(AmendmentRepository.class), mock(LetterOfCreditService.class),
            mock(AmendmentService.class), mock(SwiftImportHistoryService.class));

    @Test void detectsMt707ByProfileFields() {
        assertThat(service.detect(":20:LC-1\n:26E:2\n:30:260922\n:46B:NEW DOCUMENTS"))
                .isEqualTo("MT707");
    }

    @Test void detectsAndMapsMt760Profile() {
        String raw=":27:1/1\n:20:GTEE-1\n:22D:DGAR\n:40C:URDG\n:59:Beneficiary\n:77C:Guarantee text";
        var preview=service.preview(new SwiftImportRequest("guarantee.swift",raw));
        assertThat(preview.messageType()).isEqualTo("MT760");
        assertThat(preview.valid()).isTrue();
        assertThat(preview.rawFields()).anySatisfy(field -> {
            assertThat(field.code()).isEqualTo("77C");
            assertThat(field.suggestedTarget()).isEqualTo("guaranteeDetails");
        });
    }

    @Test void rejectsIncompleteMt760Profile() {
        var preview=service.preview(new SwiftImportRequest("guarantee.swift",":20:GTEE-1\n:77C:Guarantee text"));
        assertThat(preview.messageType()).isEqualTo("MT760");
        assertThat(preview.valid()).isFalse();
        assertThat(preview.errors()).anyMatch(error -> error.contains(":27:"));
        assertThat(preview.errors()).anyMatch(error -> error.contains(":40C:"));
    }
}
