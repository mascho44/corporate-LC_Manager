package de.ostms.lc.ebics;

import org.kopi.ebics.client.Bank;
import org.kopi.ebics.client.Partner;
import org.kopi.ebics.client.User;
import java.net.URL;

/** Thin seam in front of the third-party EBICS client so services can be tested without network access. */
public interface EbicsClientPort {
    record Keys(User user, Partner partner, Bank bank) {}
    Keys createKeys(URL bankUrl, String hostId, String partnerId, String userId) throws Exception;
    void sendIni(User user) throws Exception;
    void sendHia(User user) throws Exception;
    void fetchBankKeys(User user) throws Exception;
}
