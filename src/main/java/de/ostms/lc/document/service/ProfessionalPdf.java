package de.ostms.lc.document.service;

import static de.ostms.lc.document.service.PdfComposer.*;

import de.ostms.lc.document.api.GeneratedDocumentRequest;
import de.ostms.lc.document.domain.DocumentType;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The PDF counterpart of {@link ProfessionalDocxTemplate}: the same sections and values, drawn directly. */
final class ProfessionalPdf {
    private ProfessionalPdf() {}

    private static String s(Map<String, Object> data, String key) {
        Object v = data.get(key);
        return v == null ? "" : v.toString();
    }

    static byte[] document(Map<String, Object> data, GeneratedDocumentRequest request) throws IOException {
        try (PdfComposer pdf = new PdfComposer()) {
            letterhead(pdf, data);
            titleBar(pdf, s(data, "title"), "No. " + s(data, "documentNumber"));
            DocumentType type = request.type();
            infoGrid(pdf, data, type);
            heading(pdf, type == DocumentType.PACKING_LIST ? "PACKING DETAILS" : type == DocumentType.COMMERCIAL_INVOICE ? "GOODS AND PRICES" : "GOODS");
            items(pdf, request, s(data, "currency"));
            if (type == DocumentType.COMMERCIAL_INVOICE) {
                pdf.y += 8;
                pdf.ensure(40);
                pdf.y += 12;
                pdf.textRight(LEFT + WIDTH, pdf.y, BOLD, 11, NAVY, "Total amount: " + s(data, "totalAmount"));
                pdf.y += 12;
                pdf.textRight(LEFT + WIDTH, pdf.y, REGULAR, 8.5f, GREY, "SAY: " + s(data, "amountInWords"));
                pdf.y += 14;
                if (!s(data, "paymentInstruction").isEmpty()) pdf.paragraph(REGULAR, 9, INK, s(data, "paymentInstruction"), 6);
            } else if (type == DocumentType.PACKING_LIST) {
                pdf.y += 8;
                pdf.ensure(24);
                pdf.y += 12;
                pdf.textRight(LEFT + WIDTH, pdf.y, BOLD, 10, NAVY, "Total packages: " + s(data, "packages") + "   |   Net weight: " + s(data, "netWeight") + "   |   Gross weight: " + s(data, "grossWeight"));
                pdf.y += 12;
            }
            pdf.y += 8;
            heading(pdf, "REMARKS");
            pdf.paragraph(REGULAR, 10, INK, s(data, "notes"), 8);
            String declaration = ProfessionalDocxTemplate.declarationText(type).replace("{{companyCountry}}", s(data, "companyCountry"))
                .replace("{{lcReference}}", s(data, "lcReference")).replace("{{applicant}}", s(data, "applicant"));
            if (!declaration.isEmpty()) pdf.paragraph(REGULAR, 9.5f, INK, declaration, 6);
            signature(pdf, data, s(data, "placeDate"));
            footer(pdf, data);
            return pdf.bytes();
        }
    }

    static byte[] letter(Map<String, Object> data, List<DocxTemplateService.SubmissionRow> rows) throws IOException {
        try (PdfComposer pdf = new PdfComposer()) {
            letterhead(pdf, data);
            float top = pdf.y;
            pdf.textAt(LEFT, top + 8, BOLD, 7.5f, GREY, "TO");
            float after = top + 8;
            pdf.y = top + 13;
            pdf.paragraph(REGULAR, 10.5f, INK, s(data, "recipient"), LEFT, 280, 0);
            after = pdf.y;
            pdf.textRight(LEFT + WIDTH, top + 10, BOLD, 10, NAVY, s(data, "date"));
            pdf.textRight(LEFT + WIDTH, top + 24, REGULAR, 9, INK, "Our reference: " + s(data, "ourReference"));
            pdf.textRight(LEFT + WIDTH, top + 36, REGULAR, 9, INK, "L/C no.: " + s(data, "lcReference"));
            pdf.y = Math.max(after, top + 40) + 20;
            pdf.paragraph(BOLD, 12.5f, NAVY, "Presentation of documents under Documentary Credit No. " + s(data, "lcReference"), 8);
            pdf.paragraph(REGULAR, 10.5f, INK, "Dear Sirs,", 6);
            pdf.paragraph(REGULAR, 10.5f, INK, s(data, "intro"), 10);
            heading(pdf, "DOCUMENTS PRESENTED");
            List<String[]> body = new ArrayList<>();
            for (var r : rows) body.add(new String[]{r.number(), r.document(), r.documentNumber() + (r.date().isBlank() ? "" : "\n" + r.date()), r.originals(), r.copies(), r.pages()});
            pdf.table(new float[]{1, 7, 4, 1.9f, 1.7f, 1.86f}, new String[]{"No.", "Document", "Document no. / date", "Originals", "Copies", "Pages"}, body,
                new boolean[]{false, false, false, true, true, true}, null);
            pdf.y += 12;
            if (!s(data, "remarks").equals("-") && !s(data, "remarks").isBlank()) {
                heading(pdf, "REMARKS");
                pdf.paragraph(REGULAR, 10, INK, s(data, "remarks"), 8);
            }
            pdf.paragraph(REGULAR, 10.5f, INK, s(data, "paymentRequest"), 8);
            pdf.paragraph(REGULAR, 10.5f, INK, "Yours faithfully,", 0);
            signature(pdf, data, s(data, "placeDate"));
            footer(pdf, data);
            return pdf.bytes();
        }
    }

