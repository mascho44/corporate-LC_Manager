package de.ostms.lc.sla;

import java.util.List;

/** The activities offered as a starting point. Events are audit actions on the file, so no other module has to report anything. */
public final class SlaCatalog {
 private SlaCatalog(){}
 public record Activity(String key,String name,String nameEn,List<String> startActions,List<String> stopActions,int defaultDays){}
 public static final List<Activity> ACTIVITIES=List.of(
  new Activity("FIRST_PROCESSING","Erstbearbeitung der Akte","First processing of the file",List.of("LC_IMPORTED","SWIFT_IMPORTED","LC_CREATED"),List.of("LC_ASSIGNED"),1),
  new Activity("DOCUMENT_EXAMINATION","Dokumentenprüfung","Document examination",List.of("DOCUMENT_UPLOADED","DOCUMENT_BATCH_UPLOADED","DOCUMENT_ARCHIVE_UPLOADED","DOCUMENT_INBOX_ATTACHED"),List.of("DOCUMENT_CHECK_REPORT_CREATED"),5),
  new Activity("AMENDMENT","Änderung (MT707) bearbeiten","Process amendment (MT707)",List.of("MT707_IMPORTED"),List.of("AMENDMENT_ACCEPTED","AMENDMENT_REJECTED"),2),
  new Activity("DISCREPANCY_NOTICE","Unstimmigkeitsmeldung erstellen","Create notice of discrepancies",List.of("DOCUMENT_DISCREPANCY_CONFIRMED"),List.of("DISCREPANCY_NOTICE_CREATED"),2),
  new Activity("INBOX_ASSIGNMENT","Dokument im Posteingang zuordnen","Assign document in the inbox",List.of("INBOX_RECEIVED"),List.of("INBOX_ASSIGNED"),1),
  new Activity("TASK","Aufgabe erledigen","Complete task",List.of("LC_TASK_CREATED"),List.of("LC_TASK_COMPLETED"),3));
 /** Inbox clocks belong to documents, not to files: they are evaluated from the open inbox items, not from the audit events of a file. */
 public static final String INBOX="INBOX_ASSIGNMENT";
 public static Activity byKey(String key){return ACTIVITIES.stream().filter(a->a.key().equals(key)).findFirst().orElse(null);}
}
