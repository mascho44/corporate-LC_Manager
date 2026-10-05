package de.corporate.lc.check.api;

public record CheckResult(Severity severity, String code, String message,
                          String lcCondition, String documentName, String documentEvidence,
                          String reviewDecision,String reviewComment,String reviewedBy,java.time.LocalDateTime reviewedAt,
                          Severity automaticSeverity,String inputFingerprint) {
    public CheckResult(Severity severity,String code,String message,String condition,String document,String evidence,String decision,String comment,String reviewer,java.time.LocalDateTime reviewedAt,Severity automaticSeverity){this(severity,code,message,condition,document,evidence,decision,comment,reviewer,reviewedAt,automaticSeverity,null);}
    public CheckResult withInputFingerprint(String fingerprint){return new CheckResult(severity,code,message,lcCondition,documentName,documentEvidence,reviewDecision,reviewComment,reviewedBy,reviewedAt,automaticSeverity,fingerprint);}
    public CheckResult(Severity severity,String code,String message,String condition,String document,String evidence,String decision,String comment,String reviewer,java.time.LocalDateTime reviewedAt){this(severity,code,message,condition,document,evidence,decision,comment,reviewer,reviewedAt,severity);}
    public CheckResult(Severity severity,String code,String message){this(severity,code,message,null,null,null,null,null,null,null);}
    public CheckResult(Severity severity,String code,String message,String condition,String document,String evidence){this(severity,code,message,condition,document,evidence,null,null,null,null);}
    public enum Severity { OK, WARNING, DISCREPANCY }
    @com.fasterxml.jackson.annotation.JsonProperty("reviewFingerprint")
    public String reviewFingerprint(){return de.corporate.lc.check.service.ReviewFingerprint.of(this);}
    @com.fasterxml.jackson.annotation.JsonProperty("rule")
    public RuleDefinition rule(){return de.corporate.lc.check.service.RuleCatalog.forFinding(code);}
    @com.fasterxml.jackson.annotation.JsonProperty("ruleCatalogVersion")
    public String ruleCatalogVersion(){return de.corporate.lc.check.service.RuleCatalog.VERSION;}
}
