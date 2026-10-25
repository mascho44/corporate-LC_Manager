package de.ostms.lc.lc.service;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
class LcToolsMigrationTest {
 @Test void newTablesAcceptSnapshotsAndEnforceReferences() throws Exception {
  try(var c=DriverManager.getConnection("jdbc:h2:mem:lctoolsmigration;MODE=PostgreSQL")){
   var sql=c.createStatement();sql.execute("create table letter_of_credit(id uuid primary key)");
   for(String name:new String[]{"V42__format_neutral_lc_conditions.sql","V43__lc_charge_estimates.sql"}){
    try(var resource=getClass().getResourceAsStream("/db/migration/"+name)){
     assertThat(resource).isNotNull();
     for(String part:new String(resource.readAllBytes(),StandardCharsets.UTF_8).split(";"))
      if(!part.isBlank())sql.execute(part);
    }
   }
   sql.execute("insert into letter_of_credit values ('00000000-0000-0000-0000-000000000001')");
   sql.execute("insert into lc_condition values ('00000000-0000-0000-0000-000000000001','APPLICABLE_RULES','UCP 600')");
   assertThatThrownBy(()->sql.execute("insert into lc_condition values ('00000000-0000-0000-0000-000000000009','GOODS_DESCRIPTION','test')")).isInstanceOf(SQLException.class);
   sql.execute("delete from letter_of_credit");
   try(var rows=sql.executeQuery("select count(*) from lc_condition")){rows.next();assertThat(rows.getInt(1)).isZero();}
  }
 }
 @Test void amendmentUsesAndUpdatesCanonicalGoods() {
  var lc=new de.ostms.lc.lc.domain.LetterOfCredit();
  lc.setRawMessage(":45A:OLD IMPORT VALUE");
  lc.getConditions().put(de.ostms.lc.lc.domain.LcCondition.GOODS_DESCRIPTION,"CURRENT GOODS");
  var service=new AmendmentService(org.mockito.Mockito.mock(de.ostms.lc.lc.repository.AmendmentRepository.class),
   org.mockito.Mockito.mock(de.ostms.lc.lc.repository.LetterOfCreditRepository.class),new de.ostms.lc.swift.Mt707Parser());
  service.applyGoodsChanges(lc,"/ADD/NEW GOODS");
  assertThat(LcConditions.value(lc,de.ostms.lc.lc.domain.LcCondition.GOODS_DESCRIPTION)).contains("CURRENT GOODS\nNEW GOODS");
  assertThat(AmendmentSnapshot.capture(lc)).contains("conditions","CURRENT GOODS");
 }
}
