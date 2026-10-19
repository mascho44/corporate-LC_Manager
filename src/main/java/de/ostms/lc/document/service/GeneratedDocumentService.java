package de.ostms.lc.document.service;

import de.ostms.lc.document.api.*;
import de.ostms.lc.document.domain.*;
import de.ostms.lc.document.repository.LcDocumentRepository;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.*;
import java.math.BigDecimal;
import java.util.*;

@Service
public class GeneratedDocumentService {
    private static final Set<DocumentType> SUPPORTED=EnumSet.of(DocumentType.COMMERCIAL_INVOICE,DocumentType.PACKING_LIST,DocumentType.CERTIFICATE_OF_ORIGIN,DocumentType.BENEFICIARY_CERTIFICATE,DocumentType.QUALITY_CERTIFICATE);
    private final LetterOfCreditRepository lcs;private final LcDocumentRepository documents;private final DocxTemplateService docxTemplates;
    private final de.ostms.lc.company.service.CompanyProfileService companies;
    public GeneratedDocumentService(LetterOfCreditRepository lcs,LcDocumentRepository documents,DocxTemplateService docxTemplates,de.ostms.lc.company.service.CompanyProfileService companies){this.lcs=lcs;this.documents=documents;this.docxTemplates=docxTemplates;this.companies=companies;}
    @Transactional public DocumentView create(UUID lcId,GeneratedDocumentRequest request)throws IOException{
        validate(request.type());
        var lc=lcs.findById(lcId).orElseThrow();byte[] pdf=pdf(lc,request);String filename=safe(prefix(request.type())+"-"+request.documentNumber()+".pdf");
        LcDocument document=new LcDocument();document.setLetterOfCredit(lc);document.setDocumentType(request.type());document.setOriginalFilename(filename);document.setContentType("application/pdf");document.setFileSize(pdf.length);document.setDocumentDate(request.documentDate());document.setContent(pdf);document.setExtractionStatus("GENERATED");document.setExtractedReference(lc.getReference());document.setExtractedDocumentNumber(request.documentNumber());document.setExtractedText(text(lc,request));
        if(request.type()==DocumentType.COMMERCIAL_INVOICE){BigDecimal total=invoiceTotal(lc,request);document.setAmount(total);document.setCurrency(lc.getCurrency());document.setExtractedAmount(total);document.setExtractedCurrency(lc.getCurrency());}
        return DocumentView.from(documents.save(document));
    }
    @Transactional public DocumentView createDocx(UUID lcId,GeneratedDocumentRequest request)throws IOException{
        validate(request.type());
        var lc=lcs.findById(lcId).orElseThrow();byte[] content=docxTemplates.render(lc,request);String filename=safe(prefix(request.type())+"-"+request.documentNumber()+".docx");
        LcDocument document=new LcDocument();document.setLetterOfCredit(lc);document.setDocumentType(request.type());document.setOriginalFilename(filename);document.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");document.setFileSize(content.length);document.setDocumentDate(request.documentDate());document.setContent(content);document.setExtractionStatus("GENERATED");document.setExtractedReference(lc.getReference());document.setExtractedDocumentNumber(request.documentNumber());document.setExtractedText(text(lc,request));
        if(request.type()==DocumentType.COMMERCIAL_INVOICE){BigDecimal total=invoiceTotal(lc,request);document.setAmount(total);document.setCurrency(lc.getCurrency());document.setExtractedAmount(total);document.setExtractedCurrency(lc.getCurrency());}
        return DocumentView.from(documents.save(document));
    }
    private byte[] pdf(de.ostms.lc.lc.domain.LetterOfCredit lc,GeneratedDocumentRequest r)throws IOException{return docxTemplates.renderPdf(lc,r);}
    private String text(de.ostms.lc.lc.domain.LetterOfCredit lc,GeneratedDocumentRequest r){
        StringBuilder text=new StringBuilder("Document No: ").append(r.documentNumber())
                .append("\nDate: ").append(r.documentDate()).append("\nLC Reference: ").append(lc.getReference())
                .append("\nSeller: ").append(value(lc.getBeneficiary())).append("\nBuyer: ").append(value(lc.getApplicant()))
                .append("\nDescription: ").append(value(r.description())).append("\nQuantity: ").append(value(r.quantity()));
        if(r.type()==DocumentType.COMMERCIAL_INVOICE)text.append("\nAmount: ").append(value(lc.getCurrency())).append(' ').append(invoiceTotal(lc,r));
        else if(r.type()==DocumentType.PACKING_LIST)text.append("\nPackages: ").append(r.packages()==null?0:r.packages()).append("\nNet weight: ").append(weight(r.netWeight())).append("\nGross weight: ").append(weight(r.grossWeight()));
        if(r.items()!=null&&!r.items().isEmpty())for(var item:r.items())text.append("\nPosition ").append(value(item.position())).append(": ").append(value(item.description())).append(" | Quantity ").append(item.quantity()==null?"-":item.quantity()).append(' ').append(value(item.unit())).append(r.type()==DocumentType.PACKING_LIST?" | Packages "+(item.packages()==null?"-":item.packages())+" | Net "+weight(item.netWeight())+" | Gross "+weight(item.grossWeight()):" | Unit price "+(item.unitPrice()==null?"-":item.unitPrice())+" | Amount "+(item.amount()==null?"-":item.amount()));
        // The same details as the printed document: shipment terms and the declaration above the signature, so the automatic check reads what a person reads.
        var company=companies.profileForLetterhead(lc.getCompanyId(),lc.getBeneficiary());
        String incoterm=DocxTemplateService.incoterm(de.ostms.lc.lc.service.LcConditions.value(lc,de.ostms.lc.lc.domain.LcCondition.GOODS_DESCRIPTION).orElse(""));
        if(r.type()==DocumentType.COMMERCIAL_INVOICE){
            if(incoterm!=null)text.append("\nIncoterm: ").append(incoterm);
            String terms=DocxTemplateService.paymentTerms(lc.getRawMessage());if(terms!=null)text.append("\nPayment terms: ").append(terms);
        }
        String loading=de.ostms.lc.lc.service.LcConditions.value(lc,de.ostms.lc.lc.domain.LcCondition.PORT_OF_LOADING).orElse(null);if(loading!=null)text.append("\nPort of loading: ").append(loading);
        String discharge=de.ostms.lc.lc.service.LcConditions.value(lc,de.ostms.lc.lc.domain.LcCondition.PORT_OF_DISCHARGE).or(()->de.ostms.lc.lc.service.LcConditions.value(lc,de.ostms.lc.lc.domain.LcCondition.FINAL_DESTINATION)).orElse(null);if(discharge!=null)text.append("\nPort of discharge: ").append(discharge);
        String declaration=ProfessionalDocxTemplate.declarationText(r.type()).replace("{{companyCountry}}",value(company.getCountry())).replace("{{lcReference}}",value(lc.getReference())).replace("{{applicant}}",value(lc.getApplicant()));
        text.append("\nNotes: ").append(value(r.notes()));
        if(!declaration.isEmpty())text.append("\n").append(declaration);
        return text.append("\nPlace, date: ").append(value(company.getCity())).append(", ").append(r.documentDate()).append("\nAuthorised signatory: ").append(value(company.getLegalName())).toString();
    }
    /** The invoice total: the sum of the lines when every line has an amount, otherwise (no lines) the credit amount as before. */
    static BigDecimal invoiceTotal(de.ostms.lc.lc.domain.LetterOfCredit lc,GeneratedDocumentRequest r){
        if(r.items()!=null&&!r.items().isEmpty()){
            BigDecimal sum=BigDecimal.ZERO;boolean complete=true;
            for(var item:r.items()){if(item.amount()==null){complete=false;break;}sum=sum.add(item.amount());}
            if(complete)return sum.setScale(2,java.math.RoundingMode.HALF_UP);
        }
        return lc.getAmount();
    }
    private void validate(DocumentType type){if(!SUPPORTED.contains(type))throw new IllegalArgumentException("Dieser Dokumenttyp kann noch nicht erstellt werden.");}
    private String title(DocumentType type){return switch(type){case COMMERCIAL_INVOICE->"COMMERCIAL INVOICE";case PACKING_LIST->"PACKING LIST";case CERTIFICATE_OF_ORIGIN->"CERTIFICATE OF ORIGIN";case BENEFICIARY_CERTIFICATE->"BENEFICIARY'S CERTIFICATE";case QUALITY_CERTIFICATE->"QUALITY / ANALYSIS CERTIFICATE";default->type.getDisplayName().toUpperCase(Locale.ROOT);};}
    private String prefix(DocumentType type){return switch(type){case COMMERCIAL_INVOICE->"Handelsrechnung";case PACKING_LIST->"Packliste";case CERTIFICATE_OF_ORIGIN->"Ursprungszeugnis";case BENEFICIARY_CERTIFICATE->"Beguenstigtenzertifikat";case QUALITY_CERTIFICATE->"Qualitaetszertifikat";default->"Dokument";};}
    private String number(Number value){return value==null?"-":value.toString();}private String weight(BigDecimal value){return value==null?"-":value+" kg";}private String value(String value){return value==null||value.isBlank()?"-":value.trim();}private String safe(String name){return name.replaceAll("[^A-Za-z0-9._-]","_");}
}
