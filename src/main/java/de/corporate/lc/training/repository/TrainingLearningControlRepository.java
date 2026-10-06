package de.corporate.lc.training.repository;

import de.corporate.lc.training.domain.TrainingLearningControl;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingLearningControlRepository extends de.corporate.lc.tenant.repository.TenantScopedRepository<TrainingLearningControl,TrainingLearningControl.Key> {
    @Override @org.springframework.data.jpa.repository.Query("select c from TrainingLearningControl c where c.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()}")
    java.util.List<TrainingLearningControl> findAll();
    @org.springframework.data.jpa.repository.Query("select c from TrainingLearningControl c where c.ruleId=:ruleId and c.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()}")
    java.util.Optional<TrainingLearningControl> findByRuleId(@org.springframework.data.repository.query.Param("ruleId") String ruleId);
    @Override default java.util.Optional<TrainingLearningControl> findById(TrainingLearningControl.Key key){
        if(!de.corporate.lc.tenant.domain.TenantContext.currentId().equals(key.tenantId))return java.util.Optional.empty();
        return findByRuleId(key.ruleId);
    }
}
