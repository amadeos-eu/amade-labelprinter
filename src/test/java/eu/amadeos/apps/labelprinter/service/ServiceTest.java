package eu.amadeos.apps.labelprinter.service;

import eu.amadeos.apps.labelprinter.FakeRunner;
import eu.amadeos.apps.labelprinter.domain.Align;
import eu.amadeos.apps.labelprinter.domain.Background;
import eu.amadeos.apps.labelprinter.domain.Box;
import eu.amadeos.apps.labelprinter.domain.CodeType;
import eu.amadeos.apps.labelprinter.domain.CutGuides;
import eu.amadeos.apps.labelprinter.domain.DataSource;
import eu.amadeos.apps.labelprinter.domain.DataTable;
import eu.amadeos.apps.labelprinter.domain.Element;
import eu.amadeos.apps.labelprinter.domain.LabelDesign;
import eu.amadeos.apps.labelprinter.domain.LabelDocument;
import eu.amadeos.apps.labelprinter.domain.LabelShape;
import eu.amadeos.apps.labelprinter.domain.Merge;
import eu.amadeos.apps.labelprinter.domain.MergeSettings;
import eu.amadeos.apps.labelprinter.domain.PrintPlan;
import eu.amadeos.apps.labelprinter.domain.ShapeKind;
import eu.amadeos.apps.labelprinter.domain.SheetKind;
import eu.amadeos.apps.labelprinter.domain.SheetTemplate;
import eu.amadeos.apps.labelprinter.domain.Side;
import eu.amadeos.apps.labelprinter.domain.TextStyle;
import eu.amadeos.apps.labelprinter.repository.LabelPreferences;
import eu.amadeos.commons.contacts.ContactDirectory;
import eu.amadeos.commons.process.ProgramMissingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceTest {
    @TempDir
    Path dir;

    static final SheetTemplate CARDS = new SheetTemplate("a4-85x55-2x5", SheetKind.BUSINESS_CARD, 210, 297, 2, 5,
            85, 55, 15, 13.5, 95, 55, 0, LabelShape.RECTANGLE, 0);

    // ---- catalogue --------------------------------------------------------------------------------------------

    @Test
    void theCatalogueHasManyValidSheets() {
        List<SheetTemplate> sheets = SheetCatalog.loadBuiltIn();
        assertTrue(sheets.size() >= 70, "sheets: " + sheets.size());
        Set<String> ids = new HashSet<>();
        for (SheetTemplate s : sheets) assertTrue(ids.add(s.id()), "twice: " + s.id());
        for (SheetKind k : SheetKind.values()) {
            assertTrue(sheets.stream().anyMatch(s -> s.kind() == k), "no sheet of " + k);
        }
    }

    @Test
    void theStandardSheetsExist() {
        SheetCatalog c = new SheetCatalog(dir.resolve("own.json"));
        assertEquals("a4-70x37-3x8", c.standard(false).id());
        assertEquals("letter-66_7x25_4-3x10", c.standard(true).id());
    }

    @Test
    void findFiltersByKindPaperAndSize() {
        SheetCatalog c = new SheetCatalog(dir.resolve("own.json"));
        List<SheetTemplate> cards = c.find(SheetKind.BUSINESS_CARD, false, SheetTemplate.Paper.A4, "");
        assertFalse(cards.isEmpty());
        assertTrue(cards.stream().allMatch(s -> s.kind() == SheetKind.BUSINESS_CARD));
        assertTrue(cards.stream().allMatch(s -> SheetTemplate.Paper.A4.matches(s.paperW(), s.paperH())));
        assertTrue(c.find(null, false, null, "70x37").stream().anyMatch(s -> s.id().equals("a4-70x37-3x8")));
        assertTrue(c.find(null, true, null, "").isEmpty());
    }

    @Test
    void ownSheetsAreSavedReplacedAndDeleted() throws IOException {
        Path file = dir.resolve("config/own-sheets.json");
        SheetCatalog c = new SheetCatalog(file);
        SheetTemplate saved = c.saveOwn(CARDS);
        assertTrue(saved.isOwn());
        assertTrue(Files.exists(file));
        SheetCatalog again = new SheetCatalog(file);
        assertEquals(List.of(saved), again.own());
        assertEquals(saved, again.byId(saved.id()).orElseThrow());
        SheetTemplate changed = new SheetTemplate(saved.id(), SheetKind.NAME_BADGE, 210, 297, 2, 4, 90, 60, 10,
                20, 100, 65, 3, LabelShape.RECTANGLE, 0);
        again.saveOwn(changed);
        assertEquals(List.of(changed), new SheetCatalog(file).own());
        again.deleteOwn(saved.id());
        assertTrue(new SheetCatalog(file).own().isEmpty());
    }

    @Test
    void aBrokenOwnFileIsIgnored() throws IOException {
        Path file = dir.resolve("own.json");
        Files.writeString(file, "{ not json");
        assertTrue(new SheetCatalog(file).own().isEmpty());
    }

    @Test
    void sheetsSurviveTheirCodec() {
        SheetTemplate disc = new SheetTemplate("own-1", SheetKind.DISC, 210, 297, 1, 2, 117, 117, 46.5, 21, 117,
                138, 0, LabelShape.DISC, 41);
        assertEquals(disc, SheetCodec.read(SheetCodec.write(disc)));
    }

    // ---- CSV --------------------------------------------------------------------------------------------------

    @Test
    void csvSeparatorsAreDetected() {
        assertEquals(';', CsvReader.detect("a;b;c\n1;2;3".getBytes(StandardCharsets.UTF_8)).separator());
        assertEquals(',', CsvReader.detect("a,b\n1,2".getBytes(StandardCharsets.UTF_8)).separator());
        assertEquals('\t', CsvReader.detect("a\tb\n1\t2".getBytes(StandardCharsets.UTF_8)).separator());
    }

    @Test
    void csvReadsQuotesAndLineBreaksInFields() {
        DataTable t = CsvReader.read("Name;Note\n\"Berger; Anna\";\"two\nlines\"\n\"Say \"\"hi\"\"\";x"
                .getBytes(StandardCharsets.UTF_8), "list.csv", true);
        assertEquals(List.of("Name", "Note"), t.columns());
        assertEquals(2, t.size());
        assertEquals("Berger; Anna", t.value(0, 0));
        assertEquals("two\nlines", t.value(0, 1));
        assertEquals("Say \"hi\"", t.value(1, 0));
    }

    @Test
    void csvEncodingsAreDetected() {
        byte[] latin1 = "Name\nMüller".getBytes(StandardCharsets.ISO_8859_1);
        assertEquals("Müller", CsvReader.read(latin1, "x.csv", true).value(0, 0));
        byte[] bom = ("﻿Name\nJosé").getBytes(StandardCharsets.UTF_8);
        DataTable t = CsvReader.read(bom, "x.csv", true);
        assertEquals("Name", t.columns().getFirst());
        assertEquals("José", t.value(0, 0));
    }

    @Test
    void csvWithoutHeaderNamesItsColumns() {
        DataTable t = CsvReader.read("Anna;Ulm\nBernd;Bonn".getBytes(StandardCharsets.UTF_8), "x.csv", false);
        assertEquals(2, t.size());
        assertEquals(2, t.columns().size());
        assertFalse(t.columns().getFirst().equals(t.columns().get(1)));
    }

    @Test
    void csvColumnNamesAreMadeUnique() {
        DataTable t = CsvReader.read("Name;Name;\n1;2;3".getBytes(StandardCharsets.UTF_8), "x.csv", true);
        assertEquals(3, new HashSet<>(t.columns()).size());
    }

    @Test
    void emptyLinesAreSkipped() {
        DataTable t = CsvReader.read("Name\nAnna\n\n\nBernd\n".getBytes(StandardCharsets.UTF_8), "x.csv", true);
        assertEquals(2, t.size());
    }

    // ---- spreadsheets -----------------------------------------------------------------------------------------

    static byte[] zip(Map<String, String> entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            for (Map.Entry<String, String> e : entries.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue().getBytes(StandardCharsets.UTF_8));
            }
        }
        return out.toByteArray();
    }

    @Test
    void xlsxFirstSheetIsRead() throws IOException {
        String ns = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
        byte[] file = zip(Map.of(
                "xl/workbook.xml", "<workbook xmlns=\"" + ns + "\"/>",
                "xl/sharedStrings.xml", "<sst xmlns=\"" + ns + "\"><si><t>Name</t></si><si><t>Zip</t></si>"
                        + "<si><t>Anna</t></si></sst>",
                "xl/worksheets/sheet1.xml", "<worksheet xmlns=\"" + ns + "\"><sheetData>"
                        + "<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"B1\" t=\"s\"><v>1</v></c></row>"
                        + "<row r=\"2\"><c r=\"A2\" t=\"s\"><v>2</v></c><c r=\"B2\"><v>89073.0</v></c></row>"
                        + "<row r=\"4\"><c r=\"B4\" t=\"inlineStr\"><is><t>10115</t></is></c></row>"
                        + "</sheetData></worksheet>"));
        DataTable t = SpreadsheetReader.read(file, "list.xlsx", true);
        assertEquals(List.of("Name", "Zip"), t.columns());
        assertEquals("Anna", t.value(0, 0));
        assertEquals("89073", t.value(0, 1));
        assertEquals("10115", t.value(t.size() - 1, 1));
    }

    @Test
    void odsFirstTableIsRead() throws IOException {
        String content = "<office:document-content xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\""
                + " xmlns:table=\"urn:oasis:names:tc:opendocument:xmlns:table:1.0\""
                + " xmlns:text=\"urn:oasis:names:tc:opendocument:xmlns:text:1.0\"><office:body><office:spreadsheet>"
                + "<table:table><table:table-row><table:table-cell><text:p>Name</text:p></table:table-cell>"
                + "<table:table-cell><text:p>City</text:p></table:table-cell></table:table-row>"
                + "<table:table-row><table:table-cell table:number-columns-repeated=\"2\"><text:p>Ulm</text:p>"
                + "</table:table-cell></table:table-row>"
                + "<table:table-row table:number-rows-repeated=\"1000\"><table:table-cell/></table:table-row>"
                + "</table:table></office:spreadsheet></office:body></office:document-content>";
        DataTable t = SpreadsheetReader.read(zip(Map.of("content.xml", content)), "list.ods", true);
        assertEquals(List.of("Name", "City"), t.columns());
        assertEquals(1, t.size());
        assertEquals("Ulm", t.value(0, 1));
    }

    @Test
    void otherZipsAreNotSpreadsheets() throws IOException {
        assertTrue(SpreadsheetReader.handles("A.XLSX"));
        assertFalse(SpreadsheetReader.handles("a.csv"));
        byte[] other = zip(Map.of("readme.txt", "hi"));
        assertThrows(IOException.class, () -> SpreadsheetReader.read(other, "x.ods", true));
    }

    @Test
    void doctypesAreRefused() throws IOException {
        byte[] evil = zip(Map.of("content.xml", "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM "
                + "\"file:///etc/passwd\">]><x>&e;</x>"));
        assertThrows(IOException.class, () -> SpreadsheetReader.read(evil, "x.ods", true));
    }

    // ---- data loader ------------------------------------------------------------------------------------------

    static final String VCARDS = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Anna Berger
            N:Berger;Anna;;Dr.;
            ORG:Berger GmbH
            TITLE:Owner
            TEL;TYPE=WORK:+49 731 123
            ADR;TYPE=WORK:;;Hauptstr. 1;Ulm;;89073;Germany
            EMAIL:anna@example.org
            CATEGORIES:Club,Christmas
            END:VCARD
            BEGIN:VCARD
            VERSION:3.0
            FN:Bernd Albrecht
            N:Albrecht;Bernd;;;
            CATEGORIES:Club
            END:VCARD
            """;

    DataLoader loader() throws IOException {
        Path books = dir.resolve("contacts");
        Files.createDirectories(books.resolve("personal"));
        Files.writeString(books.resolve("personal/people.vcf"), VCARDS.replace("\n", "\r\n"));
        return new DataLoader(new ContactDirectory(books, Clock.systemUTC()), key -> key);
    }

    @Test
    void contactsBecomeRowsWithAddressColumns() throws IOException {
        DataTable t = loader().contacts("Christmas");
        assertEquals(1, t.size());
        assertEquals("Christmas", t.source());
        assertEquals("Anna", t.value(0, t.column("column.first-name").orElseThrow()));
        assertEquals("Dr.", t.value(0, t.column("column.prefix").orElseThrow()));
        assertEquals("Hauptstr. 1", t.value(0, t.column("column.street").orElseThrow()));
        assertEquals("89073", t.value(0, t.column("column.postcode").orElseThrow()));
        assertEquals("+49 731 123", t.value(0, t.column("column.phone").orElseThrow()));
        assertEquals("Owner", t.value(0, t.column("column.job-title").orElseThrow()));
    }

    @Test
    void groupsAndCounts() throws IOException {
        DataLoader l = loader();
        assertEquals(List.of("Christmas", "Club"), l.groups());
        assertEquals(2, l.contactCount(""));
        assertEquals(2, l.contactCount("Club"));
        assertEquals("source.contacts.all", l.contacts("").source());
    }

    @Test
    void vcardFilesAreReadLikeContacts() throws IOException {
        Path vcf = dir.resolve("export.vcf");
        Files.writeString(vcf, VCARDS);
        DataTable t = loader().load(DataSource.file(vcf.toString(), true));
        assertEquals(2, t.size());
        assertEquals("export.vcf", t.source());
    }

    @Test
    void csvFilesAreReadByTheLoader() throws IOException {
        Path csv = dir.resolve("members.csv");
        Files.writeString(csv, "Name;City\nAnna;Ulm\n");
        DataTable t = loader().load(DataSource.file(csv.toString(), true));
        assertEquals(List.of("Name", "City"), t.columns());
        assertEquals(DataTable.NONE, loader().load(DataSource.NONE));
        assertThrows(IOException.class, () -> loader().load(DataSource.file(dir.resolve("no.csv").toString(),
                true)));
    }

    @Test
    void typedListsHaveLinesAndText() throws IOException {
        DataTable t = loader().typed("Anna Berger\nHauptstr. 1\n89073 Ulm\n\n\nBernd\n");
        assertEquals(2, t.size());
        assertEquals("column.text", t.columns().getFirst());
        assertEquals("Anna Berger\nHauptstr. 1\n89073 Ulm", t.value(0, 0));
        assertEquals("89073 Ulm", t.value(0, 3));
        assertEquals("", t.value(1, 2));
        assertEquals("source.typed", t.source());
    }

    // ---- files ------------------------------------------------------------------------------------------------

    static LabelDocument sample() {
        Element.Text text = new Element.Text("t1", new Box(3, 3, 60, 20), "«Name»\n«City»",
                TextStyle.DEFAULT.withBold(true).withAlign(Align.CENTER).withColor(new Color(0x147A84))
                        .withBackground(new Color(0xFDF2D0)), true, false);
        Element.Figure figure = new Element.Figure("f1", new Box(1, 1, 10, 5), ShapeKind.ELLIPSE, Color.RED, null,
                0.5, 2);
        Element.Code code = new Element.Code("c1", new Box(60, 30, 20, 20), CodeType.QR, "«Phone»", Color.BLACK,
                false);
        Element.Picture picture = new Element.Picture("p1", new Box(5, 30, 20, 20), "logo.png", true);
        LabelDesign d = LabelDesign.empty(CARDS).withTwoSided(true).withCutGuides(CutGuides.CROP_MARKS)
                .withCounter(5, 3);
        d = d.withSide(false, new Side(new Background(new Color(0x2C3E80), null, true), List.of(text, figure,
                picture)));
        d = d.withSide(true, new Side(new Background(null, "logo.png", false), List.of(code)));
        return new LabelDocument(d, Map.of("logo.png", png()), DataSource.file("/home/a/list.csv", false),
                new MergeSettings(Set.of(2, 5), "City = Ulm", "Name", 2, Map.of("Name", "Full name")));
    }

    static byte[] png() {
        try {
            BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void labelFilesKeepEverything() throws IOException {
        LabelDocument doc = sample();
        Path file = dir.resolve("cards.label");
        LabelFiles.write(doc, file);
        LabelDocument read = LabelFiles.read(file);
        assertEquals(doc.design(), read.design());
        assertEquals(doc.source(), read.source());
        assertEquals(doc.merge(), read.merge());
        assertArrayEquals(doc.images().get("logo.png"), read.images().get("logo.png"));
    }

    @Test
    void unusedPicturesAreNotWritten() throws IOException {
        LabelDocument doc = sample();
        doc = doc.withImage("unused.png", png()).document();
        Path file = dir.resolve("cards.label");
        LabelFiles.write(doc, file);
        assertEquals(Set.of("logo.png"), LabelFiles.read(file).images().keySet());
    }

    @Test
    void brokenFilesAreReported() throws IOException {
        Path file = dir.resolve("broken.label");
        Files.writeString(file, "not a zip");
        assertThrows(IOException.class, () -> LabelFiles.read(file));
        Path empty = dir.resolve("empty.label");
        Files.write(empty, zip(Map.of("other.txt", "x")));
        assertThrows(IOException.class, () -> LabelFiles.read(empty));
    }

    @Test
    void picturePathsCannotLeaveTheFile() throws IOException {
        Path file = dir.resolve("evil.label");
        String json = LabelFiles.toJson(sample()).toString();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            z.putNextEntry(new ZipEntry("label.json"));
            z.write(eu.amadeos.commons.json.Json.write(LabelFiles.toJson(sample())).getBytes(StandardCharsets.UTF_8));
            z.putNextEntry(new ZipEntry("pictures/../../escape.png"));
            z.write(png());
        }
        Files.write(file, out.toByteArray());
        LabelDocument read = LabelFiles.read(file);
        assertFalse(read.images().keySet().stream().anyMatch(n -> n.contains("/") || n.contains("..")), json);
    }

    @Test
    void coloursAreHex() {
        assertEquals("#147A84", LabelFiles.hex(new Color(0x147A84)));
        assertEquals(new Color(0x147A84), LabelFiles.color("#147a84"));
        assertNull(LabelFiles.color(null));
        assertNull(LabelFiles.color("blue"));
    }

    // ---- preferences ------------------------------------------------------------------------------------------

    @Test
    void preferencesRememberColoursFilesAndOffsets() {
        Path file = dir.resolve("labelprinter.properties");
        LabelPreferences p = new LabelPreferences(file);
        for (int i = 0; i < 10; i++) p.addRecentColor(new Color(i, i, i));
        p.addRecentColor(new Color(3, 3, 3));
        p.setOffset("Office laser", 0.5, -20);
        p.setLastPrinter("Office laser");
        p.setLastSheet("a4-70x37-3x8");
        p.addRecentFile(dir.resolve("a.label"));
        p.addRecentFile(dir.resolve("b.label"));
        p.addRecentFile(dir.resolve("a.label"));
        LabelPreferences again = new LabelPreferences(file);
        assertEquals(LabelPreferences.RECENT_COLORS, again.recentColors().size());
        assertEquals(new Color(3, 3, 3), again.recentColors().getFirst());
        assertArrayEquals(new double[]{0.5, -LabelPreferences.MAX_OFFSET}, again.offset("Office laser"));
        assertArrayEquals(new double[]{0, 0}, again.offset("Other"));
        assertEquals("Office laser", again.lastPrinter().orElseThrow());
        assertEquals("a4-70x37-3x8", again.lastSheet().orElseThrow());
        assertEquals(List.of(dir.resolve("a.label"), dir.resolve("b.label")), again.recentFiles());
    }

    // ---- drawing ----------------------------------------------------------------------------------------------

    static final java.awt.font.FontRenderContext FRC = new java.awt.font.FontRenderContext(null, true, true);

    @Test
    void textsShrinkToFitTheirBox() {
        Element.Text big = new Element.Text("t", new Box(0, 0, 20, 5), "A rather long line of text",
                TextStyle.DEFAULT.withSize(20), true, true);
        double points = LabelPainter.fittedPoints(big, LabelPainter.lines(big, f -> null), FRC);
        assertTrue(points < 20 && points >= LabelPainter.MIN_POINTS, "points " + points);
        Element.Text fixed = new Element.Text("t", big.box(), big.text(), big.style(), false, true);
        assertEquals(20, LabelPainter.fittedPoints(fixed, List.of("x"), FRC), 1e-9);
    }

    @Test
    void linesAreFilled() {
        Element.Text t = new Element.Text("t", new Box(0, 0, 20, 5), "«Name»\n«Company»\n«City»",
                TextStyle.DEFAULT, true, true);
        Map<String, String> v = Map.of("Name", "Anna", "Company", "", "City", "Ulm");
        assertEquals(List.of("Anna", "Ulm"), LabelPainter.lines(t, v::get));
        assertEquals(List.of(), LabelPainter.lines(new Element.Text("e", t.box(), "", TextStyle.DEFAULT, true,
                true), v::get));
        assertEquals(List.of("Anna\nUlm"), LabelPainter.texts(Side.EMPTY.with(t), v::get));
    }

    @Test
    void codesEncodeOrRefuse() {
        assertNotNull(LabelPainter.encode(CodeType.QR, "https://amadeos.eu"));
        assertNotNull(LabelPainter.encode(CodeType.DATA_MATRIX, "ABC"));
        assertNotNull(LabelPainter.encode(CodeType.EAN_13, "400638133393"));
        assertNotNull(LabelPainter.encode(CodeType.EAN_8, "9638507"));
        assertNotNull(LabelPainter.encode(CodeType.CODE_128, "Hello 123"));
        assertNotNull(LabelPainter.encode(CodeType.CODE_39, "ABC-123"));
        assertNull(LabelPainter.encode(CodeType.EAN_13, "letters"));
        assertNull(LabelPainter.encode(CodeType.QR, ""));
    }

    @Test
    void figuresHaveOutlines() {
        Element.Figure line = new Element.Figure("l", new Box(0, 0, 10, 2), ShapeKind.LINE, null, Color.BLACK,
                0.3, 0);
        assertNotNull(LabelPainter.outline(line));
        Element.Figure round = new Element.Figure("r", new Box(0, 0, 10, 5), ShapeKind.RECTANGLE, Color.RED, null,
                0, 2);
        assertTrue(LabelPainter.outline(round).contains(5, 2.5));
    }

    /** Draws a whole A4 sheet at 2 pixels per millimetre. */
    static BufferedImage draw(LabelDesign design, PrintPlan plan, boolean back, SheetPainter.Options options) {
        BufferedImage img = new BufferedImage(420, 594, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 420, 594);
        g.scale(2, 2);
        new SheetPainter(new LabelPainter(n -> null)).paint(g, design, plan, 0, back, options);
        g.dispose();
        return img;
    }

    static boolean dark(BufferedImage img, int x, int y) {
        Color c = new Color(img.getRGB(x, y));
        return c.getRed() + c.getGreen() + c.getBlue() < 600;
    }

    @Test
    void backgroundsFillEveryLabel() {
        LabelDesign d = LabelDesign.empty(CARDS).withCutGuides(CutGuides.NONE);
        d = d.withSide(false, d.front().withBackground(new Background(new Color(0x2C3E80), null, false)));
        BufferedImage img = draw(d, new PrintPlan(CARDS, List.of(label()), 0), false, SheetPainter.Options.PRINT);
        assertTrue(dark(img, 2 * 50, 2 * 40));
        assertFalse(dark(img, 2 * 150, 2 * 40), "only one label was printed");
    }

    @Test
    void cropMarksAreOnlyInTheMargin() {
        LabelDesign d = LabelDesign.empty(CARDS);
        assertEquals(CutGuides.CROP_MARKS, d.cutGuides());
        BufferedImage img = draw(d, new PrintPlan(CARDS, List.of(), 0), false, SheetPainter.Options.PRINT);
        int marks = 0;
        for (int y = 0; y < 2 * 13; y++) if (dark(img, 2 * 15, y)) marks++;
        assertTrue(marks > 2, "a mark above the first card's left edge");
        for (int x = 2 * 16; x < 2 * 99; x++) {
            for (int y = 2 * 14; y < 2 * 68; y++) assertFalse(dark(img, x, y), "nothing on the card at " + x + "," + y);
        }
    }

    @Test
    void theBackIsMirrored() {
        assertEquals(SheetPainter.position(CARDS, 0, false).getX(), 15, 1e-9);
        assertEquals(SheetPainter.position(CARDS, 0, true).getX(), 210 - 15 - 85, 1e-9);
    }

    @Test
    void bleedIsAtMostHalfTheGap() {
        java.awt.geom.Rectangle2D r = SheetPainter.bleed(CARDS, CARDS.label(0));
        assertEquals(15 - 2, r.getX(), 1e-9);
        assertEquals(13.5, r.getY(), 1e-9);
    }

    static Merge.Label label() {
        return Merge.labels(DataTable.NONE, MergeSettings.ALL, LabelDesign.empty(CARDS), LocalDate.now(),
                Locale.UK).getFirst();
    }

    @Test
    void twoSidedPagesAlternate() throws Exception {
        LabelDesign two = LabelDesign.empty(CARDS).withTwoSided(true);
        List<Merge.Label> many = new ArrayList<>();
        for (int i = 0; i < 15; i++) many.add(label());
        SheetPages pages = new SheetPages(two, new PrintPlan(CARDS, many, 0), new SheetPainter(new LabelPainter(
                n -> null)), SheetPainter.Options.PRINT);
        assertTrue(pages.twoSided());
        assertEquals(4, pages.getNumberOfPages());
        PageFormat f = pages.getPageFormat(0);
        assertEquals(210 / 25.4 * 72, f.getWidth(), 0.5);
        BufferedImage img = new BufferedImage(600, 850, BufferedImage.TYPE_INT_RGB);
        assertEquals(Printable.PAGE_EXISTS, pages.print(img.createGraphics(), f, 3));
        assertEquals(Printable.NO_SUCH_PAGE, pages.print(img.createGraphics(), f, 4));
        SheetPages one = new SheetPages(LabelDesign.empty(CARDS), new PrintPlan(CARDS, many, 0),
                new SheetPainter(new LabelPainter(n -> null)), SheetPainter.Options.PRINT);
        assertEquals(2, one.getNumberOfPages());
        assertEquals(1, SheetPages.testPage(two, new PrintPlan(CARDS, many, 0), 0.5, 0.5).getNumberOfPages());
    }

    // ---- PDF --------------------------------------------------------------------------------------------------

    @Test
    void pdfIsMadeWithGhostscript() throws Exception {
        FakeRunner runner = new FakeRunner().prints(PdfExport.PROGRAM, "");
        SheetPages pages = new SheetPages(LabelDesign.empty(CARDS), new PrintPlan(CARDS, List.of(label()), 0),
                new SheetPainter(new LabelPainter(n -> null)), SheetPainter.Options.PRINT);
        new PdfExport(runner).export(pages, 210, 297, dir.resolve("out.pdf"), "Cards");
        String line = runner.lines().getFirst();
        assertTrue(line.startsWith("ps2pdf -dDEVICEWIDTHPOINTS=595 -dDEVICEHEIGHTPOINTS=842 -dFIXEDMEDIA"), line);
        assertTrue(line.endsWith(dir.resolve("out.pdf").toString()), line);
        try (var files = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            String ps = line.split(" ")[line.split(" ").length - 2];
            assertFalse(Files.exists(Path.of(ps)), "the PostScript file is removed");
        }
    }

    @Test
    void pdfFailuresAreReported() {
        SheetPages pages = new SheetPages(LabelDesign.empty(CARDS), new PrintPlan(CARDS, List.of(label()), 0),
                new SheetPainter(new LabelPainter(n -> null)), SheetPainter.Options.PRINT);
        assertThrows(IOException.class, () -> new PdfExport(new FakeRunner().fails(PdfExport.PROGRAM, 1, "bad"))
                .export(pages, 210, 297, dir.resolve("out.pdf"), "x"));
        assertThrows(ProgramMissingException.class, () -> new PdfExport(new FakeRunner())
                .export(pages, 210, 297, dir.resolve("out.pdf"), "x"));
    }
}
