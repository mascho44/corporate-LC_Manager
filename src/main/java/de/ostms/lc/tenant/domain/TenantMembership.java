package de.ostms.lc.tenant.domain;

import de.ostms.lc.user.domain.*;
import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import org.springframework.security.access.AccessDeniedException;
import java.util.UUID;

/** Compatibility projection: existing user administration remains the write authority. */
@Entity @Immutable
@Table(name="tenant_membership",uniqueConstraints=@UniqueConstraint(columnNames={"tenant_id","user_id"}))
public class TenantMembership extends TenantOwnedEntity {
 @Id private UUID id;
 @ManyToOne(fetch=FetchType.EAGER,optional=false) @JoinColumn(name="user_id",nullable=false,updatable=false)
 private AppUser user;
 @ManyToOne(fetch=FetchType.EAGER,optional=false) @JoinColumn(name="role_id",nullable=false)
 private AppRole role;
 @Column(nullable=false) private boolean active;
 public UUID getId(){return id;}
 public AppUser getUser(){return user;}
 public AppRole getRole(){return role;}
 public boolean isActive(){return active;}
 @PrePersist @PreRemove private void rejectDirectWrites(){throw new AccessDeniedException("Memberships are maintained by user administration during bootstrap.");}
}
