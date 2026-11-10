package eu.amadeos.apps.labelprinter.domain;

/**
 * Where the data for mail merge comes from. The design keeps only this reference, so the labels show the list
 * as it is when they are printed.
 *
 * @param kind   none, a file, the address books or a typed list
 * @param path   the file, for {@link Kind#FILE}
 * @param group  the group of the address books, "" for all contacts, for {@link Kind#CONTACTS}
 * @param text   the typed list, for {@link Kind#TYPED}
 * @param header whether the first row of a CSV file names the columns
 */
public record DataSource(Kind kind, String path, String group, String text, boolean header) {
    public static final DataSource NONE = new DataSource(Kind.NONE, "", "", "", true);

    public enum Kind { NONE, FILE, CONTACTS, TYPED }

    public DataSource {
        kind = kind == null ? Kind.NONE : kind;
        path = path == null ? "" : path;
        group = group == null ? "" : group;
        text = text == null ? "" : text;
    }

    public static DataSource file(String path, boolean header) {
        return new DataSource(Kind.FILE, path, "", "", header);
    }

    public static DataSource contacts(String group) {
        return new DataSource(Kind.CONTACTS, "", group, "", true);
    }

    public static DataSource typed(String text) {
        return new DataSource(Kind.TYPED, "", "", text, true);
    }
}
