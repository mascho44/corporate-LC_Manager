package de.ostms.lc.document.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.ostms.lc.document.repository.*;
import de.ostms.lc.document.api.DocumentView;
import de.ostms.lc.lc.repository.*;
import de.ostms.lc.check.repository.DocumentCheckDecisionRepository;
import de.ostms.lc.check.service.DocumentCheckService;
import de.ostms.lc.audit.repository.AuditEventRepository;
import org.springframework.stereotype.Service;
import java.util.*;
import java.io.IOException;
@Service
public class CasePackageContents {
 private final ObjectMapper json;private final LetterOfCreditRepository lcs;private final LcDocumentRepository docs;private final AmendmentRepository amendments;private final DocumentDraftRepository drafts;private final DocumentCheckDecisionRepository decisions;private final DocumentCheckService checks;private final AuditEventRepository audit;private final DocumentComparisonRepository comparisons;
 public CasePackageContents(ObjectMapper json,LetterOfCreditRepository lcs,LcDocumentRepository docs,AmendmentRepository amendments,DocumentDraftRepository drafts,DocumentCheckDecisionRepository decisions,DocumentCheckService checks,AuditEventRepository audit,DocumentComparisonRepository comparisons){this.json=json;this.lcs=lcs;this.docs=docs;this.amendments=amendments;this.drafts=drafts;this.decisions=decisions;this.checks=checks;this.audit=audit;this.comparisons=comparisons;}
 public Map<String,byte[]> create(UUID id)throws IOException{
  Map<String,byte[]> files=new LinkedHashMap<>();Set<String> entities=new HashSet<>();entities.add(id.toString());
  put(files,"Struktur/LC.json",lcs.findById(id).orElseThrow());
  var changes=amendments.findByLetterOfCreditIdOrderByImportedAtDesc(id);put(files,"Struktur/Amendments.json",changes);changes.forEach(change->{if(change.getId()!=null)entities.add(change.getId().toString());});
  var documents=docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id);var extracted=json.createArrayNode();
  for(var document:documents){if(document.getId()!=null)entities.add(document.getId().toString());var node=(com.fasterxml.jackson.databind.node.ObjectNode)json.valueToTree(DocumentView.from(document));node.put("extractedText",document.getExtractedText());node.set("ocrEvidence",document.getOcrEvidenceJson()==null?com.fasterxml.jackson.databind.node.NullNode.getInstance():json.readTree(document.getOcrEvidenceJson()));node.set("classificationHistory",ClassificationHistory.read(document.getClassificationHistoryJson()));extracted.add(node);}
  put(files,"Struktur/Dokumentwerte-und-Evidenz.json",extracted);
  put(files,"Pruefung/Befunde.json",checks.check(id));put(files,"Pruefung/Entscheidungen.json",decisions.findByLcId(id));
  var approvals=drafts.findByLcIdOrderByUpdatedAtDesc(id);put(files,"Freigaben/Entwuerfe-und-Freigaben.json",approvals);approvals.forEach(draft->{if(draft.getId()!=null)entities.add(draft.getId().toString());});
  put(files,"Pruefung/Dokumentvergleiche.json",comparisons.findByLcIdOrderByCreatedAtDesc(id));
  put(files,"Audit/Aktenbezogener-Audit-Trail.json",audit.findByEntityIdInOrderByOccurredAtAsc(entities));
  files.put("README.txt",("Pruefakte: strukturierter Export des aktuellen Datenbankstands.\nBefunde sind automatische Vorpruefungen; fachliche Entscheidungen und Freigaben stehen separat.\nLeere Listen bedeuten: keine gespeicherten Eintraege. Altbestand kann keine OCR-Evidenz oder Amendment-Snapshots enthalten.\nAudit umfasst Ereignisse mit LC-ID sowie IDs der enthaltenen Dokumente, Amendments und Entwuerfe; keine globalen Login- oder Adminereignisse.\nDer Export selbst wird nach Erzeugung protokolliert und ist daher nicht in seinem eigenen Audit-Trail enthalten.\nmanifest.json enthaelt SHA-256 der Nutzdateien; es ist keine digitale Signatur und keine Manipulationssicherheitsgarantie.\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
  return files;
 }
 private void put(Map<String,byte[]> files,String name,Object value)throws IOException{files.put(name,json.writerWithDefaultPrettyPrinter().writeValueAsBytes(value));}
}
