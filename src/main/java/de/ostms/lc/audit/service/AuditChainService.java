package de.ostms.lc.audit.service;

import de.ostms.lc.tenant.domain.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

/** Verifies the per-tenant hash chain with the database function that also writes it (single source of truth). */
@Service
public class AuditChainService {
 public record Chain(String tenantCode,boolean ok,long checked,Long firstBadSeq,Long headSeq,String headHash,long unchained,String reason){}
 private final JdbcTemplate jdbc;
 public AuditChainService(JdbcTemplate jdbc){this.jdbc=jdbc;}

 @Transactional(readOnly=true)
 public Chain verifyCurrent(){return verify(TenantContext.currentId());}

 /** All tenants; callers must have verified live platform access. */
 @Transactional(readOnly=true)
 public List<Chain> verifyAll(){
  return jdbc.query("select id from tenant order by code",(rs,n)->rs.getObject(1,UUID.class)).stream().map(this::verify).toList();
 }

 private Chain verify(UUID tenantId){
  String code=jdbc.queryForObject("select code from tenant where id=?",String.class,tenantId);
  return jdbc.queryForObject("select ok,checked,first_bad_seq,head_seq,head_hash,unchained,reason from verify_audit_chain(?)",
   (rs,n)->new Chain(code,rs.getBoolean(1),rs.getLong(2),(Long)rs.getObject(3),(Long)rs.getObject(4),rs.getString(5),rs.getLong(6),rs.getString(7)),tenantId);
 }
}
