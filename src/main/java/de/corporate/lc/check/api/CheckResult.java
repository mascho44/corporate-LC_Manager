package de.corporate.lc.check.api;

public record CheckResult(Severity severity, String code, String message,
                          String lcCondition, String documentName, String documentEvidence,
                          String reviewDecision,String reviewComment,String reviewedBy,java.time.LocalDateTime reviewedAt,
                          Severity automaticSeverity) {
    public CheckResult(Severity severity,String code,String message,String condition,String document,String evidence,String decision,String comment,String reviewer,java.time.LocalDateTime reviewedAt){this(severity,code,message,condition,document,evidence,decision,comment,reviewer,reviewedAt,severity);}
    public CheckResult(Severity severity,String code,String message){this(severity,code,message,null,null,null,null,null,null,null);}
    public CheckResult(Severity severity,String code,String message,String condition,String document,String evidence){this(severity,code,message,condition,document,evidence,null,null,null,null);}
    public enum Severity { OK, WARNING, DISCREPANCY }
}
