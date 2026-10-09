package de.ostms.lc.document.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
class RecognitionBenchmarkTest {
 @Test void fixedCorpusHasReproducibleResultsAndNoTenantTrainingInput()throws Exception{
  var benchmark=new RecognitionBenchmark(new ObjectMapper());var report=benchmark.run();
  assertThat(report.examples()).isEqualTo(11);assertThat(report.pages()).isEqualTo(14);assertThat(report.correctTypes()).isEqualTo(14);
  assertThat(report.cases()).allMatch(RecognitionBenchmark.CaseResult::exactSplit);
  assertThat(report.boundaries().precision()).isEqualTo(1);assertThat(report.boundaries().recall()).isEqualTo(1);
  assertThat(report.metadata().get("lcReference").correct()).isEqualTo(6);assertThat(report.metadata().get("lcReference").falsePositives()).isZero();
  assertThat(report.corpusSha256()).hasSize(64);assertThat(report.engineSha256()).hasSize(64);assertThat(report.scope()).contains("NOT_REAL_SCAN");
  assertThat(benchmark.run()).isSameAs(report);
 }
 @Test void emptyCorpusDoesNotClaimPerfectPrecisionOrRecall()throws Exception{
  var report=RecognitionBenchmark.evaluate(new RecognitionBenchmark.Corpus("empty",List.of()),"none");
  assertThat(report.pages()).isZero();assertThat(report.boundaries().precision()).isNull();assertThat(report.boundaries().recall()).isNull();
 }
 @Test void incorrectMetadataAndTypesAreNotCountedAsSuccess(){
  var counter=new RecognitionBenchmark.Counter();counter.add("expected","different");counter.add(null,"false positive");counter.add(null,null);
  var counts=counter.view();assertThat(counts.correct()).isZero();assertThat(counts.missed()).isEqualTo(1);assertThat(counts.falsePositives()).isEqualTo(2);assertThat(counts.correctNegatives()).isEqualTo(1);
 }
}
