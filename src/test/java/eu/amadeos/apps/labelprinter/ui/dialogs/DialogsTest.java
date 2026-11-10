package eu.amadeos.apps.labelprinter.ui.dialogs;

import eu.amadeos.apps.labelprinter.Direct;
import eu.amadeos.apps.labelprinter.OnEdt;
import eu.amadeos.apps.labelprinter.Ui;
import eu.amadeos.apps.labelprinter.bootstrap.LabelPrinterModule;
import eu.amadeos.apps.labelprinter.domain.DataSource;
import eu.amadeos.apps.labelprinter.domain.DataTable;
import eu.amadeos.apps.labelprinter.domain.LabelDesign;
import eu.amadeos.apps.labelprinter.domain.LabelShape;
import eu.amadeos.apps.labelprinter.domain.Merge;
import eu.amadeos.apps.labelprinter.domain.MergeSettings;
import eu.amadeos.apps.labelprinter.domain.PrintPlan;
import eu.amadeos.apps.labelprinter.domain.SheetKind;
import eu.amadeos.apps.labelprinter.domain.SheetTemplate;
import eu.amadeos.apps.labelprinter.service.DataLoader;
import eu.amadeos.apps.labelprinter.service.LabelPainter;
import eu.amadeos.apps.labelprinter.service.SheetCatalog;
import eu.amadeos.apps.labelprinter.service.SheetPainter;
import eu.amadeos.apps.labelprinter.ui.main.DataChoice;
import eu.amadeos.apps.labelprinter.ui.main.MainView;
import eu.amadeos.apps.labelprinter.ui.main.PrintChoice;
import eu.amadeos.commons.contacts.ContactDirectory;
import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** The dialogs, opened for real under a display and answered by timers; skipped without a display. */
class DialogsTest {
    @TempDir
    Path dir;
    final Messages msg = Messages.load(LabelPrinterModule.class, LabelPrinterModule.MESSAGES, Locale.ENGLISH);
    final SheetPainter painter = new SheetPainter(new LabelPainter(n -> null));
    final AtomicReference<Throwable> failure = new AtomicReference<>();
    SheetCatalog catalog;

