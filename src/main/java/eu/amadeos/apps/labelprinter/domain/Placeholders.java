package eu.amadeos.apps.labelprinter.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fields in a text: {@code «Name»} is replaced by the value of the record. Typing {@code <<Name>>} works as well,
 * for keyboards without guillemets.
 */
public final class Placeholders {
    public static final String DATE = "Date";
    public static final String COUNTER = "Counter";
    public static final String NUMBER = "Number";
    /** Fields every design has, whatever the data: today's date, the counter and the label's number. */
    public static final Set<String> BUILT_IN = Set.of(DATE, COUNTER, NUMBER);

    private static final Pattern FIELD = Pattern.compile("«([^«»\\n]{1,80})»|<<([^<>\\n]{1,80})>>");
    private static final Pattern ONLY_PUNCTUATION = Pattern.compile("[\\s,;:·/|\\-–]*");

    private Placeholders() {
    }

    /** {@code «Name»}. */
    public static String field(String name) {
        return "«" + name + "»";
    }

    /** The field names in a text, in order, each once. */
    public static Set<String> fields(String text) {
        Set<String> names = new LinkedHashSet<>();
        Matcher m = FIELD.matcher(text);
        while (m.find()) names.add(name(m));
        return names;
    }

    /**
     * The text with its fields filled.
     *
     * @param values         the value of a field: "" for an empty one, null for a field that is not known (it stays
     *                       as {@code «Name»}, so the design shows where it goes)
     * @param hideEmptyLines leave out lines that have fields and nothing but empty fields and punctuation
     */
    public static String fill(String text, Function<String, String> values, boolean hideEmptyLines) {
        List<String> out = new ArrayList<>();
        for (String line : text.split("\n", -1)) {
            Matcher m = FIELD.matcher(line);
            StringBuilder sb = new StringBuilder();
            boolean anyField = false;
            boolean anyValue = false;
            int last = 0;
            StringBuilder literal = new StringBuilder();
            while (m.find()) {
                anyField = true;
                String between = line.substring(last, m.start());
                sb.append(between);
                literal.append(between);
                String value = values.apply(name(m));
                if (value == null) {
                    sb.append(m.group());
                    anyValue = true;
                } else {
                    sb.append(value);
                    if (!value.isBlank()) anyValue = true;
                }
                last = m.end();
            }
            sb.append(line.substring(last));
            literal.append(line.substring(last));
            if (hideEmptyLines && anyField && !anyValue && ONLY_PUNCTUATION.matcher(literal).matches()) continue;
            out.add(anyField ? tidy(sb.toString()) : sb.toString());
        }
        return String.join("\n", out);
    }

    /** Two spaces where an empty field was become one; spaces at the ends go. */
    private static String tidy(String line) {
        return line.replaceAll(" {2,}", " ").strip();
    }

    private static String name(Matcher m) {
        return (m.group(1) != null ? m.group(1) : m.group(2)).strip();
    }

    /** The counter as text: {@code 7} with 4 digits is {@code 0007}. */
    public static String counter(int value, int digits) {
        String s = Integer.toString(Math.abs(value));
        if (s.length() < digits) s = "0".repeat(digits - s.length()) + s;
        return value < 0 ? "-" + s : s;
    }
}
