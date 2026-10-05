package de.corporate.lc.imports.service;

import de.corporate.lc.imports.api.*;
import de.corporate.lc.lc.domain.*;
import de.corporate.lc.lc.repository.*;
import de.corporate.lc.lc.service.*;
import de.corporate.lc.swift.*;
import de.corporate.lc.training.service.TrainingLearningService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class SwiftImportService {
    private static final Pattern FIELD = Pattern.compile("(?m)^:(\\d{2}[A-Z]?):(.*?)(?=^:\\d{2}[A-Z]?:|\\z)", Pattern.DOTALL);
    private static final Map<String,String> COMMON_LABELS = Map.ofEntries(
            Map.entry("20", "Transaction Reference Number"), Map.entry("21", "Related Reference"),
            Map.entry("27", "Sequence of Total"), Map.entry("30", "Date"),
            Map.entry("31C", "Date of Issue"), Map.entry("31D", "Expiry Date and Place"),
            Map.entry("31E", "New Expiry Date and Place"), Map.entry("32B", "Currency and Amount"),
            Map.entry("33B", "Decrease of Amount"), Map.entry("40A", "Form of Credit"),
            Map.entry("41A", "Available With"), Map.entry("42C", "Drafts at"),
            Map.entry("44A", "Place of Taking in Charge"), Map.entry("44B", "Place of Final Destination"),
            Map.entry("44C", "Latest Date of Shipment"), Map.entry("45A", "Description of Goods"),
            Map.entry("46A", "Documents Required"), Map.entry("47A", "Additional Conditions"),
            Map.entry("48", "Period for Presentation"), Map.entry("49", "Confirmation Instructions"),
            Map.entry("50", "Applicant"), Map.entry("52A", "Issuing Bank"),
            Map.entry("57A", "Advise Through Bank"), Map.entry("59", "Beneficiary"),
            Map.entry("71B", "Charges"), Map.entry("72", "Sender to Receiver Information"),
            Map.entry("78", "Instructions to Bank"));
    private static final Map<String,String> MT700_LABELS = Map.ofEntries(
            Map.entry("27", "Sequenz / Gesamtzahl (Sequence of Total)"),
            Map.entry("40A", "Form des Dokumentenakkreditivs (Form of Documentary Credit)"),
            Map.entry("20", "Dokumentenakkreditivnummer (Documentary Credit Number)"),
            Map.entry("31C", "Ausstellungsdatum (Date of Issue)"),
            Map.entry("40E", "Anwendbare Regeln (Applicable Rules)"),
            Map.entry("31D", "Ablaufdatum und -ort (Date and Place of Expiry)"),
            Map.entry("50", "Auftraggeber / Antragsteller (Applicant)"),
            Map.entry("59", "Begünstigter (Beneficiary)"),
            Map.entry("32B", "Währung und Betrag (Currency Code, Amount)"),
            Map.entry("41A", "Verfügbar bei / durch (Available With ... By ...)"),
            Map.entry("41D", "Verfügbar bei / durch - Freitext (Available With ... By ...)"),
            Map.entry("42A", "Bezogene Bank (Drawee)"), Map.entry("42C", "Trattenlaufzeit (Drafts at ...)"),
            Map.entry("42M", "Details zur Mixed Payment"), Map.entry("42P", "Details zur hinausgeschobenen Zahlung"),
            Map.entry("43P", "Teilsendungen (Partial Shipments)"), Map.entry("43T", "Umladung (Transhipment)"),
            Map.entry("44A", "Übernahme-/Versandort (Place of Taking in Charge/Dispatch)"),
            Map.entry("44B", "Endbestimmungsort (Place of Final Destination)"),
            Map.entry("44C", "Spätestes Versanddatum (Latest Date of Shipment)"),
            Map.entry("44E", "Ladehafen / Abgangsflughafen"), Map.entry("44F", "Löschhafen / Zielflughafen"),
            Map.entry("45A", "Waren- oder Leistungsbeschreibung (Description of Goods/Services)"),
            Map.entry("46A", "Erforderliche Dokumente (Documents Required)"),
            Map.entry("47A", "Zusätzliche Bedingungen (Additional Conditions)"),
            Map.entry("71D", "Gebühren (Charges)"), Map.entry("48", "Vorlagefrist in Tagen (Period for Presentation)"),
            Map.entry("49", "Bestätigungsanweisung (Confirmation Instructions)"),
            Map.entry("57A", "Avisierende Bank (Advise Through Bank)"),
            Map.entry("78", "Anweisungen an die zahlende/akzeptierende Bank"),
            Map.entry("72Z", "Informationen Sender an Empfänger")
    );
    private static final Map<String,String> MT707_LABELS = Map.ofEntries(
            Map.entry("23", "Issuing Bank's Reference"), Map.entry("26E", "Amendment Number"),
            Map.entry("30", "Date of Amendment"), Map.entry("31E", "New Expiry Date and Place"),
            Map.entry("45B", "Description of Goods Changed"), Map.entry("46B", "Documents Required Changed"),
            Map.entry("47B", "Additional Conditions Changed"));
    private static final Map<String,String> MT760_LABELS = Map.ofEntries(
            Map.entry("15A", "New Sequence"), Map.entry("15B", "New Sequence"),
            Map.entry("22A", "Purpose of Message"), Map.entry("22D", "Form of Undertaking"),
            Map.entry("23", "Further Identification"), Map.entry("23B", "Expiry Type"),
            Map.entry("23H", "Function of Message"), Map.entry("24E", "Delivery of Undertaking"),
            Map.entry("24G", "Delivery To/Collection By"), Map.entry("30", "Date of Issue"),
            Map.entry("40C", "Applicable Rules"), Map.entry("45C", "Document and Presentation Instructions"),
            Map.entry("45L", "Underlying Transaction Details"), Map.entry("77C", "Details of Guarantee"),
            Map.entry("77U", "Undertaking Terms and Conditions"));
    private static final Map<String,String> MT700_TARGETS = Map.ofEntries(
            Map.entry("20", "reference"), Map.entry("27", "sequence"), Map.entry("31C", "issueDate"),
            Map.entry("31D", "expiryDateAndPlace"), Map.entry("32B", "amountAndCurrency"),
            Map.entry("44C", "latestShipmentDate"), Map.entry("46A", "requiredDocuments"),
            Map.entry("50", "applicant"), Map.entry("52A", "issuingBank"),
            Map.entry("57A", "advisingBank"), Map.entry("59", "beneficiary"));
    private static final Map<String,String> MT707_TARGETS = Map.ofEntries(
            Map.entry("20", "reference"), Map.entry("21", "relatedReference"), Map.entry("27", "sequence"),
            Map.entry("26E", "amendmentNumber"), Map.entry("30", "amendmentDate"),
            Map.entry("31E", "newExpiryDateAndPlace"), Map.entry("32B", "amountIncrease"),
            Map.entry("33B", "amountDecrease"), Map.entry("44C", "latestShipmentDate"),
            Map.entry("45B", "changedGoods"), Map.entry("46B", "changedDocuments"),
            Map.entry("47B", "changedConditions"));
    private static final Map<String,String> MT760_TARGETS = Map.ofEntries(
            Map.entry("20", "guaranteeReference"), Map.entry("21", "relatedReference"), Map.entry("27", "sequence"),
            Map.entry("22A", "messagePurpose"), Map.entry("22D", "undertakingForm"),
            Map.entry("23", "furtherIdentification"), Map.entry("23B", "expiryType"),
            Map.entry("24E", "deliveryMethod"), Map.entry("30", "issueDate"),
            Map.entry("31C", "issueDate"), Map.entry("31D", "expiryDateAndPlace"),
            Map.entry("32B", "amountAndCurrency"), Map.entry("40C", "applicableRules"),
            Map.entry("45C", "presentationInstructions"), Map.entry("45L", "underlyingTransaction"),
            Map.entry("50", "applicant"), Map.entry("52A", "issuingBank"),
            Map.entry("59", "beneficiary"), Map.entry("77C", "guaranteeDetails"),
            Map.entry("77U", "undertakingTerms"));
    private static final Map<String,String> TARGET_LABELS = Map.ofEntries(
            Map.entry("reference", "LC-Referenz"), Map.entry("guaranteeReference", "Garantiereferenz"), Map.entry("sequence", "Sequenz"),
            Map.entry("relatedReference", "Zugehörige Referenz"), Map.entry("amendmentNumber", "Amendment-Nummer"),
            Map.entry("amendmentDate", "Amendment-Datum"), Map.entry("issueDate", "Ausstellungsdatum"),
            Map.entry("expiryDateAndPlace", "Ablaufdatum und -ort"), Map.entry("newExpiryDateAndPlace", "Neues Ablaufdatum und -ort"),
            Map.entry("amountAndCurrency", "Betrag und Währung"), Map.entry("amountIncrease", "Betragserhöhung"),
            Map.entry("amountDecrease", "Betragsreduzierung"), Map.entry("latestShipmentDate", "Spätester Versand"),
            Map.entry("requiredDocuments", "Erforderliche Dokumente"), Map.entry("changedGoods", "Geänderte Warenbeschreibung"),
            Map.entry("changedDocuments", "Geänderte Dokumente"), Map.entry("changedConditions", "Geänderte Zusatzbedingungen"),
            Map.entry("applicant", "Applicant"), Map.entry("issuingBank", "Issuing Bank"),
            Map.entry("advisingBank", "Advising Bank"), Map.entry("beneficiary", "Beneficiary"),
            Map.entry("messagePurpose", "Nachrichtenzweck"), Map.entry("undertakingForm", "Garantieform"),
            Map.entry("furtherIdentification", "Weitere Identifikation"), Map.entry("expiryType", "Ablaufart"),
            Map.entry("deliveryMethod", "Zustellungsart"), Map.entry("applicableRules", "Anwendbare Regeln"),
            Map.entry("presentationInstructions", "Vorlageanweisungen"), Map.entry("underlyingTransaction", "Grundgeschäft"),
            Map.entry("guaranteeDetails", "Garantiedetails"), Map.entry("undertakingTerms", "Garantiebedingungen"));

    private final Mt700Parser mt700;
    private final Mt707Parser mt707;
    private final LetterOfCreditRepository lcs;
    @org.springframework.beans.factory.annotation.Autowired
    private ExtractionConfidencePolicy confidencePolicy=new ExtractionConfidencePolicy(0.8);
    private final AmendmentRepository amendments;
    private final LetterOfCreditService lcService;
    private final AmendmentService amendmentService;
    private final SwiftImportHistoryService history;
    private final TrainingLearningService learning;

    public SwiftImportService(Mt700Parser p700, Mt707Parser p707, LetterOfCreditRepository l,
                              AmendmentRepository a, LetterOfCreditService ls,
                              AmendmentService as, SwiftImportHistoryService h,TrainingLearningService learning) {
        mt700=p700; mt707=p707; lcs=l; amendments=a; lcService=ls; amendmentService=as; history=h;this.learning=learning;
    }

    public SwiftImportPreview preview(SwiftImportRequest request) {
        return preview(request,true);
    }

    public SwiftImportPreview previewCorrected(SwiftImportRequest request) {
        return preview(request,false);
    }

    private SwiftImportPreview preview(SwiftImportRequest request,boolean applyLearning) {
        String raw=requireRaw(request), type=detect(raw);if(applyLearning)raw=learning.apply(type,raw);
        List<SwiftFieldView> fields=fields(raw,type);
        List<String> errors=new ArrayList<>(), warnings=new ArrayList<>();
        try {
            if (type.equals("MT760")) return previewMt760(raw, fields, errors, warnings);
            if (type.equals("MT707")) {
                var p=mt707.parse(raw); Amendment a=p.amendment();
                Optional<LetterOfCredit> lc=lcs.findByReference(p.lcReference());
                if(lc.isEmpty()) errors.add("Kein Original-Akkreditiv mit dieser Referenz gefunden.");
                if(a.getAmendmentNumber()==null||a.getAmendmentNumber().isBlank()) errors.add("Pflichtfeld :26E: (Amendment-Nummer) fehlt.");
                boolean duplicate=lc.isPresent()&&a.getAmendmentNumber()!=null&&amendments.existsByLetterOfCreditIdAndAmendmentNumber(lc.get().getId(),a.getAmendmentNumber());
                if(duplicate) errors.add("Dieses Amendment wurde bereits importiert.");
                if(a.getAmendmentDate()==null) warnings.add("Feld :30: (Amendment-Datum) fehlt.");
                return new SwiftImportPreview(type,p.lcReference(),null,null,a.getAmountIncrease(),null,a.getAmendmentDate(),a.getNewExpiryDate(),a.getNewExpiryPlace(),a.getAmendmentNumber(),List.of(),duplicate,errors.isEmpty(),errors,warnings,fields);
            }
            LetterOfCredit lc=mt700.parse(raw);
            var beneficiaryReference=de.corporate.lc.swift.BeneficiaryReferenceResolver.resolve(parseFields(raw));
            if(beneficiaryReference.source()!=null) warnings.add(beneficiaryReference.resolved()
                    ? "Begünstigter aus Feld :"+beneficiaryReference.source()+": übernommen. Bitte die Adresse fachlich prüfen; der Originaltext bleibt erhalten."
                    : "Verweis im Begünstigtenfeld auf :"+beneficiaryReference.source()+": konnte nicht eindeutig aufgelöst werden. Bitte die Adresse in :59: ergänzen.");
            if(lc.getExpiryDate()==null) errors.add("Pflichtfeld :31D: (Ablaufdatum) fehlt.");
            if(lc.getAmount()==null||lc.getCurrency()==null) errors.add("Pflichtfeld :32B: (Währung und Betrag) fehlt.");
            if(lc.getApplicant()==null) warnings.add("Feld :50: (Applicant) fehlt.");
            if(lc.getBeneficiary()==null) warnings.add("Feld :59: (Beneficiary) fehlt.");
            boolean duplicate=lcs.existsByReference(lc.getReference());
            if(duplicate) errors.add("Ein Akkreditiv mit dieser Referenz existiert bereits.");
            return new SwiftImportPreview(type,lc.getReference(),lc.getApplicant(),lc.getBeneficiary(),lc.getAmount(),lc.getCurrency(),lc.getIssueDate(),lc.getExpiryDate(),lc.getExpiryPlace(),null,lc.getRequiredDocuments(),duplicate,errors.isEmpty(),errors,warnings,fields);
        } catch(RuntimeException ex) {
            errors.add(readable(ex));
            return new SwiftImportPreview(type,null,null,null,null,null,null,null,null,null,List.of(),false,false,errors,warnings,fields);
        }
    }

    private SwiftImportPreview previewMt760(String raw,List<SwiftFieldView> fields,List<String> errors,List<String> warnings) {
        Map<String,String> values=parseFields(raw);
        require(values,"20","Transaktionsreferenz",errors);
        require(values,"27","Sequenz",errors);
        require(values,"40C","Anwendbare Regeln",errors);
        if(blank(values.get("77C"))&&blank(values.get("77U"))) errors.add("Pflichtfeld :77C: oder :77U: (Garantiebedingungen) fehlt.");
        if(blank(values.get("32B"))) warnings.add("Feld :32B: (Währung und Betrag) fehlt.");
        if(blank(values.get("59"))) warnings.add("Feld :59: (Begünstigter) fehlt.");
        return new SwiftImportPreview("MT760",values.get("20"),values.get("50"),values.get("59"),null,null,null,null,null,null,List.of(),false,errors.isEmpty(),errors,warnings,fields);
    }

    public Object execute(SwiftImportRequest request) {
        String type=detect(request.rawMessage());
        SwiftImportRequest adapted=new SwiftImportRequest(request.filename(),learning.apply(type,request.rawMessage()));
        return executeAdapted(adapted);
    }

    public Object executeCorrected(SwiftImportRequest request) {
        return executeAdapted(request);
    }

    private Object executeAdapted(SwiftImportRequest adapted) {
        SwiftImportPreview p=previewCorrected(adapted);
        if(!p.valid()) { String msg=String.join(" ",p.errors()); history.record(adapted.filename(),p.messageType(),p.reference(),"REJECTED",msg); throw new IllegalArgumentException(msg); }
        if(p.messageType().equals("MT760")) throw new IllegalArgumentException("MT760 wird im Trainingsmodul bestätigt und noch nicht als Akkreditiv importiert.");
        try {
            Object result=p.messageType().equals("MT707")?amendmentService.importMt707(adapted.rawMessage()):lcService.importMt700(adapted.rawMessage());
            history.record(adapted.filename(),p.messageType(),p.reference(),"SUCCESS","Import erfolgreich"); return result;
        } catch(RuntimeException ex) {
            history.record(adapted.filename(),p.messageType(),p.reference(),"REJECTED",readable(ex)); throw ex;
        }
    }

    public String detect(String raw) {
        String upper=raw==null?"":raw.toUpperCase(Locale.ROOT);
        if(upper.matches("(?s).*\\{2:[IO]760.*")||upper.matches("(?s).*\\bMT\\s*760\\b.*")||upper.matches("(?ms).*^:(40C|77C|77U|22D|23H|45L):.*")) return "MT760";
        if(upper.matches("(?s).*\\{2:[IO]707.*")||upper.matches("(?s).*\\bMT\\s*707\\b.*")||upper.matches("(?ms).*^:(26E|31E|45B|46B|47B):.*")) return "MT707";
        return "MT700";
    }

    private List<SwiftFieldView> fields(String raw,String type) {
        List<String[]> parsed=new ArrayList<>(); var matcher=FIELD.matcher(raw.strip());
        while(matcher.find()) parsed.add(new String[]{matcher.group(1),matcher.group(2).trim()});
        Map<String,Long> counts=parsed.stream().collect(java.util.stream.Collectors.groupingBy(x->x[0],java.util.stream.Collectors.counting()));
        Map<String,String> targets=targets(type); List<SwiftFieldView> result=new ArrayList<>();
        for(String[] item:parsed) {
            String code=item[0],value=item[1],target=targets.get(code),inferred=inferTarget(value);
            boolean duplicate=target!=null&&counts.getOrDefault(code,0L)>1;
            boolean misplaced=inferred!=null&&target!=null&&!inferred.equals(target);
            boolean unusual=value.isBlank()||duplicate||misplaced;
            String notice=value.isBlank()?"Das Feld ist leer.":duplicate?"Dieses Kernfeld kommt mehrfach vor und muss geprüft werden.":misplaced?"Der Inhalt deutet eher auf „"+TARGET_LABELS.get(inferred)+"“ hin.":target==null&&inferred!=null?"Der Inhalt könnte zu „"+TARGET_LABELS.get(inferred)+"“ gehören.":null;
            if(target==null&&inferred!=null){target=inferred;unusual=true;}
            if(code.equals("59")&&type.equals("MT700")) {
                var reference=de.corporate.lc.swift.BeneficiaryReferenceResolver.resolve(parseFields(raw));
                if(reference.source()!=null) {
                    unusual=true;
                    notice=reference.resolved()
                            ? "Begünstigtenadresse in :"+reference.source()+": erkannt: "+reference.value()+" — bitte prüfen. Der Feldtext zeigt weiterhin den Originalverweis."
                            : "Verweis auf :"+reference.source()+": nicht eindeutig auflösbar. Bitte Begünstigtenadresse ergänzen.";
                }
            }
            double confidenceScore=unusual||target==null?0.45:0.95;
            String confidence=confidencePolicy.uncertain(confidenceScore)?"LOW":"HIGH";
            String reason=target==null?"Für dieses SWIFT-Feld gibt es in diesem Profil noch kein festes Zielfeld.":unusual?"Inhaltsbasierter Prüfhinweis – manuelle Bestätigung erforderlich.":"Standardzuordnung für "+type+"-Feld :"+code+":";
            result.add(new SwiftFieldView(code,label(type,code),value,target,target==null?"Noch nicht zugeordnet":TARGET_LABELS.get(target),confidence,reason,unusual,notice,confidenceScore,"FIELD_MAPPING_HEURISTIC_V1"));
        }
        return result;
    }

    private Map<String,String> parseFields(String raw){Map<String,String> values=new LinkedHashMap<>();var m=FIELD.matcher(raw.strip());while(m.find())values.put(m.group(1),m.group(2).trim());return values;}
    private Map<String,String> targets(String type){return type.equals("MT760")?MT760_TARGETS:type.equals("MT707")?MT707_TARGETS:MT700_TARGETS;}
    private String label(String type,String code){String special=type.equals("MT760")?MT760_LABELS.get(code):type.equals("MT707")?MT707_LABELS.get(code):MT700_LABELS.get(code);return special!=null?special:COMMON_LABELS.getOrDefault(code,"Weiteres SWIFT-Feld");}
    private void require(Map<String,String> values,String code,String name,List<String> errors){if(blank(values.get(code)))errors.add("Pflichtfeld :"+code+": ("+name+") fehlt.");}
    private boolean blank(String value){return value==null||value.isBlank();}
    private String inferTarget(String value){String text=value==null?"":value.toLowerCase(Locale.ROOT);if(text.matches("(?s).*\\b(applicant|auftraggeber|antragsteller)\\b.*"))return "applicant";if(text.matches("(?s).*\\b(beneficiary|begünstigte[rrn]?)\\b.*"))return "beneficiary";if(text.matches("(?s).*\\b(expiry|expiration|ablaufdatum|valid until)\\b.*"))return "expiryDateAndPlace";if(text.matches("(?s).*\\b(latest shipment|späteste[rn]? versand)\\b.*"))return "latestShipmentDate";if(text.matches("(?s).*\\b(documents required|required documents|vorzulegende dokumente)\\b.*"))return "requiredDocuments";if(text.matches("(?s).*\\b(amount|betrag|total)\\b.*\\b(eur|usd|gbp|chf|jpy)\\b.*"))return "amountAndCurrency";return null;}
    private String requireRaw(SwiftImportRequest r){if(r==null||r.rawMessage()==null||r.rawMessage().isBlank())throw new IllegalArgumentException("Bitte eine SWIFT-Nachricht auswählen oder einfügen.");return r.rawMessage();}
    private String readable(RuntimeException ex){return ex.getMessage()==null?"SWIFT-Nachricht konnte nicht gelesen werden.":ex.getMessage();}
}
