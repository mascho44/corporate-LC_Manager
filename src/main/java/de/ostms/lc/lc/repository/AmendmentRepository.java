package de.ostms.lc.lc.repository;
import de.ostms.lc.lc.domain.Amendment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface AmendmentRepository extends de.ostms.lc.tenant.repository.TenantScopedRepository<Amendment,UUID>{
 @Override  @org.springframework.data.jpa.repository.Query("select e from Amendment e where e.id=:id and e.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()}") Optional<Amendment> findById(@org.springframework.data.repository.query.Param("id") UUID id);
 @Override  @org.springframework.data.jpa.repository.Query("select e from Amendment e where e.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()}") List<Amendment> findAll();
 @org.springframework.data.jpa.repository.Query("select e from Amendment e where e.letterOfCredit.id=:lcId and e.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()} order by e.importedAt desc") List<Amendment> findByLetterOfCreditIdOrderByImportedAtDesc(@org.springframework.data.repository.query.Param("lcId") UUID lcId);
 @org.springframework.data.jpa.repository.Query("select count(e)>0 from Amendment e where e.letterOfCredit.id=:lcId and e.amendmentNumber=:amendmentNumber and e.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()}") boolean existsByLetterOfCreditIdAndAmendmentNumber(@org.springframework.data.repository.query.Param("lcId") UUID lcId,@org.springframework.data.repository.query.Param("amendmentNumber") String amendmentNumber);
}
