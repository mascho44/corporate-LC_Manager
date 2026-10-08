package de.ostms.lc.lc.repository;
import de.ostms.lc.lc.domain.LcNote;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface LcNoteRepository extends de.ostms.lc.tenant.repository.TenantScopedRepository<LcNote,UUID>{
 @Override  @org.springframework.data.jpa.repository.Query("select e from LcNote e where e.id=:id and e.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()}") Optional<LcNote> findById(@org.springframework.data.repository.query.Param("id") UUID id);
 @Override  @org.springframework.data.jpa.repository.Query("select e from LcNote e where e.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()}") List<LcNote> findAll();
 default List<LcNote> findTop100ByLetterOfCreditIdOrderByCreatedAtDesc(UUID id){return findRecent(id,org.springframework.data.domain.PageRequest.of(0,100));}
 @org.springframework.data.jpa.repository.Query("select e from LcNote e where e.letterOfCreditId=:lcId and e.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()} order by e.createdAt desc") List<LcNote> findRecent(@org.springframework.data.repository.query.Param("lcId") UUID lcId,org.springframework.data.domain.Pageable pageable);
}
