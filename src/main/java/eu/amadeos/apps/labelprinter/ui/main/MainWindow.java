package eu.amadeos.apps.labelprinter.ui.main;

import eu.amadeos.apps.labelprinter.domain.DataSource;
import eu.amadeos.apps.labelprinter.domain.DataTable;
import eu.amadeos.apps.labelprinter.domain.Element;
import eu.amadeos.apps.labelprinter.domain.LabelDesign;
import eu.amadeos.apps.labelprinter.domain.MergeSettings;
import eu.amadeos.apps.labelprinter.domain.Placeholders;
import eu.amadeos.apps.labelprinter.domain.SheetTemplate;
import eu.amadeos.apps.labelprinter.service.LabelFiles;
import eu.amadeos.apps.labelprinter.ui.dialogs.DataDialog;
import eu.amadeos.apps.labelprinter.ui.dialogs.PrintDialog;
import eu.amadeos.apps.labelprinter.ui.dialogs.SheetDialog;
import eu.amadeos.apps.labelprinter.ui.parts.DesignCanvas;
import eu.amadeos.apps.labelprinter.ui.parts.LabelGlyph;
import eu.amadeos.apps.labelprinter.ui.parts.PropertiesPanel;
import eu.amadeos.apps.labelprinter.ui.parts.SheetView;
import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.swing.EdtExecutor;
import eu.amadeos.commons.swing.StandardGlyph;
import eu.amadeos.commons.swing.ToolButtons;
import eu.amadeos.commons.swing.ToolIcon;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.TransferHandler;
import javax.swing.border.EmptyBorder;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The main window: the toolbar, the fields on the left, the label being designed with its settings in the middle,
 * and on the right the sheet as it will be printed. The menus have every command of the toolbar.
 */
public final class MainWindow extends JFrame implements MainView {
    private static final int CTRL = InputEvent.CTRL_DOWN_MASK;
    private static final int SHIFT = InputEvent.SHIFT_DOWN_MASK;
    private static final double ZOOM_STEP = 1.25;

    private final Messages msg;
    private final LocaleSettings locales;
    private final MainPresenter.Services services;
    private final String version;
    private final Map<String, Action> actions = new HashMap<>();
    private final DefaultListModel<String> fieldModel = new DefaultListModel<>();
    private final JList<String> fieldList = new JList<>(fieldModel);
    private final JLabel recordLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JPanel recordBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
    private final JLabel sheetLabel = new JLabel(" ");
    private final JButton previousSheet = new JButton("◀");
    private final JButton nextSheet = new JButton("▶");
    private final JLabel status = new JLabel(" ");
    private final JMenu recent = new JMenu();
    private JToggleButton frontButton;
    private JToggleButton backButton;
    private MainPresenter presenter;
    private DesignCanvas canvas;
    private PropertiesPanel properties;
    private SheetView preview;
    private State state;

    public MainWindow(Messages msg, LocaleSettings locales, MainPresenter.Services services, String version) {
        super(msg.get("app.title"));
        EdtExecutor.requireEdt();
        this.msg = msg;
        this.locales = locales;
        this.services = services;
        this.version = version;
        setName("mainWindow");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        java.net.URL icon = getClass().getResource("/icons/amade-labelprinter.png");
        if (icon != null) setIconImage(new ImageIcon(icon).getImage());
    }

