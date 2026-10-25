package de.ostms.lc.ebics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** The server calls this URL, so it must not become a way to reach internal services: https only, no credentials, no local/private targets unless allow-listed. */
@Component
public class EbicsUrlPolicy {
 private final Set<String> allowed;
 public EbicsUrlPolicy(@Value("${app.ebics.allowed-hosts:}") String allowedHosts){
  allowed=Arrays.stream(allowedHosts.split(",")).map(s->s.trim().toLowerCase(Locale.ROOT)).filter(s->!s.isEmpty()).collect(Collectors.toSet());
 }
 public URL check(String value){
  try{
   if(value==null||value.length()>500)throw new IllegalArgumentException("Bitte eine gültige EBICS-URL angeben.");
   URI uri=new URI(value.trim());
   String host=uri.getHost()==null?"":uri.getHost().toLowerCase(Locale.ROOT);
   if(!"https".equalsIgnoreCase(uri.getScheme())||host.isEmpty()||uri.getUserInfo()!=null||uri.getFragment()!=null)throw new IllegalArgumentException("Die EBICS-URL muss mit https:// beginnen und darf keine Zugangsdaten enthalten.");
   if(!allowed.contains(host)){
    for(InetAddress address:InetAddress.getAllByName(host))
     if(address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isLinkLocalAddress()||address.isSiteLocalAddress()||address.isMulticastAddress()||isUniqueLocalV6(address))
      throw new IllegalArgumentException("Die EBICS-URL zeigt auf eine interne Adresse. Interne Hosts müssen in EBICS_ALLOWED_HOSTS freigegeben werden.");
   }
   return uri.toURL();
  }catch(IllegalArgumentException e){throw e;}
  catch(Exception e){throw new IllegalArgumentException("Die EBICS-URL ist ungültig oder der Host nicht auflösbar.");}
 }
 private static boolean isUniqueLocalV6(InetAddress a){byte[] b=a.getAddress();return b.length==16&&(b[0]&0xfe)==0xfc;}
}
