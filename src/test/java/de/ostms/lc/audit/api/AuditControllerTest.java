package de.ostms.lc.audit.api;

import de.ostms.lc.audit.domain.AuditEvent;
import de.ostms.lc.audit.service.AuditChainService;
import de.ostms.lc.audit.service.AuditSearchService;
import de.ostms.lc.audit.service.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuditControllerTest {
 private final AuditService service=mock(AuditService.class);private final AuditSearchService search=mock(AuditSearchService.class);
 private final AuditController controller=new AuditController(service,search,mock(AuditChainService.class));
 private final TestingAuthenticationToken auth=new TestingAuthenticationToken("markus","x");

 @Test void readingTheLogIsRecordedWithFilterAndCount(){
  when(search.search(any(),anyInt())).thenReturn(List.of(new AuditEvent(),new AuditEvent()));
  var result=controller.events(LocalDate.of(2026,10,1),null,"anna",null,null,100000,auth);
  assertThat(result).hasSize(2);
  verify(search).search(any(AuditSearchService.Filter.class),eq(AuditSearchService.MAX_LIMIT)); // the limit is capped
  verify(service).record(eq(auth),eq("AUDIT_VIEWED"),eq("AUDIT"),isNull(),eq("von 2026-10-01, Benutzer anna · 2 Ereignisse"));
 }
 @Test void exportIsRecordedAndUsesTheFilteredRows(){
  when(search.search(any(),anyInt())).thenReturn(List.of(new AuditEvent()));when(service.csv(anyList())).thenReturn(new byte[]{1,2,3});
  var response=controller.export(null,LocalDate.of(2026,10,9),null,"DOCUMENT_",null,auth);
  assertThat(response.getBody()).hasSize(3);assertThat(response.getHeaders().getFirst("Content-Disposition")).contains("Audit-Protokoll.csv");
  verify(search).search(any(AuditSearchService.Filter.class),eq(AuditSearchService.EXPORT_LIMIT));
  verify(service).record(eq(auth),eq("AUDIT_EXPORTED"),eq("AUDIT"),isNull(),eq("bis 2026-10-09, Aktion DOCUMENT_ · 1 Zeilen"));
 }
 @Test void exportWithoutFilterKeepsTheRecentEventsBehaviour(){
  when(service.recent()).thenReturn(List.of(new AuditEvent()));when(service.csv(anyList())).thenReturn(new byte[]{1});
  controller.export(null,null,null,null,null,auth);
  verify(service).recent();verifyNoInteractions(search);verify(service).record(eq(auth),eq("AUDIT_EXPORTED"),eq("AUDIT"),isNull(),eq("ohne Filter · 1 Zeilen"));
 }
}
