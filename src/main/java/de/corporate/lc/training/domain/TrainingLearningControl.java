package de.corporate.lc.training.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="training_learning_control")
@IdClass(TrainingLearningControl.Key.class)
public class TrainingLearningControl {
    @Id @Column(nullable=false,updatable=false) @com.fasterxml.jackson.annotation.JsonIgnore
    private java.util.UUID tenantId=de.corporate.lc.tenant.domain.TenantContext.currentId();
    public java.util.UUID getTenantId(){return tenantId;}
    @PrePersist @PreUpdate @PreRemove private void validateTenant(){de.corporate.lc.tenant.domain.TenantContext.require(tenantId);}
    public static class Key implements java.io.Serializable {
        public java.util.UUID tenantId;
        public String ruleId;
        public Key(){}
        public Key(java.util.UUID tenantId,String ruleId){this.tenantId=tenantId;this.ruleId=ruleId;}
        @Override public boolean equals(Object other){return other instanceof Key key&&java.util.Objects.equals(tenantId,key.tenantId)&&java.util.Objects.equals(ruleId,key.ruleId);}
        @Override public int hashCode(){return java.util.Objects.hash(tenantId,ruleId);}
    }
    @Id @Column(length=64) private String ruleId;
    private boolean active=true;
    @Column(length=100) private String updatedBy;
    private LocalDateTime updatedAt;
    public String getRuleId(){return ruleId;} public void setRuleId(String value){ruleId=value;}
    public boolean isActive(){return active;} public void setActive(boolean value){active=value;}
    public String getUpdatedBy(){return updatedBy;} public void setUpdatedBy(String value){updatedBy=value;}
    public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime value){updatedAt=value;}
}
