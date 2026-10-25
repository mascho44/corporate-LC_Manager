package de.ostms.lc.lc.repository;
import de.ostms.lc.lc.domain.Team;
import de.ostms.lc.tenant.repository.TenantScopedRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamRepository extends TenantScopedRepository<Team,UUID> {
 @Override @Query("select e from Team e where e.id=:id and "+OWNED)
 Optional<Team> findById(@Param("id") UUID id);
 @Query("select e from Team e where "+OWNED+" order by lower(e.name)")
 List<Team> all();
 @Query("select count(e)>0 from Team e where lower(e.name)=lower(:name) and (:except is null or e.id<>:except) and "+OWNED)
 boolean nameTaken(@Param("name") String name,@Param("except") UUID except);
}
