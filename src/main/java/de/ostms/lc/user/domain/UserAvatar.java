package de.ostms.lc.user.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_avatar")
public class UserAvatar {
    @Id private UUID userId;
    @Column(nullable = false, columnDefinition = "bytea") private byte[] content;
    @Column(nullable = false) private Instant updatedAt;
    public UUID getUserId(){return userId;}
    public void setUserId(UUID value){userId=value;}
    public byte[] getContent(){return content;}
    public void setContent(byte[] value){content=value;}
    public Instant getUpdatedAt(){return updatedAt;}
    public void setUpdatedAt(Instant value){updatedAt=value;}
}
