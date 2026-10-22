package de.ostms.lc.webhook;

import java.util.List;
import java.util.Optional;

/** The named domain events. Each is produced from audit actions, so no module has to publish anything itself. */
public enum DomainEvent {
 DOCUMENT_UPLOADED("DocumentUploaded","Dokument hochgeladen","Document uploaded",List.of("DOCUMENT_UPLOADED","DOCUMENT_BATCH_UPLOADED","DOCUMENT_ARCHIVE_UPLOADED","DOCUMENT_INBOX_ATTACHED","INBOX_RECEIVED")),
 OCR_COMPLETED("OcrCompleted","Texterkennung erneut abgeschlossen","Text recognition completed again",List.of("DOCUMENT_RE_RECOGNIZED","DOCUMENT_INBOX_EXTRACTION_RETRIED")),
 VALIDATION_COMPLETED("ValidationCompleted","Dokumentenprüfung abgeschlossen","Document check completed",List.of("DOCUMENT_CHECK_REPORT_CREATED")),
 DISCREPANCY_DETECTED("DiscrepancyDetected","Abweichung bestätigt","Discrepancy confirmed",List.of("DOCUMENT_DISCREPANCY_CONFIRMED")),
 APPROVAL_RECORDED("ApprovalRecorded","Freigabe erfasst","Approval recorded",List.of("DOCUMENT_DRAFT_APPROVAL_RECORDED")),
 LC_CREATED("LcCreated","Akte angelegt","File created",List.of("LC_CREATED","LC_IMPORTED","SWIFT_IMPORTED")),
 AMENDMENT_RECEIVED("AmendmentReceived","Änderung (MT707) eingegangen","Amendment (MT707) received",List.of("MT707_IMPORTED")),
 AMENDMENT_DECIDED("AmendmentDecided","Änderung angenommen oder abgelehnt","Amendment accepted or rejected",List.of("AMENDMENT_ACCEPTED","AMENDMENT_REJECTED")),
 EBICS_ORDER_FAILED("EbicsOrderFailed","EBICS-Auftrag fehlgeschlagen","EBICS order failed",List.of("EBICS_FETCH_FAILED","EBICS_KEYS_FAILED","EBICS_BANK_KEYS_FAILED"));

 private final String eventName,german,english;private final List<String> actions;
 DomainEvent(String eventName,String german,String english,List<String> actions){this.eventName=eventName;this.german=german;this.english=english;this.actions=actions;}
 public String eventName(){return eventName;} public String german(){return german;} public String english(){return english;} public List<String> actions(){return actions;}

 /** Failure events are reported whatever the success flag says (some are recorded as successful audit entries); every other event needs a successful action. */
 public static Optional<DomainEvent> forAction(String action,boolean successful){
  if(action==null)return Optional.empty();
  for(DomainEvent event:values())if(event.actions.contains(action)&&(successful||event==EBICS_ORDER_FAILED))return Optional.of(event);
  return Optional.empty();
 }
 public static Optional<DomainEvent> byName(String name){for(DomainEvent event:values())if(event.eventName.equals(name))return Optional.of(event);return Optional.empty();}
}
