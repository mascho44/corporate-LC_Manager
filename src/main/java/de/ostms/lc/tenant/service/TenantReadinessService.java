package de.ostms.lc.tenant.service;
import de.ostms.lc.tenant.domain.TenantContext;
import de.ostms.lc.tenant.repository.TenantMembershipRepository;
import de.ostms.lc.company.repository.CompanyProfileRepository;
import de.ostms.lc.document.repository.DocumentTemplateRepository;
import de.ostms.lc.user.repository.AppUserRepository;
import de.ostms.lc.user.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;

/** Presence checks only, not a business/compliance readiness certification. */
@Service public class TenantReadinessService {
 public record Readiness(UUID tenantId,long companies,long templates,long activeMembers){}
 private final CompanyProfileRepository companies;private final DocumentTemplateRepository templates;private final TenantMembershipRepository members;private final AppUserRepository users;private final TenantMembershipService access;
 public TenantReadinessService(CompanyProfileRepository companies,DocumentTemplateRepository templates,TenantMembershipRepository members,AppUserRepository users,TenantMembershipService access){this.companies=companies;this.templates=templates;this.members=members;this.users=users;this.access=access;}
 @Transactional(readOnly=true) public Readiness get(Authentication auth){
  if(auth==null||!auth.isAuthenticated()||auth instanceof AnonymousAuthenticationToken)throw new AccessDeniedException("Authentication required.");
  var user=users.findByUsernameIgnoreCase(auth.getName()).orElseThrow(()->new AccessDeniedException("Identity unavailable."));
  if(!user.isActive()||!user.isTotpEnabled())throw new AccessDeniedException("Administrator access required.");
  var membership=access.requireActiveAccess(user.getId());
  if(membership.baseRole()!=UserRole.ADMIN||!membership.permissions().containsAll(Set.of(UserPermission.USER_MANAGE,UserPermission.SETTINGS_MANAGE)))throw new AccessDeniedException("User and settings administration permissions required.");
  return new Readiness(TenantContext.currentId(),companies.count(),templates.count(),members.countAccessibleMembers());
 }
}
