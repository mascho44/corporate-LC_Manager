package de.ostms.lc.document.api;
import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.document.service.DocumentTemplateService;
import de.ostms.lc.lc.service.LetterOfCreditService;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
public class DocumentTemplateSelectionController {
    private final DocumentTemplateService templates;
    private final LetterOfCreditService lcs;
    public DocumentTemplateSelectionController(DocumentTemplateService templates,LetterOfCreditService lcs){this.templates=templates;this.lcs=lcs;}
    @GetMapping("/api/lcs/{lcId}/document-template-selection")
    public DocumentTemplateService.Selection selection(@PathVariable UUID lcId,@RequestParam DocumentType type){
        var lc=lcs.one(lcId);
        return templates.selection(type,lc.getCompanyId(),lc.getTemplateCompany()==null||lc.getTemplateCompany().isBlank()?lc.getBeneficiary():lc.getTemplateCompany());
    }
}
