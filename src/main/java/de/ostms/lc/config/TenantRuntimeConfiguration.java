package de.ostms.lc.config;
import de.ostms.lc.tenant.repository.TenantRepository;
import org.springframework.context.annotation.*;
@Configuration public class TenantRuntimeConfiguration {
 @Bean TenantModuleConfiguration tenantModules(TenantRepository tenants){return new TenantModuleConfiguration(tenants);}
}
