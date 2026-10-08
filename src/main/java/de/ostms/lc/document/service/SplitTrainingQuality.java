package de.ostms.lc.document.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.ostms.lc.tenant.domain.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Observed corrections, not an estimate of accuracy on unseen documents. */
@Service
public class SplitTrainingQuality {
 private final JdbcTemplate jdbc; private final ObjectMapper json;
 public SplitTrainingQuality(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
 public record Comparison(int pages,int correctedPages,boolean boundariesChanged){}
 public record Sample(String pattern,String method,String confirmedAt,int pages,int correctedPages,boolean boundariesChanged,List<PdfDocumentSplitter.Part> proposedParts,List<PdfDocumentSplitter.Part> confirmedParts){}
 public record Report(long confirmations,int measured,int pages,int correctedPages,int changedBoundaries,List<Sample> recent){}
 static Comparison compare(List<PdfDocumentSplitter.Part> before,List<PdfDocumentSplitter.Part> after){
  int pages=after.stream().mapToInt(PdfDocumentSplitter.Part::toPage).max().orElseThrow();
  if(before.isEmpty()||before.get(before.size()-1).toPage()!=pages)throw new IllegalArgumentException("Inconsistent proposal");
  int changed=0;
  for(int page=1;page<=pages;page++){
   final int number=page;
   var old=before.stream().filter(p->p.fromPage()<=number&&p.toPage()>=number).findFirst().orElseThrow();
   var now=after.stream().filter(p->p.fromPage()<=number&&p.toPage()>=number).findFirst().orElseThrow();
   if(old.documentType()!=now.documentType())changed++;
  }
  return new Comparison(pages,changed,!before.stream().map(PdfDocumentSplitter.Part::toPage).toList().equals(after.stream().map(PdfDocumentSplitter.Part::toPage).toList()));
 }
 @Transactional(readOnly=true)
 public Report report()throws Exception{
  var tenant=TenantContext.currentId();
  Long total=jdbc.queryForObject("select count(*) from document_split_training where tenant_id=?",Long.class,tenant);
  var rows=jdbc.queryForList("select pattern_hash,parts_json,proposed_parts_json,proposed_method,confirmed_at from document_split_training where tenant_id=? and proposed_parts_json is not null order by confirmed_at desc,id desc limit 1000",tenant);
  int measured=0,pages=0,corrected=0,boundaries=0;var recent=new ArrayList<Sample>();
  for(var row:rows){
   var before=json.readValue((String)row.get("proposed_parts_json"),new TypeReference<List<PdfDocumentSplitter.Part>>(){});
   var after=json.readValue((String)row.get("parts_json"),new TypeReference<List<PdfDocumentSplitter.Part>>(){});
   var result=compare(before,after);measured++;pages+=result.pages();corrected+=result.correctedPages();if(result.boundariesChanged())boundaries++;
   if(recent.size()<20)recent.add(new Sample(((String)row.get("pattern_hash")).substring(0,12),(String)row.get("proposed_method"),String.valueOf(row.get("confirmed_at")),result.pages(),result.correctedPages(),result.boundariesChanged(),List.copyOf(before),List.copyOf(after)));
  }
  return new Report(total==null?0:total,measured,pages,corrected,boundaries,List.copyOf(recent));
 }
}
