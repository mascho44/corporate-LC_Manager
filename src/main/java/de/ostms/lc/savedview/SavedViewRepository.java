package de.ostms.lc.savedview;

import de.ostms.lc.tenant.repository.TenantScopedRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavedViewRepository extends TenantScopedRepository<SavedView,UUID> {
 @Override @Query("select e from SavedView e where e.id=:id and "+OWNED)
 Optional<SavedView> findById(@Param("id") UUID id);
 @Override @Query("select e from SavedView e where "+OWNED)
 List<SavedView> findAll();
 @Query("select e from SavedView e where (lower(e.ownerUsername)=lower(:user) or e.shared=true) and "+OWNED+" order by lower(e.name)")
 List<SavedView> visibleTo(@Param("user") String user);
 @Query("select count(e) from SavedView e where lower(e.ownerUsername)=lower(:user) and "+OWNED)
 long countOf(@Param("user") String user);
 @Query("select count(e)>0 from SavedView e where lower(e.ownerUsername)=lower(:user) and lower(e.name)=lower(:name) and (:except is null or e.id<>:except) and "+OWNED)
 boolean nameTaken(@Param("user") String user,@Param("name") String name,@Param("except") UUID except);
}
