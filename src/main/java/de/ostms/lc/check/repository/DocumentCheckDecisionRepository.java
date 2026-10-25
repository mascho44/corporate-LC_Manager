package de.ostms.lc.check.repository;
import de.ostms.lc.check.domain.DocumentCheckDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface DocumentCheckDecisionRepository extends de.ostms.lc.tenant.repository.TenantScopedRepository<DocumentCheckDecision,UUID>{
 @Override  @org.springframework.data.jpa.repository.Query("select e from DocumentCheckDecision e where e.id=:id and e.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()}") Optional<DocumentCheckDecision> findById(@org.springframework.data.repository.query.Param("id") UUID id);
 @Override  @org.springframework.data.jpa.repository.Query("select e from DocumentCheckDecision e where e.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()}") List<DocumentCheckDecision> findAll();
 @org.springframework.data.jpa.repository.Query("select e from DocumentCheckDecision e where e.lcId=:lcId and e.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()}") List<DocumentCheckDecision> findByLcId(@org.springframework.data.repository.query.Param("lcId") UUID lcId);
 @org.springframework.data.jpa.repository.Query("select e from DocumentCheckDecision e where e.lcId=:lcId and e.findingCode=:code and e.documentName=:documentName and e.findingFingerprint=:fingerprint and e.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()}") Optional<DocumentCheckDecision> findByLcIdAndFindingCodeAndDocumentNameAndFindingFingerprint(@org.springframework.data.repository.query.Param("lcId") UUID lcId,@org.springframework.data.repository.query.Param("code") String code,@org.springframework.data.repository.query.Param("documentName") String documentName,@org.springframework.data.repository.query.Param("fingerprint") String fingerprint);
 @org.springframework.transaction.annotation.Transactional default void deleteAllByLcId(UUID lcId){findByLcId(lcId).forEach(this::delete);}
}
