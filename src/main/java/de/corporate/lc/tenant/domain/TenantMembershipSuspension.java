package de.corporate.lc.tenant.domain;
import jakarta.persistence.*;
import java.util.UUID;

/** Tenant-local access restriction; never changes the global identity or home-role projection. */
@Entity @Table(name="tenant_membership_suspension",uniqueConstraints=@UniqueConstraint(columnNames={"tenant_id","user_id"}))
public class TenantMembershipSuspension extends TenantOwnedEntity {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @Column(name="user_id",nullable=false,updatable=false) private UUID userId;
 @Column(nullable=false) private boolean suspended;
 protected TenantMembershipSuspension(){}
 public TenantMembershipSuspension(UUID userId){this.userId=java.util.Objects.requireNonNull(userId);}
 public UUID getId(){return id;}
 public UUID getUserId(){return userId;}
 public boolean isSuspended(){return suspended;}
 public void setSuspended(boolean suspended){this.suspended=suspended;}
}
