package de.corporate.lc.check.service;

import de.corporate.lc.check.api.CheckResult;
import de.corporate.lc.check.api.ReviewSummary;
import de.corporate.lc.document.domain.DocumentType;
import de.corporate.lc.document.repository.LcDocumentRepository;
import de.corporate.lc.lc.domain.LetterOfCredit;
import de.corporate.lc.lc.repository.LetterOfCreditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;
import static de.corporate.lc.check.api.CheckResult.Severity.*;

@Service
public class DocumentCheckService {
    private final LetterOfCreditRepository lcs;
    private final LcDocumentRepository documents;

    public DocumentCheckService(LetterOfCreditRepository lcs, LcDocumentRepository documents) {
        this.lcs = lcs;
        this.documents = documents;
    }

    @Transactional(readOnly = true)
    public ReviewSummary check(UUID lcId) {
        LetterOfCredit lc = lcs.findById(lcId).orElseThrow();
        var uploaded = documents.findByLetterOfCreditIdOrderByUploadedAtDesc(lcId);
        List<CheckResult> results = new ArrayList<>();

        for (String requirement : lc.getRequiredDocuments()) {
            Optional<DocumentType> expected = classify(requirement);
            if (expected.isEmpty()) {
                results.add(finding(WARNING, "UNCLASSIFIED_REQUIREMENT", "Dokumentenbedingung muss manuell geprüft werden.", requirement, null, null));
            } else if (uploaded.stream().noneMatch(d -> d.getDocumentType() == expected.get())) {
                results.add(finding(DISCREPANCY, "MISSING_DOCUMENT", expected.get().getDisplayName() + " ist gefordert, wurde aber nicht vorgelegt.", requirement, null, "Kein entsprechendes Dokument hochgeladen"));
            } else {
                var present=uploaded.stream().filter(d->d.getDocumentType()==expected.get()).findFirst().orElseThrow();
                results.add(finding(OK, "DOCUMENT_PRESENT", expected.get().getDisplayName() + " wurde vorgelegt.", requirement, present.getOriginalFilename(), "Dokumenttyp: "+expected.get().getDisplayName()));
                checkFormalRequirement(requirement,present,results);
            }
        }

        uploaded.forEach(document -> {
            if ("NO_TEXT".equals(document.getExtractionStatus()))
                results.add(new CheckResult(WARNING, "PDF_REQUIRES_OCR", document.getOriginalFilename() + ": no embedded text found; OCR/manual review required."));
            else if ("UNSUPPORTED".equals(document.getExtractionStatus()))
                results.add(new CheckResult(WARNING, "FORMAT_NOT_EXTRACTED", document.getOriginalFilename() + ": this format is stored but not automatically read."));
            else if ("FAILED".equals(document.getExtractionStatus()))
                results.add(new CheckResult(WARNING, "EXTRACTION_FAILED", document.getOriginalFilename() + ": text extraction failed."));

            if (document.getDocumentDate() == null) {
                results.add(new CheckResult(WARNING, "DATE_NOT_CAPTURED", document.getOriginalFilename() + ": document date not captured."));
            } else if (lc.getExpiryDate() != null && document.getDocumentDate().isAfter(lc.getExpiryDate())) {
                results.add(new CheckResult(DISCREPANCY, "DOCUMENT_AFTER_EXPIRY", document.getOriginalFilename() + ": date is after LC expiry."));
            }
            if (document.getDocumentType() == DocumentType.COMMERCIAL_INVOICE && document.getAmount() != null) {
                if (document.getExtractedAmount() != null && document.getAmount().compareTo(document.getExtractedAmount()) != 0)
                    results.add(new CheckResult(DISCREPANCY, "CAPTURED_AMOUNT_MISMATCH", "Captured invoice amount differs from the amount extracted from the document."));
                if (document.getExtractedCurrency() != null && document.getCurrency() != null && !document.getCurrency().equalsIgnoreCase(document.getExtractedCurrency()))
                    results.add(new CheckResult(DISCREPANCY, "CAPTURED_CURRENCY_MISMATCH", "Captured invoice currency differs from the currency extracted from the document."));
                if (document.getCurrency() != null && lc.getCurrency() != null && !document.getCurrency().equalsIgnoreCase(lc.getCurrency())) {
                    results.add(finding(DISCREPANCY, "CURRENCY_MISMATCH", "Währung der Handelsrechnung weicht vom LC ab.", "LC-Betrag :32B: "+lc.getCurrency()+" "+lc.getAmount(), document.getOriginalFilename(), amountEvidence(document.getExtractedText(),document.getCurrency(),document.getAmount())));
                } else if (lc.getAmount() != null && document.getAmount().compareTo(lc.getAmount()) > 0) {
                    results.add(finding(DISCREPANCY, "INVOICE_AMOUNT_EXCEEDED", "Rechnungsbetrag überschreitet den LC-Betrag.", "LC-Betrag :32B: "+lc.getCurrency()+" "+lc.getAmount(), document.getOriginalFilename(), amountEvidence(document.getExtractedText(),document.getCurrency(),document.getAmount())));
                } else {
                    results.add(finding(OK, "INVOICE_AMOUNT_OK", "Rechnungsbetrag liegt innerhalb des LC-Betrags.", "LC-Betrag :32B: "+lc.getCurrency()+" "+lc.getAmount(), document.getOriginalFilename(), amountEvidence(document.getExtractedText(),document.getCurrency(),document.getAmount())));
                }
            }
            if (document.getDocumentType() == DocumentType.COMMERCIAL_INVOICE && readable(document)) {
                if (document.getExtractedReference() == null)
                    results.add(finding(WARNING, "LC_REFERENCE_NOT_FOUND", "LC-Referenz konnte in der Handelsrechnung nicht gefunden werden.", "LC-Referenz :20: "+lc.getReference(), document.getOriginalFilename(), "Keine passende Referenz im ausgelesenen Text"));
                else if (!sameReference(document.getExtractedReference(), lc.getReference()))
                    results.add(finding(DISCREPANCY, "LC_REFERENCE_MISMATCH", "Die Referenz der Handelsrechnung weicht vom LC ab.", "LC-Referenz :20: "+lc.getReference(), document.getOriginalFilename(), evidence(document.getExtractedText(),document.getExtractedReference())));
                else results.add(finding(OK, "LC_REFERENCE_OK", "Die Handelsrechnung nennt die richtige LC-Referenz.", "LC-Referenz :20: "+lc.getReference(), document.getOriginalFilename(), evidence(document.getExtractedText(),document.getExtractedReference())));

                if (lc.getBeneficiary() != null && !mentionsParty(document.getExtractedText(), lc.getBeneficiary()))
                    results.add(finding(DISCREPANCY, "BENEFICIARY_NOT_FOUND", "Begünstigter konnte in der Handelsrechnung nicht zugeordnet werden.", "Begünstigter :59: "+lc.getBeneficiary(), document.getOriginalFilename(), "Keine belastbare Fundstelle im Dokumenttext"));
                else if (lc.getBeneficiary() != null)
                    results.add(finding(OK, "BENEFICIARY_OK", "Begünstigter ist in der Handelsrechnung enthalten.", "Begünstigter :59: "+lc.getBeneficiary(), document.getOriginalFilename(), partyEvidence(document.getExtractedText(),lc.getBeneficiary())));
                if (lc.getApplicant() != null && !mentionsParty(document.getExtractedText(), lc.getApplicant()))
                    results.add(finding(WARNING, "APPLICANT_NOT_FOUND", "Antragsteller konnte in der Handelsrechnung nicht sicher zugeordnet werden.", "Antragsteller :50: "+lc.getApplicant(), document.getOriginalFilename(), "Keine belastbare Fundstelle im Dokumenttext"));
                else if (lc.getApplicant() != null)
                    results.add(finding(OK, "APPLICANT_OK", "Antragsteller ist in der Handelsrechnung enthalten.", "Antragsteller :50: "+lc.getApplicant(), document.getOriginalFilename(), partyEvidence(document.getExtractedText(),lc.getApplicant())));
            }
            if(document.getDocumentType()==DocumentType.PACKING_LIST&&readable(document)){
                String condition="LC-Referenz :20: "+lc.getReference();
                if(document.getExtractedReference()==null)results.add(finding(WARNING,"PACKING_LIST_REFERENCE_NOT_FOUND","LC-Referenz konnte in der Packliste nicht gefunden werden.",condition,document.getOriginalFilename(),"Keine passende Referenz im ausgelesenen Text"));
                else if(!sameReference(document.getExtractedReference(),lc.getReference()))results.add(finding(DISCREPANCY,"PACKING_LIST_REFERENCE_MISMATCH","Die Referenz der Packliste weicht vom LC ab.",condition,document.getOriginalFilename(),evidence(document.getExtractedText(),document.getExtractedReference())));
                else results.add(finding(OK,"PACKING_LIST_REFERENCE_OK","Die Packliste nennt die richtige LC-Referenz.",condition,document.getOriginalFilename(),evidence(document.getExtractedText(),document.getExtractedReference())));
                checkParty(document,lc.getBeneficiary(),"Begünstigter :59:","PACKING_BENEFICIARY",true,results);
                checkParty(document,lc.getApplicant(),"Antragsteller :50:","PACKING_APPLICANT",false,results);
                checkPackingDetails(document,results);
            }
        });

        var invoices=uploaded.stream().filter(d->d.getDocumentType()==DocumentType.COMMERCIAL_INVOICE).toList();
        var packingLists=uploaded.stream().filter(d->d.getDocumentType()==DocumentType.PACKING_LIST).toList();
        String goodsDescription=swiftField(lc.getRawMessage(),"45A").orElseGet(()->additionalField(lc,"45A").orElse(null));
        if(goodsDescription!=null){
            uploaded.stream().filter(d->(d.getDocumentType()==DocumentType.COMMERCIAL_INVOICE||d.getDocumentType()==DocumentType.PACKING_LIST)&&readable(d)).forEach(document->{
                String documentDescription=labeledValue(document.getExtractedText(),"Description").orElse(null);
                String condition="Warenbeschreibung :45A: "+excerpt(goodsDescription);
                if(documentDescription==null)results.add(finding(WARNING,"GOODS_DESCRIPTION_NOT_FOUND","Warenbeschreibung konnte im Dokument nicht gefunden werden.",condition,document.getOriginalFilename(),"Keine Zeile 'Description' im ausgelesenen Dokumenttext"));
                else if(!similarDescription(goodsDescription,documentDescription))results.add(finding(DISCREPANCY,"GOODS_DESCRIPTION_MISMATCH","Warenbeschreibung weicht von der gültigen LC-Fassung ab.",condition,document.getOriginalFilename(),"Description: "+excerpt(documentDescription)));
                else results.add(finding(OK,"GOODS_DESCRIPTION_OK","Warenbeschreibung stimmt mit der gültigen LC-Fassung überein.",condition,document.getOriginalFilename(),"Description: "+excerpt(documentDescription)));
            });
        }
        if(!invoices.isEmpty()&&!packingLists.isEmpty()){
            var invoice=invoices.get(0);var packing=packingLists.get(0);
            if(invoice.getExtractedReference()!=null&&packing.getExtractedReference()!=null){boolean same=sameReference(invoice.getExtractedReference(),packing.getExtractedReference());results.add(finding(same?OK:DISCREPANCY,"INVOICE_PACKING_REFERENCE_CONSISTENCY",same?"Handelsrechnung und Packliste verwenden dieselbe LC-Referenz.":"Handelsrechnung und Packliste verwenden unterschiedliche LC-Referenzen.","Dokumente müssen sich auf dieselbe gültige LC-Fassung beziehen.",packing.getOriginalFilename(),"Packliste: "+packing.getExtractedReference()+" · Handelsrechnung: "+invoice.getExtractedReference()));}
            var invoiceDescription=labeledValue(invoice.getExtractedText(),"Description");var packingDescription=labeledValue(packing.getExtractedText(),"Description");
            if(invoiceDescription.isPresent()&&packingDescription.isPresent()){boolean same=similarDescription(invoiceDescription.get(),packingDescription.get());results.add(finding(same?OK:DISCREPANCY,"INVOICE_PACKING_DESCRIPTION_CONSISTENCY",same?"Handelsrechnung und Packliste enthalten eine übereinstimmende Warenbeschreibung.":"Warenbeschreibungen in Handelsrechnung und Packliste widersprechen sich.","Handelsrechnung und Packliste müssen dieselben Waren betreffen.",packing.getOriginalFilename(),"Packliste: "+excerpt(packingDescription.get())+" · Handelsrechnung: "+excerpt(invoiceDescription.get())));}
            var invoiceQuantity=labeledValue(invoice.getExtractedText(),"Quantity");var packingQuantity=labeledValue(packing.getExtractedText(),"Quantity");
            if(invoiceQuantity.isEmpty()||packingQuantity.isEmpty())results.add(finding(WARNING,"QUANTITY_NOT_COMPARABLE","Mengenangaben konnten zwischen Handelsrechnung und Packliste nicht vollständig verglichen werden.","Handelsrechnung und Packliste müssen dieselbe Warenmenge ausweisen.",packing.getOriginalFilename(),"Handelsrechnung: "+invoiceQuantity.orElse("keine Menge erkannt")+" · Packliste: "+packingQuantity.orElse("keine Menge erkannt")));
            else{boolean same=normalizeMeasure(invoiceQuantity.get()).equals(normalizeMeasure(packingQuantity.get()));results.add(finding(same?OK:DISCREPANCY,"INVOICE_PACKING_QUANTITY_CONSISTENCY",same?"Handelsrechnung und Packliste weisen dieselbe Menge aus.":"Mengenangaben in Handelsrechnung und Packliste widersprechen sich.","Handelsrechnung und Packliste müssen dieselbe Warenmenge ausweisen.",packing.getOriginalFilename(),"Packliste: "+excerpt(packingQuantity.get())+" · Handelsrechnung: "+excerpt(invoiceQuantity.get())));}
        }

        uploaded.stream().filter(d -> d.getExtractedDocumentNumber() != null)
                .collect(java.util.stream.Collectors.groupingBy(d -> d.getExtractedDocumentNumber().toUpperCase(Locale.ROOT)))
                .forEach((number, matches) -> {
                    if (matches.size() > 1) results.add(new CheckResult(WARNING, "DUPLICATE_DOCUMENT_NUMBER", "Document number " + number + " occurs more than once."));
                });

        if (lc.getExpiryDate() != null && lc.getExpiryDate().isBefore(LocalDate.now()))
            results.add(new CheckResult(WARNING, "LC_EXPIRED", "The LC has expired."));
        if (results.isEmpty()) results.add(new CheckResult(WARNING, "NO_RULES_APPLIED", "No automated rule could be applied."));

        return new ReviewSummary(count(results, DISCREPANCY), count(results, WARNING), count(results, OK), results);
    }

