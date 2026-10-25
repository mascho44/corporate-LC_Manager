package de.ostms.lc.document.service;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PreviewCacheTest {
 @Test void evictsLeastRecentlyUsedWhenOverTheByteBudget(){
  var cache=new PreviewCache(1000);
  cache.put("a",new byte[200]);cache.put("b",new byte[200]);cache.put("c",new byte[200]);cache.put("d",new byte[200]);
  cache.get("a");
  cache.put("e",new byte[200]);cache.put("f",new byte[200]);
  assertThat(cache.size()).isLessThanOrEqualTo(1000);
  assertThat(cache.get("a")).isNotNull();
  assertThat(cache.get("b")).isNull();
 }
 @Test void neverStoresOversizedEntriesAndReplacesSameKey(){
  var cache=new PreviewCache(1000);
  cache.put("big",new byte[600]);assertThat(cache.get("big")).isNull();
  cache.put("k",new byte[100]);cache.put("k",new byte[50]);
  assertThat(cache.size()).isEqualTo(50);assertThat(cache.count()).isEqualTo(1);
 }
 @Test void keyDependsOnContentPageAndSize(){
  byte[] one={1,2,3},two={1,2,4};
  assertThat(PdfPagePreviewService.cacheKey(one,1,720)).isNotEqualTo(PdfPagePreviewService.cacheKey(two,1,720))
   .isNotEqualTo(PdfPagePreviewService.cacheKey(one,2,720)).isNotEqualTo(PdfPagePreviewService.cacheKey(one,1,1400))
   .isEqualTo(PdfPagePreviewService.cacheKey(one.clone(),1,720));
 }
}
