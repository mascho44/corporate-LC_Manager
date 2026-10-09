package de.ostms.lc.user.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

/** Fixed read-only SQL across tenants for the platform area. No business data, audit details or values leave this store. */
@Repository
public class PlatformOverviewStore {
 public record Membership(String username,String displayName,boolean accountActive,String tenantCode,String tenantName,boolean tenantActive,String roleName,boolean membershipActive,boolean suspended){}
 public record AuditRow(LocalDateTime occurredAt,String tenantCode,String username,String action,String entityType,boolean successful){}
 private final JdbcTemplate jdbc;
 public PlatformOverviewStore(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public List<Membership> memberships(){
  return jdbc.query("select u.username,u.display_name,u.active,t.code,t.name,t.active,r.name,m.active,coalesce(s.suspended,false) "
   +"from tenant_membership m join app_user u on u.id=m.user_id join tenant t on t.id=m.tenant_id join app_role r on r.id=m.role_id "
   +"left join tenant_membership_suspension s on s.tenant_id=m.tenant_id and s.user_id=m.user_id "
   +"order by lower(u.username),t.code",(rs,n)->new Membership(rs.getString(1),rs.getString(2),rs.getBoolean(3),rs.getString(4),rs.getString(5),rs.getBoolean(6),rs.getString(7),rs.getBoolean(8),rs.getBoolean(9)));
 }
 public List<AuditRow> audit(String tenantCode,int limit){
  String sql="select a.occurred_at,t.code,a.username,a.action,a.entity_type,a.successful from audit_event a join tenant t on t.id=a.tenant_id "
   +(tenantCode==null?"":"where t.code=? ")+"order by a.occurred_at desc limit ?";
  var mapper=(org.springframework.jdbc.core.RowMapper<AuditRow>)(rs,n)->new AuditRow(rs.getTimestamp(1).toLocalDateTime(),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getBoolean(6));
  return tenantCode==null?jdbc.query(sql,mapper,limit):jdbc.query(sql,mapper,tenantCode,limit);
 }
}
