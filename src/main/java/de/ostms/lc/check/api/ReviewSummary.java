package de.ostms.lc.check.api;

import java.util.List;

public record ReviewSummary(String status, long discrepancies, long warnings, long passed, List<CheckResult> results,String mode) {
 public ReviewSummary(String status,long discrepancies,long warnings,long passed,List<CheckResult> results){this(status,discrepancies,warnings,passed,results,"REVIEW");}
 /** A finding decision or a green automatic result is never a final approval. */
 @com.fasterxml.jackson.annotation.JsonProperty("finalReview") public boolean finalReview(){return false;}
 @com.fasterxml.jackson.annotation.JsonProperty("reviewedFindings") public long reviewedFindings(){return results.stream().filter(f->f.reviewDecision()!=null).count();}
}
