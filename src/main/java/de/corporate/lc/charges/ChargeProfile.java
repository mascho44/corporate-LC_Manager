package de.corporate.lc.charges;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
@Entity @Table(name="charge_profile") public class ChargeProfile {
 @Id public UUID id=UUID.randomUUID();@Column(nullable=false,length=100) public String name;
 @Column(length=255) public String bankName;@Column(nullable=false,length=3) public String currency;
 @Column(nullable=false,columnDefinition="text") public String rulesJson;
 @Column(nullable=false,length=100) public String createdBy;@Column(nullable=false) public LocalDateTime createdAt=LocalDateTime.now();
}