    private long count(List<CheckResult> results, CheckResult.Severity severity) {
        return results.stream().filter(result -> result.severity() == severity).count();
    }

    private Optional<DocumentType> classify(String requirement) {
        String text = requirement.toLowerCase(Locale.ROOT);
        if (text.contains("invoice")) return Optional.of(DocumentType.COMMERCIAL_INVOICE);
        if (text.contains("packing") || text.contains("weight list")) return Optional.of(DocumentType.PACKING_LIST);
        if (text.contains("bill of lading") || text.matches(".*\\bb/?l\\b.*")) return Optional.of(DocumentType.BILL_OF_LADING);
        if (text.contains("certificate of origin") || text.contains("origin certificate")) return Optional.of(DocumentType.CERTIFICATE_OF_ORIGIN);
        if (text.contains("insurance")) return Optional.of(DocumentType.INSURANCE_CERTIFICATE);
        return Optional.empty();
    }

    private boolean sameReference(String left, String right) {
        if (left == null || right == null) return false;
        return left.replaceAll("[^A-Za-z0-9]", "").equalsIgnoreCase(right.replaceAll("[^A-Za-z0-9]", ""));
    }

    private boolean mentionsParty(String text, String party) {
        if (text == null || party == null) return false;
        String haystack = normalize(text);
        var tokens = Arrays.stream(normalize(party).split(" "))
                .filter(token -> token.length() > 2)
                .filter(token -> !Set.of("ltd", "llc", "inc", "gmbh", "corp", "company", "limited").contains(token))
                .limit(2).toList();
        return !tokens.isEmpty() && tokens.stream().allMatch(haystack::contains);
    }

