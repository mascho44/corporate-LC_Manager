package de.ostms.lc.audit.service;

import de.ostms.lc.audit.domain.AuditEvent;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Adds the technical context of the current web request to an audit event. Outside a request the fields stay empty. */
final class AuditContext {
 static final String REQUEST_ID_ATTRIBUTE="lc.requestId";
 private static final Pattern ADDRESS=Pattern.compile("[0-9a-fA-F:.]{3,45}");
 private AuditContext(){}

 static void apply(AuditEvent event,Authentication explicit){
  if(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes){
   HttpServletRequest request=attributes.getRequest();
   if(event.getIpAddress()==null)event.setIpAddress(clientAddress(request));
   event.setUserAgent(clean(request.getHeader("User-Agent"),300));
   if(request.getAttribute(REQUEST_ID_ATTRIBUTE) instanceof String id)event.setRequestId(clean(id,40));
   var session=request.getSession(false);
   if(session!=null)event.setSessionRef(hash(session.getId()));
  }
  Authentication actor=explicit!=null?explicit:SecurityContextHolder.getContext().getAuthentication();
  if(actor!=null&&actor.isAuthenticated()&&!(actor instanceof AnonymousAuthenticationToken))event.setActorRoles(roles(actor));
  if(!event.isSuccessful()&&event.getFailureReason()==null)event.setFailureReason(clean(event.getDetails(),500));
 }

 /** Behind the single trusted reverse proxy the right-most entry is the address the proxy saw; earlier entries are client-supplied. */
 static String clientAddress(HttpServletRequest request){
  String forwarded=request.getHeader("X-Forwarded-For");
  if(forwarded!=null&&!forwarded.isBlank()){
   String[] parts=forwarded.split(",");String last=parts[parts.length-1].trim();
   if(ADDRESS.matcher(last).matches())return last;
  }
  return clean(request.getRemoteAddr(),64);
 }
 private static String roles(Authentication actor){
  String roles=actor.getAuthorities().stream().map(a->a.getAuthority()).filter(a->a.startsWith("ROLE_")).map(a->a.substring(5)).sorted().collect(Collectors.joining(","));
  return roles.isEmpty()?null:clean(roles,120);
 }
 static String hash(String value){
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))).substring(0,16);}
  catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
 }
 static String clean(String value,int max){
  if(value==null)return null;
  String text=value.replaceAll("[\\p{Cntrl}]"," ").trim();
  if(text.isEmpty())return null;
  return text.length()<=max?text:text.substring(0,max);
 }
}
