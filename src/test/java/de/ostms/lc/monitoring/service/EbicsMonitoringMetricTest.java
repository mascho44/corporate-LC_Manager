package de.ostms.lc.monitoring.service;
import de.ostms.lc.ebics.EbicsConnection;
import de.ostms.lc.ebics.EbicsConnectionRepository;
import de.ostms.lc.ebics.EbicsStatus;
import de.ostms.lc.check.service.DocumentCheckService;
import de.ostms.lc.document.repository.DocumentDraftRepository;
import de.ostms.lc.imports.repository.SwiftImportRecordRepository;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import de.ostms.lc.lc.service.LcDeadlineService;
import de.ostms.lc.messaging.repository.OutboxMessageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class EbicsMonitoringMetricTest {
 private final EbicsConnectionRepository repo=mock(EbicsConnectionRepository.class);
 private OperationsMonitoringService service(){
  var s=new OperationsMonitoringService(mock(LetterOfCreditRepository.class),mock(DocumentCheckService.class),mock(LcDeadlineService.class),mock(DocumentDraftRepository.class),mock(SwiftImportRecordRepository.class),mock(OutboxMessageRepository.class));
  ReflectionTestUtils.setField(s,"ebics",repo);return s;
 }
 private EbicsConnection connection(EbicsStatus status,String lastFetch){
  var c=new EbicsConnection();c.setStatus(status);if(lastFetch!=null)c.recordFetch(lastFetch);when(repo.findCurrent()).thenReturn(Optional.of(c));return c;
 }
 @Test void withoutAConnectionTheMetricIsUnavailableButNoLongerClaimsNotImplemented(){
  when(repo.findCurrent()).thenReturn(Optional.empty());
  var m=service().ebicsMetric();assertThat(m.available()).isFalse();assertThat(m.description()).isEqualTo("Keine EBICS-Verbindung eingerichtet.").doesNotContain("nicht implementiert");
 }
 @Test void aHealthyConnectionCountsZeroProblems(){
  connection(EbicsStatus.ACTIVE,"2 neu, 0 bekannt");
  var m=service().ebicsMetric();assertThat(m.available()).isTrue();assertThat(m.value()).isZero();assertThat(m.description()).contains("aktiv").contains("letzter Abruf");
 }
 @Test void failedKeySetupAndFailedFetchAreCountedSeparately(){
  connection(EbicsStatus.ERROR,null);assertThat(service().ebicsMetric().value()).isEqualTo(1);
  assertThat(service().ebicsMetric().description()).contains("Schlüsseleinrichtung fehlgeschlagen").contains("noch kein Abruf");
  connection(EbicsStatus.ACTIVE,"0 neu, 0 bekannt · Fehler: MT700: Wrong returned HTTP code: 500");
  var fetchFailed=service().ebicsMetric();assertThat(fetchFailed.value()).isEqualTo(1);assertThat(fetchFailed.description()).contains("letzter Abruf mit Fehler");
  connection(EbicsStatus.ERROR,"Fehler: x");assertThat(service().ebicsMetric().value()).isEqualTo(2);
 }
 @Test void pendingBankReleaseIsNotAnError(){
  connection(EbicsStatus.KEYS_SENT,null);var m=service().ebicsMetric();assertThat(m.value()).isZero();assertThat(m.description()).contains("wartet auf Freigabe der Bank");
 }
}
