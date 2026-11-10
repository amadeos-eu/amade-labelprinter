package eu.amadeos.apps.labelprinter.ui.dialogs;

import eu.amadeos.apps.labelprinter.domain.PrintPlan;
import eu.amadeos.apps.labelprinter.domain.SheetTemplate;
import eu.amadeos.apps.labelprinter.service.SheetPainter;
import eu.amadeos.apps.labelprinter.ui.main.MainView;
import eu.amadeos.apps.labelprinter.ui.main.PrintChoice;
import eu.amadeos.apps.labelprinter.ui.parts.SheetView;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.Optional;

/**
 * Printing: the printer (or a PDF file), which labels, the first free label of a sheet that was used before (click
 * it on the sheet), and the fine adjustment of the printer with its test page.
 */
public final class PrintDialog extends JDialog {
    private final Messages msg;
    private final SheetView sheet;
    private final JComboBox<String> printer = new JComboBox<>();
    private final JRadioButton all;
    private final JRadioButton current;
    private final JRadioButton fill;
    private final JSpinner row;
    private final JSpinner column;
    private final JLabel startLabel = new JLabel();
    private final JLabel result = new JLabel(" ");
    private final JSpinner dx = DialogParts.mm(0, -10, 10, 0.1);
    private final JSpinner dy = DialogParts.mm(0, -10, 10, 0.1);
    private final JCheckBox outlines;
    private final JButton testPage;
    private final JButton ok;
    private MainView.PrintRequest request;
    private SheetTemplate template;
    private PrintChoice choice;
    private boolean updating;

