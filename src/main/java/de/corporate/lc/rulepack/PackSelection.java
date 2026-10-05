package de.corporate.lc.rulepack;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="internal_rule_pack")
public class PackSelection {
 @Id @Column(length=31) public String id;
 public UUID activeVersionId;
 public UUID previousVersionId;
 @Version public long lockVersion;
}
