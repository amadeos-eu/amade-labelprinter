package eu.amadeos.apps.labelprinter.ui.parts;

import eu.amadeos.apps.labelprinter.domain.Align;
import eu.amadeos.apps.labelprinter.domain.Background;
import eu.amadeos.apps.labelprinter.domain.Box;
import eu.amadeos.apps.labelprinter.domain.CodeType;
import eu.amadeos.apps.labelprinter.domain.CutGuides;
import eu.amadeos.apps.labelprinter.domain.Element;
import eu.amadeos.apps.labelprinter.domain.LabelDesign;
import eu.amadeos.apps.labelprinter.domain.ShapeKind;
import eu.amadeos.apps.labelprinter.domain.TextStyle;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.ToolButtons;
import eu.amadeos.commons.swing.ToolIcon;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The settings of the selected element under the design, or of the label itself when nothing is selected:
 * its background (colour or picture, up to the cut edge), one or two sides, the cut guides and the counter.
 */
public final class PropertiesPanel extends JPanel {
    /** What the panel changes; each call is one step for undo. */
    public interface Listener {
        void apply(Element element);

        void background(Background background);

        /** Asks for a picture file for the element ({@code id}) or, with null, for the background. */
        void choosePicture(String id);

        void twoSided(boolean two);

        void cutGuides(CutGuides guides);

        void counter(int start, int digits);
    }

    private final Messages msg;
    private final Supplier<List<Color>> recent;
    private final Consumer<Color> remember;
    private final CardLayout cards = new CardLayout();
    private final JPanel deck = new JPanel(cards);
    private final JLabel title = new JLabel();
    private Listener listener;
    private Element element;
    private boolean updating;

