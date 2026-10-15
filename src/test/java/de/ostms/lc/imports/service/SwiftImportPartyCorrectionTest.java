package de.ostms.lc.imports.service;

import de.ostms.lc.imports.api.SwiftImportRequest;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.repository.AmendmentRepository;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import de.ostms.lc.lc.service.AmendmentService;
import de.ostms.lc.lc.service.LetterOfCreditService;
import de.ostms.lc.swift.Mt700Parser;
import de.ostms.lc.swift.Mt707Parser;
import de.ostms.lc.training.service.TrainingLearningService;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SwiftImportPartyCorrectionTest {
    @Test void correctedPartiesAreStoredInTheFileWhileTheMessageStaysUnchanged() {
        var lcs=mock(LetterOfCreditRepository.class);var lcService=mock(LetterOfCreditService.class);var learning=mock(TrainingLearningService.class);
        when(learning.apply(anyString(),anyString())).thenAnswer(i->i.getArgument(1));
        when(lcs.save(any())).thenAnswer(i->i.getArgument(0));
        var imported=new LetterOfCredit();imported.setApplicant("SEE 47A");imported.setBeneficiary("SEE 46A");
        String raw=":20:REF-PC-1\n:31D:271231BERLIN\n:32B:EUR100,\n:50:SEE 47A\n:59:SEE 46A\n:47A:OTHER CONDITIONS";
        when(lcService.importMt700(raw)).thenReturn(imported);
        var service=new SwiftImportService(new Mt700Parser(),new Mt707Parser(),lcs,mock(AmendmentRepository.class),lcService,mock(AmendmentService.class),mock(SwiftImportHistoryService.class),learning);
        var result=(LetterOfCredit)service.execute(new SwiftImportRequest("lc.swift",raw,"BUYER LLC\nDUBAI","SELLER GMBH\nSTUTTGART"));
        assertThat(result.getApplicant()).isEqualTo("BUYER LLC\nDUBAI");assertThat(result.getBeneficiary()).isEqualTo("SELLER GMBH\nSTUTTGART");
        verify(lcService).importMt700(raw);
    }
}
