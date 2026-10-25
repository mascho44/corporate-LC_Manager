package de.ostms.lc.document.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "document_approval_threshold", uniqueConstraints =
        @UniqueConstraint(columnNames = {"tenant_id", "currency", "minimum_amount"}))
public class DocumentApprovalThreshold extends de.ostms.lc.tenant.domain.TenantOwnedEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, length = 3) private String currency;
    @Column(name = "minimum_amount", nullable = false, precision = 19, scale = 2) private BigDecimal minimumAmount;
    @Column(name = "required_approvals", nullable = false) private int requiredApprovals;

    public UUID getId() { return id; }
    public String getCurrency() { return currency; }
    public void setCurrency(String value) { currency = value; }
    public BigDecimal getMinimumAmount() { return minimumAmount; }
    public void setMinimumAmount(BigDecimal value) { minimumAmount = value; }
    public int getRequiredApprovals() { return requiredApprovals; }
    public void setRequiredApprovals(int value) { requiredApprovals = value; }
}
