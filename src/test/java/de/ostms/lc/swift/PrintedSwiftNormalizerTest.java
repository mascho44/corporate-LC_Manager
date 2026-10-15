package de.ostms.lc.swift;

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

    @Test
    void readsBankPrintViewWithTagColonLabelAndValueBelow() {
        String printed = """
                MESSAGE TYPE: 700
                TO INSTITUTION: ABCDDEFFXXX
                SAMPLE BANK AG
                PRIORITY: NORMAL
                27: SEQUENCE OF TOTAL
                1/1
                40A: FORM OF DOCUMENTARY CREDIT
                IRREVOCABLE
                20: DOCUMENTARY CREDIT NUMBER
                ILC250B000001
                31C: DATE OF ISSUE
                251027
                40E: APPLICABLE RULES
                UCPURR LATEST VERSION
                31D: DATE AND PLACE OF EXPIRY
                260222GERMANY
                50: APPLICANT
                SAMPLE INDUSTRIAL LLC
                P.O.BOX 1234,
                DUBAI, U.A.E AE
                59: BENEFICIARY
                MUSTER MASCHINEN GMBH,
                32B: CURRENCY CODE, AMOUNT
                EUR250000,00
                42C: DRAFTS AT ...
                AT SIGHT
                45A: DESCRIPTION OF GOODS AND/OR SERVICES
                WOODWORKING MACHINE
                """;
        String result = normalizer.normalize(printed);
        assertThat(result).startsWith(":27:1/1");
        assertThat(result).contains(":40A:IRREVOCABLE", ":20:ILC250B000001", ":31C:251027", ":40E:UCPURR LATEST VERSION", ":31D:260222GERMANY",
                ":50:SAMPLE INDUSTRIAL LLC\nP.O.BOX 1234,\nDUBAI, U.A.E AE", ":59:MUSTER MASCHINEN GMBH,", ":32B:EUR250000,00", ":42C:AT SIGHT", ":45A:WOODWORKING MACHINE");
        assertThat(result).doesNotContain("MESSAGE TYPE", "PRIORITY", "SEQUENCE OF TOTAL", "FORM OF DOCUMENTARY");
    }

    @Test
    void toleratesOcrConfusionsInTagsAndLabelWithValueOnOneLine() {
        String result = normalizer.normalize(":4OA:IRREVOCABLE\n3lD: DATE AND PLACE OF EXPIRY 260222GERMANY\nS9: BENEFICIARY\nMUSTER GMBH\n32B Currency Code, Amount EUR1000,");
        assertThat(result).contains(":40A:IRREVOCABLE", ":31D:260222GERMANY", ":59:MUSTER GMBH", ":32B:EUR1000,");
    }

    @Test
    void unreadableTextLayerIsDetected() {
        assertThat(de.ostms.lc.document.service.DocumentExtractionServiceAccess.unreadable("\u0001\u0002\u0003\uFFFD\uFFFD\uE000\uE001 \u0004\u0005\u0006 ab")).isTrue();
        assertThat(de.ostms.lc.document.service.DocumentExtractionServiceAccess.unreadable("27: SEQUENCE OF TOTAL 1/1 40A: FORM OF DOCUMENTARY CREDIT")).isFalse();
    }
}
