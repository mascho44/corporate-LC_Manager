package de.ostms.lc.user.service;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/** Read-only cross-tenant overview; every call re-verifies live platform access. */
@Service
public class PlatformOverviewService {
 static final int MAX_AUDIT=500;
 private final PlatformAdministrationService platform;
 private final PlatformOverviewStore store;
 @org.springframework.beans.factory.annotation.Autowired private de.ostms.lc.audit.service.AuditChainService chains;
 public PlatformOverviewService(PlatformAdministrationService platform,PlatformOverviewStore store){this.platform=platform;this.store=store;}
 @Transactional(readOnly=true) public List<PlatformOverviewStore.Membership> memberships(Authentication auth){platform.verifyLiveAccess(auth);return store.memberships();}
 @Transactional(readOnly=true) public List<de.ostms.lc.audit.service.AuditChainService.Chain> auditChains(Authentication auth){platform.verifyLiveAccess(auth);return chains.verifyAll();}
 @Transactional(readOnly=true) public List<PlatformOverviewStore.AuditRow> audit(String tenantCode,Integer limit,Authentication auth){
  platform.verifyLiveAccess(auth);
  int bounded=limit==null?200:Math.max(1,Math.min(limit,MAX_AUDIT));
  String code=tenantCode==null||tenantCode.isBlank()?null:tenantCode.trim();
  return store.audit(code,bounded);
 }
}
