package de.ostms.lc.ebics;

import org.kopi.ebics.client.Bank;
import org.kopi.ebics.client.Partner;
import org.kopi.ebics.client.User;
import org.kopi.ebics.interfaces.PasswordCallback;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * Wandelt Bank/Partner/User des ebics-java-client in Byte-Arrays um (fuer die verschluesselten Blob-Spalten
 * auf {@link EbicsConnection}) und zurueck. Bank wird per voller Standard-Java-Serialisierung
 * gespeichert ({@code oos.writeObject(this)} in {@code Bank.save()}), Partner/User dagegen ueber
 * handgeschriebene Feldsequenzen ohne Rueckverweis auf ihr Elternobjekt im Stream - deshalb brauchen deren
 * Rekonstruktions-Konstruktoren das bereits wiederhergestellte Elternobjekt als Java-Parameter.
 */
final class EbicsSerialisierung {

    /** Stored blobs are decrypted and authenticated first; the filter additionally restricts what may be instantiated. */
    private static final java.io.ObjectInputFilter FILTER=java.io.ObjectInputFilter.Config.createFilter("org.kopi.ebics.**;java.lang.*;java.util.*;java.net.URL;java.math.*;java.security.**;javax.crypto.**;sun.security.**;org.bouncycastle.**;!*");


    /**
     * KeyManagement.sendHPB() der Library schreibt nach jedem Bankschluessel-Abruf unbedingt ein lokales
     * PKCS12-Keystore-File mit den frisch geladenen Bankzertifikaten und braucht dafuer zwingend ein
     * PasswordCallback ungleich null (sonst NullPointerException) - dieses File landet nur im ephemeren
     * Wurzelverzeichnis von {@link EbicsClientFactory} und wird von uns nie gelesen (wir persistieren
     * Bank/Partner/User selbst, verschluesselt, in Postgres), das Passwort schuetzt also keine echten
     * Daten und muss daher kein Secret sein.
     */
    static final PasswordCallback SCHLUESSEL_PASSWORT = () -> "lcm-ebics".toCharArray();

    private EbicsSerialisierung() {
    }

    private static ObjectInputStream filtered(byte[] blob) throws Exception {
        var ois = new ObjectInputStream(new ByteArrayInputStream(blob));
        ois.setObjectInputFilter(FILTER);
        return ois;
    }

    static byte[] serialisieren(Bank bank) throws Exception {
        var bos = new ByteArrayOutputStream();
        try (var oos = new ObjectOutputStream(bos)) {
            bank.save(oos);
        }
        return bos.toByteArray();
    }

    static byte[] serialisieren(Partner partner) throws Exception {
        var bos = new ByteArrayOutputStream();
        try (var oos = new ObjectOutputStream(bos)) {
            partner.save(oos);
        }
        return bos.toByteArray();
    }

    static byte[] serialisieren(User user) throws Exception {
        var bos = new ByteArrayOutputStream();
        try (var oos = new ObjectOutputStream(bos)) {
            user.save(oos);
        }
        return bos.toByteArray();
    }

    static Bank bankLesen(byte[] blob) throws Exception {
        try (var ois = filtered(blob)) {
            return (Bank) ois.readObject();
        }
    }

    static Partner partnerLesen(byte[] blob, Bank bank) throws Exception {
        try (var ois = filtered(blob)) {
            return new Partner(bank, ois);
        }
    }

    static User userLesen(byte[] blob, Partner partner) throws Exception {
        try (var ois = filtered(blob)) {
            return new User(partner, ois, SCHLUESSEL_PASSWORT);
        }
    }
}
