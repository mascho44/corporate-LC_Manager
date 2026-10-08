package de.ostms.lc.tenant.repository;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import java.util.UUID;

/** Internal PostgreSQL boundary; callers must provide authorization and audit. */
@Repository
public class TenantMembershipProvisioningStore {
 private final EntityManager entityManager;
 public TenantMembershipProvisioningStore(EntityManager entityManager){this.entityManager=entityManager;}
 @Transactional(propagation=Propagation.MANDATORY)
 public UUID create(UUID tenantId,UUID userId,UUID roleId){
  Object result=entityManager.createNativeQuery("select provision_tenant_membership(:tenant,:identity,:role)")
   .setParameter("tenant",tenantId).setParameter("identity",userId).setParameter("role",roleId).getSingleResult();
  return result instanceof UUID uuid?uuid:UUID.fromString(result.toString());
 }
 @Transactional(propagation=Propagation.MANDATORY)
 public void updateRole(UUID tenantId,UUID userId,UUID roleId){
  entityManager.createNativeQuery("select update_tenant_membership_role(:tenant,:identity,:role)")
   .setParameter("tenant",tenantId).setParameter("identity",userId).setParameter("role",roleId).getSingleResult();
 }
}