    private static void letterhead(PdfComposer pdf, Map<String, Object> data) throws IOException {
        float top = pdf.y;
        float logo = pdf.image((byte[]) data.get("companyLogoBytes"), LEFT + WIDTH - 130, top, 130, 50, true);
        pdf.y = top;
        pdf.paragraph(BOLD, 16, NAVY, s(data, "companyName"), LEFT, 340, 1);
        pdf.paragraph(REGULAR, 8.5f, GREY, s(data, "companyAddressLine"), LEFT, 340, 0);
        pdf.paragraph(REGULAR, 8.5f, GREY, s(data, "companyContactLine"), LEFT, 340, 0);
        pdf.y = Math.max(pdf.y, top + logo) + 6;
        pdf.rule(pdf.y, LEFT, LEFT + WIDTH, 1.2f, NAVY);
        pdf.y += 14;
    }

    private static void titleBar(PdfComposer pdf, String title, String number) throws IOException {
        pdf.box(LEFT, pdf.y, WIDTH, 28, NAVY, null);
        pdf.textAt(LEFT + 10, pdf.y + 19, BOLD, 15, WHITE, title);
        pdf.textRight(LEFT + WIDTH - 10, pdf.y + 18, BOLD, 11, WHITE, number);
        pdf.y += 28 + 14;
    }

    private static void heading(PdfComposer pdf, String text) throws IOException {
        pdf.ensure(40);
        pdf.y += 9;
        pdf.textAt(LEFT, pdf.y, BOLD, 9, ACCENT, text);
        pdf.y += 4;
        pdf.rule(pdf.y, LEFT, LEFT + WIDTH, 0.6f, LINE);
        pdf.y += 6;
    }

    private static void infoGrid(PdfComposer pdf, Map<String, Object> data, DocumentType type) throws IOException {
        float colWidth = WIDTH / 2;
        List<String> shipment = new ArrayList<>(List.of("Port of loading: " + s(data, "portOfLoading"), "Port of discharge: " + s(data, "portOfDischarge")));
        if (type == DocumentType.COMMERCIAL_INVOICE) { shipment.add("Incoterm: " + s(data, "incoterm")); shipment.add("Payment terms: " + s(data, "paymentTerms")); }
        List<String> document = List.of("Document no.: " + s(data, "documentNumber"), "Date: " + s(data, "documentDate"), "L/C reference: " + s(data, "lcReference"));
        block(pdf, colWidth, "SELLER / BENEFICIARY", List.of(s(data, "beneficiary")), "DOCUMENT", document);
        block(pdf, colWidth, "BUYER / APPLICANT", List.of(s(data, "applicant")), "SHIPMENT", shipment);
        pdf.y += 4;
    }

    /** One row of two bordered cells, each with a small caption and lines of text; both cells have the height of the taller one. */
    private static void block(PdfComposer pdf, float colWidth, String leftCaption, List<String> leftLines, String rightCaption, List<String> rightLines) throws IOException {
        final float size = 9.5f, pad = 7, lh = lineHeight(size);
        int a = lines(leftLines, size, colWidth - 2 * pad), b = lines(rightLines, size, colWidth - 2 * pad);
        float height = Math.max(a, b) * lh + 2 * pad + 12;
        pdf.ensure(height);
        for (int side = 0; side < 2; side++) {
            float x = LEFT + side * colWidth;
            pdf.box(x, pdf.y, colWidth, height, null, LINE);
            pdf.textAt(x + pad, pdf.y + pad + 6, BOLD, 7.5f, GREY, side == 0 ? leftCaption : rightCaption);
            float save = pdf.y;
            pdf.y = save + pad + 10;
            for (String line : side == 0 ? leftLines : rightLines) pdf.paragraph(REGULAR, size, INK, line, x + pad, colWidth - 2 * pad, 0);
            pdf.y = save;
        }
        pdf.y += height;
    }

