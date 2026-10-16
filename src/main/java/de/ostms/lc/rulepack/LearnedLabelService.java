package de.ostms.lc.rulepack;

import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.tenant.domain.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;
import static de.ostms.lc.rulepack.PackDefinition.Field;

/**
 * Printed labels learned from manual entries: when a person enters a check value that stands on exactly one labelled line of the document
 * ("Description of goods: 2 UNITS …"), the label is remembered for the document type and later used to propose the value. Proposals only.
 */
@Service
public class LearnedLabelService {
 public record Learned(Field field,String label){}
 static final int MAX_LABEL=40,MAX_PER_TYPE=200;
 private static final Pattern LINE=Pattern.compile("^\\s*([\\p{L}][\\p{L}\\p{N} .,/()&'-]{1,"+MAX_LABEL+"}?)\\s*[:\\-–]\\s*(.+?)\\s*$");
 private static final Pattern NUMBER=Pattern.compile("\\d{1,3}(?:[.,\\s]\\d{3})*(?:[.,]\\d{1,3})?|\\d+");
 private final JdbcTemplate jdbc;
 public LearnedLabelService(JdbcTemplate jdbc){this.jdbc=jdbc;}

 /** Fields whose value can be learned: free text and numbers (dates, yes/no and currencies have their own recognition). */
 static boolean learnable(Field field){return field.document()&&("TEXT".equals(field.kind())||"NUMBER".equals(field.kind()));}

 /** The label of the single line that carries the value, or empty when none or several lines do. */
 static Optional<String> labelFor(String text,Field field,String value){
  if(text==null||value==null||value.isBlank()||!learnable(field))return Optional.empty();
  Set<String> labels=new LinkedHashSet<>();
  for(String line:text.split("\\R")){
   var m=LINE.matcher(line);if(!m.matches())continue;
   String label=m.group(1).trim(),rest=m.group(2);
   if(matches(field,rest,value)&&!label.equalsIgnoreCase(value.trim()))labels.add(label.replaceAll("\\s+"," "));
  }
  return labels.size()==1?Optional.of(labels.iterator().next()):Optional.empty();
 }
 /** The value standing after the label, for exactly one distinct occurrence; numbers are normalised. */
 static Optional<String> valueFor(String text,Field field,String label){
  if(text==null||!learnable(field))return Optional.empty();
  var m=Pattern.compile("(?im)^\\s*"+Pattern.quote(label)+"\\s*[:\\-–]\\s*(.+?)\\s*$").matcher(text);
  Set<String> values=new LinkedHashSet<>();
  while(m.find()){
   String rest=m.group(1).replaceAll("\\s+"," ").trim();
   if("NUMBER".equals(field.kind())){var n=NUMBER.matcher(rest);String v=n.find()?DocumentFactSuggester.normalizeNumber(n.group().replaceAll("\\s","")):null;if(v!=null)values.add(v);}
   else if(rest.length()>=2&&rest.length()<=500&&!"-".equals(rest))values.add(rest);
  }
  return values.size()==1?Optional.of(values.iterator().next()):Optional.empty();
 }
 private static boolean matches(Field field,String rest,String value){
  if("NUMBER".equals(field.kind())){
   BigDecimal wanted;try{wanted=new BigDecimal(value.trim());}catch(NumberFormatException e){return false;}
   var n=NUMBER.matcher(rest);
   while(n.find()){String v=DocumentFactSuggester.normalizeNumber(n.group().replaceAll("\\s",""));if(v!=null&&new BigDecimal(v).compareTo(wanted)==0)return true;}
   return false;
  }
  String a=rest.replaceAll("\\s+"," ").trim().toLowerCase(Locale.ROOT),b=value.replaceAll("\\s+"," ").trim().toLowerCase(Locale.ROOT);
  return b.length()>=3&&a.startsWith(b.length()>40?b.substring(0,40):b);
 }

 /** Learns labels for the fields a person changed by hand; returns the learned (field, label) pairs. */
 @Transactional
 public List<Learned> learn(DocumentType type,String text,Map<Field,String> before,Map<Field,String> after,String actor){
  if(type==null||type==DocumentType.OTHER)return List.of();
  UUID tenant=TenantContext.currentId();var out=new ArrayList<Learned>();
  for(var e:after.entrySet()){
   if(e.getValue()==null||e.getValue().isBlank()||Objects.equals(e.getValue(),before.get(e.getKey())))continue;
   labelFor(text,e.getKey(),e.getValue()).ifPresent(label->{
    int added=jdbc.update("insert into learned_field_label(id,tenant_id,document_type,field,label,learned_by) select ?,?,?,?,?,? where (select count(*) from learned_field_label where tenant_id=? and document_type=?)<? on conflict do nothing",
     UUID.randomUUID(),tenant,type.name(),e.getKey().name(),label,actor,tenant,type.name(),MAX_PER_TYPE);
    if(added>0)out.add(new Learned(e.getKey(),label));
   });
  }
  return out;
 }
 /** Labels learned for the document type in the current tenant. */
 public List<Learned> labels(DocumentType type){
  if(type==null)return List.of();
  return jdbc.query("select field,label from learned_field_label where tenant_id=? and document_type=? order by learned_at",(rs,n)->{
   try{return new Learned(Field.valueOf(rs.getString(1)),rs.getString(2));}catch(IllegalArgumentException unknown){return null;}
  },TenantContext.currentId(),type.name()).stream().filter(Objects::nonNull).toList();
 }
 public List<Map<String,Object>> list(){return jdbc.queryForList("select id,document_type,field,label,learned_by,learned_at from learned_field_label where tenant_id=? order by document_type,field,label",TenantContext.currentId());}
 @Transactional public boolean delete(UUID id){return jdbc.update("delete from learned_field_label where id=? and tenant_id=?",id,TenantContext.currentId())>0;}
}
