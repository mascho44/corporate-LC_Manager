package de.ostms.lc.rulepack;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
class PackMigrationTest {
 @Test void migrationEnforcesUniqueVersionsAndSelectionReferences()throws Exception{
  try(var c=DriverManager.getConnection("jdbc:h2:mem:packmigration;MODE=PostgreSQL");var resource=getClass().getResourceAsStream("/db/migration/V44__internal_rule_pack_import.sql")){
   var sql=c.createStatement();
   for(String part:new String(resource.readAllBytes(),StandardCharsets.UTF_8).split(";"))if(!part.isBlank())sql.execute(part);
   sql.execute("insert into internal_rule_pack_version values('00000000-0000-0000-0000-000000000001','own-pack','1.0.0','{}','"+"a".repeat(64)+"',true,'synthetic-admin',current_timestamp)");
   assertThatThrownBy(()->sql.execute("insert into internal_rule_pack_version values('00000000-0000-0000-0000-000000000002','own-pack','1.0.0','{}','"+"a".repeat(64)+"',true,'synthetic-admin',current_timestamp)")).isInstanceOf(SQLException.class);
   assertThatThrownBy(()->sql.execute("insert into internal_rule_pack values('own-pack','00000000-0000-0000-0000-000000000009',null,0)")).isInstanceOf(SQLException.class);
   sql.execute("insert into internal_rule_pack values('own-pack','00000000-0000-0000-0000-000000000001',null,0)");
  }
 }
}
