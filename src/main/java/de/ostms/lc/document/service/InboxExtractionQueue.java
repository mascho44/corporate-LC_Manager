package de.ostms.lc.document.service;

import de.ostms.lc.document.domain.*;
import de.ostms.lc.document.repository.DocumentInboxRepository;
import de.ostms.lc.audit.service.AuditService;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.data.domain.PageRequest;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Durable database queue: claim and finish briefly; never hold a transaction during OCR. */
@Component
public class InboxExtractionQueue {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(InboxExtractionQueue.class);
    private final DocumentInboxRepository inbox;
    private final DocumentExtractionService extraction;
    private final AuditService audit;
    private final TransactionTemplate transaction;
    private final ExecutorService executor=Executors.newSingleThreadExecutor(task->{var thread=new Thread(task,"inbox-extraction");thread.setDaemon(true);return thread;});
    private final AtomicBoolean busy=new AtomicBoolean();
    @org.springframework.beans.factory.annotation.Autowired(required=false) private de.ostms.lc.tenant.service.TenantWorkers tenantWorkers;
    @org.springframework.beans.factory.annotation.Autowired(required=false) private InboxAutomaticSplitter automaticSplitter;
    @org.springframework.beans.factory.annotation.Value("${lc.inbox.auto-split-enabled:true}") private boolean autoSplitEnabled=true;
    record Work(UUID id,UUID token,UUID tenantId,LcDocument document,String username,boolean automaticEligible){}
    public InboxExtractionQueue(DocumentInboxRepository inbox,DocumentExtractionService extraction,AuditService audit,PlatformTransactionManager manager){
        this.inbox=inbox;this.extraction=extraction;this.audit=audit;transaction=new TransactionTemplate(manager);
    }
    @Scheduled(fixedDelayString="${lc.inbox.extraction-interval-ms:2000}")
    public void dispatch(){
        if(!busy.compareAndSet(false,true))return;
        try{executor.execute(()->{try{if(tenantWorkers!=null)tenantWorkers.forEach(this::processNext);else de.ostms.lc.tenant.service.TenantJobRunner.run(de.ostms.lc.tenant.domain.Tenant.DEFAULT_ID,this::processNext);}catch(Exception failure){log.warn("Inbox extraction worker failed: type={}",failure.getClass().getSimpleName());}finally{busy.set(false);}});}
        catch(RejectedExecutionException stopped){busy.set(false);}
    }
    void processNext(){
        Work work=transaction.execute(status->claim());
        if(work==null)return;
        try(var scope=de.ostms.lc.tenant.domain.TenantContext.open(work.tenantId())){
        try{extraction.extractInBackground(work.document());}catch(Exception failure){work.document().setExtractionStatus("FAILED");}
        if(work.document().getExtractionStatus()==null)work.document().setExtractionStatus("FAILED");
        if(Thread.currentThread().isInterrupted())return; // Leave claim recoverable after shutdown.
        InboxAutomaticSplitter.Plan plan=InboxAutomaticSplitter.Plan.none();
        if(autoSplitEnabled&&automaticSplitter!=null&&work.automaticEligible())try{plan=automaticSplitter.prepare(work.document());}catch(Exception failure){log.warn("Automatic PDF splitting requires manual review: type={}",failure.getClass().getSimpleName());}
        var prepared=plan;
        Boolean completed=transaction.execute(status->{try{return finish(work,prepared);}catch(Exception failure){throw new IllegalStateException("Inbox completion failed.",failure);}});
        if(Boolean.TRUE.equals(completed))audit.record(work.username(),"DOCUMENT_INBOX_EXTRACTED","DOCUMENT_INBOX",work.id(),work.document().getExtractionStatus(),!List.of("FAILED","OCR_TIMEOUT","OCR_UNAVAILABLE").contains(work.document().getExtractionStatus()),null);
        }
    }
    private Work claim(){
        // Longer than the maximum configured document budget (30 min), plus margin.
        var expired=LocalDateTime.now().minusMinutes(35);
        for(UUID id:inbox.findExtractionCandidates(expired,PageRequest.of(0,10))){
            var item=inbox.findForUpdate(id).orElse(null);
            if(item==null||!"OPEN".equals(item.getStatus()))continue;
            // Defense in depth: validate ownership before claiming or reading payload data.
            de.ostms.lc.tenant.domain.TenantContext.require(item.getTenantId());
            if(item.getContent()==null)continue;
            boolean queued="QUEUED".equals(item.getExtractionStatus());
            boolean stale="PROCESSING".equals(item.getExtractionStatus())&&(item.getExtractionStartedAt()==null||item.getExtractionStartedAt().isBefore(expired));
            if(!queued&&!stale)continue;
            var token=UUID.randomUUID();item.setExtractionStatus("PROCESSING");item.setExtractionStartedAt(LocalDateTime.now());item.setExtractionToken(token);
            inbox.save(item);
            var document=new LcDocument();document.setOriginalFilename(item.getOriginalFilename());document.setContentType(item.getContentType());document.setContent(item.getContent());document.setDocumentType(DocumentType.ANNEX);
            return new Work(id,token,item.getTenantId(),document,item.getReceivedBy(),item.getSourceInboxId()==null&&ClassificationHistory.selectedType(item.getClassificationHistoryJson())==null);
        }
        return null;
    }
    private boolean finish(Work work,InboxAutomaticSplitter.Plan plan)throws Exception{
        var item=inbox.findForUpdate(work.id()).orElse(null);
        // Deletion or a reclaimed lease must not resurrect or overwrite an item.
        if(item==null||!work.tenantId().equals(item.getTenantId())||!"OPEN".equals(item.getStatus())||!"PROCESSING".equals(item.getExtractionStatus())||!work.token().equals(item.getExtractionToken()))return false;
        var result=work.document();item.setExtractionStatus(result.getExtractionStatus()==null?"FAILED":result.getExtractionStatus());
        item.setExtractedReference(result.getExtractedReference());item.setExtractedDocumentNumber(result.getExtractedDocumentNumber());
        item.setExtractedAmount(result.getExtractedAmount());item.setExtractedCurrency(result.getExtractedCurrency());item.setExtractedText(result.getExtractedText());
        var selected=ClassificationHistory.selectedType(item.getClassificationHistoryJson());
        item.setOcrEvidenceJson(result.getOcrEvidenceJson());item.setClassificationHistoryJson(selected==null?result.getClassificationHistoryJson():ClassificationHistory.manual(result.getClassificationHistoryJson(),selected,item.getReceivedBy()));
        item.setExtractionToken(null);item.setExtractionStartedAt(null);inbox.save(item);
        if(automaticSplitter!=null)automaticSplitter.persist(item,plan);
        return true;
    }
    @PreDestroy public void shutdown(){executor.shutdownNow();}
}
