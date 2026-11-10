package eu.amadeos.apps.labelprinter.ui.main;

import eu.amadeos.apps.labelprinter.Direct;
import eu.amadeos.apps.labelprinter.FakeRunner;
import eu.amadeos.apps.labelprinter.OnEdt;
import eu.amadeos.apps.labelprinter.Ui;
import eu.amadeos.apps.labelprinter.bootstrap.LabelPrinterModule;
import eu.amadeos.apps.labelprinter.domain.CutGuides;
import eu.amadeos.apps.labelprinter.domain.Element;
import eu.amadeos.apps.labelprinter.domain.SheetKind;
import eu.amadeos.apps.labelprinter.domain.SheetTemplate;
import eu.amadeos.apps.labelprinter.repository.LabelPreferences;
import eu.amadeos.apps.labelprinter.service.DataLoader;
import eu.amadeos.apps.labelprinter.service.PdfExport;
import eu.amadeos.apps.labelprinter.service.SheetCatalog;
import eu.amadeos.commons.contacts.ContactDirectory;
import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** The real window under a display (Xvfb in CI); skipped without one. */
class MainWindowTest {
    @TempDir
    Path dir;
    private MainWindow window;
    private MainPresenter presenter;
    private SheetCatalog catalog;

    void open(Locale locale) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        Messages msg = Messages.load(LabelPrinterModule.class, LabelPrinterModule.MESSAGES, locale);
        catalog = new SheetCatalog(dir.resolve("own.json"));
        LabelPreferences prefs = new LabelPreferences(dir.resolve("prefs.properties"));
        prefs.setLastSheet("a4-70x37-3x8");
        MainPresenter.Services s = new MainPresenter.Services(catalog, new DataLoader(new ContactDirectory(
                dir.resolve("contacts"), Clock.systemUTC()), msg::get), prefs, new PdfExport(new FakeRunner()),
                () -> {
                }, new Direct(), Runnable::run, () -> {
        }, Clock.systemUTC(), dir, dir);
        OnEdt.run(() -> {
            window = new MainWindow(msg, null, s, "test");
            presenter = new MainPresenter(window, msg, null, s);
            window.bind(presenter);
            window.setVisible(true);
            presenter.start(Optional.empty());
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        if (window != null) OnEdt.run(window::dispose);
    }

    void click(String action) {
        window.action(action).actionPerformed(new ActionEvent(window, ActionEvent.ACTION_PERFORMED, action));
    }

    @Test
    void theToolbarHasIconButtonsWithTooltips() throws Exception {
        open(Locale.ENGLISH);
        OnEdt.run(() -> {
            JPanel bar = Ui.find(window.getContentPane(), JPanel.class, "toolbar");
            for (String key : List.of("menu.file.new", "menu.file.open", "menu.file.save", "menu.edit.undo",
                    "menu.insert.text", "menu.insert.field", "menu.insert.picture", "menu.insert.shape",
                    "menu.insert.barcode", "menu.insert.qr", "menu.label.sheet", "menu.label.data",
                    "menu.view.zoom-in", "menu.file.export-pdf", "menu.file.print")) {
                AbstractButton b = Ui.find(bar, AbstractButton.class, key);
                assertNotNull(b.getIcon(), key);
                assertTrue(b.getText() == null || b.getText().isEmpty(), key + " shows only its icon");
                assertFalse(b.getToolTipText().isBlank(), key);
            }
            assertEquals("Untitled – Label Printer", window.getTitle());
        });
    }

    @Test
    void everyTextIsTranslated() throws Exception {
        for (Language language : Language.values()) {
            open(Locale.forLanguageTag(language.code()));
            OnEdt.run(() -> {
                presenter.add(MainPresenter.Add.TEXT);
                for (String text : Ui.texts(window)) {
                    assertFalse(Ui.missing(text), language + ": " + text);
                }
            });
            OnEdt.run(window::dispose);
        }
    }

    @Test
    void addingATextShowsItsProperties() throws Exception {
        open(Locale.ENGLISH);
        OnEdt.run(() -> {
            assertFalse(window.action("menu.edit.delete").isEnabled());
            click("menu.insert.text");
            assertTrue(window.action("menu.edit.delete").isEnabled());
            JLabel title = Ui.find(window.properties(), JLabel.class, "prop.title");
            assertEquals("Text", title.getText());
            JTextArea text = Ui.find(window.properties(), JTextArea.class, "prop.text");
            assertEquals("Text", text.getText());
            text.setText("Hello «Name»");
            Element.Text t = (Element.Text) window.state().design().front().elements().getFirst();
            assertEquals("Hello «Name»", t.text());
            assertTrue(window.state().fields().contains("Name"));
            JToggleButton bold = Ui.find(window.properties(), JToggleButton.class, "prop.bold");
            bold.doClick();
            assertTrue(((Element.Text) window.state().design().front().elements().getFirst()).style().bold());
            assertEquals("Bold", bold.getToolTipText());
        });
    }

    @Test
    void undoAndRedoFollowTheChanges() throws Exception {
        open(Locale.ENGLISH);
        OnEdt.run(() -> {
            assertFalse(window.action("menu.edit.undo").isEnabled());
            click("menu.insert.shape");
            assertTrue(window.action("menu.edit.undo").isEnabled());
            click("menu.edit.undo");
            assertTrue(window.state().design().front().elements().isEmpty());
            assertTrue(window.action("menu.edit.redo").isEnabled());
        });
    }

    @Test
    void theCanvasMovesTheSelectionWithTheKeys() throws Exception {
        open(Locale.ENGLISH);
        OnEdt.run(() -> {
            click("menu.insert.shape");
            double x = window.state().design().front().elements().getFirst().box().x();
            Action right = window.canvas().getActionMap().get("right5");
            right.actionPerformed(new ActionEvent(window.canvas(), 0, "right5"));
            assertEquals(x + 5, window.state().design().front().elements().getFirst().box().x(), 1e-9);
            window.canvas().getActionMap().get("delete").actionPerformed(new ActionEvent(window.canvas(), 0, ""));
            assertTrue(window.state().design().front().elements().isEmpty());
        });
    }

    @Test
    void theSidesShowOnlyForTwoSidedCards() throws Exception {
        open(Locale.ENGLISH);
        OnEdt.run(() -> {
            JToggleButton back = Ui.find(window.getContentPane(), JToggleButton.class, "menu.label.back");
            assertFalse(back.isVisible());
            SheetTemplate cards = catalog.find(SheetKind.BUSINESS_CARD, false, SheetTemplate.Paper.A4, "")
                    .getFirst();
            presenter.setSheet(cards);
            presenter.twoSided(true);
            assertTrue(back.isVisible());
            back.doClick();
            assertTrue(window.state().back());
            assertTrue(back.isSelected());
            @SuppressWarnings("unchecked")
            JComboBox<CutGuides> guides = Ui.find(window.properties(), JComboBox.class, "prop.cut");
            assertEquals(CutGuides.CROP_MARKS, guides.getSelectedItem());
            guides.setSelectedItem(CutGuides.OUTLINES);
            assertEquals(CutGuides.OUTLINES, window.state().design().cutGuides());
        });
    }

    @Test
    void theFieldListOffersTheBuiltInFields() throws Exception {
        open(Locale.GERMAN);
        OnEdt.run(() -> {
            @SuppressWarnings("unchecked")
            JList<String> fields = Ui.find(window.getContentPane(), JList.class, "fields");
            assertEquals(3, fields.getModel().getSize());
            JLabel first = (JLabel) fields.getCellRenderer().getListCellRendererComponent(fields,
                    fields.getModel().getElementAt(0), 0, false, false);
            assertEquals("Datum (heute)", first.getText());
            fields.setSelectedIndex(0);
            fields.getActionMap().get("insert").actionPerformed(new ActionEvent(fields, 0, "insert"));
            assertEquals("«Date»", ((Element.Text) window.state().design().front().elements().getFirst()).text());
        });
    }

    @Test
    void recordsShowOnlyWithData() throws Exception {
        open(Locale.ENGLISH);
        OnEdt.run(() -> {
            JLabel record = Ui.find(window.getContentPane(), JLabel.class, "record");
            assertFalse(record.getParent().isVisible());
            JLabel sheet = Ui.find(window.getContentPane(), JLabel.class, "preview.sheet");
            assertEquals("Sheet 1 of 1", sheet.getText());
            assertFalse(Ui.find(window.getContentPane(), JButton.class, "preview.next").isEnabled());
        });
    }

    @Test
    void theMenusHaveTheCommands() throws Exception {
        open(Locale.ENGLISH);
        OnEdt.run(() -> {
            assertEquals(6, window.getJMenuBar().getMenuCount());
            JComponent save = Ui.find(window.getJMenuBar().getMenu(0), javax.swing.JMenuItem.class,
                    "menu.file.save");
            assertEquals("Save", ((javax.swing.JMenuItem) save).getText());
            assertNotNull(((javax.swing.JMenuItem) save).getAccelerator());
        });
    }
}
