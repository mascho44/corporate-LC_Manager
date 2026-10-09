package de.ostms.lc.lc.service;

import de.ostms.lc.lc.api.LetterOfCreditUpdateRequest;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import de.ostms.lc.swift.Mt700Parser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class LetterOfCreditService {
    private final LetterOfCreditRepository repo;
    private final Mt700Parser parser;
    private final de.ostms.lc.company.service.CompanyProfileService companies;

    public LetterOfCreditService(LetterOfCreditRepository repo, Mt700Parser parser,de.ostms.lc.company.service.CompanyProfileService companies) {
        this.companies=companies;
        this.repo = repo;
        this.parser = parser;
    }

    public LetterOfCredit importMt700(String raw) {
        LetterOfCredit lc = parser.parse(raw);
        if (repo.existsByReference(lc.getReference())) {
            throw new IllegalArgumentException("LC reference already exists: " + lc.getReference());
        }
        return repo.save(lc);
    }

    public List<LetterOfCredit> all() {
        return repo.findAll();
    }

    public LetterOfCredit one(UUID id) {
        return repo.findById(id).orElseThrow(() -> new NoSuchElementException("Akkreditiv nicht gefunden"));
    }

    public record RequirementReparse(boolean changed,String reason,List<String> current,List<String> proposed){}

    /** Rebuilds the document conditions from the stored MT700 only while they still equal the old line-by-line split, so manual or amended changes are never overwritten. */
    @Transactional(readOnly=true)
    public RequirementReparse reparsePreview(UUID id){
        LetterOfCredit lc=one(id);
        var field=de.ostms.lc.swift.Mt700Parser.requiredDocumentsField(lc.getRawMessage());
        List<String> current=List.copyOf(lc.getRequiredDocuments());
        if(field.isEmpty())return new RequirementReparse(false,"Keine SWIFT-Nachricht mit Feld 46A gespeichert.",current,current);
        List<String> proposed=de.ostms.lc.swift.Mt700Parser.splitConditions(field.get());
        if(proposed.equals(current))return new RequirementReparse(false,"Die Bedingungen entsprechen bereits der aktuellen Aufteilung.",current,proposed);
        if(!current.equals(de.ostms.lc.swift.Mt700Parser.legacyConditions(field.get())))
            return new RequirementReparse(false,"Die Bedingungen wurden nach dem Import geändert und werden nicht automatisch ersetzt.",current,proposed);
        return new RequirementReparse(true,"Umgebrochene Zeilen werden zur jeweiligen Bedingung zusammengeführt.",current,proposed);
    }

    @Transactional
    public RequirementReparse reparse(UUID id){
        var preview=reparsePreview(id);
        if(!preview.changed())return preview;
        LetterOfCredit lc=one(id);
        lc.getRequiredDocuments().clear();lc.getRequiredDocuments().addAll(preview.proposed());
        repo.save(lc);
        return preview;
    }

    @Transactional
    public LetterOfCredit update(UUID id, LetterOfCreditUpdateRequest request) {
        LetterOfCredit lc = one(id);
        String reference = request.reference().trim();
        if (repo.existsByReferenceAndIdNot(reference, id)) {
            throw new IllegalArgumentException("LC reference already exists: " + reference);
        }
        lc.setReference(reference);
        lc.setOwnBankReference(clean(request.ownBankReference()));
        lc.setForeignBankReference(clean(request.foreignBankReference()));
        if(request.companyId()!=null)companies.profile(request.companyId());
        lc.setCompanyId(request.companyId());
        lc.setTemplateCompany(clean(request.templateCompany()));
        lc.setApplicant(clean(request.applicant()));
        lc.setBeneficiary(clean(request.beneficiary()));
        lc.setIssuingBank(clean(request.issuingBank()));
        lc.setAdvisingBank(clean(request.advisingBank()));
        lc.setAmount(request.amount());
        lc.setCurrency(upper(request.currency()));
        lc.setIssueDate(request.issueDate());
        lc.setExpiryDate(request.expiryDate());
        lc.setExpiryPlace(clean(request.expiryPlace()));
        lc.setLatestShipmentDate(request.latestShipmentDate());
        lc.setAssignedTo(clean(request.assignedTo()));
        lc.setFollowUpDate(request.followUpDate());
        lc.setStatus(request.status());
        if (request.requiredDocuments() != null)
            lc.setRequiredDocuments(request.requiredDocuments().stream().map(this::clean).filter(java.util.Objects::nonNull).toList());
        if (request.additionalFields() != null) {
            LinkedHashMap<String,String> additional = new LinkedHashMap<>();
            request.additionalFields().forEach((key,value) -> {
                String name = clean(key), content = clean(value);
                if (name != null && content != null) additional.put(name, content);
            });
            lc.setAdditionalFields(additional);
        }
        return lc;
    }

    @Transactional
    public void delete(UUID id) {
        LetterOfCredit lc = one(id);
        repo.delete(lc);
    }

    @Transactional
    public LetterOfCredit completeFollowUp(UUID id) {
        LetterOfCredit lc = one(id);
        lc.setFollowUpDate(null);
        return lc;
    }

    @Transactional
    public LetterOfCredit assignTo(UUID id, String username) {
        LetterOfCredit lc = one(id);
        lc.setAssignedTo(clean(username));
        return lc;
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String upper(String value) {
        String cleaned = clean(value);
        return cleaned == null ? null : cleaned.toUpperCase(Locale.ROOT);
    }
}
