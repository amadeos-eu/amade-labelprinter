package eu.amadeos.apps.labelprinter.domain;

import java.awt.geom.Rectangle2D;
import java.util.Locale;
import java.util.Objects;

/**
 * A sheet of labels or cards: the paper and where the labels are on it, all in millimetres. The labels form a
 * grid of {@code columns × rows}; the first one's top left corner is at ({@code left}, {@code top}) and the next
 * one starts {@code pitchX} to the right or {@code pitchY} further down (the label plus the gap).
 *
 * @param id       a stable name, such as {@code a4-70x37-3x8}; own sheets start with {@code own-}
 * @param kind     what the sheet is for, to group the catalogue
 * @param paperW   paper width
 * @param paperH   paper height
 * @param columns  labels across
 * @param rows     labels down
 * @param width    label width
 * @param height   label height
 * @param left     left margin to the first label
 * @param top      top margin to the first label
 * @param pitchX   from one label's left edge to the next one's
 * @param pitchY   from one label's top edge to the next one's
 * @param radius   corner radius, 0 for square corners
 * @param shape    rectangle, ellipse (round labels) or disc (CD labels with a hole)
 * @param hole     the hole's diameter for {@link LabelShape#DISC}, else 0
 */
public record SheetTemplate(String id, SheetKind kind, double paperW, double paperH, int columns, int rows,
                            double width, double height, double left, double top, double pitchX, double pitchY,
                            double radius, LabelShape shape, double hole) {
    /** Labels may stick out of the paper by this much (rounding in the makers' data). */
    private static final double TOLERANCE = 0.6;

    public SheetTemplate {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(shape, "shape");
        if (columns < 1 || rows < 1) throw new IllegalArgumentException("At least one label: " + id);
        if (width <= 0 || height <= 0 || paperW <= 0 || paperH <= 0) {
            throw new IllegalArgumentException("Sizes must be positive: " + id);
        }
        if (columns > 1 && pitchX < width - 0.01) throw new IllegalArgumentException("Labels overlap across: " + id);
        if (rows > 1 && pitchY < height - 0.01) throw new IllegalArgumentException("Labels overlap down: " + id);
        if (left < -TOLERANCE || top < -TOLERANCE
                || left + (columns - 1) * pitchX + width > paperW + TOLERANCE
                || top + (rows - 1) * pitchY + height > paperH + TOLERANCE) {
            throw new IllegalArgumentException("Labels do not fit on the paper: " + id);
        }
        radius = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        hole = shape == LabelShape.DISC ? Math.max(0, Math.min(hole, Math.min(width, height) - 2)) : 0;
    }

    /** A sheet with labels side by side without gaps, centred on the paper. */
    public static SheetTemplate grid(String id, SheetKind kind, double paperW, double paperH, int columns, int rows,
                                     double width, double height) {
        double left = (paperW - columns * width) / 2;
        double top = (paperH - rows * height) / 2;
        return new SheetTemplate(id, kind, paperW, paperH, columns, rows, width, height, round(left), round(top),
                width, height, 0, LabelShape.RECTANGLE, 0);
    }

    public int count() {
        return columns * rows;
    }

    public boolean isOwn() {
        return id.startsWith("own-");
    }

    /** The label at {@code index}, counted across and then down from 0. */
    public Rectangle2D label(int index) {
        if (index < 0 || index >= count()) throw new IndexOutOfBoundsException(index);
        int column = index % columns;
        int row = index / columns;
        return new Rectangle2D.Double(left + column * pitchX, top + row * pitchY, width, height);
    }

    /** The column of the same label seen from the back of the sheet, for two-sided printing. */
    public int mirroredIndex(int index) {
        int column = index % columns;
        int row = index / columns;
        return row * columns + (columns - 1 - column);
    }

    /** Space between two labels across, 0 if they touch. */
    public double gapX() {
        return columns > 1 ? Math.max(0, pitchX - width) : 0;
    }

    public double gapY() {
        return rows > 1 ? Math.max(0, pitchY - height) : 0;
    }

    /** The paper's name if it is a standard size, else its size in millimetres. */
    public String paperName() {
        for (Paper paper : Paper.values()) {
            if (paper.matches(paperW, paperH)) return paper.label();
        }
        return mm(paperW) + " × " + mm(paperH) + " mm";
    }

    /** {@code 70 × 37 mm}, or {@code Ø 40 mm} for round labels of equal width and height. */
    public String sizeText() {
        if (shape != LabelShape.RECTANGLE && Math.abs(width - height) < 0.01) return "Ø " + mm(width) + " mm";
        return mm(width) + " × " + mm(height) + " mm";
    }

    /** {@code 3 × 8}. */
    public String gridText() {
        return columns + " × " + rows;
    }

    /** Whether the search text names this sheet's size, such as {@code 70x37}, {@code 70 × 37} or {@code 63,5}. */
    public boolean matches(String search) {
        String s = search.strip().toLowerCase(Locale.ROOT).replace(',', '.').replace('×', 'x').replace("mm", "")
                .replace(" ", "");
        if (s.isEmpty()) return true;
        String size = (mm(width) + "x" + mm(height)).replace(',', '.');
        String grid = columns + "x" + rows;
        if (size.contains(s) || s.equals(grid) || s.equals(Integer.toString(count()))) return true;
        String[] parts = s.split("x");
        if (parts.length == 2) {
            try {
                double w = Double.parseDouble(parts[0]);
                double h = Double.parseDouble(parts[1]);
                return Math.abs(w - width) < 0.6 && Math.abs(h - height) < 0.6;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

    public SheetTemplate withId(String newId) {
        return new SheetTemplate(newId, kind, paperW, paperH, columns, rows, width, height, left, top, pitchX,
                pitchY, radius, shape, hole);
    }

    /** {@code 70}, {@code 63.5}, {@code 38.1}: at most one decimal place, without a trailing zero. */
    public static String mm(double value) {
        double rounded = Math.round(value * 10) / 10.0;
        return rounded == Math.rint(rounded) ? Long.toString(Math.round(rounded))
                : String.format(Locale.ROOT, "%.1f", rounded);
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }

    /** The usual papers. */
    public enum Paper {
        A4("A4", 210, 297), A5("A5", 148, 210), LETTER("Letter", 215.9, 279.4), A6("A6", 105, 148);

        private final String label;
        private final double width;
        private final double height;

        Paper(String label, double width, double height) {
            this.label = label;
            this.width = width;
            this.height = height;
        }

        public String label() {
            return label;
        }

        public double width() {
            return width;
        }

        public double height() {
            return height;
        }

        public boolean matches(double w, double h) {
            return Math.abs(w - width) < 0.5 && Math.abs(h - height) < 0.5;
        }
    }
}
