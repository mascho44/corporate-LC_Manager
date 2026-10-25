package de.ostms.lc.user.api;
import de.ostms.lc.user.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformPasswordChangeTest {
 private final PlatformAdministrationService service=mock(PlatformAdministrationService.class);private final IdentityCredentialService credentials=mock(IdentityCredentialService.class);
 private final de.ostms.lc.audit.service.AuditService audit=mock(de.ostms.lc.audit.service.AuditService.class);private final Authentication auth=mock(Authentication.class);
 private PlatformAdministrationController controller(){var c=new PlatformAdministrationController(service);ReflectionTestUtils.setField(c,"credentials",credentials);ReflectionTestUtils.setField(c,"audit",audit);return c;}

 @Test void aPlatformAccountChangesItsOwnPasswordAndItIsAudited(){
  when(auth.getName()).thenReturn("platform");
  controller().changePassword(new PlatformAdministrationController.PasswordChange("Old-Password-1","New-Password-2"),auth);
  verify(service).verifyLiveAccess(auth);verify(credentials).changeOwnPassword("platform","Old-Password-1","New-Password-2");
  verify(audit).record(eq(auth),eq("PASSWORD_CHANGED"),eq("USER"),eq("platform"),any());
 }
 @Test void withoutLivePlatformAccessNothingIsChanged(){
  doThrow(new AccessDeniedException("no")).when(service).verifyLiveAccess(auth);
  assertThatThrownBy(()->controller().changePassword(new PlatformAdministrationController.PasswordChange("a","b"),auth)).isInstanceOf(AccessDeniedException.class);
  verifyNoInteractions(credentials,audit);
 }
}
