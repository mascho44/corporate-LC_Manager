package de.ostms.lc.check.service;

import com.fasterxml.jackson.databind.*;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.document.domain.LcDocument;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.*;

/** Conservative binding to the entire LC and document set, not just a displayed excerpt. */
final class ReviewInputFingerprint {
    private static final ObjectMapper JSON=new ObjectMapper().findAndRegisterModules()
        .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,true);
    private ReviewInputFingerprint(){}
    static String of(LetterOfCredit lc,List<LcDocument> documents){
        try{
            var digest=MessageDigest.getInstance("SHA-256");
            add(digest,JSON.writeValueAsBytes(lc));
            for(var document:documents.stream().sorted(Comparator.comparing((LcDocument d)->Objects.toString(d.getId(),""))
                    .thenComparing(d->Objects.toString(d.getOriginalFilename(),""))).toList()){
                add(digest,JSON.writeValueAsBytes(document));
                add(digest,JSON.writeValueAsBytes(document.getExtractedText()));
                add(digest,document.getContent());
            }
            return HexFormat.of().formatHex(digest.digest());
        }catch(Exception e){throw new IllegalStateException("Prüfgrundlage konnte nicht gebunden werden.",e);}
    }
    /**
     * What a decision on one finding really depends on: the terms of the credit (not workflow fields such as status, assignee, follow-up or notes) and
     * the document the finding concerns; for findings about the whole set only which documents of which type are present. Another document, an assignment
     * or a status change therefore leaves the decision valid.
     */
    static String scoped(LetterOfCredit lc,List<LcDocument> documents,String documentName){
        try{
            var digest=MessageDigest.getInstance("SHA-256");
            var terms=new TreeMap<String,Object>();
            terms.put("reference",lc.getReference());terms.put("applicant",lc.getApplicant());terms.put("beneficiary",lc.getBeneficiary());
            terms.put("issuingBank",lc.getIssuingBank());terms.put("advisingBank",lc.getAdvisingBank());terms.put("amount",lc.getAmount()==null?null:lc.getAmount().stripTrailingZeros().toPlainString());
            terms.put("currency",lc.getCurrency());terms.put("issueDate",String.valueOf(lc.getIssueDate()));terms.put("expiryDate",String.valueOf(lc.getExpiryDate()));
            terms.put("expiryPlace",lc.getExpiryPlace());terms.put("latestShipmentDate",String.valueOf(lc.getLatestShipmentDate()));terms.put("requiredDocuments",lc.getRequiredDocuments());
            terms.put("instrumentType",lc.getInstrumentType());terms.put("rawMessage",lc.getRawMessage());terms.put("partyRole",lc.getPartyRole());
            terms.put("ruleFacts",lc.getRuleFactsJson());terms.put("ruleRequirements",lc.getRuleRequirementsJson());terms.put("rulePacks",lc.getRulePackIds());terms.put("ruleSource",lc.getRuleSourceOverride());
            add(digest,JSON.writeValueAsBytes(terms));
            boolean single=documentName!=null&&!documentName.isBlank();
            for(var document:documents.stream().sorted(Comparator.comparing((LcDocument d)->Objects.toString(d.getOriginalFilename(),""))
                    .thenComparing(d->Objects.toString(d.getId(),""))).toList()){
                if(single){
                    if(!documentName.equals(document.getOriginalFilename()))continue;
                    add(digest,JSON.writeValueAsBytes(document));
                    add(digest,JSON.writeValueAsBytes(document.getExtractedText()));
                    add(digest,JSON.writeValueAsBytes(document.getRuleFactsJson()));
                    add(digest,document.getContent());
                }else add(digest,(Objects.toString(document.getDocumentType(),"")+"|"+Objects.toString(document.getOriginalFilename(),"")).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        }catch(Exception e){throw new IllegalStateException("Prüfgrundlage konnte nicht gebunden werden.",e);}
    }
    private static void add(MessageDigest digest,byte[] bytes){
        digest.update(ByteBuffer.allocate(4).putInt(bytes==null?-1:bytes.length).array());
        if(bytes!=null)digest.update(bytes);
    }
}
