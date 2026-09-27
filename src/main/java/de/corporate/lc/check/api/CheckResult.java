package de.corporate.lc.check.api;

public record CheckResult(Severity severity, String code, String message,
                          String lcCondition, String documentName, String documentEvidence) {
    public CheckResult(Severity severity,String code,String message){this(severity,code,message,null,null,null);}
    public enum Severity { OK, WARNING, DISCREPANCY }
}