    private static int lines(List<String> texts, float size, float width) {
        int n = 0;
        for (String t : texts) n += Math.max(1, wrap(REGULAR, size, t, width).size());
        return n;
    }

    private static void items(PdfComposer pdf, GeneratedDocumentRequest request, String currency) throws IOException {
        var items = request.items() == null ? List.<de.ostms.lc.document.api.GeneratedDocumentItemRequest>of() : request.items();
        boolean packing = request.type() == DocumentType.PACKING_LIST, priced = request.type() == DocumentType.COMMERCIAL_INVOICE;
        String cur = currency == null || currency.isBlank() ? "" : " " + currency;
        float[] weights = packing ? new float[]{1.1f, 6.2f, 1.7f, 1.3f, 1.9f, 2.6f, 2.66f} : priced ? new float[]{1.1f, 7.3f, 1.9f, 1.4f, 2.9f, 2.86f} : new float[]{1.1f, 11.2f, 2.8f, 2.36f};
        String[] header = packing ? new String[]{"Pos.", "Description", "Quantity", "Unit", "Packages", "Net kg", "Gross kg"}
            : priced ? new String[]{"Pos.", "Description", "Quantity", "Unit", "Unit price" + cur, "Amount" + cur} : new String[]{"Pos.", "Description", "Quantity", "Unit"};
        boolean[] right = new boolean[weights.length];
        for (int i = 2; i < right.length; i++) right[i] = i != 3;
        List<String[]> rows = new ArrayList<>();
        List<Boolean> emphasis = new ArrayList<>();
        if (items.isEmpty()) {
            rows.add(pad(new String[]{"1", DocxTemplateService.value(request.description()), DocxTemplateService.value(request.quantity())}, weights.length));
            emphasis.add(false);
        }
        BigDecimal sum = BigDecimal.ZERO;
        boolean any = false;
        for (var item : items) {
            String[] row = packing ? new String[]{DocxTemplateService.value(item.position()), DocxTemplateService.value(item.description()), DocxTemplateService.number(item.quantity()), DocxTemplateService.value(item.unit()),
                    DocxTemplateService.number(item.packages()), DocxTemplateService.number(item.netWeight()), DocxTemplateService.number(item.grossWeight())}
                : priced ? new String[]{DocxTemplateService.value(item.position()), DocxTemplateService.value(item.description()), DocxTemplateService.number(item.quantity()), DocxTemplateService.value(item.unit()),
                    DocxTemplateService.money(item.unitPrice()), DocxTemplateService.money(item.amount())}
                : new String[]{DocxTemplateService.value(item.position()), DocxTemplateService.value(item.description()), DocxTemplateService.number(item.quantity()), DocxTemplateService.value(item.unit())};
            rows.add(row);
            emphasis.add(false);
            if (item.amount() != null) { sum = sum.add(item.amount()); any = true; }
        }
        if (priced && any) {
            rows.add(new String[]{"", "Total", "", "", "", DocxTemplateService.money(sum)});
            emphasis.add(true);
        }
        pdf.table(weights, header, rows, right, emphasis);
    }

    private static String[] pad(String[] cells, int length) {
        String[] row = new String[length];
        java.util.Arrays.fill(row, "");
        System.arraycopy(cells, 0, row, 0, cells.length);
        return row;
    }

    private static void signature(PdfComposer pdf, Map<String, Object> data, String placeDate) throws IOException {
        pdf.ensure(110);
        pdf.y += 40;
        float top = pdf.y;
        pdf.textAt(LEFT, top, REGULAR, 9.5f, INK, placeDate);
        pdf.textAt(LEFT, top + 11, REGULAR, 8, GREY, "Place, date");
        float x = LEFT + WIDTH / 2 + 10;
        pdf.rule(top + 30, x, LEFT + WIDTH, 0.7f, INK);
        pdf.textAt(x, top + 44, BOLD, 9.5f, INK, s(data, "companyName"));
        pdf.textAt(x, top + 55, REGULAR, 8, GREY, "Authorised signatory" + (s(data, "companyContactPerson").equals("-") || s(data, "companyContactPerson").isBlank() ? "" : "  |  " + s(data, "companyContactPerson")));
        pdf.y = top + 62;
    }

    private static void footer(PdfComposer pdf, Map<String, Object> data) throws IOException {
        pdf.footers(List.of(DocxTemplateService.joined("  |  ", s(data, "companyName"), s(data, "companyAddressLine")), s(data, "companyLegalLine"), s(data, "companyBankLine")));
    }
}
