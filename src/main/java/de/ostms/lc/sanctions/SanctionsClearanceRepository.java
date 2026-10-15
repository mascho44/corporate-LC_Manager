package de.ostms.lc.sanctions;

import java.util.*;

public interface SanctionsClearanceRepository extends de.ostms.lc.tenant.repository.TenantScopedRepository<SanctionsClearance,UUID>{
 @Override @org.springframework.data.jpa.repository.Query("select e from SanctionsClearance e where e.id=:id and e.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()}") Optional<SanctionsClearance> findById(@org.springframework.data.repository.query.Param("id") UUID id);
 @Override @org.springframework.data.jpa.repository.Query("select e from SanctionsClearance e where e.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()}") List<SanctionsClearance> findAll();
}
