package de.ostms.lc.savedview;

import de.ostms.lc.tenant.domain.TenantOwnedEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/** A named combination of the filters and the sort order of the file list; private to its owner or shared with the whole tenant. */
@Entity @Table(name="saved_view")
public class SavedView extends TenantOwnedEntity {
 @Id private UUID id=UUID.randomUUID();
 @Column(name="owner_username",nullable=false,length=100,updatable=false) private String ownerUsername;
 @Column(nullable=false,length=60) private String name;
 @Column(nullable=false) private boolean shared;
 @Column(name="filters_json",nullable=false,columnDefinition="text") private String filtersJson;
 @Column(name="created_at",nullable=false) private LocalDateTime createdAt=LocalDateTime.now();
 protected SavedView(){}
 public SavedView(String ownerUsername,String name,boolean shared,String filtersJson){this.ownerUsername=ownerUsername;this.name=name;this.shared=shared;this.filtersJson=filtersJson;}
 public UUID getId(){return id;} public String getOwnerUsername(){return ownerUsername;} public String getName(){return name;} public boolean isShared(){return shared;}
 public String getFiltersJson(){return filtersJson;} public LocalDateTime getCreatedAt(){return createdAt;}
 public void update(String name,boolean shared,String filtersJson){this.name=name;this.shared=shared;this.filtersJson=filtersJson;}
}
