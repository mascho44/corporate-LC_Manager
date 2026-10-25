package de.ostms.lc.user.service;
import de.ostms.lc.user.domain.AppUser;
import de.ostms.lc.user.repository.AppUserRepository;
import de.ostms.lc.tenant.domain.Tenant;
import de.ostms.lc.tenant.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class LanguagePreferencesServiceTest {
 final AppUserRepository users=mock(AppUserRepository.class);final TenantRepository tenants=mock(TenantRepository.class);
 final LanguagePreferencesService service=new LanguagePreferencesService(users,tenants);
 final AppUser user=new AppUser();
 void setup(){when(users.findByUsernameIgnoreCase("test")).thenReturn(Optional.of(user));when(tenants.findById(Tenant.DEFAULT_ID)).thenReturn(Optional.of(new Tenant()));}
 @Test void defaultsToEnglishWithoutUserPreference(){setup();var result=service.get("test");assertThat(result.language()).isEqualTo("en");assertThat(result.preferredLanguage()).isNull();assertThat(result.multiTenantEnabled()).isTrue();}
 @Test void userPreferenceWinsAndCanBeReset(){setup();assertThat(service.update("test","de").language()).isEqualTo("de");assertThat(service.update("test",null).language()).isEqualTo("en");}
 @Test void rejectsUnknownPackBeforeWriting(){setup();assertThatThrownBy(()->service.update("test","../../evil")).isInstanceOf(IllegalArgumentException.class);verify(users,never()).save(any());}
 @Test void refusesNonDefaultTenant(){setup();ReflectionTestUtils.setField(user,"tenantId",UUID.randomUUID());assertThatThrownBy(()->service.get("test")).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);verifyNoInteractions(tenants);}
 @Test void missingTenantIsNotSilentlyReplaced(){setup();when(tenants.findById(Tenant.DEFAULT_ID)).thenReturn(Optional.empty());assertThatThrownBy(()->service.get("test")).isInstanceOf(IllegalStateException.class);}
}
