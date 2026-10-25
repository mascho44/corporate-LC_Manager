package de.ostms.lc.imports.api;
public record SwiftFieldView(String code,String label,String value,String suggestedTarget,String targetLabel,String confidence,String reason,boolean unusual,String notice,Double confidenceScore,String confidenceMethod) {
 public SwiftFieldView(String code,String label,String value,String suggestedTarget,String targetLabel,String confidence,String reason,boolean unusual,String notice){this(code,label,value,suggestedTarget,targetLabel,confidence,reason,unusual,notice,"HIGH".equals(confidence)?0.95:0.45,"FIELD_MAPPING_HEURISTIC_V1");}
}
