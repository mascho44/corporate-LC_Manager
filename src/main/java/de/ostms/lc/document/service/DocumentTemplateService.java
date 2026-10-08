package de.ostms.lc.document.service;

import de.ostms.lc.document.api.DocumentTemplateView;
import de.ostms.lc.document.domain.*;
import de.ostms.lc.document.repository.DocumentTemplateRepository;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;import java.time.LocalDateTime;import java.util.*;

@Service
public class DocumentTemplateService {
    private static final long MAX_SIZE=5*1024*1024;
    private final DocumentTemplateRepository repository;
    private final de.ostms.lc.company.service.CompanyProfileService companies;
    public DocumentTemplateService(DocumentTemplateRepository repository,de.ostms.lc.company.service.CompanyProfileService companies){this.repository=repository;this.companies=companies;}
    @Transactional(readOnly=true) public List<DocumentTemplateView> list(){return repository.findAll().stream().map(DocumentTemplateView::from).sorted(Comparator.comparing(v->v.documentType().name())).toList();}
    private Optional<DocumentTemplate> resolve(DocumentType type,Integer companyId,String legacyName){
        if(companyId!=null)companies.profile(companyId);
        var specific=companyId==null?repository.findByDocumentTypeAndCompanyIdIsNullAndCompanyNameIgnoreCase(type,company(legacyName)):repository.findByDocumentTypeAndCompanyId(type,companyId);
        return specific.or(()->repository.findByDocumentTypeAndCompanyIdIsNullAndCompanyNameIgnoreCase(type,"*"));
    }
    @Transactional(readOnly=true) public Optional<byte[]> content(DocumentType type,String companyName){return resolve(type,null,companyName).map(DocumentTemplate::getContent);}
    @Transactional(readOnly=true) public Optional<byte[]> content(DocumentType type,Integer companyId,String legacyName){return resolve(type,companyId,legacyName).map(DocumentTemplate::getContent);}
    public record Selection(String companyName,String templateName,String source){}
    @Transactional(readOnly=true) public Selection selection(DocumentType type,Integer companyId,String legacyName){
        var profile=companies.profile(companyId);
        var selected=resolve(type,companyId,legacyName);
        return new Selection(profile.getLegalName()==null?"Standardfirma":profile.getLegalName(),
            selected.map(DocumentTemplate::getOriginalFilename).orElse("Eingebaute Word-Vorlage"),
            selected.map(t->t.getCompanyId()!=null?"Firmenvorlage":"*".equals(t.getCompanyName())?"Allgemeine Vorlage":"Namensvorlage").orElse("Eingebaute Vorlage"));
    }
    @Transactional public DocumentTemplateView save(DocumentType type,String companyName,Integer companyId,MultipartFile file,String username)throws IOException{
        if(!EnumSet.of(DocumentType.COMMERCIAL_INVOICE,DocumentType.PACKING_LIST,DocumentType.CERTIFICATE_OF_ORIGIN,DocumentType.BENEFICIARY_CERTIFICATE,DocumentType.QUALITY_CERTIFICATE).contains(type))throw new IllegalArgumentException("Für diesen Dokumenttyp werden noch keine Vorlagen unterstützt.");
        String filename=Optional.ofNullable(file.getOriginalFilename()).orElse("");if(file.isEmpty())throw new IllegalArgumentException("Die Vorlagendatei ist leer.");if(file.getSize()>MAX_SIZE)throw new IllegalArgumentException("Die Vorlage ist größer als 5 MB.");if(!filename.toLowerCase(Locale.ROOT).endsWith(".docx"))throw new IllegalArgumentException("Bitte eine DOCX-Datei hochladen.");byte[] content=file.getBytes();
        try(XWPFDocument ignored=new XWPFDocument(new ByteArrayInputStream(content))){ }catch(Exception exception){throw new IllegalArgumentException("Die Datei ist keine gültige Word-DOCX-Vorlage.");}
        String company=companyId==null?company(companyName):company(companies.profile(companyId).getLegalName());DocumentTemplate template=(companyId==null?repository.findByDocumentTypeAndCompanyIdIsNullAndCompanyNameIgnoreCase(type,company):repository.findByDocumentTypeAndCompanyId(type,companyId)).orElseGet(DocumentTemplate::new);template.setCompanyId(companyId);template.setDocumentType(type);template.setCompanyName(company);template.setOriginalFilename(filename);template.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");template.setFileSize(content.length);template.setContent(content);template.setUploadedBy(username);template.setUploadedAt(LocalDateTime.now());return DocumentTemplateView.from(repository.save(template));
    }
    @Transactional public DocumentTemplate delete(UUID id){DocumentTemplate template=repository.findById(id).orElseThrow();repository.delete(template);return template;}
    @Transactional(readOnly=true) public DocumentTemplate one(UUID id){return repository.findById(id).orElseThrow();}
    private String company(String value){if(value==null||value.isBlank())return"*";String result=value.trim();if(result.length()>255)throw new IllegalArgumentException("Der Firmenname ist zu lang.");return result;}
}