    // text
    private final JTextArea text = new JTextArea(3, 24);
    private final JComboBox<String> family = new JComboBox<>(fontFamilies());
    private final JSpinner size = new JSpinner(new SpinnerNumberModel(10.0, 3.0, 200.0, 0.5));
    private final JToggleButton bold = toggle(LabelGlyph.BOLD, "prop.bold");
    private final JToggleButton italic = toggle(LabelGlyph.ITALIC, "prop.italic");
    private final JToggleButton underline = toggle(LabelGlyph.UNDERLINE, "prop.underline");
    private final JToggleButton left = toggle(LabelGlyph.ALIGN_LEFT, "prop.align.left");
    private final JToggleButton center = toggle(LabelGlyph.ALIGN_CENTER, "prop.align.center");
    private final JToggleButton right = toggle(LabelGlyph.ALIGN_RIGHT, "prop.align.right");
    private final ColorButton textColor;
    private final ColorButton textBackground;
    private final JCheckBox shrink;
    private final JCheckBox hideEmpty;
    private final BoxFields textBox;
    // picture
    private final JCheckBox keepAspect;
    private final BoxFields pictureBox;
    // shape
    private final JComboBox<ShapeKind> shapeKind = new JComboBox<>(ShapeKind.values());
    private final ColorButton fill;
    private final ColorButton stroke;
    private final JSpinner strokeWidth = new JSpinner(new SpinnerNumberModel(0.3, 0.0, 20.0, 0.1));
    private final JSpinner radius = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 100.0, 0.5));
    private final BoxFields shapeBox;
    // code
    private final JComboBox<CodeType> codeType = new JComboBox<>(CodeType.values());
    private final JTextField codeContent = new JTextField(24);
    private final ColorButton codeColor;
    private final JCheckBox showText;
    private final BoxFields codeBox;
    // label
    private final JRadioButton bgNone;
    private final JRadioButton bgColor;
    private final JRadioButton bgPicture;
    private final ColorButton bgColorButton;
    private final JCheckBox bleed;
    private final JComboBox<String> sides;
    private final JComboBox<CutGuides> guides = new JComboBox<>(CutGuides.values());
    private final JSpinner counterStart = new JSpinner(new SpinnerNumberModel(1, -999_999, 99_999_999, 1));
    private final JSpinner counterDigits = new JSpinner(new SpinnerNumberModel(1, 1, 9, 1));
    private Background background = Background.NONE;

    public PropertiesPanel(Messages msg, Supplier<List<Color>> recent, Consumer<Color> remember) {
        super(new BorderLayout(0, 6));
        this.msg = msg;
        textBox = new BoxFields();
        pictureBox = new BoxFields();
        shapeBox = new BoxFields();
        codeBox = new BoxFields();
        for (JToggleButton b : List.of(bold, italic, underline, left, center, right)) {
            b.setToolTipText(msg.get(b.getName()));
        }
        counterStart.setPreferredSize(new Dimension(90, counterStart.getPreferredSize().height));
        this.recent = recent;
        this.remember = remember;
        setName("properties");
        setBorder(new EmptyBorder(6, 0, 0, 0));
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        title.setName("prop.title");
        add(title, BorderLayout.NORTH);
        add(deck, BorderLayout.CENTER);

        textColor = color(false, c -> applyText(s -> s.withColor(c)));
        textColor.setName("prop.text.color");
        textBackground = color(true, c -> applyText(s -> s.withBackground(c)));
        textBackground.setName("prop.text.background");
        shrink = check("prop.shrink");
        hideEmpty = check("prop.hide-empty");
        keepAspect = check("prop.keep-aspect");
        fill = color(true, c -> applyFigure(f -> f.withFill(c)));
        fill.setName("prop.fill");
        stroke = color(true, c -> applyFigure(f -> f.withStroke(c)));
        stroke.setName("prop.stroke");
        codeColor = color(false, c -> applyCode(k -> k.withColor(c)));
        codeColor.setName("prop.code.color");
        showText = check("prop.code.show-text");
        bgNone = new JRadioButton(msg.get("prop.background.none"));
        bgColor = new JRadioButton(msg.get("prop.background.color"));
        bgPicture = new JRadioButton(msg.get("prop.background.picture"));
        bgColorButton = color(false, c -> {
            if (listener != null && !updating) listener.background(background.withColor(c));
        });
        bgColorButton.setName("prop.background.color-button");
        bleed = check("prop.bleed");
        sides = new JComboBox<>(new String[]{msg.get("prop.sides.one"), msg.get("prop.sides.two")});

        deck.add(textPanel(), "text");
        deck.add(picturePanel(), "picture");
        deck.add(shapePanel(), "shape");
        deck.add(codePanel(), "code");
        deck.add(labelPanel(), "label");
        cards.show(deck, "label");
        wire();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Shows the settings of an element. */
    public void show(Element e) {
        updating = true;
        try {
            element = e;
            switch (e) {
                case Element.Text t -> showText(t);
                case Element.Picture p -> {
                    title.setText(msg.get("prop.title.picture"));
                    keepAspect.setSelected(p.keepAspect());
                    pictureBox.show(p.box());
                    cards.show(deck, "picture");
                }
                case Element.Figure f -> {
                    title.setText(msg.get("prop.title.shape"));
                    shapeKind.setSelectedItem(f.kind());
                    fill.setColor(f.fill());
                    stroke.setColor(f.stroke());
                    strokeWidth.setValue(f.strokeWidth());
                    radius.setValue(f.radius());
                    shapeBox.show(f.box());
                    cards.show(deck, "shape");
                }
                case Element.Code c -> {
                    title.setText(msg.get(c.type().square() ? "prop.title.code2d" : "prop.title.barcode"));
                    codeType.setSelectedItem(c.type());
                    if (!codeContent.getText().equals(c.content())) codeContent.setText(c.content());
                    codeColor.setColor(c.color());
                    showText.setSelected(c.showText());
                    showText.setEnabled(!c.type().square());
                    codeBox.show(c.box());
                    cards.show(deck, "code");
                }
            }
        } finally {
            updating = false;
        }
    }

    private void showText(Element.Text t) {
        TextStyle s = t.style();
        title.setText(msg.get("prop.title.text"));
        if (!text.getText().equals(t.text())) text.setText(t.text());
        family.setSelectedItem(s.family());
        size.setValue(s.size());
        bold.setSelected(s.bold());
        italic.setSelected(s.italic());
        underline.setSelected(s.underline());
        left.setSelected(s.align() == Align.LEFT);
        center.setSelected(s.align() == Align.CENTER);
        right.setSelected(s.align() == Align.RIGHT);
        textColor.setColor(s.color());
        textBackground.setColor(s.background());
        shrink.setSelected(t.shrink());
        hideEmpty.setSelected(t.hideEmptyLines());
        textBox.show(t.box());
        cards.show(deck, "text");
    }

    /** Shows the label's own settings: nothing is selected. */
    public void showLabel(LabelDesign design, boolean back) {
        updating = true;
        try {
            element = null;
            String side = design.twoSided() ? msg.get(back ? "prop.side.back" : "prop.side.front") : "";
            title.setText(msg.get(design.sheet().kind() == eu.amadeos.apps.labelprinter.domain.SheetKind.BUSINESS_CARD
                    ? "prop.title.card" : "prop.title.label") + (side.isEmpty() ? "" : " · " + side));
            background = design.side(back).background();
            if (background.image() != null) bgPicture.setSelected(true);
            else if (background.color() != null) bgColor.setSelected(true);
            else bgNone.setSelected(true);
            bgColorButton.setColor(background.color() == null ? Color.WHITE : background.color());
            bgColorButton.setEnabled(bgColor.isSelected());
            bleed.setSelected(background.bleed());
            sides.setSelectedIndex(design.twoSided() ? 1 : 0);
            guides.setSelectedItem(design.cutGuides());
            counterStart.setValue(design.counterStart());
            counterDigits.setValue(design.counterDigits());
            cards.show(deck, "label");
        } finally {
            updating = false;
        }
    }

    /** Puts {@code «Field»} into the text at the caret, if a text is shown; returns whether it did. */
    public boolean insertIntoText(String field) {
        if (!(element instanceof Element.Text)) return false;
        text.replaceSelection(field);
        text.requestFocusInWindow();
        return true;
    }

    /** Puts the focus into the text or code field, for a double click on the element. */
    public void focusContent() {
        if (element instanceof Element.Text) text.requestFocusInWindow();
        else if (element instanceof Element.Code) codeContent.requestFocusInWindow();
        else if (element instanceof Element.Picture p && listener != null) listener.choosePicture(p.id());
    }

    // ---- panels -----------------------------------------------------------------------------------------------

    private JPanel textPanel() {
        Grid g = new Grid();
        text.setLineWrap(false);
        text.setName("prop.text");
        JScrollPane sp = new JScrollPane(text);
        sp.setPreferredSize(new Dimension(260, 64));
        g.row(msg.get("prop.text"), sp);
        JPanel font = flow(family, size, new JLabel(msg.get("prop.pt")));
        family.setName("prop.font");
        size.setName("prop.size");
        g.row(msg.get("prop.font"), font);
        ButtonGroup align = new ButtonGroup();
        align.add(left);
        align.add(center);
        align.add(right);
        g.row("", flow(bold, italic, underline, separator(), left, center, right));
        g.row(msg.get("prop.colors"), flow(textColor, textBackground));
        g.row("", flow(shrink, hideEmpty));
        g.row(msg.get("prop.position"), textBox);
        return g.panel();
    }

    private JPanel picturePanel() {
        Grid g = new Grid();
        JButton choose = new JButton(msg.get("prop.picture.choose"));
        choose.setName("prop.picture.choose");
        choose.addActionListener(e -> {
            if (listener != null && element != null) listener.choosePicture(element.id());
        });
        g.row(msg.get("prop.picture"), flow(choose, keepAspect));
        g.row(msg.get("prop.position"), pictureBox);
        return g.panel();
    }

    private JPanel shapePanel() {
        Grid g = new Grid();
        shapeKind.setRenderer(enumRenderer(k -> msg.get(((ShapeKind) k).key())));
        shapeKind.setName("prop.shape");
        g.row(msg.get("prop.shape"), flow(shapeKind));
        g.row(msg.get("prop.colors"), flow(new JLabel(msg.get("prop.fill")), fill, new JLabel(msg.get("prop.stroke")),
                stroke));
        strokeWidth.setName("prop.stroke-width");
        radius.setName("prop.radius");
        g.row(msg.get("prop.stroke-width"), flow(strokeWidth, new JLabel("mm"), new JLabel(msg.get("prop.radius")),
                radius, new JLabel("mm")));
        g.row(msg.get("prop.position"), shapeBox);
        return g.panel();
    }

    private JPanel codePanel() {
        Grid g = new Grid();
        codeType.setRenderer(enumRenderer(k -> msg.get(((CodeType) k).key())));
        codeType.setName("prop.code.type");
        codeContent.setName("prop.code.content");
        g.row(msg.get("prop.code.type"), flow(codeType, codeColor));
        g.row(msg.get("prop.code.content"), codeContent);
        g.row("", flow(showText, new JLabel(msg.get("prop.code.hint"))));
        g.row(msg.get("prop.position"), codeBox);
        return g.panel();
    }

    private JPanel labelPanel() {
        Grid g = new Grid();
        ButtonGroup bg = new ButtonGroup();
        bg.add(bgNone);
        bg.add(bgColor);
        bg.add(bgPicture);
        bgNone.setName("prop.background.none");
        bgColor.setName("prop.background.color");
        bgPicture.setName("prop.background.picture");
        g.row(msg.get("prop.background"), flow(bgNone, bgColor, bgColorButton, bgPicture));
        g.row("", flow(bleed));
        sides.setName("prop.sides");
        guides.setName("prop.cut");
        guides.setRenderer(enumRenderer(k -> msg.get(((CutGuides) k).key())));
        g.row(msg.get("prop.sides"), flow(sides, new JLabel(msg.get("prop.cut")), guides));
        counterStart.setName("prop.counter.start");
        counterDigits.setName("prop.counter.digits");
        g.row(msg.get("prop.counter"), flow(counterStart, new JLabel(msg.get("prop.counter.digits")), counterDigits));
        g.row("", new JLabel(msg.get("prop.label.hint")));
        return g.panel();
    }

    // ---- wiring -----------------------------------------------------------------------------------------------

    private void wire() {
        text.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                textChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                textChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
            }
        });
        family.addActionListener(e -> applyText(s -> s.withFamily((String) family.getSelectedItem())));
        size.addChangeListener(e -> applyText(s -> s.withSize(((Number) size.getValue()).doubleValue())));
        bold.addActionListener(e -> applyText(s -> s.withBold(bold.isSelected())));
        italic.addActionListener(e -> applyText(s -> s.withItalic(italic.isSelected())));
        underline.addActionListener(e -> applyText(s -> s.withUnderline(underline.isSelected())));
        left.addActionListener(e -> applyText(s -> s.withAlign(Align.LEFT)));
        center.addActionListener(e -> applyText(s -> s.withAlign(Align.CENTER)));
        right.addActionListener(e -> applyText(s -> s.withAlign(Align.RIGHT)));
        shrink.addActionListener(e -> apply(el -> el instanceof Element.Text t ? t.withShrink(shrink.isSelected())
                : el));
        hideEmpty.addActionListener(e -> apply(el -> el instanceof Element.Text t
                ? t.withHideEmptyLines(hideEmpty.isSelected()) : el));
        keepAspect.addActionListener(e -> apply(el -> el instanceof Element.Picture p
                ? p.withKeepAspect(keepAspect.isSelected()) : el));
        shapeKind.addActionListener(e -> applyFigure(f -> f.withKind((ShapeKind) shapeKind.getSelectedItem())));
        strokeWidth.addChangeListener(e -> applyFigure(f -> f.withStrokeWidth(num(strokeWidth))));
        radius.addChangeListener(e -> applyFigure(f -> f.withRadius(num(radius))));
        codeType.addActionListener(e -> applyCode(c -> c.withType((CodeType) codeType.getSelectedItem())));
        codeContent.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyCode(c -> c.withContent(codeContent.getText()));
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyCode(c -> c.withContent(codeContent.getText()));
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
            }
        });
        showText.addActionListener(e -> applyCode(c -> c.withShowText(showText.isSelected())));
        for (BoxFields b : List.of(textBox, pictureBox, shapeBox, codeBox)) {
            b.onChange(box -> apply(el -> el.withBox(box)));
        }
        bgNone.addActionListener(e -> {
            bgColorButton.setEnabled(false);
            if (listener != null && !updating) listener.background(new Background(null, null, background.bleed()));
        });
        bgColor.addActionListener(e -> {
            bgColorButton.setEnabled(true);
            if (listener != null && !updating) {
                listener.background(new Background(bgColorButton.color() == null ? Color.WHITE : bgColorButton.color(),
                        null, background.bleed()));
            }
        });
        bgPicture.addActionListener(e -> {
            bgColorButton.setEnabled(false);
            if (listener != null && !updating) listener.choosePicture(null);
        });
        bleed.addActionListener(e -> {
            if (listener != null && !updating) listener.background(background.withBleed(bleed.isSelected()));
        });
        sides.addActionListener(e -> {
            if (listener != null && !updating) listener.twoSided(sides.getSelectedIndex() == 1);
        });
        guides.addActionListener(e -> {
            if (listener != null && !updating) listener.cutGuides((CutGuides) guides.getSelectedItem());
        });
        counterStart.addChangeListener(e -> counterChanged());
        counterDigits.addChangeListener(e -> counterChanged());
    }

    private void counterChanged() {
        if (listener != null && !updating) {
            listener.counter(((Number) counterStart.getValue()).intValue(),
                    ((Number) counterDigits.getValue()).intValue());
        }
    }

    private void textChanged() {
        apply(el -> el instanceof Element.Text t ? t.withText(text.getText()) : el);
    }

    private void applyText(java.util.function.UnaryOperator<TextStyle> change) {
        apply(el -> el instanceof Element.Text t ? t.withStyle(change.apply(t.style())) : el);
    }

    private void applyFigure(java.util.function.UnaryOperator<Element.Figure> change) {
        apply(el -> el instanceof Element.Figure f ? change.apply(f) : el);
    }

    private void applyCode(java.util.function.UnaryOperator<Element.Code> change) {
        apply(el -> el instanceof Element.Code c ? change.apply(c) : el);
    }

    private void apply(java.util.function.UnaryOperator<Element> change) {
        if (updating || element == null || listener == null) return;
        Element changed = change.apply(element);
        if (changed.equals(element)) return;
        element = changed;
        listener.apply(changed);
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private ColorButton color(boolean allowNone, Consumer<Color> chosen) {
        return new ColorButton(msg, allowNone, recent, c -> {
            if (c != null) remember.accept(c);
            chosen.accept(c);
        });
    }

    private JCheckBox check(String key) {
        JCheckBox box = new JCheckBox(msg.get(key));
        box.setName(key);
        return box;
    }

    private JToggleButton toggle(ToolIcon.Glyph glyph, String key) {
        Action a = new AbstractAction(key, new ToolIcon(glyph)) {
            @Override
            public void actionPerformed(ActionEvent e) {
            }
        };
        a.putValue(Action.ACTION_COMMAND_KEY, key);
        JToggleButton b = ToolButtons.toggle(a);
        b.setToolTipText(msgOrKey(key));
        return b;
    }

    private String msgOrKey(String key) {
        return msg == null ? key : msg.get(key);
    }

    private static JComponent separator() {
        JSeparator s = new JSeparator(SwingConstants.VERTICAL);
        s.setPreferredSize(new Dimension(6, 22));
        return s;
    }

    private static JPanel flow(Component... components) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        for (Component c : components) p.add(c);
        return p;
    }

    private static double num(JSpinner s) {
        return ((Number) s.getValue()).doubleValue();
    }

    private static String[] fontFamilies() {
        try {
            return GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        } catch (RuntimeException e) {
            return new String[]{TextStyle.DEFAULT_FAMILY};
        }
    }

    private static DefaultListCellRenderer enumRenderer(java.util.function.Function<Object, String> text) {
        return new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                          boolean focus) {
                return super.getListCellRendererComponent(list, value == null ? "" : text.apply(value), index,
                        selected, focus);
            }
        };
    }

    /** Label and field rows. */
    private static final class Grid {
        private final JPanel panel = new JPanel(new GridBagLayout());
        private int row;

        void row(String label, Component field) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridy = row++;
            c.gridx = 0;
            c.anchor = GridBagConstraints.WEST;
            c.insets = new Insets(2, 0, 2, 8);
            panel.add(new JLabel(label), c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            panel.add(field, c);
        }

        JPanel panel() {
            GridBagConstraints c = new GridBagConstraints();
            c.gridy = row;
            c.weighty = 1;
            panel.add(new JLabel(), c);
            return panel;
        }
    }

    /** X, Y, width and height in millimetres. */
    private final class BoxFields extends JPanel {
        private final JSpinner x = spinner();
        private final JSpinner y = spinner();
        private final JSpinner w = spinner();
        private final JSpinner h = spinner();
        private Consumer<Box> change;

        BoxFields() {
            super(new FlowLayout(FlowLayout.LEFT, 4, 0));
            add(new JLabel("X"));
            add(x);
            add(new JLabel("Y"));
            add(y);
            add(new JLabel(msg.get("prop.width")));
            add(w);
            add(new JLabel(msg.get("prop.height")));
            add(h);
            add(new JLabel("mm"));
            x.setName("prop.x");
            y.setName("prop.y");
            w.setName("prop.w");
            h.setName("prop.h");
            for (JSpinner s : List.of(x, y, w, h)) {
                s.addChangeListener(e -> {
                    if (!updating && change != null) change.accept(new Box(num(x), num(y), num(w), num(h)));
                });
            }
        }

        private JSpinner spinner() {
            JSpinner s = new JSpinner(new SpinnerNumberModel(0.0, -500.0, 1000.0, 0.5));
            s.setEditor(new JSpinner.NumberEditor(s, "0.0"));
            s.setPreferredSize(new Dimension(70, s.getPreferredSize().height));
            return s;
        }

        void onChange(Consumer<Box> change) {
            this.change = change;
        }

        void show(Box b) {
            x.setValue(b.x());
            y.setValue(b.y());
            w.setValue(b.w());
            h.setValue(b.h());
        }
    }

    /** A border for the panel when it stands on its own. */
    public void framed() {
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0,
                eu.amadeos.commons.swing.AmadeUI.theme().textInactive()), new EmptyBorder(6, 0, 0, 0)));
    }
}
