package de.corporate.lc.document.service;
import de.corporate.lc.document.repository.*;import de.corporate.lc.document.domain.*;
import de.corporate.lc.lc.repository.*;import de.corporate.lc.lc.domain.*;
import de.corporate.lc.check.repository.DocumentCheckDecisionRepository;import de.corporate.lc.check.service.DocumentCheckService;import de.corporate.lc.check.api.ReviewSummary;
import de.corporate.lc.audit.repository.AuditEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;import org.junit.jupiter.api.Test;import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;import static org.mockito.Mockito.*;import static org.assertj.core.api.Assertions.*;
class CasePackageContentsTest {
 @Test void includesEvidenceAndApprovalsButQueriesOnlyCaseAuditIds()throws Exception{
  UUID id=UUID.randomUUID(),docId=UUID.randomUUID(),draftId=UUID.randomUUID();var json=new ObjectMapper().findAndRegisterModules();var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);var amendments=mock(AmendmentRepository.class);var drafts=mock(DocumentDraftRepository.class);var decisions=mock(DocumentCheckDecisionRepository.class);var checks=mock(DocumentCheckService.class);var audit=mock(AuditEventRepository.class);var comparisons=mock(DocumentComparisonRepository.class);
  var lc=new LetterOfCredit();lc.setReference("LC123");when(lcs.findById(id)).thenReturn(Optional.of(lc));var doc=new LcDocument();ReflectionTestUtils.setField(doc,"id",docId);doc.setDocumentType(DocumentType.COMMERCIAL_INVOICE);doc.setExtractedText("INVOICE EVIDENCE");doc.setOcrEvidenceJson("{\"engineVersion\":\"tesseract 5\"}");when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(doc));
  var draft=new DocumentDraft();ReflectionTestUtils.setField(draft,"id",draftId);draft.setCheckedBy("checker");draft.setApprovedBy("approver");draft.setApprovalsJson("[{\"username\":\"approver\"}]");when(drafts.findByLcIdOrderByUpdatedAtDesc(id)).thenReturn(List.of(draft));when(checks.check(id)).thenReturn(new ReviewSummary("GREEN",0,0,0,List.of()));
  var service=new CasePackageContents(json,lcs,docs,amendments,drafts,decisions,checks,audit,comparisons);var files=service.create(id);
  assertThat(files).containsKeys("Struktur/LC.json","Struktur/Amendments.json","Pruefung/Befunde.json","Pruefung/Entscheidungen.json","Freigaben/Entwuerfe-und-Freigaben.json","Audit/Aktenbezogener-Audit-Trail.json");
  assertThat(new String(files.get("Struktur/Dokumentwerte-und-Evidenz.json"),java.nio.charset.StandardCharsets.UTF_8)).contains("INVOICE EVIDENCE","tesseract 5");assertThat(new String(files.get("Freigaben/Entwuerfe-und-Freigaben.json"),java.nio.charset.StandardCharsets.UTF_8)).contains("checker","approver");verify(audit).findByEntityIdInOrderByOccurredAtAsc(argThat(ids->ids.size()==3&&ids.containsAll(List.of(id.toString(),docId.toString(),draftId.toString()))));verify(audit,never()).findAll();
 }
}
