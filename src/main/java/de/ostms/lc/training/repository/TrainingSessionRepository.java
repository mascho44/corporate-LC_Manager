package de.ostms.lc.training.repository;import de.ostms.lc.training.domain.TrainingSession;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;public interface TrainingSessionRepository extends de.ostms.lc.tenant.repository.TenantScopedRepository<TrainingSession,UUID>{
 default List<TrainingSession> findTop100ByOrderByCreatedAtDesc(){return findRecent(org.springframework.data.domain.PageRequest.of(0,100));}
 @org.springframework.data.jpa.repository.Query("select s from TrainingSession s where s.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()} order by s.createdAt desc")
 List<TrainingSession> findRecent(org.springframework.data.domain.Pageable pageable);
 @Override @org.springframework.data.jpa.repository.Query("select s from TrainingSession s where s.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()}")
 List<TrainingSession> findAll();
 @Override @org.springframework.data.jpa.repository.Query("select s from TrainingSession s where s.id=:id and s.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()}")
 Optional<TrainingSession> findById(@org.springframework.data.repository.query.Param("id") UUID id);
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select s from TrainingSession s where s.id = :id and s.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()}")
 Optional<TrainingSession> findForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
}
