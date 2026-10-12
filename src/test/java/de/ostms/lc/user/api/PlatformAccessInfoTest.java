package de.ostms.lc.user.api;
import de.ostms.lc.user.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformAccessInfoTest {
 private final PlatformAdministrationService service=mock(PlatformAdministrationService.class);
 private final PlatformAdministrationController controller=new PlatformAdministrationController(service);

 @Test void aPlatformOnlySessionIsReportedAsTenantlessSoTheLoginPageSkipsTheWorkspaceChooser(){
  var auth=UsernamePasswordAuthenticationToken.authenticated("admin",null,List.of(new SimpleGrantedAuthority(AppUserDetailsService.PLATFORM_ONLY)));
  when(service.enabled(auth)).thenReturn(true);
  assertThat(controller.access(auth)).containsEntry("enabled",true).containsEntry("tenantless",true);
 }
 @Test void aSessionWithTenantAuthoritiesIsNotTenantless(){
  var auth=UsernamePasswordAuthenticationToken.authenticated("markus",null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN"),new SimpleGrantedAuthority("PERM_USER_MANAGE")));
  when(service.enabled(auth)).thenReturn(false);
  assertThat(controller.access(auth)).containsEntry("enabled",false).containsEntry("tenantless",false);
 }
}
