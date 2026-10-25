package de.ostms.lc.audit.service;

import de.ostms.lc.tenant.domain.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuditChainServiceTest {
 @SuppressWarnings("unchecked")
 private JdbcTemplate jdbcFor(UUID tenant,String code,boolean ok,Long bad,String reason)throws Exception{
  var jdbc=mock(JdbcTemplate.class);var rs=mock(ResultSet.class);
  when(rs.getBoolean(1)).thenReturn(ok);when(rs.getLong(2)).thenReturn(150L);when(rs.getObject(3)).thenReturn(bad);when(rs.getObject(4)).thenReturn(ok?150L:null);
  when(rs.getString(5)).thenReturn(ok?"ab".repeat(32):null);when(rs.getLong(6)).thenReturn(42L);when(rs.getString(7)).thenReturn(reason);
  when(jdbc.queryForObject(eq("select code from tenant where id=?"),eq(String.class),eq(tenant))).thenReturn(code);
  when(jdbc.queryForObject(startsWith("select ok,"),any(RowMapper.class),eq(tenant))).thenAnswer(call->((RowMapper<Object>)call.getArgument(1)).mapRow(rs,0));
  return jdbc;
 }
 @Test void reportsAnIntactChainWithItsHeadAndUnchainedCount()throws Exception{
  var tenant=UUID.randomUUID();var service=new AuditChainService(jdbcFor(tenant,"acme",true,null,null));
  try(var scope=TenantContext.open(tenant)){
   var chain=service.verifyCurrent();
   assertThat(chain.ok()).isTrue();assertThat(chain.tenantCode()).isEqualTo("acme");assertThat(chain.checked()).isEqualTo(150);assertThat(chain.headSeq()).isEqualTo(150);assertThat(chain.headHash()).hasSize(64);assertThat(chain.unchained()).isEqualTo(42);assertThat(chain.firstBadSeq()).isNull();
  }
 }
 @Test void reportsTheFirstBrokenPosition()throws Exception{
  var tenant=UUID.randomUUID();var service=new AuditChainService(jdbcFor(tenant,"acme",false,57L,"Inhalt veraendert"));
  try(var scope=TenantContext.open(tenant)){
   var chain=service.verifyCurrent();
   assertThat(chain.ok()).isFalse();assertThat(chain.firstBadSeq()).isEqualTo(57);assertThat(chain.reason()).isEqualTo("Inhalt veraendert");assertThat(chain.headHash()).isNull();
  }
 }
 @SuppressWarnings("unchecked")
 @Test void verifyAllChecksEveryTenantAndKeepsTheirOrder()throws Exception{
  var a=UUID.randomUUID();var b=UUID.randomUUID();
  var jdbc=mock(JdbcTemplate.class);
  when(jdbc.query(eq("select id from tenant order by code"),any(RowMapper.class))).thenReturn(List.of(a,b));
  for(var entry:java.util.Map.of(a,"alpha",b,"beta").entrySet()){
   var rs=mock(ResultSet.class);boolean ok=entry.getValue().equals("alpha");
   when(rs.getBoolean(1)).thenReturn(ok);when(rs.getLong(2)).thenReturn(3L);when(rs.getObject(3)).thenReturn(ok?null:2L);when(rs.getObject(4)).thenReturn(ok?3L:null);when(rs.getString(5)).thenReturn(ok?"c".repeat(64):null);when(rs.getLong(6)).thenReturn(0L);when(rs.getString(7)).thenReturn(ok?null:"Luecke in der Folge");
   when(jdbc.queryForObject(eq("select code from tenant where id=?"),eq(String.class),eq(entry.getKey()))).thenReturn(entry.getValue());
   when(jdbc.queryForObject(startsWith("select ok,"),any(RowMapper.class),eq(entry.getKey()))).thenAnswer(call->((RowMapper<Object>)call.getArgument(1)).mapRow(rs,0));
  }
  var chains=new AuditChainService(jdbc).verifyAll();
  assertThat(chains).extracting(AuditChainService.Chain::tenantCode).containsExactly("alpha","beta");
  assertThat(chains).extracting(AuditChainService.Chain::ok).containsExactly(true,false);
  assertThat(chains.get(1).firstBadSeq()).isEqualTo(2);
 }
}
