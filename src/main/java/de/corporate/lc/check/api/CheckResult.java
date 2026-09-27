package de.corporate.lc.check.api;

public record CheckResult(Severity severity, String code, String message,
                          String lcCondition, String documentName, String documentEvidence,
                          String reviewDecision,String reviewedBy,java.time.LocalDateTime reviewedAt) {
    public CheckResult(Severity severity,String code,String message){this(severity,code,message,null,null,null,null,null,null);}
    public CheckResult(Severity severity,String code,String message,String condition,String document,String evidence){this(severity,code,message,condition,document,evidence,null,null,null);}
    public enum Severity { OK, WARNING, DISCREPANCY }
}
