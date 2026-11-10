package eu.amadeos.apps.labelprinter.domain;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns data and merge settings into the list of labels to print. */
public final class Merge {
    private static final Pattern CONDITION = Pattern.compile("(.+?)\\s*(!=|≠|=|~)\\s*(.*)");

    private Merge() {
    }

    /**
     * One label's values.
     *
     * @param row    the row of the data, -1 for a label without data
     * @param number the label's number in the print, from 1
     */
    public record Label(int row, int number, Function<String, String> values) {
    }

    /** The rows that are printed, filtered and sorted; each row once. */
    public static List<Integer> rows(DataTable data, MergeSettings settings) {
        List<Integer> rows = new ArrayList<>();
        for (int r = 0; r < data.size(); r++) {
            if (!settings.excluded().contains(r) && matches(data, r, settings.filter())) rows.add(r);
        }
        Optional<Integer> sort = settings.sortBy().isEmpty() ? Optional.empty() : data.column(settings.sortBy());
        sort.ifPresent(c -> rows.sort(Comparator.comparing(r -> data.value(r, c), Merge::natural)));
        return rows;
    }

    /**
     * The labels: each printed row {@code copies} times, or a single label without data.
     *
     * @param today for the «Date» field
     */
    public static List<Label> labels(DataTable data, MergeSettings settings, LabelDesign design, LocalDate today,
                                     Locale locale) {
        String date = today.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale));
        List<Label> labels = new ArrayList<>();
        if (data.isEmpty()) {
            labels.add(label(data, settings, design, -1, 1, date));
            return labels;
        }
        int number = 1;
        for (int row : rows(data, settings)) {
            for (int copy = 0; copy < settings.copies(); copy++) {
                labels.add(label(data, settings, design, row, number++, date));
            }
        }
        return labels;
    }

    /** One label for {@code row} (-1 without data), as the design view and the print use it. */
    public static Label label(DataTable data, MergeSettings settings, LabelDesign design, int row, int number,
                              String date) {
        Function<String, String> values = field -> {
            Optional<Integer> column = column(data, settings, field);
            if (column.isPresent() && row >= 0) return data.value(row, column.get());
            return switch (field) {
                case Placeholders.DATE -> date;
                case Placeholders.COUNTER -> Placeholders.counter(design.counterStart() + number - 1,
                        design.counterDigits());
                case Placeholders.NUMBER -> Integer.toString(number);
                default -> row < 0 ? null : "";
            };
        };
        return new Label(row, number, values);
    }

    /** The column that fills a field: the one chosen in the mapping, else the one with the field's name. */
    public static Optional<Integer> column(DataTable data, MergeSettings settings, String field) {
        String mapped = settings.mapping().get(field);
        if (mapped != null) {
            if (mapped.isEmpty()) return Optional.empty();
            Optional<Integer> c = data.column(mapped);
            if (c.isPresent()) return c;
        }
        Optional<Integer> same = data.column(field);
        if (same.isPresent()) return same;
        return FieldNames.match(field, data.columns()).flatMap(data::column);
    }

    private static boolean matches(DataTable data, int row, String filter) {
        if (filter.isEmpty()) return true;
        Matcher m = CONDITION.matcher(filter);
        if (m.matches()) {
            Optional<Integer> column = data.column(m.group(1));
            if (column.isPresent()) {
                String value = data.value(row, column.get()).strip().toLowerCase(Locale.ROOT);
                String wanted = m.group(3).strip().toLowerCase(Locale.ROOT);
                return switch (m.group(2)) {
                    case "=" -> value.equals(wanted);
                    case "~" -> value.contains(wanted);
                    default -> !value.equals(wanted);
                };
            }
        }
        String wanted = filter.toLowerCase(Locale.ROOT);
        return data.rows().get(row).stream().anyMatch(v -> v.toLowerCase(Locale.ROOT).contains(wanted));
    }

    /** Numbers by their value (2 before 10), everything else alphabetically, ignoring case. */
    static int natural(String a, String b) {
        try {
            return Double.compare(Double.parseDouble(a.strip().replace(',', '.')),
                    Double.parseDouble(b.strip().replace(',', '.')));
        } catch (NumberFormatException e) {
            return String.CASE_INSENSITIVE_ORDER.compare(a, b);
        }
    }
}
