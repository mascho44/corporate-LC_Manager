package de.ostms.lc.lc.repository;
import de.ostms.lc.lc.domain.Workflow;
import de.ostms.lc.tenant.repository.TenantScopedRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowRepository extends TenantScopedRepository<Workflow,UUID> {
 @Override @Query("select e from Workflow e where e.id=:id and "+OWNED)
 Optional<Workflow> findById(@Param("id") UUID id);
 @Query("select e from Workflow e where e.lcId=:lcId and "+OWNED+" order by e.startedAt desc")
 List<Workflow> forLc(@Param("lcId") UUID lcId);
 @Query("select count(e)>0 from Workflow e where e.lcId=:lcId and e.template=:template and e.status='RUNNING' and "+OWNED)
 boolean running(@Param("lcId") UUID lcId,@Param("template") String template);
}
