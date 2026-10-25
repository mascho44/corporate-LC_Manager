package de.ostms.lc.check.api;
import de.ostms.lc.document.domain.LcDocument;
import de.ostms.lc.document.service.OcrEvidence;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class FindingPagesTest {
 private static LcDocument doc()throws Exception{
  var d=new LcDocument();d.setOriginalFilename("invoice.pdf");
  var ocr=new OcrEvidence("t","m",200,0.0,List.of(
   new OcrEvidence.Word("Packing",.9,1,10,10,60,20),new OcrEvidence.Word("slip",.9,1,80,10,40,20),
   new OcrEvidence.Word("Net",.9,2,10,10,30,20),new OcrEvidence.Word("weight",.9,2,50,10,50,20),new OcrEvidence.Word("12",.9,2,110,10,20,20),
   new OcrEvidence.Word("Net",.9,3,10,10,30,20),new OcrEvidence.Word("weight",.9,3,50,10,50,20),new OcrEvidence.Word("12",.9,3,110,10,20,20)),List.of());
  d.setOcrEvidenceJson(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(ocr));
  return d;
 }
 private static CheckResult result(CheckResult.Severity severity,String document,String evidence){
  return new CheckResult(severity,"X","m",null,document,evidence,null,null,null,null,null,null);
 }
 @Test void openFindingsMarkTheirPagesWithTheWorstSeverity()throws Exception{
  var marks=FindingEvidenceController.pageMarks(doc(),List.of(
   result(CheckResult.Severity.WARNING,"invoice.pdf","Packing slip"),
   result(CheckResult.Severity.DISCREPANCY,"invoice.pdf","Packing slip"),
   result(CheckResult.Severity.OK,"invoice.pdf","Net weight 12"),
   result(CheckResult.Severity.WARNING,"other.pdf","Net weight 12"),
   result(CheckResult.Severity.WARNING,"invoice.pdf",null)));
  assertThat(marks).hasSize(1);
  assertThat(marks.get(0).page()).isEqualTo(1);assertThat(marks.get(0).count()).isEqualTo(2);assertThat(marks.get(0).severity()).isEqualTo("DISCREPANCY");
 }
 @Test void ambiguousEvidenceMarksEveryMatchingPage()throws Exception{
  var marks=FindingEvidenceController.pageMarks(doc(),List.of(result(CheckResult.Severity.WARNING,"invoice.pdf","Net weight 12")));
  assertThat(marks).extracting("page").containsExactly(2,3);
  assertThat(marks).extracting("severity").containsOnly("WARNING");
 }
 @Test void evidenceWithoutALocationMarksNothing()throws Exception{
  assertThat(FindingEvidenceController.pageMarks(doc(),List.of(result(CheckResult.Severity.WARNING,"invoice.pdf","never printed anywhere")))).isEmpty();
 }
}
