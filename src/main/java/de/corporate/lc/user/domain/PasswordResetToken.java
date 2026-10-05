package de.corporate.lc.user.domain;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="password_reset_token")
public class PasswordResetToken {
    @Id private String tokenHash;
    @Column(nullable=false) private UUID userId;
    @Column(nullable=false) private String credentialStamp;
    @Column(nullable=false) private String email;
    @Column(nullable=false) private Instant expiresAt;
    public PasswordResetToken() { }
    public PasswordResetToken(String hash,UUID user,String stamp,String email,Instant expires){this.tokenHash=hash;this.userId=user;this.credentialStamp=stamp;this.email=email;this.expiresAt=expires;}
    public String getTokenHash(){return tokenHash;}
    public UUID getUserId(){return userId;}
    public String getCredentialStamp(){return credentialStamp;}
    public String getEmail(){return email;}
    public Instant getExpiresAt(){return expiresAt;}
}
