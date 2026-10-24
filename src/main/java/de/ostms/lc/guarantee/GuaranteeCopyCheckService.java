package de.ostms.lc.guarantee;

import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.document.repository.LcDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Reads the text of the newest copy of the guarantee and compares it with the data of the file. */
@Service
public class GuaranteeCopyCheckService {
 public record Result(UUID documentId,String filename,boolean textRead,List<GuaranteeCopyCheck.Item> items){}
 private final GuaranteeService guarantees;private final LcDocumentRepository documents;
 public GuaranteeCopyCheckService(GuaranteeService guarantees,LcDocumentRepository documents){this.guarantees=guarantees;this.documents=documents;}

 @Transactional(readOnly=true) public Result check(UUID lcId){
  var g=guarantees.one(lcId,LocalDate.now());
  var copy=documents.findByLetterOfCreditIdOrderByUploadedAtDesc(lcId).stream().filter(d->d.getDocumentType()==DocumentType.GUARANTEE_COPY).findFirst().orElse(null);
  if(copy==null)return null;
  var text=copy.getExtractedText();
  var items=GuaranteeCopyCheck.compare(new GuaranteeCopyCheck.Expected(g.currency(),g.amount(),g.beneficiary(),g.bank(),g.expiryDate(),g.reference()),text);
  return new Result(copy.getId(),copy.getOriginalFilename(),text!=null&&!text.isBlank(),items);
 }
}
