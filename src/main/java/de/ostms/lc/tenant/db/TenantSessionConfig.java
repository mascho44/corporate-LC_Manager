package de.ostms.lc.tenant.db;

import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TenantSessionConfig {
    @Bean
    static BeanPostProcessor tenantSessionDataSourcePostProcessor() {
        return new BeanPostProcessor() {
            @Override public Object postProcessAfterInitialization(Object bean, String name) {
                return bean instanceof DataSource source && !(bean instanceof TenantSessionDataSource)
                        && "dataSource".equals(name) ? new TenantSessionDataSource(source) : bean;
            }
        };
    }
}
