package de.ostms.lc.apikey;

import de.ostms.lc.config.AccessDenialFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/**
 * Bearer API keys (lca_…). A valid key is accepted only under /api/v1/ and carries only the authorities of its scopes (PERM_API_READ, PERM_API_LC_CREATE,
 * PERM_API_DOCUMENT_UPLOAD) and the tenant of the key; no user permission, no session. Anywhere else, and with an invalid key, the request is refused.
 */
public class ApiKeyFilter extends OncePerRequestFilter {
 /** Request attribute set after a valid key; only the server can set it. */
 public static final String AUTHENTICATED="LC_API_KEY_AUTHENTICATED";
 private final java.util.function.Supplier<ApiKeyService> keys;
 public ApiKeyFilter(java.util.function.Supplier<ApiKeyService> keys){this.keys=keys;}

 static String bearer(HttpServletRequest request){
  String header=request.getHeader("Authorization");
  return header!=null&&header.regionMatches(true,0,"Bearer ",0,7)&&header.substring(7).strip().startsWith(ApiKeyService.PREFIX)?header.substring(7).strip():null;
 }
 /** The scope a call needs; null for a call that does not exist in the API. */
 static String requiredScope(String method,String path){
  if("GET".equals(method))return "READ";
  if("POST".equals(method)&&(path.equals("/api/v1/lcs")||path.equals("/api/v1/lcs/")))return "LC_CREATE";
  if("POST".equals(method)&&path.matches("/api/v1/lcs/[^/]+/documents/?"))return "DOCUMENT_UPLOAD";
  return null;
 }
 /** Requests with an API key use no cookie and need no CSRF token. */
 public static boolean isApiKeyRequest(HttpServletRequest request){return bearer(request)!=null;}

 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
  String secret=bearer(request);
  if(secret==null){chain.doFilter(request,response);return;}
  String path=request.getRequestURI().substring(request.getContextPath().length());
  if(!path.startsWith("/api/v1/")){refuse(request,response,403,"Dieser API-Schlüssel gilt nur für /api/v1/.","API-Schlüssel außerhalb von /api/v1");return;}
  var service=keys.get();
  var identity=service==null?null:service.authenticate(secret).orElse(null);
  if(identity==null){refuse(request,response,401,"Der API-Schlüssel ist ungültig oder widerrufen.","API-Schlüssel ungültig oder widerrufen");return;}
  String needed=requiredScope(request.getMethod(),path);
  if(needed==null||!identity.scopes().contains(needed)){refuse(request,response,403,needed==null?"Dieser Aufruf steht der API nicht zur Verfügung.":"Dem API-Schlüssel fehlt die Berechtigung "+needed+".","API-Schlüssel ohne Berechtigung "+(needed==null?"(unbekannter Aufruf)":needed));return;}
  var authorities=identity.scopes().stream().map(s->new SimpleGrantedAuthority("PERM_API_"+s)).toList();
  var authentication=new UsernamePasswordAuthenticationToken("api:"+identity.name(),null,authorities);
  request.setAttribute(AUTHENTICATED,Boolean.TRUE);
  var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(authentication);SecurityContextHolder.setContext(context);
  try(var scope=de.ostms.lc.tenant.domain.TenantContext.open(identity.tenantId())){chain.doFilter(request,response);}
  finally{SecurityContextHolder.clearContext();}
 }
 private void refuse(HttpServletRequest request,HttpServletResponse response,int status,String message,String reason)throws IOException{
  request.setAttribute(AccessDenialFilter.REASON,reason);
  response.setStatus(status);response.setContentType("application/json");response.setCharacterEncoding("UTF-8");
  response.getWriter().write("{\"error\":\""+message+"\"}");
 }
}
