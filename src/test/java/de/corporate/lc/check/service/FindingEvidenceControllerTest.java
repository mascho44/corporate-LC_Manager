package de.corporate.lc.check.service;
import de.corporate.lc.check.api.*;
import de.corporate.lc.document.domain.LcDocument;
import de.corporate.lc.document.repository.LcDocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class FindingEvidenceControllerTest {
 @Test void duplicatesAndMissingFindingAreNotGuessed(){
  var checks=mock(DocumentCheckService.class);var docs=mock(LcDocumentRepository.class);var id=UUID.randomUUID();var finding=new CheckResult(CheckResult.Severity.WARNING,"TEST","Review","LC condition","invoice.pdf","Invoice 123");
  when(checks.check(id)).thenReturn(new ReviewSummary("REVIEW",0,1,0,List.of(finding)));
  var a=new LcDocument();a.setOriginalFilename("invoice.pdf");var b=new LcDocument();b.setOriginalFilename("invoice.pdf");when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(a,b));
  var controller=new FindingEvidenceController(checks,docs);var result=controller.evidence(id,"TEST","invoice.pdf");assertThat(result.documentId()).isNull();assertThat(result.location().status()).isEqualTo("AMBIGUOUS_DOCUMENT");
  assertThatThrownBy(()->controller.evidence(id,"MISSING","invoice.pdf")).isInstanceOf(ResponseStatusException.class);
  verify(docs,never()).findAll();
 }
}
