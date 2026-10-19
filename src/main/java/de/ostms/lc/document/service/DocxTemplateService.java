package de.ostms.lc.document.service;

import com.deepoove.poi.XWPFTemplate;
import de.ostms.lc.document.api.GeneratedDocumentRequest;
import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.company.service.CompanyProfileService;
import com.deepoove.poi.data.Pictures;
import com.deepoove.poi.data.Rows;
import com.deepoove.poi.data.Tables;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class DocxTemplateService {
    private final DocumentTemplateService templates;
    private final CompanyProfileService companies;
    public DocxTemplateService(DocumentTemplateService templates,CompanyProfileService companies){this.templates=templates;this.companies=companies;}
    public byte[] render(LetterOfCredit lc, GeneratedDocumentRequest request) throws IOException {
        de.ostms.lc.tenant.domain.TenantContext.require(lc.getTenantId());
        var customTemplate = templates.content(request.type(),lc.getCompanyId(),lc.getTemplateCompany()==null||lc.getTemplateCompany().isBlank()?lc.getBeneficiary():lc.getTemplateCompany());
        byte[] template = customTemplate.isPresent() ? customTemplate.get() : template(request.type());
        Map<String, Object> data = documentData(lc, request);
        data.put("itemsTable", itemsTable(request, lc.getCurrency()));
        try (XWPFTemplate compiled = XWPFTemplate.compile(new ByteArrayInputStream(template)).render(data);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            compiled.write(output);
            return output.toByteArray();
        }
    }

    /** The same document as {@link #render} but as PDF in the same layout. */
    public byte[] renderPdf(LetterOfCredit lc, GeneratedDocumentRequest request) throws IOException {
        de.ostms.lc.tenant.domain.TenantContext.require(lc.getTenantId());
        return ProfessionalPdf.document(documentData(lc, request), request);
    }

    /** The cover letter as PDF. */
    public byte[] renderSubmissionLetterPdf(LetterOfCredit lc, SubmissionLetter letter) throws IOException {
        de.ostms.lc.tenant.domain.TenantContext.require(lc.getTenantId());
        return ProfessionalPdf.letter(letterData(lc, letter), letter.rows());
    }

    /** All values of a generated document by placeholder name, shared by the Word and the PDF output. */
    Map<String, Object> documentData(LetterOfCredit lc, GeneratedDocumentRequest request) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("title", title(request.type()));
        data.put("currency", lc.getCurrency() == null ? "" : lc.getCurrency());
        data.put("documentNumber", value(request.documentNumber()));
        data.put("documentDate", request.documentDate().format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy", java.util.Locale.ENGLISH)));
        data.put("lcReference", value(lc.getReference()));
        data.put("beneficiary", value(lc.getBeneficiary()));
        data.put("applicant", value(lc.getApplicant()));
        data.put("description", value(request.description()));
        data.put("quantity", value(request.quantity()));
        data.put("amount", value(lc.getCurrency()) + " " + (lc.getAmount() == null ? "-" : money(lc.getAmount())));
        data.put("packages", request.packages() == null ? "-" : request.packages().toString());
        data.put("netWeight", request.netWeight() == null ? "-" : number(request.netWeight()) + " kg");
        data.put("grossWeight", request.grossWeight() == null ? "-" : number(request.grossWeight()) + " kg");
        data.put("notes", value(request.notes()));
        var itemSum = itemSum(request);
        data.put("totalAmount", itemSum == null ? "-" : value(lc.getCurrency()) + " " + money(itemSum));
        data.put("amountInWords", itemSum == null ? "-" : AmountInWords.english(itemSum, lc.getCurrency()));
        data.put("portOfLoading", value(de.ostms.lc.lc.service.LcConditions.value(lc, de.ostms.lc.lc.domain.LcCondition.PORT_OF_LOADING).orElse(null)));
        data.put("portOfDischarge", value(de.ostms.lc.lc.service.LcConditions.value(lc, de.ostms.lc.lc.domain.LcCondition.PORT_OF_DISCHARGE).or(() -> de.ostms.lc.lc.service.LcConditions.value(lc, de.ostms.lc.lc.domain.LcCondition.FINAL_DESTINATION)).orElse(null)));
        data.put("incoterm", value(incoterm(de.ostms.lc.lc.service.LcConditions.value(lc, de.ostms.lc.lc.domain.LcCondition.GOODS_DESCRIPTION).orElse(""))));
        data.put("paymentTerms", value(paymentTerms(lc.getRawMessage())));
        putCompany(data, lc);
        data.put("placeDate", joined(", ", (String) data.get("companyCityOnly"), (String) data.get("documentDate")));
        String bank = (String) data.get("companyBankLine");
        data.put("paymentInstruction", bank.isEmpty() ? "" : "Payment to: " + bank);
        return data;
    }

    public byte[] template(DocumentType type) throws IOException {
        if(type==null||!java.util.EnumSet.of(DocumentType.COMMERCIAL_INVOICE,DocumentType.PACKING_LIST,DocumentType.CERTIFICATE_OF_ORIGIN,DocumentType.BENEFICIARY_CERTIFICATE,DocumentType.QUALITY_CERTIFICATE).contains(type))
            throw new IllegalArgumentException("Für diesen Dokumenttyp ist keine Word-Mustervorlage verfügbar.");
        return ProfessionalDocxTemplate.build(type);
    }

    private static final java.util.regex.Pattern INCOTERM = java.util.regex.Pattern.compile("\\b(EXW|FCA|FAS|FOB|CFR|CIF|CPT|CIP|DAP|DPU|DDP)\\b([^\\n]{0,60}?)(?=\\s+INCOTERMS|\\s*$|\\n)", java.util.regex.Pattern.CASE_INSENSITIVE);
    static String incoterm(String goods) {
        if (goods == null || goods.isBlank()) return null;
        var m = INCOTERM.matcher(goods.replaceAll("\\s+", " ").trim());
        return m.find() ? (m.group(1) + m.group(2)).trim().toUpperCase(java.util.Locale.ROOT) : null;
    }

    static String paymentTerms(String rawMessage) {
        if (rawMessage == null) return null;
        var m = java.util.regex.Pattern.compile("(?m)^:42C:(.+)$").matcher(rawMessage);
        return m.find() ? m.group(1).trim() : null;
    }

    private static java.math.BigDecimal itemSum(GeneratedDocumentRequest request) {
        var items = request.items();
        if (items == null || items.isEmpty()) return null;
        var sum = java.math.BigDecimal.ZERO;
        boolean any = false;
        for (var item : items) if (item.amount() != null) { sum = sum.add(item.amount()); any = true; }
        return any ? sum : null;
    }

    private void putCompany(Map<String, Object> data, LetterOfCredit lc) {
        var company=companies.profileForLetterhead(lc.getCompanyId(),lc.getBeneficiary());data.put("companyName",value(company.getLegalName()));data.put("companyAddress",value(company.getAddressLine()));data.put("companyPostalCode",value(company.getPostalCode()));data.put("companyCity",value(company.getCity()));data.put("companyCountry",value(company.getCountry()));data.put("companyEmail",value(company.getEmail()));data.put("companyPhone",value(company.getPhone()));data.put("companyTaxId",value(company.getTaxId()));data.put("companyRegistrationNumber",value(company.getRegistrationNumber()));data.put("companyBankName",value(company.getBankName()));data.put("companyIban",value(company.getIban()));data.put("companyBic",value(company.getBic()));data.put("companyContactPerson",value(company.getContactPerson()));data.put("companyLogo",null);if(company.getLogo()!=null){data.put("companyLogo",Pictures.ofBytes(company.getLogo()).size(160,60).create());data.put("companyLogoBytes",company.getLogo());}
        // Lines built from what exists, so a missing detail leaves no "-" or empty separators in the letterhead and footer.
        data.put("companyAddressLine",joined(", ",company.getAddressLine(),joined(" ",company.getPostalCode(),company.getCity()),company.getCountry()));
        data.put("companyContactLine",joined("  ·  ",prefixed("Tel. ",company.getPhone()),company.getEmail(),prefixed("Contact: ",company.getContactPerson())));
        data.put("companyLegalLine",joined("  ·  ",prefixed("VAT ID ",company.getTaxId()),prefixed("Reg. ",company.getRegistrationNumber())));
        data.put("companyBankLine",joined("  ·  ",company.getBankName(),prefixed("IBAN ",company.getIban()),prefixed("BIC ",company.getBic())));
        data.put("companyCityOnly",company.getCity()==null?"":company.getCity().trim());
    }

    /** One row of the list of presented documents in the cover letter. */
    public record SubmissionRow(String number, String document, String documentNumber, String date, String originals, String copies, String pages) {}
    /** What the cover letter to the bank says besides the credit data and the company letterhead. */
    public record SubmissionLetter(java.time.LocalDate date, String recipient, String ourReference, String remarks, java.util.List<SubmissionRow> rows) {}

    /** The cover letter for presenting documents to the bank, filled from the credit, the company profile and the list of documents. */
    public byte[] renderSubmissionLetter(LetterOfCredit lc, SubmissionLetter letter) throws IOException {
        de.ostms.lc.tenant.domain.TenantContext.require(lc.getTenantId());
        Map<String, Object> data = letterData(lc, letter);
        data.put("documentsTable", submissionTable(letter.rows()));
        try (XWPFTemplate compiled = XWPFTemplate.compile(new ByteArrayInputStream(ProfessionalDocxTemplate.buildSubmissionLetter())).render(data);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            compiled.write(output);
            return output.toByteArray();
        }
    }

    /** All values of the cover letter by placeholder name, shared by the Word and the PDF output. */
    Map<String, Object> letterData(LetterOfCredit lc, SubmissionLetter letter) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("title", "PRESENTATION OF DOCUMENTS");
        data.put("date", letter.date().format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy", java.util.Locale.ENGLISH)));
        data.put("recipient", value(letter.recipient()));
        data.put("ourReference", value(letter.ourReference()));
        data.put("remarks", value(letter.remarks()));
        data.put("lcReference", value(lc.getReference()));
        data.put("applicant", value(lc.getApplicant()));
        data.put("issuingBank", value(lc.getIssuingBank()));
        data.put("amount", value(lc.getCurrency()) + " " + (lc.getAmount() == null ? "-" : money(lc.getAmount())));
        data.put("expiry", lc.getExpiryDate() == null ? "-" : lc.getExpiryDate().format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy", java.util.Locale.ENGLISH)));
        data.put("latestShipment", lc.getLatestShipmentDate() == null ? "-" : lc.getLatestShipmentDate().format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy", java.util.Locale.ENGLISH)));
        putCompany(data, lc);
        String bank = (String) data.get("companyBankLine");
        var intro = new StringBuilder("We refer to the above documentary credit");
        if (lc.getIssuingBank() != null && !lc.getIssuingBank().isBlank()) intro.append(" issued by ").append(oneLine(lc.getIssuingBank()));
        if (lc.getApplicant() != null && !lc.getApplicant().isBlank()) intro.append(" for the account of ").append(oneLine(lc.getApplicant()));
        if (lc.getAmount() != null) intro.append(" for ").append(data.get("amount"));
        var terms = new java.util.ArrayList<String>();
        if (lc.getLatestShipmentDate() != null) terms.add("latest date of shipment " + data.get("latestShipment"));
        if (lc.getExpiryDate() != null) terms.add("expiry " + data.get("expiry"));
        if (!terms.isEmpty()) intro.append(" (").append(String.join(", ", terms)).append(")");
        data.put("intro", intro.append(". We hereby present the following documents for examination:").toString());
        data.put("paymentRequest", "Please examine the documents in accordance with the terms and conditions of the credit and credit the proceeds on the due date" + (bank.isEmpty() ? "." : " to our account with " + bank + "."));
        data.put("placeDate", joined(", ", (String) data.get("companyCityOnly"), (String) data.get("date")));
        return data;
    }

    private Object submissionTable(java.util.List<SubmissionRow> rows) {
        var table = Tables.of().border(border()).width(17.46, new double[]{1.0, 7.0, 4.0, 1.9, 1.7, 1.86});
        table.addRow(Rows.of("No.", "Document", "Document no. / date", "Originals", "Copies", "Pages").textBold().textColor("FFFFFF").bgColor(ProfessionalDocxTemplate.NAVY).center().create());
        int n = 0;
        for (var row : rows) {
            var cells = new com.deepoove.poi.data.CellRenderData[]{cell(row.number(), false), cell(row.document(), false), cell(row.documentNumber() + (row.date().isBlank() ? "" : "\n" + row.date()), false),
                    cell(row.originals(), true), cell(row.copies(), true), cell(row.pages(), true)};
            var builder = Rows.of(cells);
            table.addRow((n++ % 2 == 1 ? builder.bgColor("F5F7FB") : builder).create());
        }
        return table.create();
    }

    private static com.deepoove.poi.data.style.BorderStyle border() {
        var style = new com.deepoove.poi.data.style.BorderStyle();
        style.setColor(ProfessionalDocxTemplate.LINE); style.setSize(4); style.setType(org.apache.poi.xwpf.usermodel.XWPFTable.XWPFBorderType.SINGLE);
        return style;
    }

    private static com.deepoove.poi.data.CellRenderData cell(String text, boolean right) {
        var builder = com.deepoove.poi.data.Cells.of(text);
        return (right ? builder.horizontalRight() : builder).create();
    }

    private static com.deepoove.poi.data.RowRenderData row(String bg, boolean bold, Object... values) {
        var cells = new com.deepoove.poi.data.CellRenderData[values.length];
        for (int i = 0; i < values.length; i++) cells[i] = cell(String.valueOf(values[i]), i >= 2 && i != 3);
        var builder = com.deepoove.poi.data.Rows.of(cells);
        if (bg != null) builder = builder.bgColor(bg);
        if (bold) builder = builder.textBold();
        return builder.create();
    }

    private Object itemsTable(GeneratedDocumentRequest request, String currency) {
        var items = request.items() == null ? java.util.List.<de.ostms.lc.document.api.GeneratedDocumentItemRequest>of() : request.items();
        boolean packing = request.type() == DocumentType.PACKING_LIST;
        boolean priced = request.type() == DocumentType.COMMERCIAL_INVOICE;
        String cur = currency == null || currency.isBlank() ? "" : " " + currency;
        double[] widths = packing ? new double[]{1.1, 6.2, 1.7, 1.3, 1.9, 2.6, 2.66} : priced ? new double[]{1.1, 7.3, 1.9, 1.4, 2.9, 2.86} : new double[]{1.1, 11.2, 2.8, 2.36};
        var table = Tables.of().border(border()).width(17.46, widths);
        var header = packing ? Rows.of("Pos.", "Description", "Quantity", "Unit", "Packages", "Net kg", "Gross kg")
                : priced ? Rows.of("Pos.", "Description", "Quantity", "Unit", "Unit price" + cur, "Amount" + cur)
                : Rows.of("Pos.", "Description", "Quantity", "Unit");
        table.addRow(header.textBold().textColor("FFFFFF").bgColor(ProfessionalDocxTemplate.NAVY).center().create());
        if (items.isEmpty()) {
            table.addRow(packing ? row(null, false, "1", value(request.description()), value(request.quantity()), "", "", "", "")
                    : priced ? row(null, false, "1", value(request.description()), value(request.quantity()), "", "", "")
                    : row(null, false, "1", value(request.description()), value(request.quantity()), ""));
            return table.create();
        }
        int n = 0;
        for (var item : items) {
            String bg = n++ % 2 == 1 ? "F5F7FB" : null;
            table.addRow(packing
                    ? row(bg, false, value(item.position()), value(item.description()), number(item.quantity()), value(item.unit()), number(item.packages()), number(item.netWeight()), number(item.grossWeight()))
                    : priced ? row(bg, false, value(item.position()), value(item.description()), number(item.quantity()), value(item.unit()), money(item.unitPrice()), money(item.amount()))
                    : row(bg, false, value(item.position()), value(item.description()), number(item.quantity()), value(item.unit())));
        }
        var sum = itemSum(request);
        if (priced && sum != null) table.addRow(row("DCE5F2", true, "", "Total", "", "", "", money(sum)));
        return table.create();
    }

    /** The non-empty parts joined by the separator; empty when there are none. */
    static String joined(String separator, String... parts) {
        var kept = new java.util.ArrayList<String>();
        for (String part : parts) if (part != null && !part.isBlank()) kept.add(part.trim());
        return String.join(separator, kept);
    }

    private static String prefixed(String prefix, String value) { return value == null || value.isBlank() ? null : prefix + value.trim(); }

    /** Several lines of an address on one line: "A\nB\nC" -> "A, B, C". */
    static String oneLine(String multiline) {
        if (multiline == null) return "";
        var lines = new java.util.ArrayList<String>();
        for (String line : multiline.split("\\R")) if (!line.isBlank()) lines.add(line.trim());
        return String.join(", ", lines);
    }

    static String money(java.math.BigDecimal value) {
        return value == null ? "-" : new java.text.DecimalFormat("#,##0.00", java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US)).format(value);
    }

    static String number(Number value) {
        if (value == null) return "-";
        var decimal = value instanceof java.math.BigDecimal d ? d : new java.math.BigDecimal(value.toString());
        return new java.text.DecimalFormat("#,##0.##", java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US)).format(decimal);
    }

    static String value(String value) { return value == null || value.isBlank() ? "-" : value.trim(); }
    private String title(DocumentType type){return switch(type){case COMMERCIAL_INVOICE->"COMMERCIAL INVOICE";case PACKING_LIST->"PACKING LIST";case CERTIFICATE_OF_ORIGIN->"CERTIFICATE OF ORIGIN";case BENEFICIARY_CERTIFICATE->"BENEFICIARY'S CERTIFICATE";case QUALITY_CERTIFICATE->"QUALITY / ANALYSIS CERTIFICATE";default->type.getDisplayName().toUpperCase(java.util.Locale.ROOT);};}
}
