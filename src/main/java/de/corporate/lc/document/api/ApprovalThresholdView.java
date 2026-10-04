package de.corporate.lc.document.api;

import de.corporate.lc.document.domain.DocumentApprovalThreshold;
import java.math.BigDecimal;

public record ApprovalThresholdView(String currency, BigDecimal minimumAmount, int requiredApprovals) {
    public static ApprovalThresholdView of(DocumentApprovalThreshold threshold) {
        return new ApprovalThresholdView(threshold.getCurrency(), threshold.getMinimumAmount(), threshold.getRequiredApprovals());
    }
}
