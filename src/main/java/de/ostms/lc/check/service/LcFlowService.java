package de.ostms.lc.check.service;

import de.ostms.lc.check.domain.LcDraft;
import de.ostms.lc.check.repository.LcDraftRepository;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.domain.LetterOfCreditStatus;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import de.ostms.lc.swift.Mt700Parser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.*;

/**
 * The guided order for a bank file: 1. check the draft of the credit (optional), 2. compare the credit that arrived with the draft, 3. check the credit itself,
 * 4. examine the documents later. Everything is computed from the stored draft and the credit; only the draft is stored.
 */
@Service
public class LcFlowService {
 public record Difference(String tag, String label, String draft, String credit) {}
 public record DraftInfo(String filename, String savedBy, LocalDateTime savedAt, long discrepancies, long warnings) {}
 public record CheckCounts(long discrepancies, long warnings, List<LcDraftReviewService.Finding> findings) {}
 public record Flow(DraftInfo draft, List<Difference> differences, CheckCounts creditCheck, String status, boolean reviewCompleted) {}
 private static final Pattern FIELD = Pattern.compile("(?m)^:(\\d{2}[A-Z]?):(.*?)(?=^:\\d{2}[A-Z]?:|\\z)", Pattern.DOTALL);
 /** Fields compared between draft and credit, in message order; the reference may legitimately differ and is left out. */
 static final List<String> COMPARED = List.of("40A", "31D", "32B", "39A", "41A", "41D", "43P", "43T", "44A", "44E", "44F", "44B", "44C", "44D", "45A", "46A", "47A", "48", "49", "50", "59", "71D");
 private static final int MAX_DRAFT = 200_000;
 private final LcDraftRepository drafts;private final LetterOfCreditRepository lcs;private final LcDraftReviewService review;
 public LcFlowService(LcDraftRepository drafts,LetterOfCreditRepository lcs,LcDraftReviewService review){this.drafts=drafts;this.lcs=lcs;this.review=review;}

 private LetterOfCredit lc(UUID id){return lcs.findById(id).orElseThrow(()->new NoSuchElementException("Akte nicht gefunden."));}
 static Map<String,String> fields(String text){Map<String,String> f=new LinkedHashMap<>();if(text==null)return f;Matcher m=FIELD.matcher(text.strip());while(m.find())f.put(m.group(1),m.group(2).trim());return f;}
 private static String norm(String s){return s==null?"":s.replaceAll("\\s+"," ").trim().toUpperCase(Locale.ROOT);}
 private static String shown(String s){String x=s==null?"":s.trim();return x.length()>800?x.substring(0,800)+" …":x;}

 /** Fields of the draft that differ from the credit (changed, only in the draft, only in the credit). */
 static List<Difference> compare(String draftText,String creditText){
  var d=fields(draftText);var c=fields(creditText);var out=new ArrayList<Difference>();
  for(String tag:COMPARED){
   String a=d.get(tag),b=c.get(tag);
   if((a==null||a.isBlank())&&(b==null||b.isBlank()))continue;
   if(norm(a).equals(norm(b)))continue;
   out.add(new Difference(tag,Mt700Parser.label(tag),shown(a),shown(b)));
  }
  return out;
 }

 @Transactional(readOnly=true) public Flow flow(UUID lcId,LocalDate today){
  LetterOfCredit lc=lc(lcId);
  var draft=drafts.forLc(lcId);
  DraftInfo info=null;List<Difference> diffs=null;
  if(draft.isPresent()){
   var dr=draft.get();long dd=0,dw=0;
   try{var r=review.check(dr.getContent(),today);dd=r.discrepancies();dw=r.warnings();}catch(RuntimeException ignored){}
   info=new DraftInfo(dr.getFilename(),dr.getSavedBy(),dr.getSavedAt(),dd,dw);
   diffs=compare(dr.getContent(),lc.getRawMessage());
  }
  CheckCounts counts=null;
  try{var r=review.check(lc.getRawMessage(),today);counts=new CheckCounts(r.discrepancies(),r.warnings(),r.findings());}catch(RuntimeException ignored){}
  return new Flow(info,diffs,counts,lc.getStatus().name(),lc.getStatus()==LetterOfCreditStatus.REVIEW_COMPLETED);
 }

 @Transactional public void saveDraft(UUID lcId,String filename,String text,String user){
  lc(lcId);
  if(text==null||text.isBlank())throw new IllegalArgumentException("Bitte den Entwurf einfügen oder eine Datei wählen.");
  if(text.length()>MAX_DRAFT)throw new IllegalArgumentException("Der Entwurf ist zu lang.");
  var f=fields(text);
  if(!f.containsKey("20")&&!f.containsKey("32B"))throw new IllegalArgumentException("Im Text wurde kein Akkreditiv im MT700-Aufbau erkannt (Felder wie :20: und :32B:).");
  String name=filename==null||filename.isBlank()?null:filename.replaceAll("[\\\\/]","_");
  var existing=drafts.forLc(lcId);
  if(existing.isPresent()){existing.get().replace(name,text,user);drafts.save(existing.get());}
  else drafts.save(new LcDraft(lcId,name,text,user));
 }

 @Transactional public void deleteDraft(UUID lcId){
  lc(lcId);
  drafts.forLc(lcId).ifPresent(drafts::delete);
 }
}
