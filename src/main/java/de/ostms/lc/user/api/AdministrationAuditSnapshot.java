package de.ostms.lc.user.api;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;

/** Allowlisted administration metadata only; never serialize entities or requests. */
public final class AdministrationAuditSnapshot {
 private static final ObjectMapper JSON=new ObjectMapper();
 private AdministrationAuditSnapshot(){}
 public static String role(RoleView role){
  var values=new LinkedHashMap<String,Object>();values.put("name",role.name());values.put("baseRole",role.baseRole());values.put("systemRole",role.systemRole());values.put("permissions",role.permissions().stream().map(Enum::name).sorted().toList());return encode(values);
 }
 public static String user(UserView user){
  var values=new LinkedHashMap<String,Object>();values.put("username",user.username());values.put("roleId",user.roleId());values.put("role",user.role());values.put("active",user.active());return encode(values);
 }
 public static String membership(de.ostms.lc.tenant.service.TenantMembershipService.Membership membership){
  var values=new LinkedHashMap<String,Object>();values.put("username",membership.username());values.put("roleId",membership.roleId());values.put("permissions",membership.permissions().stream().map(Enum::name).sorted().toList());values.put("active",membership.active());values.put("suspended",membership.suspended());return encode(values);
 }
 private static String encode(Object values){try{return JSON.writeValueAsString(values);}catch(JsonProcessingException e){throw new IllegalStateException("Administration audit snapshot could not be created.",e);}}
}
