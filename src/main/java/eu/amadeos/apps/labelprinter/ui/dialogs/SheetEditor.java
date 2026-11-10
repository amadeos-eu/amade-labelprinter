package eu.amadeos.apps.labelprinter.ui.dialogs;

import eu.amadeos.apps.labelprinter.domain.LabelShape;
import eu.amadeos.apps.labelprinter.domain.SheetKind;
import eu.amadeos.apps.labelprinter.domain.SheetTemplate;
import eu.amadeos.apps.labelprinter.service.LabelPainter;
import eu.amadeos.apps.labelprinter.service.SheetPainter;
import eu.amadeos.apps.labelprinter.ui.parts.SheetView;
import eu.amadeos.commons.i18n.Messages;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Window;
import java.util.Optional;

/**
 * Measuring an own sheet: the paper, how many labels across and down, their size, the margins to the first label
 * and the gaps between them. The drawing on the right follows every change; a sheet whose labels do not fit on the
 * paper cannot be saved.
 */
public final class SheetEditor extends JDialog {
    private static final double MAX = 1000;

    private final Messages msg;
    private final JComboBox<SheetKind> kind = new JComboBox<>(SheetKind.values());
    private final JComboBox<Object> paper = new JComboBox<>();
    private final JSpinner paperW = DialogParts.mm(210, 20, MAX, 0.5);
    private final JSpinner paperH = DialogParts.mm(297, 20, MAX, 0.5);
    private final JSpinner columns = DialogParts.count(3, 1, 30);
    private final JSpinner rows = DialogParts.count(8, 1, 60);
    private final JSpinner width = DialogParts.mm(70, 5, MAX, 0.1);
    private final JSpinner height = DialogParts.mm(37, 5, MAX, 0.1);
    private final JSpinner left = DialogParts.mm(0, -1, MAX, 0.1);
    private final JSpinner top = DialogParts.mm(0, -1, MAX, 0.1);
    private final JSpinner gapX = DialogParts.mm(0, 0, MAX, 0.1);
    private final JSpinner gapY = DialogParts.mm(0, 0, MAX, 0.1);
    private final JSpinner radius = DialogParts.mm(0, 0, 50, 0.5);
    private final JComboBox<LabelShape> shape = new JComboBox<>(LabelShape.values());
    private final JSpinner hole = DialogParts.mm(0, 0, 60, 0.5);
    private final SheetView preview;
    private final JLabel problem = new JLabel(" ");
    private final JButton ok;
    private SheetTemplate result;
    private boolean updating;

