package eu.amadeos.apps.labelprinter.domain;

/** The barcodes and 2D codes a label can carry. */
public enum CodeType {
    QR(true), DATA_MATRIX(true), EAN_13(false), EAN_8(false), CODE_128(false), CODE_39(false);

    private final boolean square;

    CodeType(boolean square) {
        this.square = square;
    }

    /** 2D codes are square; barcodes are wide and may show their digits below. */
    public boolean square() {
        return square;
    }

    public String key() {
        return "code." + name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }
}
