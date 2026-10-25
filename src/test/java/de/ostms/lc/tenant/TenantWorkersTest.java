package de.ostms.lc.tenant;
import de.ostms.lc.tenant.domain.*;
import de.ostms.lc.tenant.repository.TenantRepository;
import de.ostms.lc.tenant.service.TenantWorkers;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class TenantWorkersTest {
 @Test void eachPersistedTenantHasItsOwnScopeAndFailureDoesNotLeak(){
  var tenants=mock(TenantRepository.class);var second=new Tenant("synthetic-second","Second","en",true,false);when(tenants.findAll()).thenReturn(List.of(new Tenant(),second));
  var seen=new ArrayList<UUID>();var caller=UUID.randomUUID();try(var scope=TenantContext.open(caller)){
   new TenantWorkers(tenants).forEach(()->{seen.add(TenantContext.currentId());if(Tenant.DEFAULT_ID.equals(TenantContext.currentId()))throw new IllegalStateException("Synthetic failure");});
   assertThat(TenantContext.currentId()).isEqualTo(caller);
  }
  assertThat(seen).containsExactly(Tenant.DEFAULT_ID,second.getId());
 }
}
