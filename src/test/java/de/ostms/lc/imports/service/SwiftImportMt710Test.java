package de.ostms.lc.imports.service;

import de.ostms.lc.imports.api.SwiftImportRequest;
import de.ostms.lc.lc.repository.AmendmentRepository;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import de.ostms.lc.lc.service.AmendmentService;
import de.ostms.lc.lc.service.LetterOfCreditService;
import de.ostms.lc.swift.Mt700Parser;
import de.ostms.lc.swift.Mt707Parser;
import de.ostms.lc.training.service.TrainingLearningService;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

class SwiftImportMt710Test {
 private final LetterOfCreditRepository lcs=mock(LetterOfCreditRepository.class);
 private final SwiftImportService service=new SwiftImportService(new Mt700Parser(),new Mt707Parser(),lcs,mock(AmendmentRepository.class),mock(LetterOfCreditService.class),mock(AmendmentService.class),mock(SwiftImportHistoryService.class),learning());
 private static TrainingLearningService learning(){var s=mock(TrainingLearningService.class);when(s.apply(anyString(),anyString())).thenAnswer(i->i.getArgument(1));return s;}
 static final String MT710=":20:ADV123456\n:21:LC2026000042\n:31C:260901\n:31D:261201FRANKFURT\n:32B:EUR125000,\n:44C:261101\n:50:ACME GMBH\nHAMBURG\n:52A:ISSUBANKXXX\n:59:SUPPLIER LTD\nSHANGHAI\n:46A:+SIGNED COMMERCIAL INVOICE IN 3 ORIGINALS\n+PACKING LIST\n";

 @Test void detectsMt710ByFieldProfileAndByExplicitMarker(){
  assertThat(service.detect(MT710)).isEqualTo("MT710");
  assertThat(service.detect("{1:F01X}{2:I710Y}{4:\n:20:A\n-}")).isEqualTo("MT710");
  assertThat(service.detect(":20:A\n:31D:261201X\n:32B:EUR1,")).isEqualTo("MT700");
  assertThat(service.detect(":20:A\n:21:B\n:26E:1\n:52A:BANK")).isEqualTo("MT707");
 }
 @Test void usesCreditNumberFromField21AndKeepsAdvisingReference(){
  var preview=service.preview(new SwiftImportRequest("lc710.swift",MT710));
  assertThat(preview.messageType()).isEqualTo("MT710");
  assertThat(preview.reference()).isEqualTo("LC2026000042");
  assertThat(preview.valid()).isTrue();
  assertThat(preview.currency()).isEqualTo("EUR");
  assertThat(preview.requiredDocuments()).hasSize(2);
  var lc=new Mt700Parser().parse(MT710);
  assertThat(lc.getReference()).isEqualTo("LC2026000042");
  assertThat(lc.getAdditionalFields()).containsEntry("20 - Referenz der avisierenden Bank","ADV123456").containsKey("MT710 - Nachrichtenart");
  assertThat(lc.getAdditionalFields().keySet()).noneMatch(k->k.startsWith("21 -"));
 }
 @Test void duplicateCreditNumberIsRejected(){
  when(lcs.existsByReference("LC2026000042")).thenReturn(true);
  var preview=service.preview(new SwiftImportRequest("lc710.swift",MT710));
  assertThat(preview.valid()).isFalse();
  assertThat(preview.errors()).anyMatch(e->e.contains("existiert bereits"));
 }
 @Test void plainMt700IsUnchanged(){
  var lc=new Mt700Parser().parse(":20:REF1\n:31D:261201BERLIN\n:32B:EUR100,\n:59:BENE");
  assertThat(lc.getReference()).isEqualTo("REF1");
  assertThat(lc.getAdditionalFields()).doesNotContainKey("MT710 - Nachrichtenart");
 }
}
