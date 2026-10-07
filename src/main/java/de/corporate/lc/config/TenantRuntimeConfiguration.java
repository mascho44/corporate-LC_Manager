package de.corporate.lc.config;
import de.corporate.lc.tenant.repository.TenantRepository;
import org.springframework.context.annotation.*;
@Configuration public class TenantRuntimeConfiguration {
 @Bean TenantModuleConfiguration tenantModules(TenantRepository tenants){return new TenantModuleConfiguration(tenants);}
}
