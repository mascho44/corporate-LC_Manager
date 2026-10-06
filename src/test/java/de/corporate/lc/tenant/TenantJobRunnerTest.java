package de.corporate.lc.tenant;
import de.corporate.lc.tenant.domain.*;
import de.corporate.lc.tenant.service.TenantJobRunner;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;

class TenantJobRunnerTest {
 @Test void unsupportedAndMissingTenantsDoNotExecuteJobs(){
  var executed=new AtomicBoolean();
  assertThatThrownBy(()->TenantJobRunner.run(UUID.randomUUID(),()->executed.set(true))).isInstanceOf(AccessDeniedException.class);
  assertThatThrownBy(()->TenantJobRunner.run(null,()->executed.set(true))).isInstanceOf(AccessDeniedException.class);
  assertThat(executed).isFalse();assertThat(TenantContext.currentId()).isEqualTo(Tenant.DEFAULT_ID);
 }
 @Test void workerThreadDoesNotInheritRequestTenant()throws Exception{
  var worker=java.util.concurrent.Executors.newSingleThreadExecutor();
  try(var scope=TenantContext.open(UUID.randomUUID())){
   worker.submit(()->TenantJobRunner.run(Tenant.DEFAULT_ID,()->assertThat(TenantContext.currentId()).isEqualTo(Tenant.DEFAULT_ID))).get();
   assertThat(worker.submit(TenantContext::currentId).get()).isEqualTo(Tenant.DEFAULT_ID);
  }finally{worker.shutdownNow();}
 }
 @Test void workerUsesExplicitTenantAndRestoresCallerAfterSuccess(){
  var caller=UUID.randomUUID();try(var scope=TenantContext.open(caller)){
   TenantJobRunner.run(Tenant.DEFAULT_ID,()->assertThat(TenantContext.currentId()).isEqualTo(Tenant.DEFAULT_ID));
   assertThat(TenantContext.currentId()).isEqualTo(caller);
  }
 }
 @Test void failedWorkerRestoresCallerAndPropagatesFailure(){
  var caller=UUID.randomUUID();try(var scope=TenantContext.open(caller)){
   assertThatThrownBy(()->TenantJobRunner.run(Tenant.DEFAULT_ID,()->{throw new IllegalStateException("Synthetic failure");})).isInstanceOf(IllegalStateException.class);
   assertThat(TenantContext.currentId()).isEqualTo(caller);
  }
 }
}
