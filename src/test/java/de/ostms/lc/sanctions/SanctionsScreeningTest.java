package de.ostms.lc.sanctions;

import de.ostms.lc.check.api.CheckResult;
import de.ostms.lc.lc.domain.LetterOfCredit;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SanctionsScreeningTest {
    private static ByteArrayInputStream in(String s){return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));}

    @Test void parsesTheThreePublicFormats() throws Exception {
        var un=SanctionsListParser.un(in("<?xml version=\"1.0\"?><CONSOLIDATED_LIST dateGenerated=\"2026-10-14\"><INDIVIDUALS><INDIVIDUAL><FIRST_NAME>ERIC</FIRST_NAME><SECOND_NAME>BADEGE</SECOND_NAME><UN_LIST_TYPE>DRC</UN_LIST_TYPE><REFERENCE_NUMBER>CDi.001</REFERENCE_NUMBER><INDIVIDUAL_ALIAS><ALIAS_NAME>E. BADEGE</ALIAS_NAME></INDIVIDUAL_ALIAS></INDIVIDUAL></INDIVIDUALS><ENTITIES><ENTITY><FIRST_NAME>SAMPLE TRADING CO</FIRST_NAME><REFERENCE_NUMBER>X.1</REFERENCE_NUMBER></ENTITY></ENTITIES></CONSOLIDATED_LIST>"));
        assertThat(un.entries()).hasSize(2);
        assertThat(un.entries().get(0).name()).isEqualTo("ERIC BADEGE");
        assertThat(un.entries().get(0).aliases()).containsExactly("E. BADEGE");
        assertThat(un.entries().get(1).kind()).isEqualTo(SanctionsListParser.Kind.ENTITY);
        assertThat(un.published()).isEqualTo("2026-10-14");

        var eu=SanctionsListParser.eu(in("<?xml version=\"1.0\"?><export xmlns=\"http://eu.europa.ec/fpi/fsd/export\" generationDate=\"2026-10-12\"><sanctionEntity euReferenceNumber=\"EU.1\"><regulation programme=\"IRQ\"/><subjectType code=\"person\"/><nameAlias wholeName=\"Saddam Hussein Al-Tikriti\"/><nameAlias wholeName=\"Abu Ali\"/></sanctionEntity></export>"));
        assertThat(eu.entries()).singleElement().satisfies(e->{assertThat(e.name()).isEqualTo("Saddam Hussein Al-Tikriti");assertThat(e.aliases()).containsExactly("Abu Ali");assertThat(e.programme()).isEqualTo("IRQ");assertThat(e.kind()).isEqualTo(SanctionsListParser.Kind.PERSON);});

        var ofac=SanctionsListParser.ofac(in("306,\"BANCO NACIONAL DE CUBA\",-0- ,\"CUBA\",-0- \n4243,\"EBANO\",\"vessel\",\"CUBA\",-0- \n"),in("306,220,\"aka\",\"NATIONAL BANK OF CUBA\",-0-\n"));
        assertThat(ofac.entries()).hasSize(2);
        assertThat(ofac.entries().get(0).aliases()).containsExactly("NATIONAL BANK OF CUBA");
        assertThat(ofac.entries().get(1).kind()).isEqualTo(SanctionsListParser.Kind.VESSEL);
    }

    @Test void namesMatchRegardlessOfOrderAccentsAndLegalForm() {
        double same=NameMatcher.score(NameMatcher.tokens("Banco Nacional de Cuba S.A."),NameMatcher.tokens("BANCO NACIONAL DE CUBA"));
        double swapped=NameMatcher.score(NameMatcher.tokens("Badege, Éric"),NameMatcher.tokens("ERIC BADEGE"));
        double typo=NameMatcher.score(NameMatcher.tokens("Banko Nacional de Cuba"),NameMatcher.tokens("BANCO NACIONAL DE CUBA"));
        double other=NameMatcher.score(NameMatcher.tokens("Layout Export GmbH"),NameMatcher.tokens("BANCO NACIONAL DE CUBA"));
        assertThat(same).isEqualTo(1.0);assertThat(swapped).isEqualTo(1.0);
        assertThat(typo).isGreaterThan(SanctionsService.MATCH);assertThat(other).isLessThan(SanctionsService.POSSIBLE);
        assertThat(NameMatcher.distinctive(NameMatcher.tokens("Ali"))).isFalse();
    }

    @Test void screeningReportsListedPartiesAndIgnoresAccountsAndBics() {
        var service=new SanctionsService(mock(SanctionsListRepository.class),false,"","","","");
        var lc=new LetterOfCredit();
        assertThat(service.screen(lc)).singleElement().extracting(CheckResult::code).isEqualTo("SANCTIONS_NOT_SCREENED");
        service.useEntries("OFAC",List.of(new SanctionsListParser.Entry("OFAC","306",SanctionsListParser.Kind.ENTITY,"BANCO NACIONAL DE CUBA",List.of("NATIONAL BANK OF CUBA"),"CUBA")));
        lc.setApplicant("/DE1234567890\nLayout Import GmbH\nStuttgart");
        lc.setIssuingBank("BNACCUHH\nNational Bank of Cuba\nHavana");
        var results=service.screen(lc);
        assertThat(results).singleElement().satisfies(r->{
            assertThat(r.code()).isEqualTo("SANCTIONS_MATCH");assertThat(r.severity()).isEqualTo(CheckResult.Severity.DISCREPANCY);
            assertThat(r.lcCondition()).isEqualTo("Eröffnende Bank: National Bank of Cuba");assertThat(r.documentEvidence()).contains("OFAC 306","Übereinstimmung 1,00");
        });
        lc.setIssuingBank("Deutsche Bank AG");
        assertThat(service.screen(lc)).singleElement().extracting(CheckResult::code).isEqualTo("SANCTIONS_SCREENED");
    }
}
