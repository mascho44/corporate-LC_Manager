package de.ostms.lc.document.service;

import de.ostms.lc.document.api.ApprovalThresholdRequest;
import de.ostms.lc.document.api.ApprovalThresholdView;
import de.ostms.lc.document.domain.DocumentApprovalThreshold;
import de.ostms.lc.document.repository.DocumentApprovalThresholdRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

@Service
public class DocumentApprovalPolicyService {
    private final DocumentApprovalThresholdRepository thresholds;

    public DocumentApprovalPolicyService(DocumentApprovalThresholdRepository thresholds) { this.thresholds = thresholds; }

    @Transactional(readOnly = true)
    public List<ApprovalThresholdView> all() {
        return thresholds.findAllByOrderByCurrencyAscMinimumAmountAsc().stream().map(ApprovalThresholdView::of).toList();
    }

    @Transactional
    public List<ApprovalThresholdView> replace(List<ApprovalThresholdRequest> rules) {
        Set<String> unique = new HashSet<>();
        for (ApprovalThresholdRequest rule : rules) {
            String currency = rule.currency().trim().toUpperCase(Locale.ROOT);
            String key = currency + ":" + rule.minimumAmount().stripTrailingZeros().toPlainString();
            if (!unique.add(key)) throw new IllegalArgumentException("Doppelte Betragsgrenze für " + currency + " " + rule.minimumAmount() + ".");
        }
        thresholds.deleteForCurrentTenant();
        List<DocumentApprovalThreshold> saved = rules.stream().map(rule -> {
            DocumentApprovalThreshold threshold = new DocumentApprovalThreshold();
            threshold.setCurrency(rule.currency().trim().toUpperCase(Locale.ROOT));
            threshold.setMinimumAmount(rule.minimumAmount().setScale(2));
            threshold.setRequiredApprovals(rule.requiredApprovals());
            return thresholds.save(threshold);
        }).toList();
        return saved.stream().sorted(Comparator.comparing(DocumentApprovalThreshold::getCurrency)
                .thenComparing(DocumentApprovalThreshold::getMinimumAmount)).map(ApprovalThresholdView::of).toList();
    }

    @Transactional(readOnly = true)
    public int requiredApprovals(BigDecimal amount, String currency) {
        if (amount == null || currency == null || currency.isBlank()) return 1;
        return thresholds.findAllByOrderByCurrencyAscMinimumAmountAsc().stream()
                .filter(rule -> rule.getCurrency().equalsIgnoreCase(currency.trim()) && amount.compareTo(rule.getMinimumAmount()) >= 0)
                .max(Comparator.comparing(DocumentApprovalThreshold::getMinimumAmount))
                .map(DocumentApprovalThreshold::getRequiredApprovals).orElse(1);
    }
}
