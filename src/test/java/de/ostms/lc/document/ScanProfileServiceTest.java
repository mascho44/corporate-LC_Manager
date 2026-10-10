package de.ostms.lc.document;
import de.ostms.lc.document.repository.TenantScanProfileRepository;
import de.ostms.lc.document.service.ScanProfile;
import de.ostms.lc.document.service.ScanProfileService;
import de.ostms.lc.tenant.domain.Tenant;
import de.ostms.lc.tenant.domain.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop","spring.datasource.url=jdbc:h2:mem:scanprofile;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver"},showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
class ScanProfileServiceTest {
 @Autowired TenantScanProfileRepository repo;@Autowired de.ostms.lc.tenant.repository.TenantRepository tenants;
 ScanProfileService service;
 @BeforeEach void setUp(){if(tenants.findById(Tenant.DEFAULT_ID).isEmpty())tenants.saveAndFlush(new Tenant());service=new ScanProfileService(repo);}

 @Test void aTenantWithoutASettingUsesStandard(){
  assertThat(service.current()).isSameAs(ScanProfile.STANDARD);
  var view=service.view();assertThat(view.current()).isEqualTo("STANDARD");assertThat(view.available()).hasSize(3);assertThat(view.changedBy()).isNull();
 }
 @Test void changingThePersistsAndReturnsThePreviousProfile(){
  assertThat(service.change("PROFI_SCANNER","anna")).isEqualTo("STANDARD");
  assertThat(service.current()).isSameAs(ScanProfile.PROFI_SCANNER);assertThat(service.view().changedBy()).isEqualTo("anna");
  assertThat(service.change("SCHLECHTER_SCAN","ben")).isEqualTo("PROFI_SCANNER");
  assertThat(repo.findAll()).as("one row per tenant").hasSize(1);
 }
 @Test void unknownProfilesAreRejectedAndKeepTheCurrentOne(){
  service.change("PROFI_SCANNER","anna");
  assertThatThrownBy(()->service.change("TURBO","anna")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->service.change(null,"anna")).isInstanceOf(IllegalArgumentException.class);
  assertThat(service.current()).isSameAs(ScanProfile.PROFI_SCANNER);
 }
 @Test void aStoredUnknownValueFallsBackToStandard(){
  service.change("PROFI_SCANNER","anna");var row=repo.current().orElseThrow();row.change("RETIRED","x");repo.saveAndFlush(row);
  assertThat(service.current()).isSameAs(ScanProfile.STANDARD);
 }
}
