package de.ostms.lc.document.api;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.document.service.LcDossierExportService;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/lcs/{lcId}/dossier")
public class LcDossierController {
    private final LcDossierExportService dossiers;private final AuditService audit;
    public LcDossierController(LcDossierExportService dossiers,AuditService audit){this.dossiers=dossiers;this.audit=audit;}
    @GetMapping(produces="application/zip")
    public ResponseEntity<byte[]> download(@PathVariable UUID lcId,Authentication authentication)throws IOException{
        var dossier=dossiers.create(lcId);audit.record(authentication,"LC_DOSSIER_EXPORTED","LETTER_OF_CREDIT",lcId,dossier.filename());
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(dossier.filename()).build().toString())
                .contentLength(dossier.content().length).body(dossier.content());
    }
}
