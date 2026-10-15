package de.ostms.lc.sanctions;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/** An approved false positive: this party against this unchanged list entry is not reported again in the tenant. */
@Entity @Table(name="sanctions_clearance")
public class SanctionsClearance extends de.ostms.lc.tenant.domain.TenantOwnedEntity {
    @Id private UUID id=UUID.randomUUID();
    @Column(name="party_key",nullable=false,length=500) private String partyKey;
    @Column(name="party_value",nullable=false,length=500) private String partyValue;
    @Column(nullable=false,length=20) private String source;
    @Column(nullable=false,length=100) private String reference;
    @Column(name="entry_hash",nullable=false,length=64) private String entryHash;
    @Column(name="listed_name",nullable=false,length=500) private String listedName;
    @Column(name="lc_id") private UUID lcId;
    @Column(name="cleared_by",nullable=false,length=100) private String clearedBy;
    @Column(name="approved_by",nullable=false,length=100) private String approvedBy;
    @Column(name="cleared_at",nullable=false) private LocalDateTime clearedAt=LocalDateTime.now();
    public UUID getId(){return id;}
    public String getPartyKey(){return partyKey;} public void setPartyKey(String v){partyKey=v;}
    public String getPartyValue(){return partyValue;} public void setPartyValue(String v){partyValue=v;}
    public String getSource(){return source;} public void setSource(String v){source=v;}
    public String getReference(){return reference;} public void setReference(String v){reference=v;}
    public String getEntryHash(){return entryHash;} public void setEntryHash(String v){entryHash=v;}
    public String getListedName(){return listedName;} public void setListedName(String v){listedName=v;}
    public UUID getLcId(){return lcId;} public void setLcId(UUID v){lcId=v;}
    public String getClearedBy(){return clearedBy;} public void setClearedBy(String v){clearedBy=v;}
    public String getApprovedBy(){return approvedBy;} public void setApprovedBy(String v){approvedBy=v;}
    public LocalDateTime getClearedAt(){return clearedAt;}
}
