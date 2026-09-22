package de.corporate.lc.check.api;

public record CheckResult(Severity severity, String code, String message) {
    public enum Severity { OK, WARNING, DISCREPANCY }
}
