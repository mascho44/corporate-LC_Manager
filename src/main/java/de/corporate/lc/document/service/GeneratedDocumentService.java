package de.corporate.lc.document.service;

import de.corporate.lc.document.api.*;
import de.corporate.lc.document.domain.*;
import de.corporate.lc.document.repository.LcDocumentRepository;
import de.corporate.lc.lc.repository.LetterOfCreditRepository;
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
    private final de.corporate.lc.company.service.CompanyProfileService companies;
    public GeneratedDocumentService(LetterOfCreditRepository lcs,LcDocumentRepository documents,DocxTemplateService docxTemplates,de.corporate.lc.company.service.CompanyProfileService companies){this.lcs=lcs;this.documents=documents;this.docxTemplates=docxTemplates;this.companies=companies;}
    @Transactional public DocumentView create(UUID lcId,GeneratedDocumentRequest request)throws IOException{
        validate(request.type());
        var lc=lcs.findById(lcId).orElseThrow();byte[] pdf=pdf(lc,request);String filename=safe(prefix(request.type())+"-"+request.documentNumber()+".pdf");
        LcDocument document=new LcDocument();document.setLetterOfCredit(lc);document.setDocumentType(request.type());document.setOriginalFilename(filename);document.setContentType("application/pdf");document.setFileSize(pdf.length);document.setDocumentDate(request.documentDate());document.setContent(pdf);document.setExtractionStatus("GENERATED");document.setExtractedReference(lc.getReference());document.setExtractedDocumentNumber(request.documentNumber());document.setExtractedText(text(lc,request));
        if(request.type()==DocumentType.COMMERCIAL_INVOICE){document.setAmount(lc.getAmount());document.setCurrency(lc.getCurrency());document.setExtractedAmount(lc.getAmount());document.setExtractedCurrency(lc.getCurrency());}
        return DocumentView.from(documents.save(document));
    }
    @Transactional public DocumentView createDocx(UUID lcId,GeneratedDocumentRequest request)throws IOException{
        validate(request.type());
        var lc=lcs.findById(lcId).orElseThrow();byte[] content=docxTemplates.render(lc,request);String filename=safe(prefix(request.type())+"-"+request.documentNumber()+".docx");
        LcDocument document=new LcDocument();document.setLetterOfCredit(lc);document.setDocumentType(request.type());document.setOriginalFilename(filename);document.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");document.setFileSize(content.length);document.setDocumentDate(request.documentDate());document.setContent(content);document.setExtractionStatus("GENERATED");document.setExtractedReference(lc.getReference());document.setExtractedDocumentNumber(request.documentNumber());document.setExtractedText(text(lc,request));
        if(request.type()==DocumentType.COMMERCIAL_INVOICE){document.setAmount(lc.getAmount());document.setCurrency(lc.getCurrency());document.setExtractedAmount(lc.getAmount());document.setExtractedCurrency(lc.getCurrency());}
        return DocumentView.from(documents.save(document));
    }
    private byte[] pdf(de.corporate.lc.lc.domain.LetterOfCredit lc,GeneratedDocumentRequest r)throws IOException{
        try(PDDocument pdf=new PDDocument();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            PDPage page=new PDPage(PDRectangle.A4);pdf.addPage(page);
            var regular=new PDType1Font(Standard14Fonts.FontName.HELVETICA);var bold=new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            try(PDPageContentStream c=new PDPageContentStream(pdf,page)){
                var company=companies.profile(lc.getCompanyId());
                float y=790;
                if(company.getLogo()!=null){
                    var image=org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject.createFromByteArray(pdf,company.getLogo(),"company-logo");
                    float scale=Math.min(100f/image.getWidth(),45f/image.getHeight());
                    c.drawImage(image,445,755,image.getWidth()*scale,image.getHeight()*scale);
                }
                if(company.getLegalName()!=null)y=line(c,bold,12,50,y,company.getLegalName());
                if(company.getAddressLine()!=null)y=line(c,regular,9,50,y,company.getAddressLine());
                String locality=java.util.stream.Stream.of(company.getPostalCode(),company.getCity(),company.getCountry()).filter(Objects::nonNull).collect(java.util.stream.Collectors.joining(" "));
                if(!locality.isBlank())y=line(c,regular,9,50,y,locality);
                y=Math.min(y-12,730);y=line(c,bold,18,50,y,title(r.type()));y-=8;
                y=field(c,bold,regular,y,"Document No.",r.documentNumber());y=field(c,bold,regular,y,"Date",r.documentDate().toString());y=field(c,bold,regular,y,"LC Reference",lc.getReference());y-=8;
                y=field(c,bold,regular,y,"Seller / Beneficiary",lc.getBeneficiary());y=field(c,bold,regular,y,"Buyer / Applicant",lc.getApplicant());y-=8;
                if(r.items()!=null&&!r.items().isEmpty()){
                    y=line(c,bold,10,50,y,"POSITIONS");
                    for(var item:r.items()){
                        String details=r.type()==DocumentType.PACKING_LIST
                                ? " | Packages "+number(item.packages())+" | Net "+weight(item.netWeight())+" | Gross "+weight(item.grossWeight())
                                : " | Unit price "+number(item.unitPrice())+" | Amount "+number(item.amount());
                        y=line(c,regular,9,50,y,value(item.position())+" | "+value(item.description())+" | "+number(item.quantity())+" "+value(item.unit())+details);
                    }
                }else{
                    y=field(c,bold,regular,y,"Description",value(r.description()));y=field(c,bold,regular,y,"Quantity",value(r.quantity()));
                }
                if(r.type()==DocumentType.COMMERCIAL_INVOICE)y=field(c,bold,regular,y,"Amount",lc.getCurrency()+" "+lc.getAmount());
                else if(r.type()==DocumentType.PACKING_LIST){y=field(c,bold,regular,y,"Packages",String.valueOf(r.packages()==null?0:r.packages()));y=field(c,bold,regular,y,"Net weight",weight(r.netWeight()));y=field(c,bold,regular,y,"Gross weight",weight(r.grossWeight()));}
                y-=8;field(c,bold,regular,y,"Statement / Notes",value(r.notes()));line(c,regular,8,50,40,"Generated by Corporate LC Manager · LC "+lc.getReference());
            }
            pdf.save(out);return out.toByteArray();
        }
    }
    private float field(PDPageContentStream c,PDFont bold,PDFont regular,float y,String label,String value)throws IOException{line(c,bold,9,50,y,label.toUpperCase(Locale.ROOT));return line(c,regular,10,180,y,value==null?"-":value)-10;}
    private float line(PDPageContentStream c,PDFont font,float size,float x,float y,String text)throws IOException{String clean=(text==null?"-":text).replaceAll("[\\r\\n]+"," ");for(String part:wrap(clean,85)){c.beginText();c.setFont(font,size);c.newLineAtOffset(x,y);c.showText(part.replaceAll("[^\\x20-\\x7E]","?"));c.endText();y-=14;}return y;}
    private List<String> wrap(String text,int width){List<String> lines=new ArrayList<>();StringBuilder line=new StringBuilder();for(String word:text.split("\\s+")){if(line.length()+word.length()+1>width){lines.add(line.toString());line.setLength(0);}if(!line.isEmpty())line.append(' ');line.append(word);}if(!line.isEmpty())lines.add(line.toString());return lines.isEmpty()?List.of("-"):lines;}
    private String text(de.corporate.lc.lc.domain.LetterOfCredit lc,GeneratedDocumentRequest r){
        StringBuilder text=new StringBuilder("Document No: ").append(r.documentNumber())
                .append("\nDate: ").append(r.documentDate()).append("\nLC Reference: ").append(lc.getReference())
                .append("\nSeller: ").append(value(lc.getBeneficiary())).append("\nBuyer: ").append(value(lc.getApplicant()))
                .append("\nDescription: ").append(value(r.description())).append("\nQuantity: ").append(value(r.quantity()));
        if(r.type()==DocumentType.COMMERCIAL_INVOICE)text.append("\nAmount: ").append(value(lc.getCurrency())).append(' ').append(lc.getAmount());
        else if(r.type()==DocumentType.PACKING_LIST)text.append("\nPackages: ").append(r.packages()==null?0:r.packages()).append("\nNet weight: ").append(weight(r.netWeight())).append("\nGross weight: ").append(weight(r.grossWeight()));
        if(r.items()!=null&&!r.items().isEmpty())for(var item:r.items())text.append("\nPosition ").append(value(item.position())).append(": ").append(value(item.description())).append(" | Quantity ").append(item.quantity()==null?"-":item.quantity()).append(' ').append(value(item.unit())).append(r.type()==DocumentType.PACKING_LIST?" | Packages "+(item.packages()==null?"-":item.packages())+" | Net "+weight(item.netWeight())+" | Gross "+weight(item.grossWeight()):" | Unit price "+(item.unitPrice()==null?"-":item.unitPrice())+" | Amount "+(item.amount()==null?"-":item.amount()));
        return text.append("\nNotes: ").append(value(r.notes())).toString();
    }
    private void validate(DocumentType type){if(!SUPPORTED.contains(type))throw new IllegalArgumentException("Dieser Dokumenttyp kann noch nicht erstellt werden.");}
    private String title(DocumentType type){return switch(type){case COMMERCIAL_INVOICE->"COMMERCIAL INVOICE";case PACKING_LIST->"PACKING LIST";case CERTIFICATE_OF_ORIGIN->"CERTIFICATE OF ORIGIN";case BENEFICIARY_CERTIFICATE->"BENEFICIARY'S CERTIFICATE";case QUALITY_CERTIFICATE->"QUALITY / ANALYSIS CERTIFICATE";default->type.getDisplayName().toUpperCase(Locale.ROOT);};}
    private String prefix(DocumentType type){return switch(type){case COMMERCIAL_INVOICE->"Handelsrechnung";case PACKING_LIST->"Packliste";case CERTIFICATE_OF_ORIGIN->"Ursprungszeugnis";case BENEFICIARY_CERTIFICATE->"Beguenstigtenzertifikat";case QUALITY_CERTIFICATE->"Qualitaetszertifikat";default->"Dokument";};}
    private String number(Number value){return value==null?"-":value.toString();}private String weight(BigDecimal value){return value==null?"-":value+" kg";}private String value(String value){return value==null||value.isBlank()?"-":value.trim();}private String safe(String name){return name.replaceAll("[^A-Za-z0-9._-]","_");}
}
