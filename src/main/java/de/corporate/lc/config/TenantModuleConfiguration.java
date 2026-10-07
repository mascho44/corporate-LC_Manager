package de.corporate.lc.config;
import de.corporate.lc.tenant.domain.TenantContext;
import de.corporate.lc.tenant.repository.TenantRepository;
import de.corporate.lc.tenant.service.TenantModulePolicy;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.*;
public class TenantModuleConfiguration implements WebMvcConfigurer {
 private final TenantRepository tenants;
 public TenantModuleConfiguration(TenantRepository tenants){this.tenants=tenants;}
 @Override public void addInterceptors(InterceptorRegistry registry){registry.addInterceptor(new HandlerInterceptor(){
  @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler){
   String path=request.getRequestURI().substring(request.getContextPath().length());
   if(!path.startsWith("/api/")||path.startsWith("/api/platform/")||path.startsWith("/api/auth/")||path.equals("/api/health"))return true;
   var tenant=tenants.findById(TenantContext.currentId()).orElseThrow(()->new org.springframework.security.access.AccessDeniedException("Tenant unavailable."));
   if(!TenantModulePolicy.allowed(tenant,path,request.getMethod(),request.getParameter("status")))throw new org.springframework.security.access.AccessDeniedException("This module is not enabled for this tenant profile.");
   return true;
  }
 });}
}
