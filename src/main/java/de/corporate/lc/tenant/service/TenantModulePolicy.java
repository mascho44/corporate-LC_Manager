package de.corporate.lc.tenant.service;
import de.corporate.lc.tenant.domain.Tenant;
/** Shared modules remain available; profile grants never replace role permissions. */
public final class TenantModulePolicy {
 private TenantModulePolicy(){}
 public static boolean allowed(Tenant tenant,String path,String method,String status){
  if(!tenant.isActive())return false;
  if(path.equals("/api/training/advising")||path.startsWith("/api/training/advising/")||path.matches("/api/inbox/[^/]+/advising-preview")||path.startsWith("/api/settings/approval-thresholds"))return tenant.isBankEnabled();
  if(path.startsWith("/api/document-templates")||path.startsWith("/api/company-profile")||path.matches("/api/lcs/[^/]+/document-template-selection"))return tenant.isCorporateEnabled();
  if(path.matches("/api/lcs/[^/]+/generated-documents(?:/.*)?"))return tenant.isCorporateEnabled();
  if(path.matches("/api/lcs/[^/]+/document-drafts/[^/]+/status")&&"PUT".equals(method)){
   return switch(status==null?"":status){case "REVIEWED","FINAL"->tenant.isBankEnabled();case "DRAFT","SUBMITTED"->tenant.isCorporateEnabled();default->false;};
  }
  if(path.matches("/api/lcs/[^/]+/document-drafts(?:/.*)?")&&!"GET".equals(method))return tenant.isCorporateEnabled();
  return true;
 }
}
