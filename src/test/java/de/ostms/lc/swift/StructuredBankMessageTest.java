package de.ostms.lc.swift;

import de.ostms.lc.imports.api.SwiftImportRequest;
import de.ostms.lc.imports.service.SwiftImportHistoryService;
import de.ostms.lc.imports.service.SwiftImportService;
import de.ostms.lc.lc.repository.AmendmentRepository;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import de.ostms.lc.lc.service.AmendmentService;
import de.ostms.lc.lc.service.LetterOfCreditService;
import de.ostms.lc.lc.service.SwiftMessageService;
import de.ostms.lc.training.service.TrainingLearningService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StructuredBankMessageTest {
 static final String MT734=":20:REF734\n:21:PRESENT9\n:32A:260918EUR125000,\n:77J:LATE SHIPMENT\nINVOICE UNSIGNED\n:77B:HOLD DOCUMENTS AT YOUR DISPOSAL\n";
 static final String MT750=":20:REF750\n:21:PRESENT9\n:32B:EUR125000,\n:71D:EUR50,\n:77J:AMOUNT EXCEEDS CREDIT\n";
 static final String MT701=":27:2/2\n:20:LC2026000042\n:46A:+PACKING LIST\n:47A:+ALL DOCUMENTS IN ENGLISH\n";
 private final SwiftImportService service=build();
 private static SwiftImportService build(){
  var learning=mock(TrainingLearningService.class);when(learning.apply(anyString(),anyString())).thenAnswer(i->i.getArgument(1));
  var s=new SwiftImportService(new Mt700Parser(),new Mt707Parser(),mock(LetterOfCreditRepository.class),mock(AmendmentRepository.class),mock(LetterOfCreditService.class),mock(AmendmentService.class),mock(SwiftImportHistoryService.class),learning);
  ReflectionTestUtils.setField(s,"freeMessages",mock(SwiftMessageService.class));return s;
 }

 @Test void parsesMt734WithLabelledFields(){
  var p=FreeFormatMessage.parse("MT734",MT734);
  assertThat(p.reference()).isEqualTo("REF734");assertThat(p.relatedReference()).isEqualTo("PRESENT9");
  assertThat(p.fields()).containsEntry("32A","260918EUR125000,").containsKey("77J");
  assertThat(p.narrative()).contains(":77J: Abweichungen\nLATE SHIPMENT").contains(":32A: Datum, Währung und Betrag der Einreichung");
 }
 @Test void parsesMt750AndMt701(){
  assertThat(FreeFormatMessage.parse("MT750",MT750).fields()).containsEntry("71D","EUR50,");
  var p=FreeFormatMessage.parse("MT701",MT701);
  assertThat(p.reference()).isEqualTo("LC2026000042");assertThat(p.relatedReference()).isNull();
  assertThat(p.narrative()).contains(":27: Folgenummer").contains(":46A: Geforderte Dokumente");
 }
 @Test void rejectsMissingMandatoryFields(){
  assertThatThrownBy(()->FreeFormatMessage.parse("MT734",":20:X\n:32A:260918EUR1,")).hasMessageContaining(":77J:");
  assertThatThrownBy(()->FreeFormatMessage.parse("MT750",":77J:X")).hasMessageContaining(":20:");
  assertThatThrownBy(()->FreeFormatMessage.parse("MT701",":20:X\n:27:1/1")).hasMessageContaining(":45A:");
  assertThatThrownBy(()->FreeFormatMessage.parse("MT701",":20:X\n:46A:DOCS")).hasMessageContaining(":27:");
 }
 @Test void detectsTheThreeTypesWithoutDisturbingOthers(){
  assertThat(service.detect(MT734)).isEqualTo("MT734");assertThat(service.detect(MT750)).isEqualTo("MT750");assertThat(service.detect(MT701)).isEqualTo("MT701");
  assertThat(service.detect("{1:F01X}{2:I734Y}{4:\n:20:A\n-}")).isEqualTo("MT734");
  assertThat(service.detect(":20:LC1\n:27:1/1\n:31D:261201BERLIN\n:32B:EUR1,\n:45A:GOODS\n:46A:DOCS")).isEqualTo("MT700");
  assertThat(service.detect(":20:A\n:21:B\n:26E:1\n:52A:BANK")).isEqualTo("MT707");
  assertThat(service.detect(":20:A\n:79:TEXT")).isEqualTo("MT799");
 }
 @Test void previewShowsLabelsAndIsValid(){
  var preview=service.preview(new SwiftImportRequest("734.txt",MT734));
  assertThat(preview.messageType()).isEqualTo("MT734");assertThat(preview.valid()).isTrue();assertThat(preview.reference()).isEqualTo("REF734");
  assertThat(preview.rawFields()).extracting("label").contains("Abweichungen","Referenz der einreichenden Bank");
  assertThat(service.preview(new SwiftImportRequest("x.txt",":20:X\n:32A:260918EUR1,\n:77J:LATE\n".replace(":77J:LATE\n",""))).valid()).isFalse();
 }
}
