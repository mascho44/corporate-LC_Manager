package de.ostms.lc.user.service;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

/** Normalizes contact addresses without changing the case-sensitive local part. */
final class UserEmail {
    private UserEmail() { }
    static String required(String value) {
        String email=value==null?"":value.trim();
        if(email.isEmpty()||email.length()>255||email.chars().anyMatch(Character::isWhitespace)||email.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Bitte eine gültige E-Mail-Adresse angeben.");
        try {
            InternetAddress address=new InternetAddress(email,true);
            address.validate();
            if(address.getPersonal()!=null||!email.equals(address.getAddress())||email.indexOf('@')<1)
                throw new AddressException();
        } catch(AddressException ex) {
            throw new IllegalArgumentException("Bitte eine gültige E-Mail-Adresse angeben.");
        }
        return email;
    }
}
