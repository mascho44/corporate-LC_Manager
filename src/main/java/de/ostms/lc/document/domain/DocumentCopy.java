package de.ostms.lc.document.domain;
/** Null unspecified; zero unnumbered original; -1..-3 numbered originals; 1..3 copies. No authenticity claim. */
public final class DocumentCopy {
 private DocumentCopy(){}
 public static Integer validate(Integer value){if(value!=null&&(value< -3||value>3))throw new IllegalArgumentException("Kennzeichnung muss Original, Original 1–3 oder Copy 1–3 sein.");return value;}
 public static String label(Integer value){validate(value);return value==null?"unspecified":value==0?"Original":value<0?"Original "+(-value):"Copy "+value;}
}
