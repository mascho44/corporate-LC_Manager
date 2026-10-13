package de.ostms.lc.document.service;

import de.ostms.lc.document.domain.DocumentTemplate;
import de.ostms.lc.document.repository.DocumentTemplateRepository;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentTemplateOutlineTest {
    private byte[] docx(int paragraphs) throws Exception {
        try (var document = new XWPFDocument(); var out = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("PACKING LIST");
            document.createParagraph().createRun().setText("   ");
            for (int i = 1; i <= paragraphs; i++) document.createParagraph().createRun().setText("Zeile " + i + " ${placeholder}");
            var table = document.createTable(1, 2);
            table.getRow(0).getCell(0).setText("Pos");
            table.getRow(0).getCell(1).setText("Beschreibung");
            document.write(out);
            return out.toByteArray();
        }
    }

    private DocumentTemplateService service(byte[] content) {
        var repository = mock(DocumentTemplateRepository.class);
        var template = new DocumentTemplate();
        template.setContent(content);
        when(repository.findById(org.mockito.ArgumentMatchers.any(UUID.class))).thenReturn(Optional.of(template));
        return new DocumentTemplateService(repository, null);
    }

    @Test void outlineListsTheFirstReadableLinesAndSkipsBlanks() throws Exception {
        var lines = service(docx(3)).outline(UUID.randomUUID());
        assertThat(lines).containsExactly("PACKING LIST", "Zeile 1 ${placeholder}", "Zeile 2 ${placeholder}", "Zeile 3 ${placeholder}", "Pos | Beschreibung");
    }

    @Test void outlineIsLimitedAndAnUnreadableFileGivesNothing() throws Exception {
        assertThat(service(docx(30)).outline(UUID.randomUUID())).hasSize(10);
        assertThat(service("kein docx".getBytes()).outline(UUID.randomUUID())).isEmpty();
    }
}
