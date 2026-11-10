package eu.amadeos.apps.labelprinter.ui.dialogs;

import eu.amadeos.apps.labelprinter.domain.SheetKind;
import eu.amadeos.apps.labelprinter.domain.SheetTemplate;
import eu.amadeos.apps.labelprinter.service.SheetCatalog;
import eu.amadeos.apps.labelprinter.service.SheetPainter;
import eu.amadeos.apps.labelprinter.ui.parts.SheetView;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Choosing the sheet: the kinds on the left, the sheets of a kind on a paper in the middle (search by size, such as
 * {@code 70x37}), the chosen sheet drawn on the right. Own sheets are measured with the {@link SheetEditor}.
 */
public final class SheetDialog extends JDialog {
    /** An entry of the kind list: a kind, all sheets (null) or the own sheets. */
    record KindItem(SheetKind kind, boolean own, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    /** An entry of the paper box: a paper or any (null). */
    record PaperItem(SheetTemplate.Paper paper, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private final Messages msg;
    private final SheetCatalog catalog;
    private final DefaultListModel<KindItem> kindModel = new DefaultListModel<>();
    private final JList<KindItem> kinds = new JList<>(kindModel);
    private final JComboBox<PaperItem> paper = new JComboBox<>();
    private final JTextField search = new JTextField();
    private final SheetModel model = new SheetModel();
    private final JTable table;
    private final SheetView preview;
    private final JLabel details = new JLabel(" ");
    private final JButton edit;
    private final JButton delete;
    private SheetTemplate chosen;
    private boolean accepted;

    public SheetDialog(Window owner, Messages msg, SheetCatalog catalog, SheetPainter painter) {
        super(owner, msg.get("sheet.title"), ModalityType.APPLICATION_MODAL);
        this.msg = msg;
        this.catalog = catalog;
        this.table = new JTable(model);
        this.preview = new SheetView(painter, new Dimension(240, 330));
        setName("sheetDialog");

        JPanel root = new JPanel(new BorderLayout(12, 10));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel left = new JPanel(new BorderLayout(0, 6));
        left.setPreferredSize(new Dimension(210, 400));
        left.add(DialogParts.bold(msg.get("sheet.kind")), BorderLayout.NORTH);
        kinds.setName("sheet.kinds");
        kinds.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        left.add(new JScrollPane(kinds), BorderLayout.CENTER);
        paper.setName("sheet.paper");
        paper.addItem(new PaperItem(null, msg.get("sheet.paper.any")));
        for (SheetTemplate.Paper p : SheetTemplate.Paper.values()) {
            paper.addItem(new PaperItem(p, msg.format("sheet.paper.one", p.label())));
        }
        left.add(paper, BorderLayout.SOUTH);
        root.add(left, BorderLayout.WEST);

        JPanel mid = new JPanel(new BorderLayout(0, 6));
        search.setName("sheet.search");
        search.setToolTipText(msg.get("sheet.search.hint"));
        JPanel searchRow = new JPanel(new BorderLayout(6, 0));
        searchRow.add(Mnemonics.apply(new JLabel(), msg.get("sheet.search")), BorderLayout.WEST);
        searchRow.add(search, BorderLayout.CENTER);
        mid.add(searchRow, BorderLayout.NORTH);
        table.setName("sheet.table");
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(table.getRowHeight() + 6);
        table.setFillsViewportHeight(true);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setPreferredSize(new Dimension(470, 400));
        mid.add(tableScroll, BorderLayout.CENTER);
        mid.add(DialogParts.hint(msg.get("sheet.search.hint"), 450), BorderLayout.SOUTH);
        root.add(mid, BorderLayout.CENTER);

        JPanel right = new JPanel(new BorderLayout(0, 6));
        right.add(preview, BorderLayout.CENTER);
        details.setName("sheet.details");
        right.add(details, BorderLayout.SOUTH);
        root.add(right, BorderLayout.EAST);

        JButton ok = DialogParts.button(msg, "button.ok", this::accept);
        JButton cancel = DialogParts.button(msg, "button.cancel", this::dispose);
        JButton measure = DialogParts.button(msg, "sheet.measure", () -> editOwn(null));
        edit = DialogParts.button(msg, "sheet.edit", () -> editOwn(chosen));
        delete = DialogParts.button(msg, "sheet.delete", this::deleteOwn);
        root.add(DialogParts.okCancel(this, ok, cancel, measure, edit, delete), BorderLayout.SOUTH);
        setContentPane(root);

        kinds.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) refill();
        });
        paper.addActionListener(e -> refill());
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refill();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refill();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refill();
            }
        });
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) select(table.getSelectedRow() < 0 ? null
                    : model.rows.get(table.convertRowIndexToModel(table.getSelectedRow())));
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && chosen != null) accept();
            }
        });
        pack();
        setLocationRelativeTo(owner);
    }

    /** Shows the dialog; empty if it was cancelled. */
    public Optional<SheetTemplate> choose(SheetTemplate current) {
        fillKinds();
        for (int i = 0; i < paper.getItemCount(); i++) {
            SheetTemplate.Paper p = paper.getItemAt(i).paper();
            if (p != null && p.matches(current.paperW(), current.paperH())) paper.setSelectedIndex(i);
        }
        for (int i = 0; i < kindModel.size(); i++) {
            KindItem k = kindModel.get(i);
            if (current.isOwn() ? k.own() : k.kind() == current.kind()) kinds.setSelectedIndex(i);
        }
        showSheet(current);
        setVisible(true);
        return accepted ? Optional.ofNullable(chosen) : Optional.empty();
    }

    private void fillKinds() {
        KindItem selected = kinds.getSelectedValue();
        kindModel.clear();
        kindModel.addElement(new KindItem(null, false, msg.format("sheet.kind.all", catalog.all().size())));
        for (SheetKind k : SheetKind.values()) {
            int n = catalog.find(k, false, null, "").size();
            if (n > 0) kindModel.addElement(new KindItem(k, false, msg.get(k.key()) + " (" + n + ")"));
        }
        kindModel.addElement(new KindItem(null, true, msg.format("sheet.kind.own", catalog.own().size())));
        if (selected != null) {
            for (int i = 0; i < kindModel.size(); i++) {
                KindItem k = kindModel.get(i);
                if (k.own() == selected.own() && k.kind() == selected.kind()) kinds.setSelectedIndex(i);
            }
        }
    }

    void refill() {
        KindItem k = kinds.getSelectedValue();
        PaperItem p = (PaperItem) paper.getSelectedItem();
        List<SheetTemplate> found = catalog.find(k == null ? null : k.kind(), k != null && k.own(),
                p == null ? null : p.paper(), search.getText());
        SheetTemplate keep = chosen;
        model.set(found);
        int row = keep == null ? -1 : found.indexOf(keep);
        if (row >= 0) {
            table.setRowSelectionInterval(row, row);
            table.scrollRectToVisible(table.getCellRect(row, 0, true));
        } else if (!found.isEmpty()) {
            table.setRowSelectionInterval(0, 0);
        } else {
            select(null);
        }
    }

    private void showSheet(SheetTemplate sheet) {
        int row = model.rows.indexOf(sheet);
        if (row >= 0) {
            table.setRowSelectionInterval(row, row);
            table.scrollRectToVisible(table.getCellRect(row, 0, true));
        }
        select(sheet);
    }

    private void select(SheetTemplate sheet) {
        chosen = sheet;
        edit.setEnabled(sheet != null);
        delete.setEnabled(sheet != null && sheet.isOwn());
        if (sheet == null) {
            details.setText(" ");
            preview.showEmpty(catalog.standard(false), -1);
            return;
        }
        preview.showEmpty(sheet, -1);
        details.setText("<html>" + DialogParts.escape(msg.format("sheet.details", sheet.paperName(),
                sheet.gridText(), sheet.count(), sheet.sizeText(), SheetTemplate.mm(sheet.left()),
                SheetTemplate.mm(sheet.top()), SheetTemplate.mm(sheet.gapX()), SheetTemplate.mm(sheet.gapY())))
                + "</html>");
    }

    private void accept() {
        if (chosen == null) return;
        accepted = true;
        dispose();
    }

    /** Measures a new own sheet ({@code base} null: start from the chosen one) or changes an own one. */
    private void editOwn(SheetTemplate base) {
        SheetTemplate start = base != null ? base : chosen != null ? chosen : catalog.standard(false);
        boolean replace = base != null && base.isOwn();
        new SheetEditor(this, msg).edit(start).ifPresent(sheet -> {
            try {
                SheetTemplate toSave = replace ? sheet.withId(base.id()) : sheet.withId("new");
                SheetTemplate saved = catalog.saveOwn(toSave);
                fillKinds();
                for (int i = 0; i < kindModel.size(); i++) if (kindModel.get(i).own()) kinds.setSelectedIndex(i);
                refill();
                showSheet(saved);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, msg.format("sheet.error.save", e.getMessage()),
                        msg.get("dialog.error.title"), JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void deleteOwn() {
        if (chosen == null || !chosen.isOwn()) return;
        if (JOptionPane.showConfirmDialog(this, msg.format("sheet.delete.confirm", chosen.sizeText()),
                Mnemonics.strip(msg.get("sheet.delete")), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
                != JOptionPane.YES_OPTION) return;
        try {
            catalog.deleteOwn(chosen.id());
            chosen = null;
            fillKinds();
            refill();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, msg.format("sheet.error.save", e.getMessage()),
                    msg.get("dialog.error.title"), JOptionPane.ERROR_MESSAGE);
        }
    }

    /** For tests: the sheets in the table. */
    List<SheetTemplate> shown() {
        return List.copyOf(model.rows);
    }

    /** The sheets in the table. */
    private final class SheetModel extends AbstractTableModel {
        private final List<SheetTemplate> rows = new ArrayList<>();
        private final String[] columns = {"sheet.column.size", "sheet.column.grid", "sheet.column.count",
                "sheet.column.paper", "sheet.column.kind"};

        void set(List<SheetTemplate> sheets) {
            rows.clear();
            rows.addAll(sheets);
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return msg.get(columns[column]);
        }

        @Override
        public Object getValueAt(int row, int column) {
            SheetTemplate s = rows.get(row);
            return switch (column) {
                case 0 -> s.sizeText() + (s.radius() > 0 ? " ◜" : "");
                case 1 -> s.gridText();
                case 2 -> s.count();
                case 3 -> s.paperName();
                default -> (s.isOwn() ? "★ " : "") + msg.get(s.kind().key());
            };
        }
    }
}
