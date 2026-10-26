package de.ostms.lc.savedview;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SavedViewServiceTest {
 private final SavedViewRepository views=mock(SavedViewRepository.class);
 private final SavedViewService service=new SavedViewService(views,JsonMapper.builder().build());

 @Test void savesTheKnownFiltersOnly() {
  when(views.save(any())).thenAnswer(i->i.getArgument(0));when(views.nameTaken(anyString(),anyString(),any())).thenReturn(false);
  var map=new LinkedHashMap<String,String>();map.put("owner","mine");map.put("sort","expiry");map.put("evil","<script>");map.put("search","");
  var view=service.create("Anna"," Meine Eilfälle ",true,map);
  assertThat(view.name()).isEqualTo("Meine Eilfälle");
  assertThat(view.shared()).isTrue();assertThat(view.mine()).isTrue();
  assertThat(view.filters()).containsOnlyKeys("owner","sort").containsEntry("owner","mine");
 }
 @Test void rejectsBadInput() {
  assertThatThrownBy(()->service.create("a"," ",false,Map.of("owner","mine"))).hasMessageContaining("Namen");
  assertThatThrownBy(()->service.create("a","x".repeat(61),false,Map.of("owner","mine"))).hasMessageContaining("Namen");
  assertThatThrownBy(()->service.create("a","leer",false,Map.of())).hasMessageContaining("kein Filter");
  assertThatThrownBy(()->service.create("a","leer",false,Map.of("evil","x"))).hasMessageContaining("kein Filter");
  assertThatThrownBy(()->service.create("a","lang",false,Map.of("search","x".repeat(201)))).hasMessageContaining("zu lang");
 }
 @Test void limitsTheViewsPerPersonAndDuplicateNames() {
  when(views.countOf("anna")).thenReturn(30L);
  assertThatThrownBy(()->service.create("anna","neu",false,Map.of("owner","mine"))).hasMessageContaining("bereits 30");
  when(views.countOf("anna")).thenReturn(1L);when(views.nameTaken("anna","doppelt",null)).thenReturn(true);
  assertThatThrownBy(()->service.create("anna","doppelt",false,Map.of("owner","mine"))).hasMessageContaining("gibt es bereits");
 }
 @Test void onlyTheOwnerChangesAView() {
  var foreign=new SavedView("ben","Fremd",true,"{\"owner\":\"mine\"}");
  when(views.findById(any())).thenReturn(Optional.of(foreign));
  assertThatThrownBy(()->service.update("anna",UUID.randomUUID(),"Fremd",true,Map.of("owner","mine"))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
 }
 @Test void deletingNeedsOwnershipOrAnAdministratorForSharedViews() {
  var shared=new SavedView("ben","Geteilt",true,"{\"owner\":\"mine\"}");var privateView=new SavedView("ben","Privat",false,"{\"owner\":\"mine\"}");
  when(views.findById(any())).thenReturn(Optional.of(shared));
  assertThatThrownBy(()->service.delete("anna",UUID.randomUUID(),false)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  service.delete("anna",UUID.randomUUID(),true);verify(views).delete(shared);
  when(views.findById(any())).thenReturn(Optional.of(privateView));
  assertThatThrownBy(()->service.delete("anna",UUID.randomUUID(),true)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  service.delete("BEN",UUID.randomUUID(),false);verify(views).delete(privateView);
 }
 @Test void listShowsWhoseViewItIs() {
  when(views.visibleTo("anna")).thenReturn(List.of(new SavedView("anna","Meine",false,"{\"sort\":\"expiry\"}"),new SavedView("ben","Geteilt",true,"{\"owner\":\"mine\"}")));
  var list=service.list("anna");
  assertThat(list).extracting(SavedViewService.View::mine).containsExactly(true,false);
  assertThat(list.get(0).filters()).containsEntry("sort","expiry");
 }
}
