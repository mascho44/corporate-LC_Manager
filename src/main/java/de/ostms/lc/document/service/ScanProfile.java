package de.ostms.lc.document.service;
import java.util.*;

/**
 * Settings for turning a scanned page into text: how it is rendered and how the recognition engine is prepared. Chosen per tenant.
 * STANDARD is the behaviour the application always had. The other profiles are starting points to be calibrated with real scans
 * (see the recognition benchmark); they differ from STANDARD only in the values below.
 */
public record ScanProfile(String id,String label,String description,int renderDpi,Binarization binarization) {
 public enum Binarization { NONE, SAUVOLA }
 /** Positions are always stored in this raster, whatever the recognition resolution was. */
 public static final int EVIDENCE_DPI=200;
 public static final ScanProfile STANDARD=new ScanProfile("STANDARD","Standard","Bisheriges Verhalten: 300 DPI, adaptive Schwelle (Sauvola). Für gemischte Scans und Uploads.",300,Binarization.SAUVOLA);
 public static final ScanProfile PROFI_SCANNER=new ScanProfile("PROFI_SCANNER","Profi-Scanner","Saubere Graustufen-Scans: 300 DPI, keine zusätzliche Binarisierung (Tesseract-Standard). Startwert, mit echten Scans zu prüfen.",300,Binarization.NONE);
 public static final ScanProfile SCHLECHTER_SCAN=new ScanProfile("SCHLECHTER_SCAN","Schlechter Scan","Kleine oder blasse Schrift: 400 DPI, adaptive Schwelle (Sauvola). Langsamer. Startwert, mit echten Scans zu prüfen.",400,Binarization.SAUVOLA);
 private static final Map<String,ScanProfile> ALL;
 static{var m=new LinkedHashMap<String,ScanProfile>();for(var p:List.of(STANDARD,PROFI_SCANNER,SCHLECHTER_SCAN))m.put(p.id(),p);ALL=Collections.unmodifiableMap(m);}
 public static Collection<ScanProfile> all(){return ALL.values();}
 /** Unknown or missing ids fall back to STANDARD so a stale setting can never stop recognition. */
 public static ScanProfile byId(String id){return id==null?STANDARD:ALL.getOrDefault(id,STANDARD);}
 public static boolean exists(String id){return id!=null&&ALL.containsKey(id);}
 /** Marker appended to the evidence method for non-standard profiles, so a stored recognition can be reproduced. */
 public String methodSuffix(){return this==STANDARD||STANDARD.id().equals(id)?"":"+"+id;}
}