    @BeforeEach
    void setUp() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        catalog = new SheetCatalog(dir.resolve("own.json"));
    }

    SheetTemplate standard() {
        return catalog.standard(false);
    }

    // ---- sheets -----------------------------------------------------------------------------------------------

    @Test
    void theSheetDialogFindsSheetsBySize() throws Exception {
        List<SheetTemplate> chosen = new ArrayList<>();
        OnEdt.run(() -> {
            SheetDialog d = new SheetDialog(null, msg, catalog, painter);
            Ui.whenDialog(dialog -> {
                assertEquals(standard(), d.shown().get(Ui.find(dialog, JTable.class, "sheet.table").getSelectedRow()));
                Ui.find(dialog, javax.swing.JList.class, "sheet.kinds").setSelectedIndex(0);
                Ui.find(dialog, JTextField.class, "sheet.search").setText("85x55");
                assertFalse(d.shown().isEmpty());
                assertTrue(d.shown().stream().allMatch(s -> s.matches("85x55")));
                JLabel details = Ui.find(dialog, JLabel.class, "sheet.details");
                assertTrue(details.getText().contains("85 × 55 mm"), details.getText());
                Ui.find(dialog, JButton.class, "button.ok").doClick();
            }, failure);
            d.choose(standard()).ifPresent(chosen::add);
        });
        Ui.rethrow(failure);
        assertEquals(1, chosen.size());
        assertEquals("85 × 55 mm", chosen.getFirst().sizeText());
    }

    @Test
    void cancellingKeepsTheSheet() throws Exception {
        List<Optional<SheetTemplate>> result = new ArrayList<>();
        OnEdt.run(() -> {
            SheetDialog d = new SheetDialog(null, msg, catalog, painter);
            Ui.whenDialog(dialog -> Ui.find(dialog, JButton.class, "button.cancel").doClick(), failure);
            result.add(d.choose(standard()));
        });
        Ui.rethrow(failure);
        assertEquals(Optional.empty(), result.getFirst());
    }

    @Test
    void theEditorMeasuresAnOwnSheet() throws Exception {
        List<SheetTemplate> made = new ArrayList<>();
        OnEdt.run(() -> {
            SheetEditor e = new SheetEditor(null, msg);
            Ui.whenDialog(dialog -> {
                JButton save = Ui.find(dialog, JButton.class, "button.save");
                assertTrue(save.isEnabled());
                Ui.find(dialog, JSpinner.class, "editor.columns").setValue(4);
                assertFalse(save.isEnabled(), "four labels of 70 mm are wider than A4");
                assertEquals(msg.get("editor.problem"), Ui.find(dialog, JLabel.class, "editor.problem").getText());
                Ui.find(dialog, JSpinner.class, "editor.width").setValue(50.0);
                Ui.find(dialog, JSpinner.class, "editor.gap-x").setValue(2.5);
                Ui.find(dialog, JSpinner.class, "editor.left").setValue(1.0);
                assertTrue(save.isEnabled());
                save.doClick();
            }, failure);
            e.edit(standard()).ifPresent(made::add);
        });
        Ui.rethrow(failure);
        SheetTemplate s = made.getFirst();
        assertEquals(4, s.columns());
        assertEquals(52.5, s.pitchX(), 1e-9);
        assertEquals(2.5, s.gapX(), 1e-9);
        assertEquals(LabelShape.RECTANGLE, s.shape());
    }

    @Test
    void ownSheetsAreSavedFromTheDialog() throws Exception {
        OnEdt.run(() -> {
            SheetDialog d = new SheetDialog(null, msg, catalog, painter);
            Ui.whenDialog(dialog -> {
                Ui.whenDialog(SheetEditor.class, editor -> {
                    Ui.find(editor, JSpinner.class, "editor.rows").setValue(7);
                    Ui.find(editor, JButton.class, "button.save").doClick();
                    Ui.later(200, () -> {
                        assertEquals(1, catalog.own().size());
                        assertTrue(Ui.find(dialog, JButton.class, "sheet.delete").isEnabled());
                        dialog.dispose();
                    }, dialog, failure);
                }, failure);
                Ui.find(dialog, JButton.class, "sheet.measure").doClick();
            }, failure);
            d.choose(standard());
        });
        Ui.rethrow(failure);
        assertEquals(7, catalog.own().getFirst().rows());
        assertTrue(new SheetCatalog(dir.resolve("own.json")).own().getFirst().isOwn());
    }

    // ---- data -------------------------------------------------------------------------------------------------

    DataLoader loader() {
        return new DataLoader(new ContactDirectory(dir.resolve("contacts"), Clock.systemUTC()), msg::get);
    }

    static DataTable members() {
        return new DataTable("members.csv", List.of("Name", "City"), List.of(List.of("Anna", "Ulm"),
                List.of("Bernd", "Bonn"), List.of("Clara", "Ulm")));
    }

    @Test
    void theDataDialogFiltersAndTicksRecords() throws Exception {
        List<DataChoice> result = new ArrayList<>();
        OnEdt.run(() -> {
            DataDialog d = new DataDialog(null, msg, loader(), new Direct(), dir);
            Ui.whenDialog(dialog -> {
                assertEquals(3, Ui.find(dialog, JTable.class, "data.table").getRowCount());
                JLabel count = Ui.find(dialog, JLabel.class, "data.count");
                assertEquals("3 of 3 records · 3 labels", count.getText());
                Ui.find(dialog, JTextField.class, "data.filter").setText("City = Ulm");
                assertEquals("2 of 3 records · 2 labels", count.getText());
                JTable table = Ui.find(dialog, JTable.class, "data.table");
                table.setValueAt(false, 2, 0);
                assertEquals("1 of 3 records · 1 labels", count.getText());
                Ui.find(dialog, JSpinner.class, "data.copies").setValue(3);
                @SuppressWarnings("unchecked")
                JComboBox<String> map = Ui.find(dialog, JComboBox.class, "map:Ort");
                assertEquals("City", map.getSelectedItem());
                map.setSelectedItem("Name");
                Ui.find(dialog, JButton.class, "button.ok").doClick();
            }, failure);
            d.choose(DataSource.file("/x/members.csv", true), MergeSettings.ALL, members(), Set.of("Ort", "Date"))
                    .ifPresent(result::add);
        });
        Ui.rethrow(failure);
        DataChoice c = result.getFirst();
        assertEquals(Set.of(2), c.merge().excluded());
        assertEquals("City = Ulm", c.merge().filter());
        assertEquals(3, c.merge().copies());
        assertEquals(Map.of("Ort", "Name"), c.merge().mapping());
        assertEquals(members(), c.table());
    }

    @Test
    void aTypedListIsReadAsYouType() throws Exception {
        List<DataChoice> result = new ArrayList<>();
        OnEdt.run(() -> {
            DataDialog d = new DataDialog(null, msg, loader(), new Direct(), dir);
            Ui.whenDialog(dialog -> {
                Ui.find(dialog, JTextArea.class, "data.text").setText("Anna\nUlm\n\nBernd\nBonn");
                Ui.later(200, () -> {
                    assertEquals(2, d.data().size());
                    assertEquals(DataSource.Kind.TYPED, d.source().kind());
                    Ui.find(dialog, JButton.class, "button.ok").doClick();
                }, dialog, failure);
            }, failure);
            d.choose(DataSource.NONE, MergeSettings.ALL, DataTable.NONE, Set.of()).ifPresent(result::add);
        });
        Ui.rethrow(failure);
        assertEquals(2, result.getFirst().table().size());
        assertEquals("Line 2", result.getFirst().table().columns().get(2));
    }

    @Test
    void choosingNoDataDropsTheList() throws Exception {
        List<DataChoice> result = new ArrayList<>();
        OnEdt.run(() -> {
            DataDialog d = new DataDialog(null, msg, loader(), new Direct(), dir);
            Ui.whenDialog(dialog -> {
                Ui.find(dialog, javax.swing.JRadioButton.class, "data.none").doClick();
                Ui.find(dialog, JButton.class, "button.ok").doClick();
            }, failure);
            d.choose(DataSource.file("/x/members.csv", true), MergeSettings.ALL.withCopies(2), members(), Set.of())
                    .ifPresent(result::add);
        });
        Ui.rethrow(failure);
        assertEquals(DataSource.NONE, result.getFirst().source());
        assertEquals(MergeSettings.ALL, result.getFirst().merge());
        assertTrue(result.getFirst().table().isEmpty());
    }

    // ---- print ------------------------------------------------------------------------------------------------

    MainView.PrintRequest request(List<PrintChoice> tests) {
        SheetTemplate sheet = standard();
        LabelDesign design = LabelDesign.empty(sheet);
        DataTable data = new DataTable("d", List.of("No"), java.util.stream.IntStream.range(0, 30)
                .mapToObj(i -> List.of(Integer.toString(i))).toList());
        List<Merge.Label> labels = Merge.labels(data, MergeSettings.ALL, design, LocalDate.now(), Locale.UK);
        return new MainView.PrintRequest(design, (mode, start) -> switch (mode) {
            case ALL -> new PrintPlan(sheet, labels, start);
            case CURRENT -> new PrintPlan(sheet, labels.subList(0, 1), start);
            case FILL_SHEET -> new PrintPlan(sheet, java.util.Collections.nCopies(sheet.count() - start,
                    labels.getFirst()), start);
        }, List.of("Office laser", "Photo printer"), Optional.of("Photo printer"),
                printer -> printer.equals("Photo printer") ? new double[]{0.5, -1} : new double[]{0, 0}, tests::add);
    }

    @Test
    void thePrintDialogStartsAtTheFirstFreeLabel() throws Exception {
        List<PrintChoice> tests = new ArrayList<>();
        List<PrintChoice> result = new ArrayList<>();
        OnEdt.run(() -> {
            PrintDialog d = new PrintDialog(null, msg, painter);
            Ui.whenDialog(dialog -> {
                @SuppressWarnings("unchecked")
                JComboBox<String> printer = Ui.find(dialog, JComboBox.class, "print.printer");
                assertEquals("Photo printer", printer.getSelectedItem());
                assertEquals(0.5, ((Number) Ui.find(dialog, JSpinner.class, "print.dx").getValue()).doubleValue());
                JLabel res = Ui.find(dialog, JLabel.class, "print.result");
                assertEquals("2 sheets · 18 labels left over on the last sheet", res.getText());
                Ui.find(dialog, JSpinner.class, "print.row").setValue(3);
                Ui.find(dialog, JSpinner.class, "print.column").setValue(2);
                assertEquals(7, d.start());
                assertEquals("2 sheets · 11 labels left over on the last sheet", res.getText());
                Ui.find(dialog, JButton.class, "print.test-page").doClick();
                Ui.find(dialog, javax.swing.JRadioButton.class, "print.fill").doClick();
                assertEquals(1, d.plan().sheets());
                assertEquals(0, d.plan().leftOver());
                assertEquals("Print 1 Sheet", Ui.find(dialog, JButton.class, "print.ok").getText());
                Ui.find(dialog, JButton.class, "print.ok").doClick();
            }, failure);
            d.choose(request(tests)).ifPresent(result::add);
        });
        Ui.rethrow(failure);
        assertEquals(1, tests.size());
        assertEquals("Photo printer", tests.getFirst().printer());
        PrintChoice c = result.getFirst();
        assertEquals(PrintChoice.Mode.FILL_SHEET, c.mode());
        assertEquals(7, c.start());
        assertEquals(-1, c.dy(), 1e-9);
        assertFalse(c.pdf());
    }

    @Test
    void pdfNeedsNoAdjustment() throws Exception {
        List<PrintChoice> result = new ArrayList<>();
        OnEdt.run(() -> {
            PrintDialog d = new PrintDialog(null, msg, painter);
            Ui.whenDialog(dialog -> {
                @SuppressWarnings("unchecked")
                JComboBox<String> printer = Ui.find(dialog, JComboBox.class, "print.printer");
                printer.setSelectedIndex(printer.getItemCount() - 1);
                assertFalse(Ui.find(dialog, JSpinner.class, "print.dx").isEnabled());
                assertFalse(Ui.find(dialog, JButton.class, "print.test-page").isEnabled());
                assertEquals("Save PDF", Ui.find(dialog, JButton.class, "print.ok").getText());
                Ui.find(dialog, JButton.class, "print.ok").doClick();
            }, failure);
            d.choose(request(new ArrayList<>())).ifPresent(result::add);
        });
        Ui.rethrow(failure);
        assertTrue(result.getFirst().pdf());
        assertEquals(0, result.getFirst().dx(), 1e-9);
    }

    @Test
    void everyDialogTextIsTranslated() throws Exception {
        for (Language language : Language.values()) {
            Messages m = Messages.load(LabelPrinterModule.class, LabelPrinterModule.MESSAGES,
                    Locale.forLanguageTag(language.code()));
            OnEdt.run(() -> {
                JFrame owner = new JFrame();
                List<JDialog> dialogs = List.of(new SheetDialog(owner, m, catalog, painter), new SheetEditor(owner, m),
                        new DataDialog(owner, m, loader(), new Direct(), dir), new PrintDialog(owner, m, painter));
                for (JDialog d : dialogs) {
                    for (String text : Ui.texts(d)) assertFalse(Ui.missing(text), language + ": " + text);
                    assertFalse(Ui.missing(d.getTitle()), language + ": " + d.getTitle());
                    d.dispose();
                }
                owner.dispose();
            });
        }
    }

    @Test
    void sheetKindsHaveNames() {
        for (SheetKind k : SheetKind.values()) assertFalse(msg.get(k.key()).startsWith("!"), k.key());
    }
}
