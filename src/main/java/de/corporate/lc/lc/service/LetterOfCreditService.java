package de.corporate.lc.lc.service;

import de.corporate.lc.lc.api.LetterOfCreditUpdateRequest;
import de.corporate.lc.lc.domain.LetterOfCredit;
import de.corporate.lc.lc.repository.LetterOfCreditRepository;
import de.corporate.lc.swift.Mt700Parser;
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
    private final de.corporate.lc.company.service.CompanyProfileService companies;

    public LetterOfCreditService(LetterOfCreditRepository repo, Mt700Parser parser,de.corporate.lc.company.service.CompanyProfileService companies) {
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

    @Transactional
    public LetterOfCredit update(UUID id, LetterOfCreditUpdateRequest request) {
        LetterOfCredit lc = one(id);
        String reference = request.reference().trim();
        if (repo.existsByReferenceAndIdNot(reference, id)) {
            throw new IllegalArgumentException("LC reference already exists: " + reference);
        }
        lc.setReference(reference);
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
