package de.ostms.lc.imports.service;

import de.ostms.lc.imports.api.SwiftImportRequest;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.repository.AmendmentRepository;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import de.ostms.lc.lc.service.AmendmentService;
import de.ostms.lc.lc.service.LetterOfCreditService;
import de.ostms.lc.lc.service.SwiftMessageService;
import de.ostms.lc.swift.FreeFormatMessage;
import de.ostms.lc.swift.Mt700Parser;
import de.ostms.lc.swift.Mt707Parser;
import de.ostms.lc.training.service.TrainingLearningService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SwiftImportGuaranteeAndMessageTest {
 private final LetterOfCreditRepository lcs=mock(LetterOfCreditRepository.class);
 private final LetterOfCreditService lcService=mock(LetterOfCreditService.class);
 private final SwiftMessageService messages=mock(SwiftMessageService.class);
 private final SwiftImportService service=build();
 private SwiftImportService build(){
  var learning=mock(TrainingLearningService.class);when(learning.apply(anyString(),anyString())).thenAnswer(i->i.getArgument(1));
  var s=new SwiftImportService(new Mt700Parser(),new Mt707Parser(),lcs,mock(AmendmentRepository.class),lcService,mock(AmendmentService.class),mock(SwiftImportHistoryService.class),learning);
  ReflectionTestUtils.setField(s,"freeMessages",messages);return s;
 }
 static final String MT760=de.ostms.lc.swift.Mt760AndFreeFormatTest.MT760;
 static final String MT799=":20:BANKREF9\n:21:LC2026000042\n:79:PLEASE ADVISE STATUS.\n";

 @Test void detectsGuaranteesAndFreeFormatMessages(){
  assertThat(service.detect(MT760)).isEqualTo("MT760");
  assertThat(service.detect(MT799)).isEqualTo("MT799");
  assertThat(service.detect("{1:F01X}{2:I199Y}{4:\n:20:A\n:79:T\n-}")).isEqualTo("MT199");
  assertThat(service.detect("{1:F01X}{2:O799Y}{4:\n:20:A\n:21:B\n:79:T\n-}")).isEqualTo("MT799");
  assertThat(service.detect(":20:R\n:31D:261201X\n:32B:EUR1,\n:79:NOTE")).isEqualTo("MT700");
 }
 @Test void guaranteePreviewShowsTheDataAndRejectsDuplicates(){
  var preview=service.preview(new SwiftImportRequest("g.swift",MT760));
  assertThat(preview.messageType()).isEqualTo("MT760");assertThat(preview.valid()).isTrue();
  assertThat(preview.reference()).isEqualTo("GTEE20267829");assertThat(preview.currency()).isEqualTo("EUR");assertThat(preview.expiryDate()).hasToString("2027-12-31");
  when(lcs.existsByReference("GTEE20267829")).thenReturn(true);
  var duplicate=service.preview(new SwiftImportRequest("g.swift",MT760));
  assertThat(duplicate.valid()).isFalse();assertThat(duplicate.duplicate()).isTrue();
 }
 @Test void guaranteeExecutionCreatesTheDossierThroughTheLcService(){
  var created=new LetterOfCredit();when(lcService.importMt760(anyString())).thenReturn(created);
  assertThat(service.execute(new SwiftImportRequest("g.swift",MT760))).isSameAs(created);
  verify(lcService).importMt760(anyString());verify(lcService,never()).importMt700(anyString());
 }
 @Test void freeFormatPreviewNamesTheTargetDossierOrWarnsAboutNone(){
  var parsed=FreeFormatMessage.parse("MT799",MT799);
  var lc=new LetterOfCredit();lc.setReference("LC2026000042");
  when(messages.findTarget(any())).thenReturn(Optional.of(lc));
  var linked=service.preview(new SwiftImportRequest("m.swift",MT799));
  assertThat(linked.valid()).isTrue();assertThat(linked.reference()).isEqualTo("BANKREF9");assertThat(linked.warnings()).anyMatch(w->w.contains("LC2026000042")&&w.contains("zugeordnet"));
  when(messages.findTarget(any())).thenReturn(Optional.empty());
  assertThat(service.preview(new SwiftImportRequest("m.swift",MT799)).warnings()).anyMatch(w->w.contains("ohne Zuordnung"));
  assertThat(parsed.relatedReference()).isEqualTo("LC2026000042");
 }
 @Test void freeFormatMessageIsStoredOnExecuteAndDuplicatesAreRejected(){
  when(messages.importMessage(eq("MT799"),anyString(),eq("IMPORT"),any())).thenReturn(null);
  service.execute(new SwiftImportRequest("m.swift",MT799));
  verify(messages).importMessage(eq("MT799"),anyString(),eq("IMPORT"),any());
  when(messages.isDuplicate(eq("MT799"),eq("BANKREF9"),anyString())).thenReturn(true);
  var preview=service.preview(new SwiftImportRequest("m.swift",MT799));
  assertThat(preview.valid()).isFalse();assertThat(preview.errors()).anyMatch(e->e.contains("bereits importiert"));
 }
 @Test void freeFormatWithoutNarrativeIsInvalid(){
  var preview=service.preview(new SwiftImportRequest("m.swift","{2:I799Y}\n:20:X\n:21:Y\n"));
  assertThat(preview.valid()).isFalse();assertThat(preview.errors()).anyMatch(e->e.contains(":79:"));
 }
}
