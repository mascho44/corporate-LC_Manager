package de.ostms.lc.tenant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.tenant.domain.Tenant;
import de.ostms.lc.tenant.repository.TenantRepository;
import de.ostms.lc.user.service.PlatformAdministrationService;
import de.ostms.lc.user.service.TenantAdministrationLock;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class HomeTenantServiceTest {
    private static final UUID OLD = Tenant.DEFAULT_ID;
    private final UUID target = UUID.randomUUID();
    private JdbcTemplate jdbc;
    private TenantRepository tenants;
    private HomeTenantService service;
    private final Authentication auth = mock(Authentication.class);

    @BeforeEach void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:hometenant" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("create table app_role(id uuid primary key, tenant_id uuid not null, name varchar(100) not null, base_role varchar(30) not null, system_role boolean not null)");
        jdbc.execute("create table app_user(id uuid primary key, tenant_id uuid not null, assigned_role_id uuid, platform_administrator boolean not null default false, active boolean not null default true, invitation_pending boolean not null default false, totp_enabled boolean not null default false)");
        jdbc.execute("create table platform_home_tenant(singleton boolean default true primary key, tenant_id uuid not null)");
        jdbc.update("insert into platform_home_tenant(singleton, tenant_id) values (true, ?)", OLD);
        tenants = mock(TenantRepository.class);
        var platform = mock(PlatformAdministrationService.class);
        when(platform.enabled(any())).thenReturn(true);
        service = new HomeTenantService(tenants, platform, mock(TenantAdministrationLock.class), mock(AuditService.class), jdbc);
        var tenant = mock(Tenant.class);
        when(tenant.getId()).thenReturn(target);
        when(tenant.getCode()).thenReturn("platform");
        when(tenant.isActive()).thenReturn(true);
        when(tenants.findByCodeIgnoreCase("platform")).thenReturn(Optional.of(tenant));
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach void tearDown() { TransactionSynchronizationManager.clearSynchronization(); }

    private UUID role(UUID tenant, String name, String base) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into app_role(id, tenant_id, name, base_role, system_role) values (?,?,?,?,true)", id, tenant, name, base);
        return id;
    }

    private UUID user(UUID role, boolean platformAdmin) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into app_user(id, tenant_id, assigned_role_id, platform_administrator, active, invitation_pending, totp_enabled) values (?,?,?,?,true,false,true)", id, OLD, role, platformAdmin);
        return id;
    }

    @Test void accountsMoveByRoleNameOrStandardRoleOfTheSameKind() {
        UUID viewerOld = role(OLD, "Leser", "VIEWER"), editorOld = role(OLD, "Sachbearbeiter", "EDITOR"), adminOld = role(OLD, "Administrator", "ADMIN");
        UUID adminNew = role(target, "Administrator", "ADMIN"), editorNew = role(target, "Editor", "EDITOR"), viewerNew = role(target, "Viewer", "VIEWER");
        UUID platformAdmin = user(viewerOld, true), editor = user(editorOld, false), admin = user(adminOld, false);

        var result = service.change(new HomeTenantService.Request("platform", true), auth);

        assertThat(result.accountsMoved()).isEqualTo(3);
        assertThat(jdbc.queryForObject("select assigned_role_id from app_user where id = ?", UUID.class, platformAdmin)).isEqualTo(viewerNew);
        assertThat(jdbc.queryForObject("select assigned_role_id from app_user where id = ?", UUID.class, editor)).isEqualTo(editorNew);
        assertThat(jdbc.queryForObject("select assigned_role_id from app_user where id = ?", UUID.class, admin)).isEqualTo(adminNew);
        assertThat(jdbc.queryForObject("select count(*) from app_user where tenant_id = ?", Long.class, target)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select tenant_id from platform_home_tenant", UUID.class)).isEqualTo(target);
    }

    @Test void missingRoleKindAbortsWithoutChanges() {
        UUID viewerOld = role(OLD, "Leser", "VIEWER");
        role(target, "Administrator", "ADMIN");
        user(viewerOld, true);
        assertThatThrownBy(() -> service.change(new HomeTenantService.Request("platform", true), auth)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Leser");
    }

    @Test void confirmationAndTargetAreRequired() {
        assertThatThrownBy(() -> service.change(new HomeTenantService.Request("platform", false), auth)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.change(new HomeTenantService.Request("gibt-es-nicht", true), auth)).isInstanceOf(IllegalArgumentException.class);
    }
}
