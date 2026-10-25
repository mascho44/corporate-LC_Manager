package de.ostms.lc.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.ostms.lc.document.api.DocumentView;
import de.ostms.lc.document.domain.DocumentInboxItem;
import de.ostms.lc.document.domain.LcDocument;
import de.ostms.lc.lc.domain.LetterOfCredit;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Allowlisted, compact JSON snapshots for the before/after columns of the audit log. Only identifying and
 * decision-relevant fields plus content hashes: never file contents, extracted text or free-form notes.
 * Output always fits the 4000-character column as valid JSON.
 */
public final class AuditSnapshots {
 private static final ObjectMapper JSON=new ObjectMapper().findAndRegisterModules().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
 private static final int LIMIT=3900;
 private AuditSnapshots(){}

 public static String sha256(byte[] content){
  if(content==null)return null;
  try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));}
  catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
 }
 public static String document(LcDocument d){
  var m=new LinkedHashMap<String,Object>();
  m.put("id",d.getId());m.put("filename",d.getOriginalFilename());m.put("type",d.getDocumentType());m.put("copy",d.getCopyNumber());
  m.put("documentDate",d.getDocumentDate());m.put("amount",d.getAmount());m.put("currency",d.getCurrency());
  m.put("size",d.getFileSize());m.put("contentType",d.getContentType());m.put("sha256",sha256(d.getContent()));
  return encode(m);
 }
 public static String document(DocumentView d){
  var m=new LinkedHashMap<String,Object>();
  m.put("id",d.id());m.put("filename",d.originalFilename());m.put("type",d.documentType());m.put("copy",d.copyNumber());
  m.put("documentDate",d.documentDate());m.put("amount",d.amount());m.put("currency",d.currency());m.put("size",d.fileSize());m.put("contentType",d.contentType());
  return encode(m);
 }
 public static String inboxItem(DocumentInboxItem i){
  var m=new LinkedHashMap<String,Object>();
  m.put("id",i.getId());m.put("filename",i.getOriginalFilename());m.put("status",i.getStatus());m.put("size",i.getFileSize());m.put("contentType",i.getContentType());
  m.put("receivedBy",i.getReceivedBy());m.put("sha256",sha256(i.getContent()));
  return encode(m);
 }
 /** Master data of an LC plus a short list of its documents (filename, type, short hash); the list is dropped if it would not fit. */
 public static String letterOfCredit(LetterOfCredit lc,List<LcDocument> documents){
  var m=new LinkedHashMap<String,Object>();
  m.put("id",lc.getId());m.put("reference",lc.getReference());m.put("applicant",lc.getApplicant());m.put("beneficiary",lc.getBeneficiary());
  m.put("issuingBank",lc.getIssuingBank());m.put("advisingBank",lc.getAdvisingBank());m.put("amount",lc.getAmount());m.put("currency",lc.getCurrency());
  m.put("issueDate",lc.getIssueDate());m.put("expiryDate",lc.getExpiryDate());m.put("status",lc.getStatus());m.put("assignedTo",lc.getAssignedTo());
  m.put("requiredDocuments",lc.getRequiredDocuments().size());m.put("documentCount",documents==null?0:documents.size());
  if(documents!=null&&!documents.isEmpty()){
   var list=new ArrayList<Map<String,Object>>();
   for(var d:documents.stream().limit(20).toList()){var e=new LinkedHashMap<String,Object>();e.put("filename",d.getOriginalFilename());e.put("type",d.getDocumentType());String h=sha256(d.getContent());e.put("sha256",h==null?null:h.substring(0,16));list.add(e);}
   m.put("documents",list);
   String full=encode(m);
   if(full.length()<=LIMIT)return full;
   m.remove("documents");
  }
  return encode(m);
 }
 public static String assignment(String username){var m=new LinkedHashMap<String,Object>();m.put("assignedTo",username);return encode(m);}

 static String encode(Map<String,Object> values){
  try{String text=JSON.writeValueAsString(values);
   if(text.length()<=LIMIT)return text;
   // Shorten long text values instead of cutting the JSON in the middle.
   var shortened=new LinkedHashMap<String,Object>();
   values.forEach((k,v)->shortened.put(k,v instanceof String s&&s.length()>120?s.substring(0,117)+"...":v));
   return JSON.writeValueAsString(shortened);
  }catch(JsonProcessingException e){throw new IllegalStateException(e);}
 }
}
