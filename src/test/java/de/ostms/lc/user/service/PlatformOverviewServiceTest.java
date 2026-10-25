package de.ostms.lc.user.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformOverviewServiceTest {
 private final PlatformAdministrationService platform=mock(PlatformAdministrationService.class);
 private final PlatformOverviewStore store=mock(PlatformOverviewStore.class);
 private final PlatformOverviewService service=new PlatformOverviewService(platform,store);
 private final TestingAuthenticationToken auth=new TestingAuthenticationToken("admin","x");
 @Test void deniedWithoutLivePlatformAccessAndNothingIsRead(){
  doThrow(new AccessDeniedException("no")).when(platform).verifyLiveAccess(auth);
  assertThatThrownBy(()->service.memberships(auth)).isInstanceOf(AccessDeniedException.class);
  assertThatThrownBy(()->service.audit(null,10,auth)).isInstanceOf(AccessDeniedException.class);
  verifyNoInteractions(store);
 }
 @Test void auditLimitIsBoundedAndTenantFilterNormalized(){
  when(store.audit(any(),anyInt())).thenReturn(List.of());
  service.audit(" acme ",100000,auth);verify(store).audit("acme",500);
  service.audit("",0,auth);verify(store).audit(null,1);
  service.audit(null,null,auth);verify(store).audit(null,200);
 }
}
