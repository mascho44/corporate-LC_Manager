package de.ostms.lc.tenant.service;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.tenant.domain.HomeTenantAccess;
import de.ostms.lc.tenant.domain.Tenant;
import de.ostms.lc.tenant.domain.TenantContext;
import de.ostms.lc.tenant.repository.TenantRepository;
import de.ostms.lc.user.service.PlatformAdministrationService;
import de.ostms.lc.user.service.TenantAdministrationLock;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Moves the home tenant (global accounts, platform sign-in) to another tenant. All accounts of the current home tenant move along and
 * keep their role by name; the change is one transaction and is undone if platform administrators would be lost on the way.
 */
@Service
public class HomeTenantService {
    public record Request(String tenantCode, boolean acknowledgeAccountMove) {}
    public record Result(UUID previousTenantId, UUID tenantId, String tenantCode, int accountsMoved) {}

    private final TenantRepository tenants;
    private final PlatformAdministrationService platform;
    private final TenantAdministrationLock lock;
    private final AuditService audit;
    private final JdbcTemplate jdbc;

    public HomeTenantService(TenantRepository tenants, PlatformAdministrationService platform, TenantAdministrationLock lock, AuditService audit, JdbcTemplate jdbc) {
        this.tenants = tenants; this.platform = platform; this.lock = lock; this.audit = audit; this.jdbc = jdbc;
    }

    @Transactional
    public Result change(Request request, Authentication auth) {
        UUID current = Tenant.homeId();
        try (var home = TenantContext.open(current)) {
            lock.acquire();
            require(auth);
            if (request == null || request.tenantCode() == null || !request.acknowledgeAccountMove())
                throw new IllegalArgumentException("Zielmandant angeben und bestätigen, dass alle Konten dorthin umziehen.");
            var target = tenants.findByCodeIgnoreCase(request.tenantCode().trim())
                    .orElseThrow(() -> new IllegalArgumentException("Zielmandant nicht gefunden."));
            if (target.getId().equals(current)) throw new IllegalArgumentException("Dieser Mandant ist bereits der Heimatmandant.");
            if (!target.isActive() || target.isArchived() || target.isDeleted())
                throw new IllegalArgumentException("Der Zielmandant muss aktiv und darf weder archiviert noch gelöscht sein.");
            long adminsBefore = platformAdministrators(current);
            var accounts = jdbc.queryForList("select u.id, r.name as role_name from app_user u join app_role r on r.id = u.assigned_role_id where u.tenant_id = ?", current);
            for (var account : accounts) {
                var roles = jdbc.queryForList("select id from app_role where tenant_id = ? and name = ?", target.getId(), account.get("role_name"));
                if (roles.isEmpty())
                    throw new IllegalArgumentException("Im Zielmandanten fehlt die Rolle „" + account.get("role_name") + "“. Bitte zuerst anlegen.");
                jdbc.update("update app_user set tenant_id = ?, assigned_role_id = ? where id = ?", target.getId(), roles.get(0).get("id"), account.get("id"));
            }
            jdbc.update("update platform_home_tenant set tenant_id = ? where singleton", target.getId());
            if (platformAdministrators(target.getId()) != adminsBefore || (adminsBefore == 0 && !accounts.isEmpty()))
                throw new IllegalStateException("Plattform-Administratoren gingen beim Umzug verloren. Nichts wurde geändert.");
            audit.recordInTransaction(auth, "PLATFORM_HOME_TENANT_CHANGED", "TENANT", target.getId(),
                    accounts.size() + " Konten von " + current + " nach " + target.getCode() + " verschoben; Plattform-Administratoren: " + adminsBefore);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { HomeTenantAccess.set(target.getId()); }
            });
            return new Result(current, target.getId(), target.getCode(), accounts.size());
        }
    }

    private long platformAdministrators(UUID tenant) {
        Long count = jdbc.queryForObject("select count(*) from app_user where tenant_id = ? and platform_administrator = true and active = true and invitation_pending = false and totp_enabled = true", Long.class, tenant);
        return count == null ? 0 : count;
    }

    private void require(Authentication auth) {
        if (!platform.enabled(auth)) throw new AccessDeniedException("Platform administration with two-factor authentication is required.");
        platform.verifyLiveAccess(auth);
    }
}
