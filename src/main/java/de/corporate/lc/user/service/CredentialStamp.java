package de.corporate.lc.user.service;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;

public final class CredentialStamp {
    private CredentialStamp() { }
    public static String of(String value){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException ex){throw new IllegalStateException("SHA-256 unavailable",ex);}
    }
}
