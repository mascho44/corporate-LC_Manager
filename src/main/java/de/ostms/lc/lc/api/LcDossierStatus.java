package de.ostms.lc.lc.api;

import de.ostms.lc.check.api.CheckResult;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/** Review state of an LC file for the list; open and blocking count findings still to be decided (same rule as the document-wise review in the browser). */
public record LcDossierStatus(UUID lcId,String status,long documents,long discrepancies,long warnings,long passed,long open,long blocking) {
    private static final Pattern BLOCKING=Pattern.compile("SANCTION|EMBARGO|AMOUNT|EXPIR|PRESENTATION_PERIOD|PRESENTATION_BEFORE|SHIPMENT_DATE|LATEST_SHIPMENT|MISSING_DOCUMENT|SIGNATURE");

    public LcDossierStatus(UUID lcId,String status,long documents,long discrepancies,long warnings,long passed){this(lcId,status,documents,discrepancies,warnings,passed,0,0);}

    static boolean isOpen(CheckResult r){return r.reviewDecision()==null&&r.severity()!=CheckResult.Severity.OK&&!"REVIEW_STALE".equals(r.code());}
    static boolean isBlocking(CheckResult r){return isOpen(r)&&r.code()!=null&&BLOCKING.matcher(r.code()).find();}

    public static LcDossierStatus of(UUID lcId,String status,long documents,long discrepancies,long warnings,long passed,List<CheckResult> results){
        List<CheckResult> all=results==null?List.of():results;
        return new LcDossierStatus(lcId,status,documents,discrepancies,warnings,passed,all.stream().filter(LcDossierStatus::isOpen).count(),all.stream().filter(LcDossierStatus::isBlocking).count());
    }
}
