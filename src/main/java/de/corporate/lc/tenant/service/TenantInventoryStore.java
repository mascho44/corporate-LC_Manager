package de.corporate.lc.tenant.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

/** Fixed SQL inventory; every aggregate is explicitly bound to the requested tenant. */
@Repository
public class TenantInventoryStore {
 public record Category(String key,long records,Long binaryBytes){}
 public record Jobs(long inboxExtraction,long outbox,long invitationMail){}
 private final JdbcTemplate jdbc;
 public TenantInventoryStore(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public List<Category> categories(UUID tenant){
  var result=new ArrayList<Category>();
  for(String table:List.of("letter_of_credit","lc_amendment","lc_note","lc_task","document_draft","document_check_decision","lc_requirement_mapping","training_learning_control","company_profile","document_approval_threshold","swift_import_record","charge_profile","charge_estimate","internal_rule_pack","internal_rule_pack_version","audit_event","integration_outbox","app_role","tenant_membership","tenant_membership_suspension"))
   result.add(count(table,"from "+table+" where tenant_id=?",tenant));
  for(String table:List.of("lc_required_document","lc_additional_field","lc_condition","document_comparison","email_delivery"))
   result.add(count(table,"from "+table+" c join letter_of_credit lc on lc.id=c.lc_id where lc.tenant_id=?",tenant));
  result.add(count("app_role_permission","from app_role_permission p join app_role r on r.id=p.role_id where r.tenant_id=?",tenant));
  for(String table:List.of("lc_document","document_inbox_item","document_template","training_session")){
   String column=table.equals("training_session")?"original_pdf":"content";
   result.add(jdbc.queryForObject("select count(*),coalesce(sum(octet_length("+column+")),0) from "+table+" where tenant_id=?",(rs,n)->new Category(table,rs.getLong(1),rs.getLong(2)),tenant));
  }
  result.add(count("platform_invitation","from platform_invitation where tenant_id=?",tenant));
  return List.copyOf(result);
 }
 private Category count(String key,String clause,UUID tenant){return new Category(key,number("select count(*) "+clause,tenant),null);}
 private long number(String sql,UUID tenant){return Objects.requireNonNull(jdbc.queryForObject(sql,Long.class,tenant));}
 public long sharedMemberships(UUID tenant){return number("select count(*) from tenant_membership m where m.tenant_id=? and exists(select 1 from tenant_membership other where other.user_id=m.user_id and other.tenant_id<>m.tenant_id)",tenant);}
 public Jobs jobs(UUID tenant){return new Jobs(
  number("select count(*) from document_inbox_item where tenant_id=? and status='OPEN' and extraction_status in ('QUEUED','PROCESSING')",tenant),
  number("select count(*) from integration_outbox where tenant_id=? and status in ('PENDING','DEAD_LETTER')",tenant),
  number("select count(*) from platform_invitation where tenant_id=? and encrypted_mail_payload is not null",tenant));}
}
