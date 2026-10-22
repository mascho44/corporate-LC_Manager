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
  new Activity("TASK","Aufgabe erledigen","Complete task",List.of("LC_TASK_CREATED"),List.of("LC_TASK_COMPLETED"),3));
 public static Activity byKey(String key){return ACTIVITIES.stream().filter(a->a.key().equals(key)).findFirst().orElse(null);}
}
