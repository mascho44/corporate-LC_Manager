package de.ostms.lc.config;
import de.ostms.lc.tenant.repository.TenantRepository;
import org.springframework.context.annotation.*;
@Configuration public class TenantRuntimeConfiguration {
 @Bean TenantModuleConfiguration tenantModules(TenantRepository tenants){return new TenantModuleConfiguration(tenants);}
 @Bean InstrumentPermissionConfiguration instrumentPermissions(de.ostms.lc.lc.repository.LetterOfCreditRepository lcs){return new InstrumentPermissionConfiguration(lcs);}
}
