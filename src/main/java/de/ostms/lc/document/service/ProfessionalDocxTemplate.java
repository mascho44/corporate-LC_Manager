package de.ostms.lc.document.service;

import de.ostms.lc.document.domain.DocumentType;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;

/**
 * The sample Word templates for generated trade documents: letterhead with logo and company data, title bar, two-column header block,
 * item table, totals, declaration, signature block and a footer with bank and registration details and page numbers.
 * All {{placeholders}} are filled by {@link DocxTemplateService}; companies can download the sample, restyle it in Word and upload it again.
 */
final class ProfessionalDocxTemplate {
    static final String NAVY = "1F3A5F", ACCENT = "2F6DB5", GREY = "6B7280", LIGHT = "EEF2F8", LINE = "C9D1DE", FONT = "Calibri";
    /** Text width of the A4 page in twips (page minus left and right margin). */
    static final int CONTENT_WIDTH = 9900;

    private ProfessionalDocxTemplate() {}

    static byte[] build(DocumentType type) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            page(doc);
            letterhead(doc);
            titleBar(doc);
            infoGrid(doc, type);
            heading(doc, type == DocumentType.PACKING_LIST ? "PACKING DETAILS" : type == DocumentType.COMMERCIAL_INVOICE ? "GOODS AND PRICES" : "GOODS");
            paragraph(doc, 0, 80).createRun().setText("{{#itemsTable}}");
            totals(doc, type);
            declaration(doc, type);
            signature(doc);
            footer(doc);
            doc.write(out);
            return out.toByteArray();
        }
    }

    /** The cover letter to the bank when documents are presented under a credit. */
    static byte[] buildSubmissionLetter() throws IOException {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            page(doc);
            letterhead(doc);
            XWPFTable addresses = table(doc, 1, 2, new int[]{58, 42});
            noBorders(addresses);
            XWPFTableCell left = addresses.getRow(0).getCell(0), right = addresses.getRow(0).getCell(1);
            run(first(left), "TO", 7.5, true, GREY);
            run(left.addParagraph(), "{{recipient}}", 10.5, false, "111827");
            XWPFParagraph date = first(right);
            date.setAlignment(ParagraphAlignment.RIGHT);
            run(date, "{{date}}", 10, true, NAVY);
            XWPFParagraph ours = right.addParagraph();
            ours.setAlignment(ParagraphAlignment.RIGHT);
            run(ours, "Our reference: {{ourReference}}", 9, false, "111827");
            XWPFParagraph credit = right.addParagraph();
            credit.setAlignment(ParagraphAlignment.RIGHT);
            run(credit, "L/C no.: {{lcReference}}", 9, false, "111827");
            XWPFParagraph subject = paragraph(doc, 260, 120);
            run(subject, "Presentation of documents under Documentary Credit No. {{lcReference}}", 12.5, true, NAVY);
            run(paragraph(doc, 0, 100), "Dear Sirs,", 10.5, false, "111827");
            run(paragraph(doc, 0, 140), "We refer to the above documentary credit issued by {{issuingBank}} for the account of {{applicant}} for {{amount}} (latest date of shipment {{latestShipment}}, expiry {{expiry}}). We hereby present the following documents for examination:", 10.5, false, "111827");
            heading(doc, "DOCUMENTS PRESENTED");
            paragraph(doc, 0, 80).createRun().setText("{{#documentsTable}}");
            XWPFParagraph remarksHeading = paragraph(doc, 140, 20);
            run(remarksHeading, "REMARKS", 9, true, ACCENT);
            run(paragraph(doc, 0, 100), "{{remarks}}", 10, false, "111827");
            run(paragraph(doc, 80, 120), "Please examine the documents in accordance with the terms and conditions of the credit and credit the proceeds on the due date to our account with {{companyBankName}}, IBAN {{companyIban}}, BIC {{companyBic}}.", 10.5, false, "111827");
            run(paragraph(doc, 80, 60), "Yours faithfully,", 10.5, false, "111827");
            signature(doc, "date");
            footer(doc);
            doc.write(out);
            return out.toByteArray();
        }
    }

    // ---- page and sections -------------------------------------------------------------------------------------------------------------------

    private static void page(XWPFDocument doc) {
        CTSectPr sect = doc.getDocument().getBody().isSetSectPr() ? doc.getDocument().getBody().getSectPr() : doc.getDocument().getBody().addNewSectPr();
        CTPageSz size = sect.isSetPgSz() ? sect.getPgSz() : sect.addNewPgSz();
        size.setW(BigInteger.valueOf(11906)); size.setH(BigInteger.valueOf(16838));
        CTPageMar margin = sect.isSetPgMar() ? sect.getPgMar() : sect.addNewPgMar();
        margin.setLeft(BigInteger.valueOf(1000)); margin.setRight(BigInteger.valueOf(1000));
        margin.setTop(BigInteger.valueOf(850)); margin.setBottom(BigInteger.valueOf(1250));
        margin.setHeader(BigInteger.valueOf(500)); margin.setFooter(BigInteger.valueOf(500));
        XWPFStyles styles = doc.createStyles();
        CTFonts fonts = CTFonts.Factory.newInstance();
        fonts.setAscii(FONT); fonts.setHAnsi(FONT); fonts.setCs(FONT);
        styles.setDefaultFonts(fonts);
    }

    private static void letterhead(XWPFDocument doc) {
        XWPFTable table = table(doc, 1, 2, new int[]{70, 30});
        noBorders(table);
        XWPFTableCell left = table.getRow(0).getCell(0), right = table.getRow(0).getCell(1);
        XWPFParagraph name = first(left);
        run(name, "{{companyName}}", 16, true, NAVY);
        XWPFParagraph address = left.addParagraph();
        run(address, "{{companyAddress}}, {{companyPostalCode}} {{companyCity}}, {{companyCountry}}", 8.5, false, GREY);
        XWPFParagraph contact = left.addParagraph();
        run(contact, "Tel. {{companyPhone}}  ·  {{companyEmail}}  ·  Contact: {{companyContactPerson}}", 8.5, false, GREY);
        XWPFParagraph logo = first(right);
        logo.setAlignment(ParagraphAlignment.RIGHT);
        logo.createRun().setText("{{@companyLogo}}");
        XWPFParagraph rule = paragraph(doc, 40, 120);
        border(rule, NAVY, 12);
    }

    private static void titleBar(XWPFDocument doc) {
        XWPFTable table = table(doc, 1, 2, new int[]{65, 35});
        noBorders(table);
        XWPFTableCell left = table.getRow(0).getCell(0), right = table.getRow(0).getCell(1);
        left.setColor(NAVY); right.setColor(NAVY);
        run(first(left), "{{title}}", 15, true, "FFFFFF");
        XWPFParagraph number = first(right);
        number.setAlignment(ParagraphAlignment.RIGHT);
        run(number, "No. {{documentNumber}}", 11, true, "FFFFFF");
        paragraph(doc, 0, 100);
    }

    private static void infoGrid(XWPFDocument doc, DocumentType type) {
        XWPFTable table = table(doc, 2, 2, new int[]{50, 50});
        softBorders(table);
        labelled(table.getRow(0).getCell(0), "SELLER / BENEFICIARY", "{{beneficiary}}");
        labelled(table.getRow(1).getCell(0), "BUYER / APPLICANT", "{{applicant}}");
        XWPFTableCell reference = table.getRow(0).getCell(1);
        labelled(reference, "DOCUMENT", null);
        keyValue(reference, "Document no.", "{{documentNumber}}");
        keyValue(reference, "Date", "{{documentDate}}");
        keyValue(reference, "L/C reference", "{{lcReference}}");
        XWPFTableCell shipment = table.getRow(1).getCell(1);
        labelled(shipment, "SHIPMENT", null);
        keyValue(shipment, "Port of loading", "{{portOfLoading}}");
        keyValue(shipment, "Port of discharge", "{{portOfDischarge}}");
        if (type == DocumentType.COMMERCIAL_INVOICE) {
            keyValue(shipment, "Incoterm", "{{incoterm}}");
            keyValue(shipment, "Payment terms", "{{paymentTerms}}");
        }
        paragraph(doc, 0, 120);
    }

    private static void heading(XWPFDocument doc, String text) {
        XWPFParagraph p = paragraph(doc, 0, 60);
        run(p, text, 9, true, ACCENT);
        border(p, LINE, 6);
    }

    private static void totals(XWPFDocument doc, DocumentType type) {
        if (type == DocumentType.COMMERCIAL_INVOICE) {
            XWPFParagraph total = paragraph(doc, 100, 20);
            total.setAlignment(ParagraphAlignment.RIGHT);
            run(total, "Total amount: ", 11, true, NAVY);
            run(total, "{{totalAmount}}", 11, true, NAVY);
            XWPFParagraph words = paragraph(doc, 0, 80);
            words.setAlignment(ParagraphAlignment.RIGHT);
            run(words, "SAY: {{amountInWords}}", 8.5, false, GREY);
            XWPFParagraph bank = paragraph(doc, 60, 20);
            run(bank, "Payment to: ", 9, true, NAVY);
            run(bank, "{{companyBankName}}  ·  IBAN {{companyIban}}  ·  BIC {{companyBic}}", 9, false, "111827");
        } else if (type == DocumentType.PACKING_LIST) {
            XWPFParagraph total = paragraph(doc, 100, 20);
            total.setAlignment(ParagraphAlignment.RIGHT);
            run(total, "Total packages: {{packages}}   ·   Net weight: {{netWeight}}   ·   Gross weight: {{grossWeight}}", 10, true, NAVY);
        }
        XWPFParagraph notes = paragraph(doc, 140, 20);
        run(notes, "REMARKS", 9, true, ACCENT);
        run(paragraph(doc, 0, 100), "{{notes}}", 10, false, "111827");
    }

    /** The declaration printed above the signature, with placeholders; empty for documents without one. */
    static String declarationText(DocumentType type) {
        return switch (type) {
            case CERTIFICATE_OF_ORIGIN -> "We hereby certify that the goods described above originate in {{companyCountry}} and were shipped under documentary credit {{lcReference}}.";
            case BENEFICIARY_CERTIFICATE -> "We hereby certify that the goods and documents comply with all terms of documentary credit {{lcReference}} issued for {{applicant}}.";
            case QUALITY_CERTIFICATE -> "We hereby certify that the goods described above conform to the agreed specification and quality requirements of documentary credit {{lcReference}}.";
            case COMMERCIAL_INVOICE -> "We certify that this invoice is true and correct and that the goods are of the origin stated in documentary credit {{lcReference}}.";
            default -> "";
        };
    }

    private static void declaration(XWPFDocument doc, DocumentType type) {
        String text = declarationText(type);
        if (text.isEmpty()) return;
        XWPFParagraph p = paragraph(doc, 100, 120);
        run(p, text, 9.5, false, "111827");
    }

    private static void signature(XWPFDocument doc) { signature(doc, "documentDate"); }

    private static void signature(XWPFDocument doc, String dateKey) {
        paragraph(doc, 200, 0);
        XWPFTable table = table(doc, 1, 2, new int[]{50, 50});
        noBorders(table);
        XWPFTableCell left = table.getRow(0).getCell(0), right = table.getRow(0).getCell(1);
        run(first(left), "{{companyCity}}, {{" + dateKey + "}}", 9.5, false, "111827");
        XWPFParagraph place = left.addParagraph();
        run(place, "Place, date", 8, false, GREY);
        XWPFParagraph space = first(right);
        space.setSpacingAfter(520);
        space.createRun().setText(" ");
        XWPFParagraph line = right.addParagraph();
        border(line, "111827", 6);
        run(line, "", 4, false, GREY);
        XWPFParagraph signer = right.addParagraph();
        run(signer, "{{companyName}}", 9.5, true, "111827");
        XWPFParagraph role = right.addParagraph();
        run(role, "Authorised signatory  ·  {{companyContactPerson}}", 8, false, GREY);
    }

    private static void footer(XWPFDocument doc) {
        XWPFFooter footer = doc.createFooter(HeaderFooterType.DEFAULT);
        XWPFParagraph bank = footer.createParagraph();
        border(bank, LINE, 6, true);
        run(bank, "{{companyName}}  ·  {{companyAddress}}, {{companyPostalCode}} {{companyCity}}", 7.5, false, GREY);
        XWPFParagraph legal = footer.createParagraph();
        run(legal, "VAT ID {{companyTaxId}}  ·  Reg. {{companyRegistrationNumber}}  ·  {{companyBankName}}  ·  IBAN {{companyIban}}  ·  BIC {{companyBic}}", 7.5, false, GREY);
        XWPFParagraph pages = footer.createParagraph();
        pages.setAlignment(ParagraphAlignment.RIGHT);
        run(pages, "Page ", 7.5, false, GREY);
        field(pages, "PAGE");
        run(pages, " of ", 7.5, false, GREY);
        field(pages, "NUMPAGES");
    }

    // ---- building blocks ---------------------------------------------------------------------------------------------------------------------

    private static void labelled(XWPFTableCell cell, String label, String value) {
        XWPFParagraph l = first(cell);
        l.setSpacingAfter(20);
        run(l, label, 7.5, true, GREY);
        if (value != null) {
            XWPFParagraph v = cell.addParagraph();
            v.setSpacingAfter(60);
            run(v, value, 10, false, "111827");
        }
    }

    private static void keyValue(XWPFTableCell cell, String key, String value) {
        XWPFParagraph p = cell.addParagraph();
        p.setSpacingAfter(20);
        run(p, key + ": ", 9, true, NAVY);
        run(p, value, 9, false, "111827");
    }

    private static XWPFTable table(XWPFDocument doc, int rows, int cols, int[] percent) {
        XWPFTable table = doc.createTable(rows, cols);
        table.setWidth(CONTENT_WIDTH);
        CTTblPr properties = table.getCTTbl().getTblPr();
        (properties.isSetTblLayout() ? properties.getTblLayout() : properties.addNewTblLayout()).setType(STTblLayoutType.FIXED);
        CTTblGrid grid = table.getCTTbl().getTblGrid() != null ? table.getCTTbl().getTblGrid() : table.getCTTbl().addNewTblGrid();
        while (grid.sizeOfGridColArray() < cols) grid.addNewGridCol();
        for (int c = 0; c < cols; c++) grid.getGridColArray(c).setW(BigInteger.valueOf(CONTENT_WIDTH * percent[c] / 100));
        for (XWPFTableRow row : table.getRows())
            for (int c = 0; c < cols; c++) row.getCell(c).setWidth(String.valueOf(CONTENT_WIDTH * percent[c] / 100));
        return table;
    }

    private static void noBorders(XWPFTable table) {
        table.setTopBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        table.setBottomBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        table.setLeftBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        table.setRightBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        table.setInsideHBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        table.setInsideVBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
    }

    private static void softBorders(XWPFTable table) {
        table.setTopBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, LINE);
        table.setBottomBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, LINE);
        table.setLeftBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, LINE);
        table.setRightBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, LINE);
        table.setInsideHBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, LINE);
        table.setInsideVBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, LINE);
        table.setCellMargins(80, 120, 80, 120);
    }

    private static XWPFParagraph first(XWPFTableCell cell) { return cell.getParagraphs().get(0); }

    private static XWPFParagraph paragraph(XWPFDocument doc, int before, int after) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(before); p.setSpacingAfter(after);
        return p;
    }

    private static void run(XWPFParagraph p, String text, double size, boolean bold, String color) {
        XWPFRun r = p.createRun();
        r.setFontFamily(FONT); r.setFontSize((int) Math.round(size)); r.setBold(bold); r.setColor(color);
        if (size != Math.rint(size)) r.getCTR().addNewRPr().addNewSz().setVal(BigInteger.valueOf((long) (size * 2)));
        r.setText(text);
    }

    private static void border(XWPFParagraph p, String color, int size) { border(p, color, size, false); }

    private static void border(XWPFParagraph p, String color, int size, boolean top) {
        CTPBdr borders = p.getCTP().isSetPPr() && p.getCTP().getPPr().isSetPBdr() ? p.getCTP().getPPr().getPBdr() : (p.getCTP().isSetPPr() ? p.getCTP().getPPr() : p.getCTP().addNewPPr()).addNewPBdr();
        CTBorder b = top ? borders.addNewTop() : borders.addNewBottom();
        b.setVal(STBorder.SINGLE); b.setSz(BigInteger.valueOf(size)); b.setSpace(BigInteger.valueOf(1)); b.setColor(color);
    }

    private static void field(XWPFParagraph p, String instruction) {
        CTSimpleField field = p.getCTP().addNewFldSimple();
        field.setInstr(" " + instruction + " ");
        CTR run = field.addNewR();
        CTRPr props = run.addNewRPr();
        props.addNewSz().setVal(BigInteger.valueOf(15));
        props.addNewColor().setVal(GREY);
        run.addNewT().setStringValue("1");
    }
}
