package de.ostms.lc.tenant;
import de.ostms.lc.tenant.domain.*;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
class TenantContextTest {
 @Test void nestedScopesRestoreAndDoNotLeakAfterFailure(){
  var id=UUID.randomUUID();try(var outer=TenantContext.open(id)){
   assertThat(TenantContext.currentId()).isEqualTo(id);
   assertThatThrownBy(()->{try(var inner=TenantContext.open(UUID.randomUUID())){throw new IllegalStateException("synthetic");}}).isInstanceOf(IllegalStateException.class);
   assertThat(TenantContext.currentId()).isEqualTo(id);
  }assertThat(TenantContext.currentId()).isEqualTo(Tenant.DEFAULT_ID);
 }
 @Test void threadsDoNotInheritAnotherRequestsScope()throws Exception{
  try(var scope=TenantContext.open(UUID.randomUUID())){var result=new java.util.concurrent.atomic.AtomicReference<UUID>();var thread=new Thread(()->result.set(TenantContext.currentId()));thread.start();thread.join();assertThat(result.get()).isEqualTo(Tenant.DEFAULT_ID);}
 }
}
