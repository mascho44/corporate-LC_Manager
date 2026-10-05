package de.corporate.lc.check.service;

import de.corporate.lc.check.api.RuleDefinition;
import java.util.List;

/** Append a new version when semantics change; never relabel a heuristic as ICC compliance. */
public final class RuleCatalog {
    public static final String VERSION=RulePacks.VERSION;
    private static final String ICC="https://library.iccwbo.org/tfb/tfb-iccrules.htm";
    private static final List<RuleDefinition> RULES=List.of(
        new RuleDefinition("LC_AMOUNT_LIMIT","1.0","Rechnungsbetrag und LC-Höchstbetrag","LC-Bedingung :32B: / :39A:","Vergleicht Rechnungsbetrag und Währung mit dem LC-Höchstbetrag einschließlich erfasster Toleranz.","Metadatenprüfung; ersetzt keine vollständige Rechnungsprüfung nach UCP 600 / ISBP 821.",ICC),
        new RuleDefinition("LC_EXPIRY_DATE","1.0","Dokumentdatum und LC-Verfall","LC-Bedingung :31D:","Meldet ein erfasstes Dokumentdatum nach dem LC-Verfallsdatum.","Dokumentdatum ist nicht das Präsentationsdatum. Keine vollständige Prüfung der Präsentationsfrist.",ICC),
        new RuleDefinition("LC_SHIPMENT_DATE","1.0","Versanddatum und LC-Versandfrist","LC-Bedingung :44C:","Vergleicht das erfasste Transportdokumentdatum mit dem letzten Versandtermin.","Erfasstes Dokumentdatum kann vom maßgeblichen Versanddatum abweichen; Original und Transportart fachlich prüfen.",ICC),
        new RuleDefinition("LC_REQUIRED_DOCUMENT","1.0","Gefordertes Dokument vorhanden","LC-Dokumentenanforderung :46A:","Vergleicht den zugeordneten Dokumenttyp mit der LC-Dokumentenanforderung.","Vorhandensein allein bestätigt weder Originalstatus noch Vollständigkeit oder UCP-/ISBP-Konformität.",ICC)
    );
    private RuleCatalog(){}
    public static List<RuleDefinition> definitions(){return RULES;}
    public static RuleDefinition forFinding(String code){
        if ("UCP18_INVOICE_CURRENCY".equals(code)) return RulePacks.INVOICE_CURRENCY;
        String id=switch(code==null?"":code){
            case "INVOICE_AMOUNT_EXCEEDED","INVOICE_AMOUNT_OK","CURRENCY_MISMATCH"->"LC_AMOUNT_LIMIT";
            case "DOCUMENT_AFTER_EXPIRY"->"LC_EXPIRY_DATE";
            case "SHIPMENT_DATE_EXCEEDED","SHIPMENT_DATE_OK","AIR_SHIPMENT_DATE_EXCEEDED","AIR_SHIPMENT_DATE_OK","CMR_SHIPMENT_DATE_EXCEEDED","CMR_SHIPMENT_DATE_OK"->"LC_SHIPMENT_DATE";
            case "MISSING_DOCUMENT","DOCUMENT_PRESENT"->"LC_REQUIRED_DOCUMENT";
            default->null;
        };
        if(id!=null)return RULES.stream().filter(rule->rule.id().equals(id)).findFirst().orElseThrow();
        return new RuleDefinition(code==null?"UNKNOWN":code,"1.0","Interne Vorprüfung / Kontext","Interne Prüflogik; keine zugeordnete ICC-Einzelregel","Die konkrete Auswertung und Vergleichsgrundlage stehen im Befund.","Quellenzuordnung fachlich noch nicht freigegeben. Keine Aussage über vollständige UCP-/ISBP-Konformität.",null);
    }
}
