package de.ostms.lc.document.api;
import de.ostms.lc.document.service.SplitTrainingQuality;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
@RestController
@RequestMapping("/api/training/document-types")
public class SplitTrainingQualityController {
 private final SplitTrainingQuality quality;
 @org.springframework.beans.factory.annotation.Autowired private de.ostms.lc.document.service.RecognitionBenchmark benchmark;
 public SplitTrainingQualityController(SplitTrainingQuality quality){this.quality=quality;}
 @GetMapping("/quality")
 public ResponseEntity<SplitTrainingQuality.Report> report()throws Exception{return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(quality.report());}
 @GetMapping("/benchmark")
 public ResponseEntity<de.ostms.lc.document.service.RecognitionBenchmark.Report> benchmark()throws Exception{return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(benchmark.run());}
}
