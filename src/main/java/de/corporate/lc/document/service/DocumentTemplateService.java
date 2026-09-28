package de.corporate.lc.document.service;

import de.corporate.lc.document.api.DocumentTemplateView;
import de.corporate.lc.document.domain.*;
import de.corporate.lc.document.repository.DocumentTemplateRepository;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;import java.time.LocalDateTime;import java.util.*;

@Service
public class DocumentTemplateService {
    private static final long MAX_SIZE=5*1024*1024;
    private final DocumentTemplateRepository repository;
    public DocumentTemplateService(DocumentTemplateRepository repository){this.repository=repository;}
    @Transactional(readOnly=true) public List<DocumentTemplateView> list(){return repository.findAll().stream().map(DocumentTemplateView::from).sorted(Comparator.comparing(v->v.documentType().name())).toList();}
    @Transactional(readOnly=true) public Optional<byte[]> content(DocumentType type){return repository.findByDocumentType(type).map(DocumentTemplate::getContent);}
    @Transactional public DocumentTemplateView save(DocumentType type,MultipartFile file,String username)throws IOException{
        if(!EnumSet.of(DocumentType.COMMERCIAL_INVOICE,DocumentType.PACKING_LIST,DocumentType.CERTIFICATE_OF_ORIGIN,DocumentType.BENEFICIARY_CERTIFICATE,DocumentType.QUALITY_CERTIFICATE).contains(type))throw new IllegalArgumentException("Für diesen Dokumenttyp werden noch keine Vorlagen unterstützt.");
        String filename=Optional.ofNullable(file.getOriginalFilename()).orElse("");if(file.isEmpty())throw new IllegalArgumentException("Die Vorlagendatei ist leer.");if(file.getSize()>MAX_SIZE)throw new IllegalArgumentException("Die Vorlage ist größer als 5 MB.");if(!filename.toLowerCase(Locale.ROOT).endsWith(".docx"))throw new IllegalArgumentException("Bitte eine DOCX-Datei hochladen.");byte[] content=file.getBytes();
        try(XWPFDocument ignored=new XWPFDocument(new ByteArrayInputStream(content))){ }catch(Exception exception){throw new IllegalArgumentException("Die Datei ist keine gültige Word-DOCX-Vorlage.");}
        DocumentTemplate template=repository.findByDocumentType(type).orElseGet(DocumentTemplate::new);template.setDocumentType(type);template.setOriginalFilename(filename);template.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");template.setFileSize(content.length);template.setContent(content);template.setUploadedBy(username);template.setUploadedAt(LocalDateTime.now());return DocumentTemplateView.from(repository.save(template));
    }
    @Transactional public DocumentTemplate delete(UUID id){DocumentTemplate template=repository.findById(id).orElseThrow();repository.delete(template);return template;}
    @Transactional(readOnly=true) public DocumentTemplate one(UUID id){return repository.findById(id).orElseThrow();}
}
