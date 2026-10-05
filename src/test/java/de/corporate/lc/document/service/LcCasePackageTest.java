package de.corporate.lc.document.service;
import de.corporate.lc.document.repository.*;
import de.corporate.lc.lc.repository.*;
import de.corporate.lc.lc.domain.LetterOfCredit;
import de.corporate.lc.document.domain.*;
import de.corporate.lc.check.service.DocumentCheckReportService;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;import java.io.*;import java.util.zip.*;
import static org.mockito.Mockito.*;import static org.assertj.core.api.Assertions.*;
class LcCasePackageTest {
 @Test void manifestMatchesEveryExportedPayloadAndDuplicateNamesArePreserved()throws Exception{
  UUID id=UUID.randomUUID();var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);var amendments=mock(AmendmentRepository.class);var reports=mock(DocumentCheckReportService.class);var contents=mock(CasePackageContents.class);
  var lc=new LetterOfCredit();lc.setReference("LC-123");when(lcs.findById(id)).thenReturn(Optional.of(lc));
  var a=new LcDocument();a.setOriginalFilename("invoice.pdf");a.setContent(new byte[]{1,2,3});var b=new LcDocument();b.setOriginalFilename("invoice.pdf");b.setContent(new byte[]{4,5});when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(a,b));when(amendments.findByLetterOfCreditIdOrderByImportedAtDesc(id)).thenReturn(List.of());when(reports.create(id)).thenReturn(new DocumentCheckReportService.Report("report.pdf",new byte[]{9}));when(contents.create(id)).thenReturn(Map.of("Audit/Aktenbezogener-Audit-Trail.json","[]".getBytes()));
  var service=new LcDossierExportService(lcs,docs,amendments,reports,contents);var result=service.create(id);Map<String,byte[]> files=new HashMap<>();
  try(var zip=new ZipInputStream(new ByteArrayInputStream(result.content()))){ZipEntry entry;while((entry=zip.getNextEntry())!=null)files.put(entry.getName(),zip.readAllBytes());}
  assertThat(files).containsKeys("Dokumente/invoice.pdf","Dokumente/invoice-2.pdf","Audit/Aktenbezogener-Audit-Trail.json","manifest.json");
  var manifest=new ObjectMapper().readTree(files.get("manifest.json"));assertThat(manifest.path("files").size()).isEqualTo(files.size()-1);
  for(var entry:manifest.path("files")){var bytes=files.get(entry.path("path").asText());assertThat(bytes).isNotNull();assertThat(entry.path("size").asInt()).isEqualTo(bytes.length);assertThat(entry.path("sha256").asText()).isEqualTo(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));}
 }
}
