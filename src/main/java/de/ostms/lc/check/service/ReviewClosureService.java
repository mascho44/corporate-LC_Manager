package de.ostms.lc.check.service;

import de.ostms.lc.check.domain.LcReviewClosure;
import de.ostms.lc.check.domain.ReviewCloseThreshold;
import de.ostms.lc.check.repository.LcReviewClosureRepository;
import de.ostms.lc.check.repository.ReviewCloseThresholdRepository;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.domain.LetterOfCreditStatus;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import de.ostms.lc.lc.service.LcTimelineService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

/** Closing the examination: up to a configurable amount one person closes alone, above it several different people must sign off. A completed closure sets the file to "examination completed". */
@Service
public class ReviewClosureService {
 public record State(String status,int required,List<String> signers,String summary,String startedBy,boolean completed){}
 public record Change(State state,boolean completedNow){}
 public record Rule(String currency,BigDecimal minimumAmount,int requiredClosers){}
 private final LcReviewClosureRepository closures;private final ReviewCloseThresholdRepository thresholds;private final LetterOfCreditRepository lcs;private final LcTimelineService timeline;
 public ReviewClosureService(LcReviewClosureRepository closures,ReviewCloseThresholdRepository thresholds,LetterOfCreditRepository lcs,LcTimelineService timeline){this.closures=closures;this.thresholds=thresholds;this.lcs=lcs;this.timeline=timeline;}

 static State state(LcReviewClosure c){return new State(c.getStatus(),c.getRequiredClosers(),c.signerList(),c.getSummary(),c.getStartedBy(),LcReviewClosure.COMPLETED.equals(c.getStatus()));}
 private LetterOfCredit lc(UUID id){return lcs.findById(id).orElseThrow(()->new NoSuchElementException("Akte nicht gefunden."));}

 @Transactional(readOnly=true) public List<Rule> rules(){return thresholds.ordered().stream().map(t->new Rule(t.getCurrency(),t.getMinimumAmount(),t.getRequiredClosers())).toList();}
 @Transactional public List<Rule> replaceRules(List<Rule> rules){
  Set<String> unique=new HashSet<>();
  for(Rule r:rules){
   if(r.requiredClosers()<1||r.requiredClosers()>5)throw new IllegalArgumentException("Die Zahl der Abschließenden muss zwischen 1 und 5 liegen.");
   if(!unique.add(r.currency().toUpperCase(Locale.ROOT)+":"+r.minimumAmount().stripTrailingZeros().toPlainString()))throw new IllegalArgumentException("Doppelte Betragsgrenze für "+r.currency()+" "+r.minimumAmount()+".");
  }
  thresholds.deleteForCurrentTenant();thresholds.flush();
  rules.forEach(r->thresholds.save(new ReviewCloseThreshold(r.currency().trim().toUpperCase(Locale.ROOT),r.minimumAmount().setScale(2),r.requiredClosers())));
  return rules();
 }
 /** Number of different people needed for a file of this amount; one when no rule applies. */
 @Transactional(readOnly=true) public int requiredFor(BigDecimal amount,String currency){
  if(amount==null||currency==null||currency.isBlank())return 1;
  return thresholds.ordered().stream().filter(t->t.getCurrency().equalsIgnoreCase(currency.trim())&&amount.compareTo(t.getMinimumAmount())>=0)
   .max(Comparator.comparing(ReviewCloseThreshold::getMinimumAmount)).map(ReviewCloseThreshold::getRequiredClosers).orElse(1);
 }

 @Transactional(readOnly=true) public Optional<State> get(UUID lcId){lc(lcId);return closures.forLc(lcId).map(ReviewClosureService::state);}

 @Transactional public Change start(UUID lcId,String user,String summary){
  LetterOfCredit lc=lc(lcId);
  String text=summary==null?"":summary.strip();
  if(text.isEmpty())throw new IllegalArgumentException("Bitte einen Vermerk zum Abschluss angeben.");
  if(text.length()>1500)throw new IllegalArgumentException("Der Vermerk darf höchstens 1500 Zeichen lang sein.");
  var existing=closures.forLc(lcId);
  if(existing.isPresent()){
   var c=existing.get();
   if(LcReviewClosure.PENDING.equals(c.getStatus()))throw new IllegalStateException("Es gibt bereits einen offenen Abschluss für diese Akte.");
   if(lc.getStatus()==LetterOfCreditStatus.REVIEW_COMPLETED)throw new IllegalStateException("Die Prüfung ist bereits abgeschlossen.");
   closures.delete(c);closures.flush();
  }
  var closure=new LcReviewClosure(lcId,requiredFor(lc.getAmount(),lc.getCurrency()),text,user);
  closures.save(closure);
  boolean done=closure.getRequiredClosers()<=1;
  if(done)finish(lcId,lc,closure,user);
  return new Change(state(closure),done);
 }

 @Transactional public Change signOff(UUID lcId,String user){
  LetterOfCredit lc=lc(lcId);
  var closure=closures.forLc(lcId).filter(c->LcReviewClosure.PENDING.equals(c.getStatus())).orElseThrow(()->new IllegalStateException("Für diese Akte gibt es keinen offenen Abschluss."));
  if(closure.hasSigned(user))throw new IllegalStateException("Sie haben diesen Abschluss bereits mitgezeichnet; eine andere Person muss ihn bestätigen.");
  closure.sign(user);
  boolean done=closure.signerList().size()>=closure.getRequiredClosers();
  if(done)finish(lcId,lc,closure,user);
  closures.save(closure);
  return new Change(state(closure),done);
 }

 /** Only the person who started an open closure may withdraw it. */
 @Transactional public void withdraw(UUID lcId,String user){
  lc(lcId);
  var closure=closures.forLc(lcId).filter(c->LcReviewClosure.PENDING.equals(c.getStatus())).orElseThrow(()->new IllegalStateException("Für diese Akte gibt es keinen offenen Abschluss."));
  if(!closure.getStartedBy().equalsIgnoreCase(user))throw new IllegalStateException("Nur die Person, die den Abschluss begonnen hat, kann ihn zurückziehen.");
  closures.delete(closure);
 }

 private void finish(UUID lcId,LetterOfCredit lc,LcReviewClosure closure,String lastUser){
  closure.complete();
  String by=String.join(", ",closure.signerList());
  String note=closure.getSummary()+" · Abschluss durch: "+by+".";
  timeline.addNote(lcId,lastUser,note.length()>2000?note.substring(0,2000):note);
  lc.setStatus(LetterOfCreditStatus.REVIEW_COMPLETED);
  lcs.save(lc);
 }
}
