package de.ostms.lc.document.service;

import de.ostms.lc.document.api.LcAssignmentCandidate;
import de.ostms.lc.document.domain.DocumentInboxItem;
import de.ostms.lc.lc.repository.LetterOfCreditRepository.AssignmentTarget;
import java.util.*;
import java.util.regex.Pattern;

final class LcAssignmentMatcher {
    private record Match(int rank,LcAssignmentCandidate candidate) { }
    static List<LcAssignmentCandidate> suggest(DocumentInboxItem item,List<AssignmentTarget> targets){
        String extracted=item.getExtractedReference()==null?"":item.getExtractedReference().trim();
        String text=item.getExtractedText()==null?"":item.getExtractedText();
        String normalizedText=normalized(text);
        List<Match> matches=new ArrayList<>();
        for(AssignmentTarget target:targets){
            String ref=target.getReference();if(ref==null||ref.isBlank())continue;
            String key=normalized(ref);String reason=null,evidence=null;int rank=0;
            if(ref.equalsIgnoreCase(extracted)){rank=4;reason="REFERENCE_EXACT";evidence=extracted;}
            else if(key.length()>=4&&!extracted.isEmpty()&&key.equals(normalized(extracted))){rank=3;reason="REFERENCE_NORMALIZED";evidence=extracted;}
            else if(key.length()>=4&&key.chars().anyMatch(Character::isDigit)&&normalizedText.contains(key)){
                var exact=referencePattern(Pattern.quote(ref)).matcher(text);
                if(exact.find()){rank=2;reason="REFERENCE_IN_TEXT";evidence=context(text,exact.start(),exact.end());}
                else{
                    String flexible=String.join("[\\s./_-]*",key.chars().mapToObj(c->Pattern.quote(String.valueOf((char)c))).toList());
                    var formatted=referencePattern(flexible).matcher(text);
                    if(formatted.find()){rank=1;reason="REFERENCE_FORMATTED_IN_TEXT";evidence=context(text,formatted.start(),formatted.end());}
                }
            }
            if(reason!=null)matches.add(new Match(rank,new LcAssignmentCandidate(target.getId(),ref,target.getStatus()==null?null:target.getStatus().name(),reason,evidence)));
        }
        return matches.stream().sorted(Comparator.comparingInt(Match::rank).reversed().thenComparing(match->match.candidate().reference())).map(Match::candidate).toList();
    }
    private static String normalized(String value){return value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]","");}
    private static Pattern referencePattern(String reference){return Pattern.compile("(?<![\\p{L}\\p{N}/_.-])"+reference+"(?![\\p{L}\\p{N}/_-]|\\.[\\p{L}\\p{N}])",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);}
    private static String context(String text,int start,int end){return text.substring(Math.max(0,start-60),Math.min(text.length(),end+60)).replaceAll("\\s+"," ").trim();}
}
