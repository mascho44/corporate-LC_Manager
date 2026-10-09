package de.ostms.lc.audit.service;

import de.ostms.lc.audit.domain.AuditEvent;
import de.ostms.lc.audit.repository.AuditEventRepository;
import de.ostms.lc.messaging.service.OutboxService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AuditContextTest {
 @AfterEach void clear(){RequestContextHolder.resetRequestAttributes();SecurityContextHolder.clearContext();}

 private AuditEvent record(MockHttpServletRequest request,String user,boolean successful){
  RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  var auth=new TestingAuthenticationToken(user,"x","ROLE_ADMIN","PERM_AUDIT_VIEW","ROLE_USER");auth.setAuthenticated(true);SecurityContextHolder.getContext().setAuthentication(auth);
  var repository=mock(AuditEventRepository.class);
  var service=new AuditService(repository,mock(OutboxService.class));
  service.record(user,"LC_UPDATED","LETTER_OF_CREDIT","LC-1",successful?"ok":"Zugriff verweigert",successful,null);
  var captor=org.mockito.ArgumentCaptor.forClass(AuditEvent.class);verify(repository).save(captor.capture());return captor.getValue();
 }

 @Test void eventCarriesRequestContext(){
  var request=new MockHttpServletRequest();request.setRemoteAddr("10.0.0.7");request.addHeader("User-Agent","Mozilla/5.0\nInjected: evil");request.setAttribute(AuditContext.REQUEST_ID_ATTRIBUTE,"req-abc123");
  request.getSession(true);
  var event=record(request,"markus",true);
  assertThat(event.getIpAddress()).isEqualTo("10.0.0.7");assertThat(event.getUserAgent()).isEqualTo("Mozilla/5.0 Injected: evil");
  assertThat(event.getRequestId()).isEqualTo("req-abc123");assertThat(event.getSessionRef()).hasSize(16).isEqualTo(AuditContext.hash(request.getSession().getId())).isNotEqualTo(request.getSession().getId());
  assertThat(event.getActorRoles()).isEqualTo("ADMIN,USER");assertThat(event.getOccurredAtUtc()).isNotNull();assertThat(event.getFailureReason()).isNull();
 }
 @Test void forwardedAddressUsesTheEntryTheTrustedProxyAppendedNeverTheClientSuppliedOne(){
  var request=new MockHttpServletRequest();request.setRemoteAddr("172.18.0.2");request.addHeader("X-Forwarded-For","1.2.3.4, 203.0.113.9");
  assertThat(record(request,"markus",true).getIpAddress()).isEqualTo("203.0.113.9");
  RequestContextHolder.resetRequestAttributes();SecurityContextHolder.clearContext();
  var bad=new MockHttpServletRequest();bad.setRemoteAddr("172.18.0.2");bad.addHeader("X-Forwarded-For","not-an-ip<script>");
  assertThat(record(bad,"markus",true).getIpAddress()).isEqualTo("172.18.0.2");
 }
 @Test void failedEventsGetAnExplicitReasonAndWorkWithoutARequest(){
  var event=record(new MockHttpServletRequest(),"markus",false);
  assertThat(event.isSuccessful()).isFalse();assertThat(event.getFailureReason()).isEqualTo("Zugriff verweigert");
  RequestContextHolder.resetRequestAttributes();SecurityContextHolder.clearContext();
  var repository=mock(AuditEventRepository.class);
  new AuditService(repository,mock(OutboxService.class)).record("system","LC_UPDATED","LETTER_OF_CREDIT","LC-2","Hintergrund",true,null);
  var captor=org.mockito.ArgumentCaptor.forClass(AuditEvent.class);verify(repository).save(captor.capture());
  assertThat(captor.getValue().getIpAddress()).isNull();assertThat(captor.getValue().getRequestId()).isNull();assertThat(captor.getValue().getActorRoles()).isNull();
 }
 @Test void requestIdFilterSetsAServerGeneratedIdAndIgnoresClientSuppliedOnes()throws Exception{
  var request=new MockHttpServletRequest();request.addHeader("X-Request-Id","client-chosen");var response=new MockHttpServletResponse();
  new RequestIdFilter().doFilter(request,response,(req,res)->{});
  String id=response.getHeader("X-Request-Id");
  assertThat(id).startsWith("req-").hasSize(24).isNotEqualTo("client-chosen");assertThat(request.getAttribute(AuditContext.REQUEST_ID_ATTRIBUTE)).isEqualTo(id);
 }
}
