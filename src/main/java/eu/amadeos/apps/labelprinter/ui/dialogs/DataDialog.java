package eu.amadeos.apps.labelprinter.ui.dialogs;

import eu.amadeos.apps.labelprinter.domain.DataSource;
import eu.amadeos.apps.labelprinter.domain.DataTable;
import eu.amadeos.apps.labelprinter.domain.Merge;
import eu.amadeos.apps.labelprinter.domain.MergeSettings;
import eu.amadeos.apps.labelprinter.domain.Placeholders;
import eu.amadeos.apps.labelprinter.service.DataLoader;
import eu.amadeos.apps.labelprinter.ui.main.DataChoice;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * The data for mail merge: a file (CSV, spreadsheet, vCard), a group of the address books, or a list typed in.
 * Below, the records with a tick for each, a filter, the sort order, copies per record, and which column fills a
 * field of the design whose name the data does not have.
 */
public final class DataDialog extends JDialog {
    private final Messages msg;
    private final DataLoader loader;
    private final Executor background;
    private final Path documents;
    private final JRadioButton none;
    private final JRadioButton file;
    private final JRadioButton contacts;
    private final JRadioButton typed;
    private final JTextField path = new JTextField(30);
    private final JCheckBox header;
    private final JComboBox<String> group = new JComboBox<>();
    private final JTextArea typedText = new JTextArea(4, 30);
    private final JTextField filter = new JTextField(18);
    private final JComboBox<String> sortBy = new JComboBox<>();
    private final JSpinner copies = DialogParts.count(1, 1, 999);
    private DataTable data = DataTable.NONE;
    private final RecordModel model = new RecordModel();
    private final JTable table = new JTable(model);
    private final JLabel count = new JLabel(" ");
    private final JLabel error = new JLabel(" ");
    private final JPanel mappingPanel = new JPanel(new GridBagLayout());
    private final Map<String, JComboBox<String>> mapping = new LinkedHashMap<>();
    private Set<String> designFields = Set.of();
    private Set<Integer> excluded = new HashSet<>();
    private int loadSerial;
    private boolean updating;
    private DataChoice result;

    public DataDialog(Window owner, Messages msg, DataLoader loader, Executor background, Path documents) {
        super(owner, msg.get("data.title"), ModalityType.APPLICATION_MODAL);
        this.msg = msg;
        this.loader = loader;
        this.background = background;
        this.documents = documents;
        setName("dataDialog");

        none = radio("data.none");
        file = radio("data.file");
        contacts = radio("data.contacts");
        typed = radio("data.typed");
        ButtonGroup bg = new ButtonGroup();
        for (JRadioButton r : List.of(none, file, contacts, typed)) bg.add(r);
        header = new JCheckBox(msg.get("data.header"), true);
        header.setName("data.header");

        JPanel source = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.NORTHWEST;
        c.insets = new Insets(3, 0, 3, 8);
        c.gridy = 0;
        c.gridx = 0;
        source.add(none, c);
        c.gridy++;
        source.add(file, c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        path.setName("data.path");
        source.add(path, c);
        c.gridx = 2;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        JButton choose = DialogParts.button(msg, "data.choose", this::chooseFile);
        source.add(choose, c);
        c.gridy++;
        c.gridx = 1;
        c.gridwidth = 2;
        source.add(DialogParts.flow(header, DialogParts.hint(msg.get("data.file.hint"), 300)), c);
        c.gridy++;
        c.gridx = 0;
        c.gridwidth = 1;
        source.add(contacts, c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        group.setName("data.group");
        source.add(group, c);
        c.gridy++;
        c.gridx = 0;
        c.fill = GridBagConstraints.NONE;
        source.add(typed, c);
        c.gridx = 1;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.BOTH;
        typedText.setName("data.text");
        typedText.setToolTipText(msg.get("data.typed.hint"));
        source.add(new JScrollPane(typedText), c);
        c.gridy++;
        c.fill = GridBagConstraints.NONE;
        source.add(DialogParts.hint(msg.get("data.typed.hint"), 420), c);

        JPanel records = new JPanel(new BorderLayout(0, 6));
        JPanel options = DialogParts.flow(new JLabel(msg.get("data.filter")), filter,
                new JLabel(msg.get("data.sort")), sortBy, new JLabel(msg.get("data.copies")), copies);
        filter.setName("data.filter");
        filter.setToolTipText(msg.get("data.filter.hint"));
        sortBy.setName("data.sort");
        copies.setName("data.copies");
        records.add(options, BorderLayout.NORTH);
        table.setName("data.table");
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setRowHeight(table.getRowHeight() + 4);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(640, 260));
        records.add(scroll, BorderLayout.CENTER);
        count.setName("data.count");
        error.setForeground(new Color(0xC0392B));
        error.setName("data.error");
        JPanel south = new JPanel(new BorderLayout());
        south.add(count, BorderLayout.WEST);
        south.add(error, BorderLayout.CENTER);
        records.add(south, BorderLayout.SOUTH);

        JPanel right = new JPanel(new BorderLayout(0, 6));
        right.setPreferredSize(new Dimension(250, 300));
        right.add(DialogParts.bold(msg.get("data.mapping")), BorderLayout.NORTH);
        mappingPanel.setName("data.mapping");
        JPanel mappingTop = new JPanel(new BorderLayout());
        mappingTop.add(mappingPanel, BorderLayout.NORTH);
        right.add(new JScrollPane(mappingTop), BorderLayout.CENTER);
        right.add(DialogParts.hint(msg.get("data.mapping.hint"), 230), BorderLayout.SOUTH);

        JPanel centre = new JPanel(new BorderLayout(12, 0));
        centre.add(records, BorderLayout.CENTER);
        centre.add(right, BorderLayout.EAST);

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));
        root.add(source, BorderLayout.NORTH);
        root.add(centre, BorderLayout.CENTER);
        JButton ok = DialogParts.button(msg, "button.ok", this::accept);
        JButton cancel = DialogParts.button(msg, "button.cancel", this::dispose);
        root.add(DialogParts.okCancel(this, ok, cancel), BorderLayout.SOUTH);
        setContentPane(root);

