package de.ostms.lc.lc.repository;
import de.ostms.lc.lc.domain.LcTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface LcTaskRepository extends de.ostms.lc.tenant.repository.TenantScopedRepository<LcTask,UUID>{
 @Override  @org.springframework.data.jpa.repository.Query("select e from LcTask e where e.id=:id and e.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()}") Optional<LcTask> findById(@org.springframework.data.repository.query.Param("id") UUID id);
 @Override  @org.springframework.data.jpa.repository.Query("select e from LcTask e where e.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()}") List<LcTask> findAll();
 @org.springframework.data.jpa.repository.Query("select e from LcTask e where e.letterOfCreditId=:lcId and e.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()} order by e.completed asc,e.dueDate asc,e.createdAt desc") List<LcTask> findByLetterOfCreditIdOrderByCompletedAscDueDateAscCreatedAtDesc(@org.springframework.data.repository.query.Param("lcId") UUID lcId);
 @org.springframework.data.jpa.repository.Query("select e from LcTask e where e.completed=false and e.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()} order by e.dueDate asc,e.createdAt asc") List<LcTask> findByCompletedFalseOrderByDueDateAscCreatedAtAsc();
 @org.springframework.data.jpa.repository.Query("select e from LcTask e where e.completed=false and e.teamId in :teamIds and e.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()} order by e.dueDate asc,e.createdAt asc") List<LcTask> openForTeams(@org.springframework.data.repository.query.Param("teamIds") Collection<UUID> teamIds);
 /** Atomic claim: only succeeds while nobody has taken the task yet. */
 @org.springframework.data.jpa.repository.Modifying(clearAutomatically=true,flushAutomatically=true) @org.springframework.data.jpa.repository.Query("update LcTask e set e.assignedTo=:user,e.claimedAt=:at where e.id=:id and e.assignedTo is null and e.completed=false and e.teamId is not null and e.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()}") int claim(@org.springframework.data.repository.query.Param("id") UUID id,@org.springframework.data.repository.query.Param("user") String user,@org.springframework.data.repository.query.Param("at") java.time.LocalDateTime at);
}
