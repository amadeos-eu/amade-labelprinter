package eu.amadeos.apps.labelprinter.domain;

/** Help for cutting labels or cards out of plain paper by hand. */
public enum CutGuides {
    /** Pre-cut sheets: nothing is printed besides the labels. */
    NONE,
    /** Short lines on the paper's edges, outside the labels, in line with every edge of the labels. */
    CROP_MARKS,
    /** A thin line around each label. */
    OUTLINES;

    public String key() {
        return "cut." + name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }
}
