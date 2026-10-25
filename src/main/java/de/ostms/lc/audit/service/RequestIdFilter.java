package de.ostms.lc.audit.service;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

/** Server-generated correlation id per request (never taken from the client): audit rows, logs and the X-Request-Id header share it. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE+20)
public class RequestIdFilter extends OncePerRequestFilter {
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
  String id="req-"+UUID.randomUUID().toString().replace("-","").substring(0,20);
  request.setAttribute(AuditContext.REQUEST_ID_ATTRIBUTE,id);
  response.setHeader("X-Request-Id",id);
  MDC.put("requestId",id);
  try{chain.doFilter(request,response);}finally{MDC.remove("requestId");}
 }
}
