package de.ostms.lc.check.service;

import de.ostms.lc.check.domain.LcDraft;
import de.ostms.lc.check.repository.LcDraftRepository;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import de.ostms.lc.swift.Mt700Parser;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LcFlowServiceTest {
 static final String DRAFT = LcDraftReviewServiceTest.draft("261215", "270115", "ALL DOCUMENTS IN ENGLISH");
 final LcDraftRepository drafts = Mockito.mock(LcDraftRepository.class);final LetterOfCreditRepository lcs = Mockito.mock(LetterOfCreditRepository.class);
 final LcFlowService service = new LcFlowService(drafts, lcs, new LcDraftReviewService(new Mt700Parser()));
 final UUID id = UUID.randomUUID();final LocalDate today = LocalDate.of(2026, 10, 28);

 @Test void onlyDifferentFieldsAreListedAndWhitespaceAndCaseDoNotCount() {
  String credit = DRAFT.replace("EUR125000,00", "EUR130000,00").replace("ALL DOCUMENTS IN ENGLISH", "all documents  in english").replace(":44C:261215", ":44C:261220");
  var diffs = LcFlowService.compare(DRAFT, credit);
  assertThat(diffs).extracting(LcFlowService.Difference::tag).containsExactlyInAnyOrder("32B", "44C");
  var amount = diffs.stream().filter(d -> d.tag().equals("32B")).findFirst().get();
  assertThat(amount.draft()).contains("125000").doesNotContain("130000");assertThat(amount.credit()).contains("130000");
 }
 @Test void aFieldOnlyInOneSideIsADifference() {
  var diffs = LcFlowService.compare(DRAFT, DRAFT.replace(":47A:ALL DOCUMENTS IN ENGLISH\n", ""));
  assertThat(diffs).extracting(LcFlowService.Difference::tag).containsExactly("47A");assertThat(diffs.get(0).credit()).isEmpty();
 }
 @Test void theFlowCombinesDraftComparisonAndCheckOfTheCredit() {
  var lc = new LetterOfCredit();lc.setRawMessage(DRAFT.replace("EUR125000,00", "EUR130000,00"));
  when(lcs.findById(id)).thenReturn(Optional.of(lc));
  when(drafts.forLc(id)).thenReturn(Optional.of(new LcDraft(id, "entwurf.txt", DRAFT, "anna")));
  var flow = service.flow(id, today);
  assertThat(flow.draft().filename()).isEqualTo("entwurf.txt");assertThat(flow.differences()).hasSize(1);
  assertThat(flow.creditCheck().discrepancies()).isZero();assertThat(flow.reviewCompleted()).isFalse();
 }
 @Test void withoutADraftThereIsNoComparisonAndAMissingRawMessageIsTolerated() {
  var lc = new LetterOfCredit();when(lcs.findById(id)).thenReturn(Optional.of(lc));when(drafts.forLc(id)).thenReturn(Optional.empty());
  var flow = service.flow(id, today);
  assertThat(flow.draft()).isNull();assertThat(flow.differences()).isNull();assertThat(flow.creditCheck()).isNull();
 }
 @Test void aDraftIsSavedOrReplacedAndMustLookLikeACredit() {
  when(lcs.findById(id)).thenReturn(Optional.of(new LetterOfCredit()));
  when(drafts.forLc(id)).thenReturn(Optional.empty());
  service.saveDraft(id, "a/b.txt", DRAFT, "anna");
  verify(drafts).save(argThat(d -> d.getFilename().equals("a_b.txt") && d.getSavedBy().equals("anna")));
  var existing = new LcDraft(id, "alt.txt", DRAFT, "bernd");when(drafts.forLc(id)).thenReturn(Optional.of(existing));
  service.saveDraft(id, "neu.txt", DRAFT, "anna");
  assertThat(existing.getFilename()).isEqualTo("neu.txt");assertThat(existing.getSavedBy()).isEqualTo("anna");
  assertThatThrownBy(() -> service.saveDraft(id, "x", "kein Akkreditiv", "anna")).hasMessageContaining("MT700");
  assertThatThrownBy(() -> service.saveDraft(id, "x", " ", "anna")).hasMessageContaining("einfügen");
 }
}
