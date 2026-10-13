package de.ostms.lc.customer;

import de.ostms.lc.tenant.domain.TenantOwnedEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name="inbox_customer_assignment")
public class InboxCustomerAssignment extends TenantOwnedEntity {
 @Id private UUID id=UUID.randomUUID();
 @Column(name="inbox_item_id",nullable=false) private UUID inboxItemId;
 @Column(name="customer_id",nullable=false) private UUID customerId;
 @Column(nullable=false,length=12) private String method;
 @Column(name="assigned_by",length=100) private String assignedBy;
 @Column(name="assigned_at",nullable=false) private LocalDateTime assignedAt=LocalDateTime.now();
 public InboxCustomerAssignment(){}
 public InboxCustomerAssignment(UUID inboxItemId){this.inboxItemId=inboxItemId;}
 public UUID getInboxItemId(){return inboxItemId;} public UUID getCustomerId(){return customerId;} public String getMethod(){return method;} public String getAssignedBy(){return assignedBy;}
 public void assign(UUID customerId,String method,String user){this.customerId=customerId;this.method=method;this.assignedBy=user;this.assignedAt=LocalDateTime.now();}
}
