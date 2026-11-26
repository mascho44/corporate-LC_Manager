package de.ostms.lc.document.domain;

import de.ostms.lc.tenant.domain.TenantOwnedEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/** A person's decision about a region of a page: stamp, signature, a signed stamp or neither. Position and size are fractions of the page (0 to 1), independent of any resolution. */
@Entity @Table(name="lc_document_mark")
public class DocumentMark extends TenantOwnedEntity {
 /** SIGNED_STAMP: a signature written across a stamp, one region that is both. */
 public enum Kind { STAMP, SIGNATURE, SIGNED_STAMP, NONE }
 @Id private UUID id=UUID.randomUUID();
 @Column(name="document_id",nullable=false) private UUID documentId;
 @Column(nullable=false) private int page;
 @Column(name="box_x",nullable=false) private double x;
 @Column(name="box_y",nullable=false) private double y;
 @Column(name="box_w",nullable=false) private double w;
 @Column(name="box_h",nullable=false) private double h;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private Kind kind;
 @Column(name="marked_by",nullable=false,length=100) private String markedBy;
 @Column(name="marked_at",nullable=false) private LocalDateTime markedAt=LocalDateTime.now();
 public DocumentMark(){}
 public DocumentMark(UUID documentId,int page,double x,double y,double w,double h,Kind kind,String markedBy){
  this.documentId=documentId;this.page=page;this.x=x;this.y=y;this.w=w;this.h=h;this.kind=kind;this.markedBy=markedBy;
 }
 public UUID getId(){return id;} public UUID getDocumentId(){return documentId;} public int getPage(){return page;}
 public double getX(){return x;} public double getY(){return y;} public double getW(){return w;} public double getH(){return h;}
 public Kind getKind(){return kind;} public String getMarkedBy(){return markedBy;} public LocalDateTime getMarkedAt(){return markedAt;}
}
