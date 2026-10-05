package de.corporate.lc.document.api;
import java.util.UUID;
public record LcAssignmentCandidate(UUID lcId,String reference,String status,String reason,String evidence) { }
