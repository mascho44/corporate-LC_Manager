package de.corporate.lc.check.service;
import de.corporate.lc.check.api.*;
import de.corporate.lc.lc.domain.LetterOfCredit;
import de.corporate.lc.lc.repository.LetterOfCreditRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
class DocumentCheckReportServiceTest {
    @Test void separatesAutomaticAssessmentAndManualDecision() throws Exception {
        UUID id=UUID.randomUUID();var lcs=mock(LetterOfCreditRepository.class);var checks=mock(DocumentCheckService.class);
        var lc=new LetterOfCredit();lc.setReference("LC-REPORT");when(lcs.findById(id)).thenReturn(Optional.of(lc));
        var finding=new CheckResult(CheckResult.Severity.OK,"AMOUNT","Amount review","EUR 1000","invoice.pdf","EUR 1200","ACCEPTED","Original reviewed","checker",LocalDateTime.of(2026,10,4,10,0),CheckResult.Severity.DISCREPANCY);
        when(checks.check(id)).thenReturn(new ReviewSummary("GREEN",0,0,1,List.of(finding)));
        var report=new DocumentCheckReportService(checks,lcs).create(id);
        try(var pdf=Loader.loadPDF(report.content())){
            assertThat(new PDFTextStripper().getText(pdf)).contains("Automatic assessment: DISCREPANCY","Manual decision: fulfilled","Rule code: AMOUNT","Decision reason: Original reviewed","Reviewed by: checker","Exact PDF page reference: not recorded","EUR 1000","EUR 1200");
        }
    }
}
