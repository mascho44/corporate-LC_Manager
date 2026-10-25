package de.ostms.lc.ebics;

import org.kopi.ebics.client.Bank;
import org.kopi.ebics.client.Partner;
import org.kopi.ebics.client.User;
import org.kopi.ebics.session.Product;
import org.springframework.stereotype.Component;
import java.net.URL;

@Component
public class EbicsClientPortImpl implements EbicsClientPort {
    private final EbicsClientFactory clients;
    public EbicsClientPortImpl(EbicsClientFactory clients){this.clients=clients;}
    private static Product product(){return new Product("CorporateLCManager","de",null);}

    @Override public Keys createKeys(URL bankUrl,String hostId,String partnerId,String userId)throws Exception{
        // EbicsClient registers the BouncyCastle provider in a static block; touch it once before generating keys.
        clients.newClient();
        Bank bank=new Bank(bankUrl,hostId,hostId);
        Partner partner=new Partner(bank,partnerId);
        User user=new User(partner,userId,"CorporateLCManager","ebics@lcm.local","DE","CorporateLCManager",EbicsSerialisierung.SCHLUESSEL_PASSWORT);
        return new Keys(user,partner,bank);
    }
    @Override public void sendIni(User user)throws Exception{var c=clients.newClient();c.createUserDirectories(user);c.sendINIRequest(user,product());}
    @Override public void sendHia(User user)throws Exception{var c=clients.newClient();c.createUserDirectories(user);c.sendHIARequest(user,product());}
    @Override public void fetchBankKeys(User user)throws Exception{var c=clients.newClient();c.createUserDirectories(user);c.sendHPBRequest(user,product());}

    @Override public byte[] downloadTradeMessage(User user,String messageName)throws Exception{
        var c=clients.newClient();c.createUserDirectories(user);
        var target=java.nio.file.Files.createTempFile("ebics-trc-",".dat").toFile();
        try{
            // The order type only names the local trace files; with a service name the request is sent as BTD.
            var params=tradeParams(messageName);
            c.fetchFile(target,user,product(),org.kopi.ebics.session.OrderType.STA,params,false);
            long size=target.length();
            if(size>MAX_MESSAGE_BYTES)throw new IllegalStateException("Die Nachricht der Bank ist zu groß ("+size+" Bytes).");
            return java.nio.file.Files.readAllBytes(target.toPath());
        }catch(org.kopi.ebics.exception.NoDownloadDataAvailableException none){return null;}
        finally{java.nio.file.Files.deleteIfExists(target.toPath());}
    }
    static final long MAX_MESSAGE_BYTES=512*1024;

    /**
     * H005 only allows lower-case message names (pattern [a-z.0-9]*, at most 10) and a version of at least two digits.
     * The simulator reads the name case-insensitively, so MT700 is requested as "mt700".
     */
    static org.kopi.ebics.client.EbicsDownloadParams tradeParams(String messageName){
        return new org.kopi.ebics.client.EbicsDownloadParams("TRC",null,null,messageName.toLowerCase(java.util.Locale.ROOT),"00",null,null,null);
    }
}