    /** Builds the window for its presenter. */
    public void bind(MainPresenter newPresenter) {
        EdtExecutor.requireEdt();
        presenter = newPresenter;
        canvas = new DesignCanvas(presenter.labelPainter());
        canvas.setListener(new DesignCanvas.Listener() {
            @Override
            public void selected(String id) {
                presenter.select(id);
            }

            @Override
            public void changed(Element element) {
                presenter.apply(element);
            }

            @Override
            public void delete(String id) {
                presenter.delete(id);
            }

            @Override
            public void dropField(String field, double xMm, double yMm) {
                presenter.dropField(field, xMm, yMm);
            }

            @Override
            public void open(String id) {
                presenter.openElement(id);
            }
        });
        properties = new PropertiesPanel(msg, services.prefs()::recentColors, services.prefs()::addRecentColor);
        properties.framed();
        properties.setListener(new PropertiesPanel.Listener() {
            @Override
            public void apply(Element element) {
                presenter.apply(element);
            }

            @Override
            public void background(eu.amadeos.apps.labelprinter.domain.Background background) {
                presenter.background(background);
            }

            @Override
            public void choosePicture(String id) {
                presenter.choosePicture(id);
            }

            @Override
            public void twoSided(boolean two) {
                presenter.twoSided(two);
            }

            @Override
            public void cutGuides(eu.amadeos.apps.labelprinter.domain.CutGuides guides) {
                presenter.cutGuides(guides);
            }

            @Override
            public void counter(int start, int digits) {
                presenter.counter(start, digits);
            }
        });
        preview = new SheetView(presenter.sheetPainter(), new Dimension(300, 420));
        preview.setName("preview");
        preview.onClick(presenter::previewClicked);

        createActions();
        setJMenuBar(menus());

        JPanel root = new JPanel(new BorderLayout());
        root.add(toolBar(), BorderLayout.NORTH);
        JPanel body = new JPanel(new BorderLayout(10, 0));
        body.setBorder(new EmptyBorder(6, 10, 4, 10));
        body.add(fieldsPanel(), BorderLayout.WEST);
        body.add(designPanel(), BorderLayout.CENTER);
        body.add(previewPanel(), BorderLayout.EAST);
        root.add(body, BorderLayout.CENTER);
        status.setName("status");
        status.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, AmadeUI.theme().textInactive()),
                new EmptyBorder(3, 10, 3, 10)));
        root.add(status, BorderLayout.SOUTH);
        setContentPane(root);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                presenter.exit();
            }
        });
        setSize(new Dimension(1280, 800));
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
    }

    // ---- actions, menus, toolbar ------------------------------------------------------------------------------

    private void createActions() {
        action("menu.file.new", StandardGlyph.NEW, key(KeyEvent.VK_N, CTRL), presenter::newDesign);
        action("menu.file.open", StandardGlyph.OPEN, key(KeyEvent.VK_O, CTRL), presenter::open);
        action("menu.file.save", StandardGlyph.SAVE, key(KeyEvent.VK_S, CTRL), presenter::save);
        action("menu.file.save-as", null, key(KeyEvent.VK_S, CTRL | SHIFT), presenter::saveAs);
        action("menu.file.export-pdf", StandardGlyph.EXPORT, key(KeyEvent.VK_E, CTRL), presenter::exportPdf);
        action("menu.file.print", StandardGlyph.PRINT, key(KeyEvent.VK_P, CTRL), presenter::print);
        action("menu.file.exit", null, key(KeyEvent.VK_Q, CTRL), presenter::exit);
        action("menu.edit.undo", StandardGlyph.UNDO, key(KeyEvent.VK_Z, CTRL), presenter::undo);
        action("menu.edit.redo", StandardGlyph.REDO, key(KeyEvent.VK_Y, CTRL), presenter::redo);
        action("menu.edit.duplicate", null, key(KeyEvent.VK_D, CTRL), presenter::duplicate);
        action("menu.edit.delete", StandardGlyph.DELETE, null, presenter::deleteSelected);
        action("menu.edit.to-front", LabelGlyph.FRONT, key(KeyEvent.VK_UP, CTRL | SHIFT), presenter::toFront);
        action("menu.edit.to-back", LabelGlyph.BACK, key(KeyEvent.VK_DOWN, CTRL | SHIFT), presenter::toBack);
        action("menu.insert.text", LabelGlyph.TEXT, key(KeyEvent.VK_T, CTRL | SHIFT),
                () -> presenter.add(MainPresenter.Add.TEXT));
        action("menu.insert.field", LabelGlyph.FIELD, key(KeyEvent.VK_F, CTRL | SHIFT),
                () -> presenter.add(MainPresenter.Add.FIELD));
        action("menu.insert.picture", LabelGlyph.PICTURE, key(KeyEvent.VK_I, CTRL | SHIFT),
                () -> presenter.add(MainPresenter.Add.PICTURE));
        action("menu.insert.shape", LabelGlyph.SHAPE, null, () -> presenter.add(MainPresenter.Add.SHAPE));
        action("menu.insert.barcode", LabelGlyph.BARCODE, null, () -> presenter.add(MainPresenter.Add.BARCODE));
        action("menu.insert.qr", LabelGlyph.QR, null, () -> presenter.add(MainPresenter.Add.QR));
        action("menu.label.sheet", LabelGlyph.SHEET, key(KeyEvent.VK_L, CTRL), presenter::chooseSheet);
        action("menu.label.data", LabelGlyph.DATA, key(KeyEvent.VK_M, CTRL), presenter::chooseData);
        action("menu.label.reload", StandardGlyph.REFRESH, key(KeyEvent.VK_F5, 0), presenter::reloadData);
        action("menu.label.front", LabelGlyph.CARD_FRONT, null, () -> presenter.showSide(false));
        action("menu.label.back", LabelGlyph.CARD_BACK, null, () -> presenter.showSide(true));
        action("menu.label.previous", null, key(KeyEvent.VK_PAGE_UP, CTRL), presenter::previousRecord);
        action("menu.label.next", null, key(KeyEvent.VK_PAGE_DOWN, CTRL), presenter::nextRecord);
        action("menu.view.zoom-in", StandardGlyph.ZOOM_IN, key(KeyEvent.VK_PLUS, CTRL), () -> zoom(1));
        action("menu.view.zoom-out", StandardGlyph.ZOOM_OUT, key(KeyEvent.VK_MINUS, CTRL), () -> zoom(-1));
        action("menu.view.zoom-fit", StandardGlyph.ZOOM_FIT, key(KeyEvent.VK_0, CTRL), () -> zoom(0));
        action("menu.help.handbook", null, key(KeyEvent.VK_F1, 0), presenter::handbook);
        action("menu.help.about", null, null, presenter::about);
    }

    private void action(String key, ToolIcon.Glyph glyph, KeyStroke accelerator, Runnable run) {
        Action a = new AbstractAction(Mnemonics.strip(msg.get(key))) {
            @Override
            public void actionPerformed(ActionEvent e) {
                run.run();
            }
        };
        a.putValue(Action.ACTION_COMMAND_KEY, key);
        a.putValue(Action.SHORT_DESCRIPTION, Mnemonics.strip(msg.get(key)));
        if (glyph != null) a.putValue(Action.LARGE_ICON_KEY, new ToolIcon(glyph));
        if (accelerator != null) a.putValue(Action.ACCELERATOR_KEY, accelerator);
        actions.put(key, a);
    }

    /** The action of a menu command, for tests. */
    public Action action(String key) {
        return actions.get(key);
    }

    private static KeyStroke key(int code, int modifiers) {
        return KeyStroke.getKeyStroke(code, modifiers);
    }

    private JMenuBar menus() {
        JMenuBar bar = new JMenuBar();
        JMenu file = menu(bar, "menu.file");
        items(file, "menu.file.new", "menu.file.open");
        Mnemonics.apply(recent, msg.get("menu.file.recent"));
        recent.setName("menu.file.recent");
        file.add(recent);
        file.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
                fillRecent();
            }

            @Override
            public void menuDeselected(MenuEvent e) {
            }

            @Override
            public void menuCanceled(MenuEvent e) {
            }
        });
        file.addSeparator();
        items(file, "menu.file.save", "menu.file.save-as");
        file.addSeparator();
        items(file, "menu.file.print", "menu.file.export-pdf");
        file.addSeparator();
        items(file, "menu.file.exit");

        JMenu edit = menu(bar, "menu.edit");
        items(edit, "menu.edit.undo", "menu.edit.redo");
        edit.addSeparator();
        items(edit, "menu.edit.duplicate", "menu.edit.delete");
        edit.addSeparator();
        items(edit, "menu.edit.to-front", "menu.edit.to-back");

        JMenu insert = menu(bar, "menu.insert");
        items(insert, "menu.insert.text", "menu.insert.field", "menu.insert.picture", "menu.insert.shape",
                "menu.insert.barcode", "menu.insert.qr");

        JMenu label = menu(bar, "menu.label");
        items(label, "menu.label.sheet");
        label.addSeparator();
        items(label, "menu.label.data", "menu.label.reload");
        label.addSeparator();
        items(label, "menu.label.previous", "menu.label.next");
        label.addSeparator();
        items(label, "menu.label.front", "menu.label.back");

        JMenu view = menu(bar, "menu.view");
        items(view, "menu.view.zoom-in", "menu.view.zoom-out", "menu.view.zoom-fit");

        if (locales != null) {
            JMenu options = menu(bar, "menu.options");
            options.add(languageMenu());
        }

        JMenu help = menu(bar, "menu.help");
        items(help, "menu.help.handbook");
        help.addSeparator();
        items(help, "menu.help.about");
        return bar;
    }

    private JMenu menu(JMenuBar bar, String key) {
        JMenu menu = Mnemonics.apply(new JMenu(), msg.get(key));
        menu.setName(key);
        bar.add(menu);
        return menu;
    }

    private void items(JMenu menu, String... keys) {
        for (String key : keys) {
            JMenuItem item = new JMenuItem(actions.get(key));
            item.setIcon(null);
            Mnemonics.apply(item, msg.get(key));
            item.setName(key);
            menu.add(item);
        }
    }

    private void fillRecent() {
        recent.removeAll();
        List<Path> files = presenter.recentFiles();
        if (files.isEmpty()) {
            JMenuItem none = new JMenuItem(msg.get("menu.file.recent.none"));
            none.setEnabled(false);
            recent.add(none);
            return;
        }
        int n = 1;
        for (Path p : files) {
            JMenuItem item = new JMenuItem(n + "  " + p.getFileName());
            if (n < 10) item.setMnemonic(KeyEvent.VK_0 + n);
            item.setToolTipText(p.toString());
            item.addActionListener(e -> {
                if (presenter.confirmed()) presenter.openFile(p);
            });
            recent.add(item);
            n++;
        }
    }

    /** "Desktop setting (Deutsch)" and the five languages; the choice applies at the next start. */
    private JMenu languageMenu() {
        JMenu menu = Mnemonics.apply(new JMenu(), msg.get("menu.options.language"));
        menu.setName("menu.options.language");
        Messages commons = Messages.commons(msg.locale());
        List<Optional<Language>> choices = new ArrayList<>();
        choices.add(Optional.empty());
        for (Language language : Language.values()) choices.add(Optional.of(language));
        ButtonGroup group = new ButtonGroup();
        Optional<Language> current = locales.applicationLanguage();
        for (Optional<Language> choice : choices) {
            String text = choice.map(Language::nativeName).orElseGet(() -> commons.format("language.follow-desktop",
                    locales.desktopResolution().language().nativeName()));
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(text, choice.equals(current));
            item.setName(choice.map(Language::code).orElse(LocaleSettings.FOLLOW_DESKTOP));
            item.addActionListener(e -> presenter.setLanguage(choice));
            group.add(item);
            menu.add(item);
            if (choice.isEmpty()) menu.addSeparator();
        }
        return menu;
    }

    private JComponent toolBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setName("toolbar");
        bar.setBorder(new EmptyBorder(6, 10, 2, 10));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        buttons(left, "menu.file.new", "menu.file.open", "menu.file.save");
        left.add(separator());
        buttons(left, "menu.edit.undo", "menu.edit.redo");
        left.add(separator());
        buttons(left, "menu.insert.text", "menu.insert.field", "menu.insert.picture", "menu.insert.shape",
                "menu.insert.barcode", "menu.insert.qr");
        left.add(separator());
        buttons(left, "menu.edit.to-front", "menu.edit.to-back", "menu.edit.delete");
        left.add(separator());
        buttons(left, "menu.label.sheet", "menu.label.data");
        frontButton = ToolButtons.toggle(actions.get("menu.label.front"));
        backButton = ToolButtons.toggle(actions.get("menu.label.back"));
        left.add(frontButton);
        left.add(backButton);
        left.add(separator());
        buttons(left, "menu.view.zoom-out", "menu.view.zoom-in", "menu.view.zoom-fit");
        bar.add(left, BorderLayout.WEST);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
        buttons(right, "menu.file.export-pdf", "menu.file.print");
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private void buttons(JPanel panel, String... keys) {
        for (String key : keys) panel.add(ToolButtons.button(actions.get(key)));
    }

    private static JComponent separator() {
        JSeparator s = new JSeparator(SwingConstants.VERTICAL);
        s.setPreferredSize(new Dimension(8, ToolIcon.SIZE + 6));
        return s;
    }

    // ---- panels -----------------------------------------------------------------------------------------------

    private JComponent fieldsPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setPreferredSize(new Dimension(190, 300));
        JLabel title = new JLabel(msg.get("fields.title"));
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        p.add(title, BorderLayout.NORTH);
        fieldList.setName("fields");
        fieldList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fieldList.setDragEnabled(true);
        fieldList.setTransferHandler(new TransferHandler() {
            @Override
            public int getSourceActions(JComponent c) {
                return COPY;
            }

            @Override
            protected Transferable createTransferable(JComponent c) {
                String value = fieldList.getSelectedValue();
                return value == null ? null : new StringSelection(value);
            }
        });
        fieldList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                          boolean focus) {
                String name = String.valueOf(value);
                boolean builtIn = Placeholders.BUILT_IN.contains(name);
                String text = builtIn ? msg.get("field." + name.toLowerCase(Locale.ROOT)) : name;
                JLabel l = (JLabel) super.getListCellRendererComponent(list, text, index, selected, focus);
                l.setBorder(new EmptyBorder(3, 6, 3, 6));
                if (builtIn && !selected) l.setForeground(AmadeUI.theme().textInactive());
                if (builtIn) l.setToolTipText(Placeholders.field(name));
                return l;
            }
        });
        fieldList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && fieldList.getSelectedValue() != null) {
                    presenter.insertField(fieldList.getSelectedValue());
                }
            }
        });
        fieldList.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "insert");
        fieldList.getActionMap().put("insert", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (fieldList.getSelectedValue() != null) presenter.insertField(fieldList.getSelectedValue());
            }
        });
        p.add(new JScrollPane(fieldList), BorderLayout.CENTER);
        p.add(hint(msg.get("fields.hint"), 150), BorderLayout.SOUTH);
        return p;
    }

    private JComponent designPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        JButton previous = new JButton("◀");
        previous.setName("record.previous");
        previous.setToolTipText(Mnemonics.strip(msg.get("menu.label.previous")));
        previous.addActionListener(e -> presenter.previousRecord());
        JButton next = new JButton("▶");
        next.setName("record.next");
        next.setToolTipText(Mnemonics.strip(msg.get("menu.label.next")));
        next.addActionListener(e -> presenter.nextRecord());
        recordLabel.setName("record");
        recordLabel.setPreferredSize(new Dimension(140, recordLabel.getPreferredSize().height));
        recordBar.add(previous);
        recordBar.add(recordLabel);
        recordBar.add(next);
        p.add(recordBar, BorderLayout.NORTH);
        p.add(canvas, BorderLayout.CENTER);
        p.add(properties, BorderLayout.SOUTH);
        return p;
    }

    private JComponent previewPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        JPanel head = new JPanel(new BorderLayout());
        sheetLabel.setFont(sheetLabel.getFont().deriveFont(Font.BOLD));
        sheetLabel.setName("preview.sheet");
        head.add(sheetLabel, BorderLayout.WEST);
        JPanel nav = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        previousSheet.setName("preview.previous");
        nextSheet.setName("preview.next");
        previousSheet.addActionListener(e -> presenter.previewSheet(-1));
        nextSheet.addActionListener(e -> presenter.previewSheet(1));
        nav.add(previousSheet);
        nav.add(nextSheet);
        head.add(nav, BorderLayout.EAST);
        p.add(head, BorderLayout.NORTH);
        p.add(preview, BorderLayout.CENTER);
        p.add(hint(msg.get("preview.hint"), 280), BorderLayout.SOUTH);
        return p;
    }

    static JLabel hint(String text, int width) {
        JLabel l = new JLabel("<html><div style='width:" + width + "px'>" + escape(text) + "</div></html>");
        l.setFont(l.getFont().deriveFont(l.getFont().getSize2D() - 1f));
        l.setForeground(AmadeUI.theme().textInactive());
        return l;
    }

    static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void zoom(int direction) {
        if (direction == 0) canvas.setZoom(1, true);
        else canvas.setZoom(direction > 0 ? canvas.zoom() * ZOOM_STEP : canvas.zoom() / ZOOM_STEP, false);
    }

    // ---- MainView ---------------------------------------------------------------------------------------------

    @Override
    public void show(State s) {
        EdtExecutor.requireEdt();
        state = s;
        setTitle(s.title());
        LabelDesign d = s.design();
        canvas.show(d, s.back(), s.values(), s.selected());
        Optional<Element> element = s.selected() == null ? Optional.empty() : d.side(s.back()).find(s.selected());
        if (element.isPresent()) properties.show(element.get());
        else properties.showLabel(d, s.back());
        if (!Collections.list(fieldModel.elements()).equals(s.fields())) {
            String chosen = fieldList.getSelectedValue();
            fieldModel.clear();
            fieldModel.addAll(s.fields());
            if (chosen != null) fieldList.setSelectedValue(chosen, false);
        }
        preview.show(d, s.preview(), s.sheet(), s.back(), s.highlight());
        sheetLabel.setText(msg.format("preview.sheet", s.sheet() + 1, s.preview().sheets()));
        previousSheet.setEnabled(s.sheet() > 0);
        nextSheet.setEnabled(s.sheet() < s.preview().sheets() - 1);
        recordLabel.setText(s.record());
        recordBar.setVisible(!s.record().isEmpty());
        actions.get("menu.label.previous").setEnabled(!s.record().isEmpty());
        actions.get("menu.label.next").setEnabled(!s.record().isEmpty());
        actions.get("menu.label.reload").setEnabled(d != null);
        boolean two = d.twoSided();
        frontButton.setVisible(two);
        backButton.setVisible(two);
        frontButton.setSelected(two && !s.back());
        backButton.setSelected(two && s.back());
        actions.get("menu.label.front").setEnabled(two);
        actions.get("menu.label.back").setEnabled(two);
        boolean selected = element.isPresent();
        for (String k : List.of("menu.edit.duplicate", "menu.edit.delete", "menu.edit.to-front",
                "menu.edit.to-back")) actions.get(k).setEnabled(selected);
        actions.get("menu.edit.undo").setEnabled(s.canUndo());
        actions.get("menu.edit.redo").setEnabled(s.canRedo());
        status.setText(s.status());
    }

    /** What was shown last, for tests. */
    public State state() {
        return state;
    }

    public DesignCanvas canvas() {
        return canvas;
    }

    public PropertiesPanel properties() {
        return properties;
    }

    @Override
    public void showError(String message) {
        JOptionPane.showMessageDialog(this, message, msg.get("dialog.error.title"), JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public void showStatus(String message) {
        status.setText(message);
    }

    @Override
    public Unsaved askUnsaved(String name) {
        Object[] options = {Mnemonics.strip(msg.get("button.save")), Mnemonics.strip(msg.get("button.discard")),
                Mnemonics.strip(msg.get("button.cancel"))};
        int choice = JOptionPane.showOptionDialog(this, msg.format("dialog.unsaved.message", name),
                msg.get("dialog.unsaved.title"), JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, null,
                options, options[0]);
        return switch (choice) {
            case 0 -> Unsaved.SAVE;
            case 1 -> Unsaved.DISCARD;
            default -> Unsaved.CANCEL;
        };
    }

    @Override
    public Optional<Path> chooseOpen(Optional<Path> folder) {
        JFileChooser chooser = chooser(folder);
        chooser.setDialogTitle(Mnemonics.strip(msg.get("menu.file.open")));
        FileNameExtensionFilter labels = new FileNameExtensionFilter(msg.get("chooser.label"),
                LabelFiles.EXTENSION.substring(1));
        chooser.addChoosableFileFilter(labels);
        chooser.setFileFilter(labels);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return Optional.empty();
        return Optional.ofNullable(chooser.getSelectedFile()).map(File::toPath);
    }

    @Override
    public Optional<Path> chooseSave(Optional<Path> folder, String suggestedName, String extension) {
        JFileChooser chooser = chooser(folder);
        String ext = extension.startsWith(".") ? extension.substring(1) : extension;
        chooser.setFileFilter(new FileNameExtensionFilter(msg.get(ext.equals("pdf") ? "chooser.pdf" : "chooser.label"),
                ext));
        chooser.setSelectedFile(new File(chooser.getCurrentDirectory(), suggestedName.replaceAll("[/\\\\:]", "-")));
        while (true) {
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return Optional.empty();
            File f = chooser.getSelectedFile();
            if (f == null) return Optional.empty();
            if (!f.getName().toLowerCase(Locale.ROOT).endsWith("." + ext)) f = new File(f.getPath() + "." + ext);
            if (!f.exists() || JOptionPane.showConfirmDialog(this, msg.format("dialog.overwrite", f.getName()),
                    Mnemonics.strip(msg.get("menu.file.save-as")), JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION) {
                return Optional.of(f.toPath());
            }
        }
    }

    @Override
    public Optional<Path> choosePicture(Optional<Path> folder) {
        JFileChooser chooser = chooser(folder.or(() -> Optional.of(services.pictures())));
        chooser.setDialogTitle(Mnemonics.strip(msg.get("menu.insert.picture")));
        FileNameExtensionFilter pictures = new FileNameExtensionFilter(msg.get("chooser.picture"), "png", "jpg",
                "jpeg", "gif", "bmp");
        chooser.addChoosableFileFilter(pictures);
        chooser.setFileFilter(pictures);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return Optional.empty();
        return Optional.ofNullable(chooser.getSelectedFile()).map(File::toPath);
    }

    private JFileChooser chooser(Optional<Path> folder) {
        Path start = folder.filter(Files::isDirectory).orElse(services.documents());
        return new JFileChooser(Files.isDirectory(start) ? start.toFile() : null);
    }

    @Override
    public Optional<SheetTemplate> chooseSheet(SheetTemplate current) {
        return new SheetDialog(this, msg, services.catalog(), presenter.sheetPainter()).choose(current);
    }

    @Override
    public Optional<DataChoice> chooseData(DataSource source, MergeSettings merge, DataTable table,
                                           Set<String> designFields) {
        return new DataDialog(this, msg, services.data(), services.background(), services.documents())
                .choose(source, merge, table, designFields);
    }

    @Override
    public Optional<PrintChoice> choosePrint(PrintRequest request) {
        return new PrintDialog(this, msg, presenter.sheetPainter()).choose(request);
    }

    @Override
    public boolean insertIntoText(String field) {
        return properties.insertIntoText(field);
    }

    @Override
    public void editContent() {
        properties.focusContent();
    }

    @Override
    public void showAbout() {
        JOptionPane.showMessageDialog(this,
                "<html><center><h2>amade-labelprinter " + escape(version) + "</h2>"
                        + "<p>" + escape(msg.get("dialog.about.text")) + "</p><br>"
                        + "<small>&copy; 2026 datazuul.com &bull; EUPL-1.2</small></center></html>",
                msg.get("dialog.about.title"), JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void close() {
        dispose();
    }
}
