package eu.amadeos.apps.labelprinter.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Data for mail merge: named columns and rows of text. Rows are as long as the column list (shorter ones are
 * padded, longer ones cut).
 *
 * @param source where it came from, for the window's status line
 */
public record DataTable(String source, List<String> columns, List<List<String>> rows) {
    public static final DataTable NONE = new DataTable("", List.of(), List.of());

    public DataTable {
        source = source == null ? "" : source;
        columns = List.copyOf(columns);
        List<List<String>> fixed = new ArrayList<>(rows.size());
        for (List<String> row : rows) {
            List<String> r = new ArrayList<>(columns.size());
            for (int i = 0; i < columns.size(); i++) r.add(i < row.size() && row.get(i) != null ? row.get(i) : "");
            fixed.add(List.copyOf(r));
        }
        rows = List.copyOf(fixed);
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    public int size() {
        return rows.size();
    }

    /** The column with this name, ignoring case and spaces at the ends. */
    public Optional<Integer> column(String name) {
        String wanted = normal(name);
        for (int i = 0; i < columns.size(); i++) {
            if (normal(columns.get(i)).equals(wanted)) return Optional.of(i);
        }
        return Optional.empty();
    }

    public String value(int row, int column) {
        return rows.get(row).get(column);
    }

    static String normal(String s) {
        return Objects.requireNonNullElse(s, "").strip().toLowerCase(Locale.ROOT);
    }
}
