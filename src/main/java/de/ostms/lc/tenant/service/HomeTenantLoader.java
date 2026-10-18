package de.ostms.lc.tenant.service;

import de.ostms.lc.tenant.domain.HomeTenantAccess;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Reads the configured home tenant once the schema is migrated. A missing table keeps the legacy default. */
@Component
public class HomeTenantLoader implements ApplicationListener<ContextRefreshedEvent> {
    private static final Logger log = LoggerFactory.getLogger(HomeTenantLoader.class);
    private final JdbcTemplate jdbc;

    public HomeTenantLoader(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public void onApplicationEvent(ContextRefreshedEvent event) {
        try {
            UUID configured = jdbc.query("select tenant_id from platform_home_tenant", rs -> rs.next() ? rs.getObject(1, UUID.class) : null);
            if (configured != null) HomeTenantAccess.set(configured);
        } catch (RuntimeException unavailable) {
            log.warn("Heimatmandant nicht lesbar, es gilt der Standardmandant: {}", unavailable.getClass().getSimpleName());
        }
    }
}
