package eu.amadeos.apps.labelprinter.domain;

/** What a sheet is for; the catalogue groups its sheets by kind. */
public enum SheetKind {
    ADDRESS, SHIPPING, MULTI_PURPOSE, BUSINESS_CARD, NAME_BADGE, FILE_SPINE, ROUND, DISC, JAR;

    /** The key of its name in the messages, {@code kind.address}. */
    public String key() {
        return "kind." + name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }
}
