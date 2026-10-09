package de.ostms.lc.document.domain;
/** Null unspecified; 0 original; 1 copy. Legacy numbered values -3..-1 (originals) and 2..3 (copies) stay readable; the check counts documents. No authenticity claim. */
public final class DocumentCopy {
 private DocumentCopy(){}
 public static Integer validate(Integer value){if(value!=null&&(value< -3||value>3))throw new IllegalArgumentException("Kennzeichnung muss Original, Original 1–3 oder Copy 1–3 sein.");return value;}
 public static String label(Integer value){validate(value);return value==null?"unspecified":value==0?"Original":value<0?"Original "+(-value):value==1?"Copy":"Copy "+value;}
}
