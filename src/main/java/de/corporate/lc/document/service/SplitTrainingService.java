package de.corporate.lc.document.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.corporate.lc.tenant.domain.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

/** Tenant-local, human-confirmed template replay. Learned output always requires review. */
@Service
public class SplitTrainingService {
 private final JdbcTemplate jdbc;
 private final ObjectMapper json;
 public SplitTrainingService(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}

 @Transactional(propagation=Propagation.MANDATORY)
 public boolean confirm(byte[] content,String evidence,List<PdfDocumentSplitter.Part> parts,String actor)throws Exception{
  String hash=PdfDocumentSplitter.trainingPattern(content,evidence);
  if(hash==null)return false;
  confirmPattern(hash,parts,actor);
  return true;
 }
 @Transactional(propagation=Propagation.MANDATORY)
 public void confirmPattern(String hash,List<PdfDocumentSplitter.Part> parts,String actor)throws Exception{
  if(hash==null||!hash.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Ungültiges Trainingsmuster.");
  // Original/copy designation belongs to this presentation, never to a learned template.
  String encoded=json.writeValueAsString(parts.stream().map(p->new PdfDocumentSplitter.Part(p.fromPage(),p.toPage(),p.documentType())).toList());
  jdbc.update("insert into document_split_training(id,tenant_id,pattern_hash,parts_json,confirmed_by) values (?,?,?,?,?)",UUID.randomUUID(),TenantContext.currentId(),hash,encoded,actor);
 }

 @Transactional(readOnly=true)
 public PdfDocumentSplitter.Proposal suggest(byte[] content,String evidence,PdfDocumentSplitter.Proposal baseline)throws Exception{
  String hash=PdfDocumentSplitter.trainingPattern(content,evidence);
  if(hash==null)return baseline;
  var matches=jdbc.queryForList("select distinct parts_json from document_split_training where tenant_id=? and pattern_hash=? limit 2",String.class,TenantContext.currentId(),hash);
  // Conflicting confirmations must never be resolved by choosing the latest or majority.
  if(matches.size()!=1)return baseline;
  var parts=json.readValue(matches.get(0),new TypeReference<List<PdfDocumentSplitter.Part>>(){});
  PdfDocumentSplitter.validate(parts,baseline.pageCount());
  var pages=baseline.pages().stream().map(page->{
   var part=parts.stream().filter(p->page.number()>=p.fromPage()&&page.number()<=p.toPage()).findFirst().orElseThrow();
   return new PdfDocumentSplitter.Page(page.number(),new DocumentClassifier.Classification(part.documentType(),.8,"REVIEW","CONFIRMED_SPLIT_PATTERN_V1",List.of("Bestätigtes Dokumentmuster: Seiten "+part.fromPage()+"–"+part.toPage()+"; Zuordnung und Grenzen bitte prüfen")),page.textSource());
  }).toList();
  return new PdfDocumentSplitter.Proposal(baseline.pageCount(),pages,parts);
 }
}
