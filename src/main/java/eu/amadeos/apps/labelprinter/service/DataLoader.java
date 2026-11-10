package eu.amadeos.apps.labelprinter.service;

import eu.amadeos.apps.labelprinter.domain.DataSource;
import eu.amadeos.apps.labelprinter.domain.DataTable;
import eu.amadeos.commons.contacts.ContactCard;
import eu.amadeos.commons.contacts.ContactDirectory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Function;

/** Reads the data of a {@link DataSource}: a CSV, TSV or spreadsheet file, a vCard file, the contacts or a typed list. */
public final class DataLoader {
    /** Files larger than this are refused, so a wrong file cannot fill the memory. */
    public static final long MAX_FILE = 64L * 1024 * 1024;

    private final ContactDirectory contacts;
    private final Function<String, String> names;

    /**
     * @param contacts the address books
     * @param names    the text for a message key, for the names of the contact columns and «Line 1»
     */
    public DataLoader(ContactDirectory contacts, Function<String, String> names) {
        this.contacts = Objects.requireNonNull(contacts, "contacts");
        this.names = Objects.requireNonNull(names, "names");
    }

    public DataTable load(DataSource source) throws IOException {
        return switch (source.kind()) {
            case NONE -> DataTable.NONE;
            case FILE -> file(Path.of(source.path()), source.header());
            case CONTACTS -> contacts(source.group());
            case TYPED -> typed(source.text());
        };
    }

    public DataTable file(Path file, boolean header) throws IOException {
        if (Files.size(file) > MAX_FILE) throw new IOException("File too large: " + file.getFileName());
        String name = file.getFileName().toString();
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".vcf") || lower.endsWith(".vcard")) {
            return cards(ContactDirectory.readFile(file), name);
        }
        byte[] bytes = Files.readAllBytes(file);
        if (SpreadsheetReader.handles(name)) return SpreadsheetReader.read(bytes, name, header);
        return CsvReader.read(bytes, name, header);
    }

    /** The contacts of a group, or all contacts for "", one row each. */
    public DataTable contacts(String group) {
        contacts.refresh();
        List<ContactCard> cards = contacts.contacts().stream()
                .filter(c -> group.isEmpty() || c.groups().contains(group)).toList();
        return cards(cards, group.isEmpty() ? names.apply("source.contacts.all") : group);
    }

    /** The groups of all contacts, sorted. */
    public List<String> groups() {
        TreeSet<String> groups = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (ContactCard c : contacts.contacts()) groups.addAll(c.groups());
        return List.copyOf(groups);
    }

    public int contactCount(String group) {
        return (int) contacts.contacts().stream().filter(c -> group.isEmpty() || c.groups().contains(group)).count();
    }

    private DataTable cards(List<ContactCard> cards, String source) {
        List<String> columns = new ArrayList<>();
        for (ContactColumn c : ContactColumn.values()) columns.add(names.apply(c.key()));
        List<List<String>> rows = new ArrayList<>();
        for (ContactCard card : cards) {
            List<String> row = new ArrayList<>();
            for (ContactColumn c : ContactColumn.values()) row.add(c.value(card));
            rows.add(row);
        }
        return new DataTable(source, columns, rows);
    }

    /**
     * A typed list: records are separated by an empty line, their lines are «Line 1», «Line 2», … and «Text» is the
     * whole record.
     */
    public DataTable typed(String text) {
        List<List<String>> records = new ArrayList<>();
        List<String> current = new ArrayList<>();
        for (String line : text.replace("\r", "").split("\n", -1)) {
            if (line.isBlank()) {
                if (!current.isEmpty()) records.add(current);
                current = new ArrayList<>();
            } else {
                current.add(line.strip());
            }
        }
        if (!current.isEmpty()) records.add(current);
        int lines = records.stream().mapToInt(List::size).max().orElse(1);
        List<String> columns = new ArrayList<>();
        columns.add(names.apply("column.text"));
        for (int i = 1; i <= lines; i++) columns.add(names.apply("column.line").replace("{0}", Integer.toString(i)));
        List<List<String>> rows = new ArrayList<>();
        for (List<String> r : records) {
            List<String> row = new ArrayList<>();
            row.add(String.join("\n", r));
            row.addAll(r);
            rows.add(row);
        }
        return new DataTable(names.apply("source.typed"), columns, rows);
    }
}
