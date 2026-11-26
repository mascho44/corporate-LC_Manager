package de.ostms.lc.document.api;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MarkTrainingControllerTest {
 @Test void onlyRealPdfFilesAreAccepted(){
  assertThat(MarkTrainingController.looksLikePdf("%PDF-1.7\n....".getBytes())).isTrue();
  assertThat(MarkTrainingController.looksLikePdf("<html>not a pdf</html>".getBytes())).isFalse();
  assertThat(MarkTrainingController.looksLikePdf(null)).isFalse();
 }
 @Test void filenamesAreCleanedOfPathsAndControlCharacters(){
  assertThat(MarkTrainingController.cleanName("../../etc/pass\nwd.pdf")).doesNotContain("/").doesNotContain("\n");
  assertThat(MarkTrainingController.cleanName("  ")).isEqualTo("training.pdf");
  assertThat(MarkTrainingController.cleanName("a".repeat(500))).hasSize(200);
 }
}
