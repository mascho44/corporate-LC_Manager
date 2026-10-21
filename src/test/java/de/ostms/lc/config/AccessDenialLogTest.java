package de.ostms.lc.config;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class AccessDenialLogTest {
 @Test void aRefusalIsRecordedWithoutQueryBodyOrControlCharacters(){
  var log=new AccessDenialLog();
  log.record(403,"POST","/api/lcs/12345678-1234-1234-1234-123456789abc/x\nforged line","markus\r\nX","t-1","10.0.0.1","Berechtigung fehlt");
  var e=log.recent().get(0);
  assertThat(e.path()).doesNotContain("\n").doesNotContain("\r");assertThat(e.user()).doesNotContain("\n");assertThat(e.status()).isEqualTo(403);assertThat(e.reason()).isEqualTo("Berechtigung fehlt");
 }
 @Test void theSameRefusalIsLoggedOncePerMinuteAndIdsDoNotMakeItNew(){
  var log=new AccessDenialLog();
  for(int i=0;i<50;i++)log.record(403,"GET","/api/lcs/"+java.util.UUID.randomUUID(),"markus","t","1.1.1.1","Berechtigung fehlt");
  assertThat(log.recent()).hasSize(1);
  log.record(403,"GET","/api/other","markus","t","1.1.1.1","Berechtigung fehlt");log.record(403,"GET","/api/lcs/"+java.util.UUID.randomUUID(),"anna","t","1.1.1.1","Berechtigung fehlt");
  assertThat(log.recent()).hasSize(3);
 }
 @Test void anAnonymousVisitOfTheStartPageIsNotARefusal(){
  var log=new AccessDenialLog();
  log.record(401,"GET","/","-","-","1.1.1.1","nicht angemeldet");log.record(401,"GET","/index.html",null,null,"1.1.1.1","nicht angemeldet");
  assertThat(log.recent()).isEmpty();
  log.record(401,"GET","/api/lcs","-","-","1.1.1.1","nicht angemeldet");log.record(401,"GET","/","markus","t","1.1.1.1","Sitzung beendet");
  assertThat(log.recent()).hasSize(2);
 }
 @Test void theMemoryIsBounded(){
  var log=new AccessDenialLog();
  for(int i=0;i<500;i++)log.record(401,"GET","/p"+i+"/x","u"+i,null,"a","r");
  assertThat(log.recent().size()).isLessThanOrEqualTo(200);assertThat(log.recent().get(0).path()).isEqualTo("/p499/x");
 }
 @Test void theFilterNotesRefusalsButNeverChangesTheResponse()throws Exception{
  var log=new AccessDenialLog();var filter=new AccessDenialFilter(log);
  SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("markus",null,List.of()));
  try{
   var request=new MockHttpServletRequest("PUT","/api/ai/config");var response=new MockHttpServletResponse();
   filter.doFilter(request,response,(req,res)->((MockHttpServletResponse)res).setStatus(403));
   assertThat(response.getStatus()).isEqualTo(403);
   var e=log.recent().get(0);assertThat(e.user()).isEqualTo("markus");assertThat(e.reason()).isEqualTo("CSRF-Token fehlt");
   var ok=new MockHttpServletResponse();filter.doFilter(new MockHttpServletRequest("GET","/api/lcs"),ok,mock200());
   assertThat(log.recent()).hasSize(1); // a normal answer is not logged
   var withReason=new MockHttpServletRequest("GET","/api/lcs");withReason.setAttribute(AccessDenialFilter.REASON,"keine aktive Mitgliedschaft");
   filter.doFilter(withReason,new MockHttpServletResponse(),(req,res)->((MockHttpServletResponse)res).setStatus(401));
   assertThat(log.recent().get(0).reason()).isEqualTo("keine aktive Mitgliedschaft");
   // the login endpoint reports its own failures; wrong passwords are not repeated here
   filter.doFilter(new MockHttpServletRequest("POST","/api/auth/login"),new MockHttpServletResponse(),(req,res)->((MockHttpServletResponse)res).setStatus(401));
   assertThat(log.recent()).hasSize(2);
  }finally{SecurityContextHolder.clearContext();}
 }
 private static FilterChain mock200(){return (req,res)->((MockHttpServletResponse)res).setStatus(200);}
}
