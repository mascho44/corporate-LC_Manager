package de.ostms.lc.lc.service;
import java.util.*;

/** The fixed workflow templates. A step may require four eyes (completed by someone other than the previous step's actor) and may go to the approval team. */
public final class WorkflowTemplates {
 public record Step(String key,String title,int dueDays,boolean fourEyes,boolean approvalTeam){}
 public record Template(String id,String title,String description,List<Step> steps){}
 private static final Map<String,Template> ALL=new LinkedHashMap<>();
 static {
  add(new Template("NEW_LC","Neue Akte prüfen","Akkreditiv neu erfasst: Stammdaten prüfen, inhaltlich prüfen, freigeben.",List.of(
   new Step("CAPTURE","Akte erfassen und Stammdaten prüfen",2,false,false),
   new Step("REVIEW","Inhaltlich prüfen",2,false,false),
   new Step("APPROVE","Freigeben (Vier-Augen)",1,true,true))));
  add(new Template("AMENDMENT","Änderung (MT707) bearbeiten","Eingegangene Änderung prüfen und bestätigen.",List.of(
   new Step("REVIEW","Änderung prüfen",2,false,false),
   new Step("CONFIRM","Änderung bestätigen",1,false,false))));
  add(new Template("GUARANTEE","Garantie bearbeiten","Garantie erfassen, prüfen und freigeben.",List.of(
   new Step("CAPTURE","Garantie erfassen",2,false,false),
   new Step("REVIEW","Garantie prüfen",2,false,false),
   new Step("APPROVE","Freigeben (Vier-Augen)",1,true,true))));
  add(new Template("DOCUMENT_CHECK","Dokumentenprüfung","Dokumente prüfen, Abweichungen klären, Prüfung freigeben.",List.of(
   new Step("CHECK","Dokumente prüfen",3,false,false),
   new Step("CLARIFY","Abweichungen klären",3,false,false),
   new Step("APPROVE","Prüfung freigeben (Vier-Augen)",1,true,true))));
 }
 private static void add(Template t){ALL.put(t.id(),t);}
 private WorkflowTemplates(){}
 public static Collection<Template> all(){return ALL.values();}
 public static Template get(String id){
  var t=id==null?null:ALL.get(id);
  if(t==null)throw new IllegalArgumentException("Unbekannte Workflow-Vorlage.");
  return t;
 }
}
