package de.corporate.lc.lc.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "lc_note")
public class LcNote extends de.corporate.lc.tenant.domain.TenantOwnedEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "letter_of_credit_id", nullable = false) private UUID letterOfCreditId;
    @Column(nullable = false, length = 100) private String username;
    @Column(nullable = false, length = 2000) private String content;
    @Column(nullable = false) private LocalDateTime createdAt = LocalDateTime.now();

    public UUID getId(){return id;}
    public UUID getLetterOfCreditId(){return letterOfCreditId;} public void setLetterOfCreditId(UUID value){letterOfCreditId=value;}
    public String getUsername(){return username;} public void setUsername(String value){username=value;}
    public String getContent(){return content;} public void setContent(String value){content=value;}
    public LocalDateTime getCreatedAt(){return createdAt;}
}
