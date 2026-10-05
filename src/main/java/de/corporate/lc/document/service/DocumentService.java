package de.corporate.lc.document.service;

import de.corporate.lc.document.api.DocumentView;
import de.corporate.lc.document.api.DocumentUpdateRequest;
import de.corporate.lc.document.domain.DocumentType;
import de.corporate.lc.document.domain.LcDocument;
import de.corporate.lc.document.repository.LcDocumentRepository;
import de.corporate.lc.lc.repository.LetterOfCreditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.zip.ZipInputStream;

@Service
public class DocumentService {
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private final LcDocumentRepository documents;
    private final LetterOfCreditRepository lcs;
    private final DocumentExtractionService extraction;

    public DocumentService(LcDocumentRepository documents, LetterOfCreditRepository lcs,
            DocumentExtractionService extraction) {
        this.documents = documents;
        this.lcs = lcs;
        this.extraction = extraction;
    }

    @Transactional
    public DocumentView upload(UUID lcId, MultipartFile file, DocumentType type,
            LocalDate documentDate, BigDecimal amount, String currency) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Please select a non-empty file.");
        if (file.getSize() > MAX_FILE_SIZE) throw new IllegalArgumentException("File exceeds the 10 MB limit.");
        String filename = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
        if (filename.contains("/") || filename.contains("\\")) throw new IllegalArgumentException("Invalid filename.");

