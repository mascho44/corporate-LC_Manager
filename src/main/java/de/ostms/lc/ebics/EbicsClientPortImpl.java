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
}
