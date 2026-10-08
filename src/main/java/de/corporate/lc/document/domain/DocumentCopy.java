package de.corporate.lc.document.domain;
/** Null means unspecified; zero original; positive values numbered copies. No authenticity claim. */
public final class DocumentCopy {
 private DocumentCopy(){}
 public static Integer validate(Integer value){if(value!=null&&(value<0||value>3))throw new IllegalArgumentException("Kennzeichnung muss Original oder Copy 1, 2, 3 sein.");return value;}
}
