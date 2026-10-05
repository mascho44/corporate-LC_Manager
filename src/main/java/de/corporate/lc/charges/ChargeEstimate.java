package de.corporate.lc.charges;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
@Entity @Table(name="charge_estimate") public class ChargeEstimate {
 @Id public UUID id=UUID.randomUUID();@Column(nullable=false) public UUID lcId;
 @Column(nullable=false) public UUID profileId;@Column(nullable=false,columnDefinition="text") public String resultJson;
 @Column(nullable=false,columnDefinition="text") public String profileSnapshot;
 @Column(nullable=false,length=100) public String createdBy;@Column(nullable=false) public LocalDateTime createdAt=LocalDateTime.now();
}