    private boolean readable(de.corporate.lc.document.domain.LcDocument document){return "EXTRACTED".equals(document.getExtractionStatus())||"OCR_EXTRACTED".equals(document.getExtractionStatus())||"GENERATED".equals(document.getExtractionStatus());}
    private CheckResult finding(CheckResult.Severity severity,String code,String message,String condition,String document,String proof){return new CheckResult(severity,code,message,condition,document,proof);}
    private String evidence(String text,String value){if(text==null||value==null)return "Keine Fundstelle verfügbar";String compact=value.replaceAll("[^A-Za-z0-9]","");for(String line:text.split("\\R")){String normalized=line.replaceAll("[^A-Za-z0-9]","");if(!compact.isBlank()&&(normalized.toLowerCase(Locale.ROOT).contains(compact.toLowerCase(Locale.ROOT))||compact.toLowerCase(Locale.ROOT).contains(normalized.toLowerCase(Locale.ROOT))))return excerpt(line);}return "Erkannter Wert: "+value;}
    private String partyEvidence(String text,String party){if(text==null)return "Keine Fundstelle verfügbar";for(String token:normalize(party).split(" "))if(token.length()>3)for(String line:text.split("\\R"))if(normalize(line).contains(token))return excerpt(line);return "Partei im Dokumenttext erkannt";}
    private String amountEvidence(String text,String currency,java.math.BigDecimal amount){if(text!=null){String digits=amount.toPlainString().replaceAll("[^0-9]","");for(String line:text.split("\\R")){String normalized=line.replaceAll("[^0-9]","");if((currency!=null&&line.toUpperCase(Locale.ROOT).contains(currency.toUpperCase(Locale.ROOT)))||(!digits.isBlank()&&normalized.contains(digits)))return excerpt(line);}}return (currency==null?"":currency+" ")+amount;}
    private String excerpt(String line){String value=line.replaceAll("\\s+"," ").trim();return value.length()<=240?value:value.substring(0,237)+"…";}
    private Optional<String> swiftField(String raw,String code){if(raw==null)return Optional.empty();var matcher=java.util.regex.Pattern.compile("(?ms)^:"+java.util.regex.Pattern.quote(code)+":\\s*(.*?)(?=^:[0-9]{2}[A-Z]?:|\\z)").matcher(raw);return matcher.find()?Optional.of(matcher.group(1).trim()):Optional.empty();}
    private Optional<String> additionalField(LetterOfCredit lc,String code){return lc.getAdditionalFields().entrySet().stream().filter(e->e.getKey().toUpperCase(Locale.ROOT).contains(code)).map(Map.Entry::getValue).filter(v->v!=null&&!v.isBlank()).findFirst();}
    private Optional<String> labeledValue(String text,String label){if(text==null)return Optional.empty();String prefix=label.toLowerCase(Locale.ROOT)+":";return Arrays.stream(text.split("\\R")).map(String::trim).filter(line->line.toLowerCase(Locale.ROOT).startsWith(prefix)).map(line->line.substring(line.indexOf(':')+1).trim()).filter(value->!value.isBlank()&&!"-".equals(value)).findFirst();}
    private void checkFormalRequirement(String requirement,de.corporate.lc.document.domain.LcDocument document,List<CheckResult> results){
        String lower=requirement.toLowerCase(Locale.ROOT);String text=normalize(document.getExtractedText()==null?"":document.getExtractedText());
        if(lower.matches("(?s).*(signed|signature|duly signed|unterzeichnet|unterschrift).*")&&readable(document)){
            boolean found=text.matches("(?s).*(signed|signature|signatory|authorized signature|unterzeichnet|unterschrift).*" );
            results.add(finding(found?OK:WARNING,"SIGNATURE_REQUIREMENT_"+(found?"EVIDENCED":"REVIEW"),found?"Ein Unterschriftenvermerk wurde im Dokument erkannt.":"Geforderte Unterschrift konnte nicht automatisch belegt werden und muss visuell geprüft werden.",requirement,document.getOriginalFilename(),found?textEvidence(document.getExtractedText(),Set.of("signed","signature","signatory","unterzeichnet","unterschrift")):"Kein eindeutiger Unterschriftenvermerk im ausgelesenen Text"));
        }
        if(lower.matches("(?s).*(original|copy|copies|duplicate|triplicate|kopie|kopien|ausfertigung).*") )
            results.add(finding(WARNING,"DOCUMENT_COPIES_MANUAL_REVIEW","Geforderte Anzahl von Originalen oder Kopien muss manuell geprüft werden.",requirement,document.getOriginalFilename(),"Die Anzahl körperlicher Originale/Kopien lässt sich aus einer einzelnen Datei nicht zuverlässig ableiten."));
    }
    private void checkParty(de.corporate.lc.document.domain.LcDocument document,String party,String label,String code,boolean discrepancy,List<CheckResult> results){if(party==null||party.isBlank())return;boolean found=mentionsParty(document.getExtractedText(),party);results.add(finding(found?OK:(discrepancy?DISCREPANCY:WARNING),code+(found?"_OK":"_NOT_FOUND"),found?(label.startsWith("Begünstigter")?"Begünstigter":"Antragsteller")+" ist in der Packliste enthalten.":(label.startsWith("Begünstigter")?"Begünstigter":"Antragsteller")+" konnte in der Packliste nicht sicher zugeordnet werden.",label+" "+party,document.getOriginalFilename(),found?partyEvidence(document.getExtractedText(),party):"Keine belastbare Fundstelle im Dokumenttext"));}
    private void checkPackingDetails(de.corporate.lc.document.domain.LcDocument document,List<CheckResult> results){
        String condition="Packliste muss Packstückzahl sowie plausibles Netto- und Bruttogewicht ausweisen.";String name=document.getOriginalFilename();
        Optional<java.math.BigDecimal> packages=number(labeledValue(document.getExtractedText(),"Packages").orElse(null));Optional<java.math.BigDecimal> net=number(labeledValue(document.getExtractedText(),"Net weight").orElse(null));Optional<java.math.BigDecimal> gross=number(labeledValue(document.getExtractedText(),"Gross weight").orElse(null));
        if(packages.isEmpty()||packages.get().signum()<=0)results.add(finding(WARNING,"PACKAGES_MISSING","Keine gültige Packstückzahl erkannt.",condition,name,"Packages: "+labeledValue(document.getExtractedText(),"Packages").orElse("nicht angegeben")));
        else results.add(finding(OK,"PACKAGES_OK","Packstückzahl ist angegeben.",condition,name,"Packages: "+packages.get().stripTrailingZeros().toPlainString()));
        if(net.isEmpty()||gross.isEmpty())results.add(finding(WARNING,"WEIGHTS_MISSING","Netto- oder Bruttogewicht fehlt in der Packliste.",condition,name,"Net: "+labeledValue(document.getExtractedText(),"Net weight").orElse("nicht angegeben")+" · Brutto: "+labeledValue(document.getExtractedText(),"Gross weight").orElse("nicht angegeben")));
        else if(gross.get().compareTo(net.get())<0)results.add(finding(DISCREPANCY,"PACKING_WEIGHT_IMPLAUSIBLE","Bruttogewicht ist kleiner als das Nettogewicht.",condition,name,"Net: "+net.get()+" kg · Brutto: "+gross.get()+" kg"));
        else results.add(finding(OK,"PACKING_WEIGHT_OK","Netto- und Bruttogewicht sind plausibel.",condition,name,"Net: "+net.get()+" kg · Brutto: "+gross.get()+" kg"));
    }
    private Optional<java.math.BigDecimal> number(String value){if(value==null)return Optional.empty();var matcher=java.util.regex.Pattern.compile("[-+]?[0-9]+(?:[.,][0-9]+)?").matcher(value);if(!matcher.find())return Optional.empty();try{return Optional.of(new java.math.BigDecimal(matcher.group().replace(',','.')));}catch(NumberFormatException ignored){return Optional.empty();}}
    private String normalizeMeasure(String value){return normalize(value).replaceAll("\\b(pcs|pieces|piece|stueck|stk)\\b","stück").replaceAll("\\s+","").trim();}
    private String textEvidence(String text,Set<String> needles){if(text==null)return "Keine Fundstelle verfügbar";for(String line:text.split("\\R")){String normalized=normalize(line);if(needles.stream().anyMatch(normalized::contains))return excerpt(line);}return "Vermerk im Dokumenttext erkannt";}
    private boolean similarDescription(String expected,String actual){Set<String> left=meaningfulTokens(expected),right=meaningfulTokens(actual);if(left.isEmpty()||right.isEmpty())return false;long overlap=left.stream().filter(right::contains).count();return overlap>=Math.min(2,Math.min(left.size(),right.size()))||overlap/(double)Math.min(left.size(),right.size())>=0.6;}
    private Set<String> meaningfulTokens(String value){Set<String> ignored=Set.of("and","the","for","of","with","und","der","die","das","mit","von","goods","description");Set<String> result=new LinkedHashSet<>();for(String token:normalize(value).split(" "))if(token.length()>2&&!ignored.contains(token))result.add(token);return result;}

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }
}
