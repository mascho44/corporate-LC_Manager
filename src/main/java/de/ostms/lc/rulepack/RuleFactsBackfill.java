package de.ostms.lc.rulepack;

import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import de.ostms.lc.tenant.service.TenantWorkers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Fills the check data of files that were imported before the automatic filling existed; only files whose check data was never touched. */
@Component
public class RuleFactsBackfill {
    private static final Logger log = LoggerFactory.getLogger(RuleFactsBackfill.class);
    private final TenantWorkers tenants; private final LetterOfCreditRepository lcs; private final AutoRuleFacts auto; private final TransactionTemplate tx;
    public RuleFactsBackfill(TenantWorkers tenants, LetterOfCreditRepository lcs, AutoRuleFacts auto, TransactionTemplate tx) { this.tenants = tenants; this.lcs = lcs; this.auto = auto; this.tx = tx; }

    @Scheduled(fixedDelayString = "${app.rulefacts.backfill-ms:86400000}", initialDelayString = "${app.rulefacts.backfill-initial-ms:180000}")
    public void run() {
        try {
            tenants.forEach(() -> {
                Integer files = tx.execute(status -> {
                    int n = 0;
                    for (var lc : lcs.findAll()) {
                        boolean untouched = (lc.getRuleFactsJson() == null || lc.getRuleFactsJson().isBlank()) && (lc.getAutoRuleFacts() == null || lc.getAutoRuleFacts().isBlank());
                        if (untouched && auto.applyWithDocuments(lc) > 0) { lcs.save(lc); n++; }
                    }
                    return n;
                });
                if (files != null && files > 0) log.info("Rule facts: {} files pre-filled", files);
            });
        } catch (RuntimeException failure) { log.warn("Rule facts backfill failed", failure); }
    }
}
