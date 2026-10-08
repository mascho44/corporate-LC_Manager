package de.corporate.lc.document.service;
import de.corporate.lc.tenant.domain.TenantContext;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
class SplitPretrainingJobsTest {
 @Test void runsUnderCapturedTenantAndHidesJobsFromOtherUsersAndTenants()throws Exception{
  var jobs=new SplitPretrainingJobs();UUID tenant=UUID.randomUUID();var ran=new CompletableFuture<UUID>();
  try{
   UUID id;
   try(var scope=TenantContext.open(tenant)){
    id=jobs.submit("reviewer",()->{ran.complete(TenantContext.currentId());return null;}).id();
    assertThat(ran.get(3,TimeUnit.SECONDS)).isEqualTo(tenant);
    assertThatThrownBy(()->jobs.status(id,"other")).isInstanceOf(NoSuchElementException.class);
    assertThat(jobs.status(id,"reviewer").id()).isEqualTo(id);
   }
   assertThatThrownBy(()->jobs.status(id,"reviewer")).isInstanceOf(NoSuchElementException.class);
  }finally{jobs.shutdown();}
 }
 @Test void queueIsBoundedAndSubmissionDoesNotWaitForOcr()throws Exception{
  var jobs=new SplitPretrainingJobs();var running=new CountDownLatch(1);var release=new CountDownLatch(1);
  try{
   jobs.submit("one",()->{running.countDown();release.await();return null;});assertThat(running.await(3,TimeUnit.SECONDS)).isTrue();
   jobs.submit("two",()->null);jobs.submit("three",()->null);
   assertThatThrownBy(()->jobs.submit("four",()->null)).isInstanceOf(IllegalStateException.class).hasMessageContaining("ausgelastet");
  }finally{release.countDown();jobs.shutdown();}
 }
 @Test void reportsSafeFailureWithoutLeakingInternalExceptionDetails()throws Exception{
  var jobs=new SplitPretrainingJobs();
  try{
   var id=jobs.submit("reviewer",()->{throw new java.io.IOException("private PDF content");}).id();
   long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);SplitPretrainingJobs.Status status;
   do{status=jobs.status(id,"reviewer");if(status.state().equals("FAILED"))break;Thread.sleep(10);}while(System.nanoTime()<deadline);
   assertThat(status.state()).isEqualTo("FAILED");assertThat(status.message()).doesNotContain("private PDF content");assertThat(status.result()).isNull();
  }finally{jobs.shutdown();}
 }
}
