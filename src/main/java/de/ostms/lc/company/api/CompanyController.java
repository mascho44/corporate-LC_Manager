package de.ostms.lc.company.api;

import de.ostms.lc.company.service.CompanyProfileService;
import de.ostms.lc.audit.service.AuditService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.*;

@RestController @RequestMapping("/api/companies")
public class CompanyController {
    private final CompanyProfileService service;
    private final AuditService audit;
    public CompanyController(CompanyProfileService service,AuditService audit){this.service=service;this.audit=audit;}
    public record Choice(Integer id,String name){}
    @GetMapping("/choices") public List<Choice> choices(){return service.all().stream().map(p->new Choice(p.id(),p.legalName()==null?"Standardfirma":p.legalName())).toList();}
    @GetMapping public List<CompanyProfileView> all(){return service.all();}
    @GetMapping("/{id}") public CompanyProfileView one(@PathVariable Integer id){return CompanyProfileView.from(service.profile(id));}
    @PostMapping public CompanyProfileView create(@Valid @RequestBody CompanyProfileRequest r,Authentication auth){var result=service.create(r,auth.getName());audit.record(auth,"COMPANY_PROFILE_CREATED","COMPANY_PROFILE",result.id(),result.legalName());return result;}
    @PutMapping("/{id}") public CompanyProfileView update(@PathVariable Integer id,@Valid @RequestBody CompanyProfileRequest r,Authentication auth){var before=one(id);var result=service.update(id,r,auth.getName());audit.recordChange(auth,"COMPANY_PROFILE_UPDATED","COMPANY_PROFILE",id,"Firmenstammdaten geändert",before.toString(),result.toString());return result;}
    @PostMapping("/{id}/logo") public CompanyProfileView logo(@PathVariable Integer id,@RequestPart("file")MultipartFile file,Authentication auth)throws IOException{var result=service.logo(id,file,auth.getName());audit.record(auth,"COMPANY_LOGO_UPDATED","COMPANY_PROFILE",id,file.getOriginalFilename());return result;}
    @GetMapping("/{id}/logo") public ResponseEntity<byte[]> logo(@PathVariable Integer id){var p=service.profile(id);return p.getLogo()==null?ResponseEntity.notFound().build():ResponseEntity.ok().contentType(MediaType.parseMediaType(p.getLogoContentType())).body(p.getLogo());}
    @DeleteMapping("/{id}/logo") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteLogo(@PathVariable Integer id,Authentication auth){service.deleteLogo(id,auth.getName());audit.record(auth,"COMPANY_LOGO_DELETED","COMPANY_PROFILE",id,"Firmenlogo gelöscht");}
}
