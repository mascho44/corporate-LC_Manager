package de.corporate.lc.check.repository;
import de.corporate.lc.check.domain.LcRequirementMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface LcRequirementMappingRepository extends de.corporate.lc.tenant.repository.TenantScopedRepository<LcRequirementMapping,UUID>{
 @Override  @org.springframework.data.jpa.repository.Query("select e from LcRequirementMapping e where e.id=:id and e.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()}") Optional<LcRequirementMapping> findById(@org.springframework.data.repository.query.Param("id") UUID id);
 @Override  @org.springframework.data.jpa.repository.Query("select e from LcRequirementMapping e where e.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()}") List<LcRequirementMapping> findAll();
 @org.springframework.data.jpa.repository.Query("select e from LcRequirementMapping e where e.lcId=:lcId and e.requirement=:requirement and e.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()}") Optional<LcRequirementMapping> findByLcIdAndRequirement(@org.springframework.data.repository.query.Param("lcId") UUID lcId,@org.springframework.data.repository.query.Param("requirement") String requirement);
 @org.springframework.data.jpa.repository.Query("select e from LcRequirementMapping e where e.lcId=:lcId and e.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()} order by e.mappedAt desc") List<LcRequirementMapping> findByLcIdOrderByMappedAtDesc(@org.springframework.data.repository.query.Param("lcId") UUID lcId);
}
