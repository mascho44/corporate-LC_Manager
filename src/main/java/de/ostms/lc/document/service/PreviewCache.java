package de.ostms.lc.document.service;
import java.util.LinkedHashMap;
import java.util.Map;

/** In-memory LRU cache for rendered page previews, bounded by total bytes. Never written to disk or sent with cache headers. */
final class PreviewCache {
 private final long maxBytes;private long bytes;
 private final Map<String,byte[]> entries=new LinkedHashMap<>(16,.75f,true);
 PreviewCache(long maxBytes){this.maxBytes=maxBytes;}
 synchronized byte[] get(String key){return entries.get(key);}
 synchronized void put(String key,byte[] value){
  if(value==null||value.length>maxBytes/4)return;
  var old=entries.put(key,value);if(old!=null)bytes-=old.length;
  bytes+=value.length;
  var it=entries.entrySet().iterator();
  while(bytes>maxBytes&&it.hasNext()){var e=it.next();if(e.getKey().equals(key))continue;bytes-=e.getValue().length;it.remove();}
 }
 synchronized long size(){return bytes;}
 synchronized int count(){return entries.size();}
}
