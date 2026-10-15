package de.ostms.lc.document.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PageNumberSpellingTest {
    @Test void readsBlattAndPageNo() {
        assertThat(PdfDocumentSplitter.pageNumber("Rechnung 4711\nBlatt 2 von 3")).containsExactly(2,3);
        assertThat(PdfDocumentSplitter.pageNumber("PACKING LIST\nPage No. 1/2")).containsExactly(1,2);
        assertThat(PdfDocumentSplitter.pageNumber("Seite 3")).containsExactly(3,0);
    }
}
