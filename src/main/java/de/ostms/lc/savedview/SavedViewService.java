package de.ostms.lc.savedview;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.*;

/**
 * Saved views of the file list. Everybody sees their own and the shared ones of the tenant; only the owner changes or deletes a view (a tenant administrator may
 * delete a shared one). The stored filters are checked: only the known keys with short text values, so a view can never carry anything else into the page.
 */
@Service
public class SavedViewService {
 static final int MAX_PER_USER=30;
 static final List<String> KEYS=List.of("search","traffic","sla","owner","company","sort","quick");
 public record View(UUID id,String name,boolean shared,String owner,boolean mine,Map<String,String> filters){}
 private final SavedViewRepository views;private final ObjectMapper json;
 public SavedViewService(SavedViewRepository views,ObjectMapper json){this.views=views;this.json=json;}

 private View view(SavedView v,String user){return new View(v.getId(),v.getName(),v.isShared(),v.getOwnerUsername(),v.getOwnerUsername().equalsIgnoreCase(user),read(v.getFiltersJson()));}
 private Map<String,String> read(String text){
  try{Map<String,String> out=new LinkedHashMap<>();JsonNode node=json.readTree(text);for(String key:KEYS)if(node.hasNonNull(key)&&!node.get(key).asString().isEmpty())out.put(key,node.get(key).asString());return out;}
  catch(RuntimeException broken){return Map.of();}
 }
 static String cleanName(String name){
  String clean=name==null?"":name.strip();
  if(clean.isEmpty()||clean.length()>60)throw new IllegalArgumentException("Bitte einen Namen (höchstens 60 Zeichen) angeben, zum Beispiel „Meine Garantien“.");
  return clean;
 }
 private String cleanFilters(Map<String,String> filters){
  Map<String,String> out=new LinkedHashMap<>();
  for(String key:KEYS){String value=filters==null?null:filters.get(key);if(value==null||value.isEmpty())continue;if(value.length()>200)throw new IllegalArgumentException("Ein Filterwert ist zu lang.");out.put(key,value);}
  if(out.isEmpty())throw new IllegalArgumentException("Es ist kein Filter und keine Sortierung gesetzt, die gespeichert werden könnten.");
  return json.writeValueAsString(out);
 }

 @Transactional(readOnly=true) public List<View> list(String user){return views.visibleTo(user).stream().map(v->view(v,user)).toList();}

 @Transactional public View create(String user,String name,boolean shared,Map<String,String> filters){
  String clean=cleanName(name);String stored=cleanFilters(filters);
  if(views.countOf(user)>=MAX_PER_USER)throw new IllegalArgumentException("Es sind bereits "+MAX_PER_USER+" Ansichten gespeichert. Bitte nicht mehr benötigte löschen.");
  if(views.nameTaken(user,clean,null))throw new IllegalArgumentException("Eine Ansicht mit diesem Namen gibt es bereits.");
  return view(views.save(new SavedView(user,clean,shared,stored)),user);
 }
 @Transactional public View update(String user,UUID id,String name,boolean shared,Map<String,String> filters){
  var found=views.findById(id).orElseThrow(()->new NoSuchElementException("Ansicht nicht gefunden."));
  if(!found.getOwnerUsername().equalsIgnoreCase(user))throw new org.springframework.security.access.AccessDeniedException("Nur die Person, die die Ansicht angelegt hat, kann sie ändern.");
  String clean=cleanName(name);
  if(views.nameTaken(user,clean,id))throw new IllegalArgumentException("Eine Ansicht mit diesem Namen gibt es bereits.");
  found.update(clean,shared,cleanFilters(filters));
  return view(views.save(found),user);
 }
 /** The owner deletes their view; with `mayManageShared` also a shared view of somebody else. */
 @Transactional public void delete(String user,UUID id,boolean mayManageShared){
  var found=views.findById(id).orElseThrow(()->new NoSuchElementException("Ansicht nicht gefunden."));
  boolean owner=found.getOwnerUsername().equalsIgnoreCase(user);
  if(!owner&&!(mayManageShared&&found.isShared()))throw new org.springframework.security.access.AccessDeniedException("Diese Ansicht kann nur die Person löschen, die sie angelegt hat.");
  views.delete(found);
 }
}
