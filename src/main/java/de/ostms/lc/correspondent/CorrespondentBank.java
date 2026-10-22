package de.ostms.lc.correspondent;

import de.ostms.lc.tenant.domain.TenantOwnedEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/** A correspondent bank of a tenant: BIC, country, how to reach it (channels) and where messages are routed. */
@Entity @Table(name="correspondent_bank")
public class CorrespondentBank extends TenantOwnedEntity {
 @Id private UUID id=UUID.randomUUID();
 @Column(nullable=false,length=11) private String bic;
 @Column(nullable=false,length=140) private String name;
 @Column(length=2) private String country;
 @Column(length=80) private String city;
 @Column(nullable=false,length=60) private String channels="";
 @Column(name="preferred_channel",length=10) private String preferredChannel;
 @Column(name="routing_bic",length=11) private String routingBic;
 @Column(name="contact_email",length=200) private String contactEmail;
 @Column(name="contact_phone",length=50) private String contactPhone;
 @Column(length=1000) private String notes;
 @Column(nullable=false) private boolean active=true;
 @Column(name="changed_by",length=100) private String changedBy;
 @Column(name="changed_at",nullable=false) private LocalDateTime changedAt=LocalDateTime.now();
 public UUID getId(){return id;} public String getBic(){return bic;} public String getName(){return name;} public String getCountry(){return country;} public String getCity(){return city;}
 public java.util.List<String> channels(){return channels==null||channels.isBlank()?java.util.List.of():java.util.Arrays.asList(channels.split(","));}
 public String getPreferredChannel(){return preferredChannel;} public String getRoutingBic(){return routingBic;} public String getContactEmail(){return contactEmail;} public String getContactPhone(){return contactPhone;}
 public String getNotes(){return notes;} public boolean isActive(){return active;} public String getChangedBy(){return changedBy;} public LocalDateTime getChangedAt(){return changedAt;}
 public void set(String bic,String name,String country,String city,java.util.List<String> channels,String preferredChannel,String routingBic,String contactEmail,String contactPhone,String notes,boolean active,String user){
  this.bic=bic;this.name=name;this.country=country;this.city=city;this.channels=String.join(",",channels);this.preferredChannel=preferredChannel;this.routingBic=routingBic;this.contactEmail=contactEmail;this.contactPhone=contactPhone;this.notes=notes;this.active=active;this.changedBy=user;this.changedAt=LocalDateTime.now();
 }
}
