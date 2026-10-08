package de.ostms.lc.document.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.ostms.lc.document.api.DocumentInboxAttachRequest.Metadata;
import de.ostms.lc.tenant.domain.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;

/** Conservative exact-text replay. Never reuse monetary or date values on different documents. */
@Service
public class DocumentMetadataTraining {
 private final JdbcTemplate jdbc;private final ObjectMapper json;
 public DocumentMetadataTraining(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
 public record Confirmation(@jakarta.validation.Valid @jakarta.validation.constraints.NotNull Metadata metadata,LocalDate documentDate){}
 public record Proposal(String status,Confirmation values){}
 static String hash(String text){
  if(text==null||text.isBlank())throw new IllegalArgumentException("Kein erkannter Text für das Training vorhanden.");
  try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text.strip().replace("\r\n","\n").getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
 @Transactional
 public void confirm(String text,Confirmation value,String actor){
  try{jdbc.update("insert into document_metadata_training(id,tenant_id,text_hash,confirmed_json,confirmed_by) values (?,?,?,?,?)",UUID.randomUUID(),TenantContext.currentId(),hash(text),json.writeValueAsString(value),actor);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}
 }
 @Transactional(readOnly=true)
 public Proposal suggest(String text){
  if(text==null||text.isBlank())return new Proposal("NO_TEXT",null);
  var rows=jdbc.queryForList("select distinct confirmed_json from document_metadata_training where tenant_id=? and text_hash=? limit 2",String.class,TenantContext.currentId(),hash(text));
  if(rows.size()!=1)return new Proposal(rows.isEmpty()?"NO_PATTERN":"CONFLICT",null);
  try{return new Proposal("LEARNED_REVIEW",json.readValue(rows.get(0),Confirmation.class));}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}
 }
}
