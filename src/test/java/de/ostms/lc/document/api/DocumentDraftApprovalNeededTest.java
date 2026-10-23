package de.ostms.lc.document.api;

import de.ostms.lc.document.domain.DocumentDraftStatus;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DocumentDraftApprovalNeededTest {
 private static DocumentDraftView view(DocumentDraftStatus status,int approvals,int required){
  return new DocumentDraftView(null,null,null,"RE-2026-1",status,1,"anna",null,null,null,null,null,null,null,null,null,java.util.List.of(),approvals,required,null);
 }
 @Test void aSubmittedDraftNeedsAReview(){
  assertThat(DocumentDraftController.approvalNeeded(DocumentDraftStatus.SUBMITTED,view(DocumentDraftStatus.SUBMITTED,0,1))).contains("RE-2026-1 · Prüfung erforderlich");
 }
 @Test void aReviewedDraftNeedsTheRequiredApprovals(){
  assertThat(DocumentDraftController.approvalNeeded(DocumentDraftStatus.REVIEWED,view(DocumentDraftStatus.REVIEWED,0,2))).contains("RE-2026-1 · Freigabe erforderlich (0 von 2)");
 }
 @Test void aPartialApprovalNeedsFurtherApprovalsAndTheLastOneNeedsNothing(){
  assertThat(DocumentDraftController.approvalNeeded(DocumentDraftStatus.FINAL,view(DocumentDraftStatus.REVIEWED,1,2))).contains("RE-2026-1 · weitere Freigabe erforderlich (1 von 2)");
  assertThat(DocumentDraftController.approvalNeeded(DocumentDraftStatus.FINAL,view(DocumentDraftStatus.FINAL,2,2))).isEmpty();
 }
 @Test void withdrawingNeedsNothing(){
  assertThat(DocumentDraftController.approvalNeeded(DocumentDraftStatus.DRAFT,view(DocumentDraftStatus.DRAFT,0,1))).isEmpty();
 }
}
