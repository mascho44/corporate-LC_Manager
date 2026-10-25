package de.ostms.lc.config;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** Wraps the rest of the security chain and notes every 401/403 it produces; changes nothing about the request or the response. */
public class AccessDenialFilter extends OncePerRequestFilter {
 /** Set by filters that know why they refuse (e.g. the session filter). */
 public static final String REASON="LC_DENIAL_REASON";
 private final AccessDenialLog log;
 public AccessDenialFilter(AccessDenialLog log){this.log=log;}
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
  Authentication before=SecurityContextHolder.getContext().getAuthentication();
  String user=before!=null&&before.isAuthenticated()&&!"anonymousUser".equals(String.valueOf(before.getPrincipal()))?before.getName():"-";
  var session=request.getSession(false);Object tenant=session==null?null:session.getAttribute(CredentialSessionFilter.TENANT);
  try{chain.doFilter(request,response);}
  finally{
   int status=response.getStatus();
   if((status==401||status==403)&&!request.getRequestURI().equals("/api/auth/login")){
    String reason=request.getAttribute(REASON) instanceof String r?r:hint(request,status,user);
    log.record(status,request.getMethod(),request.getRequestURI(),user,tenant==null?null:String.valueOf(tenant),request.getRemoteAddr(),reason);
   }
  }
 }
 /** A best guess when nobody said why: unauthenticated, missing CSRF token on a changing request, or a missing authority. */
 private static String hint(HttpServletRequest request,int status,String user){
  if("-".equals(user))return "nicht angemeldet oder Sitzung beendet";
  boolean changing=!"GET".equals(request.getMethod())&&!"HEAD".equals(request.getMethod())&&!"OPTIONS".equals(request.getMethod());
  if(status==403&&changing&&request.getHeader("X-CSRF-TOKEN")==null)return "CSRF-Token fehlt";
  return status==403?"Berechtigung fehlt oder CSRF-Token ungültig":"Sitzung ungültig";
 }
}
