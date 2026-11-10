package eu.amadeos.apps.labelprinter.service;

import eu.amadeos.apps.labelprinter.domain.DataTable;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads CSV and TSV files as spreadsheets and address books export them: the separator ({@code ;}, {@code ,},
 * tab or {@code |}) and the encoding (UTF-8 with or without BOM, UTF-16 with BOM, else Windows-1252) are found
 * out, quoted fields may hold separators, quotes ({@code ""}) and line breaks.
 */
public final class CsvReader {
    private static final char[] SEPARATORS = {';', ',', '\t', '|'};
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    private CsvReader() {
    }

    /** What was found out about a file, for the data window. */
    public record Detected(char separator, Charset charset) {
    }

    public static DataTable read(byte[] bytes, String source, boolean header) {
        String text = decode(bytes).text();
        return parse(text, separator(text), source, header);
    }

    public static Detected detect(byte[] bytes) {
        Decoded d = decode(bytes);
        return new Detected(separator(d.text()), d.charset());
    }

    record Decoded(String text, Charset charset) {
    }

    static Decoded decode(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return new Decoded(new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            return new Decoded(new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE),
                    StandardCharsets.UTF_16LE);
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            return new Decoded(new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE),
                    StandardCharsets.UTF_16BE);
        }
        try {
            String utf8 = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            return new Decoded(utf8, StandardCharsets.UTF_8);
        } catch (CharacterCodingException e) {
            return new Decoded(new String(bytes, WINDOWS_1252), WINDOWS_1252);
        }
    }

    /** The separator that splits the first lines into the same, largest number of fields. */
    static char separator(String text) {
        List<String> lines = text.lines().filter(l -> !l.isBlank()).limit(10).toList();
        char best = ',';
        int bestScore = 0;
        for (char sep : SEPARATORS) {
            int first = -1;
            int score = 0;
            for (String line : lines) {
                int n = countOutsideQuotes(line, sep);
                if (first < 0) first = n;
                if (n > 0 && n == first) score += n + 1;
            }
            if (score > bestScore) {
                bestScore = score;
                best = sep;
            }
        }
        return best;
    }

    private static int countOutsideQuotes(String line, char sep) {
        int n = 0;
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') quoted = !quoted;
            else if (c == sep && !quoted) n++;
        }
        return n;
    }

    /**
     * Splits the text into rows and fields.
     *
     * @param header true when the first row names the columns; else they are called Column 1, Column 2, …
     */
    public static DataTable parse(String text, char sep, String source, boolean header) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean wasQuoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"' && field.isEmpty() && !wasQuoted) {
                quoted = true;
                wasQuoted = true;
            } else if (c == sep) {
                row.add(field.toString());
                field.setLength(0);
                wasQuoted = false;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(field.toString());
                field.setLength(0);
                wasQuoted = false;
                if (!(row.size() == 1 && row.getFirst().isEmpty())) rows.add(row);
                row = new ArrayList<>();
            } else {
                field.append(c);
            }
        }
        if (!field.isEmpty() || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return table(rows, source, header);
    }

    /** Rows to a table; empty and repeated column names get a number. */
    static DataTable table(List<List<String>> rows, String source, boolean header) {
        int width = rows.stream().mapToInt(List::size).max().orElse(0);
        List<String> columns = new ArrayList<>();
        List<String> names = header && !rows.isEmpty() ? rows.getFirst() : List.of();
        for (int i = 0; i < width; i++) {
            String name = i < names.size() ? names.get(i).strip() : "";
            if (name.isEmpty()) name = "Column " + (i + 1);
            String unique = name;
            int n = 2;
            while (columns.contains(unique)) unique = name + " " + n++;
            columns.add(unique);
        }
        List<List<String>> body = header && !rows.isEmpty() ? rows.subList(1, rows.size()) : rows;
        List<List<String>> trimmed = new ArrayList<>();
        for (List<String> r : body) {
            if (r.stream().allMatch(String::isBlank)) continue;
            trimmed.add(r.stream().map(String::strip).toList());
        }
        return new DataTable(source, columns, trimmed);
    }
}
