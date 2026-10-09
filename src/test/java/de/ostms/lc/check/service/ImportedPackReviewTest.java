package de.ostms.lc.check.service;
import de.ostms.lc.check.api.*;
import de.ostms.lc.check.repository.DocumentCheckDecisionRepository;
import de.ostms.lc.document.repository.LcDocumentRepository;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import de.ostms.lc.rulepack.InternalPackService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class ImportedPackReviewTest {
 @Test void reviewPinsImportedPackMetadataAndRejectsChangedVersion() {
  var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);var decisions=mock(DocumentCheckDecisionRepository.class);
  var packs=mock(InternalPackService.class);var service=new DocumentCheckService(lcs,docs,decisions);
  ReflectionTestUtils.setField(service,"internalPacks",packs);var id=UUID.randomUUID();var lc=new LetterOfCredit();
  when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of());
  when(decisions.findByLcId(id)).thenReturn(List.of());when(decisions.save(any())).thenAnswer(c->c.getArgument(0));
  var rule=new RuleDefinition("PACK.internal-demo.limit","1.0.0/1.0.0","Own limit","Internal pack","Compare","Partial",null);
  var raw=new CheckResult(CheckResult.Severity.WARNING,rule.id(),"Check", "Internal condition",null,"Missing metadata").withRule(rule);
  when(packs.evaluateSelected(lc,List.of(),java.util.Set.of())).thenReturn(new InternalPackService.PackEvaluation(List.of(raw),java.util.Set.of()));
  var finding=service.check(id).results().stream().filter(r->r.code().equals(rule.id())).findFirst().orElseThrow();
  var saved=service.decide(id,new CheckDecisionRequest(finding.code(),null,"ACCEPTED","Original inspected",finding.reviewFingerprint()),"synthetic-reviewer");
  assertThat(saved.getRuleVersion()).isEqualTo("1.0.0/1.0.0");
  assertThat(saved.getFindingSnapshot()).contains("Internal pack","1.0.0/1.0.0");
  when(decisions.findByLcId(id)).thenReturn(List.of(saved));
  assertThat(service.check(id).results().stream().filter(r->r.code().equals(rule.id())).findFirst().orElseThrow().rule()).isEqualTo(rule);
  var newer=new RuleDefinition(rule.id(),"2.0.0/1.0.0",rule.title(),rule.basis(),rule.explanation(),rule.limitations(),null);
  when(packs.evaluateSelected(lc,List.of(),java.util.Set.of())).thenReturn(new InternalPackService.PackEvaluation(List.of(raw.withRule(newer)),java.util.Set.of()));
  assertThatThrownBy(()->service.decide(id,new CheckDecisionRequest(finding.code(),null,"ACCEPTED","Old review",finding.reviewFingerprint()),"synthetic-reviewer")).isInstanceOf(IllegalArgumentException.class);
 }
}
