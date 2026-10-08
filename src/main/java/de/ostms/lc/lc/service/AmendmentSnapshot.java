package de.ostms.lc.lc.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.ostms.lc.lc.domain.LetterOfCredit;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable serialized business state, independent of subsequent LC edits. */
final class AmendmentSnapshot {
    private static final ObjectMapper JSON = new ObjectMapper();
    static String capture(LetterOfCredit lc) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("reference", lc.getReference());
        state.put("ownBankReference",lc.getOwnBankReference());
        state.put("foreignBankReference",lc.getForeignBankReference());
        state.put("applicant", lc.getApplicant());
        state.put("beneficiary", lc.getBeneficiary());
        state.put("issuingBank", lc.getIssuingBank());
        state.put("advisingBank", lc.getAdvisingBank());
        state.put("amount", lc.getAmount() == null ? null : lc.getAmount().toPlainString());
        state.put("currency", lc.getCurrency());
        state.put("issueDate", text(lc.getIssueDate()));
        state.put("expiryDate", text(lc.getExpiryDate()));
        state.put("expiryPlace", lc.getExpiryPlace());
        state.put("latestShipmentDate", text(lc.getLatestShipmentDate()));
        state.put("requiredDocuments", lc.getRequiredDocuments());
        state.put("additionalFields", lc.getAdditionalFields());
        state.put("conditions", lc.getConditions());
        state.put("rawMessage", lc.getRawMessage());
        try { return JSON.writeValueAsString(state); }
        catch (JsonProcessingException e) { throw new IllegalStateException("LC-Fassung konnte nicht gesichert werden", e); }
    }
    private static String text(Object value) { return value == null ? null : value.toString(); }
}
