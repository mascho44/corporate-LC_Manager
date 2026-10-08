package de.ostms.lc.document.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.ostms.lc.tenant.domain.TenantContext;
import org.springframework.stereotype.Component;
import java.util.*;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** One-hour, tenant/user-bound receipt. Restart invalidates it; contains no PDF or OCR text. */
@Component
public class SplitTrainingReceipt {
 public record Payload(UUID tenant,String actor,String hash,int pages,long expires,List<PdfDocumentSplitter.Part> proposedParts,String method){}
 private final byte[] key=new byte[32];private final ObjectMapper json;
 public SplitTrainingReceipt(ObjectMapper json){this.json=json;new java.security.SecureRandom().nextBytes(key);}
 public String issue(String hash,int pages,String actor)throws Exception{
  return issue(hash,pages,actor,null,null);
 }
 public String issue(String hash,int pages,String actor,List<PdfDocumentSplitter.Part> parts,String method)throws Exception{
  var stripped=parts==null?null:parts.stream().map(p->new PdfDocumentSplitter.Part(p.fromPage(),p.toPage(),p.documentType())).toList();
  var value=new Payload(TenantContext.currentId(),actor,hash,pages,Instant.now().plusSeconds(3600).getEpochSecond(),stripped,method);
  String payload=Base64.getUrlEncoder().withoutPadding().encodeToString(json.writeValueAsBytes(value));
  return payload+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(sign(payload));
 }
 public Payload verify(String token,String actor)throws Exception{
  try{
   if(token==null||token.length()>40000)throw new IllegalArgumentException();
   String[] bits=token.split("\\.");if(bits.length!=2||!java.security.MessageDigest.isEqual(sign(bits[0]),Base64.getUrlDecoder().decode(bits[1])))throw new IllegalArgumentException();
   var value=json.readValue(Base64.getUrlDecoder().decode(bits[0]),Payload.class);
   if(!TenantContext.currentId().equals(value.tenant())||!actor.equals(value.actor())||value.expires()<=Instant.now().getEpochSecond())throw new IllegalArgumentException();
   return value;
  }catch(Exception error){throw new IllegalArgumentException("Trainingsvorschau ist ungültig oder abgelaufen. Bitte die PDF erneut laden.");}
 }
 private byte[] sign(String value)throws Exception{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));return mac.doFinal(value.getBytes(java.nio.charset.StandardCharsets.US_ASCII));}
}