        for (JRadioButton r : List.of(none, file, contacts, typed)) r.addActionListener(e -> reload());
        header.addActionListener(e -> reload());
        group.addActionListener(e -> {
            if (!updating) {
                contacts.setSelected(true);
                reload();
            }
        });
        path.addActionListener(e -> {
            file.setSelected(true);
            reload();
        });
        typedText.getDocument().addDocumentListener(changes(() -> {
            if (updating) return;
            typed.setSelected(true);
            reload();
        }));
        filter.getDocument().addDocumentListener(changes(this::updateCount));
        sortBy.addActionListener(e -> updateCount());
        copies.addChangeListener(e -> updateCount());
        pack();
        setLocationRelativeTo(owner);
    }

    private JRadioButton radio(String key) {
        JRadioButton r = Mnemonics.apply(new JRadioButton(), msg.get(key));
        r.setName(key);
        return r;
    }

    private static DocumentListener changes(Runnable r) {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                r.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                r.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                r.run();
            }
        };
    }

    /** Shows the dialog with the document's data; empty if it was cancelled. */
    public Optional<DataChoice> choose(DataSource source, MergeSettings merge, DataTable table, Set<String> fields) {
        designFields = fields;
        updating = true;
        try {
            group.addItem(msg.format("data.group.all", loader.contactCount("")));
            for (String g : loader.groups()) group.addItem(msg.format("data.group.one", g, loader.contactCount(g)));
            switch (source.kind()) {
                case NONE -> none.setSelected(true);
                case FILE -> file.setSelected(true);
                case CONTACTS -> contacts.setSelected(true);
                case TYPED -> typed.setSelected(true);
            }
            path.setText(source.path());
            header.setSelected(source.header());
            typedText.setText(source.text());
            int g = source.group().isEmpty() ? 0 : loader.groups().indexOf(source.group()) + 1;
            group.setSelectedIndex(Math.max(0, g));
            filter.setText(merge.filter());
            copies.setValue(merge.copies());
            excluded = new HashSet<>(merge.excluded());
        } finally {
            updating = false;
        }
        show(table, merge);
        if (table.isEmpty() && source.kind() != DataSource.Kind.NONE) reload();
        setVisible(true);
        return Optional.ofNullable(result);
    }

    /** The source the controls describe. */
    DataSource source() {
        if (file.isSelected()) return DataSource.file(path.getText().strip(), header.isSelected());
        if (contacts.isSelected()) {
            int i = group.getSelectedIndex();
            return DataSource.contacts(i <= 0 ? "" : loader.groups().get(i - 1));
        }
        if (typed.isSelected()) return DataSource.typed(typedText.getText());
        return DataSource.NONE;
    }

    private void chooseFile() {
        Path start = path.getText().isBlank() ? documents : Path.of(path.getText()).toAbsolutePath().getParent();
        JFileChooser chooser = new JFileChooser(start != null && Files.isDirectory(start) ? start.toFile() : null);
        chooser.setDialogTitle(Mnemonics.strip(msg.get("data.choose")));
        FileNameExtensionFilter lists = new FileNameExtensionFilter(msg.get("data.file.types"), "csv", "tsv", "txt",
                "ods", "xlsx", "vcf", "vcard");
        chooser.addChoosableFileFilter(lists);
        chooser.setFileFilter(lists);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = chooser.getSelectedFile();
        if (f == null) return;
        path.setText(f.getPath());
        file.setSelected(true);
        excluded.clear();
        reload();
    }

    /** Reads the chosen source in the background; a newer reading wins over an older one. */
    void reload() {
        if (updating) return;
        DataSource s = source();
        int serial = ++loadSerial;
        if (s.kind() == DataSource.Kind.FILE && s.path().isEmpty()) {
            show(DataTable.NONE, merge());
            return;
        }
        error.setText(msg.get("data.reading"));
        background.execute(() -> {
            DataTable loaded;
            String problem = null;
            try {
                loaded = loader.load(s);
            } catch (Exception e) {
                loaded = DataTable.NONE;
                problem = msg.format("error.data", s.path().isEmpty() ? s.group() : Path.of(s.path()).getFileName(),
                        e.getMessage());
            }
            DataTable done = loaded;
            String shown = problem;
            SwingUtilities.invokeLater(() -> {
                if (serial != loadSerial) return;
                if (!done.columns().equals(data.columns())) excluded.clear();
                show(done, merge());
                error.setText(shown == null ? " " : shown);
            });
        });
    }

    private void show(DataTable table, MergeSettings merge) {
        data = table;
        updating = true;
        try {
            Object sort = sortBy.getSelectedItem();
            sortBy.removeAllItems();
            sortBy.addItem(msg.get("data.sort.none"));
            for (String col : table.columns()) sortBy.addItem(col);
            if (!merge.sortBy().isEmpty()) sortBy.setSelectedItem(merge.sortBy());
            else if (sort != null) sortBy.setSelectedItem(sort);
            model.fireTableStructureChanged();
            if (table.columns().size() > 0) {
                table().getColumnModel().getColumn(0).setPreferredWidth(32);
                for (int i = 1; i < table().getColumnCount(); i++) {
                    table().getColumnModel().getColumn(i).setPreferredWidth(120);
                }
            }
            fillMapping(merge);
        } finally {
            updating = false;
        }
        updateCount();
    }

    private JTable table() {
        return table;
    }

    private void fillMapping(MergeSettings merge) {
        mappingPanel.removeAll();
        mapping.clear();
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(2, 0, 2, 6);
        c.gridy = 0;
        for (String field : designFields) {
            if (Placeholders.BUILT_IN.contains(field) || data.column(field).isPresent()) continue;
            JComboBox<String> box = new JComboBox<>();
            box.addItem(msg.get("data.mapping.none"));
            for (String col : data.columns()) box.addItem(col);
            Merge.column(data, merge, field).ifPresent(i -> box.setSelectedItem(data.columns().get(i)));
            box.setName("map:" + field);
            mapping.put(field, box);
            c.gridx = 0;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            mappingPanel.add(new JLabel(Placeholders.field(field)), c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            mappingPanel.add(box, c);
            c.gridy++;
        }
        if (mapping.isEmpty()) {
            c.gridx = 0;
            mappingPanel.add(DialogParts.hint(msg.get(data.isEmpty() ? "data.mapping.no-data" : "data.mapping.all"),
                    210), c);
        }
        mappingPanel.revalidate();
        mappingPanel.repaint();
    }

    /** The merge settings the controls describe. */
    MergeSettings merge() {
        Map<String, String> map = new LinkedHashMap<>();
        for (Map.Entry<String, JComboBox<String>> e : mapping.entrySet()) {
            int i = e.getValue().getSelectedIndex();
            map.put(e.getKey(), i <= 0 ? "" : String.valueOf(e.getValue().getSelectedItem()));
        }
        String sort = sortBy.getSelectedIndex() <= 0 ? "" : String.valueOf(sortBy.getSelectedItem());
        return new MergeSettings(excluded, filter.getText(), sort, (Integer) copies.getValue(), map);
    }

    private void updateCount() {
        if (updating) return;
        if (data.isEmpty()) {
            count.setText(source().kind() == DataSource.Kind.NONE ? msg.get("data.count.none") : " ");
            return;
        }
        MergeSettings m = merge();
        int rows = Merge.rows(data, m).size();
        count.setText(msg.format("data.count", rows, data.size(), rows * m.copies()));
        model.fireTableRowsUpdated(0, Math.max(0, data.size() - 1));
    }

    private void accept() {
        DataSource s = source();
        result = new DataChoice(s, s.kind() == DataSource.Kind.NONE ? DataTable.NONE : data,
                s.kind() == DataSource.Kind.NONE ? MergeSettings.ALL : merge());
        dispose();
    }

    /** For tests: the data shown. */
    DataTable data() {
        return data;
    }

    /** The records with a tick in front. */
    private final class RecordModel extends AbstractTableModel {
        @Override
        public int getRowCount() {
            return data.size();
        }

        @Override
        public int getColumnCount() {
            return data.columns().isEmpty() ? 0 : data.columns().size() + 1;
        }

        @Override
        public String getColumnName(int column) {
            return column == 0 ? "✓" : data.columns().get(column - 1);
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 0 ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 0;
        }

        @Override
        public Object getValueAt(int row, int column) {
            return column == 0 ? !excluded.contains(row) : data.value(row, column - 1);
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            if (column != 0) return;
            if (Boolean.TRUE.equals(value)) excluded.remove(row);
            else excluded.add(row);
            updateCount();
        }
    }

    /** For tests: the rows ticked off. */
    List<Integer> excludedRows() {
        return new ArrayList<>(excluded);
    }
}
