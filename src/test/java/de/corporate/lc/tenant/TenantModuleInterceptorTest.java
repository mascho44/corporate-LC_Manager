package de.corporate.lc.tenant;
import de.corporate.lc.config.TenantModuleConfiguration;
import de.corporate.lc.tenant.domain.*;
import de.corporate.lc.tenant.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.mock.web.*;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class TenantModuleInterceptorTest {
 static class Registry extends InterceptorRegistry {HandlerInterceptor interceptor(){return (HandlerInterceptor)getInterceptors().get(0);}}
 @Test void resolvesLiveSelectedProfileAndNeverUsesRequestTenantHeader()throws Exception{var repo=mock(TenantRepository.class);var tenant=new Tenant("corp","Corporate","en",false,true);when(repo.findById(tenant.getId())).thenReturn(Optional.of(tenant));var registry=new Registry();new TenantModuleConfiguration(repo).addInterceptors(registry);try(var scope=TenantContext.open(tenant.getId())){var request=new MockHttpServletRequest("GET","/api/settings/approval-thresholds");request.addHeader("X-Tenant-ID",Tenant.DEFAULT_ID.toString());assertThatThrownBy(()->registry.interceptor().preHandle(request,new MockHttpServletResponse(),new Object())).isInstanceOf(AccessDeniedException.class);tenant.updateProfile(true,true);assertThat(registry.interceptor().preHandle(request,new MockHttpServletResponse(),new Object())).isTrue();tenant.setActive(false);assertThatThrownBy(()->registry.interceptor().preHandle(new MockHttpServletRequest("GET","/api/lcs"),new MockHttpServletResponse(),new Object())).isInstanceOf(AccessDeniedException.class);}verify(repo,never()).findById(Tenant.DEFAULT_ID);}
}
