package de.ostms.lc.config;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import de.ostms.lc.lc.service.InstrumentVisibility;
import jakarta.servlet.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;
import java.util.UUID;
import java.util.regex.Pattern;

/** Writing to a file needs the right of its kind: guarantees GUARANTEE_EDIT, letters of credit LC_EDIT. Someone who holds only the right of the other kind is refused; everyone else is left to the path rules in SecurityConfig. */
public class InstrumentPermissionConfiguration implements WebMvcConfigurer {
 private static final Pattern FILE=Pattern.compile("^/api/lcs/([0-9a-fA-F-]{36})(/.*)?$");
 private final LetterOfCreditRepository lcs;
 public InstrumentPermissionConfiguration(LetterOfCreditRepository lcs){this.lcs=lcs;}
 /** True when the user holds the right of the other kind but not the one this file needs. */
 static boolean refused(boolean guarantee,boolean hasLc,boolean hasGuarantee){return guarantee?(hasLc&&!hasGuarantee):(hasGuarantee&&!hasLc);}
 @Override public void addInterceptors(InterceptorRegistry registry){registry.addInterceptor(new HandlerInterceptor(){
  @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler){
   String method=request.getMethod();
   var matcher=FILE.matcher(request.getRequestURI().substring(request.getContextPath().length()));
   if(!matcher.matches())return true;
   // a file of a kind the user may not read does not exist for them
   if(InstrumentVisibility.restricted()&&lcs.findById(UUID.fromString(matcher.group(1))).isEmpty())throw new AccessDeniedException("Missing right to read this kind of file.");
   if("GET".equals(method)||"HEAD".equals(method)||"OPTIONS".equals(method))return true;
   var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null)return true;
   boolean hasLc=auth.getAuthorities().stream().anyMatch(a->"PERM_LC_EDIT".equals(a.getAuthority())),hasGuarantee=auth.getAuthorities().stream().anyMatch(a->"PERM_GUARANTEE_EDIT".equals(a.getAuthority()));
   if(hasLc==hasGuarantee)return true;
   var lc=lcs.findById(UUID.fromString(matcher.group(1))).orElse(null);if(lc==null)return true;
   if(refused("GUARANTEE".equals(lc.getInstrumentType()),hasLc,hasGuarantee))throw new AccessDeniedException("Missing right for this kind of file.");
   return true;
  }
 });}
}
