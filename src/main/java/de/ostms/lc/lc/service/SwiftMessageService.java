package de.ostms.lc.lc.service;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.domain.SwiftMessage;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import de.ostms.lc.lc.repository.SwiftMessageRepository;
import de.ostms.lc.swift.FreeFormatMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Stores MT199/MT799 free-format messages and links them to the dossier whose reference they name (:21: first, then :20:). */
@Service
public class SwiftMessageService {
 private final SwiftMessageRepository messages;private final LetterOfCreditRepository lcs;
 public SwiftMessageService(SwiftMessageRepository m,LetterOfCreditRepository l){messages=m;lcs=l;}

 @Transactional(readOnly=true) public Optional<LetterOfCredit> findTarget(FreeFormatMessage.Parsed p){
  if(p.relatedReference()!=null){var related=lcs.findByReference(p.relatedReference());if(related.isPresent())return related;}
  return lcs.findByReference(p.reference());
 }
 @Transactional(readOnly=true) public boolean isDuplicate(String type,String reference,String raw){return messages.existsSame(type,reference,raw);}

 @Transactional public SwiftMessage importMessage(String type,String raw,String source,String user){
  var parsed=FreeFormatMessage.parse(type,raw);
  if(messages.existsSame(type,parsed.reference(),raw))throw new IllegalArgumentException("Diese Mitteilung wurde bereits importiert.");
  UUID lc=findTarget(parsed).map(LetterOfCredit::getId).orElse(null);
  return messages.save(new SwiftMessage(lc,type,parsed.reference(),parsed.relatedReference(),parsed.narrative(),raw,source,user));
 }
 @Transactional(readOnly=true) public List<SwiftMessage> forLc(UUID lcId){return messages.forLc(lcId);}
 @Transactional(readOnly=true) public List<SwiftMessage> unassigned(){return messages.unassigned();}
 /** Manual assignment of a message that matched no dossier. */
 @Transactional public SwiftMessage assign(UUID messageId,UUID lcId){
  var m=messages.findById(messageId).orElseThrow(()->new java.util.NoSuchElementException("Mitteilung nicht gefunden."));
  lcs.findById(lcId).orElseThrow(()->new java.util.NoSuchElementException("Akte nicht gefunden."));
  m.link(lcId);return messages.save(m);
 }
}