    public SheetEditor(Window owner, Messages msg) {
        super(owner, msg.get("editor.title"), ModalityType.APPLICATION_MODAL);
        this.msg = msg;
        setName("sheetEditor");
        preview = new SheetView(new SheetPainter(new LabelPainter(n -> null)), new Dimension(260, 360));

        String[] names = {"editor.paper-w", "editor.paper-h", "editor.columns", "editor.rows", "editor.width",
                "editor.height", "editor.left", "editor.top", "editor.gap-x", "editor.gap-y", "editor.radius",
                "editor.hole"};
        JSpinner[] spinners = {paperW, paperH, columns, rows, width, height, left, top, gapX, gapY, radius, hole};
        for (int i = 0; i < names.length; i++) spinners[i].setName(names[i]);
        paper.setName("editor.paper");
        kind.setRenderer(renderer(v -> msg.get(((SheetKind) v).key())));
        shape.setRenderer(renderer(v -> msg.get("label-shape." + ((LabelShape) v).name().toLowerCase(
                java.util.Locale.ROOT))));
        for (SheetTemplate.Paper p : SheetTemplate.Paper.values()) paper.addItem(p);
        paper.addItem(msg.get("editor.paper.other"));
        paper.setRenderer(renderer(v -> v instanceof SheetTemplate.Paper p ? p.label() + " ("
                + SheetTemplate.mm(p.width()) + " × " + SheetTemplate.mm(p.height()) + " mm)" : String.valueOf(v)));

        DialogParts.Form form = new DialogParts.Form();
        form.row(msg.get("editor.kind"), kind);
        form.row(msg.get("editor.paper"), paper);
        form.row(msg.get("editor.paper-size"), DialogParts.flow(paperW, new JLabel("×"), paperH, mmLabel()));
        form.row(msg.get("editor.grid"), DialogParts.flow(columns, new JLabel(msg.get("editor.across")), rows,
                new JLabel(msg.get("editor.down"))));
        form.row(msg.get("editor.label-size"), DialogParts.flow(width, new JLabel("×"), height, mmLabel()));
        form.row(msg.get("editor.margins"), DialogParts.flow(new JLabel(msg.get("editor.left")), left,
                new JLabel(msg.get("editor.top")), top, mmLabel()));
        form.row(msg.get("editor.gaps"), DialogParts.flow(new JLabel(msg.get("editor.across")), gapX,
                new JLabel(msg.get("editor.down")), gapY, mmLabel()));
        form.row(msg.get("editor.shape"), DialogParts.flow(shape, new JLabel(msg.get("editor.radius")), radius,
                new JLabel(msg.get("editor.hole")), hole, mmLabel()));
        form.wide(DialogParts.hint(msg.get("editor.hint"), 430));
        problem.setForeground(new Color(0xC0392B));
        problem.setName("editor.problem");
        form.wide(problem);
        form.filler();

        JPanel root = new JPanel(new BorderLayout(14, 10));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));
        root.add(form, BorderLayout.CENTER);
        root.add(preview, BorderLayout.EAST);
        ok = DialogParts.button(msg, "button.save", this::accept);
        JButton cancel = DialogParts.button(msg, "button.cancel", this::dispose);
        root.add(DialogParts.okCancel(this, ok, cancel), BorderLayout.SOUTH);
        setContentPane(root);

        paper.addActionListener(e -> {
            if (updating) return;
            if (paper.getSelectedItem() instanceof SheetTemplate.Paper p) {
                updating = true;
                paperW.setValue(p.width());
                paperH.setValue(p.height());
                updating = false;
            }
            update();
        });
        for (JSpinner s : new JSpinner[]{paperW, paperH, columns, rows, width, height, left, top, gapX, gapY, radius,
                hole}) {
            s.addChangeListener(e -> {
                if (!updating) update();
            });
        }
        kind.addActionListener(e -> update());
        shape.addActionListener(e -> update());
        pack();
        setLocationRelativeTo(owner);
    }

    private JLabel mmLabel() {
        return new JLabel(msg.get("editor.mm"));
    }

    private static DefaultListCellRenderer renderer(java.util.function.Function<Object, String> text) {
        return new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                          boolean focus) {
                return super.getListCellRendererComponent(list, value == null ? "" : text.apply(value), index,
                        selected, focus);
            }
        };
    }

    /** Shows the editor with the values of a sheet; empty if it was cancelled. */
    public Optional<SheetTemplate> edit(SheetTemplate start) {
        load(start);
        update();
        setVisible(true);
        return Optional.ofNullable(result);
    }

    void load(SheetTemplate s) {
        updating = true;
        try {
            kind.setSelectedItem(s.kind());
            Object p = msg.get("editor.paper.other");
            for (SheetTemplate.Paper candidate : SheetTemplate.Paper.values()) {
                if (candidate.matches(s.paperW(), s.paperH())) p = candidate;
            }
            paper.setSelectedItem(p);
            paperW.setValue(s.paperW());
            paperH.setValue(s.paperH());
            columns.setValue(s.columns());
            rows.setValue(s.rows());
            width.setValue(s.width());
            height.setValue(s.height());
            left.setValue(s.left());
            top.setValue(s.top());
            gapX.setValue(s.gapX());
            gapY.setValue(s.gapY());
            radius.setValue(s.radius());
            shape.setSelectedItem(s.shape());
            hole.setValue(s.hole());
        } finally {
            updating = false;
        }
    }

    /** The sheet the fields describe, or a message saying what is wrong. */
    Object sheet() {
        double w = DialogParts.number(width);
        double h = DialogParts.number(height);
        try {
            return new SheetTemplate("own-edit", (SheetKind) kind.getSelectedItem(), DialogParts.number(paperW),
                    DialogParts.number(paperH), (Integer) columns.getValue(), (Integer) rows.getValue(), w, h,
                    DialogParts.number(left), DialogParts.number(top), w + DialogParts.number(gapX),
                    h + DialogParts.number(gapY), DialogParts.number(radius), (LabelShape) shape.getSelectedItem(),
                    DialogParts.number(hole));
        } catch (IllegalArgumentException e) {
            return msg.get("editor.problem");
        }
    }

    void update() {
        boolean other = !(paper.getSelectedItem() instanceof SheetTemplate.Paper);
        paperW.setEnabled(other);
        paperH.setEnabled(other);
        hole.setEnabled(shape.getSelectedItem() == LabelShape.DISC);
        radius.setEnabled(shape.getSelectedItem() == LabelShape.RECTANGLE);
        Object s = sheet();
        if (s instanceof SheetTemplate t) {
            problem.setText(" ");
            preview.showEmpty(t, -1);
            ok.setEnabled(true);
        } else {
            problem.setText(String.valueOf(s));
            ok.setEnabled(false);
        }
    }

    private void accept() {
        if (sheet() instanceof SheetTemplate t) {
            result = t;
            dispose();
        }
    }
}
