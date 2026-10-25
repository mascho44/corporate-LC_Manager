package de.ostms.lc.document.domain;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
@Entity @Table(name="document_comparison")
public class DocumentComparison extends de.ostms.lc.tenant.domain.TenantOwnedEntity {
 @Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id;
 public UUID lcId;public UUID beforeDocumentId;public UUID afterDocumentId;
 @Column(columnDefinition="text",nullable=false) public String resultJson;
 public String createdBy;public LocalDateTime createdAt;
}
