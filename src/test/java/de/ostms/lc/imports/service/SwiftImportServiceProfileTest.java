package de.ostms.lc.imports.service;

import de.ostms.lc.imports.api.SwiftImportRequest;
import de.ostms.lc.lc.repository.AmendmentRepository;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import de.ostms.lc.lc.service.AmendmentService;
import de.ostms.lc.lc.service.LetterOfCreditService;
import de.ostms.lc.swift.Mt700Parser;
import de.ostms.lc.swift.Mt707Parser;
import de.ostms.lc.training.service.TrainingLearningService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SwiftImportServiceProfileTest {
    private final SwiftImportService service = new SwiftImportService(
            new Mt700Parser(), new Mt707Parser(), mock(LetterOfCreditRepository.class),
            mock(AmendmentRepository.class), mock(LetterOfCreditService.class),
            mock(AmendmentService.class), mock(SwiftImportHistoryService.class),learning());

    private TrainingLearningService learning(){TrainingLearningService service=mock(TrainingLearningService.class);org.mockito.Mockito.when(service.apply(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation->invocation.getArgument(1));return service;}

    @Test void previewsReferencedBeneficiaryWithReviewNotice() {
        String raw=":20:REF\n:31D:271231BERLIN\n:32B:EUR100,\n:59:SEE FIELD 47A\n:47A:BENEFICIARY: ACME\nBERLIN";
        var preview=service.preview(new SwiftImportRequest("lc.swift",raw));
        assertThat(preview.beneficiary()).isEqualTo("ACME\nBERLIN");
        assertThat(preview.warnings()).anyMatch(warning->warning.contains(":47A:"));
        assertThat(preview.rawFields()).anySatisfy(field->{
            assertThat(field.code()).isEqualTo("59");
            assertThat(field.value()).isEqualTo("SEE FIELD 47A");
            assertThat(field.confidence()).isEqualTo("LOW");
        });
    }

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

    @Test void unmappableFieldsAreMarkedAsKeptAndApplicantReferenceIsExplained() {
        String raw=":20:REF\n:31D:271231BERLIN\n:32B:EUR100,\n:40A:IRREVOCABLE\n:50:ACME GMBH\nADD SEE FIELD 47A\n:59:SEE FIELD 47A\n:47A:APPLICANT: ACME GMBH\nMUSTERWEG 1\n70173 STUTTGART\nBENEFICIARY: ZHONGHUA LTD\nSHANGHAI";
        var preview=service.preview(new SwiftImportRequest("lc.swift",raw));
        assertThat(preview.applicant()).isEqualTo("ACME GMBH\nMUSTERWEG 1\n70173 STUTTGART");
        assertThat(preview.warnings()).anyMatch(w->w.contains("Auftraggeber aus Feld :47A:"));
        assertThat(preview.rawFields()).anySatisfy(f->{assertThat(f.code()).isEqualTo("40A");assertThat(f.targetLabel()).contains("Zusatzangabe");assertThat(f.reason()).contains("geht nicht verloren");assertThat(f.unusual()).isFalse();});
        assertThat(preview.rawFields()).anySatisfy(f->{assertThat(f.code()).isEqualTo("50");assertThat(f.notice()).contains("Auftraggeberadresse");});
        assertThat(preview.warnings()).noneMatch(w->w.contains("Zusatzangaben")||w.contains("weitere Angaben"));
        var unresolved=service.preview(new SwiftImportRequest("lc.swift",":20:REF\n:31D:271231BERLIN\n:32B:EUR100,\n:50:ACME\nSEE 47A\n:47A:ALL DOCUMENTS IN ENGLISH"));
        assertThat(unresolved.rawFields()).anySatisfy(f->{assertThat(f.code()).isEqualTo("50");assertThat(f.notice()).contains("kein eindeutig beschrifteter");});
    }
}
