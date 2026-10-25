package de.ostms.lc.config;
import de.ostms.lc.lc.service.InstrumentVisibility;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** Hides the kinds of files (letters of credit, guarantees) the signed-in user has no view right for. Runs inside the tenant scope of the session filter. */
public class InstrumentVisibilityFilter extends OncePerRequestFilter {
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
  var auth=SecurityContextHolder.getContext().getAuthentication();
  if(auth!=null&&auth.isAuthenticated()&&request.getRequestURI().startsWith("/api/")){var h=InstrumentVisibility.hiddenFor(auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());InstrumentVisibility.set(h[0],h[1]);}
  try{chain.doFilter(request,response);}finally{InstrumentVisibility.clear();}
 }
}
