package de.ostms.lc.check.repository;

import de.ostms.lc.check.domain.LcDraft;
import de.ostms.lc.tenant.repository.TenantScopedRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LcDraftRepository extends TenantScopedRepository<LcDraft,UUID> {
 @Override @Query("select e from LcDraft e where e.id=:id and "+OWNED)
 Optional<LcDraft> findById(@Param("id") UUID id);
 @Override @Query("select e from LcDraft e where "+OWNED)
 List<LcDraft> findAll();
 @Query("select e from LcDraft e where e.lcId=:lcId and "+OWNED)
 Optional<LcDraft> forLc(@Param("lcId") UUID lcId);
}