    public PrintDialog(Window owner, Messages msg, SheetPainter painter) {
        super(owner, Mnemonics.strip(msg.get("menu.file.print")), ModalityType.APPLICATION_MODAL);
        this.msg = msg;
        setName("printDialog");
        sheet = new SheetView(painter, new Dimension(300, 420));
        sheet.setName("print.sheet");
        sheet.onClick(this::startAt);

        all = radio("print.all");
        current = radio("print.current");
        fill = radio("print.fill");
        ButtonGroup bg = new ButtonGroup();
        bg.add(all);
        bg.add(current);
        bg.add(fill);
        all.setSelected(true);
        row = DialogParts.count(1, 1, 1);
        column = DialogParts.count(1, 1, 1);
        row.setName("print.row");
        column.setName("print.column");
        outlines = new JCheckBox(msg.get("print.outlines"));
        outlines.setName("print.outlines");
        printer.setName("print.printer");
        result.setName("print.result");
        dx.setName("print.dx");
        dy.setName("print.dy");

        DialogParts.Form form = new DialogParts.Form();
        form.row(msg.get("print.printer"), printer);
        JPanel what = new JPanel(new GridLayout(3, 1));
        what.add(all);
        what.add(current);
        what.add(fill);
        form.row(msg.get("print.what"), what);
        form.row(msg.get("print.start"), DialogParts.flow(new JLabel(msg.get("print.row")), row,
                new JLabel(msg.get("print.column")), column, startLabel));
        form.row(msg.get("print.result"), result);
        JLabel adjust = DialogParts.bold(msg.get("print.adjust"));
        adjust.setBorder(new EmptyBorder(12, 0, 0, 0));
        form.wide(adjust);
        form.wide(DialogParts.flow(new JLabel(msg.get("print.right")), dx, new JLabel(msg.get("print.down")), dy,
                new JLabel(msg.get("editor.mm"))));
        form.wide(outlines);
        testPage = DialogParts.button(msg, "print.test-page", this::testPage);
        form.wide(DialogParts.flow(testPage));
        form.wide(DialogParts.hint(msg.get("print.adjust.hint"), 420));
        form.filler();
        form.setPreferredSize(new Dimension(480, 440));

        JPanel left = new JPanel(new BorderLayout(0, 6));
        left.add(DialogParts.bold(msg.get("print.start.hint")), BorderLayout.NORTH);
        left.add(sheet, BorderLayout.CENTER);

        JPanel root = new JPanel(new BorderLayout(14, 10));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));
        root.add(left, BorderLayout.WEST);
        root.add(form, BorderLayout.CENTER);
        ok = DialogParts.button(msg, "print.ok", this::accept);
        JButton cancel = DialogParts.button(msg, "button.cancel", this::dispose);
        root.add(DialogParts.okCancel(this, ok, cancel), BorderLayout.SOUTH);
        setContentPane(root);

        for (JRadioButton r : new JRadioButton[]{all, current, fill}) r.addActionListener(e -> update());
        row.addChangeListener(e -> update());
        column.addChangeListener(e -> update());
        printer.addActionListener(e -> printerChosen());
        pack();
        setLocationRelativeTo(owner);
    }

    private JRadioButton radio(String key) {
        JRadioButton r = Mnemonics.apply(new JRadioButton(), msg.get(key));
        r.setName(key);
        return r;
    }

    /** Shows the dialog; empty if it was cancelled. */
    public Optional<PrintChoice> choose(MainView.PrintRequest r) {
        request = r;
        template = r.design().sheet();
        updating = true;
        try {
            for (String p : r.printers()) printer.addItem(p);
            printer.addItem(msg.get("print.pdf"));
            r.last().filter(r.printers()::contains).ifPresent(printer::setSelectedItem);
            row.setModel(new javax.swing.SpinnerNumberModel(1, 1, template.rows(), 1));
            column.setModel(new javax.swing.SpinnerNumberModel(1, 1, template.columns(), 1));
            int records = r.plans().apply(PrintChoice.Mode.ALL).labels().size();
            all.setText(Mnemonics.strip(msg.format("print.all.count", records)));
        } finally {
            updating = false;
        }
        printerChosen();
        setVisible(true);
        return Optional.ofNullable(choice);
    }

    private boolean pdf() {
        return printer.getSelectedIndex() == printer.getItemCount() - 1;
    }

    private void printerChosen() {
        if (updating) return;
        boolean pdf = pdf();
        double[] offset = pdf ? new double[]{0, 0} : request.offsets().apply((String) printer.getSelectedItem());
        updating = true;
        dx.setValue(offset[0]);
        dy.setValue(offset[1]);
        updating = false;
        dx.setEnabled(!pdf);
        dy.setEnabled(!pdf);
        testPage.setEnabled(!pdf);
        update();
    }

    private PrintChoice.Mode mode() {
        return current.isSelected() ? PrintChoice.Mode.CURRENT
                : fill.isSelected() ? PrintChoice.Mode.FILL_SHEET : PrintChoice.Mode.ALL;
    }

    /** The first free position, from 0. */
    int start() {
        return ((Integer) row.getValue() - 1) * template.columns() + (Integer) column.getValue() - 1;
    }

    private void startAt(int position) {
        row.setValue(position / template.columns() + 1);
        column.setValue(position % template.columns() + 1);
    }

    /** The plan as it will print, with the first free position. */
    PrintPlan plan() {
        PrintChoice.Mode mode = mode();
        PrintPlan base = request.plans().apply(mode);
        if (mode == PrintChoice.Mode.FILL_SHEET) {
            return new PrintPlan(template, base.labels().subList(0, Math.max(1, template.count() - start())),
                    start());
        }
        return new PrintPlan(template, base.labels(), start());
    }

    void update() {
        if (updating || request == null) return;
        PrintPlan p = plan();
        sheet.show(request.design(), p, 0, false, -1);
        startLabel.setText(msg.format("print.position", start() + 1));
        result.setText(msg.format("print.result.text", p.sheets(), p.leftOver()));
        String button = pdf() ? msg.get("print.ok.pdf") : msg.format("print.ok.sheets", p.sheets());
        Mnemonics.apply(ok, button);
    }

    PrintChoice current() {
        String name = pdf() ? null : (String) printer.getSelectedItem();
        return new PrintChoice(name, mode(), start(), pdf() ? 0 : DialogParts.number(dx),
                pdf() ? 0 : DialogParts.number(dy), outlines.isSelected());
    }

    private void testPage() {
        if (!pdf()) request.testPage().accept(current());
    }

    private void accept() {
        choice = current();
        dispose();
    }
}
