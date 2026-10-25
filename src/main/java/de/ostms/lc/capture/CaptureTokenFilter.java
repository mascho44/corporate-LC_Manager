package de.ostms.lc.capture;

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
import java.util.List;

/**
 * Bearer tokens of scan stations. A valid token is accepted only for document delivery: POST /api/inbox and POST /api/capture/upload; it carries only the
 * upload authority and the tenant of the token. Anywhere else, and with an invalid token, the request is refused. No session is created.
 */
public class CaptureTokenFilter extends OncePerRequestFilter {
 public static final String AUTHORITY="PERM_DOCUMENT_UPLOAD";
 private final java.util.function.Supplier<CaptureTokenService> tokens;
 /** The service is looked up per request: slices of the application without it (and tests) simply refuse tokens. */
 public CaptureTokenFilter(java.util.function.Supplier<CaptureTokenService> tokens){this.tokens=tokens;}

 static String bearer(HttpServletRequest request){
  String header=request.getHeader("Authorization");
  return header!=null&&header.regionMatches(true,0,"Bearer ",0,7)&&header.substring(7).strip().startsWith(CaptureTokenService.PREFIX)?header.substring(7).strip():null;
 }
 /** Requests that carry a capture token do not need the CSRF token: they use no cookie. */
 public static boolean isCaptureRequest(HttpServletRequest request){return bearer(request)!=null;}

 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
  String secret=bearer(request);
  if(secret==null){chain.doFilter(request,response);return;}
  String path=request.getRequestURI().substring(request.getContextPath().length());
  if(!"POST".equals(request.getMethod())||!(path.equals("/api/inbox")||path.equals("/api/capture/upload"))){refuse(request,response,403,"Dieser Zugang darf nur Dokumente in den Posteingang laden.","Erfassungszugang außerhalb des Posteingangs");return;}
  var service=tokens.get();
  var identity=service==null?null:service.authenticate(secret).orElse(null);
  if(identity==null){refuse(request,response,401,"Der Erfassungszugang ist ungültig oder widerrufen.","Erfassungszugang ungültig oder widerrufen");return;}
  var authentication=new UsernamePasswordAuthenticationToken("capture:"+identity.name(),null,List.of(new SimpleGrantedAuthority(AUTHORITY)));
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
