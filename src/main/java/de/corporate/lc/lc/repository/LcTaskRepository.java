package de.corporate.lc.lc.repository;
import de.corporate.lc.lc.domain.LcTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface LcTaskRepository extends JpaRepository<LcTask,UUID>{
 @Override  @org.springframework.data.jpa.repository.Query("select e from LcTask e where e.id=:id and e.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()}") Optional<LcTask> findById(@org.springframework.data.repository.query.Param("id") UUID id);
 @Override  @org.springframework.data.jpa.repository.Query("select e from LcTask e where e.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()}") List<LcTask> findAll();
 @org.springframework.data.jpa.repository.Query("select e from LcTask e where e.letterOfCreditId=:lcId and e.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()} order by e.completed asc,e.dueDate asc,e.createdAt desc") List<LcTask> findByLetterOfCreditIdOrderByCompletedAscDueDateAscCreatedAtDesc(@org.springframework.data.repository.query.Param("lcId") UUID lcId);
 @org.springframework.data.jpa.repository.Query("select e from LcTask e where e.completed=false and e.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()} order by e.dueDate asc,e.createdAt asc") List<LcTask> findByCompletedFalseOrderByDueDateAscCreatedAtAsc();
}
