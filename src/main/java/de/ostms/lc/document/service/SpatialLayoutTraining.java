package de.ostms.lc.document.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.ostms.lc.tenant.domain.TenantContext;
import java.util.*;
@Service
public class SpatialLayoutTraining {
 private final JdbcTemplate jdbc;private final ObjectMapper json;
 public SpatialLayoutTraining(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
 public record Confirmation(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=100) String profile,@jakarta.validation.Valid @jakarta.validation.constraints.NotNull DocumentMetadataTraining.Confirmation values){}
 @Transactional
 public void confirm(OcrEvidence evidence,Confirmation request,String actor)throws Exception{
  if(evidence==null)throw new IllegalArgumentException("Keine Positionsdaten vorhanden.");
  var labels=new LinkedHashMap<String,List<String>>();var expected=request.values();
  var targets=new LinkedHashMap<String,Object>();targets.put("reference",expected.metadata().reference());targets.put("documentNumber",expected.metadata().documentNumber());targets.put("documentDate",expected.documentDate());targets.put("amount",expected.metadata().amount());targets.put("currency",expected.metadata().currency());
  var baseline=SpatialMetadata.detect(evidence);baseline.forEach((key,field)->{if(targets.get(key)!=null&&equal(MetadataFieldAnchors.parse(key,field.value()),targets.get(key)))labels.put(key,List.of(field.evidence().split(": ",2)[0]));});
  for(var target:targets.entrySet()){
   if(labels.containsKey(target.getKey()))continue;
   if(target.getValue()==null)continue;var options=new LinkedHashSet<String>();int occurrences=0;
   for(var word:evidence.words()){
    Object parsed=MetadataFieldAnchors.parse(target.getKey(),word.text());if(parsed==null||!equal(parsed,target.getValue()))continue;
    if(++occurrences>8)throw new IllegalArgumentException("Zu viele gleiche Feldwerte; keine eindeutige räumliche Zuordnung.");
    // Nearest label on the same row or immediately above, in the same column.
    var neighbors=evidence.words().stream().filter(w->w.page()==word.page()&&((Math.abs(w.top()-word.top())<word.height()/2&&w.left()+w.width()<=word.left()&&word.left()-w.left()<600)||(w.top()<word.top()&&word.top()-w.top()<word.height()*4&&Math.abs(w.left()-word.left())<200))).sorted(Comparator.comparingInt(OcrEvidence.Word::top).thenComparingInt(OcrEvidence.Word::left)).toList();
    var row=neighbors.stream().filter(w->w.text().endsWith(":")).max(Comparator.comparingInt(OcrEvidence.Word::top).thenComparingInt(OcrEvidence.Word::left));if(row.isEmpty())continue;
    var end=row.get();String label=neighbors.stream().filter(w->Math.abs(w.top()-end.top())<end.height()/2&&w.left()<=end.left()&&end.left()-w.left()<350).map(OcrEvidence.Word::text).reduce((a,b)->a+" "+b).orElse("").replace(":","").toLowerCase(Locale.ROOT);
    if(label.matches("[\\p{L} ./'()-]{2,80}"))options.add(label);
   }
   if(options.size()==1)labels.put(target.getKey(),List.of(options.iterator().next()));
  }
  var detected=SpatialMetadata.detect(evidence,labels);
  labels.keySet().removeIf(key->!detected.containsKey(key)||!equal(MetadataFieldAnchors.parse(key,detected.get(key).value()),targets.get(key)));
  if(labels.size()<2)throw new IllegalArgumentException("Mindestens zwei eindeutige bestätigte Feldzuordnungen mit beschrifteten Ankern erforderlich.");
  jdbc.update("insert into document_spatial_layout(id,tenant_id,profile,labels_json,confirmed_by) values (?,?,?,?,?)",UUID.randomUUID(),TenantContext.currentId(),request.profile().strip(),json.writeValueAsString(labels),actor);
 }
 @Transactional(readOnly=true)
 public Map<String,SpatialMetadata.Field> suggest(OcrEvidence evidence,String profile)throws Exception{
  if(profile==null||profile.isBlank())return SpatialMetadata.detect(evidence);
  if(profile.length()>100)throw new IllegalArgumentException("Layoutprofil zu lang.");
  var patterns=jdbc.queryForList("select distinct labels_json from document_spatial_layout where tenant_id=? and profile=? and active=true limit 2",String.class,TenantContext.currentId(),profile.strip());
  if(patterns.size()>1)throw new IllegalArgumentException("Widersprüchliche Layoutbestätigungen. Bitte Profil fachlich prüfen.");
  if(patterns.isEmpty())return SpatialMetadata.detect(evidence);
  return SpatialMetadata.detect(evidence,json.readValue(patterns.get(0),new TypeReference<Map<String,List<String>>>(){}));
 }
 @Transactional(readOnly=true)
 public List<Map<String,Object>> layouts(){
  return jdbc.queryForList("select id,profile,active,labels_json,confirmed_by,confirmed_at from document_spatial_layout where tenant_id=? order by profile,confirmed_at desc",TenantContext.currentId());
 }
 @Transactional
 public void updateLayout(UUID id,String profile,boolean active){
  if(profile==null||profile.isBlank()||profile.strip().length()>100)throw new IllegalArgumentException("Profilname erforderlich, maximal 100 Zeichen.");
  int changed=jdbc.update("update document_spatial_layout set profile=?,active=? where id=? and tenant_id=?",profile.strip(),active,id,TenantContext.currentId());
  if(changed!=1)throw new NoSuchElementException("Layout nicht gefunden.");
 }
 private static boolean equal(Object a,Object b){return a instanceof java.math.BigDecimal x&&b instanceof java.math.BigDecimal y?x.compareTo(y)==0:Objects.equals(a,b);}
}
