package de.ostms.lc.check.service;

import de.ostms.lc.check.api.CheckResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RuleCatalogTest {
    @Test void mapsKnownFindingToExplicitVersionedRule(){
        var rule=RuleCatalog.forFinding("INVOICE_AMOUNT_EXCEEDED");
        assertThat(rule.id()).isEqualTo("LC_AMOUNT_LIMIT");assertThat(rule.version()).isEqualTo("1.0");
        assertThat(rule.limitations()).contains("keine vollständige");assertThat(rule.sourceUrl()).isNull();
    }
    @Test void unknownRuleNeverClaimsAnIccRule(){
        var rule=RuleCatalog.forFinding("SOME_NEW_HEURISTIC");assertThat(rule.sourceUrl()).isNull();assertThat(rule.basis()).contains("keine zugeordnete ICC");
    }
    @Test void apiIncludesCatalogVersionAndRuleMetadata()throws Exception{
        var result=new CheckResult(CheckResult.Severity.DISCREPANCY,"DOCUMENT_AFTER_EXPIRY","Test");
        var json=new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(result));
        assertThat(json.get("ruleCatalogVersion").asText()).isEqualTo(RuleCatalog.VERSION);
        assertThat(json.get("rule").get("id").asText()).isEqualTo("LC_EXPIRY_DATE");
    }
}
