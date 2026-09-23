package de.corporate.lc.training.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="training_learning_control")
public class TrainingLearningControl {
    @Id @Column(length=64) private String ruleId;
    private boolean active=true;
    @Column(length=100) private String updatedBy;
    private LocalDateTime updatedAt;
    public String getRuleId(){return ruleId;} public void setRuleId(String value){ruleId=value;}
    public boolean isActive(){return active;} public void setActive(boolean value){active=value;}
    public String getUpdatedBy(){return updatedBy;} public void setUpdatedBy(String value){updatedBy=value;}
    public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime value){updatedAt=value;}
}