        LcDocument document = new LcDocument();
        document.setLetterOfCredit(lcs.findById(lcId).orElseThrow(() -> new NoSuchElementException("LC not found: " + lcId)));
        document.setDocumentType(type);
        document.setOriginalFilename(filename);
        document.setContentType(file.getContentType() == null ? "application/octet-stream" : file.getContentType());
        document.setFileSize(file.getSize());
        document.setDocumentDate(documentDate);
        document.setAmount(amount);
        document.setCurrency(currency == null || currency.isBlank() ? null : currency.trim().toUpperCase());
        document.setContent(file.getBytes());
        extraction.extract(document);document.setClassificationHistoryJson(ClassificationHistory.manual(document.getClassificationHistoryJson(),document.getDocumentType(),ClassificationHistory.actor()));
        if (document.getAmount() == null) document.setAmount(document.getExtractedAmount());
        if (document.getCurrency() == null) document.setCurrency(document.getExtractedCurrency());
        return DocumentView.from(documents.save(document));
    }

    @Transactional
    public List<DocumentView> uploadArchive(UUID lcId,MultipartFile archive)throws IOException{
        if(archive==null||archive.isEmpty())throw new IllegalArgumentException("Bitte ein nicht leeres ZIP-Archiv auswählen.");
        if(archive.getSize()>50L*1024*1024)throw new IllegalArgumentException("ZIP-Archiv überschreitet 50 MB.");
        var lc=lcs.findById(lcId).orElseThrow(()->new NoSuchElementException("LC not found: "+lcId));
        List<DocumentView> imported=new ArrayList<>();long total=0;int entries=0;
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(archive.getBytes()))){
            for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()){
                if(entry.isDirectory()||entry.getName().startsWith("__MACOSX/")||entry.getName().endsWith(".DS_Store"))continue;
                if(++entries>100)throw new IllegalArgumentException("ZIP-Archiv enthält mehr als 100 Dateien.");
                byte[] content=zip.readNBytes((int)MAX_FILE_SIZE+1);
                if(content.length>MAX_FILE_SIZE)throw new IllegalArgumentException("Datei im ZIP überschreitet 10 MB: "+entry.getName());
                total+=content.length;if(total>50L*1024*1024)throw new IllegalArgumentException("Entpackter ZIP-Inhalt überschreitet 50 MB.");
                String filename=entry.getName().replace('\\','/');filename=filename.substring(filename.lastIndexOf('/')+1);
                if(filename.isBlank())continue;
                imported.add(save(lc,filename,content,contentType(filename),type(filename),null,null,null));
            }
        }catch(java.util.zip.ZipException exception){throw new IllegalArgumentException("ZIP-Archiv ist beschädigt oder ungültig.",exception);}
        if(imported.isEmpty())throw new IllegalArgumentException("ZIP-Archiv enthält keine importierbaren Dateien.");
        return imported;
    }

    @Transactional
    public List<DocumentView> uploadBatch(UUID lcId,List<MultipartFile> files,List<DocumentType> types)throws IOException{
        if(files==null||files.isEmpty())throw new IllegalArgumentException("Bitte mindestens eine Datei auswählen.");
        if(files.size()>100)throw new IllegalArgumentException("Ein Batch darf höchstens 100 Dateien enthalten.");
        if(types==null||types.size()!=files.size())throw new IllegalArgumentException("Für jede Datei muss ein Dokumenttyp angegeben werden.");
        long total=files.stream().mapToLong(MultipartFile::getSize).sum();if(total>50L*1024*1024)throw new IllegalArgumentException("Der Batch überschreitet 50 MB.");
        List<DocumentView> imported=new ArrayList<>();
        for(int index=0;index<files.size();index++){
            MultipartFile file=files.get(index);String name=file.getOriginalFilename()==null?"":file.getOriginalFilename().toLowerCase(Locale.ROOT);
            if(name.endsWith(".zip"))imported.addAll(uploadArchive(lcId,file));else imported.add(upload(lcId,file,types.get(index),null,null,null));
        }
        return imported;
    }

    public DocumentType suggestType(String filename){return type(filename==null?"":filename);}

    private DocumentView save(de.corporate.lc.lc.domain.LetterOfCredit lc,String filename,byte[] content,String contentType,DocumentType type,LocalDate date,BigDecimal amount,String currency){
        LcDocument document=new LcDocument();document.setLetterOfCredit(lc);document.setDocumentType(type);document.setOriginalFilename(filename);document.setContentType(contentType);document.setFileSize(content.length);document.setDocumentDate(date);document.setAmount(amount);document.setCurrency(currency);document.setContent(content);extraction.extract(document);document.setClassificationHistoryJson(ClassificationHistory.manual(document.getClassificationHistoryJson(),document.getDocumentType(),ClassificationHistory.actor()));if(document.getAmount()==null)document.setAmount(document.getExtractedAmount());if(document.getCurrency()==null)document.setCurrency(document.getExtractedCurrency());return DocumentView.from(documents.save(document));
    }
    private DocumentType type(String filename){String name=filename.toLowerCase(Locale.ROOT);if(name.contains("invoice")||name.contains("rechnung"))return DocumentType.COMMERCIAL_INVOICE;if(name.contains("packing")||name.contains("packliste"))return DocumentType.PACKING_LIST;if(name.contains("bill-of-lading")||name.contains("bill_of_lading")||name.matches(".*(?:^|[-_ ])bl(?:[-_ .]|$).*"))return DocumentType.BILL_OF_LADING;if(name.contains("airway")||name.contains("air-waybill")||name.contains("awb"))return DocumentType.AIR_WAYBILL;if(name.contains("cmr"))return DocumentType.ROAD_CONSIGNMENT_NOTE;if(name.contains("origin")||name.contains("ursprung"))return DocumentType.CERTIFICATE_OF_ORIGIN;if(name.contains("insurance")||name.contains("versicherung"))return DocumentType.INSURANCE_CERTIFICATE;if(name.contains("inspection")||name.contains("inspektion"))return DocumentType.INSPECTION_CERTIFICATE;if(name.contains("draft")||name.contains("bill-of-exchange")||name.contains("wechsel"))return DocumentType.BILL_OF_EXCHANGE;if(name.contains("beneficiary-certificate"))return DocumentType.BENEFICIARY_CERTIFICATE;if(name.contains("quality")||name.contains("analysis")||name.contains("analyse"))return DocumentType.QUALITY_CERTIFICATE;if(name.contains("courier")||name.contains("dhl")||name.contains("fedex"))return DocumentType.COURIER_RECEIPT;return DocumentType.ANNEX;}
    private String contentType(String filename){String name=filename.toLowerCase(Locale.ROOT);if(name.endsWith(".pdf"))return "application/pdf";if(name.endsWith(".xml"))return "application/xml";if(name.endsWith(".txt")||name.endsWith(".csv"))return "text/plain";if(name.endsWith(".png"))return "image/png";if(name.endsWith(".jpg")||name.endsWith(".jpeg"))return "image/jpeg";return "application/octet-stream";}

    @Transactional(readOnly = true)
    public List<DocumentView> forLc(UUID lcId) {
        if (!lcs.existsById(lcId)) throw new NoSuchElementException("LC not found: " + lcId);
        return documents.findByLetterOfCreditIdOrderByUploadedAtDesc(lcId).stream().map(DocumentView::from).toList();
    }

    @Transactional(readOnly = true)
    public LcDocument one(UUID id) {
        return documents.findById(id).orElseThrow(() -> new NoSuchElementException("Document not found: " + id));
    }

    @Transactional
    public LcDocument delete(UUID lcId, UUID id) {
        LcDocument document = one(id);
        if (document.getLetterOfCredit() == null || !lcId.equals(document.getLetterOfCredit().getId()))
            throw new IllegalArgumentException("Dokument gehört nicht zu diesem Akkreditiv.");
        documents.delete(document);
        return document;
    }

    @Transactional
    public DocumentView update(UUID lcId,UUID id,DocumentUpdateRequest request){
        LcDocument document=one(id);
        if(document.getLetterOfCredit()==null||!lcId.equals(document.getLetterOfCredit().getId()))throw new IllegalArgumentException("Dokument gehört nicht zu diesem Akkreditiv.");
        if(request.amount()!=null&&request.amount().signum()<0)throw new IllegalArgumentException("Betrag darf nicht negativ sein.");
        if(document.getDocumentType()!=request.type()){
            var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            document.setClassificationHistoryJson(ClassificationHistory.manual(document.getClassificationHistoryJson(),request.type(),auth==null?"unknown":auth.getName()));
        }
        document.setDocumentType(request.type());document.setDocumentDate(request.documentDate());document.setAmount(request.amount());
        document.setCurrency(request.currency()==null||request.currency().isBlank()?null:request.currency().trim().toUpperCase(Locale.ROOT));
        return DocumentView.from(document);
    }
}
