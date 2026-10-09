package de.ostms.lc.lc;

import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import de.ostms.lc.tenant.domain.Tenant;
import de.ostms.lc.tenant.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** Loading two collections of one LC with a joined entity graph multiplied the list once per additional field. */
@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop","spring.datasource.url=jdbc:h2:mem:lccollections;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver"},showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
class LetterOfCreditCollectionsTest {
 @Autowired TenantRepository tenants;
 @Autowired LetterOfCreditRepository lcs;
 @Autowired EntityManager em;
 @Test void requiredDocumentsAreNotMultipliedByAdditionalFields(){
  tenants.saveAndFlush(new Tenant());
  var lc=new LetterOfCredit();lc.setReference("COLLECTIONS-1");
  lc.setRequiredDocuments(new java.util.ArrayList<>(List.of("INVOICE","PACKING LIST")));
  lc.getAdditionalFields().put("40A - Form","IRREVOCABLE");lc.getAdditionalFields().put("41D - Verfügbar","ANY BANK");lc.getAdditionalFields().put("71D - Gebühren","BENEFICIARY");
  lcs.saveAndFlush(lc);em.clear();
  assertThat(lcs.findById(lc.getId()).orElseThrow().getRequiredDocuments()).containsExactly("INVOICE","PACKING LIST");
  em.clear();
  var all=lcs.findAll();assertThat(all).hasSize(1);
  assertThat(all.get(0).getRequiredDocuments()).containsExactly("INVOICE","PACKING LIST");
  assertThat(all.get(0).getAdditionalFields()).hasSize(3);
 }
}
