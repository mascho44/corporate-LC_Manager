package de.corporate.lc.check.service;

import com.fasterxml.jackson.databind.*;
import de.corporate.lc.lc.domain.LetterOfCredit;
import de.corporate.lc.document.domain.LcDocument;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.*;

/** Conservative binding to the entire LC and document set, not just a displayed excerpt. */
final class ReviewInputFingerprint {
    private static final ObjectMapper JSON=new ObjectMapper().findAndRegisterModules()
        .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,true);
    private ReviewInputFingerprint(){}
    static String of(LetterOfCredit lc,List<LcDocument> documents){
        try{
            var digest=MessageDigest.getInstance("SHA-256");
            add(digest,JSON.writeValueAsBytes(lc));
            for(var document:documents.stream().sorted(Comparator.comparing((LcDocument d)->Objects.toString(d.getId(),""))
                    .thenComparing(d->Objects.toString(d.getOriginalFilename(),""))).toList()){
                add(digest,JSON.writeValueAsBytes(document));
                add(digest,JSON.writeValueAsBytes(document.getExtractedText()));
                add(digest,document.getContent());
            }
            return HexFormat.of().formatHex(digest.digest());
        }catch(Exception e){throw new IllegalStateException("Prüfgrundlage konnte nicht gebunden werden.",e);}
    }
    private static void add(MessageDigest digest,byte[] bytes){
        digest.update(ByteBuffer.allocate(4).putInt(bytes==null?-1:bytes.length).array());
        if(bytes!=null)digest.update(bytes);
    }
}
