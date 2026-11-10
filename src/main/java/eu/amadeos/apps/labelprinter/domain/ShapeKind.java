package eu.amadeos.apps.labelprinter.domain;

/** The shapes a label can carry. */
public enum ShapeKind {
    RECTANGLE, ELLIPSE, LINE;

    public String key() {
        return "shape." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
