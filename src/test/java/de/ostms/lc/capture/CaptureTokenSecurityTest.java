package de.ostms.lc.capture;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.config.SecurityConfig;
import de.ostms.lc.document.api.DocumentInboxController;
import de.ostms.lc.document.service.DocumentInboxService;
import de.ostms.lc.document.service.InboxAvisService;
import de.ostms.lc.tenant.domain.TenantContext;
import de.ostms.lc.tenant.service.TenantMembershipService;
import de.ostms.lc.training.service.AdvisingTrainingService;
import de.ostms.lc.user.repository.AppUserRepository;
import de.ostms.lc.user.service.AppUserDetailsService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({DocumentInboxController.class,CaptureUploadController.class}) @Import(SecurityConfig.class)
class CaptureTokenSecurityTest {
 @Autowired MockMvc mvc;
 @MockitoBean CaptureTokenService tokens;@MockitoBean CaptureUploadService uploads;@MockitoBean DocumentInboxService inbox;@MockitoBean AuditService audit;@MockitoBean AdvisingTrainingService advising;@MockitoBean InboxAvisService avis;
 @MockitoBean de.ostms.lc.document.service.DocumentMetadataTraining metadataTraining;@MockitoBean de.ostms.lc.document.service.SpatialLayoutTraining spatialTraining;@MockitoBean de.ostms.lc.document.service.PdfPagePreviewService pagePreview;
 @MockitoBean AppUserDetailsService details;@MockitoBean AppUserRepository users;@MockitoBean TenantMembershipService memberships;@MockitoBean de.ostms.lc.tenant.repository.TenantRepository tenantRepository;
 final UUID tenant=UUID.randomUUID();
 final MockMultipartFile pdf=new MockMultipartFile("file","scan.pdf","application/pdf","%PDF-1.4".getBytes());

 private void validToken(){when(tokens.authenticate("lcc_good")).thenReturn(Optional.of(new CaptureTokenService.Identity(UUID.randomUUID(),tenant,"Poststelle Scanner 2")));}

 @Test void validTokenMayUploadWithoutSessionOrCsrfAndWorksInItsTenant()throws Exception{
  validToken();
  var seen=new UUID[1];
  when(inbox.receive(any(),anyString())).thenAnswer(call->{seen[0]=TenantContext.currentId();return List.of();});
  var result=mvc.perform(multipart("/api/inbox").file(pdf).header("Authorization","Bearer lcc_good")).andExpect(status().isAccepted()).andReturn();
  assertThat(result.getRequest().getSession(false)).isNull();
  assertThat(seen[0]).isEqualTo(tenant);
  var user=ArgumentCaptor.forClass(String.class);
  verify(inbox).receive(any(),user.capture());
  assertThat(user.getValue()).isEqualTo("capture:Poststelle Scanner 2");
 }

 @Test void tokenWorksForTheCaptureUploadToo()throws Exception{
  validToken();
  when(uploads.receive(any(),any(),anyString())).thenReturn(List.of());
  mvc.perform(multipart("/api/capture/upload").file(pdf).param("batchId","Stapel 17").header("Authorization","Bearer lcc_good")).andExpect(status().isAccepted());
  var source=ArgumentCaptor.forClass(String.class);
  verify(uploads).receive(any(),any(),source.capture());
  assertThat(source.getValue()).isEqualTo("capture:Poststelle Scanner 2");
 }

 @Test void tokenIsRefusedForAnythingButTheDelivery()throws Exception{
  validToken();
  mvc.perform(get("/api/inbox").header("Authorization","Bearer lcc_good")).andExpect(status().isForbidden());
  mvc.perform(get("/api/inbox-capture").header("Authorization","Bearer lcc_good")).andExpect(status().isForbidden());
  mvc.perform(delete("/api/inbox/"+UUID.randomUUID()).header("Authorization","Bearer lcc_good")).andExpect(status().isForbidden());
  mvc.perform(get("/api/lcs").header("Authorization","Bearer lcc_good")).andExpect(status().isForbidden());
  verifyNoInteractions(inbox);verifyNoInteractions(uploads);
 }

 @Test void unknownOrRevokedTokensAndPlainRequestsAreRefused()throws Exception{
  when(tokens.authenticate(anyString())).thenReturn(Optional.empty());
  mvc.perform(multipart("/api/inbox").file(pdf).header("Authorization","Bearer lcc_revoked")).andExpect(status().isUnauthorized());
  mvc.perform(multipart("/api/capture/upload").file(pdf).header("Authorization","Bearer lcc_revoked")).andExpect(status().isUnauthorized());
  // Without any login the request is stopped by the CSRF check before authorisation, still before it reaches a controller.
  mvc.perform(multipart("/api/inbox").file(pdf)).andExpect(status().isForbidden());
  verifyNoInteractions(inbox);verifyNoInteractions(uploads);
 }
}
