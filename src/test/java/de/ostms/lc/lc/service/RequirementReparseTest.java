package de.ostms.lc.lc.service;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
class RequirementReparseTest {
 private static final String RAW=":20:X\n:46A:+SIGNED COMMERCIAL INVOICE SHOWING CONTRACT\nNO.: 1 AND L/C NO.\n+PACKING LIST\n:47A:TERMS\n";
 private LetterOfCreditService service(LetterOfCredit lc){
  var repo=mock(LetterOfCreditRepository.class);when(repo.findById(any())).thenReturn(Optional.of(lc));
  return new LetterOfCreditService(repo,null,null);
 }
 @Test void rebuildsOnlyWhileConditionsStillMatchTheOldSplit(){
  var lc=new LetterOfCredit();lc.setRawMessage(RAW);lc.setRequiredDocuments(new ArrayList<>(List.of("SIGNED COMMERCIAL INVOICE SHOWING CONTRACT","NO.: 1 AND L/C NO.","PACKING LIST")));
  var result=service(lc).reparse(UUID.randomUUID());
  assertThat(result.changed()).isTrue();
  assertThat(lc.getRequiredDocuments()).containsExactly("SIGNED COMMERCIAL INVOICE SHOWING CONTRACT NO.: 1 AND L/C NO.","PACKING LIST");
  assertThat(service(lc).reparsePreview(UUID.randomUUID()).changed()).isFalse();
 }
 @Test void manuallyChangedConditionsAreNeverOverwritten(){
  var lc=new LetterOfCredit();lc.setRawMessage(RAW);lc.setRequiredDocuments(new ArrayList<>(List.of("MANUALLY EDITED")));
  var result=service(lc).reparse(UUID.randomUUID());
  assertThat(result.changed()).isFalse();assertThat(result.reason()).contains("geändert");
  assertThat(lc.getRequiredDocuments()).containsExactly("MANUALLY EDITED");
 }
}
