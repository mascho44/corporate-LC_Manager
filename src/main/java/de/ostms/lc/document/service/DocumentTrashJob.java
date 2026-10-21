package de.ostms.lc.document.service;

import de.ostms.lc.tenant.service.TenantWorkers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

/** Daily removal of documents that stayed in the trash longer than the retention period, for every tenant. */
@Component
public class DocumentTrashJob {
    private static final Logger log = LoggerFactory.getLogger(DocumentTrashJob.class);
    private final TenantWorkers tenants; private final DocumentTrashService trash;
    public DocumentTrashJob(TenantWorkers tenants, DocumentTrashService trash) { this.tenants = tenants; this.trash = trash; }

    @Scheduled(fixedDelayString = "${app.trash.purge-ms:86400000}", initialDelayString = "${app.trash.initial-ms:600000}")
    public void purge() {
        try { tenants.forEach(() -> { int removed = trash.purgeExpired(LocalDateTime.now()); if (removed > 0) log.info("Trash: {} expired documents removed", removed); }); }
        catch (RuntimeException failure) { log.warn("Trash cleanup failed", failure); }
    }
}
