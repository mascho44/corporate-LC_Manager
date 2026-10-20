package de.ostms.lc.check.api;

public record CheckResult(Severity severity, String code, String message,
                          String lcCondition, String documentName, String documentEvidence,
                          String reviewDecision,String reviewComment,String reviewedBy,java.time.LocalDateTime reviewedAt,
                          Severity automaticSeverity,String inputFingerprint,
                          @com.fasterxml.jackson.annotation.JsonIgnore RuleDefinition importedRule,String explanation) {
    public CheckResult(Severity severity,String code,String message,String condition,String document,String evidence,String decision,String comment,String reviewer,java.time.LocalDateTime reviewedAt,Severity automaticSeverity,String inputFingerprint,RuleDefinition importedRule){this(severity,code,message,condition,document,evidence,decision,comment,reviewer,reviewedAt,automaticSeverity,inputFingerprint,importedRule,null);}
    public CheckResult withExplanation(String text){return new CheckResult(severity,code,message,lcCondition,documentName,documentEvidence,reviewDecision,reviewComment,reviewedBy,reviewedAt,automaticSeverity,inputFingerprint,importedRule,text);}
    public CheckResult(Severity severity,String code,String message,String condition,String document,String evidence,String decision,String comment,String reviewer,java.time.LocalDateTime reviewedAt,Severity automaticSeverity,String inputFingerprint){this(severity,code,message,condition,document,evidence,decision,comment,reviewer,reviewedAt,automaticSeverity,inputFingerprint,null);}
    public CheckResult(Severity severity,String code,String message,String condition,String document,String evidence,String decision,String comment,String reviewer,java.time.LocalDateTime reviewedAt,Severity automaticSeverity){this(severity,code,message,condition,document,evidence,decision,comment,reviewer,reviewedAt,automaticSeverity,null);}
    public CheckResult withInputFingerprint(String fingerprint){return new CheckResult(severity,code,message,lcCondition,documentName,documentEvidence,reviewDecision,reviewComment,reviewedBy,reviewedAt,automaticSeverity,fingerprint,importedRule,explanation);}
    public CheckResult withRule(RuleDefinition rule){return new CheckResult(severity,code,message,lcCondition,documentName,documentEvidence,reviewDecision,reviewComment,reviewedBy,reviewedAt,automaticSeverity,inputFingerprint,rule,explanation);}
    public CheckResult(Severity severity,String code,String message,String condition,String document,String evidence,String decision,String comment,String reviewer,java.time.LocalDateTime reviewedAt){this(severity,code,message,condition,document,evidence,decision,comment,reviewer,reviewedAt,severity);}
    public CheckResult(Severity severity,String code,String message){this(severity,code,message,null,null,null,null,null,null,null);}
    public CheckResult(Severity severity,String code,String message,String condition,String document,String evidence){this(severity,code,message,condition,document,evidence,null,null,null,null);}
    public enum Severity { OK, WARNING, DISCREPANCY }
    @com.fasterxml.jackson.annotation.JsonProperty("reviewFingerprint")
    public String reviewFingerprint(){return de.ostms.lc.check.service.ReviewFingerprint.of(this);}
    @com.fasterxml.jackson.annotation.JsonProperty("rule")
    public RuleDefinition rule(){return importedRule!=null?importedRule:de.ostms.lc.check.service.RuleCatalog.forFinding(code);}
    /** What to do about an open finding, see {@link de.ostms.lc.check.service.CorrectionAdvice}. */
    @com.fasterxml.jackson.annotation.JsonProperty("advice")
    public String advice(){return de.ostms.lc.check.service.CorrectionAdvice.forFinding(code,severity);}
    /** English wording of the finding and of its advice, for the English interface (null when there is none). */
    @com.fasterxml.jackson.annotation.JsonProperty("messageEn")
    public String messageEn(){return de.ostms.lc.check.service.FindingText.english(code,severity);}
    @com.fasterxml.jackson.annotation.JsonProperty("adviceEn")
    public String adviceEn(){return de.ostms.lc.check.service.CorrectionAdvice.forFindingEnglish(code,severity);}
    @com.fasterxml.jackson.annotation.JsonProperty("ruleCatalogVersion")
    public String ruleCatalogVersion(){return de.ostms.lc.check.service.RuleCatalog.VERSION;}
}
