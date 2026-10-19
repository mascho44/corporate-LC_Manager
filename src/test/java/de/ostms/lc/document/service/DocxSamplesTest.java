package de.ostms.lc.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.ostms.lc.company.domain.CompanyProfile;
import de.ostms.lc.company.service.CompanyProfileService;
import de.ostms.lc.document.api.GeneratedDocumentItemRequest;
import de.ostms.lc.document.api.GeneratedDocumentRequest;
import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.lc.domain.LetterOfCredit;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Renders every sample template with invented data into target/docx-samples so the layout can be looked at in Word. */
class DocxSamplesTest {
    private static byte[] logo() throws Exception {
        var image = new BufferedImage(240, 80, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x1F3A5F)); g.fillRoundRect(0, 0, 80, 80, 16, 16);
        g.setColor(Color.WHITE); g.setFont(new Font("SansSerif", Font.BOLD, 34)); g.drawString("BM", 15, 53);
        g.setColor(new Color(0x1F3A5F)); g.setFont(new Font("SansSerif", Font.BOLD, 22)); g.drawString("Beispiel", 92, 36);
        g.setColor(new Color(0x6B7280)); g.setFont(new Font("SansSerif", Font.PLAIN, 14)); g.drawString("Maschinenbau", 92, 58);
        g.dispose();
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test void writesAllSamples() throws Exception {
        var lc = new LetterOfCredit();
        lc.setReference("TEST-LC-2026-001"); lc.setApplicant("MUSTER IMPORT TRADING LLC\nP.O. BOX 1234\nDUBAI, UNITED ARAB EMIRATES");
        lc.setBeneficiary("BEISPIEL MASCHINENBAU GMBH\nINDUSTRIESTRASSE 10\n70565 STUTTGART, GERMANY");
        lc.setCurrency("EUR"); lc.setAmount(new BigDecimal("125000.00")); lc.setCompanyId(1);
        lc.setRawMessage(":20:TEST-LC-2026-001\n:42C:AT SIGHT\n:44E:HAMBURG, GERMANY\n:44F:JEBEL ALI, UNITED ARAB EMIRATES\n:45A:2 UNITS CNC MILLING MACHINE MODEL BM-500\nCIF JEBEL ALI INCOTERMS 2020");
        var company = new CompanyProfile();
        company.setLegalName("Beispiel Maschinenbau GmbH"); company.setAddressLine("Industriestrasse 10"); company.setPostalCode("70565"); company.setCity("Stuttgart"); company.setCountry("Germany");
        company.setEmail("export@beispiel-maschinenbau.example"); company.setPhone("+49 711 000000"); company.setTaxId("DE000000000"); company.setRegistrationNumber("HRB 000000, Amtsgericht Stuttgart");
        company.setBankName("Testbank AG"); company.setIban("DE00 0000 0000 0000 0000 00"); company.setBic("TESTDEFFXXX"); company.setContactPerson("Anna Muster"); company.setLogo(logo()); company.setLogoContentType("image/png");
        var templates = mock(DocumentTemplateService.class);
        when(templates.content(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty());
        var companies = mock(CompanyProfileService.class);
        when(companies.profileForLetterhead(org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.any())).thenReturn(company);
        var service = new DocxTemplateService(templates, companies);
        var invoiceItems = List.of(
            new GeneratedDocumentItemRequest("1", "CNC milling machine model BM-500, 5-axis, incl. control unit and standard tooling, HS 8459.61", new BigDecimal("2"), "pcs", new BigDecimal("58000.00"), new BigDecimal("116000.00"), null, null, null),
            new GeneratedDocumentItemRequest("2", "Spare parts kit SP-500 and operator manual", new BigDecimal("2"), "sets", new BigDecimal("4500.00"), new BigDecimal("9000.00"), null, null, null));
        var packingItems = List.of(
            new GeneratedDocumentItemRequest("1", "CNC milling machine model BM-500, seaworthy crate", new BigDecimal("2"), "pcs", null, null, 2, new BigDecimal("2480"), new BigDecimal("2760")),
            new GeneratedDocumentItemRequest("2", "Spare parts kit SP-500 and operator manual, carton", new BigDecimal("2"), "sets", null, null, 1, new BigDecimal("64"), new BigDecimal("72")));
        Path dir = Path.of("target", "docx-samples");
        Files.createDirectories(dir);
        for (var type : List.of(DocumentType.COMMERCIAL_INVOICE, DocumentType.PACKING_LIST, DocumentType.CERTIFICATE_OF_ORIGIN, DocumentType.BENEFICIARY_CERTIFICATE, DocumentType.QUALITY_CERTIFICATE)) {
            var items = type == DocumentType.PACKING_LIST ? packingItems : invoiceItems;
            var request = new GeneratedDocumentRequest(type, "INV-2026-0815", LocalDate.of(2026, 10, 5), "CNC milling machines", "2 pcs", 3, new BigDecimal("2544"), new BigDecimal("2832"), "Marks and numbers: BM-500 / DUBAI / 1-3", items);
            byte[] rendered = service.render(lc, request);
            Files.write(dir.resolve("muster-" + type.name().toLowerCase().replace('_', '-') + ".docx"), rendered);
            Files.write(dir.resolve("vorlage-" + type.name().toLowerCase().replace('_', '-') + ".docx"), service.template(type));
            assertThat(rendered.length).isGreaterThan(5000);
        }
    }
}
