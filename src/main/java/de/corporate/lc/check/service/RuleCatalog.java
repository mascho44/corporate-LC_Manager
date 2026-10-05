package de.corporate.lc.check.service;

import de.corporate.lc.check.api.RuleDefinition;
import java.util.List;

/** Append a new version when semantics change; never relabel a heuristic as ICC compliance. */
public final class RuleCatalog {
    public static final String VERSION="2026-10-05.3";
    private static final List<RuleDefinition> RULES=List.of(
        new RuleDefinition("LC_AMOUNT_LIMIT","1.0","Rechnungsbetrag und LC-Höchstbetrag","LC-Bedingung :32B: / :39A:","Vergleicht Rechnungsbetrag und Währung mit dem LC-Höchstbetrag einschließlich erfasster Toleranz.","Interne Metadatenprüfung; keine vollständige fachliche Konformitätsprüfung.",null),
        new RuleDefinition("LC_EXPIRY_DATE","1.0","Dokumentdatum und LC-Verfall","LC-Bedingung :31D:","Meldet ein erfasstes Dokumentdatum nach dem LC-Verfallsdatum.","Dokumentdatum ist nicht das Präsentationsdatum. Keine vollständige Prüfung der Präsentationsfrist.",null),
        new RuleDefinition("LC_SHIPMENT_DATE","1.0","Versanddatum und LC-Versandfrist","LC-Bedingung :44C:","Vergleicht das erfasste Transportdokumentdatum mit dem letzten Versandtermin.","Erfasstes Dokumentdatum kann vom maßgeblichen Versanddatum abweichen; Original und Transportart fachlich prüfen.",null),
        new RuleDefinition("LC_REQUIRED_DOCUMENT","1.0","Gefordertes Dokument vorhanden","LC-Dokumentenanforderung :46A:","Vergleicht den zugeordneten Dokumenttyp mit der LC-Dokumentenanforderung.","Vorhandensein allein bestätigt weder Originalstatus noch Vollständigkeit oder fachliche Konformität.",null)
    );
    private RuleCatalog(){}
    public static List<RuleDefinition> definitions(){return RULES;}
    public static RuleDefinition forFinding(String code){
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
