package de.ostms.lc.document.service;

import de.ostms.lc.document.api.SplitPretrainingController.Preview;
import de.ostms.lc.tenant.domain.TenantContext;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** Bounded ephemeral jobs. PDFs exist only in queued/running callbacks, not in stored results. */
@Service
public class SplitPretrainingJobs {
 public record Status(UUID id,String state,String message,Preview result){}
 private static final class Job {
  final UUID id=UUID.randomUUID(),tenant;final String actor;
  volatile Status status;volatile Instant completed;
  Job(UUID tenant,String actor){this.tenant=tenant;this.actor=actor;status=new Status(id,"QUEUED","Wartet auf die Dokumentenerkennung …",null);}
 }
 private final Map<UUID,Job> jobs=new ConcurrentHashMap<>();
 private final ThreadPoolExecutor executor=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(2),r->{var thread=new Thread(r,"split-pretraining");thread.setDaemon(true);return thread;},new ThreadPoolExecutor.AbortPolicy());
 public synchronized Status submit(String actor,Callable<Preview> work){
  cleanup();
  if(jobs.size()>=8)throw new IllegalStateException("Zu viele Trainingsaufträge. Bitte später erneut versuchen.");
  var job=new Job(TenantContext.currentId(),actor);
  jobs.put(job.id,job);
  try{executor.execute(()->{
   try(var scope=TenantContext.open(job.tenant)){
    job.status=new Status(job.id,"RUNNING","PDF wird ausgelesen; bei Scans läuft die OCR. Große PDFs können mehrere Minuten benötigen …",null);
    var result=work.call();job.status=new Status(job.id,"COMPLETED","Vorschläge stehen zur Prüfung bereit.",result);
   }catch(Exception failure){
    if(failure instanceof InterruptedException)Thread.currentThread().interrupt();
    String message=failure instanceof IllegalArgumentException||failure instanceof IllegalStateException?failure.getMessage():"Vortraining fehlgeschlagen. Bitte erneut versuchen oder die PDF im Posteingang verarbeiten.";
    job.status=new Status(job.id,"FAILED",message==null?"Vortraining fehlgeschlagen.":message,null);
   }finally{job.completed=Instant.now();}
  });}catch(RejectedExecutionException busy){jobs.remove(job.id);throw new IllegalStateException("Dokumentenerkennung ausgelastet. Bitte später erneut versuchen.");}
  return new Status(job.id,"QUEUED","Trainingsauftrag angenommen.",null);
 }
 public Status status(UUID id,String actor){
  cleanup();var job=jobs.get(id);
  if(job==null||!job.tenant.equals(TenantContext.currentId())||!job.actor.equals(actor))throw new NoSuchElementException("Trainingsauftrag nicht gefunden oder abgelaufen. Bitte die PDF erneut laden.");
  return job.status;
 }
 private void cleanup(){var cutoff=Instant.now().minusSeconds(1800);jobs.values().removeIf(job->job.completed!=null&&job.completed.isBefore(cutoff));}
 @jakarta.annotation.PreDestroy public void shutdown(){executor.shutdownNow();jobs.clear();}
}
