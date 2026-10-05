package de.corporate.lc.check.service;

import de.corporate.lc.check.api.*;
import de.corporate.lc.document.domain.LcDocument;
import de.corporate.lc.document.repository.LcDocumentRepository;
import de.corporate.lc.lc.domain.LetterOfCredit;
import de.corporate.lc.lc.repository.LetterOfCreditRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class ValidationSimulationService {
    private final LetterOfCreditRepository lcs;
    private final LcDocumentRepository documents;
    private final DocumentCheckService checks;
    public ValidationSimulationService(LetterOfCreditRepository lcs,LcDocumentRepository documents,DocumentCheckService checks){this.lcs=lcs;this.documents=documents;this.checks=checks;}

    @Transactional(readOnly=true)
    public ReviewSummary simulate(UUID id, SimulationRequest request){
        var original=lcs.findById(id).orElseThrow();
        var lc=new LetterOfCredit();
        BeanUtils.copyProperties(original,lc,"id","requiredDocuments","additionalFields");
        lc.setRequiredDocuments(new ArrayList<>(original.getRequiredDocuments()));
        lc.setAdditionalFields(new LinkedHashMap<>(original.getAdditionalFields()));
        if(request.amount()!=null)lc.setAmount(request.amount());
        if(request.expiryDate()!=null)lc.setExpiryDate(request.expiryDate());
        if(request.latestShipmentDate()!=null)lc.setLatestShipmentDate(request.latestShipmentDate());
        var source=documents.findByLetterOfCreditIdOrderByUploadedAtDesc(id);
        Map<UUID,SimulationRequest.DocumentOverride> overrides=new HashMap<>();
        for(var override:request.documents()==null?List.<SimulationRequest.DocumentOverride>of():request.documents()){
            if(overrides.put(override.id(),override)!=null)throw new IllegalArgumentException("Dokument im Testpaket doppelt angegeben.");
            if(source.stream().noneMatch(d->d.getId().equals(override.id())))throw new IllegalArgumentException("Testdokument gehört nicht zur LC-Akte.");
        }
        List<LcDocument> copies=new ArrayList<>();
        for(var document:source){
            var copy=new LcDocument();
            BeanUtils.copyProperties(document,copy,"id","letterOfCredit","content");
            var override=overrides.get(document.getId());
            if(override!=null){copy.setDocumentDate(override.documentDate());copy.setAmount(override.amount());copy.setCurrency(override.currency());}
            copies.add(copy);
        }
        return checks.simulate(id,lc,copies);
    }
}
