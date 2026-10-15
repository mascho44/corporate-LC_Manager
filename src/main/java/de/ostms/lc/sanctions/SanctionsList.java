package de.ostms.lc.sanctions;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/** One downloaded version of a public sanctions list (gzip-compressed original file). Global, not tenant-owned. */
@Entity @Table(name="sanctions_list")
public class SanctionsList {
    @Id private UUID id=UUID.randomUUID();
    @Column(nullable=false,length=20) private String source;
    @Column(name="fetched_at",nullable=false) private LocalDateTime fetchedAt=LocalDateTime.now();
    @Column(nullable=false,length=64) private String sha256;
    @Column(name="entry_count",nullable=false) private int entryCount;
    @Column(length=60) private String published;
    @Basic(fetch=FetchType.LAZY) @Column(nullable=false,columnDefinition="bytea") @com.fasterxml.jackson.annotation.JsonIgnore private byte[] content;
    public UUID getId(){return id;}
    public String getSource(){return source;} public void setSource(String v){source=v;}
    public LocalDateTime getFetchedAt(){return fetchedAt;} public void setFetchedAt(LocalDateTime v){fetchedAt=v;}
    public String getSha256(){return sha256;} public void setSha256(String v){sha256=v;}
    public int getEntryCount(){return entryCount;} public void setEntryCount(int v){entryCount=v;}
    public String getPublished(){return published;} public void setPublished(String v){published=v;}
    public byte[] getContent(){return content;} public void setContent(byte[] v){content=v;}
}
