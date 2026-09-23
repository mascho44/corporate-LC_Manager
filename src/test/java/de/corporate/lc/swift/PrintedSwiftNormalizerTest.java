package de.corporate.lc.swift;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PrintedSwiftNormalizerTest {
    private final PrintedSwiftNormalizer normalizer = new PrintedSwiftNormalizer();

    @Test
    void convertsPrintedSwiftTableToTaggedMessage() {
        String printed = """
                Message Details
                Fieldtag Fieldname
                Fielddetails
                27 Sequence of Total
                1/1
                20 Documentary Credit Number
                LC32299C500180
                31C Date of Issue
                250522
                31D Date and Place of Expiry
                250910GERMANY
                50 Applicant
                NANJING AUTOMOBILE IMP. AND EXP.
                CO.,LTD.
                59 Beneficiary
                NAGEL TECHNOLOGIES GMBH
                32B Currency Code, Amount
                EUR1403200,
                """;

        String result = normalizer.normalize(printed);

        assertThat(result).contains(":20:LC32299C500180", ":31C:250522", ":31D:250910GERMANY");
        assertThat(result).contains(":50:NANJING AUTOMOBILE IMP. AND EXP.\nCO.,LTD.");
        assertThat(result).contains(":59:NAGEL TECHNOLOGIES GMBH", ":32B:EUR1403200,");
    }

    @Test
    void keepsStandardColonFormat() {
        assertThat(normalizer.normalize(":20:ABC123\n:31C:260923")).isEqualTo(":20:ABC123\n:31C:260923");
    }
}
