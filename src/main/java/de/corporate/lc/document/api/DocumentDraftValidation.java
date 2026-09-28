package de.corporate.lc.document.api;

import java.util.List;

public record DocumentDraftValidation(String status, List<String> blockers, List<String> warnings) {}
