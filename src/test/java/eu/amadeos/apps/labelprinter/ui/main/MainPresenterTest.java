package eu.amadeos.apps.labelprinter.ui.main;

import eu.amadeos.apps.labelprinter.Direct;
import eu.amadeos.apps.labelprinter.FakeRunner;
import eu.amadeos.apps.labelprinter.bootstrap.LabelPrinterModule;
import eu.amadeos.apps.labelprinter.domain.Background;
import eu.amadeos.apps.labelprinter.domain.CutGuides;
import eu.amadeos.apps.labelprinter.domain.DataSource;
import eu.amadeos.apps.labelprinter.domain.Element;
import eu.amadeos.apps.labelprinter.domain.MergeSettings;
import eu.amadeos.apps.labelprinter.domain.PrintPlan;
import eu.amadeos.apps.labelprinter.domain.SheetKind;
import eu.amadeos.apps.labelprinter.domain.SheetTemplate;
import eu.amadeos.apps.labelprinter.domain.Box;
import eu.amadeos.apps.labelprinter.repository.LabelPreferences;
import eu.amadeos.apps.labelprinter.service.DataLoader;
import eu.amadeos.apps.labelprinter.service.LabelFiles;
import eu.amadeos.apps.labelprinter.service.PdfExport;
import eu.amadeos.apps.labelprinter.service.SheetCatalog;
import eu.amadeos.commons.contacts.ContactDirectory;
import eu.amadeos.commons.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainPresenterTest {
    @TempDir
    Path dir;

    /** A clock the test moves forward by hand. */
    static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-11-10T10:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }
    }

    final FakeView view = new FakeView();
    final TestClock clock = new TestClock();
    final FakeRunner runner = new FakeRunner();
    final Messages msg = Messages.load(LabelPrinterModule.class, LabelPrinterModule.MESSAGES, Locale.ENGLISH);
    int exits;
    int handbooks;
    SheetCatalog catalog;
    LabelPreferences prefs;
    MainPresenter p;

    @BeforeEach
    void setUp() {
        catalog = new SheetCatalog(dir.resolve("config/own-sheets.json"));
        prefs = new LabelPreferences(dir.resolve("config/labelprinter.properties"));
        prefs.setLastSheet("a4-70x37-3x8");
        DataLoader loader = new DataLoader(new ContactDirectory(dir.resolve("contacts"), Clock.systemUTC()),
                msg::get);
        MainPresenter.Services s = new MainPresenter.Services(catalog, loader, prefs, new PdfExport(runner),
                () -> {
                    handbooks++;
                    throw new IOException("no browser");
                }, new Direct(), Runnable::run, () -> exits++, clock, dir, dir);
        p = new MainPresenter(view, msg, null, s);
        p.start(Optional.empty());
    }

    MainView.State state() {
        return view.last();
    }

    Element selected() {
        return state().design().side(state().back()).find(state().selected()).orElseThrow();
    }

    SheetTemplate cards() {
        return catalog.find(SheetKind.BUSINESS_CARD, false, SheetTemplate.Paper.A4, "85x55").getFirst();
    }

    @Test
    void startsWithAnEmptyLabel() {
        assertEquals("Untitled – Label Printer", state().title());
        assertTrue(state().design().front().elements().isEmpty());
        assertEquals(List.of("Date", "Counter", "Number"), state().fields());
        assertEquals("", state().record());
        assertFalse(state().canUndo());
        assertTrue(state().status().contains("No data"), state().status());
    }

    @Test
    void theLastSheetIsUsedAgain() {
        prefs.setLastSheet(cards().id());
        FakeView other = new FakeView();
        MainPresenter q = new MainPresenter(other, msg, null, new MainPresenter.Services(catalog,
                new DataLoader(new ContactDirectory(dir, clock), msg::get), prefs, new PdfExport(runner), () -> {
        }, new Direct(), Runnable::run, () -> { }, clock, dir, dir));
        q.start(Optional.empty());
        assertEquals(cards(), other.last().design().sheet());
    }

    @Test
    void addingSelectsAndCanBeUndone() {
        p.add(MainPresenter.Add.TEXT);
        assertEquals(1, state().design().front().elements().size());
        assertTrue(selected() instanceof Element.Text t && t.text().equals("Text"));
        assertTrue(state().title().startsWith("*"));
        p.undo();
        assertTrue(state().design().front().elements().isEmpty());
        assertNull(state().selected());
        assertTrue(state().canRedo());
        p.redo();
        assertEquals(1, state().design().front().elements().size());
    }

    @Test
    void everyKindOfElementCanBeAdded() {
        for (MainPresenter.Add a : List.of(MainPresenter.Add.TEXT, MainPresenter.Add.FIELD, MainPresenter.Add.SHAPE,
                MainPresenter.Add.BARCODE, MainPresenter.Add.QR)) p.add(a);
        List<Element> elements = state().design().front().elements();
        assertEquals(5, elements.size());
        assertEquals("«Date»", ((Element.Text) elements.get(1)).text());
        SheetTemplate s = state().design().sheet();
        for (Element e : elements) {
            assertTrue(e.box().x() >= 0 && e.box().x() + e.box().w() <= s.width() + 1e-9, e.toString());
            assertTrue(e.box().y() >= 0 && e.box().y() + e.box().h() <= s.height() + 1e-9, e.toString());
        }
    }

    @Test
    void typingIsOneUndoStep() {
        p.add(MainPresenter.Add.TEXT);
        Element.Text t = (Element.Text) selected();
        p.apply(t.withText("A"));
        p.apply(t.withText("An"));
        p.apply(t.withText("Ann"));
        p.undo();
        assertEquals("Text", ((Element.Text) selected()).text());
    }

    @Test
    void aPauseStartsANewUndoStep() {
        p.add(MainPresenter.Add.TEXT);
        Element.Text t = (Element.Text) selected();
        p.apply(t.withText("A"));
        clock.advance(Duration.ofSeconds(3));
        p.apply(t.withText("AB"));
        p.undo();
        assertEquals("A", ((Element.Text) selected()).text());
    }

    @Test
    void deleteDuplicateAndOrder() {
        p.add(MainPresenter.Add.SHAPE);
        String first = state().selected();
        p.duplicate();
        String copy = state().selected();
        assertNotEquals(first, copy);
        Element a = state().design().front().find(first).orElseThrow();
        Element b = state().design().front().find(copy).orElseThrow();
        assertEquals(a.box().x() + 2, b.box().x(), 1e-9);
        p.toBack();
        assertEquals(copy, state().design().front().elements().getFirst().id());
        p.toFront();
        assertEquals(copy, state().design().front().elements().getLast().id());
        p.deleteSelected();
        assertEquals(List.of(first), state().design().front().elements().stream().map(Element::id).toList());
        assertNull(state().selected());
    }

    @Test
    void droppedFieldsGoIntoTheTextBelowOrMakeANewOne() {
        p.dropField("Name", 10, 10);
        Element.Text t = (Element.Text) selected();
        assertEquals("«Name»", t.text());
        p.dropField("City", t.box().x() + 1, t.box().y() + 1);
        Element.Text grown = (Element.Text) selected();
        assertEquals(t.id(), grown.id());
        assertEquals("«Name»\n«City»", grown.text());
        assertTrue(grown.box().h() > t.box().h());
        assertTrue(state().fields().containsAll(List.of("Name", "City")));
    }

    @Test
    void doubleClickedFieldsGoToTheTextBeingEdited() {
        view.editingText = true;
        p.insertField("Name");
        assertEquals(List.of("«Name»"), view.inserted);
        view.editingText = false;
        p.insertField("City");
        assertEquals("«City»", ((Element.Text) selected()).text());
    }

    @Test
    void openingAnElementEditsItsContent() {
        p.add(MainPresenter.Add.TEXT);
        p.openElement(state().selected());
        assertTrue(view.contentEdited);
    }

    @Test
    void aNewSheetKeepsElementsInside() {
        p.setSheet(cards());
        p.add(MainPresenter.Add.QR);
        p.apply(selected().withBox(new Box(70, 40, 15, 15)));
        SheetTemplate small = catalog.find(SheetKind.ADDRESS, false, SheetTemplate.Paper.A4, "")
                .getFirst();
        p.setSheet(small);
        Box b = selected().box();
        assertTrue(b.x() + b.w() <= small.width() + 1e-9 && b.y() + b.h() <= small.height() + 1e-9, b.toString());
        assertEquals(small.id(), prefs.lastSheet().orElseThrow());
    }

    @Test
    void businessCardsGetCropMarks() {
        view.sheet = Optional.of(cards());
        p.chooseSheet();
        assertEquals(CutGuides.CROP_MARKS, state().design().cutGuides());
        p.cutGuides(CutGuides.OUTLINES);
        assertEquals(CutGuides.OUTLINES, state().design().cutGuides());
    }

    @Test
    void twoSidedCardsShowEitherSide() {
        p.setSheet(cards());
        p.twoSided(true);
        p.showSide(true);
        assertTrue(state().back());
        p.add(MainPresenter.Add.TEXT);
        assertEquals(1, state().design().back().elements().size());
        assertTrue(state().design().front().elements().isEmpty());
        p.twoSided(false);
        assertFalse(state().back());
        assertFalse(state().design().twoSided());
    }

    @Test
    void backgroundAndCounter() {
        p.background(new Background(Color.ORANGE, null, true));
        assertEquals(Color.ORANGE, state().design().front().background().color());
        p.counter(100, 4);
        assertEquals(100, state().design().counterStart());
        assertEquals(4, state().design().counterDigits());
    }

    void typedList(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= n; i++) sb.append("Person ").append(i).append("\nStreet ").append(i).append("\n\n");
        DataSource source = DataSource.typed(sb.toString());
        view.data = Optional.of(new DataChoice(source, new DataLoader(new ContactDirectory(dir, clock), msg::get)
                .typed(sb.toString()), MergeSettings.ALL));
        p.chooseData();
    }

    @Test
    void dataGivesRecordsFieldsAndSheets() {
        typedList(30);
        assertEquals("Record 1 of 30", state().record());
        assertEquals(2, state().preview().sheets());
        assertTrue(state().fields().containsAll(List.of("Text", "Line 1", "Line 2")));
        assertEquals("Person 1", state().values().apply("Line 1"));
        assertEquals(0, state().highlight());
        assertTrue(state().status().contains("30 of 30"), state().status());
    }

    @Test
    void recordsAndPreviewSheetsAreBrowsed() {
        typedList(30);
        for (int i = 0; i < 24; i++) p.nextRecord();
        assertEquals("Record 25 of 30", state().record());
        assertEquals(1, state().sheet());
        assertEquals(0, state().highlight());
        p.previewSheet(-1);
        assertEquals(0, state().sheet());
        assertEquals(-1, state().highlight());
        p.previewClicked(4);
        assertEquals("Record 5 of 30", state().record());
        assertEquals("Person 5", state().values().apply("Line 1"));
        for (int i = 0; i < 99; i++) p.nextRecord();
        assertEquals("Record 30 of 30", state().record());
        p.previousRecord();
        assertEquals("Record 29 of 30", state().record());
    }

    @Test
    void cancelledDataChangesNothing() {
        p.chooseData();
        assertFalse(state().title().startsWith("*"));
    }

    @Test
    void savedFilesOpenAgainWithTheirData() throws IOException {
        typedList(3);
        p.add(MainPresenter.Add.FIELD);
        Path file = dir.resolve("club");
        view.files.add(Optional.of(file));
        assertTrue(p.save());
        Path saved = dir.resolve("club.label");
        assertTrue(Files.exists(saved));
        assertEquals("club.label – Label Printer", state().title());
        assertEquals(List.of(saved), p.recentFiles());

        p.newDesign();
        assertTrue(state().design().front().elements().isEmpty());
        p.openFile(saved);
        assertEquals(1, state().design().front().elements().size());
        assertEquals("Record 1 of 3", state().record());
        assertFalse(state().canUndo());
    }

    @Test
    void unsavedChangesAreAskedAbout() {
        p.add(MainPresenter.Add.TEXT);
        view.unsaved = MainView.Unsaved.CANCEL;
        p.newDesign();
        assertEquals(1, state().design().front().elements().size());
        view.unsaved = MainView.Unsaved.DISCARD;
        p.newDesign();
        assertTrue(state().design().front().elements().isEmpty());
        assertEquals(2, view.unsavedAsked);
    }

    @Test
    void exitAsksAndCloses() {
        p.add(MainPresenter.Add.TEXT);
        view.unsaved = MainView.Unsaved.CANCEL;
        p.exit();
        assertFalse(view.closed);
        view.unsaved = MainView.Unsaved.SAVE;
        view.files.add(Optional.empty());
        p.exit();
        assertFalse(view.closed, "saving was cancelled");
        view.unsaved = MainView.Unsaved.DISCARD;
        p.exit();
        assertTrue(view.closed);
        assertEquals(1, exits);
    }

    @Test
    void brokenFilesShowAnError() throws IOException {
        Path broken = dir.resolve("broken.label");
        Files.writeString(broken, "nope");
        p.openFile(broken);
        assertEquals(1, view.errors.size());
        assertTrue(view.errors.getFirst().startsWith("Could not open broken.label"), view.errors.getFirst());
    }

    @Test
    void picturesAreReadIntoTheDocument() throws IOException {
        Path png = dir.resolve("logo.png");
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", png.toFile());
        view.files.add(Optional.of(png));
        p.add(MainPresenter.Add.PICTURE);
        assertTrue(selected() instanceof Element.Picture pic && pic.image().equals("logo.png"));
        assertTrue(p.document().images().containsKey("logo.png"));
        assertEquals(dir, prefs.lastFolder().orElseThrow());
        view.files.add(Optional.of(png));
        p.choosePicture(null);
        assertEquals("logo.png", state().design().front().background().image());
    }

    @Test
    void filesThatAreNoPicturesAreRefused() throws IOException {
        Path txt = dir.resolve("note.png");
        Files.writeString(txt, "text");
        view.files.add(Optional.of(txt));
        p.add(MainPresenter.Add.PICTURE);
        assertTrue(state().design().front().elements().isEmpty());
        assertEquals(1, view.errors.size());
    }

    @Test
    void printPlansForEachMode() {
        typedList(5);
        p.nextRecord();
        PrintPlan all = p.plan(PrintChoice.Mode.ALL, 2);
        assertEquals(5, all.labels().size());
        assertEquals(2, all.start());
        PrintPlan current = p.plan(PrintChoice.Mode.CURRENT, 0);
        assertEquals(1, current.labels().size());
        assertEquals("Person 2", current.labels().getFirst().values().apply("Line 1"));
        PrintPlan fill = p.plan(PrintChoice.Mode.FILL_SHEET, 4);
        assertEquals(state().design().sheet().count() - 4, fill.labels().size());
        assertEquals(1, fill.sheets());
    }

    @Test
    void withoutDataTheLabelFillsTheSheet() {
        int count = state().design().sheet().count();
        assertEquals(count, state().preview().labels().size());
        assertEquals(1, state().preview().sheets());
        PrintPlan all = p.plan(PrintChoice.Mode.ALL, 3);
        assertEquals(count - 3, all.labels().size());
        assertEquals(1, all.sheets());
        assertEquals(4, all.labels().get(3).number());
    }

    @Test
    void printingAsksTheWindow() {
        p.print();
        assertTrue(view.printRequest != null);
        int count = state().design().sheet().count();
        assertEquals(count, view.printRequest.plans().apply(PrintChoice.Mode.ALL, 0).labels().size());
        assertEquals(1, view.printRequest.plans().apply(PrintChoice.Mode.CURRENT, 0).labels().size());
        assertArrayEquals(new double[]{0, 0}, view.printRequest.offsets().apply("Any"));
    }

    static void assertArrayEquals(double[] a, double[] b) {
        org.junit.jupiter.api.Assertions.assertArrayEquals(a, b, 1e-9);
    }

    @Test
    void pdfExportRunsGhostscript() {
        runner.prints(PdfExport.PROGRAM, "");
        view.print = Optional.of(new PrintChoice(null, PrintChoice.Mode.ALL, 0, 0, 0, false));
        view.files.add(Optional.of(dir.resolve("labels")));
        p.print();
        assertEquals(1, runner.commands.size());
        assertTrue(runner.lines().getFirst().endsWith(dir.resolve("labels.pdf").toString()));
        assertEquals("Saved labels.pdf.", state().status());
    }

    @Test
    void missingGhostscriptIsExplained() {
        view.files.add(Optional.of(dir.resolve("labels.pdf")));
        p.exportPdf();
        assertEquals(List.of(msg.get("error.ps2pdf")), view.errors);
    }

    @Test
    void testPagesNeedAPrinter() {
        p.printTestPage(new PrintChoice(null, PrintChoice.Mode.ALL, 0, 1, 1, false));
        assertTrue(view.errors.isEmpty());
    }

    @Test
    void handbookProblemsAreShown() {
        p.handbook();
        assertEquals(1, handbooks);
        assertTrue(view.errors.getFirst().contains("no browser"));
        p.about();
        assertTrue(view.aboutShown);
    }

    @Test
    void savingKeepsTheFileFormat() throws IOException {
        p.add(MainPresenter.Add.TEXT);
        view.files.add(Optional.of(dir.resolve("a.label")));
        p.saveAs();
        assertEquals(1, LabelFiles.read(dir.resolve("a.label")).design().front().elements().size());
        assertEquals(Map.of(), LabelFiles.read(dir.resolve("a.label")).images());
        assertEquals(Set.of(), LabelFiles.read(dir.resolve("a.label")).design().fields());
    }
}
