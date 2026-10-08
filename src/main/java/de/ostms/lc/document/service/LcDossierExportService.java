package de.ostms.lc.document.service;

import de.ostms.lc.check.service.DocumentCheckReportService;
import de.ostms.lc.document.domain.LcDocument;
import de.ostms.lc.document.repository.LcDocumentRepository;
import de.ostms.lc.lc.domain.Amendment;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.repository.AmendmentRepository;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class LcDossierExportService {
    private final LetterOfCreditRepository lcs;
    private final LcDocumentRepository documents;
    private final AmendmentRepository amendments;
    private final DocumentCheckReportService reports;
    private final CasePackageContents contents;

    public LcDossierExportService(LetterOfCreditRepository lcs,LcDocumentRepository documents,
                                  AmendmentRepository amendments,DocumentCheckReportService reports,CasePackageContents contents){
        this.lcs=lcs;this.documents=documents;this.amendments=amendments;this.reports=reports;this.contents=contents;
    }

    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Dossier create(UUID lcId)throws IOException{
        LetterOfCredit lc=lcs.findById(lcId).orElseThrow();
        var docs=documents.findByLetterOfCreditIdOrderByUploadedAtDesc(lcId);
        var changes=amendments.findByLetterOfCreditIdOrderByImportedAtDesc(lcId);
        var report=reports.create(lcId);
        try(ByteArrayOutputStream output=new ByteArrayOutputStream();ZipOutputStream zip=new ZipOutputStream(output,StandardCharsets.UTF_8)){
            Set<String> names=new HashSet<>();
            java.util.List<java.util.Map<String,Object>> manifest=new java.util.ArrayList<>();
            add(zip,names,manifest,"00-Aktenuebersicht.txt",overview(lc,docs.size(),changes.size()).getBytes(StandardCharsets.UTF_8));
            add(zip,names,manifest,"01-SWIFT-MT700.txt",value(lc.getRawMessage()).getBytes(StandardCharsets.UTF_8));
            add(zip,names,manifest,"02-"+report.filename(),report.content());
            for(Amendment amendment:changes)add(zip,names,manifest,"Amendments/MT707-"+safe(value(amendment.getAmendmentNumber(),"ohne-Nummer"))+".txt",value(amendment.getRawMessage()).getBytes(StandardCharsets.UTF_8));
            for(LcDocument document:docs)add(zip,names,manifest,"Dokumente/"+safe(value(document.getOriginalFilename(),"Dokument")),document.getContent());
            for(var entry:contents.create(lcId).entrySet())add(zip,names,manifest,entry.getKey(),entry.getValue());
            byte[] metadata=new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(java.util.Map.of("formatVersion","1.0","lcId",lcId.toString(),"generatedAt",java.time.Instant.now().toString(),"algorithm","SHA-256","files",manifest));
            add(zip,names,null,"manifest.json",metadata);
            zip.finish();
            return new Dossier("LC-Akte-"+safe(lc.getReference())+".zip",output.toByteArray());
        }
    }

    private String overview(LetterOfCredit lc,int documents,int amendments){
        StringBuilder text=new StringBuilder("CORPORATE LC MANAGER - DIGITALE LC-AKTE\n\n");
        line(text,"Erstellt",ZonedDateTime.now());line(text,"LC-Referenz",lc.getReference());line(text,"Status",lc.getStatus());
        line(text,"Applicant",lc.getApplicant());line(text,"Beneficiary",lc.getBeneficiary());line(text,"Issuing Bank",lc.getIssuingBank());line(text,"Advising Bank",lc.getAdvisingBank());
        line(text,"Betrag",value(lc.getCurrency())+" "+value(lc.getAmount()));line(text,"Ausstellungsdatum",lc.getIssueDate());line(text,"Ablaufdatum",lc.getExpiryDate());line(text,"Ablaufort",lc.getExpiryPlace());line(text,"Spaetester Versand",lc.getLatestShipmentDate());
        line(text,"Dokumente und Anlagen",documents);line(text,"Amendments",amendments);text.append("\nERFORDERLICHE DOKUMENTE\n");
        if(lc.getRequiredDocuments().isEmpty())text.append("- keine erfasst\n");else lc.getRequiredDocuments().forEach(item->text.append("- ").append(item).append('\n'));
        if(!lc.getAdditionalFields().isEmpty()){text.append("\nWEITERE ANGABEN\n");lc.getAdditionalFields().forEach((key,val)->line(text,key,val));}
        return text.toString();
    }
    private void line(StringBuilder text,String label,Object value){text.append(label).append(": ").append(value(value)).append('\n');}
    private void add(ZipOutputStream zip,Set<String> names,java.util.List<java.util.Map<String,Object>> manifest,String requested,byte[] content)throws IOException{String name=unique(names,requested);byte[] bytes=content==null?new byte[0]:content;zip.putNextEntry(new ZipEntry(name));zip.write(bytes);zip.closeEntry();if(manifest!=null){try{String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));manifest.add(java.util.Map.of("path",name,"size",bytes.length,"sha256",hash));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}}
    private String unique(Set<String> names,String requested){String name=requested;int dot=requested.lastIndexOf('.'),counter=2;while(!names.add(name)){name=dot>requested.lastIndexOf('/')?requested.substring(0,dot)+"-"+counter+requested.substring(dot):requested+"-"+counter;counter++;}return name;}
    private String safe(String value){String name=value.replaceAll("[^A-Za-z0-9._-]","_");return name.isBlank()||name.equals(".")||name.equals("..")?"Dokument":name;}
    private String value(Object value){return value==null?"-":String.valueOf(value);}
    private String value(String value,String fallback){return value==null||value.isBlank()?fallback:value;}
    public record Dossier(String filename,byte[] content){}
}
