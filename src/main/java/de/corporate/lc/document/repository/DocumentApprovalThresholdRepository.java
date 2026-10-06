package de.corporate.lc.document.repository;

import de.corporate.lc.document.domain.DocumentApprovalThreshold;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface DocumentApprovalThresholdRepository extends JpaRepository<DocumentApprovalThreshold, UUID> {
    @org.springframework.data.jpa.repository.Query("select t from DocumentApprovalThreshold t where t.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()} order by t.currency asc,t.minimumAmount asc")
    List<DocumentApprovalThreshold> findAllByOrderByCurrencyAscMinimumAmountAsc();
    @Override @org.springframework.data.jpa.repository.Query("select t from DocumentApprovalThreshold t where t.id=:id and t.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()}")
    java.util.Optional<DocumentApprovalThreshold> findById(@org.springframework.data.repository.query.Param("id") UUID id);
    @Override @org.springframework.data.jpa.repository.Query("select t from DocumentApprovalThreshold t where t.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()}")
    List<DocumentApprovalThreshold> findAll();
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from DocumentApprovalThreshold t where t.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()}")
    void deleteForCurrentTenant();
}
