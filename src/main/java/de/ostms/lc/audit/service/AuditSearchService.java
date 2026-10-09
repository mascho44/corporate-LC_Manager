package de.ostms.lc.audit.service;

import de.ostms.lc.audit.domain.AuditEvent;
import de.ostms.lc.tenant.domain.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Tenant-scoped audit search with optional filters, and the retention overview. Never deletes anything. */
@Service
public class AuditSearchService {
 public static final int DEFAULT_LIMIT=200,MAX_LIMIT=2000,EXPORT_LIMIT=50_000;
 public record Filter(LocalDate from,LocalDate to,String user,String action,String entityId){
  public Filter{if(from!=null&&to!=null&&from.isAfter(to))throw new IllegalArgumentException("Der Beginn des Zeitraums liegt nach dem Ende.");}
  public boolean isEmpty(){return from==null&&to==null&&blank(user)&&blank(action)&&blank(entityId);}
  public String describe(){
   var parts=new java.util.ArrayList<String>();
   if(from!=null)parts.add("von "+from);if(to!=null)parts.add("bis "+to);if(!blank(user))parts.add("Benutzer "+user.trim());if(!blank(action))parts.add("Aktion "+action.trim());if(!blank(entityId))parts.add("Objekt "+entityId.trim());
   return parts.isEmpty()?"ohne Filter":String.join(", ",parts);
  }
 }
 public record Retention(int retentionYears,LocalDateTime oldestEvent,long eventsBeyondRetention,boolean automaticDeletion,String note){}
 @PersistenceContext private EntityManager em;
 private final int retentionYears;
 public AuditSearchService(@Value("${lc.audit.retention-years:10}") int retentionYears){this.retentionYears=Math.max(1,Math.min(retentionYears,100));}
 private static boolean blank(String v){return v==null||v.isBlank();}
 private static String like(String value){return "%"+value.trim().toLowerCase(java.util.Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";}

 @Transactional(readOnly=true)
 public List<AuditEvent> search(Filter filter,int limit){
  var jpql=new StringBuilder("select e from AuditEvent e where e.tenantId=:tenant");
  if(filter.from()!=null)jpql.append(" and e.occurredAt>=:from");
  if(filter.to()!=null)jpql.append(" and e.occurredAt<:toExclusive");
  if(!blank(filter.user()))jpql.append(" and lower(e.username) like :user escape '\\'");
  if(!blank(filter.action()))jpql.append(" and lower(e.action) like :action escape '\\'");
  if(!blank(filter.entityId()))jpql.append(" and e.entityId=:entityId");
  jpql.append(" order by e.occurredAt desc");
  var query=em.createQuery(jpql.toString(),AuditEvent.class).setParameter("tenant",TenantContext.currentId());
  if(filter.from()!=null)query.setParameter("from",filter.from().atStartOfDay());
  if(filter.to()!=null)query.setParameter("toExclusive",filter.to().plusDays(1).atStartOfDay());
  if(!blank(filter.user()))query.setParameter("user",like(filter.user()));
  if(!blank(filter.action()))query.setParameter("action",like(filter.action()));
  if(!blank(filter.entityId()))query.setParameter("entityId",filter.entityId().trim());
  return query.setMaxResults(Math.max(1,Math.min(limit,EXPORT_LIMIT))).getResultList();
 }

 @Transactional(readOnly=true)
 public Retention retention(){
  var tenant=TenantContext.currentId();
  LocalDateTime oldest=em.createQuery("select min(e.occurredAt) from AuditEvent e where e.tenantId=:tenant",LocalDateTime.class).setParameter("tenant",tenant).getSingleResult();
  long beyond=em.createQuery("select count(e) from AuditEvent e where e.tenantId=:tenant and e.occurredAt<:cutoff",Long.class).setParameter("tenant",tenant).setParameter("cutoff",LocalDateTime.now().minusYears(retentionYears)).getSingleResult();
  return new Retention(retentionYears,oldest,beyond,false,"Einträge werden nie automatisch gelöscht und dürfen vor Ablauf der Frist nicht entfernt werden. Eine spätere Löschung nach Fristablauf ist ein bewusster, dokumentierter Betriebsvorgang.");
 }
}
